# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I8.T2",
  "iteration_id": "I8",
  "iteration_goal": "Prepare application for production deployment including Kubernetes manifests, monitoring setup, performance optimization, security hardening, documentation, and final end-to-end testing.",
  "description": "Set up Prometheus metrics collection and create Grafana dashboards. Configure Prometheus to scrape Quarkus `/q/metrics` endpoint (ServiceMonitor for Prometheus Operator or scrape config). Create custom business metrics in Quarkus: `scrumpoker_active_sessions`, `scrumpoker_websocket_connections`, `scrumpoker_votes_cast_total`, `scrumpoker_subscriptions_active`. Create Grafana dashboards: Application Overview (requests, errors, latency, active sessions), WebSocket Metrics (connections, message rate, disconnections), Business Metrics (active subscriptions by tier, voting activity, room creation rate), Infrastructure (pod CPU/memory, database connections, Redis hit rate). Export dashboards as JSON for version control.",
  "agent_type_hint": "SetupAgent",
  "inputs": "Observability requirements from architecture blueprint, Prometheus/Grafana patterns, Business metrics list",
  "input_files": [".codemachine/artifacts/architecture/05_Operational_Architecture.md"],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/metrics/BusinessMetrics.java",
    "infra/monitoring/prometheus/servicemonitor.yaml",
    "infra/monitoring/grafana/dashboards/application-overview.json",
    "infra/monitoring/grafana/dashboards/websocket-metrics.json",
    "infra/monitoring/grafana/dashboards/business-metrics.json",
    "infra/monitoring/grafana/dashboards/infrastructure.json"
  ],
  "deliverables": "BusinessMetrics class with Micrometer gauges/counters, ServiceMonitor configuring Prometheus to scrape app pods, 4 Grafana dashboards (exported as JSON), Dashboard panels: request rate, error rate, p95 latency, active sessions gauge, WebSocket dashboard: connection count, message throughput, reconnection rate, Business dashboard: MRR, subscription tier distribution, votes per session",
  "acceptance_criteria": "Prometheus scrapes application metrics endpoint, Custom business metrics appear in Prometheus targets, Grafana application dashboard displays request rate and latency, WebSocket dashboard shows connection count (test with active connections), Business metrics dashboard shows subscription counts by tier, Dashboards load without errors in Grafana",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: monitoring-metrics (from 05_Operational_Architecture.md)

```markdown
##### Monitoring & Metrics

**Prometheus Metrics (Quarkus Micrometer Integration):**

**Business Metrics:**
- `scrumpoker_active_sessions_total` (Gauge) - Current number of active rooms
- `scrumpoker_websocket_connections_total` (Gauge) - Active WebSocket connections
- `scrumpoker_votes_cast_total` (Counter) - Cumulative votes cast, labeled by `deck_type`
- `scrumpoker_rounds_completed_total` (Counter) - Completed estimation rounds, labeled by `consensus_reached`
- `scrumpoker_subscriptions_active_total` (Gauge) - Active subscriptions by `tier`
- `scrumpoker_revenue_monthly_cents` (Gauge) - Monthly recurring revenue (MRR) in cents

**Application Metrics:**
- `http_server_requests_seconds` (Histogram) - REST API latency distribution, labeled by `uri`, `method`, `status`
- `websocket_message_latency_seconds` (Histogram) - WebSocket message processing time, labeled by `message_type`
- `db_query_duration_seconds` (Histogram) - Database query execution time, labeled by `query_name`
- `redis_operation_duration_seconds` (Histogram) - Redis command latency, labeled by `command`
- `jvm_memory_used_bytes` (Gauge) - JVM heap/non-heap memory usage
- `jvm_gc_pause_seconds` (Histogram) - Garbage collection pause duration

**Infrastructure Metrics:**
- `kube_pod_status_phase` (Gauge) - Kubernetes pod health status
- `kube_deployment_replicas` (Gauge) - Desired vs. available replicas for auto-scaling monitoring
- `node_cpu_seconds_total` (Counter) - Node-level CPU usage
- `node_memory_MemAvailable_bytes` (Gauge) - Available memory on nodes

**Alerting Rules (Prometheus Alertmanager):**
- **Critical:**
  - `HighErrorRate` - API error rate >5% for 5 minutes → PagerDuty escalation
  - `DatabaseConnectionPoolExhausted` - Available connections <10% for 2 minutes
  - `WebSocketDisconnectionSpike` - Disconnection rate >20% baseline for 3 minutes
- **Warning:**
  - `SlowAPIResponse` - p95 latency >1s for 10 minutes → Slack notification
  - `HighMemoryUsage` - JVM heap >85% for 15 minutes
  - `ReplicaCountMismatch` - Deployment desired ≠ available for 5 minutes

**Dashboards (Grafana):**
1. **Application Overview:** Active sessions, WebSocket connections, request rate, error rate
2. **Real-Time Performance:** API latency (p50/p95/p99), WebSocket message latency, database query time
3. **Business Metrics:** Daily active rooms, votes per session, subscription tier distribution, MRR trend
4. **Infrastructure Health:** Pod CPU/memory, replica count, database connection pool, Redis hit rate
5. **WebSocket Deep Dive:** Connection lifecycle, message type distribution, reconnection rate, Pub/Sub lag

**Distributed Tracing (Optional for MVP+):**
- **Framework:** OpenTelemetry with Jaeger backend
- **Traces:** End-to-end spans from browser HTTP request → API → database → Redis Pub/Sub → WebSocket broadcast
- **Correlation:** `traceId` propagated via HTTP headers (`traceparent`) and WebSocket message metadata
- **Sampling:** 10% sampling in production, 100% for error traces
```

### Context: logging-strategy (from 05_Operational_Architecture.md)

```markdown
##### Logging Strategy

**Structured Logging (JSON Format):**
- **Library:** SLF4J with Quarkus Logging JSON formatter
- **Schema:** Each log entry includes:
  - `timestamp` - ISO8601 format
  - `level` - DEBUG, INFO, WARN, ERROR
  - `logger` - Java class name
  - `message` - Human-readable description
  - `correlationId` - Unique request/WebSocket session identifier for distributed tracing
  - `userId` - Authenticated user ID (omitted for anonymous)
  - `roomId` - Estimation room context
  - `action` - Semantic action (e.g., `vote.cast`, `room.created`, `subscription.upgraded`)
  - `duration` - Operation latency in milliseconds (for timed operations)
  - `error` - Exception stack trace (for ERROR level)

**Log Levels by Environment:**
- **Development:** DEBUG (verbose SQL queries, WebSocket message payloads)
- **Staging:** INFO (API requests, service method calls, integration events)
- **Production:** WARN (error conditions, performance degradation, security events)

**Log Aggregation:**
- **Stack:** Loki (log aggregation) + Promtail (log shipper) + Grafana (visualization)
- **Alternative:** AWS CloudWatch Logs or GCP Cloud Logging for managed service
- **Retention:** 30 days for application logs, 90 days for audit logs (compliance requirement)
- **Query Optimization:** Indexed fields: `correlationId`, `userId`, `roomId`, `action`, `level`
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### ✅ ALREADY COMPLETE - BusinessMetrics.java
*   **File:** `backend/src/main/java/com/scrumpoker/metrics/BusinessMetrics.java`
    *   **Summary:** This file contains the complete implementation of all custom business metrics required by the task. It includes:
        - All 6 required business metrics: `scrumpoker_active_sessions_total`, `scrumpoker_websocket_connections_total`, `scrumpoker_votes_cast_total`, `scrumpoker_rounds_completed_total`, `scrumpoker_subscriptions_active_total`, `scrumpoker_revenue_monthly_cents`
        - Proper Micrometer gauge and counter implementations
        - Integration with ConnectionRegistry for WebSocket metrics
        - Integration with SubscriptionRepository for subscription metrics
        - Public methods `incrementVotesCast(String deckType)` and `incrementRoundsCompleted(boolean consensusReached)` for event tracking
    *   **Status:** **COMPLETE** - This file is fully implemented and meets all requirements.
    *   **Critical Note:** The BusinessMetrics class is **NOT YET INTEGRATED** into VotingService. You MUST add metrics tracking calls in VotingService.

#### ✅ ALREADY COMPLETE - ServiceMonitor Configuration
*   **File:** `infra/monitoring/prometheus/servicemonitor.yaml`
    *   **Summary:** This file contains the complete Kubernetes ServiceMonitor custom resource for Prometheus Operator. It configures:
        - Service selector matching `app: scrum-poker-backend`
        - Scrape endpoint: `/q/metrics` on port `http`
        - 10-second scrape interval with 5-second timeout
        - Relabeling rules to add Kubernetes metadata (namespace, pod, node, service)
        - Metric relabeling to add application and component labels
        - Namespace selector for production, staging, and development environments
    *   **Status:** **COMPLETE** - This file is production-ready.
    *   **Recommendation:** You SHOULD verify this configuration is correct, but no changes are needed.

#### ✅ ALREADY COMPLETE - Grafana Dashboards
*   **Files:**
    - `infra/monitoring/grafana/dashboards/application-overview.json` (554 lines)
    - `infra/monitoring/grafana/dashboards/websocket-metrics.json` (591 lines)
    - `infra/monitoring/grafana/dashboards/business-metrics.json` (736 lines)
    - `infra/monitoring/grafana/dashboards/infrastructure.json` (753 lines)
    *   **Summary:** All four required Grafana dashboards are already created with:
        - Proper Prometheus data source configuration
        - Multiple panels showing key metrics
        - Business metrics queries using the custom metrics (e.g., `scrumpoker_revenue_monthly_cents / 100`, `scrumpoker_subscriptions_active_total`)
        - Standard Quarkus metrics queries for HTTP requests and JVM metrics
    *   **Status:** **COMPLETE** - All dashboards are fully implemented.
    *   **Verification Needed:** You SHOULD verify the dashboards load correctly in Grafana and display data.

#### 🔧 NEEDS INTEGRATION - ConnectionRegistry.java
*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/ConnectionRegistry.java`
    *   **Summary:** This file manages WebSocket connections and provides the data sources for WebSocket metrics:
        - `getTotalConnectionCount()` returns total active WebSocket connections (used by BusinessMetrics)
        - `getActiveRoomCount()` returns number of rooms with active connections (used by BusinessMetrics)
        - Thread-safe ConcurrentHashMap implementation for tracking sessions
    *   **Status:** **COMPLETE** - This file is already integrated with BusinessMetrics.
    *   **Note:** The BusinessMetrics class correctly uses lambda suppliers to call these methods.

#### 🔧 NEEDS INTEGRATION - VotingService.java
*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Summary:** This service handles voting operations and round lifecycle. It contains the business logic that needs to trigger metrics:
        - `castVote()` method persists votes and publishes events - **NEEDS** to call `businessMetrics.incrementVotesCast(deckType)`
        - `revealRound()` method calculates statistics and marks round as revealed - **NEEDS** to call `businessMetrics.incrementRoundsCompleted(consensusReached)`
    *   **Critical Action Required:** You MUST inject BusinessMetrics into VotingService and add metric increment calls.
    *   **Recommendation:** Add the metric calls in the `.onItem().call()` chain after persistence to ensure metrics are only incremented on successful operations.

#### ✅ ALREADY CONFIGURED - Application Properties
*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Micrometer/Prometheus is already configured:
        ```properties
        quarkus.micrometer.enabled=true
        quarkus.micrometer.export.prometheus.enabled=true
        quarkus.micrometer.export.prometheus.path=/q/metrics
        ```
    *   **Status:** **COMPLETE** - Configuration is correct.
    *   **Note:** The metrics endpoint is exposed at `/q/metrics` as required by the ServiceMonitor.

#### ✅ ALREADY CONFIGURED - Maven Dependencies
*   **File:** `backend/pom.xml`
    *   **Summary:** The required Micrometer dependency is present:
        ```xml
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
        </dependency>
        ```
    *   **Status:** **COMPLETE** - All dependencies are correctly configured.

### Implementation Tips & Notes

#### ⚠️ CRITICAL - Task Status Assessment
**IMPORTANT:** Based on my analysis, this task is **ALMOST COMPLETE**. Here's what's done and what's missing:

**✅ COMPLETE (Already Implemented):**
1. ✅ BusinessMetrics class with all 6 custom metrics
2. ✅ ServiceMonitor YAML for Prometheus scraping
3. ✅ All 4 Grafana dashboards (application-overview, websocket-metrics, business-metrics, infrastructure)
4. ✅ Micrometer configuration in application.properties
5. ✅ Maven dependencies for Prometheus metrics
6. ✅ ConnectionRegistry integration for WebSocket metrics

**❌ MISSING (Needs Implementation):**
1. ❌ BusinessMetrics integration in VotingService (2 method calls)
2. ❌ Testing/verification that metrics work end-to-end

#### 🎯 PRIMARY TASK: Integrate BusinessMetrics into VotingService

You MUST complete the following integration:

1. **In VotingService.castVote() method:**
   - Inject BusinessMetrics via `@Inject BusinessMetrics businessMetrics;`
   - After successfully persisting the vote, add: `.onItem().call(vote -> { businessMetrics.incrementVotesCast(room.config.deckType); return Uni.createFrom().voidItem(); })`
   - The deck type should come from the Room.config.deckType field

2. **In VotingService.revealRound() method:**
   - After updating the round with consensus information, add: `.onItem().call(round -> { businessMetrics.incrementRoundsCompleted(round.consensusReached); return Uni.createFrom().voidItem(); })`

**Example Integration Pattern:**
```java
@Inject
BusinessMetrics businessMetrics;

public Uni<Vote> castVote(...) {
    return voteRepository.persist(vote)
        .onItem().call(v -> publishVoteRecordedEvent(roomId, v))
        .onItem().call(v -> {
            // Increment metrics after successful vote
            businessMetrics.incrementVotesCast(room.config.deckType);
            return Uni.createFrom().voidItem();
        });
}
```

#### 🔍 VERIFICATION STEPS

After integration, you MUST verify:

1. **Start the application:** `mvn quarkus:dev`
2. **Check metrics endpoint:** `curl http://localhost:8080/q/metrics | grep scrumpoker`
3. **Expected output:** You should see all custom metrics:
   - `scrumpoker_active_sessions_total`
   - `scrumpoker_websocket_connections_total`
   - `scrumpoker_votes_cast_total`
   - `scrumpoker_rounds_completed_total`
   - `scrumpoker_subscriptions_active_total`
   - `scrumpoker_revenue_monthly_cents`

4. **Simulate activity:**
   - Create a room, cast votes, reveal a round
   - Check metrics again to verify counters increment

5. **Dashboard verification:**
   - If Grafana is running locally (via docker-compose), verify dashboards load
   - Check that panels display data (may require activity in the app)

#### 💡 Additional Tips

*   **Tip:** The BusinessMetrics class uses `@Observes StartupEvent` to register all metrics on application startup. This is the correct Quarkus pattern.
*   **Tip:** The gauge metrics (active sessions, connections, subscriptions) use lambda suppliers, so they provide real-time values on every scrape. No explicit updates needed.
*   **Tip:** The counter metrics (votes cast, rounds completed) require explicit calls to `increment()`, which is why VotingService integration is critical.
*   **Note:** The ServiceMonitor uses labels to enrich metrics with Kubernetes metadata. This is useful for multi-environment deployments.
*   **Note:** The MRR calculation in BusinessMetrics uses hardcoded pricing ($20, $50, $200). In production, this should ideally query actual Stripe prices.
*   **Warning:** The subscription and MRR metrics perform synchronous database queries in the gauge lambda. For high-scrape-frequency environments, consider caching these values or using scheduled updates.

#### 📋 Task Completion Checklist

Before marking this task as complete, ensure:

- [ ] BusinessMetrics injected into VotingService
- [ ] `incrementVotesCast()` called after successful vote persistence
- [ ] `incrementRoundsCompleted()` called after round reveal
- [ ] Application starts without errors
- [ ] `/q/metrics` endpoint returns all custom business metrics
- [ ] Metrics increment correctly after simulating voting activity
- [ ] Grafana dashboards load without errors (if Grafana is running)
- [ ] ServiceMonitor configuration is valid (can test with `kubectl apply --dry-run=client`)

---

## Summary

**Task Status:** ~95% complete. All infrastructure, configuration, and dashboard code is implemented. Only the business logic integration (2 method calls in VotingService) is missing.

**Primary Action:** Integrate BusinessMetrics into VotingService to increment vote and round completion counters.

**Verification:** Test metrics endpoint and verify counters increment with application activity.

All monitoring infrastructure is production-ready once the VotingService integration is complete.
