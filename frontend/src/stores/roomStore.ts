/**
 * Room state management using Zustand.
 * Manages real-time room state synchronized via WebSocket events.
 */

import { create } from 'zustand';
import type {
  RoomStatePayload,
  Participant,
  Round,
  RoomConfig,
  Vote,
  VoteStatistics,
} from '@/types/websocket';

// ========================================
// State Interface
// ========================================

interface RoomState {
  // Room Information
  roomId: string | null;
  title: string | null;
  config: RoomConfig | null;

  // Participants
  participants: Map<string, Participant>;

  // Current Round
  currentRound: Round | null;

  // Revealed Votes (only populated after round.revealed.v1)
  revealedVotes: Vote[] | null;
  statistics: VoteStatistics | null;

  // Loading State
  isLoading: boolean;

  // ========================================
  // Actions
  // ========================================

  /**
   * Initialize room state from room.state.v1 message.
   */
  initializeRoomState: (state: RoomStatePayload) => void;

  /**
   * Clear all room state (on disconnect or leave).
   */
  clearRoomState: () => void;

  /**
   * Add a participant to the room.
   */
  addParticipant: (participant: Participant) => void;

  /**
   * Remove a participant from the room.
   */
  removeParticipant: (participantId: string) => void;

  /**
   * Update a participant's vote status.
   */
  updateParticipantVoteStatus: (participantId: string, hasVoted: boolean) => void;

  /**
   * Start a new round.
   */
  startRound: (round: Round) => void;

  /**
   * Set revealed votes and statistics.
   */
  setRevealedVotes: (votes: Vote[], stats: VoteStatistics) => void;

  /**
   * Reset the current round (clear votes).
   */
  resetRound: () => void;

  /**
   * Update room configuration.
   */
  updateConfig: (config: Partial<RoomConfig>) => void;

  /**
   * Get a participant by ID.
   */
  getParticipant: (participantId: string) => Participant | undefined;

  /**
   * Get all participants as an array.
   */
  getParticipantsArray: () => Participant[];

  /**
   * Get the current user's participant data.
   * Note: Requires comparing with auth store's user ID.
   */
  getCurrentParticipant: (userId: string) => Participant | undefined;
}

// ========================================
// Initial State
// ========================================

const initialState = {
  roomId: null,
  title: null,
  config: null,
  participants: new Map<string, Participant>(),
  currentRound: null,
  revealedVotes: null,
  statistics: null,
  isLoading: false,
};

// ========================================
// Zustand Store
// ========================================

export const useRoomStore = create<RoomState>((set, get) => ({
  ...initialState,

  // ========================================
  // Action Implementations
  // ========================================

  initializeRoomState: (state: RoomStatePayload) => {
    const participantsMap = new Map<string, Participant>();
    state.participants.forEach((p) => {
      participantsMap.set(p.participantId, p);
    });

    set({
      roomId: state.roomId,
      title: state.title,
      config: state.config,
      participants: participantsMap,
      currentRound: state.currentRound,
      revealedVotes: null, // Clear revealed votes on state initialization
      statistics: null,
      isLoading: false,
    });

    console.log('[RoomStore] Room state initialized:', state.roomId);
  },

  clearRoomState: () => {
    set(initialState);
    console.log('[RoomStore] Room state cleared');
  },

  addParticipant: (participant: Participant) => {
    set((state) => {
      const participants = new Map(state.participants);
      participants.set(participant.participantId, participant);
      return { participants };
    });

    console.log('[RoomStore] Participant added:', participant.participantId);
  },

  removeParticipant: (participantId: string) => {
    set((state) => {
      const participants = new Map(state.participants);
      participants.delete(participantId);
      return { participants };
    });

    console.log('[RoomStore] Participant removed:', participantId);
  },

  updateParticipantVoteStatus: (participantId: string, hasVoted: boolean) => {
    set((state) => {
      const participants = new Map(state.participants);
      const participant = participants.get(participantId);

      if (participant) {
        participants.set(participantId, {
          ...participant,
          hasVoted,
        });
      }

      return { participants };
    });

    console.log('[RoomStore] Participant vote status updated:', participantId, hasVoted);
  },

  startRound: (round: Round) => {
    set({
      currentRound: round,
      revealedVotes: null, // Clear previous revealed votes
      statistics: null,
    });

    // Reset all participants' vote status
    set((state) => {
      const participants = new Map(state.participants);
      participants.forEach((participant, id) => {
        participants.set(id, {
          ...participant,
          hasVoted: false,
        });
      });
      return { participants };
    });

    console.log('[RoomStore] Round started:', round.roundId);
  },

  setRevealedVotes: (votes: Vote[], stats: VoteStatistics) => {
    set({
      revealedVotes: votes,
      statistics: stats,
    });

    // Update current round to mark as revealed
    set((state) => {
      if (state.currentRound) {
        return {
          currentRound: {
            ...state.currentRound,
            revealed: true,
            revealedAt: new Date().toISOString(),
          },
        };
      }
      return {};
    });

    console.log('[RoomStore] Votes revealed:', votes.length, 'votes');
  },

  resetRound: () => {
    set({
      revealedVotes: null,
      statistics: null,
    });

    // Reset all participants' vote status
    set((state) => {
      const participants = new Map(state.participants);
      participants.forEach((participant, id) => {
        participants.set(id, {
          ...participant,
          hasVoted: false,
        });
      });

      // Reset revealed status on current round
      const currentRound = state.currentRound
        ? {
            ...state.currentRound,
            revealed: false,
            revealedAt: null,
          }
        : null;

      return { participants, currentRound };
    });

    console.log('[RoomStore] Round reset');
  },

  updateConfig: (config: Partial<RoomConfig>) => {
    set((state) => ({
      config: state.config ? { ...state.config, ...config } : null,
    }));

    console.log('[RoomStore] Config updated:', config);
  },

  getParticipant: (participantId: string) => {
    return get().participants.get(participantId);
  },

  getParticipantsArray: () => {
    return Array.from(get().participants.values());
  },

  getCurrentParticipant: (userId: string) => {
    // Note: participantId might be different from userId
    // This is a helper method - implementation depends on how participant IDs are structured
    return Array.from(get().participants.values()).find(
      (p) => p.participantId === userId
    );
  },
}));
