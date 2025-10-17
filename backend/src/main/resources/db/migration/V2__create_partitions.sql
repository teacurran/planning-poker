-- ============================================================================
-- Planning Poker - Partition Creation Migration
-- Version: V2
-- Description: Creates monthly partitions for SessionHistory and AuditLog tables
-- ============================================================================

-- ----------------------------------------------------------------------------
-- SessionHistory Partitions (monthly range partitions on started_at)
-- ----------------------------------------------------------------------------
-- Creates partitions for current month and next 3 months (4 partitions total)

CREATE TABLE session_history_2025_10 PARTITION OF session_history
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');

CREATE TABLE session_history_2025_11 PARTITION OF session_history
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');

CREATE TABLE session_history_2025_12 PARTITION OF session_history
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

CREATE TABLE session_history_2026_01 PARTITION OF session_history
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

COMMENT ON TABLE session_history_2025_10 IS 'SessionHistory partition for October 2025';
COMMENT ON TABLE session_history_2025_11 IS 'SessionHistory partition for November 2025';
COMMENT ON TABLE session_history_2025_12 IS 'SessionHistory partition for December 2025';
COMMENT ON TABLE session_history_2026_01 IS 'SessionHistory partition for January 2026';

-- ----------------------------------------------------------------------------
-- AuditLog Partitions (monthly range partitions on timestamp)
-- ----------------------------------------------------------------------------

CREATE TABLE audit_log_2025_10 PARTITION OF audit_log
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');

CREATE TABLE audit_log_2025_11 PARTITION OF audit_log
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');

CREATE TABLE audit_log_2025_12 PARTITION OF audit_log
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

CREATE TABLE audit_log_2026_01 PARTITION OF audit_log
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

COMMENT ON TABLE audit_log_2025_10 IS 'AuditLog partition for October 2025';
COMMENT ON TABLE audit_log_2025_11 IS 'AuditLog partition for November 2025';
COMMENT ON TABLE audit_log_2025_12 IS 'AuditLog partition for December 2025';
COMMENT ON TABLE audit_log_2026_01 IS 'AuditLog partition for January 2026';

-- ----------------------------------------------------------------------------
-- Partition Management Notes
-- ----------------------------------------------------------------------------
--
-- Future partitions must be created before the current month ends to avoid
-- insert failures. Consider using one of these approaches:
--
-- Option 1: Automated partition creation with pg_partman extension:
--   SELECT partman.create_parent('public.session_history', 'started_at', 'native', 'monthly');
--   SELECT partman.create_parent('public.audit_log', 'timestamp', 'native', 'monthly');
--
-- Option 2: Scheduled job (cron/pg_cron) to create partitions:
--   CREATE OR REPLACE FUNCTION create_next_month_partitions()
--   RETURNS void AS $$
--   DECLARE
--       next_month DATE := date_trunc('month', CURRENT_DATE + interval '1 month');
--       month_after DATE := next_month + interval '1 month';
--       table_name TEXT;
--   BEGIN
--       table_name := 'session_history_' || to_char(next_month, 'YYYY_MM');
--       EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF session_history FOR VALUES FROM (%L) TO (%L)',
--           table_name, next_month, month_after);
--
--       table_name := 'audit_log_' || to_char(next_month, 'YYYY_MM');
--       EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF audit_log FOR VALUES FROM (%L) TO (%L)',
--           table_name, next_month, month_after);
--   END;
--   $$ LANGUAGE plpgsql;
--
-- Option 3: Manual creation via migration scripts before month boundary
--
-- Data retention: Old partitions can be dropped or archived:
--   DROP TABLE session_history_2024_01;  -- Drops all data from January 2024
--   ALTER TABLE session_history DETACH PARTITION session_history_2024_01;  -- Detach for archival
-- ----------------------------------------------------------------------------
