# Project Plan: Scrum Poker Platform - Verification & Glossary

**Version:** 1.0
**Date:** 2025-10-17

---

<!-- anchor: verification-and-integration-strategy -->
## 5. Verification and Integration Strategy

<!-- anchor: testing-levels -->
### 5.1. Testing Levels

The project employs a comprehensive testing strategy following the test pyramid model to ensure quality at all levels:

<!-- anchor: unit-testing -->
#### Unit Testing

**Scope:** Individual classes and methods in isolation (services, utilities, validators)

**Framework:** JUnit 5 (backend), Jest/Vitest (frontend)

**Coverage Target:** >90% code coverage for service layer, >80% for overall codebase

**Approach:**
- Mock external dependencies (repositories, adapters, external services) using Mockito
- Test business logic thoroughly (happy paths, edge cases, error scenarios)
- Fast execution (<5 minutes for entire unit test suite)
- Run on every developer commit and in CI pipeline

**Examples:**
- `RoomServiceTest`: Tests room creation with unique ID generation, config validation, soft delete
- `VotingServiceTest`: Tests vote casting, consensus calculation with known inputs
- `BillingServiceTest`: Tests subscription tier transitions, Stripe integration mocking

**Acceptance Criteria:**
- All unit tests pass (`mvn test`, `npm run test:unit`)
- Coverage reports meet targets (verify with JaCoCo, Istanbul)
- No flaky tests (consistent results across runs)

---

<!-- anchor: integration-testing -->
#### Integration Testing

**Scope:** Multiple components working together with real infrastructure (database, cache, message queue)

**Framework:** Quarkus Test (`@QuarkusTest`), Testcontainers, REST Assured

**Coverage Target:** Critical integration points (API → Service → Repository → Database)

**Approach:**
- Use Testcontainers for PostgreSQL and Redis (real instances, not mocks)
- Test REST endpoints end-to-end (request → response with database persistence)
- Test WebSocket flows (connection → message handling → database → Pub/Sub broadcast)
- Verify transaction boundaries and data consistency
- Run in CI pipeline (longer execution time acceptable: 10-15 minutes)

**Examples:**
- `RoomControllerTest`: POST /rooms creates database record, GET retrieves it
- `VotingFlowIntegrationTest`: WebSocket vote message → database insert → Redis Pub/Sub → client broadcast
- `StripeWebhookControllerTest`: Webhook event → signature verification → database update

**Acceptance Criteria:**
- All integration tests pass (`mvn verify`)
- Testcontainers start successfully (PostgreSQL, Redis)
- Database schema migrations execute correctly in tests
- No test pollution (each test isolated with database cleanup)

---

<!-- anchor: end-to-end-testing -->
#### End-to-End (E2E) Testing

**Scope:** Complete user journeys from browser through entire backend stack

**Framework:** Playwright (browser automation)

**Coverage Target:** Top 10 critical user flows

**Approach:**
- Simulate real user interactions (clicks, form submissions, navigation)
- Test against running application (frontend + backend + database)
- Mock external services where necessary (OAuth providers, Stripe)
- Visual regression testing for UI components (optional, future enhancement)
- Run in CI pipeline on staging environment before production deployment

**Examples:**
- `auth.spec.ts`: OAuth login flow → callback → token storage → dashboard redirect
- `voting.spec.ts`: Create room → join → cast vote → reveal → see results
- `subscription.spec.ts`: Upgrade to Pro → Stripe checkout → webhook → tier updated

**Acceptance Criteria:**
- All E2E tests pass (`npm run test:e2e`)
- Tests run headless in CI (no UI required)
- Screenshots captured on failure for debugging
- Test execution time <10 minutes for full suite

---

<!-- anchor: smoke-testing -->
#### Smoke Testing

**Scope:** High-level validation of critical functionality in production-like environment

**Purpose:** Verify deployment succeeded and core features operational

**Approach:**
- Subset of E2E tests focusing on must-work scenarios
- Run immediately after deployment to staging/production
- Fast execution (<5 minutes)
- Trigger rollback if any smoke test fails

**Examples:**
- Health check endpoints return 200 OK
- User can log in via OAuth
- User can create and join a room
- WebSocket connection establishes successfully
- Database connectivity verified

**Acceptance Criteria:**
- Smoke tests run automatically post-deployment
- Failed smoke test prevents production rollout (deployment gate)
- Alerts sent to on-call team if smoke tests fail in production

---

<!-- anchor: performance-testing -->
#### Performance Testing

**Scope:** Validate non-functional requirements (latency, throughput, scalability)

**Framework:** k6 (load testing), Apache JMeter (alternative)

**Scenarios:**
1. **Concurrent Sessions:** 500 rooms with 10 participants each (5,000 WebSocket connections)
2. **Vote Storm:** All participants in 100 rooms vote within 10-second window
3. **API Load:** 1,000 requests/second to REST endpoints (room creation, user queries)
4. **Subscription Checkout:** 100 concurrent Stripe checkout sessions

**Metrics:**
- **Latency:** p50, p95, p99 for REST and WebSocket messages
- **Throughput:** Requests per second, WebSocket messages per second
- **Error Rate:** <1% errors under target load
- **Resource Usage:** CPU, memory, database connections

**Acceptance Criteria:**
- p95 latency <200ms for WebSocket messages under 500-room load
- p95 latency <500ms for REST API endpoints
- No database connection pool exhaustion
- Application auto-scales (HPA adds pods) under sustained load
- Performance benchmarks documented in `docs/performance-benchmarks.md`

---

<!-- anchor: security-testing -->
#### Security Testing

**Scope:** Identify vulnerabilities and validate security controls

**Tools:**
- **SAST (Static Analysis):** SonarQube code scanning
- **Dependency Scanning:** Snyk or Dependabot for vulnerable dependencies
- **Container Scanning:** Trivy for Docker image vulnerabilities
- **DAST (Dynamic Analysis):** OWASP ZAP scan against running application

**Test Areas:**
- Authentication (JWT validation, OAuth flows, session management)
- Authorization (RBAC enforcement, tier gating, org admin checks)
- Input validation (SQL injection, XSS, command injection)
- Rate limiting (brute force protection)
- Security headers (HSTS, CSP, X-Frame-Options)

**Acceptance Criteria:**
- No HIGH or CRITICAL vulnerabilities in dependency scan
- SonarQube security rating A or B
- OWASP ZAP scan shows no HIGH risk findings (or findings documented with mitigation plan)
- Penetration test report (if required for Enterprise customers) shows acceptable risk level

---

<!-- anchor: ci-cd-pipeline -->
### 5.2. CI/CD Pipeline Integration

**Continuous Integration (CI):**

Every push to `main` branch or pull request triggers:

1. **Backend CI Pipeline:**
   - Compile Java code (`mvn clean compile`)
   - Run unit tests (`mvn test`)
   - Run integration tests (`mvn verify` with Testcontainers)
   - SonarQube code quality analysis
   - Dependency vulnerability scan (Snyk)
   - Build Docker image
   - Container security scan (Trivy)
   - Publish test results and coverage reports

2. **Frontend CI Pipeline:**
   - Install dependencies (`npm ci`)
   - Lint code (`npm run lint`)
   - Run unit tests (`npm run test:unit`)
   - Build production bundle (`npm run build`)
   - Upload build artifacts

**Quality Gates:**
- Unit test coverage >80% (fail build if below threshold)
- SonarQube quality gate passed (no blocker/critical issues)
- No HIGH/CRITICAL vulnerabilities in dependencies
- Linter passes with no errors (warnings acceptable)

**Continuous Deployment (CD):**

Merges to `main` branch trigger automated deployments:

1. **Deploy to Staging:**
   - Deploy backend Docker image to Kubernetes staging namespace
   - Deploy frontend build to staging CDN
   - Run smoke tests against staging environment
   - If smoke tests pass, mark deployment as successful

2. **Deploy to Production (Manual Approval):**
   - Product owner reviews staging deployment
   - Manual approval gate in GitHub Actions
   - Deploy backend to production Kubernetes namespace (rolling update, 0 downtime)
   - Deploy frontend to production CDN
   - Run smoke tests against production
   - Monitor error rates and latency for 30 minutes
   - Automated rollback if error rate >2x baseline or smoke tests fail

**Deployment Strategy:**
- **Rolling Update:** MaxSurge=1, MaxUnavailable=0 (ensures at least 2 pods always available)
- **Canary Deployment (Future):** Route 10% traffic to new version, monitor for 15 minutes, gradually increase to 100%
- **Blue/Green Deployment (Future):** Maintain two full environments, instant traffic switch via load balancer

---

<!-- anchor: code-quality-gates -->
### 5.3. Code Quality Gates

**Automated Quality Checks:**

1. **Code Coverage:**
   - Backend: JaCoCo reports, threshold 80% line coverage
   - Frontend: Istanbul/c8 reports, threshold 75% statement coverage
   - Fail CI build if below threshold

2. **Static Analysis (SonarQube):**
   - Code smells: <5 per 1000 lines of code
   - Duplications: <3% duplicated lines
   - Maintainability rating: A or B
   - Reliability rating: A or B
   - Security rating: A or B

3. **Linting:**
   - Backend: Checkstyle or SpotBugs for Java code style
   - Frontend: ESLint with recommended rules, Prettier for formatting
   - Zero errors allowed (warnings are flagged but don't fail build)

4. **Dependency Audit:**
   - `mvn dependency:analyze` for unused dependencies
   - `npm audit` for frontend security vulnerabilities
   - Dependabot automatic PRs for dependency updates

**Manual Quality Checks:**

1. **Code Review:**
   - All pull requests require at least 1 approval
   - Review checklist: code clarity, test coverage, security considerations, performance impact
   - Backend and frontend leads review architectural changes

2. **Architectural Decision Records (ADRs):**
   - Significant architectural decisions documented in `docs/adr/`
   - ADR template: Context, Decision, Consequences, Alternatives Considered
   - ADRs reviewed by technical lead before implementation

---

<!-- anchor: artifact-validation -->
### 5.4. Artifact Validation

**API Specification Validation:**

1. **OpenAPI Specification:**
   - Validate YAML against OpenAPI 3.1 schema using Spectral or Swagger CLI
   - Generate TypeScript client SDK, verify compilation without errors
   - Import into Postman or Insomnia, verify all endpoints documented
   - Contract testing: Pact or Spring Cloud Contract to ensure API matches spec

2. **WebSocket Protocol Specification:**
   - Validate message JSON schemas using AJV validator
   - Test sample messages against schemas, verify validation passes
   - Frontend and backend teams review for completeness

**Diagram Validation:**

1. **PlantUML Diagrams:**
   - Syntax validation: Render diagrams using PlantUML CLI or online server
   - Visual review: Diagrams accurately represent system structure and flows
   - Consistency check: Component names in diagrams match code package/class names

2. **Architecture Blueprint:**
   - Peer review by senior developers and architects
   - Traceability: Diagrams referenced in implementation tasks (bidirectional links)
   - Update diagrams when architecture changes (living documentation)

**Database Migration Validation:**

1. **Flyway/Liquibase Scripts:**
   - Syntax validation: Run migrations against local PostgreSQL instance
   - Idempotency: Re-run migrations, verify no errors (Flyway checksum validation)
   - Rollback testing: Test rollback scripts (if applicable) in staging
   - Data migration testing: Validate data transformations with sample data

---

<!-- anchor: integration-strategy -->
### 5.5. Integration Strategy

**Component Integration:**

1. **Service Layer Integration:**
   - Services depend on repositories via constructor injection (testable)
   - Integration tests verify service → repository → database flow
   - Use Testcontainers for realistic database behavior

2. **REST API Integration:**
   - REST controllers depend on services via injection
   - Integration tests verify HTTP request → controller → service → database → response
   - Use REST Assured for fluent HTTP test assertions

3. **WebSocket Integration:**
   - WebSocket handlers depend on services and event publisher
   - Integration tests verify WebSocket message → handler → service → database → Redis Pub/Sub → broadcast
   - Use Quarkus WebSocket test client or custom WebSocket client library

4. **External Service Integration:**
   - Adapters abstract external services (OAuth, Stripe, email)
   - Unit tests mock adapters, integration tests use sandboxes (Stripe test mode, OAuth dev apps)
   - Contract testing or VCR (record/replay) for external API calls

**Cross-Module Integration:**

1. **Backend ↔ Database:**
   - Hibernate Reactive Panache repositories
   - Integration tests with Testcontainers PostgreSQL
   - Migration scripts applied automatically in tests

2. **Backend ↔ Redis:**
   - Quarkus reactive Redis client
   - Integration tests with Testcontainers Redis
   - Pub/Sub and caching tested end-to-end

3. **Frontend ↔ Backend:**
   - React Query hooks calling REST API and WebSocket
   - E2E tests verify frontend → backend integration
   - OpenAPI-generated TypeScript client ensures type safety

4. **Backend ↔ External Services:**
   - OAuth2Adapter tested with real OAuth providers (dev apps)
   - StripeAdapter tested in Stripe test mode
   - Email adapter tested with sandbox (Mailtrap or similar)

---

<!-- anchor: glossary -->
## 6. Glossary

| Term | Definition |
|------|------------|
| **ACID** | Atomicity, Consistency, Isolation, Durability - properties guaranteeing reliable database transactions |
| **ADR** | Architectural Decision Record - document capturing important architectural choices with context and rationale |
| **API Gateway** | Entry point for API requests, handles routing, authentication, rate limiting |
| **Backpressure** | Flow control mechanism preventing overwhelming downstream systems with too many messages |
| **Bean Validation** | JSR-380 specification for declarative validation using annotations (e.g., `@NotNull`, `@Size`) |
| **CDI** | Contexts and Dependency Injection - Java EE standard for dependency injection and lifecycle management |
| **CQRS** | Command Query Responsibility Segregation - pattern separating read and write operations for optimization |
| **Circuit Breaker** | Resilience pattern preventing cascading failures by stopping requests to failing services |
| **Consensus** | Agreement among voters in estimation (low variance in votes, e.g., all votes within 2 points) |
| **DTO** | Data Transfer Object - object carrying data between processes, often for API request/response |
| **Event Sourcing** | Pattern storing state changes as sequence of events rather than current state snapshots |
| **Fibonacci Deck** | Estimation deck using Fibonacci sequence (1, 2, 3, 5, 8, 13, 21) for story point voting |
| **Flyway** | Database migration tool managing schema versions with SQL or Java migrations |
| **Graceful Shutdown** | Controlled application shutdown allowing in-flight requests to complete before termination |
| **Hexagonal Architecture** | Architectural pattern (Ports & Adapters) isolating core logic from infrastructure |
| **HPA** | Horizontal Pod Autoscaler - Kubernetes resource scaling pods based on metrics (CPU, custom) |
| **Idempotency** | Property where operation produces same result regardless of how many times executed |
| **Ingress** | Kubernetes resource managing external access to services (HTTP/HTTPS routing, TLS) |
| **JIT Provisioning** | Just-In-Time user account creation during first SSO login |
| **JWT** | JSON Web Token - compact token format for authentication with signature verification |
| **Liveness Probe** | Kubernetes health check determining if container is alive (restart if failing) |
| **MapStruct** | Java library generating type-safe mappers for DTO ↔ entity conversion |
| **MDC** | Mapped Diagnostic Context - thread-local storage for logging contextual information (e.g., correlation ID) |
| **Mutiny** | Reactive programming library used by Quarkus for asynchronous, non-blocking operations |
| **Nanoid** | URL-safe, compact unique ID generator (alternative to UUID, shorter: 6-char vs. 36-char) |
| **OIDC** | OpenID Connect - identity layer on OAuth2 for authentication and user profile retrieval |
| **Panache** | Quarkus extension simplifying Hibernate with active record or repository patterns |
| **PKCE** | Proof Key for Code Exchange - OAuth2 security extension for public clients (SPAs, mobile) |
| **Pub/Sub** | Publish/Subscribe messaging pattern for decoupled event broadcasting |
| **Reactive Streams** | Specification for asynchronous stream processing with non-blocking backpressure |
| **Readiness Probe** | Kubernetes health check determining if container ready to receive traffic |
| **REST Assured** | Java library for fluent testing of RESTful APIs |
| **SAML2** | Security Assertion Markup Language 2.0 - XML-based SSO protocol |
| **ServiceMonitor** | Prometheus Operator custom resource configuring Prometheus to scrape metrics from services |
| **Soft Delete** | Marking records as deleted (e.g., `deleted_at` timestamp) without physical deletion |
| **Sticky Sessions** | Load balancer routing same client to same backend instance (session affinity) |
| **Testcontainers** | Java library providing lightweight, disposable database/cache containers for integration tests |
| **Uni** | Mutiny type representing asynchronous single-item result (similar to CompletableFuture) |
| **Multi** | Mutiny type representing asynchronous stream of 0-N items (similar to Reactive Streams Publisher) |
| **WebSocket** | Protocol providing full-duplex communication over single TCP connection |
| **Zustand** | Lightweight React state management library with minimal boilerplate |

---

<!-- anchor: acronyms -->
### Common Acronyms Used in Plan

- **API:** Application Programming Interface
- **CI/CD:** Continuous Integration / Continuous Deployment
- **CDN:** Content Delivery Network
- **CRUD:** Create, Read, Update, Delete
- **CSV:** Comma-Separated Values
- **DAST:** Dynamic Application Security Testing
- **ERD:** Entity Relationship Diagram
- **E2E:** End-to-End (testing)
- **GDPR:** General Data Protection Regulation
- **HTTP:** Hypertext Transfer Protocol
- **HTTPS:** HTTP Secure (over TLS/SSL)
- **IDE:** Integrated Development Environment
- **JAX-RS:** Java API for RESTful Web Services
- **JPA:** Java Persistence API
- **JSON:** JavaScript Object Notation
- **JSONB:** JSON Binary (PostgreSQL data type)
- **JVM:** Java Virtual Machine
- **K8s:** Kubernetes (8 letters between K and s)
- **MVP:** Minimum Viable Product
- **NFR:** Non-Functional Requirement
- **ORM:** Object-Relational Mapping
- **PDF:** Portable Document Format
- **POJO:** Plain Old Java Object
- **RBAC:** Role-Based Access Control
- **REST:** Representational State Transfer
- **RPC:** Remote Procedure Call
- **SAST:** Static Application Security Testing
- **SDK:** Software Development Kit
- **SEO:** Search Engine Optimization
- **SPA:** Single-Page Application
- **SQL:** Structured Query Language
- **SSO:** Single Sign-On
- **TLS:** Transport Layer Security
- **TTL:** Time To Live
- **UI:** User Interface
- **UX:** User Experience
- **UUID:** Universally Unique Identifier
- **YAML:** YAML Ain't Markup Language (recursive acronym)

---

**End of Plan Document**

For iteration-specific task details, see:
- [Iteration 1](./02_Iteration_I1.md)
- [Iteration 2](./02_Iteration_I2.md)
- [Iteration 3](./02_Iteration_I3.md)
- [Iteration 4](./02_Iteration_I4.md)
- [Iteration 5](./02_Iteration_I5.md)
- [Iteration 6](./02_Iteration_I6.md)
- [Iteration 7](./02_Iteration_I7.md)
- [Iteration 8](./02_Iteration_I8.md)
