-- ============================================================================
-- Database Performance Analysis Script
-- ============================================================================
-- Purpose: Analyze database performance during load testing
-- Usage: psql -U scrum_poker -d scrum_poker_db -f scripts/analyze-database-performance.sql
-- ============================================================================

\echo '========================================================================'
\echo 'Planning Poker Database Performance Analysis'
\echo '========================================================================'
\echo ''

-- ----------------------------------------------------------------------------
-- 1. Active Connections and Connection Pool Usage
-- ----------------------------------------------------------------------------
\echo '1. ACTIVE CONNECTIONS AND POOL USAGE'
\echo '------------------------------------------------------------------------'

SELECT
    state,
    count(*) as connection_count,
    round(100.0 * count(*) / sum(count(*)) OVER (), 2) as percentage
FROM pg_stat_activity
WHERE datname = current_database()
GROUP BY state
ORDER BY connection_count DESC;

\echo ''
\echo 'Total active connections:'
SELECT count(*) as total_connections
FROM pg_stat_activity
WHERE datname = current_database();

\echo ''
\echo 'Max connections configured:'
SHOW max_connections;

\echo ''

-- ----------------------------------------------------------------------------
-- 2. Slow Queries (requires pg_stat_statements extension)
-- ----------------------------------------------------------------------------
\echo '2. SLOW QUERIES (Top 10 by Mean Execution Time)'
\echo '------------------------------------------------------------------------'

-- Note: Requires pg_stat_statements extension
-- Enable with: CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

SELECT
    round(mean_exec_time::numeric, 2) as avg_time_ms,
    round(total_exec_time::numeric, 2) as total_time_ms,
    calls,
    round((100 * total_exec_time / sum(total_exec_time) OVER ())::numeric, 2) as percent_total_time,
    left(query, 100) as query_preview
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
ORDER BY mean_exec_time DESC
LIMIT 10;

\echo ''

-- ----------------------------------------------------------------------------
-- 3. Most Called Queries
-- ----------------------------------------------------------------------------
\echo '3. MOST FREQUENTLY CALLED QUERIES (Top 10)'
\echo '------------------------------------------------------------------------'

SELECT
    calls,
    round(mean_exec_time::numeric, 2) as avg_time_ms,
    round(total_exec_time::numeric, 2) as total_time_ms,
    left(query, 100) as query_preview
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
ORDER BY calls DESC
LIMIT 10;

\echo ''

-- ----------------------------------------------------------------------------
-- 4. Table Statistics (Sequential Scans vs Index Scans)
-- ----------------------------------------------------------------------------
\echo '4. TABLE ACCESS PATTERNS (Sequential Scans vs Index Scans)'
\echo '------------------------------------------------------------------------'

SELECT
    schemaname,
    tablename,
    seq_scan as sequential_scans,
    seq_tup_read as rows_fetched_seq_scan,
    idx_scan as index_scans,
    idx_tup_fetch as rows_fetched_index_scan,
    CASE
        WHEN seq_scan = 0 THEN 'No sequential scans'
        WHEN idx_scan = 0 THEN 'WARNING: No index scans!'
        ELSE round(100.0 * idx_scan / (seq_scan + idx_scan), 2) || '% index usage'
    END as index_usage_ratio
FROM pg_stat_user_tables
ORDER BY seq_scan DESC;

\echo ''

-- ----------------------------------------------------------------------------
-- 5. Index Usage Statistics
-- ----------------------------------------------------------------------------
\echo '5. INDEX USAGE STATISTICS'
\echo '------------------------------------------------------------------------'

SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan as times_used,
    idx_tup_read as tuples_read,
    idx_tup_fetch as tuples_fetched,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

\echo ''

-- ----------------------------------------------------------------------------
-- 6. Unused or Rarely Used Indexes
-- ----------------------------------------------------------------------------
\echo '6. UNUSED OR RARELY USED INDEXES (Less than 100 scans)'
\echo '------------------------------------------------------------------------'

SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan as times_used,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size,
    'Consider dropping if truly unused' as recommendation
FROM pg_stat_user_indexes
WHERE idx_scan < 100
ORDER BY pg_relation_size(indexrelid) DESC;

\echo ''

-- ----------------------------------------------------------------------------
-- 7. Database Size and Table Bloat
-- ----------------------------------------------------------------------------
\echo '7. DATABASE SIZE AND TABLE SIZES'
\echo '------------------------------------------------------------------------'

SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as total_size,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) as table_size,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) - pg_relation_size(schemaname||'.'||tablename)) as indexes_size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

\echo ''

-- ----------------------------------------------------------------------------
-- 8. Cache Hit Ratio (should be >99% for good performance)
-- ----------------------------------------------------------------------------
\echo '8. CACHE HIT RATIO (Target: >99%)'
\echo '------------------------------------------------------------------------'

SELECT
    'index hit rate' as cache_type,
    round((sum(idx_blks_hit) - sum(idx_blks_read)) / NULLIF(sum(idx_blks_hit), 0) * 100, 2) as hit_ratio_percent
FROM pg_statio_user_indexes
UNION ALL
SELECT
    'table hit rate' as cache_type,
    round((sum(heap_blks_hit) - sum(heap_blks_read)) / NULLIF(sum(heap_blks_hit), 0) * 100, 2) as hit_ratio_percent
FROM pg_statio_user_tables;

\echo ''

-- ----------------------------------------------------------------------------
-- 9. Lock Analysis (potential blocking)
-- ----------------------------------------------------------------------------
\echo '9. CURRENT LOCKS AND BLOCKING QUERIES'
\echo '------------------------------------------------------------------------'

SELECT
    locktype,
    database,
    relation::regclass as table_name,
    mode,
    granted,
    pid,
    left(query, 100) as query_preview
FROM pg_locks
JOIN pg_stat_activity USING (pid)
WHERE NOT granted
ORDER BY pid;

\echo ''
\echo 'If no rows returned, no blocking locks detected.'
\echo ''

-- ----------------------------------------------------------------------------
-- 10. EXPLAIN ANALYZE for Critical Queries
-- ----------------------------------------------------------------------------
\echo '10. CRITICAL QUERY ANALYSIS (EXPLAIN ANALYZE)'
\echo '------------------------------------------------------------------------'
\echo 'Running EXPLAIN ANALYZE on vote reveal query...'
\echo ''

-- Critical Query 1: Vote reveal (fetch all votes for a round)
EXPLAIN (ANALYZE, BUFFERS)
SELECT v.*
FROM vote v
WHERE v.round_id = '00000000-0000-0000-0000-000000000001'::uuid
ORDER BY v.voted_at;

\echo ''
\echo 'Expected: Index Scan or Index Only Scan using idx_vote_round_voted'
\echo 'Warning: Seq Scan indicates index not being used!'
\echo ''

-- Critical Query 2: Room listing (with pagination)
\echo 'Running EXPLAIN ANALYZE on room listing query...'
\echo ''

EXPLAIN (ANALYZE, BUFFERS)
SELECT r.*
FROM room r
WHERE r.deleted_at IS NULL
  AND r.privacy_mode = 'PUBLIC'
ORDER BY r.created_at DESC
LIMIT 20;

\echo ''
\echo 'Expected: Index Scan using idx_room_privacy_created or similar'
\echo ''

-- Critical Query 3: Participant lookup for a room
\echo 'Running EXPLAIN ANALYZE on participant lookup query...'
\echo ''

EXPLAIN (ANALYZE, BUFFERS)
SELECT p.*
FROM participant p
WHERE p.room_id = '00000000-0000-0000-0000-000000000001'::uuid
  AND p.left_at IS NULL;

\echo ''
\echo 'Expected: Index Scan using idx_participant_room_active or idx_participant_room'
\echo ''

-- ----------------------------------------------------------------------------
-- 11. Statistics Freshness (important for query planner)
-- ----------------------------------------------------------------------------
\echo '11. TABLE STATISTICS FRESHNESS'
\echo '------------------------------------------------------------------------'

SELECT
    schemaname,
    tablename,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze,
    CASE
        WHEN last_analyze IS NULL AND last_autoanalyze IS NULL THEN 'WARNING: Never analyzed!'
        WHEN GREATEST(last_analyze, last_autoanalyze) < now() - interval '7 days' THEN 'WARNING: Statistics are stale'
        ELSE 'OK'
    END as statistics_status
FROM pg_stat_user_tables
ORDER BY GREATEST(last_analyze, last_autoanalyze) NULLS FIRST;

\echo ''

-- ----------------------------------------------------------------------------
-- 12. Recommendations Summary
-- ----------------------------------------------------------------------------
\echo '12. OPTIMIZATION RECOMMENDATIONS'
\echo '------------------------------------------------------------------------'

\echo 'Based on analysis above, check for:'
\echo '  1. Connection pool exhaustion (if connections near max_connections)'
\echo '  2. Slow queries with high mean_exec_time (>100ms)'
\echo '  3. Tables with high sequential scan ratios (should be mostly index scans)'
\echo '  4. Unused indexes consuming disk space'
\echo '  5. Cache hit ratio <99% (indicates insufficient shared_buffers)'
\echo '  6. Blocking locks during high load'
\echo '  7. Stale statistics (run ANALYZE if needed)'
\echo ''
\echo 'To update statistics: ANALYZE;'
\echo 'To reset pg_stat_statements: SELECT pg_stat_statements_reset();'
\echo ''
\echo '========================================================================'
\echo 'Analysis Complete'
\echo '========================================================================'
