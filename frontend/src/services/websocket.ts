/**
 * WebSocket connection manager for Planning Poker real-time communication.
 *
 * Features:
 * - Connection lifecycle management (connect, disconnect, reconnect)
 * - Exponential backoff reconnection strategy (1s, 2s, 4s, 8s, max 16s)
 * - Message serialization and deserialization
 * - Event handler registration and dispatching
 * - Application-level ping/pong heartbeat (ping.v1 / pong.v1)
 * - Connection status tracking
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

const getWebSocketBaseUrl = (): string => {
  const envUrl = import.meta.env.VITE_WS_BASE_URL;
  if (envUrl) {
    return envUrl;
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const hostname = window.location.hostname;

  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    return `${protocol}//${hostname}:8080`;
  }

  return `${protocol}//${hostname}`;
};

const WS_BASE_URL = getWebSocketBaseUrl();
const PING_INTERVAL = 25000; // 25 seconds - send ping.v1 every 25s
const JOIN_TIMEOUT = 10000; // 10 seconds
const MAX_RECONNECT_DELAY = 16000;
const INITIAL_RECONNECT_DELAY = 1000;

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
  private pingInterval: ReturnType<typeof setInterval> | null = null;
  private joinTimeout: ReturnType<typeof setTimeout> | null = null;

  private eventHandlers = new Map<string, Set<MessageHandler>>();
  private statusListeners = new Set<(status: ConnectionStatus) => void>();
  private lastEventId: string | null = null;

  private userDisplayName: string | null = null;
  private userRole: 'HOST' | 'VOTER' | 'OBSERVER' = 'VOTER';

  /**
   * Connect to a room's WebSocket endpoint.
   */
  connect(
    roomId: string,
    token: string,
    displayName: string,
    role: 'HOST' | 'VOTER' | 'OBSERVER' = 'VOTER'
  ): void {
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
   */
  disconnect(): void {
    this.clearTimers();

    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      try {
        this.send('room.leave.v1', { reason: 'user_initiated' });
      } catch {
        // Ignore send errors during disconnect
      }
    }

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

    this.ws.send(JSON.stringify(message));
    return requestId;
  }

  /**
   * Register an event handler for a specific message type.
   */
  on<T = unknown>(messageType: string, handler: MessageHandler<T>): () => void {
    if (!this.eventHandlers.has(messageType)) {
      this.eventHandlers.set(messageType, new Set());
    }

    const handlers = this.eventHandlers.get(messageType)!;
    handlers.add(handler as MessageHandler);

    return () => {
      handlers.delete(handler as MessageHandler);
      if (handlers.size === 0) {
        this.eventHandlers.delete(messageType);
      }
    };
  }

  /**
   * Register a connection status listener.
   */
  onStatusChange(listener: (status: ConnectionStatus) => void): () => void {
    this.statusListeners.add(listener);
    listener(this.connectionStatus);

    return () => {
      this.statusListeners.delete(listener);
    };
  }

  getStatus(): ConnectionStatus {
    return this.connectionStatus;
  }

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
    this.reconnectAttempts = 0;

    // Start application-level ping
    this.startPing();

    // Send room.join.v1 message immediately
    this.sendJoinMessage();
  }

  private handleMessage(event: MessageEvent): void {
    try {
      const message = JSON.parse(event.data) as WebSocketMessage;

      // Track lastEventId for reconnection replay
      if (message.payload && typeof message.payload === 'object' && 'lastEventId' in message.payload) {
        this.lastEventId = (message.payload as { lastEventId: string }).lastEventId;
      }

      // Handle pong.v1 silently (just confirms connection is alive)
      if (message.type === 'pong.v1') {
        return;
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

    // Reconnect if closure was unexpected
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
      lastEventId: this.lastEventId,
    };

    try {
      this.send('room.join.v1', payload);
    } catch (error) {
      console.error('[WebSocketManager] Failed to send join message:', error);
    }

    this.joinTimeout = setTimeout(() => {
      if (this.connectionStatus === 'connected') {
        console.warn('[WebSocketManager] Join message timeout');
      }
    }, JOIN_TIMEOUT);
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
    }

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

  /**
   * Send application-level ping.v1 messages every 25 seconds.
   * The backend responds with pong.v1 and tracks liveness.
   */
  private startPing(): void {
    if (this.pingInterval) {
      clearInterval(this.pingInterval);
    }

    this.pingInterval = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        try {
          this.send('ping.v1', { timestamp: Date.now() });
        } catch {
          // Connection may have dropped
        }
      }
    }, PING_INTERVAL);
  }

  private clearTimers(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.pingInterval) {
      clearInterval(this.pingInterval);
      this.pingInterval = null;
    }

    if (this.joinTimeout) {
      clearTimeout(this.joinTimeout);
      this.joinTimeout = null;
    }
  }

  private setConnectionStatus(status: ConnectionStatus): void {
    if (this.connectionStatus !== status) {
      this.connectionStatus = status;

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
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID();
    }

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

export const wsManager = new WebSocketManager();
