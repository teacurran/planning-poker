# System Architecture Blueprint: Scrum Poker Platform

---

<!-- anchor: proposed-architecture -->
## 3. Proposed Architecture

<!-- anchor: architectural-style -->
### 3.1. Architectural Style

**Primary Style:** **Modular Monolith with Event-Driven Reactive Patterns**

#### Rationale

The chosen architectural style balances the simplicity and operational efficiency required for a small-to-medium scale SaaS application with the scalability and real-time responsiveness demanded by WebSocket-based collaborative features.

**Why Modular Monolith:**
1. **Team Size & Velocity:** A modular monolith allows a small development team to iterate rapidly without the operational overhead of microservices (service discovery, distributed tracing, inter-service versioning)
2. **Transactional Consistency:** Core business operations (room creation, vote recording, subscription changes) benefit from ACID transactions within a single database
3. **Simplified Deployment:** Single deployable artifact reduces CI/CD complexity and eliminates network latency between services
4. **Clear Upgrade Path:** Well-defined module boundaries (User Management, Room Management, Billing, Reporting) enable future extraction into microservices if scale demands

**Why Event-Driven Reactive Patterns:**
1. **Real-Time Requirements:** WebSocket connections require non-blocking I/O to efficiently handle thousands of concurrent connections on limited thread pools
2. **Quarkus Reactive:** Leverages Quarkus's reactive runtime (built on Vert.x) for high-concurrency WebSocket handling and asynchronous database operations
3. **Event Propagation:** Redis Pub/Sub enables stateless application nodes to broadcast room state changes (votes, reveals, participant joins) across all connected clients
4. **Resource Efficiency:** Reactive streams and backpressure handling prevent memory overflow during traffic spikes

**Alternatives Considered:**
- **Microservices Architecture:** Rejected due to operational complexity overhead for initial MVP and team size. The modular monolith provides a clear path to extract services (e.g., Billing, Reporting) when specific scaling needs emerge.
- **Pure Serverless (AWS Lambda/Azure Functions):** Rejected due to cold start latency incompatible with real-time WebSocket requirements and higher costs for long-lived connections. Serverless may be appropriate for background jobs (report generation, email notifications).
- **Monolithic N-tier:** Rejected as it lacks the module isolation needed for future evolution and doesn't leverage Quarkus's reactive capabilities for handling WebSocket concurrency.

**Key Architectural Principles:**
1. **Module Isolation:** Bounded contexts communicate via well-defined interfaces (e.g., `UserService`, `RoomService`, `BillingService`)
2. **Hexagonal Architecture (Ports & Adapters):** Core domain logic isolated from infrastructure concerns (databases, message brokers, external APIs)
3. **CQRS-Lite:** Read-heavy operations (reporting, session history) use optimized read models and query paths separate from write operations
4. **Eventual Consistency:** Non-critical updates (analytics aggregations, audit logs) processed asynchronously via message queues

---

<!-- anchor: technology-stack-summary -->
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
