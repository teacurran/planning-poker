# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

**Task ID:** I8.T4 - Performance Optimization and Load Testing

**Description:** Conduct performance optimization and load testing to validate NFRs. Optimize database queries: add missing indexes, use query plan analysis (EXPLAIN), implement pagination efficiently (cursor-based vs. offset). Optimize Redis usage: configure connection pooling, use pipelining for batch operations. Configure Quarkus JVM settings: heap size (1GB), GC tuning (G1GC), thread pool sizing. Create k6 load test scripts: scenario 1 (500 concurrent rooms, 10 participants each, vote casting), scenario 2 (100 subscription checkouts/min), scenario 3 (WebSocket reconnection storm). Run tests, analyze results, identify bottlenecks (database, Redis, CPU), iterate optimizations. Document performance benchmarks (p95 latency, throughput, error rate under load).

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

The implementation created comprehensive load testing infrastructure (scripts, documentation, analysis tools) but **FAILED TO COMPLETE THE CORE REQUIREMENT**: executing the load tests and collecting performance data.

### 1. **Critical: Load Tests Not Executed**
*   The task explicitly states: "**Run tests, analyze results, identify bottlenecks, iterate optimizations**"
*   Three k6 scripts were created but never executed
*   No test result files exist (no `results-*.json` files)
*   Acceptance criteria states "Load test **achieves** 500 concurrent sessions" - this requires actual execution

### 2. **Critical: No Performance Data Collected**
*   The `docs/performance-benchmarks.md` document contains **56 placeholder entries** marked "[TBF]" or "[To be filled]"
*   No actual latency measurements (p50, p95, p99)
*   No throughput data
*   No error rates
*   No resource utilization metrics
*   Document status shows: "Pending execution"

### 3. **Critical: Bottleneck Analysis Not Performed**
*   Task requires: "identify bottlenecks (database, Redis, CPU)"
*   No database EXPLAIN ANALYZE results captured
*   No Redis INFO stats collected during load
*   No CPU/memory profiling data
*   The "Bottleneck Analysis" section in the benchmarks doc is empty

### 4. **Critical: Optimizations Not Validated**
*   Configuration recommendations were documented but never tested
*   No before/after performance comparison
*   Cannot verify if suggested optimizations actually meet NFR targets
*   The "Validation Results" section has all checkboxes unchecked

### 5. **Blocking Issue: Pre-existing Compilation Errors**
*   The codebase has compilation errors in `com.scrumpoker.metrics.BusinessMetrics` class
*   Missing imports for `Gauge` and `Counter` from Micrometer
*   These errors prevent the application from starting
*   Cannot run load tests against a non-functional application
*   Health check endpoint returns error: "Error restarting Quarkus"

### 6. **Missing: Actual Optimization Iteration**
*   Task requires: "iterate optimizations" - no iteration was performed
*   Only documented what COULD be optimized, not what WAS optimized based on real findings

---

## Root Cause Analysis

The Coder Agent treated this as a **"create test infrastructure"** task rather than a **"conduct performance testing"** task. The work completed is entirely preparatory - excellent scripts and documentation were created, but the actual performance validation work was not done.

**Compounding Issue:** Pre-existing compilation errors from task I8.T2 (monitoring setup) prevent the application from running, which blocks load test execution.

---

## Best Approach to Fix

You MUST complete this task in the following order:

### Step 1: Fix Compilation Errors (BLOCKING)

Before load testing can begin, fix the compilation errors in `backend/src/main/java/com/scrumpoker/metrics/BusinessMetrics.java`:

1. **Review the file** and identify missing imports
2. **Add missing imports** for Micrometer classes (`io.micrometer.core.instrument.Gauge`, `io.micrometer.core.instrument.Counter`)
3. **Verify compilation** with: `./mvnw clean compile -DskipTests`
4. **Start the application** and verify health check passes: `curl http://localhost:8080/q/health/ready`

**Expected imports to add:**
```java
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
```

### Step 2: Execute Baseline Load Tests

Once the application is running, execute all three load test scenarios and capture results:

**2a. Start Required Infrastructure:**
```bash
# Verify PostgreSQL is running and accessible
psql -U scrum_poker -d scrum_poker_db -c "SELECT 1"

# Enable pg_stat_statements extension (if not already enabled)
psql -U scrum_poker -d scrum_poker_db -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements"

# Verify Redis is running
redis-cli PING

# Verify application health
curl http://localhost:8080/q/health/ready
```

**2b. Raise System Limits:**
```bash
ulimit -n 65536
```

**2c. Execute Load Tests (Scaled Down for Local Environment):**

Since local machines cannot support 5,000 connections, run scaled-down tests:

```bash
# Test 1: WebSocket Voting (scaled to 1,000 connections = 100 rooms × 10 participants)
k6 run -e VUS=1000 -e ROOMS=100 --out json=results-voting-baseline.json scripts/load-test-voting.js

# Test 2: REST API Load
k6 run --out json=results-api-baseline.json scripts/load-test-api.js

# Test 3: Reconnection Storm (scaled to 100 reconnections/min)
k6 run -e RECONNECTIONS_PER_MIN=100 --out json=results-reconnection-baseline.json scripts/load-test-reconnection-storm.js
```

**2d. Capture Metrics During Tests:**

While tests are running, collect supporting data:

```bash
# Database analysis (run after each test completes)
psql -U scrum_poker -d scrum_poker_db -f scripts/analyze-database-performance.sql > db-analysis-baseline.txt

# Redis analysis (run during test execution)
./scripts/analyze-redis-performance.sh > redis-analysis-baseline.txt
```

### Step 3: Analyze Results and Identify Bottlenecks

**3a. Extract k6 Metrics:**

From each JSON output file and console summary, extract:
- p50, p95, p99 latency percentiles
- Request/message throughput (per second)
- Error rate
- Virtual users (VUs)
- Test duration

**3b. Analyze Database Performance:**

From `db-analysis-baseline.txt`, identify:
- Slow queries (execution time >100ms)
- Sequential scans (queries not using indexes)
- Connection pool utilization
- Cache hit ratio
- Most called queries

**3c. Analyze Redis Performance:**

From `redis-analysis-baseline.txt`, calculate:
- Hit rate: `keyspace_hits / (keyspace_hits + keyspace_misses)`
- Memory usage
- Connected clients (peak)
- Slow commands (if any)

**3d. Monitor Application Resources:**

Document from system monitoring:
- CPU usage (peak and average)
- Memory usage (heap and non-heap)
- GC pause times (if JVM logging enabled)
- Number of active threads

### Step 4: Document Actual Findings in performance-benchmarks.md

You MUST fill in ALL placeholder entries marked "[TBF]" or "[To be filled]" with actual data:

**4a. Test Environment Section (lines 131-165):**
- Replace ALL hardware/software placeholders with actual specifications
- Document your local machine specs (CPU cores, RAM, disk type)
- Document PostgreSQL version: `psql --version`
- Document Redis version: `redis-cli --version`
- Document k6 version: `k6 version`

**4b. Baseline Performance Results (lines 408-500+):**
- Fill in ALL latency tables with actual p50/p95/p99 values from k6 output
- Fill in throughput metrics (messages/sec, requests/sec)
- Fill in error rates
- Fill in resource utilization (CPU %, memory MB, connections used)

**4c. Bottleneck Analysis Section:**
- Document ACTUAL bottlenecks found (or state "No bottlenecks identified at scaled load")
- Provide evidence: paste slow query results, show sequential scans, cite metrics
- Explain root causes based on collected data

**4d. Mark Test Date:**
- Replace `[To be filled]` test date with actual date: `date "+%Y-%m-%d %H:%M:%S"`

### Step 5: Apply Optimizations (If Bottlenecks Found)

**Only if Step 3 identifies specific bottlenecks:**

**5a. Database Optimizations:**
- If sequential scans found: Add missing indexes (create new migration file)
- If connection pool exhausted: Increase `DB_POOL_MAX_SIZE` environment variable
- If slow queries found: Analyze query plans with `EXPLAIN ANALYZE` and optimize

**5b. Redis Optimizations:**
- If hit rate <90%: Review cache key patterns and TTL settings
- If connection issues: Increase `REDIS_POOL_MAX_SIZE`

**5c. JVM Optimizations:**
- If GC pauses high: Apply recommended JAVA_OPTS from application.properties comments
- If heap exhaustion: Increase heap size beyond 1GB

**5d. Document Optimizations Applied:**
In `docs/performance-benchmarks.md`, fill the "Optimizations Applied" section with:
- What was changed (specific config parameter or code change)
- Why (based on identified bottleneck)
- Expected improvement

### Step 6: Execute Validation Tests

**If optimizations were applied in Step 5**, re-run all load tests:

```bash
# Re-run with optimized configuration
k6 run -e VUS=1000 -e ROOMS=100 --out json=results-voting-optimized.json scripts/load-test-voting.js
k6 run --out json=results-api-optimized.json scripts/load-test-api.js
k6 run -e RECONNECTIONS_PER_MIN=100 --out json=results-reconnection-optimized.json scripts/load-test-reconnection-storm.js
```

### Step 7: Document Before/After Comparison

In the "Validation Results" section of `docs/performance-benchmarks.md`:

- Create before/after comparison tables
- Show percentage improvement in latencies
- Show improvement in error rates (if applicable)
- Show improvement in resource utilization
- Check all acceptance criteria checkboxes that passed
- Document which NFRs were met and which were not (with explanations)

### Step 8: Handle Scaled-Down Testing

Since you cannot test at full 5,000 connection scale locally:

**In the performance benchmarks document, add a section:**

```markdown
## Testing Limitations

**Environment:** Local development machine (not production-scale)

**Scale:** Tests conducted at 20% of production target:
- WebSocket connections: 1,000 (vs. 5,000 target)
- Reconnections/min: 100 (vs. 1,000 target)

**Extrapolation:** Results indicate [likely/unlikely] to meet NFRs at full production scale because [reasoning based on resource utilization trends].

**Recommendation:** Conduct full-scale testing in staging environment with production-equivalent resources before deployment.
```

---

## Acceptance Criteria for Completion

This task is ONLY complete when:

- [ ] Compilation errors fixed, application starts successfully
- [ ] All 3 k6 load tests executed with captured JSON output files
- [ ] Database analysis performed with results saved to file
- [ ] Redis analysis performed with results saved to file
- [ ] `docs/performance-benchmarks.md` has **ZERO** "[TBF]" or "[To be filled]" placeholders remaining
- [ ] All latency tables filled with actual numbers (p50, p95, p99)
- [ ] All throughput metrics filled with actual numbers
- [ ] All resource utilization metrics filled with actual numbers
- [ ] Test environment section completely filled (hardware, software versions)
- [ ] Bottleneck analysis section documents ACTUAL findings (even if "no bottlenecks at scaled load")
- [ ] If bottlenecks found: Optimizations applied, validation tests run, before/after comparison documented
- [ ] Validation results section has checkboxes marked based on actual test results
- [ ] Each acceptance criterion has evidence (pass/fail with specific metrics)
- [ ] Scaled-down testing limitations documented with extrapolation reasoning

---

## Key Reminders

1. **You MUST execute the load tests** - creating scripts is not sufficient
2. **You MUST collect actual performance data** - templates must be filled with real numbers
3. **You MUST fix compilation errors first** - the application must be running before testing
4. **Document what you FOUND, not what you EXPECT** - base analysis on actual data
5. **Local testing at reduced scale is acceptable** - but must be documented clearly
6. **If no bottlenecks found at scaled load** - document this as a positive finding
7. **Zero placeholders allowed** - all "[TBF]" entries must be replaced with actual content

---

## Expected Deliverables After Fix

1. **Source Code Changes:**
   - `backend/src/main/java/com/scrumpoker/metrics/BusinessMetrics.java` (fixed imports)
   - Potentially new migration file if indexes added based on findings

2. **Test Result Files:**
   - `results-voting-baseline.json` (k6 output)
   - `results-api-baseline.json` (k6 output)
   - `results-reconnection-baseline.json` (k6 output)
   - `db-analysis-baseline.txt` (database performance)
   - `redis-analysis-baseline.txt` (Redis metrics)
   - If optimizations applied: `results-*-optimized.json` files

3. **Updated Documentation:**
   - `docs/performance-benchmarks.md` (all placeholders filled, actual data present)
   - Potentially `PERFORMANCE_TESTING_SUMMARY.md` (update status from "Ready" to "Complete" with actual results)

4. **Evidence of Execution:**
   - Console output showing k6 test summaries
   - Metrics proving tests were run (latency histograms, throughput graphs)
   - Clear before/after comparison if optimizations were applied

---

## Timeline Estimate

- **Step 1 (Fix compilation):** 15 minutes
- **Step 2 (Execute baseline tests):** 30-45 minutes (including infrastructure setup)
- **Step 3 (Analyze results):** 45-60 minutes
- **Step 4 (Document findings):** 30-45 minutes
- **Step 5 (Apply optimizations - if needed):** 30-60 minutes
- **Step 6 (Validation tests - if needed):** 20 minutes
- **Step 7 (Before/after comparison):** 20 minutes
- **Step 8 (Document limitations):** 15 minutes

**Total Estimated Time:** 3-5 hours depending on whether optimizations are needed.

---

**CRITICAL:** This task is about **conducting performance testing**, not just **preparing for it**. The actual test execution and data collection is the core deliverable. Scripts and documentation without execution results is incomplete work.
