-- ============================================================================
-- Planning Poker - Export Job Tracking Migration
-- Version: V5
-- Description: Creates export_job table for tracking CSV/PDF report generation
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ENUM Type for Job Status
-- ----------------------------------------------------------------------------

CREATE TYPE job_status_enum AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');

COMMENT ON TYPE job_status_enum IS 'Export job processing status lifecycle';

-- ----------------------------------------------------------------------------
-- Export Job Table
-- ----------------------------------------------------------------------------

CREATE TABLE export_job (
    job_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    format VARCHAR(10) NOT NULL CHECK (format IN ('CSV', 'PDF')),
    status job_status_enum NOT NULL DEFAULT 'PENDING',
    download_url TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_export_job_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

COMMENT ON TABLE export_job IS 'Tracks asynchronous CSV/PDF export job status and download URLs';
COMMENT ON COLUMN export_job.job_id IS 'Unique job identifier (UUID)';
COMMENT ON COLUMN export_job.session_id IS 'Reference to session_history composite key (no FK due to composite key)';
COMMENT ON COLUMN export_job.user_id IS 'User who requested the export';
COMMENT ON COLUMN export_job.format IS 'Export format: CSV or PDF';
COMMENT ON COLUMN export_job.status IS 'Job lifecycle: PENDING → PROCESSING → COMPLETED/FAILED';
COMMENT ON COLUMN export_job.download_url IS 'S3 signed URL for file download (set when status=COMPLETED)';
COMMENT ON COLUMN export_job.error_message IS 'Error details if status=FAILED';
COMMENT ON COLUMN export_job.created_at IS 'Job creation timestamp';
COMMENT ON COLUMN export_job.processing_started_at IS 'Timestamp when worker started processing';
COMMENT ON COLUMN export_job.completed_at IS 'Timestamp when job completed successfully';
COMMENT ON COLUMN export_job.failed_at IS 'Timestamp when job failed';

-- ----------------------------------------------------------------------------
-- Indexes for Performance
-- ----------------------------------------------------------------------------

CREATE INDEX idx_export_job_status_created
    ON export_job (status, created_at DESC);

CREATE INDEX idx_export_job_user_created
    ON export_job (user_id, created_at DESC);

CREATE INDEX idx_export_job_session
    ON export_job (session_id);

COMMENT ON INDEX idx_export_job_status_created IS 'Index for job queue queries (find PENDING jobs ordered by creation time)';
COMMENT ON INDEX idx_export_job_user_created IS 'Index for user job history lookups';
COMMENT ON INDEX idx_export_job_session IS 'Index for session-specific export job lookups';
