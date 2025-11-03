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

/**
 * Individual vote detail within a round.
 */
export interface VoteDetailDTO {
  participant_name: string;
  card_value: string;
  voted_at: string; // ISO 8601 timestamp
}

/**
 * Round detail with individual votes.
 */
export interface RoundDetailDTO {
  round_number: number;
  story_title: string;
  started_at: string; // ISO 8601 timestamp
  revealed_at: string; // ISO 8601 timestamp
  votes: VoteDetailDTO[];
  average: number;
  median: number;
  consensus_reached: boolean;
}

/**
 * Detailed session report with round-by-round breakdown.
 * Pro/Enterprise tier only. Free tier users receive 403 error.
 */
export interface DetailedSessionReportDTO {
  session_id: string;
  room_title: string;
  started_at: string; // ISO 8601 timestamp
  ended_at: string; // ISO 8601 timestamp
  total_stories: number;
  total_rounds: number;
  consensus_rate: number; // 0.0 to 1.0
  average_vote: number;
  participant_count: number;
  participants: string[]; // Array of participant names
  rounds: RoundDetailDTO[]; // Pro tier only - round details
  user_consistency_map: Record<string, number>; // Pro tier only - username -> variance
}

/**
 * Export format types.
 */
export type ExportFormat = 'CSV' | 'PDF';

/**
 * Request body for creating export job.
 */
export interface ExportJobRequest {
  session_id: string;
  format: ExportFormat;
}

/**
 * Response from creating export job.
 */
export interface ExportJobResponse {
  job_id: string;
}

/**
 * Export job status.
 */
export type JobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

/**
 * Response from polling job status.
 */
export interface JobStatusResponse {
  job_id: string;
  status: JobStatus;
  download_url?: string; // Present when status is COMPLETED
  error_message?: string; // Present when status is FAILED
  created_at: string; // ISO 8601 timestamp
  completed_at?: string; // ISO 8601 timestamp, present when COMPLETED or FAILED
}
