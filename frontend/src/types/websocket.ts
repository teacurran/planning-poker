/**
 * WebSocket protocol types matching the WebSocket Protocol Specification.
 * @see api/websocket-protocol.md
 */

// ========================================
// Connection Status Types
// ========================================

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected';

// ========================================
// Message Envelope Structure
// ========================================

export interface WebSocketMessage<T = unknown> {
  type: string;
  requestId: string;
  payload: T;
}

// ========================================
// Client → Server Message Payloads
// ========================================

export type ParticipantRole = 'HOST' | 'VOTER' | 'OBSERVER';

export interface RoomJoinPayload {
  displayName: string;
  role: ParticipantRole;
  avatarUrl?: string;
  lastEventId?: string | null; // For event replay on reconnection
}

export interface RoomLeavePayload {
  reason?: 'user_initiated' | 'timeout' | 'kicked';
}

export interface VoteCastPayload {
  cardValue: string;
}

export interface RoundStartPayload {
  storyTitle?: string;
  timerDurationSeconds?: number;
}

export interface RoundRevealPayload {
  // Empty payload
}

export interface RoundResetPayload {
  clearVotes?: boolean;
}

export interface ChatMessagePayload {
  message: string;
  replyToMessageId?: string;
}

export interface PresenceUpdatePayload {
  status: 'ready' | 'away' | 'typing';
  customMessage?: string;
}

// ========================================
// Server → Client Message Payloads
// ========================================

export interface RoomConfig {
  deckType: string;
  customDeck: string[] | null;
  timerEnabled: boolean;
  timerDurationSeconds: number;
  allowObservers: boolean;
}

export interface Participant {
  participantId: string;
  displayName: string;
  avatarUrl?: string;
  role: ParticipantRole;
  connectedAt: string;
  hasVoted: boolean;
}

export interface Round {
  roundId: string;
  roundNumber: number;
  storyTitle?: string;
  startedAt: string;
  revealed: boolean;
  revealedAt: string | null;
}

export interface RoomStatePayload {
  roomId: string;
  title: string;
  config: RoomConfig;
  participants: Participant[];
  currentRound: Round | null;
  lastEventId: string;
}

export interface ParticipantJoinedPayload {
  participantId: string;
  displayName: string;
  avatarUrl?: string;
  role: ParticipantRole;
  connectedAt: string;
}

export interface ParticipantLeftPayload {
  participantId: string;
  leftAt: string;
  reason: 'user_initiated' | 'timeout' | 'kicked';
}

export interface ParticipantDisconnectedPayload {
  participantId: string;
  disconnectedAt: string;
  gracePeriodSeconds: number;
}

export interface VoteRecordedPayload {
  participantId: string;
  votedAt: string;
  hasVoted: boolean;
}

export interface RoundStartedPayload {
  roundId: string;
  roundNumber: number;
  storyTitle?: string;
  startedAt: string;
  timerDurationSeconds?: number;
}

export interface Vote {
  participantId: string;
  displayName: string;
  cardValue: string;
  votedAt: string;
}

export interface VoteStatistics {
  average: number | null;
  median: string | null;
  mode: string | null;
  consensusReached: boolean;
  totalVotes: number;
  distribution: Record<string, number>;
}

export interface RoundRevealedPayload {
  roundId: string;
  revealedAt: string;
  votes: Vote[];
  statistics: VoteStatistics;
}

export interface RoundResetPayload {
  roundId: string;
  resetAt: string;
  votesCleared: boolean;
}

export interface ChatMessageBroadcastPayload {
  messageId: string;
  participantId: string;
  displayName: string;
  message: string;
  sentAt: string;
  replyToMessageId?: string | null;
}

export interface PresenceBroadcastPayload {
  participantId: string;
  status: 'ready' | 'away' | 'typing';
  customMessage?: string;
  updatedAt: string;
}

export interface ErrorPayload {
  code: number;
  error: string;
  message: string;
  timestamp: string;
  details?: Record<string, unknown>;
}

// ========================================
// Message Type Constants
// ========================================

export const MessageType = {
  // Client → Server
  ROOM_JOIN: 'room.join.v1',
  ROOM_LEAVE: 'room.leave.v1',
  VOTE_CAST: 'vote.cast.v1',
  ROUND_START: 'round.start.v1',
  ROUND_REVEAL: 'round.reveal.v1',
  ROUND_RESET: 'round.reset.v1',
  CHAT_MESSAGE: 'chat.message.v1',
  PRESENCE_UPDATE: 'presence.update.v1',

  // Server → Client
  ROOM_STATE: 'room.state.v1',
  ROOM_PARTICIPANT_JOINED: 'room.participant_joined.v1',
  ROOM_PARTICIPANT_LEFT: 'room.participant_left.v1',
  ROOM_PARTICIPANT_DISCONNECTED: 'room.participant_disconnected.v1',
  VOTE_RECORDED: 'vote.recorded.v1',
  ROUND_STARTED: 'round.started.v1',
  ROUND_REVEALED: 'round.revealed.v1',
  ROUND_RESET_EVENT: 'round.reset.v1',
  CHAT_MESSAGE_BROADCAST: 'chat.message.v1',
  PRESENCE_BROADCAST: 'presence.update.v1',
  ERROR: 'error.v1',
} as const;

// ========================================
// Error Code Constants
// ========================================

export const ErrorCode = {
  UNAUTHORIZED: 4000,
  ROOM_NOT_FOUND: 4001,
  INVALID_VOTE: 4002,
  FORBIDDEN: 4003,
  VALIDATION_ERROR: 4004,
  INVALID_STATE: 4005,
  RATE_LIMIT_EXCEEDED: 4006,
  ROOM_FULL: 4007,
  POLICY_VIOLATION: 4008,
  INTERNAL_SERVER_ERROR: 4999,
} as const;

// ========================================
// Type Helpers
// ========================================

export type MessageHandler<T = unknown> = (payload: T) => void;
