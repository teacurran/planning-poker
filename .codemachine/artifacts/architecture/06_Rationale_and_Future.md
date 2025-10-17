# System Architecture Blueprint: Scrum Poker Platform

---

<!-- anchor: design-rationale-and-tradeoffs -->
## 4. Design Rationale & Trade-offs

<!-- anchor: key-decisions-summary -->
### 4.1. Key Decisions Summary

<!-- anchor: decision-modular-monolith -->
#### 1. Modular Monolith over Microservices

**Decision:** Implement a modular monolith with well-defined bounded contexts (User Management, Room Management, Billing, Reporting) rather than decomposing into microservices from day one.

**Rationale:**
- **Operational Simplicity:** Single deployment artifact reduces CI/CD complexity, eliminates distributed debugging challenges, and requires fewer infrastructure components (no service mesh, API gateway complexity, distributed tracing overhead)
- **Transactional Consistency:** Core business operations (vote recording with room state updates, subscription changes with feature access modifications) benefit from ACID transactions within a single database
- **Team Velocity:** Small development team can iterate faster without inter-service contract negotiations, versioning coordination, and deployment orchestration
- **Cost Efficiency:** Shared database connection pools, single JVM memory footprint, reduced network overhead between components

**Trade-offs:**
- **Scaling Granularity:** Cannot independently scale individual modules (e.g., reporting service separate from real-time voting). Mitigation: Horizontal pod auto-scaling handles overall load, background worker separated for async jobs.
- **Technology Heterogeneity:** Entire application bound to Java/Quarkus stack. Mitigation: Modular boundaries enable future extraction to polyglot microservices if specific components demand different technologies.
- **Deployment Coupling:** Bug in one module requires full application redeployment. Mitigation: Comprehensive integration tests, canary deployments, feature flags for gradual rollouts.

---

<!-- anchor: decision-quarkus-reactive -->
#### 2. Quarkus Reactive Stack for WebSocket Concurrency

**Decision:** Leverage Quarkus's reactive runtime (Vert.x-based) with Hibernate Reactive for non-blocking I/O rather than traditional synchronous Spring Boot with blocking JDBC.

**Rationale:**
- **High Concurrency:** Reactive streams handle thousands of concurrent WebSocket connections on limited thread pools (event loop model vs. thread-per-request)
- **Resource Efficiency:** Non-blocking database queries and Redis operations prevent thread pool exhaustion under load spikes
- **Cloud-Native Optimization:** Fast startup time (<1 second in JVM mode) enables rapid auto-scaling, reduced memory footprint (90MB vs. 150MB+ for Spring Boot)
- **Requirement Alignment:** Specified in product requirements, team expertise available

**Trade-offs:**
- **Learning Curve:** Reactive programming paradigms (Mutiny, backpressure handling) steeper than traditional imperative code. Mitigation: Team training, pair programming, comprehensive code examples in documentation.
- **Debugging Complexity:** Asynchronous stack traces harder to follow than synchronous calls. Mitigation: Structured logging with correlation IDs, reactive debugging tools, OpenTelemetry tracing.
- **Ecosystem Maturity:** Fewer third-party libraries support reactive patterns vs. blocking APIs. Mitigation: Quarkus extensions cover critical needs (Hibernate Reactive, reactive Redis client), blocking calls isolated to background workers.

---

<!-- anchor: decision-postgres-primary -->
#### 3. PostgreSQL as Primary Data Store

**Decision:** Use PostgreSQL for all persistent data (users, rooms, votes, sessions, subscriptions) rather than polyglot persistence (e.g., MongoDB for rooms, PostgreSQL for billing).

**Rationale:**
- **Relational Integrity:** Foreign key constraints ensure referential integrity (votes → rounds → rooms → users), preventing orphaned data
- **ACID Guarantees:** Critical for payment transactions, subscription state changes, and vote recording consistency
- **JSONB Flexibility:** Hybrid approach using JSONB columns for semi-structured data (room configurations, user preferences) combines schema flexibility with queryability
- **Advanced Features:** Partitioning for time-series data (session history, audit logs), full-text search for room titles, window functions for analytics

**Trade-offs:**
- **Horizontal Scaling Complexity:** PostgreSQL scaling requires read replicas + manual sharding vs. MongoDB's native horizontal scaling. Mitigation: Read replicas for reporting queries sufficient for target scale (500 concurrent sessions), vertical scaling available if needed.
- **Schema Migrations:** Relational schema changes require coordinated migrations vs. schema-less flexibility. Mitigation: Flyway/Liquibase automated migrations, JSONB columns for frequently changing structures.

---

<!-- anchor: decision-redis-pubsub -->
#### 4. Redis Pub/Sub for WebSocket Event Broadcasting

**Decision:** Use Redis Pub/Sub channels to broadcast WebSocket events across stateless application nodes rather than sticky sessions to single nodes or distributed data grids (Hazelcast, Ignite).

**Rationale:**
- **Simplicity:** Redis already required for session caching, Pub/Sub adds minimal operational overhead
- **Low Latency:** In-memory Pub/Sub delivers messages to subscribers in <10ms, acceptable for real-time voting reveals
- **Stateless App Nodes:** Any node can handle any WebSocket connection, load balancer can distribute freely (sticky sessions improve efficiency but not required for correctness)
- **Proven Pattern:** Redis Pub/Sub widely used for WebSocket broadcasting (Socket.IO, SignalR reference implementations)

**Trade-offs:**
- **No Message Persistence:** Pub/Sub fire-and-forget, disconnected clients miss events. Mitigation: Client reconnection logic requests missed events from database (last 5 minutes cached), critical state (votes) persisted to PostgreSQL before broadcast.
- **Scalability Ceiling:** Redis Pub/Sub single-threaded, limited to ~100K messages/second per instance. Mitigation: Sufficient for target scale (500 rooms * 10 participants * 10 msg/min = 50K msg/min peak), cluster mode sharding available if needed.
- **Ordering Guarantees:** Messages may arrive out-of-order across channels. Mitigation: Single channel per room (`room:{roomId}`) preserves order within session, sequence numbers in messages for client-side reordering if needed.

---

<!-- anchor: decision-stripe-billing -->
#### 5. Stripe for Subscription Billing

**Decision:** Integrate Stripe for payment processing and subscription lifecycle management rather than building custom billing or using alternatives (PayPal, Braintree).

**Rationale:**
- **Developer Experience:** Best-in-class API design, comprehensive SDKs (Java, TypeScript), extensive documentation and testing tools (Stripe CLI)
- **Subscription Features:** Native support for tiered pricing, metered billing (future), proration, trial periods, coupon codes
- **Compliance:** PCI-DSS Level 1 certified, handles sensitive card data tokenization, reduces compliance burden
- **Webhook Reliability:** Robust webhook delivery with automatic retries, signature verification, idempotency key support

**Trade-offs:**
- **Transaction Fees:** 2.9% + $0.30 per transaction higher than some competitors. Mitigation: Pricing model accounts for fees, annual billing option reduces per-transaction cost.
- **Vendor Lock-In:** Stripe-specific subscription IDs and webhook payloads tightly coupled. Mitigation: Adapter pattern isolates Stripe integration, internal domain models map from Stripe entities.
- **Geographic Limitations:** Not available in all countries. Mitigation: Initial launch targets North America/Europe where Stripe is supported, alternative payment methods (PayPal) deferred to future roadmap.

---

<!-- anchor: decision-react-frontend -->
#### 6. React for Frontend SPA

**Decision:** Build frontend as React single-page application with TypeScript rather than Vue (also specified as option), server-side rendering (Next.js), or multi-page application.

**Rationale:**
- **Ecosystem Maturity:** Largest ecosystem of third-party libraries (React Query, Recharts, Headless UI), extensive community resources
- **Real-Time Updates:** Concurrent rendering (React 18) optimizes UI responsiveness during frequent WebSocket state updates
- **Type Safety:** TypeScript integration prevents runtime errors, auto-completion improves developer productivity
- **Talent Pool:** Wider availability of React developers vs. Vue or Svelte

**Trade-offs:**
- **Bundle Size:** React library larger than Vue/Svelte (45KB gzipped vs. 16KB/3KB). Mitigation: Code splitting, tree shaking, CDN caching reduce perceived load time.
- **SEO Challenges:** SPA requires JavaScript for rendering, not ideal for marketing pages. Mitigation: Separate marketing website (Next.js or static site generator) for SEO-critical content, application SPA for authenticated experience.
- **Initial Load Time:** SPA downloads entire bundle before rendering vs. server-side rendering. Mitigation: Skeleton screens, service worker caching for repeat visits, lazy loading for secondary features.

---

<!-- anchor: alternatives-considered -->
### 4.2. Alternatives Considered

<!-- anchor: alternative-microservices -->
#### Microservices Architecture

**Description:** Decompose application into separate services (User Service, Room Service, Billing Service, Notification Service) communicating via REST APIs or message queues.

**Why Not Chosen:**
- Operational complexity disproportionate to team size (2-5 developers) and target scale
- Distributed transaction challenges for operations spanning multiple services (room creation + user preference update)
- Network latency and serialization overhead for inter-service calls
- Increased infrastructure costs (separate databases, multiple deployments, service mesh)

**Future Consideration:** Extract Reporting Service as separate microservice if analytics queries impact real-time voting performance.

---

<!-- anchor: alternative-graphql -->
#### GraphQL API

**Description:** Replace REST API with GraphQL for flexible client queries and subscriptions for real-time updates.

**Why Not Chosen:**
- Complexity overhead (schema definition, resolver implementation, N+1 query problem mitigation) not justified by query flexibility benefits
- WebSocket protocol already handles real-time updates, GraphQL subscriptions add redundant mechanism
- REST caching (HTTP cache headers, CDN) simpler than GraphQL caching strategies
- Team unfamiliarity increases development timeline risk

**Future Consideration:** GraphQL suitable if mobile native apps require highly customized data fetching patterns.

---

<!-- anchor: alternative-mongodb -->
#### MongoDB for Room/Vote Storage

**Description:** Use MongoDB for room configurations and vote data due to flexible schema, horizontal scaling, and document model alignment with room state.

**Why Not Chosen:**
- Lack of multi-document ACID transactions (in versions <4.0) problematic for vote recording consistency
- Team expertise stronger in PostgreSQL query optimization and indexing
- PostgreSQL JSONB provides sufficient schema flexibility for room configurations
- Referential integrity (foreign keys) critical for data quality in billing/subscription context

**Future Consideration:** MongoDB viable if room state complexity explodes beyond JSONB manageability.

---

<!-- anchor: alternative-kafka -->
#### Apache Kafka for Event Streaming

**Description:** Replace Redis Pub/Sub with Kafka for WebSocket event broadcasting and asynchronous job processing.

**Why Not Chosen:**
- Operational complexity (ZooKeeper/KRaft cluster, partition management, consumer group coordination) excessive for MVP
- Higher latency (~10-50ms) vs. Redis Pub/Sub (<10ms) for real-time voting reveals
- Infrastructure costs (3+ broker nodes for production reliability) vs. Redis cluster already required
- Redis Streams sufficient for job queue needs (report generation, emails)

**Future Consideration:** Kafka appropriate if event sourcing becomes architectural requirement or event replay needed for compliance.

---

<!-- anchor: alternative-websocket-fallback -->
#### Server-Sent Events (SSE) Instead of WebSockets

**Description:** Use SSE for server-to-client updates (vote notifications, reveals) with REST POST for client-to-server actions (vote casting).

**Why Not Chosen:**
- SSE unidirectional (server → client only), requires separate HTTP requests for client actions, doubling roundtrips
- No native browser support for reconnection with event ID synchronization (custom implementation needed)
- HTTP/1.1 connection limits (6 per domain) problematic if multiple tabs open
- WebSocket bidirectional protocol cleaner for chat/reactions features

**Future Consideration:** SSE viable fallback for corporate proxies blocking WebSocket upgrades (graceful degradation path).

---

<!-- anchor: known-risks-and-mitigation -->
### 4.3. Known Risks & Mitigation

<!-- anchor: risk-websocket-scaling -->
#### Risk 1: WebSocket Connection Scaling Limits

**Risk Description:** Redis Pub/Sub single-threaded message processing becomes bottleneck when concurrent rooms exceed 1,000 (10,000+ WebSocket connections).

**Impact:** High - Degrades core real-time voting experience, revenue loss if users abandon sessions.

**Probability:** Medium - Dependent on adoption velocity and marketing success.

**Mitigation Strategies:**
1. **Monitoring:** Track `scrumpoker_websocket_connections_total` and Redis Pub/Sub latency metrics, alert at 70% of tested capacity
2. **Optimization:** Implement message batching (aggregate multiple vote events into single broadcast), binary serialization (MessagePack vs. JSON)
3. **Sharding:** Distribute rooms across multiple Redis clusters based on `room_id` hash, route WebSocket connections to corresponding cluster
4. **Alternative Technology:** Evaluate NATS or RabbitMQ if Redis Pub/Sub insufficient, both support higher throughput with horizontal scaling

---

<!-- anchor: risk-database-hotspots -->
#### Risk 2: Database Write Hotspots on High-Traffic Rooms

**Risk Description:** Popular rooms (10+ participants, rapid voting) create write contention on `vote` table inserts, causing timeout errors.

**Impact:** Medium - Affects premium users (larger teams), negative reviews and churn.

**Probability:** Low - Target audience is small teams (2-12 participants), edge case for conferences/workshops.

**Mitigation Strategies:**
1. **Batch Inserts:** Aggregate multiple votes within 100ms window, insert as single JDBC batch operation
2. **Connection Pooling:** Increase pool size dynamically for high write load, separate pool for vote writes vs. reads
3. **Database Tuning:** Optimize PostgreSQL `shared_buffers`, `work_mem` for write-heavy workload, consider UNLOGGED tables for transient vote data
4. **Partitioning:** Partition `vote` table by `room_id` range if single table becomes bottleneck

---

<!-- anchor: risk-oauth-provider-outage -->
#### Risk 3: OAuth Provider Outage (Google/Microsoft)

**Risk Description:** Google or Microsoft OAuth2 service downtime prevents new user logins, blocks access to authenticated features.

**Impact:** High - Unable to acquire new paying customers, existing users locked out if tokens expired.

**Probability:** Low - Google/Microsoft SLAs >99.9%, but occasional outages occur (e.g., GCP outage 2023-Q2).

**Mitigation Strategies:**
1. **Token Lifetime:** Extend access token expiration to 4 hours (vs. 1 hour) during detected provider outage, refresh tokens valid 30 days
2. **Cached User Sessions:** Server-side session cache in Redis persists user identity even if OAuth provider unreachable for token validation
3. **Degraded Mode:** Allow existing authenticated users to continue sessions, disable new registrations with informative error message
4. **Multi-Provider Redundancy:** If Google OAuth down, prompt users to link Microsoft account as backup (requires user action pre-outage)

---

<!-- anchor: risk-stripe-webhook-failures -->
#### Risk 4: Stripe Webhook Delivery Failures

**Risk Description:** Network issues or application downtime cause missed Stripe webhooks for subscription events (cancellations, payment failures), leading to stale subscription state.

**Impact:** High - Users billed but tier not activated, or subscriptions canceled but features still accessible (revenue leakage).

**Probability:** Medium - Webhook delivery not guaranteed, requires idempotent handling and manual reconciliation.

**Mitigation Strategies:**
1. **Webhook Retry Logic:** Stripe retries failed webhooks automatically (up to 3 days), endpoint must return 200 OK only after processing
2. **Idempotency Keys:** Store `stripe_event_id` in database, skip processing if event already handled (prevent duplicate credits/debits)
3. **Manual Reconciliation:** Daily scheduled job queries Stripe API for recent subscription changes, compares with local database, flags discrepancies for admin review
4. **Monitoring:** Alert if webhook processing error rate >1%, manual investigation required

---

<!-- anchor: risk-gdpr-data-deletion -->
#### Risk 5: GDPR Data Deletion Compliance

**Risk Description:** User exercises "right to be forgotten," requiring complete data deletion across PostgreSQL, Redis, S3, audit logs, and anonymization in historical session data.

**Impact:** Medium - Non-compliance fines (up to 4% of revenue), reputational damage.

**Probability:** Medium - GDPR requests uncommon but legally mandated.

**Mitigation Strategies:**
1. **Soft Deletes:** Mark user as `deleted_at = NOW()`, anonymize PII (email → hashed, display_name → "Anonymous User #{user_id_hash}")
2. **Cascading Deletion:** Foreign key `ON DELETE SET NULL` for room ownership, preserve vote records but dissociate from user identity
3. **Audit Log Retention:** Anonymize user data in audit logs while preserving action timestamps for compliance (6-year retention for financial records)
4. **Data Export:** Provide user data export (JSON format) before deletion, fulfill "right to data portability" simultaneously
5. **Verification:** Manual QA checklist for deletion completeness (database queries, S3 bucket search, Redis key scan)

---

<!-- anchor: risk-concurrent-deployment -->
#### Risk 6: Zero-Downtime Deployment with WebSocket Connections

**Risk Description:** Rolling deployment terminates active WebSocket connections when pods shut down, disrupting ongoing estimation sessions.

**Impact:** Medium - Poor user experience (forced reconnection), potential vote loss if client disconnected mid-cast.

**Probability:** High - Occurs on every production deployment (weekly cadence).

**Mitigation Strategies:**
1. **Graceful Shutdown:** Kubernetes `preStop` hook sends WebSocket close frame (code 1001 "going away") 30 seconds before SIGTERM, clients reconnect to new pods
2. **Connection Draining:** New WebSocket connections routed to new pods immediately, existing connections drain over 60-second window
3. **Client Reconnection:** Automatic reconnection with exponential backoff, replay missed events from database or Redis cache
4. **Deployment Timing:** Schedule deployments during low-traffic windows (2am-4am UTC), avoid Friday deployments
5. **Blue/Green Deployment:** Maintain two full environments, switch load balancer traffic atomically (higher infrastructure cost, reserved for major releases)

---

<!-- anchor: future-considerations -->
## 5. Future Considerations

<!-- anchor: potential-evolution -->
### 5.1. Potential Evolution

<!-- anchor: evolution-native-mobile -->
#### Native Mobile Applications (iOS/Android)

**Trigger:** User feedback requesting offline mode, push notifications, or native mobile UX (6+ months post-launch).

**Architectural Impact:**
- **Mobile BFF (Backend for Frontend):** GraphQL API layer optimized for mobile data fetching patterns, reduces over-fetching vs. REST
- **Push Notifications:** Integrate Firebase Cloud Messaging (FCM) or Apple Push Notification Service (APNs) for round start/reveal alerts
- **Offline Support:** Local SQLite database for room configurations, sync via REST API when connection restored
- **WebSocket Optimization:** Binary protocol (Protocol Buffers) reduces mobile data usage vs. JSON

**Technology Stack:**
- React Native or Flutter for cross-platform development
- Native Swift/Kotlin for performance-critical features (real-time vote animations)

---

<!-- anchor: evolution-ai-estimation -->
#### AI-Powered Estimation Suggestions

**Trigger:** Enterprise tier customers request historical estimation analysis to recommend story point values (12+ months post-launch).

**Architectural Impact:**
- **ML Pipeline:** Extract room → Python ML service (FastAPI microservice), train models on session history
- **Feature Engineering:** Aggregate vote variance, team velocity, story complexity embeddings from title/description
- **Model Serving:** Deploy trained models (scikit-learn, TensorFlow Lite) alongside application, inference via REST API
- **Data Requirements:** Minimum 100 completed sessions per organization for statistically valid recommendations

**Privacy Considerations:**
- Enterprise tier opt-in required, models trained only on org's own data (no cross-organization learning)
- GDPR compliance: model explanations (SHAP values) provided to users, right to object to automated decisions

---

<!-- anchor: evolution-integrations -->
#### Project Management Tool Integrations (Jira, Azure DevOps)

**Trigger:** Enterprise customers request seamless workflow for importing stories from Jira and exporting estimates back (9+ months post-launch).

**Architectural Impact:**
- **Integration Service:** Separate microservice handling OAuth2 flows for Jira/Azure DevOps APIs, webhook subscriptions for story updates
- **Event-Driven Sync:** Kafka topic for bidirectional events (story imported → estimation completed → estimate exported)
- **Schema Mapping:** Transform Jira Issue fields to Room Story format, custom field mapping UI for enterprise admins
- **Rate Limiting:** Respect Jira API rate limits (100 req/min), queue import jobs during bulk operations

**Business Model:**
- Integration marketplace: $10/month per integration (Jira, Azure DevOps, GitHub Projects)
- Enterprise tier includes unlimited integrations

---

<!-- anchor: evolution-white-label -->
#### White-Label / Self-Hosted Deployment

**Trigger:** Enterprise customers (financial services, government) require on-premise deployment for regulatory compliance (18+ months post-launch).

**Architectural Impact:**
- **Kubernetes Helm Chart:** Packaged deployment with all dependencies (PostgreSQL, Redis via Bitnami charts)
- **Licensing Service:** License key validation service checks seat count, feature tier, expiration date
- **Update Mechanism:** Docker image registry hosted by vendor, customer pulls updates on-demand (no auto-update)
- **Support Model:** Dedicated support tier ($500/month) for deployment assistance, troubleshooting

**Operational Challenges:**
- Version fragmentation (customers on older versions with known bugs)
- Database migration coordination (customer schedules maintenance windows)
- Security patches (communicate CVEs, ensure timely customer updates)

---

<!-- anchor: evolution-advanced-analytics -->
#### Advanced Analytics & Reporting

**Trigger:** Pro+ and Enterprise tiers request predictive analytics (velocity forecasting, team performance benchmarks) (12+ months post-launch).

**Architectural Impact:**
- **Data Warehouse:** Extract session history to Snowflake/BigQuery for OLAP queries, separate from transactional PostgreSQL
- **ETL Pipeline:** Daily batch jobs (dbt or Airflow) aggregate votes, calculate team metrics, generate pre-computed reports
- **BI Dashboards:** Embedded Metabase or Looker for interactive exploration, drill-down from org-level to room-level
- **Custom Reports:** Report builder UI for admins (drag-drop dimensions/measures), generates SQL via query builder

**Metrics Examples:**
- Team estimation consistency score (variance across sessions)
- Velocity trends (average story points per sprint)
- Consensus rate improvement over time
- Participant engagement (vote frequency, discussion contributions)

---

<!-- anchor: evolution-horizontal-scaling -->
#### Horizontal Scaling Beyond Single Region

**Trigger:** User base exceeds 5,000 concurrent sessions, geographic distribution spans APAC/EMEA/Americas (24+ months post-launch).

**Architectural Impact:**
- **Multi-Region Deployment:** Replicate Kubernetes clusters in us-east-1, eu-west-1, ap-southeast-1
- **Data Residency:** Region-specific PostgreSQL instances for GDPR compliance (EU user data stored in EU region)
- **Global Load Balancing:** Route53 latency-based routing directs users to nearest region
- **Cross-Region Replication:** Asynchronous replication of user accounts, subscriptions to all regions (eventual consistency acceptable)
- **Room Affinity:** Room data stored only in region where created, WebSocket connections pinned to that region (minimizes latency)

**Challenges:**
- Subscription state synchronization (Stripe webhook must update all regions)
- User profile updates (eventual consistency window ~5 seconds)
- Cost increase (3x infrastructure footprint)

---

<!-- anchor: areas-for-deeper-dive -->
### 5.2. Areas for Deeper Dive

<!-- anchor: deepdive-security-audit -->
#### 1. Security Architecture & Compliance

**Scope:** Detailed threat modeling, OWASP Top 10 mitigation strategies, SOC2 Type II audit preparation.

**Tasks:**
- Formalize authentication flows (sequence diagrams for OAuth2, SSO, refresh token rotation)
- Define encryption key management strategy (AWS KMS for database encryption keys, key rotation policy)
- Document RBAC permission matrix (roles × resources × operations)
- Conduct penetration testing (OWASP ZAP, manual testing by security firm)
- Implement security headers (CSP, X-Frame-Options, HSTS) and validate with SecurityHeaders.com
- GDPR compliance audit (data flow mapping, consent management, data deletion verification)

**Deliverable:** Security Architecture Document, Penetration Test Report, SOC2 Audit Readiness Checklist

---

<!-- anchor: deepdive-performance-testing -->
#### 2. Performance Testing & Capacity Planning

**Scope:** Load testing to validate NFRs, establish baseline metrics, identify bottlenecks before production launch.

**Tasks:**
- **Load Test Scenarios:**
  - 500 concurrent rooms with 10 participants each (5,000 WebSocket connections)
  - Vote casting storm: All participants vote within 10-second window
  - Reveal spike: 100 simultaneous reveals triggering database aggregations
  - Subscription checkout burst: 50 concurrent Stripe checkout sessions
- **Tooling:** k6 for WebSocket load generation, JMeter for REST API load
- **Metrics Collection:** p50/p95/p99 latencies, error rates, database connection pool saturation, Redis memory usage
- **Bottleneck Identification:** Profile slow queries (pg_stat_statements), analyze Redis slow log, JVM heap dumps for memory leaks
- **Capacity Planning:** Determine pod count needed for target load, database instance size, Redis cluster shard count

**Deliverable:** Load Test Report, Capacity Planning Spreadsheet, Performance Tuning Recommendations

---

<!-- anchor: deepdive-disaster-recovery -->
#### 3. Disaster Recovery & Business Continuity

**Scope:** Formalize backup/restore procedures, define RTO/RPO targets, test failover mechanisms.

**Tasks:**
- **Backup Strategy:**
  - PostgreSQL automated daily snapshots (30-day retention), WAL archival to S3 (7-day point-in-time recovery)
  - Redis AOF snapshots hourly to S3, test restore to new cluster
  - S3 bucket versioning for report exports, cross-region replication for critical data
- **Failover Testing:**
  - Simulate RDS primary failure, validate automatic failover to standby (<60 seconds downtime)
  - Test Kubernetes node failure, pod rescheduling to healthy nodes
  - Chaos engineering (Chaos Monkey): Randomly terminate pods, verify graceful degradation
- **Runbooks:**
  - Database restore procedure (step-by-step commands, estimated recovery time)
  - Complete region outage response (promote read replica to primary, update DNS)
  - Incident communication templates (status page updates, customer emails)

**Deliverable:** Disaster Recovery Plan (DRP), Runbook Repository, Quarterly DR Drill Schedule

---

<!-- anchor: deepdive-api-contracts -->
#### 4. API Contract Design & Versioning

**Scope:** Define REST API and WebSocket protocol specifications, versioning strategy, backward compatibility guarantees.

**Tasks:**
- **OpenAPI 3.1 Specification:**
  - Document all REST endpoints (request/response schemas, error codes, authentication requirements)
  - Generate TypeScript client SDK via openapi-generator
  - Publish interactive API documentation (Swagger UI or Redoc)
- **WebSocket Protocol Specification:**
  - Define message types (vote.cast.v1, room.reveal.v1), JSON schemas for payloads
  - Versioning strategy: Clients send supported version in handshake, server routes to appropriate handler
  - Error code enumeration (4000-4999 custom codes for domain errors)
- **Backward Compatibility Policy:**
  - Additive changes allowed within minor version (new optional fields)
  - Breaking changes require major version bump, old version supported for 6 months
- **Contract Testing:** Pact or Spring Cloud Contract for consumer-driven contract tests

**Deliverable:** API Specification Repository, Client SDK npm package, Versioning Policy Document

---

<!-- anchor: deepdive-observability -->
#### 5. Observability Deep Dive (Distributed Tracing)

**Scope:** Implement end-to-end distributed tracing for complex WebSocket flows, integrate with logging and metrics for unified observability.

**Tasks:**
- **OpenTelemetry Integration:**
  - Instrument Quarkus application with auto-instrumentation agent
  - Propagate trace context via HTTP headers (traceparent) and WebSocket metadata
  - Custom spans for business operations (castVote, revealRound, calculateConsensus)
- **Trace Backend:** Deploy Jaeger or Grafana Tempo for trace storage and visualization
- **Correlation:**
  - Link traces to logs via traceId in structured JSON logs
  - Link traces to metrics via exemplars (Prometheus exemplars showing trace IDs for high-latency requests)
- **Dashboards:** Grafana dashboards showing:
  - Request flow waterfall (browser → load balancer → API → database → Redis → WebSocket broadcast)
  - Service dependency graph (automatic from trace data)
  - Error rate by trace span (identify which component failing)

**Deliverable:** Tracing Architecture Document, Grafana Tracing Dashboard, Runbook for Trace Analysis

---

<!-- anchor: deepdive-cicd-pipeline -->
#### 6. CI/CD Pipeline Hardening

**Scope:** Enhance CI/CD pipeline with advanced testing, security scanning, and deployment strategies.

**Tasks:**
- **Test Pyramid:**
  - Unit tests: 80% code coverage target, fast feedback (<2 minutes)
  - Integration tests: Testcontainers for PostgreSQL/Redis, API contract tests
  - E2E tests: Playwright for critical user journeys (login, create room, cast vote, reveal), run on every PR
- **Security Scanning:**
  - SAST: SonarQube quality gate (no critical bugs, <3% code duplication)
  - Dependency scanning: Snyk or Dependabot for vulnerable dependencies, fail build on HIGH/CRITICAL
  - Container scanning: Trivy scan Docker images, fail if base image outdated >90 days
  - Secrets scanning: GitGuardian or Gitleaks prevent accidental credential commits
- **Deployment Strategies:**
  - Canary deployments: 10% traffic to new version for 15 minutes, rollback if error rate >2x baseline
  - Feature flags: LaunchDarkly or Unleash for gradual feature rollouts, instant kill switch
- **Rollback Automation:** Automatic rollback if health checks fail post-deployment (3 consecutive failures within 5 minutes)

**Deliverable:** CI/CD Pipeline Documentation, Test Strategy Document, Deployment Playbook

---

<!-- anchor: glossary -->
## 6. Glossary

| Term | Definition |
|------|------------|
| **ACID** | Atomicity, Consistency, Isolation, Durability - database transaction properties ensuring data integrity |
| **ALB** | Application Load Balancer - AWS Layer 7 load balancer with HTTP/HTTPS routing and sticky sessions |
| **AOF** | Append-Only File - Redis persistence mechanism logging every write operation for crash recovery |
| **C4 Model** | Context, Containers, Components, Code - hierarchical software architecture diagram framework |
| **CDN** | Content Delivery Network - geographically distributed servers caching static assets near users |
| **CQRS** | Command Query Responsibility Segregation - separating read and write operations for different optimization strategies |
| **CSP** | Content Security Policy - HTTP header restricting resource loading to prevent XSS attacks |
| **EKS** | Elastic Kubernetes Service - AWS managed Kubernetes control plane |
| **ERD** | Entity Relationship Diagram - visual representation of database schema and table relationships |
| **HPA** | Horizontal Pod Autoscaler - Kubernetes resource automatically scaling pod replicas based on metrics |
| **HSTS** | HTTP Strict Transport Security - header forcing browsers to use HTTPS for all requests |
| **JIT Provisioning** | Just-In-Time Provisioning - automatic user account creation upon first SSO login |
| **JWT** | JSON Web Token - compact, URL-safe token format for authentication and information exchange |
| **MRR** | Monthly Recurring Revenue - predictable revenue from active subscriptions |
| **NFR** | Non-Functional Requirement - system quality attributes like performance, security, scalability |
| **OIDC** | OpenID Connect - identity layer on top of OAuth2 for authentication and user profile retrieval |
| **Panache** | Quarkus extension simplifying Hibernate ORM/Reactive with active record or repository patterns |
| **PKCE** | Proof Key for Code Exchange - OAuth2 extension securing authorization code flow in public clients (SPAs, mobile apps) |
| **Pub/Sub** | Publish/Subscribe - messaging pattern where publishers send messages to channels, subscribers receive from channels |
| **RBAC** | Role-Based Access Control - authorization model granting permissions based on user roles |
| **RPO** | Recovery Point Objective - maximum acceptable data loss measured in time (e.g., 5 minutes) |
| **RTO** | Recovery Time Objective - maximum acceptable downtime for service restoration (e.g., 1 hour) |
| **SAML2** | Security Assertion Markup Language 2.0 - XML-based protocol for SSO authentication |
| **SAST** | Static Application Security Testing - analyzing source code for vulnerabilities without execution |
| **SOC2** | Service Organization Control 2 - audit framework for data security, availability, and confidentiality controls |
| **SPA** | Single-Page Application - web app loading single HTML page, dynamically updating via JavaScript |
| **SSO** | Single Sign-On - authentication scheme allowing users to log in once for multiple applications |
| **TLS** | Transport Layer Security - cryptographic protocol encrypting data in transit (successor to SSL) |
| **WAL** | Write-Ahead Logging - database technique logging changes before applying, enabling crash recovery and replication |
| **WebSocket** | Protocol providing full-duplex communication over single TCP connection, enabling real-time bidirectional data flow |
| **WSS** | WebSocket Secure - WebSocket protocol over TLS/SSL encryption (wss:// scheme) |
