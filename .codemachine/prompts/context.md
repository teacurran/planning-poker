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
  "inputs": "Technology stack requirements (PostgreSQL 15, Redis 7 cluster), Observability stack (Prometheus, Grafana), Environment variable needs from application.properties",
  "input_files": [
    "backend/src/main/resources/application.properties"
  ],
  "target_files": [
    "docker-compose.yml",
    ".env.example",
    "README.md",
    "infra/local/prometheus.yml",
    "infra/local/grafana/dashboards/quarkus-dashboard.json"
  ],
  "deliverables": "Docker Compose file with 4 services (PostgreSQL, Redis, Prometheus, Grafana), PostgreSQL container with automatic schema initialization (Flyway migrations), Redis cluster configuration (3 nodes with replication), Prometheus configured to scrape `http://host.docker.internal:8080/q/metrics`, Grafana preconfigured with Prometheus datasource, Environment variable template file, README section documenting `docker-compose up`, connection strings, port mappings",
  "acceptance_criteria": "`docker-compose up` starts all services without errors, PostgreSQL accessible at `localhost:5432` with credentials from `.env`, Redis cluster accessible at `localhost:6379-6381`, Prometheus UI at `http://localhost:9090` shows Quarkus target, Grafana UI at `http://localhost:3000` displays preconfigured dashboard, Flyway migrations execute automatically when PostgreSQL container starts",
  "dependencies": [
    "I1.T3"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: technology-constraints (from 01_Context_and_Drivers.md)

```markdown
#### Technology Constraints
- **Backend Framework:** Quarkus with Hibernate Reactive (specified requirement)
- **Database:** PostgreSQL for relational data integrity and JSONB support
- **Cache/Message Bus:** Redis for session state distribution and Pub/Sub messaging
- **Payment Provider:** Stripe for subscription billing and payment processing
- **Containerization:** Docker containers orchestrated via Kubernetes
```

### Context: deployment-view (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: deployment-view -->
## 5.2. Deployment View

<!-- anchor: target-environment -->
### 5.2.1. Target Environment

**Cloud Platform:** AWS (Amazon Web Services)

**Rationale:**
- **Managed Kubernetes:** Amazon EKS provides battle-tested container orchestration with native integration to AWS services (RDS, ElastiCache, CloudFront, S3)
- **Regional Presence:** AWS has extensive global coverage with data centers in North America and Europe, supporting the initial target markets with <100ms latency requirements
- **Ecosystem Maturity:** Extensive tooling for monitoring (CloudWatch), logging (CloudWatch Logs), and secret management (Secrets Manager)
- **Cost Optimization:** Reserved instances, spot instances, and auto-scaling capabilities align with budget constraints
- **Compliance:** AWS certifications (SOC 2, ISO 27001, GDPR-ready) meet regulatory requirements
```

### Context: container-diagram (from 03_System_Structure_and_Data.md)

```markdown
**Key Containers:**
- **Web Application (SPA):** React-based single-page application served via CDN, communicating with backend via REST and WebSocket
- **API Gateway / Load Balancer:** Kubernetes Ingress or cloud load balancer providing TLS termination and sticky session routing
- **Quarkus Application (Reactive):** Core backend containing business logic, WebSocket handlers, REST controllers, and integration adapters
- **PostgreSQL Database:** Primary persistent store for users, rooms, votes, subscriptions, and audit logs
- **Redis Cluster:** In-memory cache for session state, WebSocket message broadcasting (Pub/Sub), and transient room data
- **Background Job Processor:** Asynchronous worker consuming Redis Streams for report generation, email notifications, and analytics aggregation
```

### Context: monitoring-metrics (from 05_Operational_Architecture.md)

```markdown
#### Monitoring Metrics

**Prometheus Metrics Collection:**

The application exposes Quarkus-generated metrics at `/q/metrics` endpoint in Prometheus format. Custom business metrics are instrumented using Micrometer's `MeterRegistry`.

**Key Metrics Categories:**

1. **HTTP Request Metrics**
   - `http_server_requests_seconds_count` - Total request count by endpoint, method, status
   - `http_server_requests_seconds_sum` - Total request duration
   - `http_server_requests_seconds_max` - Maximum request duration (with percentiles: p50, p95, p99)

2. **WebSocket Metrics** (custom metrics)
   - `scrumpoker_websocket_connections_active` - Current active WebSocket connections (gauge)
   - `scrumpoker_websocket_messages_sent_total` - Total WebSocket messages sent (counter)
   - `scrumpoker_websocket_disconnections_total` - Total disconnections by reason (counter)

3. **Business Metrics** (custom metrics)
   - `scrumpoker_active_rooms` - Current number of active estimation rooms (gauge)
   - `scrumpoker_votes_cast_total` - Total votes cast across all sessions (counter)
   - `scrumpoker_subscriptions_active` - Active subscriptions by tier (gauge with tier label)
   - `scrumpoker_round_consensus_rate` - Percentage of rounds achieving consensus (histogram)

4. **JVM Metrics** (Quarkus built-in)
   - `jvm_memory_used_bytes` - Heap/non-heap memory usage
   - `jvm_threads_live` - Current thread count
   - `jvm_gc_pause_seconds` - Garbage collection pause time

5. **Database Metrics** (Hibernate Reactive)
   - `hikari_connections_active` - Active database connections
   - `hibernate_query_execution_total` - Query execution count
   - `hibernate_query_execution_seconds` - Query execution duration

**Grafana Dashboards:**

Three preconfigured dashboards provide operational visibility:

1. **Application Overview Dashboard**
   - Request rate (requests/second)
   - Error rate (4xx/5xx percentage)
   - Latency percentiles (p50, p95, p99)
   - Active sessions and WebSocket connections

2. **WebSocket Real-Time Dashboard**
   - Connection count by room
   - Message throughput (messages/second)
   - Disconnection rate by reason (network failure, client close, timeout)
   - Average room lifetime

3. **Business Metrics Dashboard**
   - Subscription tier distribution (pie chart)
   - Daily active users (DAU)
   - Votes cast per hour (time series)
   - Consensus achievement rate trend
```

### Context: technology-stack (from 01_Plan_Overview_and_Setup.md)

```markdown
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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `docker-compose.yml`
    *   **Summary:** Currently contains only PostgreSQL 15 service with basic configuration. Database name is `planningpoker`, user/password is `dockercompose`, exposed on port `5445:5432`.
    *   **Recommendation:** You MUST extend this file to add Redis cluster (3 nodes), Prometheus, and Grafana services. Keep the existing PostgreSQL configuration but ensure it aligns with the task requirements (database name should be `scrumpoker` according to the task, but current setup uses `planningpoker` - verify which is correct or update accordingly).
    *   **Note:** Current setup has healthcheck configured for PostgreSQL - maintain this pattern for other services.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** This is the Quarkus configuration file that will define database connection strings, Redis URLs, and other runtime parameters.
    *   **Recommendation:** You MUST read this file to understand what environment variables need to be templated in `.env.example`. Look for properties related to datasource, Redis, JWT, and any other configurable parameters.
    *   **Note:** Quarkus uses property names like `quarkus.datasource.reactive.url`, `quarkus.redis.hosts`, etc. The `.env.example` should provide templates for these values.

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven project configuration with Quarkus 3.15.1, includes dependencies for Hibernate Reactive Panache, PostgreSQL reactive client, Redis client, WebSockets, OIDC, JWT, Micrometer Prometheus, Flyway, and SmallRye Health.
    *   **Recommendation:** The Prometheus configuration MUST scrape the `/q/metrics` endpoint which is provided by the `quarkus-micrometer-registry-prometheus` dependency already included. Grafana should be configured to pull from Prometheus and display Quarkus/JVM metrics.
    *   **Note:** Flyway is already configured (`quarkus-flyway` dependency present), so PostgreSQL service should automatically run migrations from `backend/src/main/resources/db/migration/` when the application starts.

*   **File:** `backend/src/main/resources/db/migration/V1__initial_schema.sql`
    *   **Summary:** Complete database schema migration creating 11 core tables (User, UserPreference, Organization, OrgMember, Subscription, PaymentHistory, Room, RoomParticipant, Round, Vote, SessionHistory, AuditLog) with proper ENUMs, constraints, indexes, and partitioning setup.
    *   **Recommendation:** The PostgreSQL Docker container SHOULD be configured to automatically run these migrations. Consider whether to run Flyway migrations via Quarkus application startup (preferred) or via a separate init container in docker-compose (less preferred but possible for local dev).
    *   **Note:** Schema is production-ready with partitioning, soft deletes, and comprehensive indexes. Database user needs sufficient permissions to create tables, types, and partitions.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/` (various entity classes)
    *   **Summary:** JPA entities with Hibernate Reactive Panache already implemented for User, Room, Vote, Organization, Subscription, etc. Entities use UUID primary keys (except Room which uses 6-char String), proper validation annotations, and JSONB columns.
    *   **Recommendation:** No code changes needed for this task, but understanding the entities helps ensure the database configuration in docker-compose supports the schema requirements (JSONB support, UUID generation, ENUM types).

*   **File:** `README.md`
    *   **Summary:** Current README exists but content unknown without reading it.
    *   **Recommendation:** You MUST update the README to include a section on local development setup with docker-compose. Document: prerequisites (Docker, Docker Compose), how to start services (`docker-compose up`), how to stop services, port mappings, accessing UIs (Prometheus, Grafana), database connection details.

### Implementation Tips & Notes

*   **Tip:** For Redis cluster mode with 3 nodes, you'll need to use the official Redis image and configure cluster initialization. Consider using `redis:7-alpine` for smaller image size. The cluster setup requires specific configuration for master-replica topology and cluster discovery.

*   **Tip:** Prometheus configuration file (`infra/local/prometheus.yml`) should define scrape targets. For local development, the Quarkus app runs on the host machine (not in Docker by default), so use `host.docker.internal:8080` to allow the Prometheus container to reach the host machine's Quarkus dev server.

*   **Tip:** Grafana dashboard JSON should be created with a preconfigured Prometheus datasource. You can generate this by:
    1. Manually configuring Grafana (start it once, add Prometheus datasource, create dashboard)
    2. Exporting the dashboard JSON via Grafana UI
    3. Placing the exported JSON in `infra/local/grafana/dashboards/` and mounting it as a volume
    OR create a minimal dashboard JSON manually following Grafana's schema.

*   **Tip:** The `.env.example` file should contain placeholder values with comments explaining what each variable is for. Example:
    ```
    # PostgreSQL Configuration
    POSTGRES_USER=scrumpoker
    POSTGRES_PASSWORD=change_me_in_production
    POSTGRES_DB=scrumpoker

    # Redis Configuration
    REDIS_PASSWORD=change_me_in_production

    # JWT Configuration
    JWT_SECRET=changeme_must_be_long_random_string_min_32_chars
    ```

*   **Note:** The existing docker-compose.yml uses port `5445:5432` for PostgreSQL (non-standard). The acceptance criteria states `localhost:5432`, so you may need to change the port mapping to `5432:5432` OR update the acceptance criteria interpretation to use `5445`.

*   **Note:** For Flyway migrations to run automatically, the Quarkus application must be started AFTER PostgreSQL is ready. In docker-compose, this is handled by the Quarkus application healthcheck depending on PostgreSQL service. However, for local development (Quarkus running in dev mode on host), migrations will run on first app startup.

*   **Warning:** Redis cluster mode requires at least 3 master nodes and optionally 3 replica nodes (6 total) for production HA. For local development, you can simplify to 3 standalone Redis instances or use a single Redis node with cluster mode enabled. Verify the task requirement: "Redis 7 cluster mode with 3 nodes" - interpret this as 3 Redis nodes in cluster topology.

*   **Warning:** Grafana default credentials are typically `admin/admin` and must be changed on first login. Document this in README. Consider using environment variables to set initial admin password in docker-compose.

*   **Important:** The task specifies "PostgreSQL container with automatic schema initialization (Flyway migrations)". Flyway migrations are managed by Quarkus application, NOT by PostgreSQL container. The migrations will execute when Quarkus starts, not when PostgreSQL starts. Make this clear in documentation.

*   **Important:** Volume persistence is critical for PostgreSQL to avoid data loss on container restart. Ensure named volumes are used for `postgres_data` (as already present in current docker-compose.yml). Consider adding volume for Grafana data persistence as well.
