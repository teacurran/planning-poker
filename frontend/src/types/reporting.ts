/**
 * Reporting and analytics TypeScript types matching the backend API responses.
 *
 * NOTE: Backend uses snake_case JSON property names, so these types match that format.
 */

/**
 * Session summary DTO for session list.
 * Contains high-level session metrics without detailed round-by-round data.
 */
export interface SessionSummaryDTO {
  session_id: string;
  room_title: string;
  started_at: string; // ISO 8601 timestamp
  ended_at: string; // ISO 8601 timestamp
  total_stories: number;
  total_rounds: number;
  consensus_rate: number; // 0.0 to 1.0
  average_vote: number;
  participant_count: number;
  total_votes: number;
}

/**
 * Paginated session list response.
 *
 * NOTE: Backend uses has_next instead of totalPages, and total instead of totalElements.
 */
export interface SessionListResponse {
  sessions: SessionSummaryDTO[];
  page: number; // 0-indexed
  size: number;
  total: number; // Total count of sessions
  has_next: boolean; // Whether there are more pages available
}

/**
 * Query parameters for listing sessions.
 */
export interface SessionsQueryParams {
  from?: string; // ISO 8601 date (YYYY-MM-DD)
  to?: string; // ISO 8601 date (YYYY-MM-DD)
  roomId?: string;
  page?: number; // 0-indexed
  size?: number;
}
