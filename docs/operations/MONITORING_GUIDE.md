# Monitoring Guide

**Last Updated:** 2026-01-18
**Application:** Planning Poker
**Monitoring Stack:** Prometheus + Grafana

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Daily Monitoring Workflow](#daily-monitoring-workflow)
- [Grafana Dashboards](#grafana-dashboards)
- [Alert Triage Procedures](#alert-triage-procedures)
- [Key Metrics Baselines](#key-metrics-baselines)
- [Prometheus Query Examples](#prometheus-query-examples)
- [Business Metrics Interpretation](#business-metrics-interpretation)
- [Performance Metrics Interpretation](#performance-metrics-interpretation)

## Overview

This guide provides operational procedures for monitoring the Planning Poker application using Prometheus and Grafana. It is designed for on-call engineers, DevOps teams, and product managers who need to monitor application health and respond to alerts.

**Monitoring Architecture:**
- **Metrics Collection:** Prometheus with ServiceMonitor for Kubernetes
- **Visualization:** Grafana with pre-configured dashboards
- **Alerting:** Prometheus Alertmanager with PagerDuty/Slack integration
- **Metrics Retention:** 30 days (Prometheus), 90 days (long-term storage)

For detailed technical documentation on metrics and alert rules, see [infra/monitoring/README.md](../../infra/monitoring/README.md).

## Quick Start

### Access Grafana

**Option 1: Port Forward (Local Access)**
```bash
# Port-forward Grafana service
kubectl port-forward -n monitoring svc/grafana 3000:3000

# Open browser: http://localhost:3000
# Default credentials: admin/admin (change on first login)
```

**Option 2: Public Ingress (Production)**
```bash
# Access via public URL (if configured)
# URL: https://grafana.planningpoker.example.com
```

### Access Prometheus

**Port Forward Prometheus (for direct queries)**
```bash
# Port-forward Prometheus service
kubectl port-forward -n monitoring svc/prometheus 9090:9090

# Open browser: http://localhost:9090
```

### Quick Health Check Commands

```bash
# Check application health
curl https://planningpoker.example.com/q/health/ready

# Check pod status
kubectl get pods -n production -l app=scrum-poker-backend

# View recent logs
kubectl logs -n production -l app=scrum-poker-backend --tail=100
```

## Daily Monitoring Workflow

Follow this checklist every morning (5-10 minutes) to ensure system health.

### Morning Health Check (5 minutes)

**Step 1: Open Application Overview Dashboard**

Navigate to: Grafana → Dashboards → Application Overview

**Check:**
- [ ] **Request Rate:** Normal baseline (100-500 req/min)
  - Spike or drop indicates traffic anomaly
- [ ] **Error Rate:** <1% (green zone)
  - Yellow zone (1-5%): Investigate warning
  - Red zone (>5%): Critical, investigate immediately
- [ ] **P50/P95/P99 Latency:** P95 <500ms
  - P95 >1000ms indicates performance degradation
- [ ] **HTTP Status Codes:** Mostly 2xx responses
  - High 4xx: Client errors or validation failures
  - High 5xx: Server errors, check logs

**Step 2: Open WebSocket Metrics Dashboard**

Navigate to: Grafana → Dashboards → WebSocket Metrics

**Check:**
- [ ] **Active Connections:** 10-100 (normal load)
  - Sudden drop: Connection failures, check logs
- [ ] **Message Rate:** Proportional to active connections
  - Low message rate with high connections: Users idle
- [ ] **Disconnection Rate:** <5 disconnections/minute
  - High disconnection rate: Network issues or server instability

**Step 3: Open Infrastructure Dashboard**

Navigate to: Grafana → Dashboards → Infrastructure

**Check:**
- [ ] **Pod Replica Count:** Matches expected (2-4 for production)
  - HPA may have scaled up/down based on load
- [ ] **CPU Usage:** 20-50% (normal), <70% (healthy)
  - >70%: Consider scaling or optimization
- [ ] **Memory Usage:** 40-70% (normal), <85% (healthy)
  - >85%: Risk of OOMKill, increase limits
- [ ] **JVM Heap Usage:** 40-70% (normal)
  - >85%: GC pressure, tune JVM settings
- [ ] **Database Connection Pool:** 5-20 active connections
  - >40: Connection exhaustion risk, investigate queries
- [ ] **Redis Hit Rate:** >80%
  - <80%: Cache ineffective, review caching strategy

**Step 4: Check Active Alerts**

Navigate to: Grafana → Alerting → Alert Rules

**Check:**
- [ ] **No Critical Alerts Firing**
  - If alerts firing, follow [Alert Triage Procedures](#alert-triage-procedures)
- [ ] **Warning Alerts:** Review and assess
  - Document recurring warnings for trend analysis

**Step 5: Review Business Metrics (Weekly)**

Navigate to: Grafana → Dashboards → Business Metrics

**Check:**
- [ ] **Monthly Recurring Revenue (MRR):** Trending upward
- [ ] **Active Subscriptions:** Growing or stable
- [ ] **Active Sessions:** Consistent with user growth
- [ ] **Voting Activity:** Engagement metric trending upward

**Action Items:**
- Document any anomalies in daily log
- Create tickets for investigation if needed
- Escalate critical issues immediately

## Grafana Dashboards

For complete dashboard panel documentation, see [infra/monitoring/README.md](../../infra/monitoring/README.md) lines 133-275.

### Dashboard 1: Application Overview

**Purpose:** High-level application health and performance

**Key Panels:**
1. **Request Rate (req/min)** - Total HTTP requests per minute
2. **Error Rate (%)** - Percentage of HTTP 5xx errors
3. **Request Latency (P50/P95/P99)** - Response time percentiles
4. **HTTP Status Code Distribution** - 2xx, 4xx, 5xx breakdown
5. **Top Endpoints by Request Count** - Hottest API routes
6. **Top Endpoints by Latency** - Slowest API routes

**When to Use:**
- Daily health check
- Performance degradation investigation
- Capacity planning

**Screenshot Example:**
```
┌─────────────────────────────────────────────────────────────┐
│ Application Overview                                        │
├─────────────────────────────────────────────────────────────┤
│ Request Rate: 245 req/min  │ Error Rate: 0.3%              │
│ P50: 45ms  P95: 320ms  P99: 890ms                          │
├─────────────────────────────────────────────────────────────┤
│ [Graph: Request rate over last 6 hours - steady line]      │
│ [Graph: Latency percentiles - P95 under 500ms threshold]   │
│ [Table: Top endpoints - /api/sessions, /api/votes]         │
└─────────────────────────────────────────────────────────────┘
```

### Dashboard 2: WebSocket Metrics

**Purpose:** Real-time WebSocket connection monitoring

**Key Panels:**
1. **Active WebSocket Connections** - Current connected users
2. **WebSocket Message Rate** - Messages sent/received per second
3. **WebSocket Disconnection Rate** - Disconnections per minute
4. **WebSocket Message Latency** - Time to process messages

**When to Use:**
- WebSocket connection failures
- User reports of disconnections
- Real-time feature issues

**Normal Baselines:**
- Active Connections: 10-100 (varies by time of day)
- Message Rate: 5-50 msg/sec (proportional to connections)
- Disconnection Rate: <5 disconnections/min
- Message Latency: <100ms (P95)

### Dashboard 3: Business Metrics

**Purpose:** Business KPIs and product analytics

**Key Panels:**
1. **Monthly Recurring Revenue (MRR)** - Revenue in cents
2. **Active Subscriptions by Tier** - Free/Pro/Team distribution
3. **Active Sessions** - Current planning poker rooms
4. **Votes Cast by Deck Type** - Fibonacci, T-Shirt, etc.
5. **Rounds Completed** - Total consensus rounds
6. **Voting Activity Trend** - Daily voting volume

**When to Use:**
- Weekly business review
- Product feature adoption analysis
- Growth trend monitoring

**Interpretation:**
- **MRR Growth:** Target 10-20% month-over-month
- **Subscription Mix:** Pro/Team subscriptions indicate enterprise adoption
- **Active Sessions:** Peak during business hours (9am-5pm local time)
- **Voting Activity:** Indicates product engagement

### Dashboard 4: Infrastructure

**Purpose:** Kubernetes and infrastructure resource monitoring

**Key Panels:**
1. **Pod Replica Count** - Current/desired replicas
2. **CPU Usage per Pod** - CPU utilization percentage
3. **Memory Usage per Pod** - Memory utilization percentage
4. **JVM Heap Memory** - Used/max heap memory
5. **Database Connection Pool** - Active/idle/max connections
6. **Redis Operations** - Command rate and latency

**When to Use:**
- Resource exhaustion investigation
- Capacity planning
- Performance optimization

**Resource Thresholds:**

| Metric | Normal | Warning | Critical |
|--------|--------|---------|----------|
| CPU Usage | 20-50% | 50-70% | >70% |
| Memory Usage | 40-70% | 70-85% | >85% |
| JVM Heap | 40-70% | 70-85% | >85% |
| DB Connections | 5-20 | 20-40 | >40 |

## Alert Triage Procedures

For complete alert rule documentation, see [infra/monitoring/README.md](../../infra/monitoring/README.md) lines 393-486.

### Critical Alerts

#### Alert: HighErrorRate

**Definition:**
```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) /
sum(rate(http_server_requests_seconds_count[5m])) > 0.05
```

**Severity:** CRITICAL
**Threshold:** 5% error rate for 5 minutes
**SLA Impact:** Yes (availability SLA breach if prolonged)

**Triage Decision Tree:**

```
[Alert: HighErrorRate Triggered]
         |
         v
   Check Application Overview Dashboard
         |
         ├─> Error rate spike (sudden) ──────> Recent deployment?
         |                                       |
         |                                       ├─ YES ─> Rollback deployment
         |                                       |          (See OPERATIONS_RUNBOOK.md)
         |                                       |
         |                                       └─ NO ──> Check dependency health
         |                                                  - Database connectivity?
         |                                                  - Redis connectivity?
         |                                                  - External API outage?
         |
         └─> Error rate gradual increase ──────> Check resource usage
                                                   |
                                                   ├─ CPU/Memory high ─> Scale pods
                                                   |                     (See OPERATIONS_RUNBOOK.md)
                                                   |
                                                   └─ Database slow ───> Check slow queries
                                                                         (See OPERATIONS_RUNBOOK.md)
```

**Response Steps:**

**1. Identify Error Pattern (10 seconds)**
```bash
# Check recent error logs
kubectl logs -n production -l app=scrum-poker-backend --tail=100 | grep ERROR

# Look for common exceptions:
# - NullPointerException: Code bug
# - SQLTransientConnectionException: Database connectivity
# - RedisConnectionException: Redis connectivity
# - TimeoutException: External dependency timeout
```

**2. Check Recent Changes (30 seconds)**
```bash
# View deployment history
kubectl rollout history deployment/scrum-poker-backend -n production

# Check last deployment time
kubectl get deployment scrum-poker-backend -n production -o jsonpath='{.metadata.creationTimestamp}'
```

**3. Assess Impact (1 minute)**
- Check user impact via support channels (#support Slack)
- Check business metrics dashboard (active sessions dropping?)
- Estimate affected user percentage

**4. Execute Mitigation (choose ONE)**

**Option A: Rollback Deployment (if recent deploy)**
```bash
# Rollback to previous version
kubectl rollout undo deployment/scrum-poker-backend -n production

# Monitor error rate decrease in Grafana
```
**Expected Resolution Time:** 2-3 minutes

**Option B: Scale Pods (if resource exhaustion)**
```bash
# Scale to 5 replicas
kubectl scale deployment scrum-poker-backend --replicas=5 -n production

# Monitor CPU/memory metrics in Infrastructure dashboard
```
**Expected Resolution Time:** 3-5 minutes

**Option C: Restart Pods (if transient error)**
```bash
# Rolling restart
kubectl rollout restart deployment/scrum-poker-backend -n production

# Monitor error rate in Application Overview dashboard
```
**Expected Resolution Time:** 2-3 minutes

**5. Post-Incident (after resolved)**
- Document root cause in incident ticket
- Update TROUBLESHOOTING_GUIDE.md if new issue pattern
- Schedule post-mortem if SEV1/SEV2
- Implement preventive measures

#### Alert: HighLatency

**Definition:**
```promql
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le)
) > 1.0
```

**Severity:** CRITICAL
**Threshold:** P95 latency >1 second for 5 minutes

**Triage Decision Tree:**

```
[Alert: HighLatency Triggered]
         |
         v
   Check Application Overview Dashboard
         |
         ├─> Latency spike on all endpoints ────> System-wide issue
         |                                           |
         |                                           ├─> Database slow?
         |                                           |   └─> Check slow queries
         |                                           |
         |                                           ├─> CPU throttling?
         |                                           |   └─> Scale pods
         |                                           |
         |                                           └─> Redis slow?
         |                                               └─> Check Redis latency
         |
         └─> Latency spike on specific endpoint ──> Endpoint-specific issue
                                                      └─> Review endpoint logs
                                                          Check recent code changes
```

**Response Steps:**

**1. Identify Slow Endpoints (30 seconds)**
```bash
# Check "Top Endpoints by Latency" panel in Application Overview
# Identify which endpoints have high P95/P99 latency

# Or query Prometheus directly:
# histogram_quantile(0.95,
#   sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri)
# )
```

**2. Check Database Query Performance (1 minute)**
```bash
# Check database query duration metrics in Infrastructure dashboard
# Or query Prometheus:
# db_query_duration_seconds{quantile="0.95"}

# If high, check slow queries:
kubectl run -it --rm postgres-client --image=postgres:15 --restart=Never -- \
  psql -h scrumpoker-prod.xxxxx.rds.amazonaws.com -U scrumpoker_app -d scrumpoker \
  -c "SELECT pid, now() - query_start as duration, query FROM pg_stat_activity WHERE state = 'active' ORDER BY duration DESC LIMIT 10;"
```

**3. Check Resource Utilization (1 minute)**
```bash
# Check Infrastructure dashboard
# - CPU usage >70%? Scale pods
# - Memory usage >85%? Increase memory limits
# - JVM heap >85%? Tune JVM settings or increase heap
```

**4. Execute Mitigation**

**If Database Slow:**
- Kill long-running queries (see OPERATIONS_RUNBOOK.md)
- Check missing indexes
- Review recent schema changes

**If CPU/Memory High:**
- Scale pods horizontally (see OPERATIONS_RUNBOOK.md)
- Or scale pods vertically (increase resource limits)

**If Endpoint-Specific:**
- Review endpoint code for inefficiencies
- Check for N+1 query problems
- Consider caching strategy

#### Alert: HighMemoryUsage

**Definition:**
```promql
(container_memory_usage_bytes{pod=~"scrum-poker-backend-.*"} /
 container_memory_limit_bytes{pod=~"scrum-poker-backend-.*"}) > 0.85
```

**Severity:** WARNING (becomes CRITICAL at >90%)
**Threshold:** 85% memory usage

**Response Steps:**

**1. Check JVM Heap Usage (30 seconds)**
```bash
# View Infrastructure dashboard
# Check "JVM Heap Memory" panel
# If heap high (>85%), likely memory leak or insufficient heap
```

**2. Identify Memory Consumer (1 minute)**
```bash
# Check pod logs for OutOfMemoryError
kubectl logs -n production -l app=scrum-poker-backend --tail=500 | grep -i "OutOfMemory\|OOM"

# Check for memory leak indicators:
# - Growing heap despite GC
# - Frequent full GC cycles
# - Large object allocations
```

**3. Execute Mitigation**

**Immediate (if >90%):**
```bash
# Restart pod to free memory (temporary fix)
kubectl rollout restart deployment/scrum-poker-backend -n production
```

**Short-term (if recurring):**
```bash
# Increase memory limits
kubectl edit deployment scrum-poker-backend -n production
# Update resources.limits.memory to higher value (e.g., 2Gi → 3Gi)
```

**Long-term:**
- Profile application for memory leaks (heap dump analysis)
- Review WebSocket connection registry size
- Optimize caching strategy
- Tune JVM GC settings

### Warning Alerts

#### Alert: DatabaseConnectionPoolHigh

**Definition:**
```promql
hikaricp_connections_active{application="planning-poker"} /
hikaricp_connections_max{application="planning-poker"} > 0.8
```

**Severity:** WARNING
**Threshold:** 80% connection pool utilization

**Response:** See TROUBLESHOOTING_GUIDE.md → "Database Connection Exhaustion"

#### Alert: RedisMemoryHigh

**Definition:**
```promql
redis_memory_used_bytes / redis_memory_max_bytes > 0.85
```

**Severity:** WARNING
**Threshold:** 85% Redis memory usage

**Response:** See TROUBLESHOOTING_GUIDE.md → "Redis Out of Memory"

#### Alert: PodCrashLooping

**Definition:** Pod restart count >3 in last 10 minutes

**Severity:** WARNING
**Threshold:** 3 restarts in 10 minutes

**Response:** See TROUBLESHOOTING_GUIDE.md → "Pods in CrashLoopBackOff"

## Key Metrics Baselines

Use these baselines to identify anomalies. Baselines vary by time of day and traffic patterns.

### Application Metrics

| Metric | Normal (Business Hours) | Normal (Off-Hours) | Warning | Critical |
|--------|------------------------|-------------------|---------|----------|
| Request Rate | 200-800 req/min | 50-150 req/min | >1000 req/min | >2000 req/min |
| Error Rate | <0.5% | <0.5% | 1-5% | >5% |
| P50 Latency | 30-100ms | 20-80ms | 100-300ms | >500ms |
| P95 Latency | 200-500ms | 100-400ms | 500-1000ms | >1000ms |
| P99 Latency | 500-1000ms | 300-800ms | 1000-2000ms | >2000ms |

### WebSocket Metrics

| Metric | Normal (Business Hours) | Normal (Off-Hours) | Warning | Critical |
|--------|------------------------|-------------------|---------|----------|
| Active Connections | 50-200 | 10-50 | >200 | >300 |
| Message Rate | 20-100 msg/sec | 5-20 msg/sec | >150 msg/sec | >200 msg/sec |
| Disconnection Rate | <5/min | <2/min | 5-10/min | >10/min |
| Message Latency (P95) | <100ms | <80ms | 100-300ms | >500ms |

### Infrastructure Metrics

| Metric | Normal | Warning | Critical | Action |
|--------|--------|---------|----------|--------|
| Pod Replicas | 2-4 | 5-7 | >8 | Review HPA scaling events |
| CPU Usage | 20-50% | 50-70% | >70% | Scale pods or optimize code |
| Memory Usage | 40-70% | 70-85% | >85% | Increase limits or fix leaks |
| JVM Heap Usage | 40-70% | 70-85% | >85% | Tune JVM or increase heap |
| DB Connections (Active) | 5-20 | 20-40 | >40 | Scale pods or optimize queries |
| DB Connections (Idle) | 5-15 | 15-30 | >30 | Reduce pool size |
| Redis Hit Rate | >85% | 70-85% | <70% | Review caching strategy |

## Prometheus Query Examples

Useful Prometheus queries for custom analysis and investigation.

### Request Rate Queries

```promql
# Total request rate (all endpoints)
sum(rate(http_server_requests_seconds_count[5m]))

# Request rate by endpoint
sum(rate(http_server_requests_seconds_count[5m])) by (uri)

# Request rate by status code
sum(rate(http_server_requests_seconds_count[5m])) by (status)

# Request rate for specific endpoint
sum(rate(http_server_requests_seconds_count{uri="/api/sessions"}[5m]))
```

### Error Rate Queries

```promql
# Overall error rate (5xx errors)
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) /
sum(rate(http_server_requests_seconds_count[5m]))

# Error rate by endpoint
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (uri) /
sum(rate(http_server_requests_seconds_count[5m])) by (uri)

# Count of errors by exception type (if instrumented)
sum(increase(http_server_requests_seconds_count{status="500"}[1h])) by (exception)
```

### Latency Queries

```promql
# P50 latency (all endpoints)
histogram_quantile(0.50,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le)
)

# P95 latency (all endpoints)
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le)
)

# P99 latency (all endpoints)
histogram_quantile(0.99,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le)
)

# P95 latency by endpoint
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri)
)
```

### WebSocket Queries

```promql
# Active WebSocket connections
scrumpoker_websocket_connections_total

# WebSocket message rate
rate(scrumpoker_websocket_messages_total[5m])

# WebSocket disconnection rate
rate(scrumpoker_websocket_disconnections_total[5m])

# WebSocket message latency (P95)
histogram_quantile(0.95,
  sum(rate(websocket_message_latency_seconds_bucket[5m])) by (le)
)
```

### Database Queries

```promql
# Active database connections
hikaricp_connections_active{application="planning-poker"}

# Database connection pool utilization
hikaricp_connections_active{application="planning-poker"} /
hikaricp_connections_max{application="planning-poker"}

# Database query latency (P95)
histogram_quantile(0.95,
  sum(rate(db_query_duration_seconds_bucket[5m])) by (le)
)

# Slow queries (>1 second)
increase(db_query_duration_seconds_count{le="+Inf"}[5m]) -
increase(db_query_duration_seconds_count{le="1.0"}[5m])
```

### Redis Queries

```promql
# Redis memory usage
redis_memory_used_bytes

# Redis memory utilization percentage
redis_memory_used_bytes / redis_memory_max_bytes

# Redis hit rate
rate(redis_keyspace_hits_total[5m]) /
(rate(redis_keyspace_hits_total[5m]) + rate(redis_keyspace_misses_total[5m]))

# Redis command rate
sum(rate(redis_commands_total[5m])) by (cmd)
```

### Business Metrics Queries

```promql
# Monthly Recurring Revenue
scrumpoker_revenue_monthly_cents

# Active subscriptions by tier
scrumpoker_subscriptions_active_total{tier="free"}
scrumpoker_subscriptions_active_total{tier="pro"}
scrumpoker_subscriptions_active_total{tier="team"}

# Active planning poker sessions
scrumpoker_active_sessions_total

# Total votes cast (by deck type)
sum(increase(scrumpoker_votes_cast_total[24h])) by (deck_type)

# Completed rounds (by consensus status)
sum(increase(scrumpoker_rounds_completed_total[24h])) by (consensus)
```

## Business Metrics Interpretation

### Monthly Recurring Revenue (MRR)

**Metric:** `scrumpoker_revenue_monthly_cents`

**Interpretation:**
- **Healthy Growth:** 10-20% month-over-month increase
- **Stagnant:** <5% growth for 3 consecutive months
- **Churn Alert:** Negative growth (revenue decreasing)

**Actionable Insights:**
- Track MRR trend weekly in Business Metrics dashboard
- Correlate with marketing campaigns and product releases
- Investigate churn if revenue decreasing

### Active Subscriptions

**Metric:** `scrumpoker_subscriptions_active_total{tier="..."}`

**Interpretation:**
- **Free Tier:** Entry funnel, target conversion rate >10%
- **Pro Tier:** Individual users, steady growth expected
- **Team Tier:** Enterprise indicator, high-value customers

**Actionable Insights:**
- Monitor free-to-paid conversion rate
- Track team tier growth (enterprise adoption)
- Investigate cancellations (churn alerts)

### Voting Activity

**Metric:** `scrumpoker_votes_cast_total`

**Interpretation:**
- **High Activity:** Product engagement strong
- **Low Activity:** Users not actively using product
- **Deck Type Distribution:** Fibonacci most popular (agile teams)

**Actionable Insights:**
- Correlate voting activity with active sessions
- Track votes per session (engagement metric)
- Monitor deck type preferences for feature prioritization

## Performance Metrics Interpretation

### Request Latency

**P50 (Median):** Typical user experience
**P95:** Most users experience this or better
**P99:** Worst-case (1 in 100 requests)

**Healthy System:**
- P50: <100ms (fast)
- P95: <500ms (acceptable)
- P99: <1000ms (edge cases)

**Degraded System:**
- P50: >200ms (slow for typical user)
- P95: >1000ms (poor experience for most)
- P99: >2000ms (unacceptable edge cases)

**Actionable Insights:**
- If P50 high: System-wide performance issue
- If P95 high but P50 OK: Some requests slow (database, external API)
- If P99 high but P95 OK: Edge cases or outliers (timeout handling)

### Error Rate

**Normal:** <1% (most systems have some transient errors)
**Warning:** 1-5% (investigate, but not critical)
**Critical:** >5% (immediate action required)

**Error Types:**
- **4xx (Client Errors):** User input validation, authentication failures
  - 400 Bad Request: Invalid input
  - 401 Unauthorized: Authentication failures
  - 403 Forbidden: Authorization failures
  - 404 Not Found: Resource doesn't exist
- **5xx (Server Errors):** Application bugs, infrastructure issues
  - 500 Internal Server Error: Unhandled exceptions
  - 502 Bad Gateway: Load balancer can't reach pods
  - 503 Service Unavailable: Pods not ready
  - 504 Gateway Timeout: Request timeout

**Actionable Insights:**
- High 4xx: Client-side issues or API breaking changes
- High 5xx: Server-side issues, investigate logs
- Spike in errors: Recent deployment or infrastructure change

## Support and Resources

**Related Documentation:**
- [infra/monitoring/README.md](../../infra/monitoring/README.md) - Detailed metrics and alert rules
- [OPERATIONS_RUNBOOK.md](./OPERATIONS_RUNBOOK.md) - Operational procedures
- [TROUBLESHOOTING_GUIDE.md](./TROUBLESHOOTING_GUIDE.md) - Common issues and solutions
- [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Deployment procedures

**External Resources:**
- Prometheus Query Basics: https://prometheus.io/docs/prometheus/latest/querying/basics/
- Grafana Dashboard Best Practices: https://grafana.com/docs/grafana/latest/dashboards/
- PromQL Examples: https://prometheus.io/docs/prometheus/latest/querying/examples/

**Team Contacts:**
- Monitoring Team: #monitoring Slack channel
- On-Call Engineer: PagerDuty rotation
- DevOps Lead: devops-lead@example.com
