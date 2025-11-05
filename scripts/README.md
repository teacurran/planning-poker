# Load Testing Scripts

This directory contains k6 load testing scripts for validating the Planning Poker application's performance against non-functional requirements (NFRs).

## Prerequisites

### Install k6

**macOS:**
```bash
brew install k6
```

**Linux:**
```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D00
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

**Windows:**
```powershell
choco install k6
```

Or download from: https://k6.io/docs/getting-started/installation/

### System Limits

For high connection count tests (5,000+ WebSockets), increase file descriptor limits:

**macOS/Linux (temporary):**
```bash
ulimit -n 65536
```

**Linux (permanent):**
Edit `/etc/security/limits.conf`:
```
* soft nofile 65536
* hard nofile 65536
```

## Test Scenarios

### 1. WebSocket Voting Load Test (`load-test-voting.js`)

Tests WebSocket connection stability, voting latency, and concurrent session handling.

**NFR Targets:**
- 500 concurrent rooms with 10 participants each (5,000 WebSocket connections)
- p95 latency <200ms for WebSocket messages
- Error rate <1%

**Run full-scale test:**
```bash
k6 run scripts/load-test-voting.js
```

**Run scaled-down for local testing (500 connections):**
```bash
k6 run -e VUS=500 -e ROOMS=50 scripts/load-test-voting.js
```

**Custom environment:**
```bash
k6 run -e BASE_URL=https://staging.example.com -e WS_URL=wss://staging.example.com scripts/load-test-voting.js
```

**Output results to JSON:**
```bash
k6 run --out json=results-voting.json scripts/load-test-voting.js
```

**Environment Variables:**
- `BASE_URL`: Base HTTP URL (default: `http://localhost:8080`)
- `WS_URL`: WebSocket URL (default: `ws://localhost:8080`)
- `ROOMS`: Number of rooms (default: `500`)
- `PARTICIPANTS`: Participants per room (default: `10`)
- `VUS`: Total virtual users (default: `ROOMS * PARTICIPANTS`)
- `RAMP_UP`: Ramp-up duration (default: `2m`)
- `SUSTAIN`: Sustain duration (default: `5m`)
- `RAMP_DOWN`: Ramp-down duration (default: `1m`)

### 2. REST API Load Test (`load-test-api.js`)

Tests REST API endpoints including room creation, listing, participant management, and subscription checkout.

**NFR Targets:**
- p95 latency <500ms for REST API endpoints
- 100 subscription checkouts per minute
- Error rate <1%

**Run full-scale test:**
```bash
k6 run scripts/load-test-api.js
```

**Run specific scenario only:**
```bash
k6 run -e SCENARIO=api_load scripts/load-test-api.js
k6 run -e SCENARIO=subscription_checkout scripts/load-test-api.js
```

**Custom environment:**
```bash
k6 run -e BASE_URL=https://staging.example.com scripts/load-test-api.js
```

**Environment Variables:**
- `BASE_URL`: Base HTTP URL (default: `http://localhost:8080`)
- `SCENARIO`: Run specific scenario: `api_load`, `subscription_checkout`, or `all` (default: `all`)

### 3. WebSocket Reconnection Storm Test (`load-test-reconnection-storm.js`)

Tests WebSocket connection resilience under rapid connect/disconnect/reconnect patterns, validates connection handling, timeout mechanisms, and state recovery.

**NFR Targets:**
- Support 1,000+ reconnections per minute
- Connection establishment time <1s under load
- Successful reconnection rate >95%
- Heartbeat timeout mechanism (60s) enforced
- Join timeout (10s) enforced

**Test Scenarios:**
- 50% Normal reconnections (connect, join, stay 2-5s, disconnect)
- 30% Rapid connect/disconnect (no join message to test join timeout)
- 15% Reconnect with message exchange (connect, join, vote, disconnect)
- 5% Heartbeat timeout tests (join but ignore heartbeat pings)

**Run full-scale test:**
```bash
k6 run scripts/load-test-reconnection-storm.js
```

**Run scaled-down for local testing (100 reconnections/min):**
```bash
k6 run -e RECONNECTIONS_PER_MIN=100 scripts/load-test-reconnection-storm.js
```

**Custom environment:**
```bash
k6 run -e BASE_URL=https://staging.example.com -e WS_URL=wss://staging.example.com scripts/load-test-reconnection-storm.js
```

**Output results to JSON:**
```bash
k6 run --out json=results-reconnection.json scripts/load-test-reconnection-storm.js
```

**Environment Variables:**
- `BASE_URL`: Base HTTP URL (default: `http://localhost:8080`)
- `WS_URL`: WebSocket URL (default: `ws://localhost:8080`)
- `RECONNECTIONS_PER_MIN`: Target reconnections per minute (default: `1000`)
- `TEST_DURATION`: Test duration (default: `5m`)

## Monitoring During Tests

### Real-time Metrics

k6 provides real-time console output during test execution. Watch for:
- Current VUs (virtual users)
- Request rate (req/s)
- Latency percentiles (p50, p95, p99)
- Error rate

### Grafana Dashboards

Open Grafana dashboards to monitor application-side metrics during load tests:

```bash
# Assuming Grafana is running on localhost:3000
open http://localhost:3000
```

Key dashboards:
- **Application Overview**: Request rate, latency, error rate
- **WebSocket Metrics**: Active connections, message throughput
- **Business Metrics**: Active sessions, voting activity
- **Infrastructure**: CPU, memory, DB connections, Redis metrics

### Database Monitoring

Monitor PostgreSQL during load test:

```bash
# Connect to database
psql -U scrum_poker -d scrum_poker_db

# Check active connections
SELECT count(*) FROM pg_stat_activity;

# Check slow queries
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;

# Check connection pool usage
SELECT state, count(*)
FROM pg_stat_activity
GROUP BY state;
```

### Redis Monitoring

Monitor Redis during load test:

```bash
redis-cli INFO stats      # Hit/miss ratio, commands/sec
redis-cli INFO memory     # Memory usage
redis-cli INFO clients    # Connected clients
redis-cli SLOWLOG GET 10  # Recent slow commands
```

## Analyzing Results

### k6 Summary Output

After test completion, k6 displays a summary with:
- Total requests/connections
- Latency percentiles (p50, p95, p99)
- Error rates
- Threshold pass/fail status

### JSON Output Analysis

For detailed analysis, output results to JSON:

```bash
k6 run --out json=results.json scripts/load-test-voting.js
```

Process with `jq`:

```bash
# Extract p95 latency
cat results.json | jq -r 'select(.type=="Point" and .metric=="ws_message_latency") | .data.value' | sort -n | awk '{all[NR] = $0} END{print all[int(NR*0.95)]}'

# Count errors
cat results.json | jq -r 'select(.type=="Point" and .metric=="message_errors") | .data.value' | wc -l
```

### Common Issues

**Issue: Connection refused**
- Ensure application is running: `curl http://localhost:8080/q/health/ready`
- Check WebSocket endpoint: `wscat -c ws://localhost:8080/ws/room/test`

**Issue: Too many open files**
- Increase file descriptor limit: `ulimit -n 65536`
- Check current limit: `ulimit -n`

**Issue: Database connection pool exhausted**
- Check application logs for: `FATAL: sorry, too many clients already`
- Increase `quarkus.datasource.reactive.max-size` in `application.properties`
- Increase PostgreSQL `max_connections` (default: 100)

**Issue: High latency during test**
- Monitor CPU/memory usage: `top` or `htop`
- Check database slow queries
- Check Redis SLOWLOG
- Review application logs for errors

**Issue: WebSocket connections drop**
- Check heartbeat configuration (30s interval, 60s timeout)
- Monitor network stability
- Check for connection pool exhaustion

## Performance Benchmarking Workflow

1. **Baseline Test**
   ```bash
   # Run with current configuration
   k6 run --out json=baseline-voting.json scripts/load-test-voting.js
   k6 run --out json=baseline-api.json scripts/load-test-api.js
   ```

2. **Capture Metrics**
   - Save k6 JSON output
   - Take Grafana dashboard screenshots
   - Export database slow query logs
   - Capture Redis INFO stats

3. **Analyze Bottlenecks**
   - Review latency percentiles (p95, p99)
   - Identify error spikes
   - Check resource utilization (CPU, memory, DB connections)
   - Run database EXPLAIN ANALYZE on slow queries

4. **Apply Optimizations**
   - Increase connection pool sizes
   - Add database indexes (if needed)
   - Tune JVM settings (heap size, GC)
   - Optimize slow queries

5. **Validation Test**
   ```bash
   # Run with optimized configuration
   k6 run --out json=optimized-voting.json scripts/load-test-voting.js
   k6 run --out json=optimized-api.json scripts/load-test-api.js
   ```

6. **Compare Results**
   - Before/after latency comparison
   - Before/after error rate comparison
   - Resource utilization improvement

7. **Document**
   - Update `docs/performance-benchmarks.md`
   - Include test environment details
   - Document configuration changes
   - Provide recommendations

## Troubleshooting

### Enable Verbose Logging

```bash
k6 run --verbose scripts/load-test-voting.js
```

### Run Single Virtual User

```bash
k6 run --vus 1 --iterations 1 scripts/load-test-voting.js
```

### Test WebSocket Connection Manually

```bash
# Install wscat
npm install -g wscat

# Connect to WebSocket
wscat -c ws://localhost:8080/ws/room/test-room

# Send join message
{"type":"room.join.v1","requestId":"test-123","payload":{"displayName":"Test User","role":"VOTER"}}
```

## Additional Resources

- [k6 Documentation](https://k6.io/docs/)
- [k6 WebSocket API](https://k6.io/docs/javascript-api/k6-ws/)
- [Performance Testing Best Practices](https://k6.io/docs/testing-guides/load-testing/)
- [Grafana k6 Integration](https://k6.io/docs/results-output/real-time/grafana/)
