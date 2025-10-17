# Project Plan: Scrum Poker Platform - Iteration 8

**Version:** 1.0
**Date:** 2025-10-17

---

<!-- anchor: iteration-8 -->
### Iteration 8: Deployment & Production Readiness

*   **Iteration ID:** `I8`

*   **Goal:** Prepare application for production deployment including Kubernetes manifests, monitoring setup, performance optimization, security hardening, documentation, and final end-to-end testing.

*   **Prerequisites:** All previous iterations (I1-I7) completed

*   **Tasks:**

<!-- anchor: task-i8-t1 -->
*   **Task 8.1: Create Kubernetes Deployment Manifests**
    *   **Task ID:** `I8.T1`
    *   **Description:** Create comprehensive Kubernetes manifests for production deployment. Resources: Deployment (backend application with replicas, resource limits, liveness/readiness probes), Service (ClusterIP for internal traffic), Ingress (ALB/Nginx with TLS termination, sticky sessions), ConfigMap (environment-specific configuration: database URL, Redis URL, feature flags), Secret (sensitive data: database password, OAuth secrets, JWT signing key, Stripe API key), HorizontalPodAutoscaler (target CPU 70%, custom metric for WebSocket connections). Use Kustomize for environment overlays (dev, staging, production). Configure rolling update strategy (maxSurge: 1, maxUnavailable: 0 for zero-downtime).
    *   **Agent Type Hint:** `SetupAgent`
    *   **Inputs:**
        *   Deployment architecture from architecture blueprint
        *   Kubernetes best practices (resource limits, health checks, secrets)
        *   Environment configuration requirements
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/05_Operational_Architecture.md` (deployment section)
        *   Directory structure specification (infra/kubernetes/)
    *   **Target Files:**
        *   `infra/kubernetes/base/deployment.yaml`
        *   `infra/kubernetes/base/service.yaml`
        *   `infra/kubernetes/base/ingress.yaml`
        *   `infra/kubernetes/base/configmap.yaml`
        *   `infra/kubernetes/base/secret.yaml` (template with placeholders)
        *   `infra/kubernetes/base/hpa.yaml`
        *   `infra/kubernetes/overlays/dev/kustomization.yaml`
        *   `infra/kubernetes/overlays/staging/kustomization.yaml`
        *   `infra/kubernetes/overlays/production/kustomization.yaml`
    *   **Deliverables:**
        *   Deployment manifest with 2 replicas, resource requests/limits (1GB mem, 500m CPU)
        *   Liveness probe: `/q/health/live`, readiness probe: `/q/health/ready`
        *   Service exposing port 8080
        *   Ingress with sticky session annotation, TLS certificate config
        *   ConfigMap with database/Redis URLs, log level
        *   Secret template for sensitive values (to be populated by CI/CD or external secrets operator)
        *   HPA scaling 2-10 pods based on CPU
        *   Kustomize overlays for each environment
    *   **Acceptance Criteria:**
        *   `kubectl apply -k infra/kubernetes/overlays/dev` deploys to dev cluster
        *   Deployment creates 2 pods initially
        *   Liveness/readiness probes configured correctly (verify pod status)
        *   Ingress routes traffic to service
        *   HPA created with correct scaling thresholds
        *   ConfigMap mounted as environment variables in pods
        *   Secrets referenced in deployment (values from external secret manager in prod)
    *   **Dependencies:** []
    *   **Parallelizable:** Yes

<!-- anchor: task-i8-t2 -->
*   **Task 8.2: Configure Prometheus Metrics & Grafana Dashboards**
    *   **Task ID:** `I8.T2`
    *   **Description:** Set up Prometheus metrics collection and create Grafana dashboards. Configure Prometheus to scrape Quarkus `/q/metrics` endpoint (ServiceMonitor for Prometheus Operator or scrape config). Create custom business metrics in Quarkus: `scrumpoker_active_sessions`, `scrumpoker_websocket_connections`, `scrumpoker_votes_cast_total`, `scrumpoker_subscriptions_active`. Create Grafana dashboards: Application Overview (requests, errors, latency, active sessions), WebSocket Metrics (connections, message rate, disconnections), Business Metrics (active subscriptions by tier, voting activity, room creation rate), Infrastructure (pod CPU/memory, database connections, Redis hit rate). Export dashboards as JSON for version control.
    *   **Agent Type Hint:** `SetupAgent`
    *   **Inputs:**
        *   Observability requirements from architecture blueprint
        *   Prometheus/Grafana patterns
        *   Business metrics list
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/05_Operational_Architecture.md` (monitoring section)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/metrics/BusinessMetrics.java` (custom metrics)
        *   `infra/monitoring/prometheus/servicemonitor.yaml`
        *   `infra/monitoring/grafana/dashboards/application-overview.json`
        *   `infra/monitoring/grafana/dashboards/websocket-metrics.json`
        *   `infra/monitoring/grafana/dashboards/business-metrics.json`
        *   `infra/monitoring/grafana/dashboards/infrastructure.json`
    *   **Deliverables:**
        *   BusinessMetrics class with Micrometer gauges/counters
        *   ServiceMonitor configuring Prometheus to scrape app pods
        *   4 Grafana dashboards (exported as JSON)
        *   Dashboard panels: request rate, error rate, p95 latency, active sessions gauge
        *   WebSocket dashboard: connection count, message throughput, reconnection rate
        *   Business dashboard: MRR, subscription tier distribution, votes per session
    *   **Acceptance Criteria:**
        *   Prometheus scrapes application metrics endpoint
        *   Custom business metrics appear in Prometheus targets
        *   Grafana application dashboard displays request rate and latency
        *   WebSocket dashboard shows connection count (test with active connections)
        *   Business metrics dashboard shows subscription counts by tier
        *   Dashboards load without errors in Grafana
    *   **Dependencies:** []
    *   **Parallelizable:** Yes (can work parallel with I8.T1)

<!-- anchor: task-i8-t3 -->
*   **Task 8.3: Implement Structured Logging with Correlation IDs**
    *   **Task ID:** `I8.T3`
    *   **Description:** Configure structured JSON logging for production. Set up Quarkus Logging JSON formatter. Add correlation ID (request ID) to all log entries: generate UUID on request entry, store in thread-local or request context, include in log MDC (Mapped Diagnostic Context). Propagate correlation ID across async operations (reactive streams, background jobs). Configure log levels: WARN in production, INFO in staging, DEBUG in dev. Implement request logging filter capturing: timestamp, method, path, status code, duration, correlation ID. Configure log aggregation destination (Loki, CloudWatch Logs).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Logging requirements from architecture blueprint
        *   Quarkus logging configuration
        *   Correlation ID pattern
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/05_Operational_Architecture.md` (logging section)
    *   **Target Files:**
        *   `backend/src/main/resources/application.properties` (logging config)
        *   `backend/src/main/java/com/scrumpoker/logging/CorrelationIdFilter.java`
        *   `backend/src/main/java/com/scrumpoker/logging/LoggingConstants.java`
    *   **Deliverables:**
        *   Quarkus logging configured for JSON output
        *   CorrelationIdFilter generating and adding correlation ID to MDC
        *   Request logging filter logging every HTTP request with correlation ID
        *   Correlation ID propagated to WebSocket messages
        *   Environment-specific log levels (WARN prod, INFO staging, DEBUG dev)
        *   Log aggregation endpoint configured (Loki or CloudWatch)
    *   **Acceptance Criteria:**
        *   Application logs in JSON format
        *   Each log entry includes `correlationId` field
        *   HTTP requests logged with method, path, status, duration, correlationId
        *   Correlation ID appears consistently across multiple log entries for same request
        *   WebSocket messages include correlationId in logs
        *   Log level adjusts per environment
    *   **Dependencies:** []
    *   **Parallelizable:** Yes

<!-- anchor: task-i8-t4 -->
*   **Task 8.4: Performance Optimization & Load Testing**
    *   **Task ID:** `I8.T4`
    *   **Description:** Conduct performance optimization and load testing to validate NFRs. Optimize database queries: add missing indexes, use query plan analysis (EXPLAIN), implement pagination efficiently (cursor-based vs. offset). Optimize Redis usage: configure connection pooling, use pipelining for batch operations. Configure Quarkus JVM settings: heap size (1GB), GC tuning (G1GC), thread pool sizing. Create k6 load test scripts: scenario 1 (500 concurrent rooms, 10 participants each, vote casting), scenario 2 (100 subscription checkouts/min), scenario 3 (WebSocket reconnection storm). Run tests, analyze results, identify bottlenecks (database, Redis, CPU), iterate optimizations. Document performance benchmarks (p95 latency, throughput, error rate under load).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Performance NFRs from architecture blueprint (500 concurrent sessions, <200ms latency)
        *   Load testing patterns (k6, JMeter)
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/01_Context_and_Drivers.md` (NFR section)
    *   **Target Files:**
        *   `scripts/load-test-voting.js` (k6 script for voting flow)
        *   `scripts/load-test-api.js` (k6 script for REST API)
        *   `docs/performance-benchmarks.md` (results documentation)
        *   `backend/src/main/resources/application.properties` (JVM tuning params)
    *   **Deliverables:**
        *   k6 load test scripts for voting and API scenarios
        *   Performance test execution and results analysis
        *   Database index additions for slow queries
        *   Redis connection pool configuration
        *   JVM tuning parameters documented
        *   Performance benchmarks document (latency, throughput, error rate)
    *   **Acceptance Criteria:**
        *   Load test achieves 500 concurrent sessions without errors
        *   p95 latency <200ms for WebSocket messages under load
        *   p95 latency <500ms for REST API endpoints
        *   Database connection pool doesn't exhaust under load
        *   Redis hit rate >90% for session cache
        *   No memory leaks (heap usage stable during sustained load)
        *   Benchmarks documented in performance report
    *   **Dependencies:** [I8.T2]
    *   **Parallelizable:** No (needs monitoring setup for metrics)

<!-- anchor: task-i8-t5 -->
*   **Task 8.5: Security Hardening & Vulnerability Scanning**
    *   **Task ID:** `I8.T5`
    *   **Description:** Implement security hardening for production. Configure security headers in HTTP responses: HSTS (max-age 31536000), Content-Security-Policy (restrict script sources), X-Frame-Options (DENY), X-Content-Type-Options (nosniff). Implement rate limiting: Redis-backed token bucket (10 req/min for anonymous, 100 req/min for authenticated users). Configure CORS (restrict allowed origins to frontend domains). Set up dependency vulnerability scanning: Snyk or Dependabot in GitHub Actions, fail build on HIGH/CRITICAL vulnerabilities. Container image scanning: Trivy scan in CI pipeline. Run OWASP ZAP dynamic scan against staging environment. Document security findings and remediation.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Security requirements from architecture blueprint
        *   OWASP Top 10 guidelines
        *   Security scanning tools (Snyk, Trivy, ZAP)
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/05_Operational_Architecture.md` (security section)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/security/SecurityHeadersFilter.java`
        *   `backend/src/main/java/com/scrumpoker/security/RateLimitingFilter.java`
        *   `.github/workflows/security-scan.yml`
        *   `docs/security-assessment.md`
    *   **Deliverables:**
        *   SecurityHeadersFilter adding security headers to all responses
        *   RateLimitingFilter using Redis for rate limit enforcement
        *   CORS configuration (allowed origins from environment variable)
        *   GitHub Actions workflow for dependency scanning (Snyk/Dependabot)
        *   GitHub Actions workflow for container scanning (Trivy)
        *   OWASP ZAP scan execution against staging
        *   Security assessment document with findings and remediations
    *   **Acceptance Criteria:**
        *   HTTP responses include HSTS, CSP, X-Frame-Options headers
        *   Rate limiting blocks requests exceeding threshold (test with curl loop)
        *   CORS blocks requests from unauthorized origins
        *   Dependency scan runs in CI, fails on HIGH/CRITICAL vulnerabilities
        *   Container scan runs in CI, reports vulnerabilities
        *   ZAP scan completes without HIGH risk findings (or findings documented)
        *   Security assessment document reviewed
    *   **Dependencies:** []
    *   **Parallelizable:** Yes (can work parallel with other tasks)

<!-- anchor: task-i8-t6 -->
*   **Task 8.6: Create Deployment & Operations Documentation**
    *   **Task ID:** `I8.T6`
    *   **Description:** Create comprehensive deployment and operations documentation. Documents: Deployment Guide (prerequisites, infrastructure setup, Kubernetes deployment steps, environment variable configuration, secret management), Operations Runbook (common tasks: scaling pods, viewing logs, restarting services, database backup/restore, disaster recovery), Monitoring Guide (Grafana dashboard usage, alert interpretation, Prometheus query examples), Troubleshooting Guide (common issues: WebSocket connection failures, database connection exhaustion, Redis out of memory, OAuth failures). Include step-by-step commands, screenshots of dashboards, decision trees for incident response.
    *   **Agent Type Hint:** `DocumentationAgent`
    *   **Inputs:**
        *   Deployment architecture from blueprint
        *   Kubernetes manifests from I8.T1
        *   Monitoring setup from I8.T2
    *   **Input Files:**
        *   `infra/kubernetes/` (manifests)
        *   `.codemachine/artifacts/architecture/05_Operational_Architecture.md`
    *   **Target Files:**
        *   `docs/deployment-guide.md`
        *   `docs/runbooks/operations-runbook.md`
        *   `docs/runbooks/monitoring-guide.md`
        *   `docs/runbooks/troubleshooting-guide.md`
        *   `docs/runbooks/disaster-recovery.md`
    *   **Deliverables:**
        *   Deployment Guide with prerequisites, step-by-step deployment, verification steps
        *   Operations Runbook with common administrative tasks
        *   Monitoring Guide explaining dashboards and alert rules
        *   Troubleshooting Guide with decision trees for common issues
        *   Disaster Recovery procedures (database restore, failover)
    *   **Acceptance Criteria:**
        *   Deployment Guide enables new team member to deploy application
        *   Operations Runbook includes commands for scaling, logs, restarts
        *   Monitoring Guide explains each Grafana dashboard panel
        *   Troubleshooting Guide covers top 5 incident scenarios
        *   Disaster Recovery doc includes RTO/RPO definitions and procedures
        *   All documents reviewed by DevOps lead
    *   **Dependencies:** [I8.T1, I8.T2]
    *   **Parallelizable:** No (needs deployment and monitoring setup)

<!-- anchor: task-i8-t7 -->
*   **Task 8.7: End-to-End Production Smoke Tests**
    *   **Task ID:** `I8.T7`
    *   **Description:** Create automated smoke test suite verifying critical user journeys in production-like environment. Test scenarios: user registration (OAuth login), room creation, joining room, voting (cast vote, reveal), subscription upgrade (Stripe checkout, webhook), organization creation (Enterprise, SSO config), report export (CSV download). Use Playwright for frontend E2E tests or REST Assured + WebSocket client for backend integration. Run tests against staging environment (production mirror). Integrate into deployment pipeline (smoke tests run post-deployment, rollback if failures).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Critical user journeys from product spec
        *   Playwright or REST Assured test patterns
    *   **Input Files:**
        *   Product specification (core flows)
    *   **Target Files:**
        *   `frontend/e2e/smoke-tests.spec.ts` (Playwright)
        *   `backend/src/test/java/com/scrumpoker/smoke/SmokeTestSuite.java` (REST Assured)
        *   `.github/workflows/deploy-production.yml` (add smoke test step)
    *   **Deliverables:**
        *   Smoke test suite covering 6+ critical journeys
        *   Tests executable against staging/production URLs (configurable)
        *   CI pipeline integration (run post-deployment)
        *   Deployment rollback on smoke test failure
        *   Test execution report (pass/fail for each journey)
    *   **Acceptance Criteria:**
        *   `npm run test:smoke` runs all smoke tests against staging
        *   OAuth login test completes successfully
        *   Voting flow test (create room, vote, reveal) passes
        *   Subscription upgrade test verifies Stripe integration
        *   Report export test downloads CSV file
        *   Tests integrated into production deployment workflow
        *   Failed smoke test triggers rollback alert
    *   **Dependencies:** [All previous iterations for complete feature set]
    *   **Parallelizable:** No (needs full system functional)

<!-- anchor: task-i8-t8 -->
*   **Task 8.8: Prepare Marketing Website & Launch Checklist**
    *   **Task ID:** `I8.T8`
    *   **Description:** Finalize marketing website and create launch readiness checklist. Marketing site: polish landing page (hero section, feature highlights, testimonials placeholder), pricing page (tier comparison, FAQ), demo page (video or interactive demo link), blog section (first post: product announcement), SEO optimization (meta tags, sitemap, robots.txt). Launch checklist: DNS configured, SSL certificates valid, Stripe production keys configured, OAuth production apps registered (Google, Microsoft), privacy policy and terms of service published, support email/contact form functional, monitoring alerts configured, on-call schedule established, backup/restore tested, performance benchmarks validated, security scan passed, documentation complete.
    *   **Agent Type Hint:** `SetupAgent`
    *   **Inputs:**
        *   Marketing website structure from directory spec
        *   Launch readiness best practices
    *   **Input Files:**
        *   Directory structure (marketing-site/)
    *   **Target Files:**
        *   `marketing-site/src/pages/index.astro` (polish landing page)
        *   `marketing-site/src/pages/pricing.astro`
        *   `marketing-site/src/pages/demo.astro`
        *   `marketing-site/public/sitemap.xml`
        *   `marketing-site/public/robots.txt`
        *   `docs/launch-checklist.md`
    *   **Deliverables:**
        *   Marketing website with polished pages (landing, pricing, demo, blog)
        *   SEO optimization (meta tags, Open Graph tags, sitemap)
        *   Privacy policy and Terms of Service pages
        *   Contact form connected to support email
        *   Launch readiness checklist with 20+ items
        *   Checklist items verified (checkboxes marked)
    *   **Acceptance Criteria:**
        *   Marketing website deploys to production domain
        *   Landing page loads in <2 seconds, mobile-responsive
        *   Pricing page displays all tiers correctly
        *   Demo page provides access to trial or video
        *   SEO meta tags present (check with View Source)
        *   Privacy policy and ToS published and linked in footer
        *   Launch checklist complete (all items checked or documented)
    *   **Dependencies:** [All previous iterations]
    *   **Parallelizable:** Yes (marketing site can work parallel with backend tasks)

---

**Iteration 8 Summary:**

*   **Deliverables:**
    *   Production-ready Kubernetes manifests with auto-scaling
    *   Prometheus metrics and Grafana dashboards
    *   Structured JSON logging with correlation IDs
    *   Performance optimization and load test benchmarks
    *   Security hardening (headers, rate limiting, vulnerability scans)
    *   Deployment and operations documentation
    *   End-to-end smoke tests in CI/CD pipeline
    *   Finalized marketing website and launch checklist

*   **Acceptance Criteria (Iteration-Level):**
    *   Application deploys to Kubernetes successfully
    *   Monitoring dashboards display real-time metrics
    *   Performance tests meet NFR targets (500 sessions, <200ms latency)
    *   Security scans pass without critical vulnerabilities
    *   Documentation enables operations team to manage production
    *   Smoke tests verify critical user journeys
    *   Marketing website live and SEO-optimized
    *   Launch checklist complete and reviewed

*   **Estimated Duration:** 3 weeks

---

**Post-Launch Maintenance Plan:**

After successful launch, establish ongoing maintenance processes:

*   **Weekly:** Review monitoring dashboards, check error rates, investigate anomalies
*   **Bi-Weekly:** Dependency updates, security patch review
*   **Monthly:** Performance review, capacity planning, cost optimization
*   **Quarterly:** Feature roadmap review, user feedback analysis, architectural evolution planning
*   **On-Demand:** Incident response (on-call rotation), customer support escalations, urgent security patches
