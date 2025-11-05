#!/bin/bash

# ============================================================================
# Redis Performance Analysis Script
# ============================================================================
# Purpose: Monitor Redis performance during load testing
# Usage: ./scripts/analyze-redis-performance.sh
# ============================================================================

set -e

echo "========================================================================"
echo "Planning Poker Redis Performance Analysis"
echo "========================================================================"
echo ""

# Configuration
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_CLI="redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT}"

# Check if redis-cli is available
if ! command -v redis-cli &> /dev/null; then
    echo "ERROR: redis-cli not found. Please install Redis CLI tools."
    exit 1
fi

# Check if Redis is accessible
if ! ${REDIS_CLI} ping &> /dev/null; then
    echo "ERROR: Cannot connect to Redis at ${REDIS_HOST}:${REDIS_PORT}"
    exit 1
fi

echo "Connected to Redis at ${REDIS_HOST}:${REDIS_PORT}"
echo ""

# ----------------------------------------------------------------------------
# 1. Server Information
# ----------------------------------------------------------------------------
echo "1. REDIS SERVER INFORMATION"
echo "------------------------------------------------------------------------"
${REDIS_CLI} INFO server | grep -E "redis_version|redis_mode|os|arch_bits|process_id|uptime_in_seconds"
echo ""

# ----------------------------------------------------------------------------
# 2. Memory Usage
# ----------------------------------------------------------------------------
echo "2. MEMORY USAGE"
echo "------------------------------------------------------------------------"
${REDIS_CLI} INFO memory | grep -E "used_memory_human|used_memory_peak_human|used_memory_rss_human|mem_fragmentation_ratio|maxmemory_human|maxmemory_policy"
echo ""

# Calculate memory usage percentage
USED_MEMORY=$(${REDIS_CLI} INFO memory | grep "^used_memory:" | cut -d: -f2 | tr -d '\r')
MAX_MEMORY=$(${REDIS_CLI} INFO memory | grep "^maxmemory:" | cut -d: -f2 | tr -d '\r')

if [ "$MAX_MEMORY" != "0" ]; then
    MEMORY_PCT=$(awk "BEGIN {printf \"%.2f\", ($USED_MEMORY / $MAX_MEMORY) * 100}")
    echo "Memory Usage: ${MEMORY_PCT}%"

    if (( $(echo "$MEMORY_PCT > 90" | bc -l) )); then
        echo "WARNING: Memory usage is above 90%!"
    fi
else
    echo "Note: No maxmemory limit configured (maxmemory=0)"
fi
echo ""

# ----------------------------------------------------------------------------
# 3. Stats - Hit Rate
# ----------------------------------------------------------------------------
echo "3. CACHE HIT RATE (Target: >90%)"
echo "------------------------------------------------------------------------"
STATS=$(${REDIS_CLI} INFO stats | grep -E "keyspace_hits|keyspace_misses")
HITS=$(echo "$STATS" | grep keyspace_hits | cut -d: -f2 | tr -d '\r')
MISSES=$(echo "$STATS" | grep keyspace_misses | cut -d: -f2 | tr -d '\r')

echo "$STATS"

if [ "$HITS" != "" ] && [ "$MISSES" != "" ]; then
    TOTAL=$((HITS + MISSES))
    if [ "$TOTAL" -gt 0 ]; then
        HIT_RATE=$(awk "BEGIN {printf \"%.2f\", ($HITS / $TOTAL) * 100}")
        echo "Hit Rate: ${HIT_RATE}%"

        if (( $(echo "$HIT_RATE < 90" | bc -l) )); then
            echo "WARNING: Hit rate is below 90%!"
            echo "  - Check cache TTL configuration"
            echo "  - Review cache key patterns"
            echo "  - Consider cache warming strategy"
        else
            echo "Hit rate is healthy (>90%)"
        fi
    else
        echo "No cache operations yet"
    fi
fi
echo ""

# ----------------------------------------------------------------------------
# 4. Commands Statistics
# ----------------------------------------------------------------------------
echo "4. COMMAND STATISTICS"
echo "------------------------------------------------------------------------"
${REDIS_CLI} INFO stats | grep -E "total_commands_processed|instantaneous_ops_per_sec|total_net_input_bytes|total_net_output_bytes"
echo ""

# ----------------------------------------------------------------------------
# 5. Connected Clients
# ----------------------------------------------------------------------------
echo "5. CONNECTED CLIENTS"
echo "------------------------------------------------------------------------"
${REDIS_CLI} INFO clients | grep -E "connected_clients|blocked_clients|client_recent_max_input_buffer|client_recent_max_output_buffer"
echo ""

CLIENT_COUNT=$(${REDIS_CLI} INFO clients | grep "^connected_clients:" | cut -d: -f2 | tr -d '\r')
echo "Current connected clients: ${CLIENT_COUNT}"

if [ "$CLIENT_COUNT" -gt 1000 ]; then
    echo "WARNING: High number of connected clients!"
    echo "  - Check application connection pooling configuration"
    echo "  - Verify connections are being released properly"
fi
echo ""

# ----------------------------------------------------------------------------
# 6. Pub/Sub Statistics (Critical for WebSocket Broadcasting)
# ----------------------------------------------------------------------------
echo "6. PUB/SUB STATISTICS (WebSocket Broadcasting)"
echo "------------------------------------------------------------------------"
${REDIS_CLI} INFO stats | grep -E "pubsub_channels|pubsub_patterns"
echo ""

PUBSUB_CHANNELS=$(${REDIS_CLI} PUBSUB CHANNELS | wc -l)
echo "Active Pub/Sub channels: ${PUBSUB_CHANNELS}"

if [ "$PUBSUB_CHANNELS" -gt 0 ]; then
    echo "Pub/Sub channels (top 10):"
    ${REDIS_CLI} PUBSUB CHANNELS | head -10
fi
echo ""

# ----------------------------------------------------------------------------
# 7. Slow Log (Commands taking longer than slowlog-log-slower-than)
# ----------------------------------------------------------------------------
echo "7. SLOW LOG (Last 10 slow commands)"
echo "------------------------------------------------------------------------"
SLOWLOG_COUNT=$(${REDIS_CLI} SLOWLOG LEN)
echo "Total slow log entries: ${SLOWLOG_COUNT}"
echo ""

if [ "$SLOWLOG_COUNT" -gt 0 ]; then
    echo "Recent slow commands:"
    ${REDIS_CLI} SLOWLOG GET 10
    echo ""

    if [ "$SLOWLOG_COUNT" -gt 100 ]; then
        echo "WARNING: High number of slow commands detected!"
        echo "  - Review commands in slow log"
        echo "  - Optimize O(N) operations (KEYS, SMEMBERS on large sets)"
        echo "  - Consider using pipelining for batch operations"
    fi
else
    echo "No slow commands recorded (this is good!)"
fi
echo ""

# ----------------------------------------------------------------------------
# 8. Persistence Status
# ----------------------------------------------------------------------------
echo "8. PERSISTENCE STATUS"
echo "------------------------------------------------------------------------"
${REDIS_CLI} INFO persistence | grep -E "rdb_last_save_time|rdb_changes_since_last_save|aof_enabled|aof_last_rewrite_time_sec"
echo ""

# ----------------------------------------------------------------------------
# 9. Keyspace Information
# ----------------------------------------------------------------------------
echo "9. KEYSPACE INFORMATION"
echo "------------------------------------------------------------------------"
${REDIS_CLI} INFO keyspace
echo ""

# Get sample of keys by pattern
echo "Sample keys by pattern:"
echo "  - Session keys:"
SESSION_KEYS=$(${REDIS_CLI} KEYS "session:*" | wc -l)
echo "    Count: ${SESSION_KEYS}"

echo "  - Room keys:"
ROOM_KEYS=$(${REDIS_CLI} KEYS "room:*" | wc -l)
echo "    Count: ${ROOM_KEYS}"

echo "  - Pub/Sub channels:"
CHANNEL_KEYS=$(${REDIS_CLI} KEYS "channel:*" | wc -l)
echo "    Count: ${CHANNEL_KEYS}"
echo ""

# ----------------------------------------------------------------------------
# 10. CPU and Performance Metrics
# ----------------------------------------------------------------------------
echo "10. CPU AND PERFORMANCE METRICS"
echo "------------------------------------------------------------------------"
${REDIS_CLI} INFO cpu | grep -E "used_cpu_sys|used_cpu_user"
echo ""

# Get instantaneous metrics
echo "Instantaneous metrics:"
${REDIS_CLI} INFO stats | grep "instantaneous" | head -5
echo ""

# ----------------------------------------------------------------------------
# 11. Replication Status (if applicable)
# ----------------------------------------------------------------------------
echo "11. REPLICATION STATUS"
echo "------------------------------------------------------------------------"
REPLICATION_ROLE=$(${REDIS_CLI} INFO replication | grep "^role:" | cut -d: -f2 | tr -d '\r')
echo "Role: ${REPLICATION_ROLE}"

if [ "$REPLICATION_ROLE" = "master" ]; then
    ${REDIS_CLI} INFO replication | grep -E "connected_slaves|master_repl_offset"
elif [ "$REPLICATION_ROLE" = "slave" ]; then
    ${REDIS_CLI} INFO replication | grep -E "master_host|master_port|master_link_status|slave_repl_offset"
fi
echo ""

# ----------------------------------------------------------------------------
# 12. Configuration Check
# ----------------------------------------------------------------------------
echo "12. CRITICAL CONFIGURATION SETTINGS"
echo "------------------------------------------------------------------------"
echo "maxmemory: $(${REDIS_CLI} CONFIG GET maxmemory | tail -1)"
echo "maxmemory-policy: $(${REDIS_CLI} CONFIG GET maxmemory-policy | tail -1)"
echo "timeout: $(${REDIS_CLI} CONFIG GET timeout | tail -1)"
echo "tcp-keepalive: $(${REDIS_CLI} CONFIG GET tcp-keepalive | tail -1)"
echo "slowlog-log-slower-than: $(${REDIS_CLI} CONFIG GET slowlog-log-slower-than | tail -1) microseconds"
echo ""

# ----------------------------------------------------------------------------
# 13. Recommendations
# ----------------------------------------------------------------------------
echo "13. OPTIMIZATION RECOMMENDATIONS"
echo "------------------------------------------------------------------------"
echo "Based on analysis above, check for:"
echo "  1. Hit rate <90% - optimize cache strategy"
echo "  2. High memory usage (>90%) - increase maxmemory or optimize data structures"
echo "  3. Memory fragmentation ratio >1.5 - consider restarting Redis"
echo "  4. High number of connected clients - check connection pooling"
echo "  5. Slow commands in SLOWLOG - optimize O(N) operations"
echo "  6. High CPU usage - review command patterns, use pipelining"
echo "  7. Many pub/sub channels - this is normal for multi-room WebSocket architecture"
echo ""
echo "To reset stats: redis-cli CONFIG RESETSTAT"
echo "To clear slowlog: redis-cli SLOWLOG RESET"
echo ""
echo "========================================================================"
echo "Analysis Complete"
echo "========================================================================"
