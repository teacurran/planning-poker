# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

**Task:** Conduct performance optimization and load testing to validate NFRs. Optimize database queries: add missing indexes, use query plan analysis (EXPLAIN), implement pagination efficiently (cursor-based vs. offset). Optimize Redis usage: configure connection pooling, use pipelining for batch operations. Configure Quarkus JVM settings: heap size (1GB), GC tuning (G1GC), thread pool sizing. Create k6 load test scripts: scenario 1 (500 concurrent rooms, 10 participants each, vote casting), scenario 2 (100 subscription checkouts/min), scenario 3 (WebSocket reconnection storm). Run tests, analyze results, identify bottlenecks (database, Redis, CPU), iterate optimizations. Document performance benchmarks (p95 latency, throughput, error rate under load).

**Acceptance Criteria:**
- Load test achieves 500 concurrent sessions without errors
- p95 latency <200ms for WebSocket messages under load
- p95 latency <500ms for REST API endpoints
- Database connection pool doesn't exhaust under load
- Redis hit rate >90% for session cache
- No memory leaks (heap usage stable during sustained load)
- Benchmarks documented in performance report

---

## Issues Detected

The implementation created comprehensive load testing scripts and documentation but **did not execute the actual load tests or collect performance data**. This is a critical gap:

### 1. **Load Tests Not Executed**
*   The k6 load test scripts were created (`scripts/load-test-voting.js`, `scripts/load-test-api.js`) but never run
*   The task description explicitly states: "Run tests, analyze results, identify bottlenecks"
*   Acceptance criteria states "Load test achieves 500 concurrent sessions" - this is a statement of actual achievement, not preparation

### 2. **No Performance Data Collected**
*   The `docs/performance-benchmarks.md` document has all tables marked with "[To be filled]" or "[TBF]"
*   No baseline metrics captured
*   No actual latency measurements (p50, p95, p99)
*   No resource utilization data (CPU, memory, DB connections)
*   Document explicitly states: "Status: Pending execution"

### 3. **Bottleneck Analysis Not Performed**
*   The task requires: "identify bottlenecks (database, Redis, CPU), iterate optimizations"
*   Only methodology and templates were documented
*   No actual bottlenecks identified from real test data
*   No evidence of running EXPLAIN ANALYZE on queries
*   No Redis INFO stats captured during load

### 4. **Optimizations Not Validated**
*   Configuration changes were documented but not tested
*   No before/after performance comparison
*   Cannot verify if optimizations actually meet NFR targets without real data
*   The "Validation Results" section is empty with all checkboxes unchecked

### 5. **Missing Scenario 3**
*   Task specifically requests: "scenario 3 (WebSocket reconnection storm)"
*   Only 2 k6 scripts were created (voting and API)
*   No WebSocket reconnection storm scenario implemented

---

## Best Approach to Fix

You MUST complete the following steps to fulfill the task requirements:

### Step 1: Create Missing Test Scenario
Create `scripts/load-test-reconnection-storm.js` implementing a WebSocket reconnection storm scenario:
- Rapidly connect, disconnect, and reconnect WebSocket sessions
- Simulate network instability (clients dropping and rejoining)
- Measure connection establishment time, reconnection success rate
- Target: 1,000+ reconnections per minute
- Verify heartbeat timeout and join timeout mechanisms handle this correctly

### Step 2: Execute Load Tests
You MUST actually run the load tests to collect performance data:

1. **Start the application** (if not running):
   ```bash
   # Start backend, PostgreSQL, Redis
   # Document which environment you're testing (local, staging, etc.)
   ```

2. **Run scaled-down tests** (local environment may not support full 5,000 connections):
   ```bash
   # Voting test - scaled to local capacity (e.g., 100 rooms = 1,000 connections)
   k6 run -e VUS=1000 -e ROOMS=100 --out json=results-voting-baseline.json scripts/load-test-voting.js

   # API test
   k6 run --out json=results-api-baseline.json scripts/load-test-api.js

   # Reconnection storm
   k6 run --out json=results-reconnection-baseline.json scripts/load-test-reconnection-storm.js
   ```

3. **Capture actual metrics**:
   - Save k6 JSON output files
   - Extract p50, p95, p99 latency from k6 summary
   - Monitor system resources during test (CPU, memory)
   - Capture error rates and throughput

### Step 3: Perform Database Analysis
Execute the database performance analysis:

1. **Run EXPLAIN ANALYZE on critical queries**:
   ```bash
   psql -U scrum_poker -d scrum_poker_db -f scripts/analyze-database-performance.sql > db-analysis-results.txt
   ```

2. **Identify actual query plans**:
   - Verify indexes are being used (look for "Index Scan" not "Seq Scan")
   - Measure actual query execution times
   - Document which queries are slow (if any)

3. **Add indexes if needed**:
   - If any sequential scans found, create new migration file with additional indexes
   - Otherwise, document that existing indexes are sufficient

### Step 4: Perform Redis Analysis
Execute Redis monitoring during load test:

1. **Run Redis analysis script**:
   ```bash
   ./scripts/analyze-redis-performance.sh > redis-analysis-results.txt
   ```

2. **Capture hit rate**:
   - Calculate actual hit rate from INFO stats
   - Document if it meets >90% target
   - Identify cache misses patterns if below target

### Step 5: Document ACTUAL Results
Update `docs/performance-benchmarks.md` with real data:

1. **Fill in Test Environment section**:
   - Actual hardware specs (CPU cores, RAM)
   - Software versions (PostgreSQL, Redis, Quarkus)
   - Environment type (local, staging, production-like)

2. **Fill in Baseline Performance Results tables**:
   - Replace all "[TBF]" with actual numbers
   - Include p50, p95, p99 latencies from k6 output
   - Include resource utilization from monitoring

3. **Complete Bottleneck Analysis section**:
   - Document ACTUAL bottlenecks found (if any)
   - Provide evidence (metrics, logs, query plans)
   - Explain root causes based on real data

4. **Fill in Validation Results section**:
   - Check all acceptance criteria checkboxes that passed
   - Document which NFRs were met and which were not
   - Provide before/after comparison if optimizations were applied

### Step 6: Apply and Validate Optimizations (if bottlenecks found)
If tests reveal performance issues:

1. Apply specific optimizations based on identified bottlenecks
2. Re-run load tests with optimized configuration
3. Compare before/after metrics
4. Document improvements in the "Optimizations Applied" section

### Important Notes
- **If local environment cannot support 5,000 connections**, scale tests down appropriately (e.g., 100 rooms = 1,000 connections) and document this limitation
- **If no bottlenecks are found** and all NFRs are met with current config, document this as a PASS in the benchmarks document
- **If some NFRs cannot be met locally**, document this and provide recommendations for staging/production testing
- The key is to have ACTUAL DATA and EVIDENCE, not just templates and methodology

### Acceptance Criteria for Completion
- [ ] All 3 k6 test scenarios created (voting, API, reconnection storm)
- [ ] All 3 tests executed with results captured
- [ ] Database EXPLAIN ANALYZE performed with results documented
- [ ] Redis analysis performed with hit rate measured
- [ ] `docs/performance-benchmarks.md` has ZERO "[TBF]" placeholders - all filled with actual data
- [ ] Bottleneck analysis section documents ACTUAL findings (even if "no bottlenecks found")
- [ ] Validation results section has checkboxes marked based on actual test results
- [ ] Evidence provided for each NFR (met or not met) with specific numbers
