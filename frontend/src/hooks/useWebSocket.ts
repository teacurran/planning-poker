/**
 * React hook for WebSocket connection management.
 *
 * Provides:
 * - Connection status tracking
 * - Automatic connection/disconnection based on room ID
 * - Message sending interface
 * - Integration with auth store for token
 *
 * @example
 * ```tsx
 * function RoomPage({ roomId }: { roomId: string }) {
 *   const { connectionStatus, send, isConnected } = useWebSocket(roomId);
 *
 *   const handleVote = (cardValue: string) => {
 *     send('vote.cast.v1', { cardValue });
 *   };
 *
 *   return (
 *     <div>
 *       <div>Status: {connectionStatus}</div>
 *       <button onClick={() => handleVote('5')} disabled={!isConnected}>
 *         Vote 5
 *       </button>
 *     </div>
 *   );
 * }
 * ```
 */

import { useEffect, useState, useCallback, useRef } from 'react';
import { wsManager } from '@/services/websocket';
import { useAuthStore } from '@/stores/authStore';
import { useRoomStore } from '@/stores/roomStore';
import type { ConnectionStatus } from '@/types/websocket';
import {
  MessageType,
  type RoomStatePayload,
  type ParticipantJoinedPayload,
  type ParticipantLeftPayload,
  type VoteRecordedPayload,
  type RoundStartedPayload,
  type RoundRevealedPayload,
  type RoundResetPayload,
  type ErrorPayload,
} from '@/types/websocket';

// ========================================
// Hook Interface
// ========================================

interface UseWebSocketResult {
  /** Current connection status */
  connectionStatus: ConnectionStatus;

  /** Whether currently connected */
  isConnected: boolean;

  /** Send a message to the server */
  send: <T = unknown>(type: string, payload: T) => string | null;

  /** Last error (if any) */
  error: ErrorPayload | null;
}

// ========================================
// Hook Implementation
// ========================================

/**
 * Hook for managing WebSocket connection to a room.
 *
 * @param roomId - Room ID to connect to (null to disconnect)
 * @returns WebSocket connection interface
 */
export function useWebSocket(roomId: string | null): UseWebSocketResult {
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('disconnected');
  const [error, setError] = useState<ErrorPayload | null>(null);

  // Get auth token from auth store
  const accessToken = useAuthStore((state) => state.accessToken);
  const user = useAuthStore((state) => state.user);

  // Get room store actions
  const initializeRoomState = useRoomStore((state) => state.initializeRoomState);
  const clearRoomState = useRoomStore((state) => state.clearRoomState);
  const addParticipant = useRoomStore((state) => state.addParticipant);
  const removeParticipant = useRoomStore((state) => state.removeParticipant);
  const updateParticipantVoteStatus = useRoomStore((state) => state.updateParticipantVoteStatus);
  const startRound = useRoomStore((state) => state.startRound);
  const setRevealedVotes = useRoomStore((state) => state.setRevealedVotes);
  const resetRound = useRoomStore((state) => state.resetRound);

  // Track if we've already set up event handlers to avoid duplicates
  const handlersSetup = useRef(false);

  // ========================================
  // Event Handlers
  // ========================================

  useEffect(() => {
    // Only set up handlers once
    if (handlersSetup.current) return;
    handlersSetup.current = true;

    // Register event handlers for WebSocket messages
    const unsubscribers: Array<() => void> = [];

    // Handle room.state.v1 - Initial state snapshot
    unsubscribers.push(
      wsManager.on<RoomStatePayload>(MessageType.ROOM_STATE, (payload) => {
        console.log('[useWebSocket] Received room state:', payload);
        initializeRoomState(payload);
        setError(null); // Clear any previous errors
      })
    );

    // Handle room.participant_joined.v1 - New participant joined
    unsubscribers.push(
      wsManager.on<ParticipantJoinedPayload>(MessageType.ROOM_PARTICIPANT_JOINED, (payload) => {
        console.log('[useWebSocket] Participant joined:', payload);
        addParticipant({
          participantId: payload.participantId,
          displayName: payload.displayName,
          avatarUrl: payload.avatarUrl,
          role: payload.role,
          connectedAt: payload.connectedAt,
          hasVoted: false,
        });
      })
    );

    // Handle room.participant_left.v1 - Participant left
    unsubscribers.push(
      wsManager.on<ParticipantLeftPayload>(MessageType.ROOM_PARTICIPANT_LEFT, (payload) => {
        console.log('[useWebSocket] Participant left:', payload);
        removeParticipant(payload.participantId);
      })
    );

    // Handle vote.recorded.v1 - Vote recorded (broadcast)
    unsubscribers.push(
      wsManager.on<VoteRecordedPayload>(MessageType.VOTE_RECORDED, (payload) => {
        console.log('[useWebSocket] Vote recorded:', payload);
        updateParticipantVoteStatus(payload.participantId, payload.hasVoted);
      })
    );

    // Handle round.started.v1 - New round started
    unsubscribers.push(
      wsManager.on<RoundStartedPayload>(MessageType.ROUND_STARTED, (payload) => {
        console.log('[useWebSocket] Round started:', payload);
        startRound({
          roundId: payload.roundId,
          roundNumber: payload.roundNumber,
          storyTitle: payload.storyTitle,
          startedAt: payload.startedAt,
          revealed: false,
          revealedAt: null,
        });
      })
    );

    // Handle round.revealed.v1 - Votes revealed
    unsubscribers.push(
      wsManager.on<RoundRevealedPayload>(MessageType.ROUND_REVEALED, (payload) => {
        console.log('[useWebSocket] Round revealed:', payload);
        setRevealedVotes(payload.votes, payload.statistics);
      })
    );

    // Handle round.reset.v1 - Round reset
    unsubscribers.push(
      wsManager.on<RoundResetPayload>(MessageType.ROUND_RESET_EVENT, (payload) => {
        console.log('[useWebSocket] Round reset:', payload);
        resetRound();
      })
    );

    // Handle error.v1 - Error messages
    unsubscribers.push(
      wsManager.on<ErrorPayload>(MessageType.ERROR, (payload) => {
        console.error('[useWebSocket] Error received:', payload);
        setError(payload);
      })
    );

    // Cleanup on unmount
    return () => {
      unsubscribers.forEach((unsub) => unsub());
      handlersSetup.current = false;
    };
  }, [
    initializeRoomState,
    clearRoomState,
    addParticipant,
    removeParticipant,
    updateParticipantVoteStatus,
    startRound,
    setRevealedVotes,
    resetRound,
  ]);

  // ========================================
  // Connection Management
  // ========================================

  useEffect(() => {
    // Subscribe to connection status changes
    const unsubscribe = wsManager.onStatusChange((status) => {
      setConnectionStatus(status);
    });

    return unsubscribe;
  }, []);

  useEffect(() => {
    // Connect when we have roomId and token
    if (roomId && accessToken && user) {
      console.log('[useWebSocket] Connecting to room:', roomId);

      // Use user's display name from auth store
      const displayName = user.displayName || user.email;
      const role = 'VOTER'; // Default role - could be determined based on room ownership

      wsManager.connect(roomId, accessToken, displayName, role);
    } else {
      // Disconnect if any required data is missing
      if (wsManager.getStatus() !== 'disconnected') {
        console.log('[useWebSocket] Disconnecting (missing roomId or token)');
        wsManager.disconnect();
        clearRoomState();
      }
    }

    // Cleanup on unmount or when roomId/token changes
    return () => {
      console.log('[useWebSocket] Cleanup - disconnecting');
      wsManager.disconnect();
      clearRoomState();
    };
  }, [roomId, accessToken, user, clearRoomState]);

  // ========================================
  // Send Function
  // ========================================

  const send = useCallback(
    <T = unknown>(type: string, payload: T): string | null => {
      try {
        return wsManager.send(type, payload);
      } catch (error) {
        console.error('[useWebSocket] Failed to send message:', error);
        return null;
      }
    },
    []
  );

  // ========================================
  // Return Interface
  // ========================================

  return {
    connectionStatus,
    isConnected: connectionStatus === 'connected',
    send,
    error,
  };
}
