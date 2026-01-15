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
  "inputs": "*   Directory structure specification from Section 3 of plan overview\n        *   Technology stack requirements (Quarkus 3.x, Java 17, reactive extensions)\n        *   Maven dependency list from architecture blueprint",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   Working Maven project buildable with `mvn clean compile`\n        *   Configured Quarkus extensions in `pom.xml`\n        *   Application properties with placeholder values for database, Redis, JWT secret\n        *   Package directory structure following hexagonal architecture",
  "acceptance_criteria": "*   `mvn clean compile` executes without errors\n        *   `mvn quarkus:dev` starts Quarkus in dev mode and serves health check at `/q/health`\n        *   All required Quarkus extensions listed in `pom.xml` dependencies\n        *   Package structure matches specification (6+ top-level packages created)",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: directory-structure (from 01_Plan_Overview_and_Setup.md)

```markdown
## 3. Directory Structure

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
    │
    ├── frontend/                         # React SPA
    │   ├── public/
    │   │   ├── index.html
    │   │   └── favicon.ico
    │   ├── src/
    │   │   ├── components/               # Reusable UI components
    │   │   │   ├── common/               # Buttons, modals, forms
    │   │   │   ├── room/                 # Room lobby, voting card, reveal
    │   │   │   ├── auth/                 # Login, OAuth callback
    │   │   │   └── dashboard/            # User dashboard, settings
    │   │   ├── pages/                    # Route-level page components
    │   │   │   ├── HomePage.tsx
    │   │   │   ├── RoomPage.tsx
    │   │   │   ├── DashboardPage.tsx
    │   │   │   └── SettingsPage.tsx
    │   │   ├── services/                 # API clients and WebSocket manager
    │   │   │   ├── api.ts                # REST API client (React Query)
    │   │   │   └── websocket.ts          # WebSocket connection manager
    │   │   ├── stores/                   # Zustand state stores
    │   │   │   ├── authStore.ts
    │   │   │   ├── roomStore.ts
    │   │   │   └── uiStore.ts
    │   │   ├── types/                    # TypeScript type definitions
    │   │   │   ├── api.ts                # API DTOs (generated from OpenAPI)
    │   │   │   └── websocket.ts          # WebSocket message types
    │   │   ├── utils/                    # Utility functions
    │   │   ├── App.tsx                   # Root component with routing
    │   │   ├── index.tsx
    │   │   └── ...
    │   ├── package.json
    │   ├── tsconfig.json
    │   └── vite.config.ts
    │
    ├── marketing-site/                   # Static marketing website
    │   ├── src/
    │   │   ├── pages/
    │   │   ├── components/
    │   │   ├── styles/
    │   │   └── content/
    │   ├── public/
    │   ├── package.json
    │   ├── tailwind.config.js
    │   └── astro.config.mjs (if Astro is chosen)
    │
    ├── api/                              # Contract-first API specifications
    │   ├── openapi.yaml                  # REST API specification
    │   └── websocket-protocol.md         # WebSocket message schema
    │
    ├── docs/                             # Architecture, ADRs, diagrams
    │   ├── 01_Context_and_Drivers.md
    │   ├── 02_Architecture_Overview.md
    │   ├── 03_System_Structure_and_Data.md
    │   ├── 04_Behavior_and_Communication.md
    │   ├── 05_Operational_Architecture.md
    │   ├── 06_Rationale_and_Future.md
    │   └── diagrams/
    │       ├── c4-system.puml
    │       ├── c4-container.puml
    │       ├── erd.puml
    │       └── sequence-vote-round.puml
    │
    ├── infra/                            # Infrastructure-as-code
    │   ├── kubernetes/
    │   │   ├── base/
    │   │   │   ├── deployment.yaml
    │   │   │   ├── service.yaml
    │   │   │   ├── ingress.yaml
    │   │   │   ├── configmap.yaml
    │   │   │   └── hpa.yaml              # HorizontalPodAutoscaler
    │   │   ├── overlays/
    │   │   │   ├── dev/
    │   │   │   ├── staging/
    │   │   │   └── production/
    │   │   └── kustomization.yaml
    │   ├── terraform/                    # AWS infrastructure (optional)
    │   │   ├── main.tf
    │   │   ├── vpc.tf
    │   │   ├── eks.tf
    │   │   ├── rds.tf
    │   │   └── elasticache.tf
    │   └── helm/                         # Helm chart (alternative to raw K8s)
    │       ├── Chart.yaml
    │       ├── values.yaml
    │       └── templates/
    │
    ├── scripts/                          # Utility scripts
    │   ├── generate-nanoid.js            # Room ID generator
    │   ├── seed-database.sql             # Test data seeding
    │   └── load-test.js                  # k6 load test script
    │
    ├── .github/                          # GitHub Actions CI/CD
    │   └── workflows/
    │       ├── backend-ci.yml            # Backend build, test, scan
    │       ├── frontend-ci.yml           # Frontend build, test, lint
    │       ├── deploy-staging.yml
    │       └── deploy-production.yml
    │
    ├── docker-compose.yml                # Local development environment
    ├── .gitignore
    ├── README.md                         # Project overview and setup instructions
    └── LICENSE
    ~~~

**Justifications for Key Choices:**

1. **Maven Standard Layout (backend):** Quarkus convention, familiar to Java developers, supports multi-module if needed
2. **Domain-Driven Directory Structure (backend/src/main/java/com/scrumpoker/domain/):** Clear bounded contexts (user, room, billing, reporting, organization) align with business domains and support future microservice extraction
3. **Separate `api/` Directory:** Decouples API contracts (OpenAPI spec, WebSocket protocol) from implementation, enables contract-first development and client SDK generation
4. **Dedicated `docs/` Hierarchy:** Centralizes architecture blueprints, diagrams (PlantUML source), ADRs, and runbooks for discoverability
5. **Separate `marketing-site/` Directory:** Isolates SEO-optimized static content from SPA, different deployment pipeline (static hosting vs. CDN)
6. **Infrastructure as Code (`infra/`):** Supports GitOps workflows, version-controlled Kubernetes manifests and Terraform scripts
7. **Flyway Migration Scripts (`backend/src/main/resources/db/migration/`):** Automated, version-controlled database schema evolution
8. **Component-Based Frontend (`frontend/src/components/`):** Reusable UI patterns, aligns with atomic design principles
9. **Testcontainers Support (`backend/src/test/`):** Integration tests with real PostgreSQL/Redis instances for high confidence
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

### Context: technology-constraints (from 01_Context_and_Drivers.md)

```markdown
#### Technology Constraints
- **Backend Framework:** Quarkus with Hibernate Reactive (specified requirement)
- **Database:** PostgreSQL for relational data integrity and JSONB support
- **Cache/Message Bus:** Redis for session state distribution and Pub/Sub messaging
- **Payment Provider:** Stripe for subscription billing and payment processing
- **Containerization:** Docker containers orchestrated via Kubernetes
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/pom.xml`
    *   **Summary:** This Maven descriptor already defines the backend as a Quarkus 3.15.1 application targeting Java 17. It imports the Quarkus BOM, pulls in every required extension from the blueprint (REST reactive, websockets, Hibernate Reactive with Panache, reactive Postgres client, Redis, OIDC, SmallRye JWT, Flyway, Prometheus, Scheduler, Fault Tolerance, Stripe SDK, MapStruct, etc.), and configures the standard plugin stack (Quarkus plugin, compiler with `-parameters` and MapStruct processors, Surefire/Failsafe, JaCoCo). Repository definitions include the Shibboleth repo for OpenSAML artifacts.
    *   **Recommendation:** You SHOULD treat this POM as the canonical dependency list—add new libraries through the existing dependency blocks to preserve BOM-managed versions, and keep Java release targets aligned with the `<maven.compiler.release>` property. When altering build configuration, extend the defined plugins instead of replacing them to avoid breaking the already wired Quarkus goals.
*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** The configuration file is fully scaffolded with placeholder-driven sections for PostgreSQL (reactive pool tuning + Flyway), Redis, JWT (issuer, signing/verification key locations, expirations), OIDC providers, Stripe, WebSockets, logging, metrics, and environment-specific overrides for dev/prod/test. Each setting is parameterized via environment variables (e.g., `DB_USERNAME`, `REDIS_URL`, `JWT_ISSUER`) and includes operational guidance comments.
    *   **Recommendation:** Reuse these placeholders whenever you add new modules (e.g., additional datasources or services). Secrets such as RSA keys or Stripe credentials MUST remain externalized via the documented environment variables—never hardcode them. Leverage the existing `%dev` profile overrides while developing locally so that production defaults stay hardened.

### Implementation Tips & Notes
*   **Tip:** The repository already follows the detailed package layout from the plan (e.g., `com/scrumpoker/api`, `domain`, `repository`, `integration`, `event`, `config`, `security`). When creating or moving classes, keep them within these bounded contexts to maintain the hexagonal structure.
*   **Tip:** Use the Maven Wrapper (`./mvnw`) at the repo root; it is already configured for the Quarkus plugin and ensures contributors run with the expected Maven version.
*   **Note:** Many infrastructure utilities (Flyway migrations, Redis/Testcontainers setup, logging defaults) are already in place. Before introducing new configuration keys or scripts, scan `application.properties` and `scripts/` to avoid duplicating existing capabilities.
*   **Warning:** `application.properties` references external key files (`/privateKey.pem`, `/publicKey.pem`) that are intentionally ignored via `.gitignore`. If your task requires touching key material, rely on the existing `backend/generate-keys.sh` helper rather than attempting to store secrets in the repository.
