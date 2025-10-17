/**
 * Room-related TypeScript types matching the OpenAPI specification.
 */

export type PrivacyMode = 'PUBLIC' | 'PRIVATE';
export type VotingSystem = 'FIBONACCI' | 'T_SHIRT' | 'LINEAR' | 'CUSTOM';

/**
 * Room configuration settings.
 */
export interface RoomConfig {
  votingSystem: VotingSystem;
  allowRevote: boolean;
  autoRevealVotes: boolean;
  participantLimit: number;
  customVotingOptions?: string[];
}

/**
 * Room data transfer object.
 */
export interface RoomDTO {
  roomId: string;
  ownerId?: string | null;
  organizationId?: string | null;
  title: string;
  privacyMode: PrivacyMode;
  config: RoomConfig;
  createdAt: string;
  lastActiveAt: string;
  deletedAt?: string | null;
}

/**
 * Paginated room list response.
 */
export interface RoomListResponse {
  rooms: RoomDTO[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Request payload for creating a new room.
 */
export interface CreateRoomRequest {
  title: string;
  privacyMode?: PrivacyMode;
  config?: Partial<RoomConfig>;
}
