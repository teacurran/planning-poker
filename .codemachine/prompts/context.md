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
  "input_files": [
    ".codemachine/artifacts/architecture/05_Operational_Architecture.md"
  ],
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
<!-- anchor: monitoring-metrics -->
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
```

### Context: deployment-strategy (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: deployment-strategy -->
#### Deployment Strategy

**Containerization (Docker):**
- **Base Image:** `registry.access.redhat.com/ubi9/openjdk-17-runtime` (Red Hat Universal Base Image for Quarkus)
- **Build Mode:** JVM mode for faster build times, potential future migration to native mode for reduced memory footprint
- **Multi-Stage Build:**
  1. Maven build stage: Compile Java, run tests, package Quarkus uber-jar
  2. Runtime stage: Copy jar to minimal JRE image, set entrypoint
- **Image Registry:** AWS ECR (Elastic Container Registry) with vulnerability scanning enabled
- **Tagging Strategy:** Semantic versioning (`v1.2.3`) + Git commit SHA for traceability

**Orchestration (Kubernetes):**
- **Cluster:** AWS EKS (managed Kubernetes) with 3 worker nodes (t3.large instances) across 3 availability zones
- **Namespaces:** `production`, `staging`, `development` for environment isolation
- **Deployment Objects:**
  - `Deployment` for Quarkus application (rolling update strategy, max surge: 1, max unavailable: 0 for zero-downtime)
  - `Service` (ClusterIP) for internal pod-to-pod communication
  - `Ingress` (ALB Ingress Controller) for external HTTPS traffic with sticky sessions enabled
  - `ConfigMap` for environment-specific configuration (feature flags, API endpoints)
  - `Secret` for sensitive data (database credentials, OAuth secrets, JWT keys)
  - `HorizontalPodAutoscaler` for auto-scaling based on CPU and custom metrics
- **Storage:** `PersistentVolumeClaim` for temporary file storage (report generation), backed by EBS volumes
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** This is the Quarkus application configuration file. Prometheus metrics are **already enabled** with basic configuration at lines 201-205. The metrics endpoint is configured as `/q/metrics` and Micrometer is enabled.
    *   **Recommendation:** You DO NOT need to enable Micrometer or Prometheus - it's already configured. You only need to CREATE the custom business metrics class and ensure it integrates with the existing Micrometer registry.
    *   **Key Configuration Lines:**
        - Line 203: `quarkus.micrometer.enabled=true`
        - Line 204: `quarkus.micrometer.export.prometheus.enabled=true`
        - Line 205: `quarkus.micrometer.export.prometheus.path=/q/metrics`

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/ConnectionRegistry.java`
    *   **Summary:** This class manages WebSocket connection lifecycle and tracking. It has thread-safe methods to count connections per room and total connections.
    *   **Recommendation:** You MUST integrate this class with your BusinessMetrics class to track WebSocket connections. The class provides these critical methods:
        - `getTotalConnectionCount()` (line 284): Returns total active WebSocket connections across all rooms
        - `getActiveRoomCount()` (line 293): Returns number of active rooms with connections
        - `getConnectionCount(String roomId)` (line 142): Returns connection count for a specific room
    *   **Integration Point:** Your BusinessMetrics class should @Inject this ConnectionRegistry and use it to provide gauge values for `scrumpoker_active_sessions_total` and `scrumpoker_websocket_connections_total`.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Summary:** This service handles voting operations including casting votes and completing rounds. This is where vote and round completion events occur.
    *   **Recommendation:** You SHOULD inject your BusinessMetrics class into VotingService and increment the appropriate counters when:
        - A vote is cast: increment `scrumpoker_votes_cast_total` counter (potentially in the `castVote` method around line 69-94)
        - A round is completed: increment `scrumpoker_rounds_completed_total` counter with label `consensus_reached=true/false` (likely in the reveal round logic)
    *   **Pattern:** Use `@Inject` to inject BusinessMetrics, then call methods like `metrics.incrementVotesCast(deckType)` at the appropriate points in the service logic.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Summary:** This service manages subscription lifecycle and tracks subscription status for users.
    *   **Recommendation:** You SHOULD inject your BusinessMetrics class here to track active subscription counts and MRR. Consider adding metric updates when subscriptions are created, upgraded, or canceled.
    *   **Integration Points:**
        - Track active subscription counts by tier (FREE, PRO, PRO_PLUS, ENTERPRISE)
        - Calculate and expose Monthly Recurring Revenue (MRR) based on active subscriptions

*   **File:** `infra/kubernetes/base/deployment.yaml`
    *   **Summary:** This is the Kubernetes Deployment manifest that defines the Quarkus application pods. It includes resource limits, health probes, and labels.
    *   **Recommendation:** The deployment already has the correct labels for Prometheus discovery (`app: scrum-poker-backend`, `app.kubernetes.io/name: scrum-poker-backend`). Your ServiceMonitor MUST use these label selectors.
    *   **Key Details:**
        - Container port 8080 is exposed (line 39)
        - Pods have labels that ServiceMonitor should target (lines 24-28)
        - The metrics endpoint `/q/metrics` will be available on port 8080

*   **File:** `infra/local/prometheus.yml`
    *   **Summary:** This is the local development Prometheus configuration. It already scrapes the Quarkus application at `host.docker.internal:8080/q/metrics`.
    *   **Recommendation:** Use this as a reference for the Kubernetes ServiceMonitor. The scrape path is `/q/metrics` (line 41), scrape interval is 10s (line 39), and the job is labeled with `application: planning-poker` (line 45).
    *   **Note:** This file is for local development only. You need to create a ServiceMonitor for Kubernetes production environments.

*   **File:** `infra/local/grafana/dashboards/quarkus-dashboard.json`
    *   **Summary:** This is an existing Grafana dashboard (likely a basic Quarkus metrics dashboard). It's 17KB and uses Prometheus as its datasource.
    *   **Recommendation:** You can use this as a **template** for understanding the Grafana dashboard JSON structure, but you need to create 4 NEW dashboards as specified in the task. Study the structure (panels, queries, datasource configuration) but create fresh dashboards with the specific metrics and panels required by the architecture.

### Implementation Tips & Notes

*   **Tip:** Quarkus Micrometer integration is already set up. You just need to create a new `@ApplicationScoped` CDI bean in the `com.scrumpoker.metrics` package (you'll need to create this package) that uses the Micrometer `MeterRegistry` to register custom metrics.

*   **Note:** For Gauge metrics that track dynamic values (like connection counts), use `Gauge.builder()` with a lambda function that queries the current value from your services. For example:
    ```java
    Gauge.builder("scrumpoker_websocket_connections_total", connectionRegistry, ConnectionRegistry::getTotalConnectionCount)
        .description("Total active WebSocket connections")
        .register(registry);
    ```

*   **Note:** For Counter metrics (votes cast, rounds completed), inject the BusinessMetrics bean into the relevant service classes (VotingService, BillingService) and call increment methods at the appropriate points in the business logic.

*   **Tip:** When creating the ServiceMonitor YAML, use the Prometheus Operator CRD format. Target pods with the selector `matchLabels: { app: scrum-poker-backend }` and specify the endpoint as `port: http` (matching the port name in the Service) with `path: /q/metrics`.

*   **Tip:** For Grafana dashboards, each dashboard JSON should include:
    - A datasource reference to Prometheus (uid: "prometheus")
    - Multiple panels with appropriate visualizations (time series graphs for rates/latencies, gauges for current values)
    - Proper PromQL queries for each metric
    - Meaningful titles, descriptions, and units
    - A refresh interval (e.g., 5s or 10s for real-time dashboards)

*   **Warning:** When you create Counter metrics for votes and rounds, ensure you add appropriate labels (tags) as specified in the architecture. For example:
    - `scrumpoker_votes_cast_total` should have a label `deck_type` (e.g., "fibonacci", "t-shirt", "modified-fibonacci")
    - `scrumpoker_rounds_completed_total` should have a label `consensus_reached` (true/false)

*   **Note:** The directory structure shows that `infra/monitoring/` does not yet exist. You need to create the following directory structure:
    ```
    infra/monitoring/
    ├── prometheus/
    │   └── servicemonitor.yaml
    └── grafana/
        └── dashboards/
            ├── application-overview.json
            ├── websocket-metrics.json
            ├── business-metrics.json
            └── infrastructure.json
    ```

*   **Tip:** For subscription metrics, you'll need to query the database or repository to count active subscriptions by tier. Consider using a scheduled task (Quarkus @Scheduled) to periodically update gauge values, or make the gauge callback query the repository on-demand.

*   **Warning:** Ensure your BusinessMetrics class is injected AFTER the application context is fully initialized. Use `@Observes StartupEvent` if you need to initialize metrics on application startup.

*   **Note:** For the MRR (Monthly Recurring Revenue) gauge, you'll need to calculate the sum based on subscription tiers. Reference the Stripe price configuration in `application.properties` (lines 150-156) to determine tier pricing, though the actual price amounts are stored in Stripe.

### Package Structure Requirements

You MUST create a new package: `backend/src/main/java/com/scrumpoker/metrics/`

This package should contain:
- `BusinessMetrics.java` - The main metrics registry class
- Potentially additional classes for metric collection if needed

### Integration Points Summary

1. **ConnectionRegistry** → Provides real-time WebSocket connection counts
2. **VotingService** → Trigger points for vote and round completion counters
3. **BillingService** → Source for subscription and revenue metrics
4. **RoomService** → May be needed for active room/session tracking
5. **Existing Micrometer MeterRegistry** → Already available via CDI injection

### Acceptance Criteria Verification Strategy

To verify each acceptance criterion:

1. **"Prometheus scrapes application metrics endpoint"**
   - Start the Quarkus application
   - Check `curl http://localhost:8080/q/metrics` shows metrics output
   - If using ServiceMonitor in Kubernetes, verify the ServiceMonitor resource is created and targets are discovered in Prometheus UI

2. **"Custom business metrics appear in Prometheus targets"**
   - Check the `/q/metrics` endpoint includes your custom metrics (e.g., `scrumpoker_active_sessions_total`)
   - In Prometheus UI, verify these metrics are queryable

3. **"Grafana application dashboard displays request rate and latency"**
   - Import the application-overview.json dashboard
   - Verify panels show data from Prometheus queries
   - Generate some REST API traffic and confirm graphs update

4. **"WebSocket dashboard shows connection count"**
   - Open a few WebSocket connections to rooms
   - Check the websocket-metrics dashboard shows the connection count gauge
   - Disconnect and verify count decreases

5. **"Business metrics dashboard shows subscription counts by tier"**
   - Ensure test data includes subscriptions at different tiers
   - Verify the business-metrics dashboard displays subscription distribution

6. **"Dashboards load without errors in Grafana"**
   - Import all 4 JSON dashboards
   - Check each loads without "Panel Error" or "Data source not found" messages
   - Verify all queries return data (may be zero if no activity, but should not error)
