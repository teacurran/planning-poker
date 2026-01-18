# Scrum Poker - Monitoring & Observability

This directory contains the monitoring and observability infrastructure for the Scrum Poker application, including Prometheus metrics collection and Grafana dashboards.

## Overview

The monitoring stack consists of:

1. **Prometheus** - Metrics collection and storage
2. **Grafana** - Visualization and dashboarding
3. **Custom Business Metrics** - Application-specific metrics tracked via Micrometer

## Architecture

```
┌─────────────────────┐
│  Scrum Poker App    │
│  (Quarkus/Vert.x)   │
│                     │
│  /q/metrics         │ ← Exposes Prometheus metrics
└──────────┬──────────┘
           │
           │ HTTP scrape (10s interval)
           │
┌──────────▼──────────┐
│   Prometheus        │
│   (TSDB)            │
└──────────┬──────────┘
           │
           │ PromQL queries
           │
┌──────────▼──────────┐
│   Grafana           │
│   (Dashboards)      │
└─────────────────────┘
```

## Directory Structure

```
infra/monitoring/
├── README.md                           # This file
├── prometheus/
│   └── servicemonitor.yaml            # Prometheus Operator ServiceMonitor
└── grafana/
    └── dashboards/
        ├── application-overview.json  # Application health & performance
        ├── websocket-metrics.json     # WebSocket connection metrics
        ├── business-metrics.json      # Business KPIs & revenue
        └── infrastructure.json        # Infrastructure & JVM metrics
```

## Metrics Exposed

### Business Metrics

Custom application metrics defined in `backend/src/main/java/com/scrumpoker/metrics/BusinessMetrics.java`:

| Metric Name | Type | Labels | Description |
|------------|------|--------|-------------|
| `scrumpoker_active_sessions_total` | Gauge | - | Current number of active rooms with connections |
| `scrumpoker_websocket_connections_total` | Gauge | - | Total active WebSocket connections across all rooms |
| `scrumpoker_votes_cast_total` | Counter | `deck_type` | Cumulative votes cast, labeled by deck type (fibonacci, t-shirt, etc.) |
| `scrumpoker_rounds_completed_total` | Counter | `consensus_reached` | Completed estimation rounds, labeled by consensus status (true/false) |
| `scrumpoker_subscriptions_active_total` | Gauge | `tier` | Active subscriptions by tier (FREE, PRO, PRO_PLUS, ENTERPRISE) |
| `scrumpoker_revenue_monthly_cents` | Gauge | - | Monthly recurring revenue (MRR) in cents |

### Application Metrics (Quarkus Micrometer)

Standard Quarkus/Micrometer metrics:

- `http_server_requests_seconds` - HTTP request latency histogram (labeled by `uri`, `method`, `status`)
- `websocket_message_latency_seconds` - WebSocket message processing time
- `db_query_duration_seconds` - Database query execution time
- `redis_operation_duration_seconds` - Redis command latency
- `jvm_memory_used_bytes` - JVM heap/non-heap memory usage
- `jvm_gc_pause_seconds` - Garbage collection pause duration
- `hikaricp_connections_active` - Active database connections
- `process_cpu_usage` - Process CPU usage percentage

### Infrastructure Metrics (Kubernetes)

Metrics provided by Prometheus Operator and exporters:

- `kube_pod_status_phase` - Pod health status
- `kube_deployment_status_replicas_available` - Available replicas for deployments
- `container_cpu_usage_seconds_total` - Container CPU usage
- `container_memory_working_set_bytes` - Container memory usage

## Prometheus Configuration

### ServiceMonitor

The `prometheus/servicemonitor.yaml` configures Prometheus Operator to scrape metrics from the Scrum Poker backend:

```yaml
spec:
  selector:
    matchLabels:
      app: scrum-poker-backend
  endpoints:
  - port: http
    path: /q/metrics
    interval: 10s
    scrapeTimeout: 5s
```

**Key Features:**

- **Scrape Interval:** 10 seconds
- **Scrape Timeout:** 5 seconds
- **Namespaces:** production, staging, development
- **Labels Added:** `kubernetes_namespace`, `kubernetes_pod_name`, `kubernetes_node_name`, `kubernetes_service_name`, `application=planning-poker`, `component=backend`

### Deployment

To deploy the ServiceMonitor to Kubernetes:

```bash
kubectl apply -f infra/monitoring/prometheus/servicemonitor.yaml -n production
```

Verify Prometheus is scraping:

```bash
# Check Prometheus targets
kubectl port-forward -n monitoring svc/prometheus-operated 9090:9090
# Open http://localhost:9090/targets
# Look for "scrum-poker-backend" endpoint with status=UP
```

## Grafana Dashboards

### 1. Application Overview (`application-overview.json`)

**Purpose:** High-level application health and performance monitoring

**Panels:**

- **Active Sessions** (Gauge) - Current number of active rooms
- **HTTP Request Rate** (Time Series) - Requests per second by endpoint
- **HTTP Error Rate (5xx)** (Time Series) - Server error rate percentage
- **HTTP Request Latency (p50/p95/p99)** (Time Series) - Latency percentiles by endpoint
- **WebSocket Connections** (Gauge) - Current WebSocket connection count
- **Active Sessions & Connections** (Time Series) - Combined view over time

**PromQL Examples:**

```promql
# Request rate
rate(http_server_requests_seconds_count{application="planning-poker"}[1m])

# Error rate
sum(rate(http_server_requests_seconds_count{application="planning-poker",status=~"5.."}[1m])) /
sum(rate(http_server_requests_seconds_count{application="planning-poker"}[1m]))

# p95 latency
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="planning-poker"}[1m])) by (le, uri))
```

**Refresh Rate:** 5 seconds
**Time Range:** Last 15 minutes

---

### 2. WebSocket Metrics (`websocket-metrics.json`)

**Purpose:** Real-time WebSocket connection and messaging metrics

**Panels:**

- **Active WebSocket Connections** (Gauge) - Current connection count
- **Active Rooms** (Gauge) - Number of rooms with active connections
- **Avg Connections per Room** (Stat) - Connection density metric
- **WebSocket Connection Count Over Time** (Time Series) - Connection trend
- **Active Rooms Over Time** (Time Series) - Room creation/closure trend
- **WebSocket Message Latency** (Time Series) - p50/p95/p99 message processing time
- **WebSocket Message Rate** (Time Series) - Messages per second by type

**PromQL Examples:**

```promql
# Connection count
scrumpoker_websocket_connections_total

# Message latency p95
histogram_quantile(0.95, sum(rate(websocket_message_latency_seconds_bucket{application="planning-poker"}[1m])) by (le, message_type))

# Message rate
rate(websocket_message_latency_seconds_count{application="planning-poker"}[1m])
```

**Refresh Rate:** 5 seconds
**Time Range:** Last 15 minutes

---

### 3. Business Metrics (`business-metrics.json`)

**Purpose:** Business KPIs, revenue tracking, and user engagement metrics

**Panels:**

- **Monthly Recurring Revenue (MRR)** (Stat) - Current MRR in USD
- **Active Subscriptions by Tier** (Pie Chart) - Subscription distribution (FREE, PRO, PRO_PLUS, ENTERPRISE)
- **Total Paid Subscriptions** (Stat) - Count of non-FREE subscriptions
- **Subscription Tier Distribution Over Time** (Time Series) - Subscription trends
- **MRR Trend** (Time Series) - Revenue growth over time
- **Votes Cast per Hour (by Deck Type)** (Time Series) - Voting activity by deck type
- **Rounds Completed per Hour** (Time Series) - Estimation round completion rate
- **Consensus Rate (Last Hour)** (Gauge) - Percentage of rounds reaching consensus
- **Total Votes (Last Hour)** (Stat) - Recent voting activity
- **Total Rounds (Last Hour)** (Stat) - Recent round completions

**PromQL Examples:**

```promql
# MRR in dollars
scrumpoker_revenue_monthly_cents / 100

# Subscription distribution
scrumpoker_subscriptions_active_total

# Votes per hour
increase(scrumpoker_votes_cast_total[1h])

# Consensus rate
sum(increase(scrumpoker_rounds_completed_total{consensus_reached="true"}[1h])) /
sum(increase(scrumpoker_rounds_completed_total[1h]))
```

**Refresh Rate:** 30 seconds
**Time Range:** Last 24 hours

---

### 4. Infrastructure (`infrastructure.json`)

**Purpose:** Infrastructure health, resource usage, and database/cache metrics

**Panels:**

- **Pod CPU Usage** (Time Series) - CPU usage percentage by pod
- **JVM Memory Usage** (Time Series) - Heap and non-heap memory by pod
- **JVM Garbage Collection Pause Time** (Time Series) - GC pause duration
- **Available Pod Replicas** (Gauge) - Kubernetes deployment replica count
- **Database Query Duration (p95)** (Time Series) - Database query latency
- **Active Database Connections** (Gauge) - HikariCP active connections
- **Database Connection Pool Usage** (Gauge) - Connection pool utilization percentage
- **Redis Operation Duration (p95)** (Time Series) - Redis command latency
- **Redis Cache Hit Rate** (Gauge) - Cache hit ratio

**PromQL Examples:**

```promql
# Pod CPU
rate(process_cpu_usage{application="planning-poker"}[1m]) * 100

# JVM Heap Memory
jvm_memory_used_bytes{application="planning-poker",area="heap"}

# Database query p95
histogram_quantile(0.95, sum(rate(db_query_duration_seconds_bucket{application="planning-poker"}[1m])) by (le, query_name))

# Connection pool usage
hikaricp_connections_active{application="planning-poker"} / hikaricp_connections_max{application="planning-poker"}

# Redis cache hit rate
sum(rate(redis_cache_hits_total{application="planning-poker"}[5m])) /
(sum(rate(redis_cache_hits_total{application="planning-poker"}[5m])) + sum(rate(redis_cache_misses_total{application="planning-poker"}[5m])))
```

**Refresh Rate:** 10 seconds
**Time Range:** Last 15 minutes

---

## Importing Dashboards

### Option 1: Grafana UI

1. Open Grafana: `http://grafana.yourdomain.com`
2. Navigate to **Dashboards** → **Import**
3. Upload JSON file or paste JSON content
4. Select Prometheus datasource (UID: `prometheus`)
5. Click **Import**

### Option 2: Grafana Provisioning (Kubernetes)

Create a ConfigMap with dashboard JSON:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grafana-dashboards-scrum-poker
  namespace: monitoring
data:
  application-overview.json: |
    <paste dashboard JSON>
  websocket-metrics.json: |
    <paste dashboard JSON>
  business-metrics.json: |
    <paste dashboard JSON>
  infrastructure.json: |
    <paste dashboard JSON>
```

Mount in Grafana deployment:

```yaml
volumeMounts:
- name: dashboards
  mountPath: /etc/grafana/provisioning/dashboards/scrum-poker
volumes:
- name: dashboards
  configMap:
    name: grafana-dashboards-scrum-poker
```

### Option 3: GitOps (Recommended)

Use GitOps tools like ArgoCD or Flux to sync dashboards from this repository:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: scrum-poker-monitoring
spec:
  source:
    repoURL: https://github.com/yourorg/planning-poker
    path: infra/monitoring/grafana/dashboards
    targetRevision: main
  destination:
    namespace: monitoring
```

## Testing Metrics

### Local Development

1. **Start application:**
   ```bash
   cd backend
   ./mvnw quarkus:dev
   ```

2. **View metrics endpoint:**
   ```bash
   curl http://localhost:8080/q/metrics
   ```

3. **Start local Grafana (Docker Compose):**
   ```bash
   cd infra/local
   docker-compose up -d grafana prometheus
   ```

4. **Access Grafana:**
   - URL: http://localhost:3000
   - Default credentials: admin/admin
   - Import dashboards from `infra/monitoring/grafana/dashboards/`

### Generate Test Metrics

**Trigger Business Metrics:**

```bash
# Create WebSocket connections (increases scrumpoker_websocket_connections_total)
wscat -c ws://localhost:8080/ws/room/test-room-1

# Cast votes via API (requires integration with VotingService)
curl -X POST http://localhost:8080/api/v1/rooms/test-room-1/vote \
  -H "Content-Type: application/json" \
  -d '{"userId": "user1", "value": "5"}'

# Reveal round (requires integration with VotingService)
curl -X POST http://localhost:8080/api/v1/rooms/test-room-1/reveal
```

**Generate HTTP Traffic:**

```bash
# Generate request rate
for i in {1..100}; do curl -s http://localhost:8080/q/health > /dev/null; sleep 0.1; done

# Trigger 5xx errors
curl -X GET http://localhost:8080/api/v1/rooms/nonexistent-room-id
```

## Alerting Rules

Recommended Prometheus alerting rules based on the architecture blueprint:

### Critical Alerts

```yaml
groups:
- name: scrum-poker-critical
  interval: 30s
  rules:
  - alert: HighErrorRate
    expr: |
      sum(rate(http_server_requests_seconds_count{application="planning-poker",status=~"5.."}[5m])) /
      sum(rate(http_server_requests_seconds_count{application="planning-poker"}[5m])) > 0.05
    for: 5m
    labels:
      severity: critical
      component: backend
    annotations:
      summary: "High error rate detected (>5%)"
      description: "API error rate is {{ $value | humanizePercentage }} for the last 5 minutes"

  - alert: DatabaseConnectionPoolExhausted
    expr: |
      (hikaricp_connections_max{application="planning-poker"} - hikaricp_connections_active{application="planning-poker"}) /
      hikaricp_connections_max{application="planning-poker"} < 0.10
    for: 2m
    labels:
      severity: critical
      component: database
    annotations:
      summary: "Database connection pool nearly exhausted (<10% available)"
      description: "Only {{ $value | humanizePercentage }} of database connections available"

  - alert: WebSocketDisconnectionSpike
    expr: |
      rate(scrumpoker_websocket_connections_total[5m]) < -0.20 * avg_over_time(scrumpoker_websocket_connections_total[1h])
    for: 3m
    labels:
      severity: critical
      component: websocket
    annotations:
      summary: "WebSocket disconnection spike detected (>20% of baseline)"
      description: "WebSocket connections dropping at {{ $value }} connections/sec"
```

### Warning Alerts

```yaml
groups:
- name: scrum-poker-warning
  interval: 1m
  rules:
  - alert: SlowAPIResponse
    expr: |
      histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="planning-poker"}[5m])) by (le, uri)) > 1
    for: 10m
    labels:
      severity: warning
      component: backend
    annotations:
      summary: "Slow API response time (p95 >1s)"
      description: "p95 latency is {{ $value }}s for endpoint {{ $labels.uri }}"

  - alert: HighMemoryUsage
    expr: |
      jvm_memory_used_bytes{application="planning-poker",area="heap"} /
      jvm_memory_max_bytes{application="planning-poker",area="heap"} > 0.85
    for: 15m
    labels:
      severity: warning
      component: jvm
    annotations:
      summary: "High JVM heap memory usage (>85%)"
      description: "JVM heap usage is {{ $value | humanizePercentage }} on pod {{ $labels.kubernetes_pod_name }}"

  - alert: ReplicaCountMismatch
    expr: |
      kube_deployment_spec_replicas{deployment="scrum-poker-backend"} !=
      kube_deployment_status_replicas_available{deployment="scrum-poker-backend"}
    for: 5m
    labels:
      severity: warning
      component: kubernetes
    annotations:
      summary: "Deployment replica count mismatch"
      description: "Desired replicas: {{ $value }}, Available replicas differ"
```

Deploy alerts:

```bash
kubectl apply -f infra/monitoring/prometheus/alerts.yaml -n monitoring
```

## Troubleshooting

### Metrics Not Appearing in Prometheus

1. **Check ServiceMonitor is applied:**
   ```bash
   kubectl get servicemonitor -n production
   ```

2. **Verify Service selector matches:**
   ```bash
   kubectl get svc -n production -l app=scrum-poker-backend
   ```

3. **Check Prometheus target status:**
   ```bash
   kubectl port-forward -n monitoring svc/prometheus-operated 9090:9090
   # Visit http://localhost:9090/targets
   ```

4. **Inspect Prometheus logs:**
   ```bash
   kubectl logs -n monitoring -l app=prometheus --tail=100
   ```

### Dashboard Shows "No Data"

1. **Verify datasource UID matches:**
   - Dashboards expect datasource UID: `prometheus`
   - Check in Grafana: Configuration → Data Sources → Prometheus → Settings

2. **Test PromQL query:**
   - Open Grafana → Explore
   - Run query: `scrumpoker_active_sessions_total`
   - If empty, check application metrics endpoint: `curl http://backend:8080/q/metrics`

3. **Check time range:**
   - Ensure dashboard time range includes data (default: last 15m)
   - Try extending to last 1h or 6h

### Business Metrics Show Zero

**Subscription Metrics:**

- The scheduled update task is disabled in MVP due to Reactive context issues
- Subscription counts default to 0
- **Workaround:** Manually trigger `BusinessMetrics.updateSubscriptionMetrics()` via JMX or create a REST endpoint:

```java
@GET
@Path("/admin/metrics/refresh")
public void refreshMetrics() {
    businessMetrics.updateSubscriptionMetrics();
}
```

**Vote/Round Metrics:**

- Ensure `BusinessMetrics.incrementVotesCast()` and `BusinessMetrics.incrementRoundsCompleted()` are called from VotingService
- Check application logs for metric registration: `grep "Business metrics initialized" logs/quarkus.log`

### High Cardinality Warnings

If Prometheus complains about high cardinality:

1. **Limit deck_type labels:**
   - Validate deck types to known set (fibonacci, t-shirt, etc.)
   - See `BusinessMetrics.incrementVotesCast()` - defaults unknown types to "unknown"

2. **Avoid URI path parameters in labels:**
   - Use route patterns instead of actual IDs
   - Example: `/api/v1/rooms/{id}` not `/api/v1/rooms/abc-123`

## Performance Considerations

### Metrics Cardinality

- **Low Cardinality (<100 series):** `scrumpoker_*` business metrics, JVM metrics
- **Medium Cardinality (<1000 series):** HTTP metrics (limited endpoints), WebSocket message types
- **High Cardinality (>1000 series):** Avoided by not using dynamic labels (user IDs, room IDs)

### Storage Requirements

Estimated Prometheus storage (30-day retention):

- **Time series count:** ~500 series
- **Scrape interval:** 10s
- **Sample size:** 2 bytes/sample
- **Storage estimate:** ~500 series × 259,200 samples/month × 2 bytes = **~250 MB/month**

Adjust retention in Prometheus configuration:

```yaml
spec:
  retention: 30d
  storage:
    volumeClaimTemplate:
      spec:
        resources:
          requests:
            storage: 10Gi
```

## References

- [Quarkus Micrometer Documentation](https://quarkus.io/guides/micrometer)
- [Prometheus Operator ServiceMonitor](https://prometheus-operator.dev/docs/operator/design/#servicemonitor)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/best-practices/)
- [PromQL Cheat Sheet](https://promlabs.com/promql-cheat-sheet/)

## License

Copyright © 2026 Scrum Poker Team. All rights reserved.
