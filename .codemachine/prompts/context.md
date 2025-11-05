# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I8.T4",
  "iteration_id": "I8",
  "iteration_goal": "Prepare application for production deployment including Kubernetes manifests, monitoring setup, performance optimization, security hardening, documentation, and final end-to-end testing.",
  "description": "Conduct performance optimization and load testing to validate NFRs. Optimize database queries: add missing indexes, use query plan analysis (EXPLAIN), implement pagination efficiently (cursor-based vs. offset). Optimize Redis usage: configure connection pooling, use pipelining for batch operations. Configure Quarkus JVM settings: heap size (1GB), GC tuning (G1GC), thread pool sizing. Create k6 load test scripts: scenario 1 (500 concurrent rooms, 10 participants each, vote casting), scenario 2 (100 subscription checkouts/min), scenario 3 (WebSocket reconnection storm). Run tests, analyze results, identify bottlenecks (database, Redis, CPU), iterate optimizations. Document performance benchmarks (p95 latency, throughput, error rate under load).",
  "agent_type_hint": "BackendAgent",
  "inputs": "Performance NFRs from architecture blueprint (500 concurrent sessions, <200ms latency), Load testing patterns (k6, JMeter)",
  "input_files": [
    ".codemachine/artifacts/architecture/01_Context_and_Drivers.md"
  ],
  "target_files": [
    "scripts/load-test-voting.js",
    "scripts/load-test-api.js",
    "docs/performance-benchmarks.md",
    "backend/src/main/resources/application.properties"
  ],
  "deliverables": "k6 load test scripts for voting and API scenarios, Performance test execution and results analysis, Database index additions for slow queries, Redis connection pool configuration, JVM tuning parameters documented, Performance benchmarks document (latency, throughput, error rate)",
  "acceptance_criteria": "Load test achieves 500 concurrent sessions without errors, p95 latency <200ms for WebSocket messages under load, p95 latency <500ms for REST API endpoints, Database connection pool doesn't exhaust under load, Redis hit rate >90% for session cache, No memory leaks (heap usage stable during sustained load), Benchmarks documented in performance report",
  "dependencies": [
    "I8.T2"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: performance-nfrs (from 01_Context_and_Drivers.md)

```markdown
#### Performance
- **Latency:** <200ms round-trip time for WebSocket messages within region
- **Throughput:** Support 500 concurrent sessions with 6,000 active WebSocket connections
- **Response Time:** REST API endpoints respond within <500ms for p95
- **Real-time Updates:** State synchronization across clients within 100ms
```

### Context: scalability-nfrs (from 01_Context_and_Drivers.md)

```markdown
#### Scalability
- **Horizontal Scaling:** Stateless application nodes behind load balancer with session affinity
- **Connection Distribution:** WebSocket connections distributed across multiple backend nodes using Redis Pub/Sub
- **Database Scaling:** Read replicas for reporting queries, connection pooling for transactional load
- **Auto-scaling:** Kubernetes HPA based on CPU/memory and custom WebSocket connection metrics
```

### Context: performance-testing (from 03_Verification_and_Glossary.md)

```markdown
**Scope:** Validate non-functional requirements (latency, throughput, scalability)

**Framework:** k6 (load testing), Apache JMeter (alternative)

**Scenarios:**
1. **Concurrent Sessions:** 500 rooms with 10 participants each (5,000 WebSocket connections)
2. **Vote Storm:** All participants in 100 rooms vote within 10-second window
3. **API Load:** 1,000 requests/second to REST endpoints (room creation, user queries)
4. **Subscription Checkout:** 100 concurrent Stripe checkout sessions

**Metrics:**
- **Latency:** p50, p95, p99 for REST and WebSocket messages
- **Throughput:** Requests per second, WebSocket messages per second
- **Error Rate:** <1% errors under target load
- **Resource Usage:** CPU, memory, database connections

**Acceptance Criteria:**
- p95 latency <200ms for WebSocket messages under 500-room load
- p95 latency <500ms for REST API endpoints
- No database connection pool exhaustion
- Application auto-scales (HPA adds pods) under sustained load
- Performance benchmarks documented in `docs/performance-benchmarks.md`
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** This file contains the complete application configuration including database connection pooling, Redis settings, and logging configuration. Current settings:
        - Database pool: `quarkus.datasource.reactive.max-size=${DB_POOL_MAX_SIZE:20}` (default 20 connections)
        - Redis pool: `quarkus.redis.max-pool-size=${REDIS_POOL_MAX_SIZE:20}` (default 20 connections)
        - No JVM heap size or GC tuning parameters currently configured
    *   **Recommendation:** You MUST update this file to add JVM tuning parameters and potentially increase connection pool sizes for production load. For standard JVM mode (not native), JVM settings should be passed via environment variables or documented in deployment configurations. Add a comment section explaining recommended production JVM settings.

*   **File:** `backend/src/main/resources/db/migration/V3__create_indexes.sql`
    *   **Summary:** This file contains comprehensive index definitions for all tables with 40+ indexes. Notable indexes include:
        - `idx_vote_round_participant` covering index with INCLUDE clause for vote aggregation during reveal
        - `idx_vote_round_voted` covering index ordered by voted_at with card_value included
        - `idx_room_owner_created` for user's room queries with partial index WHERE deleted_at IS NULL
        - Partitioned table indexes for SessionHistory and AuditLog
        - Comprehensive indexes for all foreign key relationships
    *   **Recommendation:** These indexes are already well-designed for the current schema. You SHOULD run EXPLAIN ANALYZE on critical queries (vote reveal, room listing, participant lookup) to verify they are being used effectively. Focus particularly on queries that may cause N+1 issues or sequential scans. The indexes appear complete, so you likely won't need to add new ones unless testing reveals specific bottlenecks.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
    *   **Summary:** Contains reactive Panache repository with custom finder methods. Key query patterns:
        - `findByRoundId()`: **CRITICAL PATH** for vote reveal - uses `round.roundId = ?1 order by votedAt`
        - `findByRoomIdAndRoundNumber()`: Alternative query with room + round number
        - `findByRoundIdAndParticipantId()`: Used for duplicate vote detection (upsert logic)
        - All methods return reactive types (Uni<>, Multi<>) for non-blocking I/O
    *   **Recommendation:** You MUST use EXPLAIN ANALYZE on the `findByRoundId()` query under load to verify the `idx_vote_round_voted` covering index is being used. The query should show "Index Scan using idx_vote_round_voted" or ideally "Index Only Scan". If you see "Seq Scan", there's a problem with query planning. The covering index includes card_value, so the query should be fully satisfied from the index without table access.

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
    *   **Summary:** WebSocket endpoint implementation managing connection lifecycle, heartbeat protocol, and message routing. Key characteristics:
        - JWT authentication on connection
        - Heartbeat interval: 30 seconds, timeout: 60 seconds (constants defined at lines 65-66)
        - Join timeout: 10 seconds (line 64) - sessions must send room.join.v1 or be disconnected
        - Uses ConnectionRegistry for thread-safe session management
        - Scheduled cleanup tasks for stale connections and pending joins
        - All handlers use reactive patterns with Uni/Multi
    *   **Recommendation:** This is a **CRITICAL PERFORMANCE HOTSPOT**. Under 5,000 concurrent WebSocket connections, the heartbeat scheduled tasks will execute frequently. You SHOULD monitor:
        1. The number of active sessions in ConnectionRegistry during load testing
        2. The execution time of scheduled cleanup tasks (heartbeat checks, join timeouts)
        3. CPU usage spikes correlating with heartbeat intervals
        If heartbeat overhead is too high, consider increasing intervals: heartbeat to 60s, timeout to 120s. This reduces overhead at the cost of slower stale connection detection.

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven POM with Quarkus 3.15.1 and all reactive extensions. Java 17 target. Notable dependencies:
        - quarkus-hibernate-reactive-panache (reactive database)
        - quarkus-reactive-pg-client (non-blocking PostgreSQL)
        - quarkus-redis-client (reactive Redis)
        - quarkus-websockets (WebSocket support)
        - quarkus-micrometer & quarkus-micrometer-registry-prometheus (metrics)
        - quarkus-smallrye-fault-tolerance (circuit breakers, retry)
    *   **Recommendation:** All necessary dependencies are present for production workload. You do NOT need to add k6 as a dependency (it's a standalone CLI tool). Ensure builds use `-Dquarkus.package.type=uber-jar` for simplified containerization. For JVM tuning, you'll document the recommended JAVA_OPTS environment variables rather than changing POM configuration.

### Implementation Tips & Notes

*   **Tip #1 - k6 Script Structure:** The directory `scripts/` does not currently exist. You MUST create it to store the k6 load test scripts. k6 uses JavaScript (ES6+ modules) with k6-specific APIs. For WebSocket testing, import the `ws` module. Example structure:
    ```javascript
    import http from 'k6/http';
    import ws from 'k6/ws';
    import { check } from 'k6';

    export let options = {
      scenarios: {
        voting_load: {
          executor: 'ramping-vus',
          startVUs: 0,
          stages: [
            { duration: '2m', target: 500 },  // ramp up to 500 VUs
            { duration: '5m', target: 500 },  // sustain 500 VUs
            { duration: '1m', target: 0 }     // ramp down
          ]
        }
      },
      thresholds: {
        'ws_message_duration': ['p(95)<200'],  // p95 < 200ms
        'http_req_duration': ['p(95)<500'],    // p95 < 500ms
        'http_req_failed': ['rate<0.01']       // <1% error rate
      }
    };
    ```

*   **Tip #2 - WebSocket k6 Pattern:** For voting scenario, each VU (virtual user) should:
    1. **Authenticate:** POST to `/api/v1/auth/...` to obtain JWT token (or use a pre-generated test token)
    2. **Connect WebSocket:** `ws.connect('ws://localhost:8080/ws/room/{roomId}?token={jwt}', ...)`
    3. **Send join message:** Within 10 seconds, send `{"type": "room.join.v1", "requestId": "...", "payload": {...}}`
    4. **Cast votes:** Send `vote.cast.v1` messages at regular intervals
    5. **Measure latency:** Track time from sending vote to receiving `vote.recorded.v1` event
    6. **Handle disconnection:** Test reconnection logic

*   **Tip #3 - Database Query Analysis Process:** To identify slow queries, use this workflow:
    1. Enable query logging: Set `%dev.quarkus.hibernate-orm.log.sql=true` (already configured in application.properties line 257)
    2. Run load test and capture slow query logs
    3. For each slow query, run EXPLAIN ANALYZE in PostgreSQL:
       ```sql
       EXPLAIN (ANALYZE, BUFFERS)
       SELECT * FROM vote WHERE round_id = 'some-uuid' ORDER BY voted_at;
       ```
    4. Look for:
       - "Seq Scan" (bad - means index not used)
       - "Index Scan" or "Index Only Scan" (good)
       - "Buffers: shared hit=" with high hit rate (good cache utilization)
       - Actual execution time should be <10ms for indexed queries
    5. If Seq Scan is found, investigate: is the index present? Is query written correctly? Are statistics up to date (`ANALYZE` command)?

*   **Tip #4 - Redis Monitoring Commands:** During load testing, monitor Redis performance with these commands:
    ```
    INFO stats          # Get hit/miss ratio, total commands
    INFO memory         # Memory usage, fragmentation
    INFO clients        # Connected clients count
    SLOWLOG GET 10      # Recent slow commands
    ```
    You want to see:
    - Hit rate >90% (keyspace_hits / (keyspace_hits + keyspace_misses))
    - Memory usage stable and not near maxmemory
    - No commands in slowlog taking >100ms

*   **Tip #5 - Connection Pool Sizing Formula:** For database connection pool sizing under high load:
    - **Formula:** `pool_size = Tn × (Cm - 1) + 1` where Tn = number of CPU cores, Cm = number of concurrent requests
    - **Simplified for reactive:** Since reactive queries are non-blocking, you need fewer connections than in blocking model. Start with `2 × num_cores + effective_parallelism`.
    - **For this workload:** With 500 concurrent rooms and 10 participants = 5,000 connections. During vote reveal, assume 10% of participants vote simultaneously = 500 queries. With avg query time 10ms (0.01s), you need ~5-10 connections for steady state. However, for burst traffic (all 5,000 vote at once), increase to 50-100 connections to buffer the spike.
    - **Current setting:** 20 connections is probably too low. Try 50-100 for load testing.

*   **Tip #6 - JVM Tuning Recommendations:** For production Quarkus application with 5,000 WebSocket connections:
    - **Heap Size:** 1GB minimum (`-Xms1g -Xmx1g`). With 5,000 connections, estimate ~200KB per connection = 1GB for connection state alone, plus application objects.
    - **GC Algorithm:** G1GC is recommended for large heaps (`-XX:+UseG1GC`). Set max GC pause target: `-XX:MaxGCPauseMillis=200` (200ms).
    - **GC Logging:** Enable for troubleshooting: `-Xlog:gc*:file=/tmp/gc.log:time:filecount=5,filesize=10M`
    - **Thread Pool:** Quarkus manages worker threads automatically, but you can tune: `-Dquarkus.thread-pool.core-threads=8 -Dquarkus.thread-pool.max-threads=200`
    - **Document these in application.properties comments or in docs/performance-benchmarks.md under "JVM Configuration" section.**

*   **Tip #7 - k6 Script Environment Variables:** Make your k6 scripts configurable via environment variables so they can run against different environments:
    ```javascript
    const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
    const WS_URL = __ENV.WS_URL || 'ws://localhost:8080';
    const VUS = __ENV.VUS || '500';
    const DURATION = __ENV.DURATION || '5m';
    ```
    Run with: `k6 run -e BASE_URL=http://staging.example.com -e VUS=100 scripts/load-test-voting.js`

*   **Tip #8 - File Descriptor Limits:** k6 creating 5,000 WebSocket connections will require 5,000+ file descriptors (each socket is a file descriptor on Unix). On macOS/Linux, the default limit is often 256 or 1024. You MUST increase it before running tests:
    ```bash
    ulimit -n 65536  # Increase to 65,536 for current shell session
    ```
    For permanent change on Linux, edit `/etc/security/limits.conf`:
    ```
    * soft nofile 65536
    * hard nofile 65536
    ```

*   **Warning #1 - Local Testing Limitations:** Running load tests generating 5,000 WebSocket connections on a local development machine may not give accurate results. The machine itself may become the bottleneck (CPU, network, file descriptors). For reliable performance testing, you SHOULD:
    - **Option A:** Run load tests from a separate machine with higher specs
    - **Option B:** Deploy application to staging environment (Kubernetes cluster) and run tests against it
    - **Option C:** Scale down test parameters for local testing (e.g., 100 rooms instead of 500) and extrapolate results

*   **Warning #2 - Redis Pub/Sub Bottleneck:** With Redis Pub/Sub broadcasting messages to 5,000 clients across multiple application nodes, Redis itself can become a bottleneck. Monitor Redis CPU usage during tests. If Redis CPU saturates at 100%, consider:
    - Using Redis cluster mode for horizontal scaling
    - Reducing message payload sizes
    - Batching message broadcasts where possible

*   **Warning #3 - PostgreSQL Connection Exhaustion:** Even with connection pooling, under extreme load you might see "FATAL: sorry, too many clients already" errors from PostgreSQL. This means the database max_connections limit is reached. Default PostgreSQL max_connections is 100. With multiple application pods, each with a pool of 50-100 connections, you could easily exceed this. Solutions:
    - Increase PostgreSQL max_connections (requires database restart): `max_connections = 300`
    - Use PgBouncer connection pooler in transaction mode (more complex setup)
    - Reduce per-pod connection pool size (trade-off: may increase query queuing delays)

*   **Note #1 - Existing Indexes Are Comprehensive:** After reviewing V3__create_indexes.sql, I can confirm the indexing strategy is already comprehensive. The file contains 40+ indexes covering:
    - All foreign key relationships
    - All WHERE clause filters (privacy_mode, deleted_at, status, etc.)
    - All ORDER BY columns
    - Covering indexes with INCLUDE clauses for vote and room queries
    - Partial indexes for active records (WHERE deleted_at IS NULL)
    Unless EXPLAIN ANALYZE reveals specific sequential scans, you likely won't need to add new indexes. Your focus should be on verifying they are being used.

*   **Note #2 - Redis Pipelining Opportunity:** The codebase uses Redis for Pub/Sub (RoomEventPublisher) and presumably for session caching. I haven't seen explicit pipelining usage in the code. For batch operations (e.g., fetching multiple session states), pipelining can reduce latency. Example with Quarkus Redis client:
    ```java
    redisAPI.batch(Arrays.asList(
        Request.cmd(Command.GET).arg("key1"),
        Request.cmd(Command.GET).arg("key2"),
        Request.cmd(Command.GET).arg("key3")
    ))
    ```
    If you identify batch Redis operations during analysis, add pipelining as an optimization.

*   **Note #3 - Performance Benchmarks Document Structure:** The task requires documenting results in `docs/performance-benchmarks.md`. This document MUST include:
    1. **Test Environment:**
       - Hardware specs (CPU cores, RAM, disk type)
       - Software versions (Quarkus, PostgreSQL, Redis versions)
       - Deployment configuration (local, staging, production-like)
    2. **Test Scenarios:**
       - Description of each scenario (VUs, duration, ramp-up)
       - k6 command used to execute
    3. **Results Tables:**
       - Scenario | p50 | p95 | p99 | RPS | Error Rate | Resource Utilization
       - One table per scenario (voting, API, checkouts)
    4. **Bottleneck Analysis:**
       - What components were bottlenecks? (DB, Redis, CPU, network?)
       - Evidence (metrics, logs, profiler output)
    5. **Optimizations Applied:**
       - Before/after comparison for each optimization
       - Specific configuration changes made
    6. **Recommendations:**
       - Production configuration recommendations
       - Scaling guidance (when to add pods, when to scale DB)

*   **Note #4 - Monitoring During Load Tests:** You SHOULD have Grafana dashboards (from I8.T2) open during load testing to observe:
    - Application Overview dashboard: request rate, error rate, latency trends
    - WebSocket Metrics dashboard: connection count, message throughput
    - Business Metrics dashboard: active sessions, voting activity
    - Infrastructure dashboard: pod CPU/memory, DB connection pool, Redis metrics
    This real-time monitoring helps identify bottlenecks as they occur.

### Implementation Strategy

Follow this phased approach to complete the task:

**Phase 1: Setup & Preparation**
1. Create `scripts/` directory
2. Write k6 script for Scenario 1 (voting load)
3. Write k6 script for Scenario 2 (API load)
4. Write k6 script for Scenario 3 (WebSocket reconnection storm)
5. Increase local file descriptor limits if testing locally
6. Verify Grafana dashboards are accessible for monitoring

**Phase 2: Baseline Testing**
1. Run Scenario 1 at 10% scale (50 rooms, 500 connections) to validate script works
2. Run Scenario 1 at full scale (500 rooms, 5,000 connections)
3. Run Scenario 2 (API load)
4. Run Scenario 3 (reconnection storm)
5. Capture baseline metrics (p50, p95, p99, error rates, resource utilization)
6. Save k6 JSON output and Grafana screenshots

**Phase 3: Analysis & Optimization**
1. Analyze results: identify bottlenecks (CPU? DB? Redis? Network?)
2. Run EXPLAIN ANALYZE on slow database queries
3. Check Redis INFO stats for cache hit rate
4. Review application logs for errors or warnings
5. Apply optimizations based on findings:
   - Increase connection pool sizes if exhaustion detected
   - Add JVM tuning if memory issues found
   - Optimize queries if slow queries identified
   - Adjust heartbeat intervals if WebSocket overhead is high
6. Document each optimization applied

**Phase 4: Validation & Documentation**
1. Re-run load tests after optimizations
2. Compare before/after metrics
3. Verify acceptance criteria met (p95 latencies, no pool exhaustion, etc.)
4. Create `docs/performance-benchmarks.md` with full report
5. Update application.properties with comments about production tuning
6. Commit changes (k6 scripts, config updates, documentation)

### Acceptance Criteria Checklist

Before marking this task complete, verify:
- [ ] All three k6 scenarios created and executable
- [ ] Baseline tests run and results captured (JSON + screenshots)
- [ ] Database EXPLAIN ANALYZE performed on critical queries (vote reveal, room list)
- [ ] Connection pool sizes reviewed and optimized if needed
- [ ] JVM settings documented in application.properties or deployment docs
- [ ] Load test achieves 500 concurrent sessions (or scaled equivalent)
- [ ] p95 latency <200ms for WebSocket messages under load
- [ ] p95 latency <500ms for REST API endpoints under load
- [ ] Database connection pool doesn't exhaust (no "too many clients" errors)
- [ ] Redis hit rate >90% for session cache (from INFO stats)
- [ ] No memory leaks (heap usage stable during sustained load, check via JVM metrics)
- [ ] Performance benchmarks document created with:
   - [ ] Test environment specifications
   - [ ] All scenario descriptions
   - [ ] Results tables with latencies, throughput, error rates
   - [ ] Bottleneck analysis section
   - [ ] Optimizations applied with before/after comparison
   - [ ] Production recommendations

### Quick Reference: Common k6 WebSocket Pattern

```javascript
import ws from 'k6/ws';
import { check } from 'k6';

export default function() {
  const url = 'ws://localhost:8080/ws/room/ABC123?token=jwt_token_here';
  const res = ws.connect(url, null, function(socket) {
    socket.on('open', () => {
      console.log('Connected');
      // Must send join message within 10 seconds
      socket.send(JSON.stringify({
        type: 'room.join.v1',
        requestId: 'req-' + Date.now(),
        payload: { displayName: 'Test User', role: 'VOTER' }
      }));
    });

    socket.on('message', (data) => {
      const msg = JSON.parse(data);
      check(msg, { 'received valid message': (m) => m.type !== undefined });
    });

    socket.on('close', () => console.log('Disconnected'));
    socket.on('error', (e) => console.error('Error:', e));

    // Cast a vote after joining
    socket.setTimeout(() => {
      socket.send(JSON.stringify({
        type: 'vote.cast.v1',
        requestId: 'vote-' + Date.now(),
        payload: { cardValue: '5' }
      }));
    }, 1000);

    // Keep connection open for 60 seconds
    socket.setTimeout(() => socket.close(), 60000);
  });

  check(res, { 'status is 101': (r) => r && r.status === 101 });
}
```
