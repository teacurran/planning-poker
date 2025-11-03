-- ============================================================================
-- Planning Poker - Webhook Event Log Migration
-- Version: V4
-- Description: Creates webhook_event_log table for Stripe webhook idempotency
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ENUM Type for Webhook Event Status
-- ----------------------------------------------------------------------------

CREATE TYPE webhook_event_status_enum AS ENUM ('PROCESSED', 'FAILED');

COMMENT ON TYPE webhook_event_status_enum IS 'Webhook event processing outcome status';

-- ----------------------------------------------------------------------------
-- Webhook Event Log Table
-- ----------------------------------------------------------------------------

CREATE TABLE webhook_event_log (
    event_id VARCHAR(100) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status webhook_event_status_enum NOT NULL,
    CONSTRAINT uq_webhook_event_id UNIQUE (event_id)
);

COMMENT ON TABLE webhook_event_log IS 'Idempotency log for Stripe webhook events to prevent duplicate processing';
COMMENT ON COLUMN webhook_event_log.event_id IS 'Stripe event ID (e.g., evt_1234567890abcdefghijklmnop)';
COMMENT ON COLUMN webhook_event_log.event_type IS 'Stripe event type (e.g., customer.subscription.created)';
COMMENT ON COLUMN webhook_event_log.processed_at IS 'Timestamp when event was processed';
COMMENT ON COLUMN webhook_event_log.status IS 'Processing outcome: PROCESSED (success) or FAILED (error logged)';

-- ----------------------------------------------------------------------------
-- Indexes for Performance
-- ----------------------------------------------------------------------------

CREATE INDEX idx_webhook_event_log_processed_at
    ON webhook_event_log (processed_at DESC);

CREATE INDEX idx_webhook_event_log_event_type
    ON webhook_event_log (event_type);

CREATE INDEX idx_webhook_event_log_status
    ON webhook_event_log (status);

COMMENT ON INDEX idx_webhook_event_log_processed_at IS 'Index for cleanup queries and date range filtering';
COMMENT ON INDEX idx_webhook_event_log_event_type IS 'Index for event type analysis and monitoring';
COMMENT ON INDEX idx_webhook_event_log_status IS 'Index for failed event monitoring';
