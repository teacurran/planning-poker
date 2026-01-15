-- ============================================================================
-- Planning Poker - Partition Maintenance Migration
-- Version: V7
-- Description: Ensures SessionHistory and AuditLog have partitions for the
--              current month and the next three months.
-- ============================================================================

DO $$
DECLARE
    base_month DATE := date_trunc('month', CURRENT_DATE);
    partition_start DATE;
    partition_end DATE;
    partition_suffix TEXT;
    partition_label TEXT;
BEGIN
    FOR month_offset IN 0..3 LOOP
        partition_start := base_month + make_interval(months => month_offset);
        partition_end := base_month + make_interval(months => (month_offset + 1));
        partition_suffix := to_char(partition_start, 'YYYY_MM');
        partition_label := to_char(partition_start, 'FMMonth YYYY');

        IF to_regclass('public.session_history_' || partition_suffix) IS NULL THEN
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF session_history FOR VALUES FROM (%L) TO (%L)',
                'session_history_' || partition_suffix,
                partition_start,
                partition_end
            );

            EXECUTE format(
                'COMMENT ON TABLE %I IS %L',
                'session_history_' || partition_suffix,
                'SessionHistory partition for ' || partition_label
            );
        END IF;

        IF to_regclass('public.audit_log_' || partition_suffix) IS NULL THEN
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF audit_log FOR VALUES FROM (%L) TO (%L)',
                'audit_log_' || partition_suffix,
                partition_start,
                partition_end
            );

            EXECUTE format(
                'COMMENT ON TABLE %I IS %L',
                'audit_log_' || partition_suffix,
                'AuditLog partition for ' || partition_label
            );
        END IF;
    END LOOP;
END $$ LANGUAGE plpgsql;
