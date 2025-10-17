# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T1",
  "iteration_id": "I1",
  "iteration_goal": "Establish project scaffolding, configure development environment, define database schema, and set up CI/CD foundation to enable parallel backend and frontend development in subsequent iterations.",
  "description": "Create Quarkus 3.x Maven project with reactive extensions (hibernate-reactive-panache, reactive-pg-client, redis-client, websockets, oidc, smallrye-jwt, micrometer-prometheus). Configure `application.properties` with database connection placeholders, Redis configuration, and JWT settings. Set up Maven build with compiler plugin (Java 17 target), Quarkus plugin, and Surefire for testing. Create package structure: `api`, `domain`, `repository`, `integration`, `event`, `config`, `security`.",
  "agent_type_hint": "SetupAgent",
  "inputs": "Directory structure specification from Section 3 of plan overview, Technology stack requirements (Quarkus 3.x, Java 17, reactive extensions), Maven dependency list from architecture blueprint",
  "input_files": [],
  "target_files": [
    "backend/pom.xml",
    "backend/src/main/resources/application.properties",
    "backend/src/main/java/com/scrumpoker/",
    "backend/.gitignore"
  ],
  "deliverables": "Working Maven project buildable with `mvn clean compile`, Configured Quarkus extensions in `pom.xml`, Application properties with placeholder values for database, Redis, JWT secret, Package directory structure following hexagonal architecture",
  "acceptance_criteria": "`mvn clean compile` executes without errors, `mvn quarkus:dev` starts Quarkus in dev mode and serves health check at `/q/health`, All required Quarkus extensions listed in `pom.xml` dependencies, Package structure matches specification (6+ top-level packages created)",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

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

### Context: directory-structure (from 01_Plan_Overview_and_Setup.md)

```markdown
*   **Root Directory:** `scrum-poker-platform/`

*   **Structure Definition:**

    The project follows a standard Maven multi-module structure for the backend and modern React SPA conventions for the frontend, with clear separation of concerns and dedicated locations for documentation, API specifications, and infrastructure-as-code.

    ~~~
    scrum-poker-platform/
    ├── backend/                          # Quarkus backend application
    │   ├── src/
    │   │   ├── main/
    │   │   │   ├── java/
    │   │   │   │   └── com/scrumpoker/
    │   │   │   │       ├── api/          # REST controllers, WebSocket handlers
    │   │   │   │       │   ├── rest/     # JAX-RS resource classes
    │   │   │   │       │   └── websocket/ # WebSocket endpoint handlers
    │   │   │   │       ├── domain/       # Domain services and business logic
    │   │   │   │       │   ├── user/     # User, UserPreference entities + service
    │   │   │   │       │   ├── room/     # Room, Round, Vote entities + service
    │   │   │   │       │   ├── billing/  # Subscription, Payment entities + service
    │   │   │   │       │   ├── reporting/ # SessionHistory, analytics service
    │   │   │   │       │   └── organization/ # Organization, OrgMember entities + service
    │   │   │   │       ├── repository/   # Panache repositories
    │   │   │   │       ├── integration/  # External service adapters
    │   │   │   │       │   ├── oauth/    # Google/Microsoft OAuth2 clients
    │   │   │   │       │   ├── sso/      # OIDC/SAML2 adapters
    │   │   │   │       │   ├── stripe/   # Stripe SDK wrapper
    │   │   │   │       │   └── email/    # SendGrid/SES client
    │   │   │   │       ├── event/        # Redis Pub/Sub publisher/subscriber
    │   │   │   │       ├── config/       # Application configuration classes
    │   │   │   │       └── security/     # Authentication filters, JWT utilities
    │   │   │   └── resources/
    │   │   │       ├── application.properties  # Quarkus configuration
    │   │   │       └── db/
    │   │   │           └── migration/    # Flyway SQL migration scripts
    │   │   │               ├── V1__initial_schema.sql
    │   │   │               ├── V2__add_organizations.sql
    │   │   │               └── ...
    │   │   └── test/
    │   │       ├── java/
    │   │       │   └── com/scrumpoker/
    │   │       │       ├── api/          # REST/WebSocket integration tests
    │   │       │       ├── domain/       # Unit tests for services
    │   │       │       └── repository/   # Repository tests with Testcontainers
    │   │       └── resources/
    │   │           └── application-test.properties
    │   ├── pom.xml                       # Maven project descriptor
    │   └── Dockerfile                    # Multi-stage Docker build
    ~~~
```

### Context: technology-stack-summary (from 02_Architecture_Overview.md)

```markdown
### 3.2. Technology Stack Summary

| **Category** | **Technology Choice** | **Justification** |
|--------------|----------------------|-------------------|
| **Frontend Framework** | **React 18+ with TypeScript** | Strong ecosystem, concurrent rendering for real-time updates, TypeScript for type safety in WebSocket message contracts |
| **UI Component Library** | **Tailwind CSS + Headless UI** | Utility-first CSS for rapid development, Headless UI for accessible components (modals, dropdowns), minimal bundle size |
| **State Management** | **Zustand + React Query** | Lightweight state management (Zustand), server state caching and synchronization (React Query), WebSocket integration support |
| **WebSocket Client** | **Native WebSocket API + Reconnecting wrapper** | Native browser API for compatibility, lightweight reconnection logic with exponential backoff |
| **Backend Framework** | **Quarkus 3.x (Reactive)** | Specified requirement, optimized for cloud-native deployment, reactive runtime for WebSocket concurrency, fast startup times |
| **Language** | **Java 17+ (LTS)** | Native Quarkus support, strong type system, mature ecosystem, team expertise |
| **ORM/Data Access** | **Hibernate Reactive + Panache** | Specified requirement, reactive database access with Mutiny streams, simplified repository pattern via Panache |
| **Database** | **PostgreSQL 15+** | ACID compliance, JSONB for flexible room configuration storage, proven scalability, strong community support |
| **Cache/Session Store** | **Redis 7+ (Cluster mode)** | In-memory performance for session state, Pub/Sub for WebSocket message broadcasting, horizontal scaling via cluster mode |
| **Message Queue** | **Redis Streams** | Leverages existing Redis infrastructure, sufficient for asynchronous job processing (report generation, email notifications), simpler than dedicated message brokers |
| **Authentication** | **OAuth2/OIDC (Google, Microsoft)** | Leverages existing identity providers, reduces password management risk, Quarkus OIDC extension for SSO integration |
| **Authorization** | **Quarkus Security (RBAC)** | Built-in role-based access control, annotation-driven security, JWT token validation |
| **Payment Processing** | **Stripe API (v2023-10+)** | Industry-leading payment gateway, comprehensive subscription management, webhook-based event handling, PCI compliance |
| **WebSocket Protocol** | **Custom JSON-RPC style over WebSocket** | Lightweight request/response + event notification pattern, versioned message types for backward compatibility |
| **API Style (REST)** | **RESTful JSON API (OpenAPI 3.1)** | Standard HTTP semantics for CRUD operations, OpenAPI specification for client generation and documentation |
| **Containerization** | **Docker (multi-stage builds)** | Standardized deployment artifact, multi-stage builds for optimized image size (Quarkus native or JVM mode) |
| **Orchestration** | **Kubernetes (managed service)** | Horizontal scaling, health checks, rolling deployments, Ingress for load balancing with sticky sessions |
| **Observability - Metrics** | **Prometheus + Grafana** | Cloud-native standard, Quarkus Micrometer extension, custom business metrics (active sessions, vote latency) |
| **Observability - Logging** | **Structured JSON + Loki/CloudWatch** | Structured logging for query efficiency, centralized aggregation, correlation IDs for distributed tracing |
| **Observability - Tracing** | **OpenTelemetry (optional MVP+)** | Distributed tracing for debugging WebSocket flows, integration with Jaeger/Tempo |
| **CI/CD** | **GitHub Actions** | Native integration with repository, Docker build/push, automated testing, deployment to Kubernetes |
| **Infrastructure as Code** | **Terraform or Helm Charts** | Declarative infrastructure provisioning (Terraform for cloud resources, Helm for K8s manifests) |
| **Cloud Platform** | **AWS (preferred) or GCP** | Managed Kubernetes (EKS), managed PostgreSQL (RDS), managed Redis (ElastiCache), CDN (CloudFront/Cloud CDN) |
| **CDN** | **CloudFront (AWS) or Cloud CDN (GCP)** | Static asset caching (React SPA), edge termination for HTTPS, DDoS protection |
| **DNS/SSL** | **Route53 + ACM (AWS) or Cloud DNS + Let's Encrypt** | Managed DNS with health checks, automated SSL certificate provisioning and renewal |
| **Email Service** | **SendGrid or AWS SES** | Transactional email delivery (password reset, subscription notifications), deliverability monitoring |
| **Monitoring/Alerting** | **Prometheus Alertmanager + PagerDuty** | Rule-based alerting (CPU, error rates, WebSocket connection drops), on-call escalation |

#### Key Libraries & Extensions

**Backend (Quarkus):**
- `quarkus-resteasy-reactive-jackson` - Reactive REST endpoints with JSON serialization
- `quarkus-hibernate-reactive-panache` - Reactive database access layer
- `quarkus-reactive-pg-client` - Non-blocking PostgreSQL driver
- `quarkus-redis-client` - Redis integration for caching and Pub/Sub
- `quarkus-websockets` - WebSocket server implementation
- `quarkus-oidc` - OAuth2/OIDC authentication and SSO support
- `quarkus-smallrye-jwt` - JWT token generation and validation
- `quarkus-micrometer-registry-prometheus` - Metrics export
- `stripe-java` - Stripe API client for payment processing

**Frontend (React):**
- `@tanstack/react-query` - Server state management and caching
- `zustand` - Client-side state management (UI, WebSocket connection state)
- `react-hook-form` - Form validation and submission
- `zod` - Schema validation for API responses and WebSocket messages
- `date-fns` - Date/time formatting for session history
- `recharts` - Charting library for analytics dashboards
- `@headlessui/react` - Accessible UI components
- `heroicons` - Icon library

**DevOps & Testing:**
- `testcontainers` - Integration testing with PostgreSQL and Redis containers
- `rest-assured` - REST API testing
- `playwright` - End-to-end testing for WebSocket flows
- `k6` - Load testing for WebSocket concurrency benchmarks
```

### Context: key-assumptions (from 01_Plan_Overview_and_Setup.md)

```markdown
*   **Key Assumptions:**
    *   **Tech Stack:** Quarkus reactive backend (Java 17), React 18 frontend, PostgreSQL 15+ database, Redis 7+ for caching/Pub/Sub
    *   **Deployment:** Kubernetes-based container orchestration (AWS EKS preferred) with managed services (RDS, ElastiCache)
    *   **Team:** 2-5 developers with expertise in Java/Quarkus, React, cloud-native patterns, and reactive programming
    *   **Timeline:** MVP delivery within 4-6 months with iterative feature releases
    *   **Scale:** Target 500 concurrent sessions at launch, with auto-scaling to handle growth
    *   **Compliance:** GDPR and basic data protection requirements (SOC2/HIPAA not required in initial release)
    *   **Browser Support:** Modern evergreen browsers (Chrome, Firefox, Safari, Edge) with WebSocket and ES2020+ support
    *   **OAuth Providers:** Google and Microsoft OAuth2 endpoints maintain >99.9% uptime
    *   **Payment Gateway:** Stripe available and compliant in target markets (North America, Europe)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `pom.xml` (current root location)
    *   **Summary:** This is an existing Quarkus 3.15.1 Maven project with basic reactive extensions. It currently uses the package structure `com.terrencecurran.planningpoker` and includes Vue.js integration via Quinoa.
    *   **Recommendation:** You MUST restructure this project. The task requires creating a NEW `backend/` directory with a fresh `pom.xml`. The current project structure conflicts with the planned architecture which calls for separate `backend/` and `frontend/` directories. You should create a NEW Maven project in `backend/pom.xml` using the package name `com.scrumpoker` (NOT `com.terrencecurran.planningpoker`).

*   **File:** `src/main/resources/application.properties`
    *   **Summary:** This file contains working PostgreSQL reactive datasource configuration and Flyway migration setup. It also includes Quinoa configuration for Vue.js integration.
    *   **Recommendation:** You SHOULD use this as a reference for database and reactive configuration patterns, BUT you MUST create a NEW `backend/src/main/resources/application.properties` file. Remove the Quinoa configuration (this task is backend-only). Add placeholder configurations for Redis and JWT settings as required by the task.

*   **File:** `src/main/java/com/terrencecurran/planningpoker/entity/Room.java`
    *   **Summary:** This is an existing Panache entity demonstrating the project's current entity pattern using `PanacheEntityBase`, UUID primary keys with `@PrePersist`, and reactive relationships.
    *   **Recommendation:** You SHOULD NOT modify this file in this task. This is part of the OLD structure. Your task is to create a NEW package structure under `backend/src/main/java/com/scrumpoker/` with the specified directories (api, domain, repository, integration, event, config, security). Do NOT copy the old entities - later tasks will create new entities following the architecture blueprint.

*   **File:** `src/main/resources/db/migration/V1__Create_tables.sql`
    *   **Summary:** This shows the current database migration pattern using Flyway with the naming convention `V{version}__{description}.sql`.
    *   **Recommendation:** Do NOT modify this file. Task I1.T3 (not this task) will create NEW migration scripts in `backend/src/main/resources/db/migration/`. This task focuses on project structure and configuration only.

### Implementation Tips & Notes

*   **Critical: Directory Structure**
    The task specification requires creating a `backend/` directory at the ROOT level with its own `pom.xml` and `src/` structure. This is NOT a refactoring of the existing project - it's creating a parallel, new structure. The current codebase at the root will eventually be replaced or moved, but for THIS task, you're establishing the new structure.

*   **Package Naming Convention**
    The architecture blueprint specifies `com.scrumpoker` as the base package (NOT `com.terrencecurran.planningpoker`). All new packages must use this naming: `com.scrumpoker.api`, `com.scrumpoker.domain`, etc.

*   **Maven Dependencies - Missing Extensions**
    The current `pom.xml` is MISSING several required Quarkus extensions specified in the task:
    - `quarkus-redis-client` (for Redis Pub/Sub and caching)
    - `quarkus-oidc` (for OAuth2/SSO authentication)
    - `quarkus-smallrye-jwt` (for JWT token generation/validation)
    - `quarkus-micrometer-registry-prometheus` (for metrics)

    You MUST add these dependencies to the NEW `backend/pom.xml`.

*   **Application Properties Placeholders**
    The task requires "placeholder values" for Redis and JWT configuration. Based on the architecture blueprint and existing patterns, you should add:
    - Redis connection URL (similar to the PostgreSQL reactive URL pattern)
    - Redis pool configuration (max-size, similar to datasource)
    - JWT token issuer, signing key path, expiration times
    - Use environment variable placeholders like `${REDIS_URL:redis://localhost:6379}` to support configuration via environment variables

*   **Quarkus Dev Mode Health Check**
    The acceptance criteria require that `/q/health` is accessible. The current project already includes `quarkus-smallrye-health`, so health checks will be automatically available. Ensure this dependency is included in the NEW `backend/pom.xml`.

*   **Directory Creation Strategy**
    Maven requires at least one Java file in a package for it to be recognized during compilation. To satisfy the "Package structure matches specification (6+ top-level packages created)" acceptance criterion, you should create `.gitkeep` files OR simple placeholder classes (like `package-info.java`) in each of the 6 required packages: `api`, `domain`, `repository`, `integration`, `event`, `config`, `security`.

*   **Build Verification**
    After creating the new structure, you can verify success by running:
    ```bash
    cd backend
    mvn clean compile
    mvn quarkus:dev
    ```
    The first command should complete without errors, and the second should start the Quarkus dev server with health checks at `http://localhost:8080/q/health`.

*   **Gitignore for Backend**
    You MUST create a `backend/.gitignore` file to exclude Maven build artifacts. Standard patterns:
    - `target/` (Maven build output)
    - `*.log` (log files)
    - `.mvn/wrapper/` (if Maven wrapper is NOT intended to be versioned)

### Architecture Decision Context

*   **Why Separate backend/ Directory?**
    The architecture blueprint (directory-structure section) shows the project will eventually have BOTH `backend/` and `frontend/` as separate top-level directories. This separation enables:
    - Independent CI/CD pipelines for backend and frontend
    - Different deployment strategies (backend on Kubernetes, frontend on CDN)
    - Clear separation of concerns for team members specializing in either backend or frontend

    This task (I1.T1) is establishing the backend foundation, while I1.T2 (the next parallelizable task) will create the `frontend/` structure.

*   **Why Hexagonal Architecture Package Structure?**
    The specified packages (`api`, `domain`, `repository`, `integration`, `event`, `config`, `security`) follow the ports-and-adapters (hexagonal) architecture pattern:
    - `domain/`: Core business logic, independent of infrastructure
    - `repository/`: Data access ports (Panache repositories)
    - `integration/`: Adapters for external systems (OAuth, Stripe, email)
    - `api/`: Primary adapters (REST controllers, WebSocket handlers)
    - `event/`: Event-driven communication (Redis Pub/Sub)
    - `config/`: Application configuration and CDI setup
    - `security/`: Cross-cutting security concerns (filters, JWT utilities)

    This structure supports the "Module Isolation" principle from the architecture blueprint and enables future microservice extraction if needed.

*   **Reactive Extensions Importance**
    The task emphasizes reactive extensions (`hibernate-reactive-panache`, `reactive-pg-client`) because the architecture requires non-blocking I/O for:
    - Handling thousands of concurrent WebSocket connections efficiently
    - Asynchronous database operations using Mutiny reactive streams
    - Redis Pub/Sub for event broadcasting across stateless application nodes

    The Quarkus reactive runtime (built on Vert.x) is a core architectural decision to meet the NFR of 500 concurrent sessions with low latency.

