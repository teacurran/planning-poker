# System Architecture Blueprint: Scrum Poker Platform

---

<!-- anchor: cross-cutting-concerns -->
### 3.8. Cross-Cutting Concerns

<!-- anchor: authentication-and-authorization -->
#### Authentication & Authorization

<!-- anchor: authentication-mechanisms -->
##### Authentication Mechanisms

**OAuth2 Social Login (Free/Pro Tiers):**
- **Providers:** Google OAuth2, Microsoft Identity Platform
- **Flow:** Authorization Code Flow with PKCE (Proof Key for Code Exchange) for browser-based clients
- **Implementation:** Quarkus OIDC extension handling token exchange and validation
- **Token Storage:** JWT access tokens (1-hour expiration) in browser `localStorage`, refresh tokens (30-day expiration) in `httpOnly` secure cookies
- **User Provisioning:** Automatic user creation on first login with `oauth_provider` and `oauth_subject` as unique identifiers
- **Profile Sync:** Email, display name, and avatar URL synced from OAuth provider on each login

**Enterprise SSO (Enterprise Tier):**
- **Protocols:** OIDC (OpenID Connect) and SAML2 support via Quarkus Security extensions
- **Configuration:** Per-organization SSO settings stored in `Organization.sso_config` JSONB field (IdP endpoint, certificate, attribute mapping)
- **Domain Enforcement:** Email domain verification ensures users with `@company.com` email automatically join organization workspace
- **Just-In-Time (JIT) Provisioning:** User accounts created on first SSO login with organization membership pre-assigned
- **Session Management:** SSO sessions synchronized with IdP via backchannel logout or session validation

**Anonymous Play:**
- **Identifier:** Client-generated UUID stored in browser `sessionStorage` for session continuity
- **Room Association:** Anonymous participants linked to room via `RoomParticipant.anonymous_id`
- **Feature Restrictions:** No session history access, no saved preferences, no administrative capabilities
- **Data Lifecycle:** Anonymous session data purged 24 hours after room inactivity

<!-- anchor: authorization-strategy -->
##### Authorization Strategy

**Role-Based Access Control (RBAC):**
- **Roles:** `ANONYMOUS`, `USER`, `PRO_USER`, `ORG_ADMIN`, `ORG_MEMBER`
- **Implementation:** Quarkus Security annotations (`@RolesAllowed`) on REST endpoints and service methods
- **JWT Claims:** Access token includes `roles` array for authorization decisions
- **Dynamic Role Mapping:** Subscription tier (`FREE`, `PRO`, `PRO_PLUS`, `ENTERPRISE`) mapped to roles during token generation

**Resource-Level Permissions:**
- **Room Access:**
  - `PUBLIC` rooms: Accessible to anyone with room ID
  - `INVITE_ONLY` rooms: Requires room owner to whitelist participant (Pro+ tier)
  - `ORG_RESTRICTED` rooms: Requires organization membership (Enterprise tier)
- **Room Operations:**
  - Host controls (reveal, reset, kick): Room creator or user with `HOST` role in `RoomParticipant`
  - Configuration updates: Room owner only
  - Vote casting: Participants with `VOTER` role (excludes `OBSERVER`)
- **Report Access:**
  - Free tier: Session summary only (no round-level detail)
  - Pro tier: Full session history with round breakdown
  - Enterprise tier: Organization-wide analytics with member filtering

**Enforcement Points:**
1. **API Gateway/Ingress:** JWT validation and signature verification
2. **REST Controllers:** Role-based annotations reject unauthorized requests with `403 Forbidden`
3. **WebSocket Handshake:** Token validation before connection upgrade
4. **Service Layer:** Domain-level checks (e.g., room privacy mode enforcement, subscription feature gating)

---

<!-- anchor: logging-and-monitoring -->
#### Logging & Monitoring

<!-- anchor: logging-strategy -->
##### Logging Strategy

**Structured Logging (JSON Format):**
- **Library:** SLF4J with Quarkus Logging JSON formatter
- **Schema:** Each log entry includes:
  - `timestamp` - ISO8601 format
  - `level` - DEBUG, INFO, WARN, ERROR
  - `logger` - Java class name
  - `message` - Human-readable description
  - `correlationId` - Unique request/WebSocket session identifier for distributed tracing
  - `userId` - Authenticated user ID (omitted for anonymous)
  - `roomId` - Estimation room context
  - `action` - Semantic action (e.g., `vote.cast`, `room.created`, `subscription.upgraded`)
  - `duration` - Operation latency in milliseconds (for timed operations)
  - `error` - Exception stack trace (for ERROR level)

**Log Levels by Environment:**
- **Development:** DEBUG (verbose SQL queries, WebSocket message payloads)
- **Staging:** INFO (API requests, service method calls, integration events)
- **Production:** WARN (error conditions, performance degradation, security events)

**Log Aggregation:**
- **Stack:** Loki (log aggregation) + Promtail (log shipper) + Grafana (visualization)
- **Alternative:** AWS CloudWatch Logs or GCP Cloud Logging for managed service
- **Retention:** 30 days for application logs, 90 days for audit logs (compliance requirement)
- **Query Optimization:** Indexed fields: `correlationId`, `userId`, `roomId`, `action`, `level`

**Audit Logging:**
- **Scope:** Enterprise tier security and compliance events
- **Storage:** Dedicated `AuditLog` table (partitioned by month) + immutable S3 bucket for archival
- **Events:**
  - User authentication (SSO login, logout)
  - Organization configuration changes (SSO settings, branding)
  - Member management (invite, role change, removal)
  - Administrative actions (room deletion, user account suspension)
- **Attributes:** `timestamp`, `orgId`, `userId`, `action`, `resourceType`, `resourceId`, `ipAddress`, `userAgent`, `changeDetails` (JSONB)

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

**Distributed Tracing (Optional for MVP+):**
- **Framework:** OpenTelemetry with Jaeger backend
- **Traces:** End-to-end spans from browser HTTP request → API → database → Redis Pub/Sub → WebSocket broadcast
- **Correlation:** `traceId` propagated via HTTP headers (`traceparent`) and WebSocket message metadata
- **Sampling:** 10% sampling in production, 100% for error traces

---

<!-- anchor: security-considerations -->
#### Security Considerations

<!-- anchor: transport-security -->
##### Transport Security

- **HTTPS/TLS 1.3:** All REST API and WebSocket traffic encrypted in transit
- **Certificate Management:** AWS Certificate Manager (ACM) or Let's Encrypt with automated renewal
- **HSTS (HTTP Strict Transport Security):** `Strict-Transport-Security: max-age=31536000; includeSubDomains` header enforced
- **WebSocket Secure (WSS):** TLS-encrypted WebSocket connections (`wss://` protocol)

<!-- anchor: application-security -->
##### Application Security

**Input Validation:**
- **REST APIs:** Bean Validation (JSR-380) annotations on DTOs, automatic validation in Quarkus REST layer
- **WebSocket Messages:** Zod schema validation on client, server-side JSON schema validation before deserialization
- **SQL Injection Prevention:** Parameterized queries via Hibernate Reactive, no dynamic SQL concatenation
- **XSS Prevention:** React automatic escaping for user-generated content, CSP (Content Security Policy) headers

**Authentication Security:**
- **JWT Signature:** RS256 (RSA with SHA-256) algorithm, private key stored in Kubernetes Secret
- **Token Expiration:** Short-lived access tokens (1 hour), refresh tokens rotated on use
- **OAuth2 State Parameter:** CSRF protection for OAuth flow, state validated on callback
- **PKCE:** Protects authorization code from interception in browser-based flows

**Authorization Security:**
- **Least Privilege:** Default deny policy, explicit role grants required for resource access
- **Resource Ownership Validation:** Service layer verifies user owns/has permission for requested resource (e.g., room, report)
- **Rate Limiting:** Redis-backed token bucket algorithm:
  - Anonymous users: 10 req/min per IP
  - Authenticated users: 100 req/min per user
  - WebSocket messages: 50 msg/min per connection

**Data Protection:**
- **Encryption at Rest:** PostgreSQL Transparent Data Encryption (TDE) for sensitive columns (email, payment metadata)
- **PII Handling:** User emails hashed in logs, full values only in database and audit logs
- **Secrets Management:** Kubernetes Secrets for database credentials, OAuth client secrets, JWT signing keys
- **Payment Security:** Stripe tokenization for card details, no PCI-sensitive data stored in application database

<!-- anchor: vulnerability-management -->
##### Vulnerability Management

- **Dependency Scanning:** Snyk or Dependabot automated PR checks for known vulnerabilities in Maven dependencies and npm packages
- **Container Scanning:** Trivy or AWS ECR scanning for base image vulnerabilities
- **SAST (Static Analysis):** SonarQube code quality and security analysis in CI pipeline
- **DAST (Dynamic Analysis):** OWASP ZAP scheduled scans against staging environment
- **Penetration Testing:** Annual third-party security assessment for Enterprise tier compliance

**GDPR & Privacy Compliance:**
- **Data Minimization:** Anonymous users tracked by session UUID, no personal data collected without consent
- **Right to Erasure:** `/api/v1/users/{userId}/delete` endpoint implements account deletion with data anonymization (preserves aggregate statistics)
- **Data Portability:** Export user data (profile, session history) via `/api/v1/users/{userId}/export` (JSON format)
- **Cookie Consent:** GDPR cookie banner for analytics cookies, essential cookies (authentication) exempted
- **Privacy Policy:** Hosted on marketing website, version tracked in `UserConsent` table

---

<!-- anchor: scalability-and-performance -->
#### Scalability & Performance

<!-- anchor: horizontal-scaling -->
##### Horizontal Scaling

**Stateless Application Design:**
- **Session State:** Stored in Redis, not in JVM memory, enabling any node to serve any request
- **WebSocket Affinity:** Load balancer sticky sessions based on `room_id` hash for optimal Redis Pub/Sub efficiency, but not required for correctness
- **Database Connection Pooling:** HikariCP with max pool size = (core_count * 2) + effective_spindle_count, distributed across replicas

**Auto-Scaling Configuration (Kubernetes HPA):**
- **Metric:** Average CPU utilization target: 70%
- **Custom Metric:** `scrumpoker_websocket_connections_total / pod_count` target: 1000 connections/pod
- **Min Replicas:** 2 (high availability)
- **Max Replicas:** 10 (cost constraint, sufficient for 10,000 concurrent connections)
- **Scale-Up:** Add pod when metric exceeds target for 2 minutes
- **Scale-Down:** Remove pod when metric below 50% of target for 10 minutes (conservative to avoid thrashing)

**Database Scaling:**
- **Read Replicas:** 1-2 read replicas for reporting queries (`GET /api/v1/reports/*` routes)
- **Connection Pooling:** Separate pools for transactional writes (master) and analytical reads (replicas)
- **Query Optimization:** Indexed columns (see ERD section), materialized views for complex aggregations
- **Partitioning:** `SessionHistory` and `AuditLog` tables partitioned by month, automated partition creation

**Redis Scaling:**
- **Cluster Mode:** 3-node Redis cluster for horizontal scalability and high availability
- **Pub/Sub Sharding:** Channels sharded by `room_id` hash for distributed subscription load
- **Eviction Policy:** `allkeys-lru` for session cache, `noeviction` for critical room state (manual TTL management)

<!-- anchor: performance-optimization -->
##### Performance Optimization

**Caching Strategy:**
- **L1 Cache (Browser):** React Query caches API responses (5-minute stale time for user profiles, room configs)
- **L2 Cache (Redis):**
  - User profile: 15-minute TTL
  - Room configuration: 5-minute TTL, invalidated on update
  - Session history summaries: 1-hour TTL
- **CDN Caching:** Static assets (SPA bundle, images) cached with 1-year max-age, cache-busted via webpack content hash

**Database Optimization:**
- **Reactive Queries:** Non-blocking database I/O via Mutiny reactive streams, prevents thread pool exhaustion
- **Batch Operations:** Vote insertion uses JDBC batch inserts for multiple votes in single round
- **Pagination:** Cursor-based pagination for session history (`WHERE created_at < ? ORDER BY created_at DESC LIMIT 50`)
- **Index-Only Scans:** Covering indexes for frequently queried columns (e.g., `Room(privacy_mode, last_active_at) INCLUDE (title, owner_id)`)

**WebSocket Optimization:**
- **Binary Serialization (Future):** Consider MessagePack or Protocol Buffers for reduced payload size (currently JSON for debuggability)
- **Message Batching:** Client buffers rapid UI events (e.g., chat typing indicators) and sends batched updates every 200ms
- **Backpressure Handling:** Server drops low-priority events (presence updates) if client connection buffer full, preserves critical events (votes, reveals)

**Frontend Optimization:**
- **Code Splitting:** React lazy loading for admin dashboard, reporting pages (reduce initial bundle size)
- **Tree Shaking:** Webpack eliminates unused Tailwind CSS classes and library code
- **Image Optimization:** WebP format for avatars/logos, responsive images via `srcset`, lazy loading below fold
- **Virtual Scrolling:** React Virtualized for long session history lists (1000+ items)

---

<!-- anchor: reliability-and-availability -->
#### Reliability & Availability

<!-- anchor: fault-tolerance -->
##### Fault Tolerance

**Graceful Degradation:**
- **Analytics Unavailable:** If reporting service fails, core gameplay (WebSocket voting) continues unaffected, reports return cached summaries
- **Email Service Down:** Notification emails queued in Redis Stream, retried with exponential backoff (max 24 hours), admin alerted if queue depth exceeds threshold
- **OAuth Provider Outage:** Cached user sessions remain valid until token expiration, new logins return informative error with retry guidance

**Circuit Breaker Pattern:**
- **External Services:** Stripe API, email service protected by Resilience4j circuit breaker
- **Thresholds:** Open circuit after 50% failure rate over 10 requests, half-open after 30 seconds
- **Fallback:** Return cached subscription status (Stripe), queue email for retry (email service)

**Health Checks:**
- **Readiness Probe:** `GET /q/health/ready` checks database connectivity, Redis availability, essential service health
- **Liveness Probe:** `GET /q/health/live` confirms JVM running and responsive (no external dependency checks)
- **Probe Configuration:** Initial delay: 30s, period: 10s, timeout: 5s, failure threshold: 3

<!-- anchor: high-availability -->
##### High Availability

**Target Uptime:** 99.5% monthly (3.6 hours downtime allowance)

**Redundancy:**
- **Application Nodes:** Minimum 2 replicas across different availability zones
- **Database:** PostgreSQL managed service (RDS/Cloud SQL) with multi-AZ automatic failover
- **Redis:** Cluster mode with replication (1 primary + 1 replica per shard)
- **Load Balancer:** Cloud-managed ALB/Load Balancer with health checks, automatic unhealthy target removal

**Disaster Recovery:**
- **Database Backups:** Automated daily snapshots, 30-day retention, cross-region replication for critical data
- **Point-in-Time Recovery:** PostgreSQL WAL (Write-Ahead Logging) enables recovery to any second within 7-day window
- **Backup Testing:** Monthly restore drill to staging environment, verified by automated tests
- **RTO (Recovery Time Objective):** 1 hour for full service restoration
- **RPO (Recovery Point Objective):** 5 minutes maximum data loss (based on WAL archival frequency)

**Incident Response:**
1. **Detection:** Prometheus alerting triggers PagerDuty escalation for critical failures
2. **Triage:** On-call engineer assesses impact (full outage vs. degraded performance)
3. **Mitigation:** Rollback deployment (Kubernetes rollout undo) or scale up resources
4. **Communication:** Status page updates for user-facing incidents (>5% users affected)
5. **Post-Mortem:** Blameless retrospective within 72 hours, action items tracked

---

<!-- anchor: deployment-view -->
### 3.9. Deployment View

<!-- anchor: target-environment -->
#### Target Environment

**Cloud Platform:** AWS (Primary) with GCP as multi-cloud option

**Rationale:**
- Managed Kubernetes (EKS) reduces operational overhead vs. self-managed clusters
- Mature managed services ecosystem (RDS, ElastiCache, S3) for PostgreSQL, Redis, object storage
- Global edge network (CloudFront) for CDN and DDoS protection
- Enterprise-grade compliance certifications (SOC2, PCI-DSS) for payment processing

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

**CI/CD Pipeline (GitHub Actions):**

**Stages:**
1. **Build & Test:**
   - Checkout code, setup Java 17
   - Maven build with unit tests
   - Integration tests using Testcontainers (PostgreSQL, Redis)
   - SonarQube code quality gate
2. **Container Build:**
   - Docker build multi-stage image
   - Tag with Git SHA and semantic version
   - Push to ECR
3. **Security Scan:**
   - Trivy vulnerability scan on Docker image
   - Fail pipeline if HIGH/CRITICAL vulnerabilities found
4. **Deploy to Staging:**
   - Helm upgrade staging environment with new image tag
   - Run smoke tests (health checks, critical API endpoints)
   - Playwright E2E tests for WebSocket flows
5. **Manual Approval Gate:**
   - Product owner review and approval for production deploy
6. **Deploy to Production:**
   - Helm upgrade production with blue/green strategy
   - Gradual traffic shift (10% → 50% → 100% over 30 minutes)
   - Automated rollback if error rate exceeds baseline by 2x

**Deployment Environments:**

| Environment | Purpose | Infrastructure | Data |
|-------------|---------|----------------|------|
| **Development** | Local developer machines | Docker Compose, local PostgreSQL/Redis | Synthetic test data |
| **Staging** | Pre-production testing | EKS cluster (2 nodes, t3.medium), RDS (db.t3.small), ElastiCache (cache.t3.micro) | Anonymized production data subset |
| **Production** | Live user traffic | EKS cluster (3+ nodes, t3.large), RDS (db.r6g.large with read replica), ElastiCache Redis Cluster (cache.r6g.large, 3 shards) | Real user data |

<!-- anchor: deployment-diagram -->
#### Deployment Diagram (AWS Architecture)

~~~plantuml
@startuml
!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Deployment.puml

LAYOUT_WITH_LEGEND()

title Deployment Diagram - AWS Production Environment

deploymentNode("AWS Cloud", "us-east-1", "AWS Region") {

  deploymentNode("CloudFront CDN", "Global Edge Network", "AWS CloudFront") {
    containerInstance(cdn_edge, "Edge Location", "Serves static SPA assets, SSL termination") #Container=web_app
  }

  deploymentNode("Route 53", "DNS", "AWS Route 53") {
    node "DNS Zone" as dns {
    }
  }

  deploymentNode("VPC", "Production VPC (10.0.0.0/16)", "AWS VPC") {

    deploymentNode("Public Subnet A", "us-east-1a (10.0.1.0/24)", "AWS Subnet") {
      node "ALB A" <<AWS Application Load Balancer>> {
        containerInstance(alb_a, "Load Balancer AZ-A", "HTTPS/WSS listener, sticky sessions") #Container=ingress
      }
    }

    deploymentNode("Public Subnet B", "us-east-1b (10.0.2.0/24)", "AWS Subnet") {
      node "ALB B" <<AWS Application Load Balancer>> {
        containerInstance(alb_b, "Load Balancer AZ-B", "HTTPS/WSS listener, sticky sessions") #Container=ingress
      }
    }

    deploymentNode("EKS Cluster", "Kubernetes Control Plane", "AWS EKS") {

      deploymentNode("Node Group", "t3.large instances (3 nodes across AZs)", "EC2 Auto Scaling Group") {

        deploymentNode("Worker Node 1 (AZ-A)", "t3.large (2 vCPU, 8GB RAM)", "EC2 Instance") {
          containerInstance(api_pod_1, "Quarkus API Pod 1", "Docker container, 1GB memory limit") #Container=api_app
        }

        deploymentNode("Worker Node 2 (AZ-B)", "t3.large", "EC2 Instance") {
          containerInstance(api_pod_2, "Quarkus API Pod 2", "Docker container") #Container=api_app
          containerInstance(worker_pod_1, "Background Worker Pod", "Docker container") #Container=background_worker
        }

        deploymentNode("Worker Node 3 (AZ-C)", "t3.large", "EC2 Instance") {
          containerInstance(api_pod_3, "Quarkus API Pod 3", "Docker container") #Container=api_app
        }

      }
    }

    deploymentNode("Private Subnet A", "us-east-1a (10.0.11.0/24)", "AWS Subnet") {

      node "RDS Primary" <<AWS RDS>> {
        databaseInstance(rds_primary, "PostgreSQL 15 Primary", "db.r6g.large (multi-AZ)") #Container=postgres
      }

      node "ElastiCache Shard 1 Primary" <<AWS ElastiCache>> {
        containerInstance(redis_1, "Redis Cluster Node 1", "cache.r6g.large") #Container=redis
      }

    }

    deploymentNode("Private Subnet B", "us-east-1b (10.0.12.0/24)", "AWS Subnet") {

      node "RDS Replica" <<AWS RDS>> {
        databaseInstance(rds_replica, "PostgreSQL Read Replica", "db.r6g.large") #Container=postgres
      }

      node "ElastiCache Shard 2 Primary" <<AWS ElastiCache>> {
        containerInstance(redis_2, "Redis Cluster Node 2", "cache.r6g.large") #Container=redis
      }

    }

    deploymentNode("Private Subnet C", "us-east-1c (10.0.13.0/24)", "AWS Subnet") {

      node "ElastiCache Shard 3 Primary" <<AWS ElastiCache>> {
        containerInstance(redis_3, "Redis Cluster Node 3", "cache.r6g.large") #Container=redis
      }

    }

  }

  deploymentNode("S3", "Object Storage", "AWS S3") {
    node "S3 Buckets" {
      containerInstance(s3_assets, "Static Assets Bucket", "SPA bundle, images (CloudFront origin)") #Container=object_storage
      containerInstance(s3_reports, "Reports Bucket", "Generated CSV/PDF exports (private)") #Container=object_storage
    }
  }

  deploymentNode("Secrets Manager", "Secrets Storage", "AWS Secrets Manager") {
    node "Secrets" {
      artifact "DB Credentials"
      artifact "OAuth Secrets"
      artifact "JWT Signing Key"
      artifact "Stripe API Key"
    }
  }

  deploymentNode("CloudWatch", "Observability", "AWS CloudWatch") {
    node "Logs" {
      artifact "Application Logs"
      artifact "Audit Logs"
    }
  }

  deploymentNode("Prometheus/Grafana", "Metrics & Dashboards", "EKS Pods") {
    containerInstance(prometheus, "Prometheus Server", "Metrics scraping and storage")
    containerInstance(grafana, "Grafana", "Dashboards and alerting")
  }

}

deploymentNode("External Services", "Third-Party SaaS", "Internet") {
  node "Google OAuth2" as google_ext
  node "Microsoft OAuth2" as microsoft_ext
  node "Stripe API" as stripe_ext
  node "SendGrid/SES" as email_ext
}

' Relationships
Rel(dns, cdn_edge, "Resolves app.scrumpoker.com to")
Rel(cdn_edge, alb_a, "Origin requests to", "HTTPS")
Rel(cdn_edge, alb_b, "Origin requests to", "HTTPS")

Rel(alb_a, api_pod_1, "Routes to", "HTTP/WS")
Rel(alb_b, api_pod_2, "Routes to", "HTTP/WS")
Rel(alb_b, api_pod_3, "Routes to", "HTTP/WS")

Rel(api_pod_1, rds_primary, "Writes to", "PostgreSQL wire protocol (5432)")
Rel(api_pod_2, rds_primary, "Writes to")
Rel(api_pod_3, rds_replica, "Reads from", "PostgreSQL (read-only)")

Rel(api_pod_1, redis_1, "Pub/Sub, caching", "Redis protocol (6379)")
Rel(api_pod_2, redis_2, "Pub/Sub, caching")
Rel(api_pod_3, redis_3, "Pub/Sub, caching")

Rel(worker_pod_1, rds_replica, "Reads session data")
Rel(worker_pod_1, redis_1, "Consumes Redis Streams")
Rel(worker_pod_1, s3_reports, "Uploads reports", "S3 API")

Rel(api_pod_1, s3_assets, "Serves fallback assets (rare)")
Rel(prometheus, api_pod_1, "Scrapes /q/metrics", "HTTP")
Rel(prometheus, api_pod_2, "Scrapes metrics")
Rel(grafana, prometheus, "Queries metrics")

Rel(api_pod_1, google_ext, "OAuth token exchange", "HTTPS")
Rel(api_pod_2, stripe_ext, "Payment API calls", "HTTPS")
Rel(worker_pod_1, email_ext, "Sends emails", "SMTP/API")

@enduml
~~~

**Deployment Notes:**

1. **High Availability:** Application pods distributed across 3 availability zones (AZs), database and Redis configured for multi-AZ failover
2. **Network Security:** Private subnets for database and cache layers, only application load balancer exposed to internet
3. **Secrets Management:** Kubernetes Secrets mounted as environment variables, sourced from AWS Secrets Manager via External Secrets Operator
4. **Monitoring Integration:** Prometheus deployed as in-cluster pods, scrapes application metrics via Kubernetes service discovery
5. **Backup Strategy:** RDS automated daily snapshots to S3, Redis AOF persistence with hourly snapshots
6. **Cost Optimization:** Reserved Instances for baseline capacity (2 t3.large nodes), Spot Instances for auto-scaled burst capacity
