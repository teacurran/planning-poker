# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T5",
  "iteration_id": "I1",
  "iteration_goal": "Establish project scaffolding, configure development environment, define database schema, and set up CI/CD foundation to enable parallel backend and frontend development in subsequent iterations.",
  "description": "Create `docker-compose.yml` defining services: PostgreSQL 15 (with initialized database `scrumpoker`), Redis 7 (cluster mode with 3 nodes), Prometheus (scraping Quarkus metrics), Grafana (preconfigured with Prometheus datasource and dashboard). Configure volume mounts for database persistence and Grafana dashboards. Create `.env.example` file with environment variable templates (database credentials, Redis URLs, JWT secret placeholder). Document startup commands in README.md.",
  "agent_type_hint": "SetupAgent",
  "inputs": "*   Technology stack requirements (PostgreSQL 15, Redis 7 cluster)\n        *   Observability stack (Prometheus, Grafana)\n        *   Environment variable needs from application.properties",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   Docker Compose file with 4 services (PostgreSQL, Redis, Prometheus, Grafana)\n        *   PostgreSQL container with automatic schema initialization (Flyway migrations)\n        *   Redis cluster configuration (3 nodes with replication)\n        *   Prometheus configured to scrape `http://host.docker.internal:8080/q/metrics`\n        *   Grafana preconfigured with Prometheus datasource\n        *   Environment variable template file\n        *   README section documenting `docker-compose up`, connection strings, port mappings",
  "acceptance_criteria": "*   `docker-compose up` starts all services without errors\n        *   PostgreSQL accessible at `localhost:5432` with credentials from `.env`\n        *   Redis cluster accessible at `localhost:6379-6381`\n        *   Prometheus UI at `http://localhost:9090` shows Quarkus target\n        *   Grafana UI at `http://localhost:3000` displays preconfigured dashboard\n        *   Flyway migrations execute automatically when PostgreSQL container starts",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: technology-constraints (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: technology-constraints -->
#### Technology Constraints
- **Backend Framework:** Quarkus with Hibernate Reactive (specified requirement)
- **Database:** PostgreSQL for relational data integrity and JSONB support
- **Cache/Message Bus:** Redis for session state distribution and Pub/Sub messaging
- **Payment Provider:** Stripe for subscription billing and payment processing
- **Containerization:** Docker containers orchestrated via Kubernetes
```

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

### Context: technology-stack (from 01_Plan_Overview_and_Setup.md)

```markdown
<!-- anchor: technology-stack -->
*   **Technology Stack:**
    *   **Frontend:**
        *   Framework: React 18+ with TypeScript
        *   UI Library: Tailwind CSS + Headless UI
        *   State Management: Zustand (client state) + React Query (server state)
        *   WebSocket: Native WebSocket API with reconnection wrapper
    *   **Backend:**
        *   Framework: Quarkus 3.x (Reactive mode)
        *   Language: Java 17 (LTS)
        *   Runtime: JVM mode (potential future native compilation)
    *   **Database:**
        *   Primary: PostgreSQL 15+ (ACID compliance, JSONB support, partitioning)
        *   ORM: Hibernate Reactive + Panache repositories
    *   **Messaging/Queues:**
        *   Redis 7+ Cluster (Pub/Sub for WebSocket broadcasting, Streams for async jobs)
    *   **Deployment:**
        *   Containerization: Docker (multi-stage builds)
        *   Orchestration: Kubernetes (AWS EKS or GCP GKE)
        *   Cloud Platform: AWS (primary) with CloudFront CDN, RDS, ElastiCache
    *   **Other Key Libraries/Tools:**
        *   **Auth:** Quarkus OIDC extension (OAuth2/SSO), SmallRye JWT
        *   **Payments:** Stripe Java SDK
        *   **Logging:** SLF4J with JSON formatter, Loki/CloudWatch aggregation
        *   **Metrics:** Prometheus + Grafana dashboards
        *   **Testing:** Testcontainers (integration), Playwright (E2E), JUnit 5
```

### Context: task-i1-t5 (from 02_Iteration_I1.md)

```markdown
<!-- anchor: task-i1-t5 -->
*   **Task 1.5: Set Up Local Development Environment with Docker Compose**
    *   **Task ID:** `I1.T5`
    *   **Description:** Create `docker-compose.yml` defining services: PostgreSQL 15 (with initialized database `scrumpoker`), Redis 7 (cluster mode with 3 nodes), Prometheus (scraping Quarkus metrics), Grafana (preconfigured with Prometheus datasource and dashboard). Configure volume mounts for database persistence and Grafana dashboards. Create `.env.example` file with environment variable templates (database credentials, Redis URLs, JWT secret placeholder). Document startup commands in README.md.
    *   **Agent Type Hint:** `SetupAgent`
    *   **Inputs:**
        *   Technology stack requirements (PostgreSQL 15, Redis 7 cluster)
        *   Observability stack (Prometheus, Grafana)
        *   Environment variable needs from application.properties
    *   **Input Files:**
        *   `backend/src/main/resources/application.properties`
    *   **Target Files:**
        *   `docker-compose.yml`
        *   `.env.example`
        *   `README.md` (development setup section)
        *   `infra/local/prometheus.yml` (Prometheus configuration)
        *   `infra/local/grafana/dashboards/quarkus-dashboard.json`
    *   **Deliverables:**
        *   Docker Compose file with 4 services (PostgreSQL, Redis, Prometheus, Grafana)
        *   PostgreSQL container with automatic schema initialization (Flyway migrations)
        *   Redis cluster configuration (3 nodes with replication)
        *   Prometheus configured to scrape `http://host.docker.internal:8080/q/metrics`
        *   Grafana preconfigured with Prometheus datasource
        *   Environment variable template file
        *   README section documenting `docker-compose up`, connection strings, port mappings
    *   **Acceptance Criteria:**
        *   `docker-compose up` starts all services without errors
        *   PostgreSQL accessible at `localhost:5432` with credentials from `.env`
        *   Redis cluster accessible at `localhost:6379-6381`
        *   Prometheus UI at `http://localhost:9090` shows Quarkus target
        *   Grafana UI at `http://localhost:3000` displays preconfigured dashboard
        *   Flyway migrations execute automatically when PostgreSQL container starts
    *   **Dependencies:** [I1.T3]
    *   **Parallelizable:** No (needs migration scripts for database init)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `docker-compose.yml`
    *   **Summary:** Defines PostgreSQL 15 with persistent volume, a three-node Redis 7 cluster plus one-shot initializer, Prometheus v2.48 scraping the host Quarkus metrics/health endpoints, and Grafana 10.2 with provisioning folders mounted, health checks, and named volumes on the shared `planning-poker-network`.
    *   **Recommendation:** Keep these service names, health checks, and volumes intact when iterating; any extra container (e.g., backend app) should join the same network and reuse the existing `${VAR:-default}` interpolation style so local overrides keep working.
*   **File:** `.env.example`
    *   **Summary:** Lists the template values the Compose stack expects—PostgreSQL credentials/ports, JDBC and reactive URLs, Redis password/URL, JWT issuer/secret metadata, Grafana admin credentials, and reference comments for exposed local ports.
    *   **Recommendation:** Whenever the Compose services consume a new variable, add it here with descriptive guidance (and safe defaults) so contributors can `cp .env.example .env` and boot everything without guesswork.
*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Documents all environment-driven settings the backend reads, including `DB_USERNAME`, `DB_REACTIVE_URL`, `REDIS_URL`, Flyway flags, JWT paths, and dev profile overrides (`%dev.quarkus.redis.health.enabled=false`).
    *   **Recommendation:** Align every Compose/README environment instruction with these property keys—mismatched names will cause Quarkus to fall back to defaults and fail to reach the containers.
*   **File:** `infra/local/prometheus.yml` & `infra/local/grafana/dashboards/quarkus-dashboard.json`
    *   **Summary:** Prometheus already scrapes `host.docker.internal:8080` for `/q/metrics` and `/q/health`, while the bundled Grafana dashboard queries the `prometheus` datasource for HTTP rates, JVM stats, and WebSocket metrics so dashboards auto-provision at startup.
    *   **Recommendation:** If you change scrape targets or add exporters, update both the Prometheus config and dashboard JSON together and ensure the Compose volume mounts (`./infra/local/...`) remain read-only to keep provisioning deterministic.
*   **File:** `README.md`
    *   **Summary:** Walks developers through copying `.env`, running `docker-compose up -d`, checking health via `docker-compose ps`, verifying PostgreSQL/Redis/Prometheus/Grafana, and shutting the stack down (with warnings about `-v`).
    *   **Recommendation:** Extend the existing numbered setup steps—reuse fenced shell blocks and bullet lists so the new documentation for Redis cluster checks or Grafana dashboards feels consistent with the rest of the guide.

### Implementation Tips & Notes
*   **Tip:** Prometheus and Grafana services mount `./infra/local` plus named volumes; remind readers that `docker-compose down -v` deletes persisted metrics/dashboards so they only run it when resetting the stack.
*   **Tip:** Redis nodes and the cluster-init container all reference `REDIS_PASSWORD`; if you change the default or introduce sentinel/cluster URLs, propagate the same value across commands, health checks, and the `.env` template.
*   **Tip:** Compose’s Prometheus job targets `host.docker.internal`; keep the backend running on the host at `:8080` (or update the job/README) so scrapes succeed without extra network aliases.
*   **Note:** `%dev.quarkus.redis.health.enabled=false` in `application.properties` temporarily disables the dev health check. If you want developers to rely on the Redis containers for integration tests, flip this flag back on once the stack is stable and mention it in the README.
*   **Warning:** Tell developers to wait for `docker-compose ps` to show every service as `healthy` before launching Quarkus; Flyway migrations and Redis Pub/Sub wiring expect their dependencies to be available immediately.
