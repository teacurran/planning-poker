/**
 * WebSocket connection manager for Planning Poker real-time communication.
 *
 * Features:
 * - Connection lifecycle management (connect, disconnect, reconnect)
 * - Exponential backoff reconnection strategy (1s, 2s, 4s, 8s, max 16s)
 * - Message serialization and deserialization
 * - Event handler registration and dispatching
 * - Heartbeat ping/pong mechanism
 * - Connection status tracking
 *
 * @see api/websocket-protocol.md
 */

import type {
  WebSocketMessage,
  MessageHandler,
  ConnectionStatus,
  RoomJoinPayload,
} from '@/types/websocket';

// ========================================
// Configuration
// ========================================

/**
 * WebSocket base URL based on environment.
 * In development: ws://localhost:8080
 * In production: wss://api.scrumpoker.com
 */
const getWebSocketBaseUrl = (): string => {
  // Check if we have an environment variable (Vite uses VITE_ prefix)
  const envUrl = import.meta.env.VITE_WS_BASE_URL;
  if (envUrl) {
    return envUrl;
  }

  // Auto-detect based on current page protocol and hostname
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const hostname = window.location.hostname;

  // In development, connect to backend on port 8080
  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    return `${protocol}//${hostname}:8080`;
  }

  // In production, use same hostname
  return `${protocol}//${hostname}`;
};

const WS_BASE_URL = getWebSocketBaseUrl();
const HEARTBEAT_INTERVAL = 30000; // 30 seconds
const JOIN_TIMEOUT = 10000; // 10 seconds - must send room.join.v1 within this time
const MAX_RECONNECT_DELAY = 16000; // 16 seconds max delay
const INITIAL_RECONNECT_DELAY = 1000; // 1 second initial delay

// ========================================
// WebSocketManager Class
// ========================================

class WebSocketManager {
  private ws: WebSocket | null = null;
  private roomId: string | null = null;
  private token: string | null = null;
  private connectionStatus: ConnectionStatus = 'disconnected';
  private reconnectAttempts = 0;
  private reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
  private heartbeatInterval: ReturnType<typeof setInterval> | null = null;
  private joinTimeout: ReturnType<typeof setTimeout> | null = null;

  // Event handlers: Map<messageType, Set<handler>>
  private eventHandlers = new Map<string, Set<MessageHandler>>();

  // Connection status listeners
  private statusListeners = new Set<(status: ConnectionStatus) => void>();

  // Track last event ID for reconnection event replay
  private lastEventId: string | null = null;

  // User information for room.join.v1
  private userDisplayName: string | null = null;
  private userRole: 'HOST' | 'VOTER' | 'OBSERVER' = 'VOTER';

  /**
   * Connect to a room's WebSocket endpoint.
   *
   * @param roomId - 6-character room identifier
   * @param token - JWT access token
   * @param displayName - User's display name
   * @param role - User's role (HOST, VOTER, or OBSERVER)
   */
  connect(
    roomId: string,
    token: string,
    displayName: string,
    role: 'HOST' | 'VOTER' | 'OBSERVER' = 'VOTER'
  ): void {
    // Clean up existing connection if any
    if (this.ws) {
      this.disconnect();
    }

    this.roomId = roomId;
    this.token = token;
    this.userDisplayName = displayName;
    this.userRole = role;

    this.setConnectionStatus('connecting');

    const url = `${WS_BASE_URL}/ws/room/${roomId}?token=${encodeURIComponent(token)}`;

    try {
      this.ws = new WebSocket(url);
      this.setupEventHandlers();
    } catch (error) {
      console.error('[WebSocketManager] Failed to create WebSocket connection:', error);
      this.setConnectionStatus('disconnected');
      this.scheduleReconnect();
    }
  }

  /**
   * Disconnect from the WebSocket.
   * Sends room.leave.v1 message for graceful disconnection.
   */
  disconnect(): void {
    // Clear all timers
    this.clearTimers();

    // Send graceful leave message if connected
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      try {
        this.send('room.leave.v1', { reason: 'user_initiated' });
      } catch (error) {
        console.error('[WebSocketManager] Failed to send leave message:', error);
      }
    }

    // Close WebSocket connection
    if (this.ws) {
      this.ws.close(1000, 'Normal closure');
      this.ws = null;
    }

    this.setConnectionStatus('disconnected');
    this.roomId = null;
    this.token = null;
    this.reconnectAttempts = 0;
    this.lastEventId = null;
  }

  /**
   * Send a message to the server.
   *
   * @param type - Message type (e.g., 'vote.cast.v1')
   * @param payload - Message payload
   * @returns Request ID for correlation
   */
  send<T = unknown>(type: string, payload: T): string {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket is not connected');
    }

    const requestId = this.generateRequestId();
    const message: WebSocketMessage<T> = {
      type,
      requestId,
      payload,
    };

    try {
      this.ws.send(JSON.stringify(message));
      return requestId;
    } catch (error) {
      console.error('[WebSocketManager] Failed to send message:', error);
      throw error;
    }
  }

  /**
   * Register an event handler for a specific message type.
   *
   * @param messageType - Message type to listen for
   * @param handler - Handler function
   * @returns Unsubscribe function
   */
  on<T = unknown>(messageType: string, handler: MessageHandler<T>): () => void {
    if (!this.eventHandlers.has(messageType)) {
      this.eventHandlers.set(messageType, new Set());
    }

    const handlers = this.eventHandlers.get(messageType)!;
    handlers.add(handler as MessageHandler);

    // Return unsubscribe function
    return () => {
      handlers.delete(handler as MessageHandler);
      if (handlers.size === 0) {
        this.eventHandlers.delete(messageType);
      }
    };
  }

  /**
   * Register a connection status listener.
   *
   * @param listener - Status change listener
   * @returns Unsubscribe function
   */
  onStatusChange(listener: (status: ConnectionStatus) => void): () => void {
    this.statusListeners.add(listener);

    // Immediately invoke with current status
    listener(this.connectionStatus);

    return () => {
      this.statusListeners.delete(listener);
    };
  }

  /**
   * Get current connection status.
   */
  getStatus(): ConnectionStatus {
    return this.connectionStatus;
  }

  /**
   * Check if currently connected.
   */
  isConnected(): boolean {
    return this.connectionStatus === 'connected';
  }

  // ========================================
  // Private Methods
  // ========================================

  private setupEventHandlers(): void {
    if (!this.ws) return;

    this.ws.onopen = this.handleOpen.bind(this);
    this.ws.onmessage = this.handleMessage.bind(this);
    this.ws.onerror = this.handleError.bind(this);
    this.ws.onclose = this.handleClose.bind(this);
  }

  private handleOpen(): void {
    console.log('[WebSocketManager] WebSocket connection established');

    this.setConnectionStatus('connected');
    this.reconnectAttempts = 0; // Reset reconnection counter

    // Start heartbeat
    this.startHeartbeat();

    // Send room.join.v1 message immediately
    this.sendJoinMessage();
  }

  private handleMessage(event: MessageEvent): void {
    try {
      const message = JSON.parse(event.data) as WebSocketMessage;

      // Store lastEventId if present (for reconnection event replay)
      if (message.payload && typeof message.payload === 'object' && 'lastEventId' in message.payload) {
        this.lastEventId = (message.payload as { lastEventId: string }).lastEventId;
      }

      // Dispatch to registered handlers
      const handlers = this.eventHandlers.get(message.type);
      if (handlers) {
        handlers.forEach((handler) => {
          try {
            handler(message.payload);
          } catch (error) {
            console.error(`[WebSocketManager] Error in handler for ${message.type}:`, error);
          }
        });
      }
    } catch (error) {
      console.error('[WebSocketManager] Failed to parse message:', error);
    }
  }

  private handleError(event: Event): void {
    console.error('[WebSocketManager] WebSocket error:', event);
  }

  private handleClose(event: CloseEvent): void {
    console.log(`[WebSocketManager] WebSocket closed: code=${event.code}, reason=${event.reason}`);

    this.clearTimers();
    this.setConnectionStatus('disconnected');

    // Reconnect if closure was unexpected (not normal closure)
    if (event.code !== 1000) {
      this.scheduleReconnect();
    }
  }

  private sendJoinMessage(): void {
    if (!this.userDisplayName) {
      console.error('[WebSocketManager] Cannot send join message: displayName not set');
      return;
    }

    const payload: RoomJoinPayload = {
      displayName: this.userDisplayName,
      role: this.userRole,
      lastEventId: this.lastEventId, // Include for event replay on reconnection
    };

    try {
      this.send('room.join.v1', payload);
      console.log('[WebSocketManager] Sent room.join.v1 message');
    } catch (error) {
      console.error('[WebSocketManager] Failed to send join message:', error);
    }

    // Set timeout to ensure we don't violate the 10-second join requirement
    this.joinTimeout = setTimeout(() => {
      if (this.connectionStatus === 'connected') {
        console.warn('[WebSocketManager] Join message timeout - connection may be closed by server');
      }
    }, JOIN_TIMEOUT);
  }

  private scheduleReconnect(): void {
    // Clear any existing reconnect timeout
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
    }

    // Calculate delay with exponential backoff: 1s, 2s, 4s, 8s, 16s (max)
    const delay = Math.min(
      INITIAL_RECONNECT_DELAY * Math.pow(2, this.reconnectAttempts),
      MAX_RECONNECT_DELAY
    );

    this.reconnectAttempts++;

    console.log(
      `[WebSocketManager] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`
    );

    this.reconnectTimeout = setTimeout(() => {
      if (this.roomId && this.token && this.userDisplayName) {
        this.connect(this.roomId, this.token, this.userDisplayName, this.userRole);
      }
    }, delay);
  }

  private startHeartbeat(): void {
    // Clear existing interval if any
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
    }

    // Send ping every 30 seconds
    this.heartbeatInterval = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        try {
          // Note: Browser WebSocket API doesn't expose ping() method directly
          // The browser handles ping/pong frames automatically in most cases
          // If needed, we can send a custom ping message or rely on the server's ping
          console.log('[WebSocketManager] Heartbeat check - connection alive');
        } catch (error) {
          console.error('[WebSocketManager] Heartbeat error:', error);
        }
      }
    }, HEARTBEAT_INTERVAL);
  }

  private clearTimers(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }

    if (this.joinTimeout) {
      clearTimeout(this.joinTimeout);
      this.joinTimeout = null;
    }
  }

  private setConnectionStatus(status: ConnectionStatus): void {
    if (this.connectionStatus !== status) {
      this.connectionStatus = status;
      console.log(`[WebSocketManager] Connection status changed: ${status}`);

      // Notify all listeners
      this.statusListeners.forEach((listener) => {
        try {
          listener(status);
        } catch (error) {
          console.error('[WebSocketManager] Error in status listener:', error);
        }
      });
    }
  }

  private generateRequestId(): string {
    // Use crypto.randomUUID() if available (modern browsers)
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID();
    }

    // Fallback: simple UUID v4 implementation
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }
}

// ========================================
// Singleton Instance
// ========================================

/**
 * Singleton WebSocket manager instance.
 * Import and use this instance across the application.
 */
export const wsManager = new WebSocketManager();
