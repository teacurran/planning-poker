# Performance Benchmarks & Load Testing Results

This document contains performance benchmarking results, load testing analysis, optimization strategies, and production recommendations for the Planning Poker application.

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Test Environment](#test-environment)
3. [Test Scenarios](#test-scenarios)
4. [Baseline Performance Results](#baseline-performance-results)
5. [Bottleneck Analysis](#bottleneck-analysis)
6. [Optimizations Applied](#optimizations-applied)
7. [Validation Results](#validation-results)
8. [Production Configuration Recommendations](#production-configuration-recommendations)
9. [Monitoring & Alerting](#monitoring--alerting)
10. [Troubleshooting Guide](#troubleshooting-guide)

---

## Executive Summary

### Non-Functional Requirements (NFRs)

The Planning Poker application has the following performance NFRs:

- **Latency:** <200ms p95 round-trip time for WebSocket messages within region
- **Throughput:** Support 500 concurrent sessions with 5,000 active WebSocket connections
- **Response Time:** REST API endpoints respond within <500ms for p95
- **Real-time Updates:** State synchronization across clients within 100ms
- **Error Rate:** <1% under target load
- **Database Performance:** No connection pool exhaustion under load
- **Cache Performance:** Redis hit rate >90% for session cache
- **Memory Stability:** No memory leaks (heap usage stable during sustained load)

### Testing Status

> **Note:** This document provides the testing framework, scripts, and optimization guidance. Actual load testing should be performed in a staging or production-like environment with sufficient resources (CPU, memory, network capacity).

**Test Scripts Created:**
- ✅ WebSocket voting load test (`scripts/load-test-voting.js`)
- ✅ REST API load test (`scripts/load-test-api.js`)
- ✅ WebSocket reconnection storm test (`scripts/load-test-reconnection-storm.js`)
- ✅ Database performance analysis script (`scripts/analyze-database-performance.sql`)
- ✅ Redis monitoring script (`scripts/analyze-redis-performance.sh`)
- ✅ Comprehensive testing documentation (`scripts/README.md`)

**Configuration Optimized:**
- ✅ Database connection pool settings documented and optimized
- ✅ Redis connection pool settings documented and optimized
- ✅ JVM tuning parameters documented
- ✅ HTTP thread pool configuration added
- ✅ Production deployment guidance provided

**Next Steps:**
1. Deploy application to staging environment with production-like resources
2. Start all infrastructure services (PostgreSQL, Redis, Grafana)
3. Install k6 on load test client machine
4. Execute baseline load tests (all 3 scenarios)
5. Capture performance metrics (k6 output, Grafana, database analysis)
6. Analyze bottlenecks using collected data
7. Apply optimizations based on identified bottlenecks
8. Re-run validation tests with optimized configuration
9. Compare before/after metrics and document improvements
10. Update this document with actual test results

**Environment Requirements for Load Testing:**
- **Staging/Production-like Environment:** Local development machines may not support 5,000+ connections
- **Separate Load Test Client:** Run k6 from a different machine to avoid resource contention
- **Monitoring Infrastructure:** Grafana, Prometheus, and logging must be operational
- **Database with Extensions:** PostgreSQL with pg_stat_statements extension enabled
- **Sufficient Resources:** Match production specs (CPU, memory, network bandwidth)

**Pre-Test Checklist:**
- [ ] k6 installed on load test client: `k6 version`
- [ ] Application health check passes: `curl http://<app-url>/q/health/ready`
- [ ] PostgreSQL accessible and pg_stat_statements enabled
- [ ] Redis accessible and responding to PING
- [ ] Grafana dashboards loading and showing metrics
- [ ] File descriptor limit raised: `ulimit -n 65536`
- [ ] Docker containers running (if using containers)
- [ ] Environment variables set (DB_POOL_MAX_SIZE, REDIS_POOL_MAX_SIZE, JAVA_OPTS)

**Execution Commands:**

```bash
# 1. Execute WebSocket Voting Test (Scenario 1)
# For production-scale: 5,000 connections
k6 run --out json=results-voting-baseline.json scripts/load-test-voting.js

# For local/staging-scale: 500 connections
k6 run -e VUS=500 -e ROOMS=50 --out json=results-voting-baseline.json scripts/load-test-voting.js

# 2. Execute REST API Test (Scenario 2)
k6 run --out json=results-api-baseline.json scripts/load-test-api.js

# 3. Execute Reconnection Storm Test (Scenario 3)
# For production-scale: 1,000 reconnections/min
k6 run --out json=results-reconnection-baseline.json scripts/load-test-reconnection-storm.js

# For local/staging-scale: 100 reconnections/min
k6 run -e RECONNECTIONS_PER_MIN=100 --out json=results-reconnection-baseline.json scripts/load-test-reconnection-storm.js

# 4. Run Database Analysis (during or after tests)
psql -U scrum_poker -d scrum_poker_db -f scripts/analyze-database-performance.sql > db-analysis-baseline.txt

# 5. Run Redis Monitoring (during tests)
./scripts/analyze-redis-performance.sh > redis-analysis-baseline.txt

# 6. Extract p95 latencies from k6 JSON output
cat results-voting-baseline.json | jq -r 'select(.type=="Point" and .metric=="ws_message_latency") | .data.value' | \
  awk '{all[NR] = $0} END {asort(all); print "p95:", all[int(NR*0.95)]}'
```

**Post-Test Analysis:**
1. Review k6 summary output for threshold pass/fail
2. Extract latency percentiles (p50, p95, p99) from JSON results
3. Analyze Grafana dashboards for resource utilization spikes
4. Review database slow queries from pg_stat_statements
5. Check Redis hit rate and slow log
6. Correlate errors in application logs with load test timestamps
7. Document findings in "Bottleneck Analysis" section below

---

## Test Environment

### Hardware Specifications

**Application Server:**
- **CPU:** [To be filled after testing] (e.g., 4 cores @ 2.5 GHz)
- **Memory:** [To be filled] (recommended: 4GB minimum)
- **Disk:** [To be filled] (e.g., SSD with 500 MB/s throughput)
- **Network:** [To be filled] (e.g., 1 Gbps)

**Database Server (PostgreSQL):**
- **CPU:** [To be filled] (e.g., 2 cores @ 2.5 GHz)
- **Memory:** [To be filled] (recommended: 4GB minimum)
- **Disk:** [To be filled] (SSD recommended for IOPS)
- **Version:** PostgreSQL 14+

**Cache Server (Redis):**
- **CPU:** [To be filled] (e.g., 2 cores @ 2.5 GHz)
- **Memory:** [To be filled] (recommended: 2GB minimum)
- **Version:** Redis 7+

**Load Test Client:**
- **CPU:** [To be filled] (recommended: separate machine from application)
- **Memory:** [To be filled] (minimum 4GB for k6 with 5,000 VUs)
- **Network:** [To be filled] (same region as application for realistic latency)

### Software Versions

- **Application:** Planning Poker v1.0.0
- **Quarkus:** 3.15.1
- **Java:** 17
- **PostgreSQL:** [To be filled]
- **Redis:** [To be filled]
- **k6:** [To be filled] (install from https://k6.io)
- **Operating System:** [To be filled]

### Network Configuration

- **Region:** [To be filled] (e.g., us-east-1, local datacenter)
- **Latency:** [To be filled] (e.g., <1ms intra-datacenter, <50ms intra-region)
- **Bandwidth:** [To be filled]

### Configuration Settings (Baseline)

**Application Configuration:**
```properties
# Database Connection Pool
DB_POOL_MAX_SIZE=20

# Redis Connection Pool
REDIS_POOL_MAX_SIZE=20

# Thread Pool
QUARKUS_THREAD_POOL_CORE=8
QUARKUS_THREAD_POOL_MAX=200

# JVM Settings
JAVA_OPTS=-Xms1g -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

**Database Configuration:**
```sql
-- PostgreSQL postgresql.conf settings
max_connections = 100
shared_buffers = 256MB
effective_cache_size = 1GB
work_mem = 4MB
maintenance_work_mem = 64MB
```

**Redis Configuration:**
```conf
maxmemory 512mb
maxmemory-policy allkeys-lru
```

---

## Test Scenarios

### Scenario 1: WebSocket Voting Load Test

**Objective:** Validate WebSocket connection stability, message latency, and concurrent voting performance.

**Test Script:** `scripts/load-test-voting.js`

**Load Profile:**
- **Target Rooms:** 500 concurrent rooms
- **Participants per Room:** 10 participants (= 5,000 WebSocket connections)
- **Ramp-up:** 2 minutes (0 → 5,000 VUs)
- **Sustain:** 5 minutes at full load
- **Ramp-down:** 1 minute (5,000 → 0 VUs)
- **Total Duration:** 8 minutes

**User Behavior:**
1. Connect to WebSocket endpoint
2. Authenticate with JWT token
3. Send `room.join.v1` message (within 10 seconds)
4. Cast votes every 30-60 seconds
5. Respond to heartbeat pings
6. Maintain connection for test duration

**Metrics Collected:**
- WebSocket connection success rate (target: >99%)
- Message round-trip latency (p50, p95, p99)
- Vote end-to-end latency (vote.cast → vote.recorded)
- Error rate (target: <1%)
- Active connection count
- Messages sent/received per second

**Thresholds:**
```javascript
thresholds: {
  'ws_message_latency': ['p(95)<200', 'p(99)<500'],
  'vote_e2e_latency': ['p(95)<200', 'p(99)<500'],
  'ws_connection_success': ['rate>0.99'],
  'message_errors': ['rate<0.01']
}
```

**Execution:**
```bash
# Full scale test (5,000 connections)
k6 run --out json=results-voting-baseline.json scripts/load-test-voting.js

# Scaled down for local testing (500 connections)
k6 run -e VUS=500 -e ROOMS=50 scripts/load-test-voting.js
```

### Scenario 2: REST API Load Test

**Objective:** Validate REST API endpoint performance, throughput, and subscription checkout flow.

**Test Script:** `scripts/load-test-api.js`

**Load Profile:**
- **API Load:** Ramp up to 100 requests/second, sustain for 5 minutes
- **Subscription Checkouts:** 100 checkouts per minute (constant rate)
- **Total Duration:** 7 minutes

**API Operations Tested:**
- Room creation (`POST /api/v1/rooms`)
- Room listing with pagination (`GET /api/v1/rooms`)
- Room details retrieval (`GET /api/v1/rooms/{id}`)
- Participant join (`POST /api/v1/rooms/{id}/participants`)
- Subscription plans (`GET /api/v1/subscriptions/plans`)
- Checkout session creation (`POST /api/v1/subscriptions/checkout`)
- Subscription status (`GET /api/v1/subscriptions/status`)

**Metrics Collected:**
- HTTP request latency (p50, p95, p99)
- Requests per second (RPS)
- HTTP error rate (target: <1%)
- Endpoint-specific latencies
- Success rates per operation

**Thresholds:**
```javascript
thresholds: {
  'http_req_duration': ['p(95)<500', 'p(99)<1000'],
  'room_creation_latency': ['p(95)<500', 'p(99)<1000'],
  'subscription_checkout_latency': ['p(95)<1000', 'p(99)<2000'],
  'http_req_failed': ['rate<0.01']
}
```

**Execution:**
```bash
# Full API load test
k6 run --out json=results-api-baseline.json scripts/load-test-api.js

# Specific scenario only
k6 run -e SCENARIO=api_load scripts/load-test-api.js
```

### Scenario 3: WebSocket Reconnection Storm Test

**Objective:** Validate WebSocket connection resilience, reconnection handling, and timeout mechanisms under rapid connect/disconnect patterns.

**Test Script:** `scripts/load-test-reconnection-storm.js`

**Load Profile:**
- **Target Reconnections:** 1,000 reconnections per minute
- **Test Duration:** 5 minutes
- **Concurrent VUs:** ~50 (calculated as reconnections_per_min / 20)

**Reconnection Scenarios:**
- **Normal Reconnections (50%):** Connect → Join → Stay 2-5s → Disconnect
- **Rapid Connect/Disconnect (30%):** Connect → No Join → Disconnect after 0.5s (tests join timeout)
- **Message Exchange (15%):** Connect → Join → Vote → Disconnect
- **Heartbeat Timeout (5%):** Connect → Join → Ignore heartbeat pings (tests heartbeat timeout)

**Metrics Collected:**
- Connection establishment latency (p50, p95, p99)
- Reconnection success rate (target: >95%)
- Join timeout enforcement rate
- Heartbeat timeout trigger rate
- Post-reconnect message latency
- Connection errors

**Thresholds:**
```javascript
thresholds: {
  'connection_establishment_latency': ['p(95)<1000', 'p(99)<2000'],
  'reconnection_success': ['rate>0.95'],
  'post_reconnect_message_latency': ['p(95)<300', 'p(99)<500'],
  'join_timeout_enforced': ['rate>0.5']
}
```

**Execution:**
```bash
# Full reconnection storm (1,000 reconnections/min)
k6 run --out json=results-reconnection-baseline.json scripts/load-test-reconnection-storm.js

# Scaled down for local testing (100 reconnections/min)
k6 run -e RECONNECTIONS_PER_MIN=100 scripts/load-test-reconnection-storm.js
```

### Scenario 4: Database Query Performance

**Objective:** Identify slow queries and verify index usage for critical operations.

**Test Script:** `scripts/analyze-database-performance.sql`

**Queries Analyzed:**
1. **Vote Reveal Query:** Fetch all votes for a round (critical path)
   ```sql
   SELECT v.* FROM vote v
   WHERE v.round_id = ? ORDER BY v.voted_at;
   ```
   - Expected index: `idx_vote_round_voted`
   - Expected plan: Index Scan or Index Only Scan

2. **Room Listing Query:** Paginated public room list
   ```sql
   SELECT r.* FROM room r
   WHERE r.deleted_at IS NULL AND r.privacy_mode = 'PUBLIC'
   ORDER BY r.created_at DESC LIMIT 20;
   ```
   - Expected index: `idx_room_privacy_created`
   - Expected plan: Index Scan

3. **Participant Lookup Query:** Active participants in a room
   ```sql
   SELECT p.* FROM participant p
   WHERE p.room_id = ? AND p.left_at IS NULL;
   ```
   - Expected index: `idx_participant_room_active`
   - Expected plan: Index Scan

**Execution:**
```bash
psql -U scrum_poker -d scrum_poker_db -f scripts/analyze-database-performance.sql > db-analysis.txt
```

### Scenario 5: Redis Performance Monitoring

**Objective:** Monitor Redis cache hit rate, memory usage, and command performance.

**Test Script:** `scripts/analyze-redis-performance.sh`

**Metrics Collected:**
- Cache hit rate (target: >90%)
- Memory usage and fragmentation
- Connected clients count
- Commands per second
- Pub/Sub channel count
- Slow log entries

**Execution:**
```bash
./scripts/analyze-redis-performance.sh > redis-analysis.txt
```

---

## Baseline Performance Results

> **Status:** Pending execution. Run load tests in staging environment and fill in results below.

### Scenario 1: WebSocket Voting Load Test - Baseline

**Test Date:** [To be filled]

**Load Profile Achieved:**
- Peak VUs: [e.g., 5,000]
- Total Connections Established: [e.g., 5,000]
- Test Duration: [e.g., 8 minutes]
- Total Messages Sent: [e.g., 150,000]

**Performance Metrics:**

| Metric | p50 | p95 | p99 | Target | Status |
|--------|-----|-----|-----|--------|--------|
| WebSocket Message Latency (ms) | [TBF] | [TBF] | [TBF] | p95 <200ms | ⏳ |
| Vote E2E Latency (ms) | [TBF] | [TBF] | [TBF] | p95 <200ms | ⏳ |
| Connection Success Rate | - | - | - | >99% | ⏳ |
| Message Error Rate | - | - | - | <1% | ⏳ |

**Resource Utilization:**

| Resource | Average | Peak | Limit | Status |
|----------|---------|------|-------|--------|
| Application CPU | [TBF] | [TBF] | [e.g., 400% (4 cores)] | ⏳ |
| Application Memory | [TBF] | [TBF] | [e.g., 1.5GB] | ⏳ |
| Database CPU | [TBF] | [TBF] | - | ⏳ |
| Database Connections | [TBF] | [TBF] | 100 | ⏳ |
| Redis CPU | [TBF] | [TBF] | - | ⏳ |
| Redis Memory | [TBF] | [TBF] | 512MB | ⏳ |

**Key Observations:**
- [To be filled after testing]
- Example: "Connection establishment took 50-100ms under load"
- Example: "GC pauses observed up to 150ms during peak load"
- Example: "Database connection pool reached 18/20 at peak"

### Scenario 2: REST API Load Test - Baseline

**Test Date:** [To be filled]

**Load Profile Achieved:**
- Peak RPS: [e.g., 100 req/s]
- Total Requests: [e.g., 42,000]
- Subscription Checkouts: [e.g., 500]

**Performance Metrics:**

| Endpoint | p50 (ms) | p95 (ms) | p99 (ms) | Target | Status |
|----------|----------|----------|----------|--------|--------|
| POST /rooms | [TBF] | [TBF] | [TBF] | p95 <500ms | ⏳ |
| GET /rooms | [TBF] | [TBF] | [TBF] | p95 <500ms | ⏳ |
| GET /rooms/{id} | [TBF] | [TBF] | [TBF] | p95 <500ms | ⏳ |
| POST /rooms/{id}/participants | [TBF] | [TBF] | [TBF] | p95 <500ms | ⏳ |
| POST /subscriptions/checkout | [TBF] | [TBF] | [TBF] | p95 <1000ms | ⏳ |

**Error Rates:**

| Endpoint | Success Rate | Error Rate | Target | Status |
|----------|-------------|------------|--------|--------|
| Overall | [TBF] | [TBF] | >99% | ⏳ |
| Room Creation | [TBF] | [TBF] | >99% | ⏳ |
| Subscription Checkout | [TBF] | [TBF] | >95% | ⏳ |

**Key Observations:**
- [To be filled after testing]

### Scenario 3: WebSocket Reconnection Storm Test - Baseline

**Test Date:** [To be filled]

**Load Profile Achieved:**
- Target Reconnections/Min: [e.g., 1,000]
- Total Reconnections: [e.g., 5,000]
- Test Duration: [e.g., 5 minutes]

**Performance Metrics:**

| Metric | p50 | p95 | p99 | Target | Status |
|--------|-----|-----|-----|--------|--------|
| Connection Establishment Latency (ms) | [TBF] | [TBF] | [TBF] | p95 <1000ms | ⏳ |
| Post-Reconnect Message Latency (ms) | [TBF] | [TBF] | [TBF] | p95 <300ms | ⏳ |
| Reconnection Success Rate | - | - | - | >95% | ⏳ |
| Join Timeout Enforcement Rate | - | - | - | >50% | ⏳ |
| Heartbeat Timeout Trigger Rate | - | - | - | Measured | ⏳ |

**Connection Statistics:**

| Metric | Count | Rate |
|--------|-------|------|
| Connections Established | [TBF] | [TBF/min] |
| Connections Dropped | [TBF] | [TBF/min] |
| Reconnections Attempted | [TBF] | [TBF/min] |
| Reconnections Succeeded | [TBF] | [TBF/min] |
| Connection Errors | [TBF] | [TBF/min] |

**Key Observations:**
- [To be filled after testing]
- Example: "Join timeout correctly enforced after 10s for connections without join message"
- Example: "Heartbeat timeout triggered after 60s of no heartbeat pong responses"
- Example: "Connection establishment averaged 50ms under load"

---

## Bottleneck Analysis

> **Status:** To be completed after baseline testing. Document bottlenecks identified through metrics analysis.

### Methodology

1. **Identify Performance Degradation Points:**
   - Review k6 metrics for threshold violations
   - Analyze Grafana dashboards for spikes in latency, error rates, or resource usage
   - Correlate application logs with performance degradation timestamps

2. **Profiling Techniques:**
   - Database: EXPLAIN ANALYZE on slow queries, pg_stat_statements analysis
   - Redis: SLOWLOG, INFO stats, command latency tracking
   - Application: JVM metrics (heap usage, GC pauses), thread pool saturation
   - Network: WebSocket message queueing, connection establishment delays

3. **Root Cause Analysis:**
   - For each bottleneck, determine:
     - What component is bottlenecked? (Database, Redis, CPU, Memory, Network)
     - What operation is slow? (Specific query, Redis command, GC pause)
     - What is the impact? (Latency increase, error rate, throughput reduction)
     - What is the underlying cause? (Missing index, pool exhaustion, memory leak)

### Example Bottleneck Template

**Bottleneck #1: [Name]**

**Symptom:**
- [Description of observed performance issue]
- [Metrics that show the problem]

**Impact:**
- [Quantify the impact on user experience or system performance]

**Root Cause:**
- [Detailed explanation of why this bottleneck occurs]

**Evidence:**
- [Logs, metrics, query plans, or other data supporting the diagnosis]

**Resolution:** (See Optimizations Applied section)

---

### Identified Bottlenecks (To Be Filled After Testing)

**Example Structure:**

**Bottleneck #1: Database Connection Pool Exhaustion**

**Symptom:**
- HTTP 500 errors with "Pool timeout" in logs
- Database acquisition-timeout exceeded messages
- p95 latency spikes from 150ms to 2000ms during peak load

**Impact:**
- ~5% of requests failing during sustained load
- User experience: failed room creation, vote cast failures

**Root Cause:**
- Default connection pool size (20) insufficient for 5,000 concurrent connections
- Average query time 10ms × concurrent queries = pool saturation
- PostgreSQL max_connections=100 also too low for multi-pod deployment

**Evidence:**
```
Database connection pool usage: 20/20 (100% utilized)
Average wait time for connection: 1500ms
PostgreSQL active connections: 97/100
```

**Resolution:**
- Increase DB_POOL_MAX_SIZE from 20 to 50
- Increase PostgreSQL max_connections to 300
- (See Optimizations Applied section for full details)

---

## Optimizations Applied

> **Status:** Configuration optimizations have been documented. Apply and test in staging environment.

### Optimization #1: Database Connection Pool Sizing

**Objective:** Prevent connection pool exhaustion under high concurrent load.

**Changes Made:**

**File:** `backend/src/main/resources/application.properties`

```properties
# Before:
quarkus.datasource.reactive.max-size=${DB_POOL_MAX_SIZE:20}

# After (recommended for production):
quarkus.datasource.reactive.max-size=${DB_POOL_MAX_SIZE:50}
```

**Environment Variable Configuration:**
```bash
# Development/Staging
export DB_POOL_MAX_SIZE=50

# Production (Kubernetes)
env:
  - name: DB_POOL_MAX_SIZE
    value: "50"
```

**Database Configuration:**
```sql
-- PostgreSQL: Increase max_connections
-- File: postgresql.conf
max_connections = 300
```

**Rationale:**
- Reactive applications use non-blocking I/O, requiring fewer connections than blocking
- Formula: `pool_size = 2 × num_cores + effective_parallelism`
- For 5,000 concurrent connections with burst voting: 50-100 connections handle load
- PostgreSQL must support all pods: 3 pods × 50 connections = 150 + buffer = 300

**Expected Impact:**
- Eliminate "Pool timeout" errors
- Reduce p95 latency by 500-1000ms during peak
- Improve success rate from 95% to >99%

**Validation:**
- Monitor `database_connections_active` metric in Grafana
- Check application logs for "Pool timeout" messages (should be zero)
- Run Scenario 1 and verify no pool exhaustion

---

### Optimization #2: Redis Connection Pool Sizing

**Objective:** Support high-throughput Pub/Sub broadcasting and session caching.

**Changes Made:**

**File:** `backend/src/main/resources/application.properties`

```properties
# Before:
quarkus.redis.max-pool-size=${REDIS_POOL_MAX_SIZE:20}

# After (recommended for production):
quarkus.redis.max-pool-size=${REDIS_POOL_MAX_SIZE:50}
```

**Environment Variable Configuration:**
```bash
export REDIS_POOL_MAX_SIZE=50
```

**Rationale:**
- Redis Pub/Sub broadcasting to 5,000 clients requires high command throughput
- Session caching for 500 concurrent rooms
- Default 20 connections may bottleneck under heavy pub/sub load

**Expected Impact:**
- Reduce Redis command queueing delays
- Improve message broadcast latency
- Maintain >90% cache hit rate

**Validation:**
- Run `./scripts/analyze-redis-performance.sh` during load test
- Check `connected_clients` in Redis INFO (should be ≤50 per pod)
- Verify no commands in SLOWLOG

---

### Optimization #3: JVM Heap Size and Garbage Collection Tuning

**Objective:** Prevent OutOfMemoryError and minimize GC pause times for 5,000 WebSocket connections.

**Changes Made:**

**File:** `backend/src/main/resources/application.properties` (documentation section added)

**Kubernetes Deployment:**
```yaml
env:
  - name: JAVA_OPTS
    value: >-
      -Xms1g -Xmx1g
      -XX:+UseG1GC
      -XX:MaxGCPauseMillis=200
      -XX:+UseStringDeduplication
      -XX:MaxMetaspaceSize=256m
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/tmp/heapdump.hprof

resources:
  limits:
    memory: 1536Mi  # 1GB heap + 0.5GB non-heap
  requests:
    memory: 1536Mi
```

**Rationale:**
- 5,000 WebSocket connections × ~200KB per connection = ~1GB heap requirement
- G1GC is optimal for large heaps (>4GB) and low-latency requirements
- MaxGCPauseMillis=200 aligns with 200ms latency NFR
- String deduplication reduces memory footprint for duplicate strings (user names, room IDs)

**Expected Impact:**
- No OutOfMemoryError under sustained load
- GC pauses <200ms (p99)
- Stable heap usage (no memory leaks)

**Validation:**
- Monitor `jvm_memory_used_bytes{area="heap"}` in Prometheus
- Check GC logs for pause times: `tail -f /tmp/gc.log`
- Run 5+ minute sustained load test and verify heap plateaus

---

### Optimization #4: HTTP Thread Pool Configuration

**Objective:** Handle high concurrency with 5,000 active WebSocket connections and REST API traffic.

**Changes Made:**

**File:** `backend/src/main/resources/application.properties`

```properties
# Added:
quarkus.thread-pool.core-threads=${QUARKUS_THREAD_POOL_CORE:8}
quarkus.thread-pool.max-threads=${QUARKUS_THREAD_POOL_MAX:200}
quarkus.thread-pool.queue-size=${QUARKUS_THREAD_POOL_QUEUE:10000}
```

**Rationale:**
- Default Quarkus worker threads: 8 × CPU cores (often 32-64)
- For 5,000+ WebSocket connections, increase worker threads to 200
- Reactive I/O handles most work, but worker threads needed for blocking operations

**Expected Impact:**
- Prevent thread pool saturation during peak load
- Reduce queueing delays for blocking operations

**Validation:**
- Monitor `executor_queued_tasks` metric
- Check for thread pool warnings in logs

---

### Optimization #5: Database Query Optimization (Verify Index Usage)

**Objective:** Ensure critical queries use indexes and avoid sequential scans.

**Analysis Performed:**
- Reviewed existing indexes in `V3__create_indexes.sql`
- 40+ comprehensive indexes already defined
- Covering indexes for vote reveal, room listing, participant lookup

**Critical Queries Validated:**

**1. Vote Reveal Query:**
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT v.* FROM vote v WHERE v.round_id = ? ORDER BY v.voted_at;
```

**Expected Plan:**
```
Index Scan using idx_vote_round_voted on vote v
  Index Cond: (round_id = '...')
  Buffers: shared hit=...
```

**Index Used:** `idx_vote_round_voted` (covering index with INCLUDE(card_value))

**2. Room Listing Query:**
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.* FROM room r
WHERE r.deleted_at IS NULL AND r.privacy_mode = 'PUBLIC'
ORDER BY r.created_at DESC LIMIT 20;
```

**Expected Plan:**
```
Index Scan using idx_room_privacy_created on room r
  Index Cond: (privacy_mode = 'PUBLIC' AND deleted_at IS NULL)
```

**Actions Required:**
1. Run `scripts/analyze-database-performance.sql` during load testing
2. If sequential scans found, add targeted indexes
3. Run `ANALYZE` command to update PostgreSQL statistics

**Expected Impact:**
- Vote reveal query <10ms (currently likely optimal)
- Room listing query <5ms
- No sequential scans under load

**Validation:**
- Run EXPLAIN ANALYZE during load test
- Check pg_stat_statements for slow queries
- Monitor query latency in application logs

---

## Validation Results

> **Status:** To be completed after applying optimizations and re-running load tests.

### Post-Optimization Test Results

**Test Date:** [To be filled]

### Scenario 1: WebSocket Voting Load Test - Optimized

**Performance Metrics Comparison:**

| Metric | Baseline p95 | Optimized p95 | Target | Improvement | Status |
|--------|--------------|---------------|--------|-------------|--------|
| WebSocket Message Latency (ms) | [TBF] | [TBF] | <200ms | [TBF] | ⏳ |
| Vote E2E Latency (ms) | [TBF] | [TBF] | <200ms | [TBF] | ⏳ |
| Connection Success Rate | [TBF] | [TBF] | >99% | [TBF] | ⏳ |
| Message Error Rate | [TBF] | [TBF] | <1% | [TBF] | ⏳ |

**Resource Utilization Comparison:**

| Resource | Baseline Peak | Optimized Peak | Improvement |
|----------|---------------|----------------|-------------|
| Application CPU | [TBF] | [TBF] | [TBF] |
| Application Memory | [TBF] | [TBF] | [TBF] |
| Database Connections | [TBF] | [TBF] | [TBF] |
| Redis Memory | [TBF] | [TBF] | [TBF] |

### Scenario 2: REST API Load Test - Optimized

**Performance Metrics Comparison:**

| Endpoint | Baseline p95 | Optimized p95 | Target | Improvement |
|----------|--------------|---------------|--------|-------------|
| POST /rooms | [TBF] | [TBF] | <500ms | [TBF] |
| GET /rooms | [TBF] | [TBF] | <500ms | [TBF] |
| POST /subscriptions/checkout | [TBF] | [TBF] | <1000ms | [TBF] |

### Acceptance Criteria Checklist

- [ ] Load test achieves 500 concurrent sessions without errors
- [ ] p95 latency <200ms for WebSocket messages under load
- [ ] p95 latency <500ms for REST API endpoints
- [ ] Database connection pool doesn't exhaust under load
- [ ] Redis hit rate >90% for session cache
- [ ] No memory leaks (heap usage stable during sustained load)
- [ ] Performance benchmarks documented in this report

---

## Production Configuration Recommendations

### Application Configuration

**Environment Variables (Kubernetes Deployment):**

```yaml
env:
  # Database Connection Pool
  - name: DB_POOL_MAX_SIZE
    value: "50"
  - name: DB_POOL_IDLE_TIMEOUT
    value: "PT10M"
  - name: DB_POOL_MAX_LIFETIME
    value: "PT30M"
  - name: DB_POOL_ACQUISITION_TIMEOUT
    value: "10"

  # Redis Connection Pool
  - name: REDIS_POOL_MAX_SIZE
    value: "50"
  - name: REDIS_POOL_MAX_WAITING
    value: "50"
  - name: REDIS_TIMEOUT
    value: "10s"

  # Thread Pool
  - name: QUARKUS_THREAD_POOL_CORE
    value: "8"
  - name: QUARKUS_THREAD_POOL_MAX
    value: "200"
  - name: QUARKUS_THREAD_POOL_QUEUE
    value: "10000"

  # JVM Settings
  - name: JAVA_OPTS
    value: >-
      -Xms1g -Xmx1g
      -XX:+UseG1GC
      -XX:MaxGCPauseMillis=200
      -XX:+UseStringDeduplication
      -XX:MaxMetaspaceSize=256m
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/tmp/heapdump.hprof
      -Xlog:gc*:file=/tmp/gc.log:time:filecount=5,filesize=10M

# Resource Limits
resources:
  requests:
    cpu: 2000m      # 2 CPU cores
    memory: 1536Mi  # 1.5GB (1GB heap + 0.5GB non-heap)
  limits:
    cpu: 4000m      # 4 CPU cores (allow burst)
    memory: 1536Mi  # Hard limit to prevent OOM
```

### Database Configuration

**PostgreSQL `postgresql.conf`:**

```conf
# Connection Settings
max_connections = 300
superuser_reserved_connections = 3

# Memory Settings
shared_buffers = 512MB              # 25% of system memory (for 2GB RAM)
effective_cache_size = 1536MB       # 75% of system memory
work_mem = 8MB                      # Per-operation memory
maintenance_work_mem = 128MB        # For VACUUM, CREATE INDEX

# Query Planner
random_page_cost = 1.1              # SSD-optimized (default 4.0 for HDD)
effective_io_concurrency = 200      # SSD can handle more concurrent I/O

# WAL Settings
wal_buffers = 16MB
checkpoint_completion_target = 0.9
max_wal_size = 2GB

# Logging (for performance analysis)
log_min_duration_statement = 500    # Log queries >500ms
log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on

# Performance Extensions
shared_preload_libraries = 'pg_stat_statements'
pg_stat_statements.track = all
```

**Create pg_stat_statements extension:**
```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
```

### Redis Configuration

**Redis `redis.conf`:**

```conf
# Memory Management
maxmemory 2gb
maxmemory-policy allkeys-lru
maxmemory-samples 5

# Persistence (for production)
save 900 1              # Save if at least 1 key changed in 900 seconds
save 300 10             # Save if at least 10 keys changed in 300 seconds
save 60 10000           # Save if at least 10000 keys changed in 60 seconds
stop-writes-on-bgsave-error yes
rdbcompression yes

# Networking
timeout 300             # Close idle clients after 300 seconds
tcp-keepalive 60        # Send TCP ACKs every 60 seconds
tcp-backlog 511         # Connection backlog

# Slow Log
slowlog-log-slower-than 10000   # Log commands taking >10ms
slowlog-max-len 128             # Keep last 128 slow commands

# Threaded I/O (Redis 6+)
io-threads 4
io-threads-do-reads yes

# Client Limits
maxclients 10000        # Support up to 10,000 connected clients
```

### Horizontal Scaling Guidance

**When to scale horizontally (add more pods):**

1. **CPU Saturation:**
   - If application CPU consistently >80% during normal load
   - Add 1-2 pods to distribute load

2. **Memory Pressure:**
   - If heap usage consistently >85% of max heap
   - Consider increasing heap size first, then scale horizontally

3. **Connection Count:**
   - If approaching 10,000+ WebSocket connections
   - Scale to 2+ pods with load balancer session affinity

4. **Database Bottleneck:**
   - If database is bottleneck (CPU >80%, slow queries)
   - Scale database vertically (more CPU/RAM)
   - Consider read replicas for reporting queries

5. **Redis Bottleneck:**
   - If Redis CPU >80% (Redis is single-threaded per instance)
   - Deploy Redis cluster mode for horizontal scaling
   - Or scale to Redis Sentinel with read replicas

**Kubernetes Horizontal Pod Autoscaler (HPA):**

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: planning-poker-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: planning-poker-backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    - type: Pods
      pods:
        metric:
          name: websocket_connections_active
        target:
          type: AverageValue
          averageValue: "2500"  # Scale when avg connections per pod >2,500
```

---

## Monitoring & Alerting

### Key Metrics to Monitor

**Application Metrics:**
- `http_server_requests_seconds` (REST API latency)
- `websocket_connections_active` (active WebSocket count)
- `websocket_messages_sent_total` (message throughput)
- `jvm_memory_used_bytes` (heap usage)
- `jvm_gc_pause_seconds` (GC pause times)
- `executor_queued_tasks` (thread pool queue depth)

**Database Metrics:**
- `pg_stat_activity` (active connections)
- `pg_stat_database` (queries per second, cache hit rate)
- `pg_stat_statements` (slow queries)

**Redis Metrics:**
- `redis_connected_clients` (connection count)
- `redis_keyspace_hits_total` / `redis_keyspace_misses_total` (hit rate)
- `redis_memory_used_bytes` (memory usage)
- `redis_commands_processed_total` (throughput)

### Alert Rules (Prometheus)

```yaml
groups:
  - name: planning-poker-performance
    rules:
      - alert: HighWebSocketLatency
        expr: histogram_quantile(0.95, rate(websocket_message_latency_seconds_bucket[5m])) > 0.2
        for: 2m
        annotations:
          summary: "p95 WebSocket latency >200ms"

      - alert: HighAPILatency
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 0.5
        for: 2m
        annotations:
          summary: "p95 API latency >500ms"

      - alert: DatabaseConnectionPoolExhaustion
        expr: database_connections_active / database_connections_max > 0.9
        for: 1m
        annotations:
          summary: "Database pool >90% utilized"

      - alert: RedisLowHitRate
        expr: rate(redis_keyspace_hits_total[5m]) / (rate(redis_keyspace_hits_total[5m]) + rate(redis_keyspace_misses_total[5m])) < 0.9
        for: 5m
        annotations:
          summary: "Redis hit rate <90%"

      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m]) > 0.01
        for: 2m
        annotations:
          summary: "Error rate >1%"
```

---

## Troubleshooting Guide

### Common Performance Issues

#### Issue: High WebSocket Message Latency

**Symptoms:**
- p95 latency >200ms for WebSocket messages
- Users report delayed vote updates

**Diagnosis:**
1. Check Redis Pub/Sub performance: `./scripts/analyze-redis-performance.sh`
2. Check Redis CPU usage (should be <80%)
3. Check application CPU usage (GC pauses?)
4. Check network latency between application and Redis

**Solutions:**
- If Redis CPU high: Deploy Redis cluster or scale vertically
- If application GC pauses high: Increase heap size or tune GC
- If network latency high: Deploy Redis and application in same AZ

#### Issue: Database Connection Pool Exhaustion

**Symptoms:**
- HTTP 500 errors with "Pool timeout" in logs
- `quarkus.datasource.reactive.acquisition-timeout` exceeded
- Database connection pool at 100% utilization

**Diagnosis:**
1. Check database connection metrics in Grafana
2. Review slow queries: `psql -f scripts/analyze-database-performance.sql`
3. Check PostgreSQL max_connections: `SHOW max_connections;`

**Solutions:**
- Increase `DB_POOL_MAX_SIZE` (e.g., 20 → 50)
- Increase PostgreSQL `max_connections` (e.g., 100 → 300)
- Optimize slow queries (add indexes, refactor N+1 queries)

#### Issue: Memory Leaks / OutOfMemoryError

**Symptoms:**
- Heap usage grows continuously without plateau
- `java.lang.OutOfMemoryError: Java heap space`
- Application pod restarts frequently

**Diagnosis:**
1. Check heap usage over time in Grafana: `jvm_memory_used_bytes{area="heap"}`
2. Capture heap dump: `-XX:+HeapDumpOnOutOfMemoryError`
3. Analyze heap dump with VisualVM or Eclipse MAT

**Solutions:**
- Fix memory leak (e.g., unclosed WebSocket sessions, cached objects not evicted)
- Increase heap size: `-Xmx2g`
- Review object retention (WebSocket session lifecycle, cache TTL)

#### Issue: Redis Hit Rate <90%

**Symptoms:**
- Redis cache hit rate <90%
- High database query load

**Diagnosis:**
1. Run `./scripts/analyze-redis-performance.sh` to check hit rate
2. Review cache key patterns: `redis-cli KEYS "*" | head -20`
3. Check TTL configuration

**Solutions:**
- Increase cache TTL for frequently accessed data
- Implement cache warming strategy (pre-populate cache on startup)
- Review cache eviction policy (allkeys-lru vs noeviction)

#### Issue: Slow Database Queries

**Symptoms:**
- Query latency >100ms
- pg_stat_statements shows slow queries

**Diagnosis:**
1. Run `psql -f scripts/analyze-database-performance.sql`
2. Identify slow queries in pg_stat_statements
3. Run EXPLAIN ANALYZE on slow queries

**Solutions:**
- Add missing indexes for WHERE/ORDER BY clauses
- Use covering indexes (INCLUDE clause) to avoid table access
- Optimize N+1 queries with JOINs or batch loading
- Run ANALYZE to update statistics: `ANALYZE;`

---

## Appendix

### References

- [k6 Load Testing Documentation](https://k6.io/docs/)
- [Quarkus Performance Tuning](https://quarkus.io/guides/performance-measure)
- [PostgreSQL Performance Optimization](https://www.postgresql.org/docs/current/performance-tips.html)
- [Redis Performance Best Practices](https://redis.io/docs/manual/performance/)

### Load Test Scripts Location

- **WebSocket Voting Test:** `scripts/load-test-voting.js`
- **REST API Test:** `scripts/load-test-api.js`
- **WebSocket Reconnection Storm Test:** `scripts/load-test-reconnection-storm.js`
- **Database Analysis:** `scripts/analyze-database-performance.sql`
- **Redis Monitoring:** `scripts/analyze-redis-performance.sh`
- **Documentation:** `scripts/README.md`

### Contact

For questions or issues with performance testing:
- Review `scripts/README.md` for detailed testing instructions
- Check application logs for error messages
- Review Grafana dashboards for real-time metrics
- Consult this document for troubleshooting guidance
