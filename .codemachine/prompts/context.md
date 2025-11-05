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

### Context: key-assumptions (from 01_Context_and_Drivers.md)

```markdown
### 1.4. Key Assumptions

1. **User Base:** Primary target is small to medium Agile teams (2-12 concurrent users per session), with peak concurrency of ~500 simultaneous sessions
2. **Geographic Distribution:** Initial deployment targets North America and Europe with <100ms latency requirements within regions
3. **Session Duration:** Average estimation session lasts 30-60 minutes with 10-20 estimation rounds
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `scripts/load-test-voting.js`
    *   **Summary:** This file contains a comprehensive k6 load test script for WebSocket voting scenarios. It tests 500 concurrent rooms with 10 participants each (5,000 WebSocket connections total). The script includes heartbeat handling, vote casting, message latency tracking, and custom metrics (ws_message_latency, vote_e2e_latency, ws_connection_success, message_errors).
    *   **Recommendation:** This script is **ALREADY COMPLETE** and functional. You DO NOT need to recreate it. The script includes proper WebSocket lifecycle management (join, vote, heartbeat), latency measurement using pending request tracking, and comprehensive threshold definitions matching the NFRs (p95<200ms, connection success >99%, error rate <1%).
    *   **Action Required:** You MUST execute this script and analyze the results. Use `k6 run scripts/load-test-voting.js` for full load or `k6 run -e VUS=500 -e ROOMS=50 scripts/load-test-voting.js` for scaled-down testing.

*   **File:** `scripts/load-test-api.js`
    *   **Summary:** This file contains a comprehensive k6 load test script for REST API scenarios including room creation, listing, participant management, and subscription checkout. It includes two main scenarios: `api_load` (ramping arrival rate up to 100 req/s) and `subscription_checkout` (100 checkouts per minute).
    *   **Recommendation:** This script is **ALREADY COMPLETE** and functional. You DO NOT need to recreate it. The script includes proper custom metrics (room_creation_latency, subscription_checkout_latency), threshold definitions (p95<500ms for REST), and both individual endpoint tests and user journey tests.
    *   **Action Required:** You MUST execute this script and analyze the results. Use `k6 run scripts/load-test-api.js` for full test.

*   **File:** `scripts/analyze-database-performance.sql`
    *   **Summary:** This file contains SQL queries for analyzing database performance including active connections, slow queries (using pg_stat_statements), most called queries, missing indexes, table bloat, and cache hit ratios.
    *   **Recommendation:** This script is READY TO USE. You SHOULD run it against your database during and after load testing to identify bottlenecks.
    *   **Action Required:** Execute with `psql -U scrum_poker -d scrum_poker_db -f scripts/analyze-database-performance.sql` to gather performance data.

*   **File:** `scripts/analyze-redis-performance.sh`
    *   **Summary:** This is a bash script that connects to Redis and extracts performance metrics including memory usage, connected clients, hit/miss ratio, command statistics, and slow log entries.
    *   **Recommendation:** This script is READY TO USE for Redis monitoring during load tests.
    *   **Action Required:** Execute `./scripts/analyze-redis-performance.sh` during load testing to capture Redis metrics.

*   **File:** `scripts/README.md`
    *   **Summary:** Comprehensive documentation for running k6 load tests, including prerequisites, environment variables, monitoring guidance, result analysis, troubleshooting steps, and a complete performance benchmarking workflow (7 steps: baseline → capture → analyze → optimize → validate → compare → document).
    *   **Recommendation:** FOLLOW THIS GUIDE EXACTLY when executing load tests. It provides the complete workflow that you MUST follow to complete this task.
    *   **Action Required:** Use this as your primary reference for test execution procedure.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** This file contains production-ready configuration with extensive documentation (394 lines). Key performance-related configurations already present:
        - Database connection pool: `quarkus.datasource.reactive.max-size=20` with production notes recommending 50-100 (lines 17-20)
        - Redis connection pool: `quarkus.redis.max-pool-size=20` with production notes recommending 50-100 (lines 55-56)
        - HTTP thread pool: `quarkus.thread-pool.max-threads=200` (line 207)
        - **JVM tuning parameters FULLY DOCUMENTED** in comments (lines 339-393) including:
          * Heap size recommendations: `-Xms1g -Xmx1g`
          * G1GC configuration: `-XX:+UseG1GC -XX:MaxGCPauseMillis=200`
          * GC logging, metaspace, heap dump settings
          * Kubernetes deployment YAML example with JAVA_OPTS
          * Container memory limits (1.5GB)
          * Performance monitoring guidance
          * Scaling guidance for >10,000 connections
    *   **Recommendation:** The configuration ALREADY INCLUDES comprehensive JVM tuning documentation. You DO NOT need to add JVM settings to this file - they are passed via JAVA_OPTS environment variable in Kubernetes/Docker. The documentation is complete (lines 339-393).
    *   **Action Required:** Based on load test results, you may need to ADJUST the configurable parameters (DB_POOL_MAX_SIZE, REDIS_POOL_MAX_SIZE, QUARKUS_THREAD_POOL_MAX) via environment variables. Read the extensive production tuning comments already in the file. For local testing, set JAVA_OPTS before starting Quarkus.

*   **File:** `docs/performance-benchmarks.md`
    *   **Summary:** This document (currently 430+ lines) provides a comprehensive template for performance testing results with sections for executive summary, test environment, baseline results, bottleneck analysis, optimizations applied, validation results, and production recommendations. Currently contains placeholders marked "[To be filled after testing]" and a "Testing Status" section noting scripts are created but actual tests need to be run.
    *   **Recommendation:** This document structure is COMPLETE and ready for you to populate with actual test results. The template already includes all necessary sections.
    *   **Action Required:** You MUST fill in the placeholder sections with your actual test results, including hardware specs, software versions, baseline metrics, identified bottlenecks, optimizations applied, and validation results.

### Implementation Tips & Notes

*   **Tip #1 - Load Test Scripts Are Complete:** The project has **ALREADY** created production-grade load test scripts (`load-test-voting.js`, `load-test-api.js`) with proper metrics, thresholds, comprehensive testing documentation (`scripts/README.md`), and analysis scripts (`analyze-database-performance.sql`, `analyze-redis-performance.sh`). You DO NOT need to write new test scripts from scratch. Your focus should be on EXECUTING the existing scripts, ANALYZING the results, and OPTIMIZING based on findings.

*   **Tip #2 - Follow the 7-Step Workflow:** The `scripts/README.md` file (lines 229-273) documents a complete 7-step workflow you MUST follow:
    1. **Baseline Test**: Run k6 scripts with current configuration, output to JSON files
    2. **Capture Metrics**: Save k6 output, take Grafana screenshots, run database/Redis analysis scripts
    3. **Analyze Bottlenecks**: Review latency percentiles, identify errors, check resource utilization, run EXPLAIN ANALYZE on slow queries
    4. **Apply Optimizations**: Increase pool sizes, add indexes if needed, tune JVM settings
    5. **Validation Test**: Re-run k6 scripts with optimized configuration
    6. **Compare Results**: Before/after comparison of latency, error rate, resource utilization
    7. **Document**: Update docs/performance-benchmarks.md with all findings

*   **Tip #3 - System Prerequisites:** Before running load tests, you MUST ensure (from `scripts/README.md`):
    1. k6 is installed (`brew install k6` on macOS)
    2. File descriptor limits are raised: `ulimit -n 65536`
    3. Application is running and healthy: `curl http://localhost:8080/q/health/ready`
    4. PostgreSQL has pg_stat_statements extension: `CREATE EXTENSION IF NOT EXISTS pg_stat_statements;`
    5. Grafana dashboards from I8.T2 are accessible for real-time monitoring

*   **Tip #4 - Start with Scaled-Down Testing:** For local testing, the full 5,000 WebSocket connection load may overwhelm your development machine. The scripts support environment variables for scaling down:
    ```bash
    # Run with 50 rooms instead of 500 (500 connections instead of 5,000)
    k6 run -e VUS=500 -e ROOMS=50 scripts/load-test-voting.js
    ```
    Start small, verify it works, then scale up gradually.

*   **Tip #5 - Database Connection Pool Sizing:** The current default (20 connections) is documented as too small for production load. The application.properties comments (lines 13-16) recommend 50-100 connections for sustained load with 5,000 concurrent WebSocket connections. Set via environment variable:
    ```bash
    export DB_POOL_MAX_SIZE=50
    ./mvnw quarkus:dev
    ```

*   **Tip #6 - Redis Connection Pool Sizing:** Similarly, the Redis pool default (20) is too small. Comments (lines 52-55) recommend 50-100 connections. Set via environment variable:
    ```bash
    export REDIS_POOL_MAX_SIZE=50
    ```

*   **Tip #7 - JVM Configuration for Local Testing:** The JVM tuning is documented in application.properties lines 339-393. For local testing, set JAVA_OPTS before starting the application:
    ```bash
    export JAVA_OPTS="-Xms1g -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
    ./mvnw quarkus:dev
    ```

*   **Warning #1 - Local Testing Limitations:** Running load tests generating 5,000 WebSocket connections on a local development machine may not give accurate results. The machine itself may become the bottleneck. For reliable performance testing, you SHOULD either:
    - **Option A:** Run load tests from a separate machine
    - **Option B:** Deploy to staging environment and test against it
    - **Option C:** Scale down test parameters for local testing and extrapolate results (this is acceptable for this task)

*   **Warning #2 - Database Indexes Already Comprehensive:** The migration file `V3__create_indexes.sql` already contains 40+ indexes covering all foreign keys, WHERE clauses, ORDER BY columns, and covering indexes. Unless EXPLAIN ANALYZE reveals specific sequential scans during load testing, you likely won't need to add new indexes. The existing indexes are well-designed.

*   **Warning #3 - Monitoring Is Essential:** During load testing, you MUST monitor in real-time using:
    - Grafana dashboards (from I8.T2) showing application metrics
    - k6 console output showing VUs, request rate, latency, errors
    - `scripts/analyze-database-performance.sql` for database metrics
    - `scripts/analyze-redis-performance.sh` for Redis metrics

    This allows you to identify bottlenecks as they occur.

*   **Note #1 - Scenario 3 Missing:** The task description mentions "scenario 3 (WebSocket reconnection storm)" but this script does NOT currently exist in `scripts/`. The existing scripts only cover voting load and API load. You may need to create a third script testing reconnection behavior, or acknowledge that only scenarios 1 and 2 are covered by the existing comprehensive scripts.

*   **Note #2 - Performance Benchmarks Template Complete:** The `docs/performance-benchmarks.md` file already has a complete structure with:
    - Executive Summary section with NFR targets listed
    - Testing Status section noting scripts are created
    - Test Environment section with placeholders
    - Test Scenarios, Baseline Results, Bottleneck Analysis, Optimizations, Validation, Production Recommendations sections
    - Your task is to EXECUTE the tests and POPULATE the placeholders with actual results.

*   **Note #3 - Authentication Simplification:** Both k6 scripts include comments about authentication being simplified for load testing (lines 131-149 in load-test-voting.js, lines 123-127 in load-test-api.js). The scripts currently use a placeholder test token or skip auth. For more realistic testing, you may want to:
    - Create a test endpoint that issues JWT tokens without full OAuth flow
    - OR pre-generate valid JWT tokens and use them in tests
    - OR accept that authentication overhead isn't included in the performance baseline

*   **Note #4 - Redis Hit Rate Measurement:** The acceptance criteria require "Redis hit rate >90% for session cache". However, the current application primarily uses Redis for Pub/Sub (WebSocket events) and refresh token storage, not traditional session caching. The hit rate metric may not be directly applicable. You should clarify what "session cache" means in this context or measure hit rate for refresh token lookups.

### Expected Bottlenecks & Optimization Path

Based on the architecture and configuration, here's the likely optimization path you'll follow:

1. **First Run (Baseline)**: Likely failures due to connection pool exhaustion
   - **Symptom**: "FATAL: sorry, too many clients already" from PostgreSQL
   - **Fix**: Increase DB_POOL_MAX_SIZE from 20 to 50-100
   - **Fix**: Increase REDIS_POOL_MAX_SIZE from 20 to 50-100

2. **Second Run**: May see memory pressure or GC pauses
   - **Symptom**: High GC pause times in logs, or heap exhaustion
   - **Fix**: Set JAVA_OPTS with proper heap size (-Xms1g -Xmx1g)
   - **Fix**: Enable G1GC with MaxGCPauseMillis=200

3. **Third Run**: May see slow database queries
   - **Symptom**: p95 latency >200ms for WebSocket messages
   - **Fix**: Run EXPLAIN ANALYZE on slow queries
   - **Fix**: Verify indexes are being used (should already be present)

4. **Final Validation**: All thresholds should pass
   - p95 latency <200ms for WebSocket
   - p95 latency <500ms for REST API
   - No connection pool exhaustion
   - Stable memory usage

### Acceptance Criteria Checklist

Before marking this task complete, verify:
- [ ] k6 load-test-voting.js executed successfully (or scaled-down equivalent)
- [ ] k6 load-test-api.js executed successfully
- [ ] Baseline test results captured (k6 JSON output saved)
- [ ] Database performance analyzed (analyze-database-performance.sql run)
- [ ] Redis performance analyzed (analyze-redis-performance.sh run)
- [ ] Bottlenecks identified and documented
- [ ] Optimizations applied (connection pools, JVM settings, etc.)
- [ ] Validation tests run after optimizations
- [ ] Before/after comparison documented
- [ ] docs/performance-benchmarks.md populated with:
   - [ ] Test environment specifications (hardware, software versions)
   - [ ] All scenario descriptions
   - [ ] Baseline results tables (latencies, throughput, error rates)
   - [ ] Bottleneck analysis section completed
   - [ ] Optimizations applied with before/after metrics
   - [ ] Production configuration recommendations
- [ ] Acceptance criteria validated:
   - [ ] Load test achieves 500 concurrent sessions (or proportional scaled version)
   - [ ] p95 latency <200ms for WebSocket messages
   - [ ] p95 latency <500ms for REST API endpoints
   - [ ] No database connection pool exhaustion
   - [ ] No memory leaks (heap usage stable)
   - [ ] Redis hit rate >90% (if applicable) or caveat documented
