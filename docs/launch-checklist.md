# Planning Poker - Production Launch Readiness Checklist

**Version:** 1.0
**Last Updated:** 2026-01-18
**Owner:** DevOps & Platform Team
**Status:** Pre-Launch Review

---

## Overview

This checklist ensures all critical systems, configurations, and processes are production-ready before launching Planning Poker to the public. Each item must be verified, documented, and signed off by the responsible team member.

**Launch Gate Criteria:**
- All items marked **[CRITICAL]** must be complete
- At least 90% of all items must be complete or have documented mitigation plans
- All verification tests must pass

---

## 1. Infrastructure & Configuration

### DNS & Networking

- [ ] **[CRITICAL]** DNS A/AAAA records configured for production domain
  - **Domain:** `planningpoker.example.com`
  - **Verification:** `nslookup planningpoker.example.com`
  - **Assigned:** DevOps Team
  - **Status:** Pending
  - **Notes:** Point to production load balancer IP

- [ ] **[CRITICAL]** DNS CNAME records configured for subdomains
  - **Subdomains:** `app.planningpoker.example.com`, `api.planningpoker.example.com`
  - **Verification:** `dig app.planningpoker.example.com`
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] DNS TTL values set appropriately (3600s for production)
  - **Verification:** Check DNS records TTL
  - **Assigned:** DevOps Team
  - **Status:** Pending

### SSL/TLS Certificates

- [ ] **[CRITICAL]** SSL certificates provisioned and valid
  - **Certificate Authority:** Let's Encrypt (or DigiCert for Enterprise)
  - **Verification:** `curl -vI https://planningpoker.example.com 2>&1 | grep 'SSL certificate verify ok'`
  - **Assigned:** DevOps Team
  - **Status:** Pending
  - **Expiry Date:** _________
  - **Auto-renewal:** Enabled via cert-manager

- [ ] **[CRITICAL]** HTTPS enforced (HTTP → HTTPS redirect)
  - **Verification:** `curl -I http://planningpoker.example.com | grep 'Location: https'`
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] HSTS header configured (Strict-Transport-Security)
  - **Max-Age:** 31536000 (1 year)
  - **Verification:** Check response headers
  - **Assigned:** Backend Team
  - **Status:** Pending

- [ ] SSL/TLS best practices enforced (TLS 1.2+, strong ciphers)
  - **Verification:** Test with SSL Labs (https://www.ssllabs.com/ssltest/)
  - **Target Grade:** A or A+
  - **Assigned:** Security Team
  - **Status:** Pending

### Third-Party Service Configuration

- [ ] **[CRITICAL]** Stripe production keys configured
  - **Environment:** Kubernetes secret `stripe-credentials`
  - **Keys:** `STRIPE_PUBLISHABLE_KEY`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`
  - **Verification:** Test subscription creation in production mode
  - **Assigned:** Backend Team
  - **Status:** Pending
  - **Notes:** Switch from test keys (`pk_test_*`, `sk_test_*`) to live keys (`pk_live_*`, `sk_live_*`)

- [ ] Stripe webhook endpoint configured
  - **URL:** `https://api.planningpoker.example.com/webhooks/stripe`
  - **Events:** `checkout.session.completed`, `customer.subscription.updated`, `customer.subscription.deleted`
  - **Verification:** Send test webhook from Stripe dashboard
  - **Assigned:** Backend Team
  - **Status:** Pending

- [ ] **[CRITICAL]** OAuth production apps registered (Google)
  - **Client ID:** `_________.apps.googleusercontent.com`
  - **Redirect URI:** `https://app.planningpoker.example.com/auth/callback/google`
  - **Verification:** Test OAuth flow in production
  - **Assigned:** Backend Team
  - **Status:** Pending

- [ ] **[CRITICAL]** OAuth production apps registered (Microsoft)
  - **Application ID:** `_________`
  - **Redirect URI:** `https://app.planningpoker.example.com/auth/callback/microsoft`
  - **Verification:** Test OAuth flow in production
  - **Assigned:** Backend Team
  - **Status:** Pending

### Cloud Infrastructure

- [ ] **[CRITICAL]** S3 production bucket configured
  - **Bucket Name:** `planning-poker-exports-prod`
  - **Region:** `us-east-1`
  - **Versioning:** Enabled
  - **Encryption:** SSE-S3 or SSE-KMS
  - **Verification:** Upload/download test file
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] S3 bucket lifecycle policies configured
  - **Delete objects older than:** 90 days (Free tier exports)
  - **Verification:** Check bucket lifecycle rules
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] **[CRITICAL]** Redis production cluster configured (ElastiCache or equivalent)
  - **Cluster ID:** `planning-poker-redis-prod`
  - **Node Type:** `cache.t3.medium` (or larger)
  - **Nodes:** 2 (primary + replica for HA)
  - **Verification:** Connect via `redis-cli` and run `INFO`
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] **[CRITICAL]** PostgreSQL production database configured (RDS or equivalent)
  - **Instance ID:** `planning-poker-postgres-prod`
  - **Engine:** PostgreSQL 15+
  - **Instance Type:** `db.t3.large` (or larger)
  - **Multi-AZ:** Enabled (for high availability)
  - **Automated Backups:** Enabled (retention: 30 days)
  - **Verification:** Connect via `psql` and run `SELECT version();`
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] Database migrations applied to production
  - **Flyway/Liquibase:** All migrations up to latest version
  - **Verification:** Check migration history table
  - **Assigned:** Backend Team
  - **Status:** Pending

### Kubernetes Deployment

- [ ] **[CRITICAL]** Environment variables set (Kubernetes secrets)
  - **Secrets:** `app-secrets`, `db-credentials`, `stripe-credentials`, `oauth-credentials`
  - **Verification:** `kubectl get secrets -n planning-poker-prod`
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] **[CRITICAL]** Resource limits configured (CPU, memory)
  - **Frontend:** `requests: 100m CPU, 128Mi RAM | limits: 500m CPU, 512Mi RAM`
  - **Backend:** `requests: 500m CPU, 512Mi RAM | limits: 2000m CPU, 2Gi RAM`
  - **Verification:** Check pod resource specifications
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] Horizontal Pod Autoscaler (HPA) configured
  - **Target CPU Utilization:** 70%
  - **Min Replicas:** 3
  - **Max Replicas:** 20
  - **Verification:** `kubectl get hpa -n planning-poker-prod`
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] **[CRITICAL]** Health checks configured (liveness, readiness)
  - **Liveness Probe:** `GET /health/live`
  - **Readiness Probe:** `GET /health/ready`
  - **Verification:** Check pod events for probe results
  - **Assigned:** Backend Team
  - **Status:** Pending

---

## 2. Content & Legal

### Marketing Website

- [ ] **[CRITICAL]** Marketing website deployed to production
  - **URL:** `https://planningpoker.example.com`
  - **Verification:** Visit URL and verify all pages load
  - **Assigned:** Frontend Team
  - **Status:** Pending

- [ ] Landing page performance validated (<2 seconds load time)
  - **Tool:** Google PageSpeed Insights
  - **Target:** Score ≥90 for mobile and desktop
  - **Verification:** Test with PageSpeed Insights
  - **Assigned:** Frontend Team
  - **Status:** Pending

- [ ] Pricing page displays all tiers correctly
  - **Tiers:** Free ($0), Pro ($10), Pro Plus ($30), Enterprise (Contact Sales)
  - **Verification:** Visual inspection of /pricing page
  - **Assigned:** Product Team
  - **Status:** Pending

- [ ] Demo page provides access to demo room or video
  - **Demo Room URL:** `https://app.planningpoker.example.com/room/DEMO01`
  - **Verification:** Click demo link and verify room loads
  - **Assigned:** Product Team
  - **Status:** Pending

### Legal Pages

- [ ] **[CRITICAL]** Privacy policy published and accessible
  - **URL:** `https://planningpoker.example.com/privacy`
  - **Verification:** Visit URL and verify content is complete
  - **Legal Review:** Required (consult legal counsel)
  - **Assigned:** Legal/Compliance Team
  - **Status:** Pending
  - **Notes:** Ensure GDPR and CCPA compliance

- [ ] **[CRITICAL]** Terms of service published and accessible
  - **URL:** `https://planningpoker.example.com/terms`
  - **Verification:** Visit URL and verify content is complete
  - **Legal Review:** Required (consult legal counsel)
  - **Assigned:** Legal/Compliance Team
  - **Status:** Pending

- [ ] Privacy policy and ToS linked in footer on ALL pages
  - **Verification:** Check footer links on every page (landing, pricing, demo, blog)
  - **Assigned:** Frontend Team
  - **Status:** Pending

### Support & Communication

- [ ] **[CRITICAL]** Support email functional (`support@planningpoker.example.com`)
  - **Email Forwarding:** Configure to team support inbox
  - **Verification:** Send test email and verify receipt
  - **Response SLA:** <24 hours for initial response
  - **Assigned:** Operations Team
  - **Status:** Pending

- [ ] Contact form functional (Formspree or custom endpoint)
  - **Form URL:** `https://planningpoker.example.com/#contact`
  - **Backend:** Formspree form ID or custom `/api/contact` endpoint
  - **Verification:** Submit test inquiry and verify email delivery
  - **Assigned:** Frontend Team
  - **Status:** Pending

- [ ] Blog first post published (product announcement)
  - **URL:** `https://planningpoker.example.com/blog/launch-announcement`
  - **Verification:** Visit URL and verify post displays correctly
  - **Assigned:** Marketing Team
  - **Status:** Pending

---

## 3. Monitoring & Operations

### Observability

- [ ] **[CRITICAL]** Prometheus metrics scraping configured
  - **Targets:** Application pods, PostgreSQL, Redis, Kubernetes nodes
  - **Verification:** Check Prometheus targets (`/targets` endpoint)
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] **[CRITICAL]** Grafana dashboards configured
  - **Dashboards:**
    1. Application Metrics (request rate, error rate, latency)
    2. WebSocket Metrics (connections, messages, disconnections)
    3. Business Metrics (sessions, users, subscriptions)
    4. Infrastructure Metrics (CPU, memory, disk, network)
  - **Verification:** Access Grafana and verify all dashboards load with data
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] **[CRITICAL]** Alerting rules configured
  - **Critical Alerts:**
    - Application pod crash loop (threshold: 3 restarts in 5 minutes)
    - High error rate (threshold: >5% of requests)
    - Database connection pool exhaustion (threshold: >90% utilization)
    - Certificate expiration warning (threshold: <30 days)
  - **Verification:** Review alerting rules in Prometheus
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] Alert notification channels configured
  - **Channels:** Email, Slack (#alerts channel), PagerDuty
  - **Verification:** Send test alert to each channel
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] Logging aggregation configured
  - **Tool:** AWS CloudWatch Logs, Loki, or ELK Stack
  - **Log Retention:** 90 days
  - **Verification:** Search logs for recent application events
  - **Assigned:** DevOps Team
  - **Status:** Pending

### Incident Management

- [ ] **[CRITICAL]** On-call schedule established
  - **Tool:** PagerDuty, Opsgenie, or similar
  - **Schedule:** 24/7 coverage with rotation
  - **Escalation Policy:** Defined and documented
  - **Verification:** Review on-call calendar
  - **Assigned:** Operations Team
  - **Status:** Pending

- [ ] Runbook documentation complete
  - **Location:** `docs/operations/runbooks/`
  - **Runbooks:**
    - Incident response procedure
    - Database failover
    - Application rollback
    - Service degradation mitigation
  - **Verification:** Review runbook completeness
  - **Assigned:** Operations Team
  - **Status:** Pending

- [ ] Incident communication plan documented
  - **Status Page:** Set up status.planningpoker.example.com (optional)
  - **Communication Channels:** Email, Twitter, in-app banner
  - **Verification:** Review communication plan document
  - **Assigned:** Operations Team
  - **Status:** Pending

### Backup & Recovery

- [ ] **[CRITICAL]** Database backup/restore tested
  - **Backup Frequency:** Daily automated snapshots (RDS)
  - **Backup Retention:** 30 days
  - **Recovery Test:** Restore from snapshot to separate instance
  - **RTO (Recovery Time Objective):** <4 hours
  - **RPO (Recovery Point Objective):** <24 hours
  - **Verification:** Perform test restore and validate data integrity
  - **Assigned:** DevOps Team
  - **Status:** Pending
  - **Last Test Date:** _________

- [ ] S3 bucket versioning enabled for exports
  - **Verification:** Check S3 bucket versioning settings
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] Disaster recovery plan documented
  - **Location:** `docs/operations/disaster-recovery.md`
  - **Scenarios:** Region failure, data corruption, complete outage
  - **Verification:** Review DR plan with team
  - **Assigned:** Operations Team
  - **Status:** Pending

---

## 4. Quality Assurance

### Performance

- [ ] **[CRITICAL]** Performance benchmarks validated
  - **Load Test Tool:** k6, Locust, or JMeter
  - **Target Metrics:**
    - 500 concurrent sessions
    - <200ms median API latency (p50)
    - <500ms p95 latency
    - <1000ms p99 latency
  - **Verification:** Run load tests and review results
  - **Assigned:** QA/Performance Team
  - **Status:** Pending
  - **Last Test Date:** _________
  - **Results:** _________

- [ ] WebSocket connection stability tested
  - **Concurrent Connections:** 1,000+ simultaneous WebSocket connections
  - **Message Throughput:** 10,000+ messages/second
  - **Connection Drop Rate:** <0.1%
  - **Verification:** Run WebSocket load tests
  - **Assigned:** QA/Performance Team
  - **Status:** Pending

- [ ] Database query performance optimized
  - **Slow Query Threshold:** >100ms
  - **Indexes:** All slow queries indexed
  - **Verification:** Review slow query log and execution plans
  - **Assigned:** Backend Team
  - **Status:** Pending

### Security

- [ ] **[CRITICAL]** Security scan passed (container images)
  - **Tool:** Trivy, Snyk, or similar
  - **Severity Threshold:** No CRITICAL or HIGH vulnerabilities
  - **Verification:** Run image scan and review results
  - **Assigned:** Security Team
  - **Status:** Pending
  - **Last Scan Date:** _________

- [ ] **[CRITICAL]** OWASP ZAP scan passed (web application)
  - **Scan Type:** Full active scan
  - **Findings:** No HIGH or CRITICAL vulnerabilities
  - **Verification:** Run ZAP scan and review report
  - **Assigned:** Security Team
  - **Status:** Pending
  - **Last Scan Date:** _________

- [ ] Dependency vulnerability scan passed
  - **Tool:** Snyk, Dependabot, or npm audit
  - **Verification:** Review scan results and remediate findings
  - **Assigned:** Backend/Frontend Teams
  - **Status:** Pending

- [ ] Penetration testing completed (optional for enterprise)
  - **Scope:** External penetration test by third-party firm
  - **Verification:** Review pentest report and remediate findings
  - **Assigned:** Security Team
  - **Status:** Optional (recommended for Enterprise launch)

- [ ] Security headers configured
  - **Headers:** Content-Security-Policy, X-Frame-Options, X-Content-Type-Options
  - **Verification:** Test with securityheaders.com
  - **Target Grade:** A
  - **Assigned:** Backend Team
  - **Status:** Pending

### Functional Testing

- [ ] **[CRITICAL]** Smoke tests passing (all critical journeys)
  - **Test Suite:** `tests/smoke/`
  - **Journeys:**
    1. Anonymous user creates room
    2. User signs up via OAuth
    3. User creates session and votes
    4. User upgrades to Pro tier
    5. User exports session data
    6. Organization admin invites members
  - **Verification:** Run smoke test suite in production environment
  - **Assigned:** QA Team
  - **Status:** Pending

- [ ] End-to-end tests passing
  - **Test Framework:** Playwright or Cypress
  - **Coverage:** All major user flows
  - **Verification:** Run E2E test suite
  - **Assigned:** QA Team
  - **Status:** Pending

- [ ] Cross-browser compatibility tested
  - **Browsers:** Chrome, Firefox, Safari, Edge (latest 2 versions)
  - **Verification:** Manual testing on each browser
  - **Assigned:** QA Team
  - **Status:** Pending

- [ ] Mobile responsiveness tested
  - **Devices:** iOS (iPhone 12+, iPad), Android (Pixel, Samsung Galaxy)
  - **Viewport Sizes:** 320px, 375px, 768px, 1024px
  - **Verification:** Manual testing on real devices or BrowserStack
  - **Assigned:** QA Team
  - **Status:** Pending

### Documentation

- [ ] **[CRITICAL]** Deployment guide complete
  - **Location:** `docs/deployment/production-deployment.md`
  - **Contents:** Step-by-step deployment instructions, rollback procedure
  - **Verification:** Follow guide to deploy to staging environment
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] Operations runbook complete
  - **Location:** `docs/operations/runbook.md`
  - **Contents:** Common operational tasks, troubleshooting procedures
  - **Verification:** Review with operations team
  - **Assigned:** Operations Team
  - **Status:** Pending

- [ ] API documentation complete
  - **Location:** Swagger/OpenAPI spec at `/api/docs`
  - **Verification:** Visit API docs endpoint and verify completeness
  - **Assigned:** Backend Team
  - **Status:** Pending

- [ ] User documentation complete
  - **Location:** Help center or in-app documentation
  - **Verification:** Review user guides for completeness
  - **Assigned:** Product Team
  - **Status:** Pending

---

## 5. Marketing & Announcements

### Pre-Launch Marketing

- [ ] Social media accounts created
  - **Platforms:** Twitter (@PlanningPoker), LinkedIn (Planning Poker)
  - **Bio/Description:** Completed with links to website
  - **Verification:** Verify accounts are live and branded
  - **Assigned:** Marketing Team
  - **Status:** Pending

- [ ] Launch announcement drafted
  - **Channels:** Email (beta users), Twitter, LinkedIn, Product Hunt
  - **Verification:** Review announcement copy
  - **Assigned:** Marketing Team
  - **Status:** Pending

- [ ] Product Hunt submission prepared (optional)
  - **Assets:** Logo, screenshots, description, tagline
  - **Hunter:** Identified (if using hunter)
  - **Verification:** Review submission materials
  - **Assigned:** Marketing Team
  - **Status:** Optional

- [ ] Press kit created (optional)
  - **Assets:** Logo (PNG, SVG), brand colors, boilerplate description
  - **Location:** `https://planningpoker.example.com/press`
  - **Verification:** Verify press kit page is accessible
  - **Assigned:** Marketing Team
  - **Status:** Optional

### SEO & Analytics

- [ ] **[CRITICAL]** SEO meta tags verified on all pages
  - **Tags:** Title, description, Open Graph (og:title, og:description, og:image), Twitter Card
  - **Verification:** View page source on each page and verify tags
  - **Assigned:** Frontend Team
  - **Status:** Pending

- [ ] Sitemap submitted to Google Search Console
  - **Sitemap URL:** `https://planningpoker.example.com/sitemap.xml`
  - **Verification:** Check Google Search Console for sitemap submission
  - **Assigned:** Marketing Team
  - **Status:** Pending

- [ ] Google Analytics or Plausible configured
  - **Tracking ID:** `G-XXXXXXXXXX` (Google Analytics) or Plausible domain
  - **Verification:** Verify analytics tracking is recording pageviews
  - **Assigned:** Marketing Team
  - **Status:** Pending

- [ ] Conversion tracking configured
  - **Events:** Sign-up, subscription purchase, trial start
  - **Verification:** Test conversion events in analytics dashboard
  - **Assigned:** Marketing Team
  - **Status:** Pending

---

## 6. Launch Execution

### Pre-Launch (T-7 days)

- [ ] Final checklist review meeting
  - **Attendees:** DevOps, Backend, Frontend, QA, Product, Marketing leads
  - **Date:** _________
  - **Status:** Pending

- [ ] Production environment smoke test
  - **Verification:** Run full smoke test suite in production
  - **Assigned:** QA Team
  - **Status:** Pending

- [ ] Launch communication drafted
  - **Internal:** Slack announcement, email to team
  - **External:** Social media posts, email to beta users
  - **Assigned:** Marketing Team
  - **Status:** Pending

### Launch Day (T-0)

- [ ] **[CRITICAL]** Final deployment to production
  - **Deployment Window:** Off-peak hours (e.g., Saturday 6 AM UTC)
  - **Rollback Plan:** Documented and rehearsed
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] Post-deployment smoke tests
  - **Verification:** Run critical journey smoke tests
  - **Assigned:** QA Team
  - **Status:** Pending

- [ ] Monitoring dashboards reviewed
  - **Metrics:** Error rate, latency, connection count
  - **Assigned:** DevOps Team
  - **Status:** Pending

- [ ] Launch announcement published
  - **Channels:** Twitter, LinkedIn, blog, email
  - **Assigned:** Marketing Team
  - **Status:** Pending

### Post-Launch (T+1 to T+7 days)

- [ ] Monitor error rates and alerts (first 24 hours)
  - **Threshold:** Error rate <1%
  - **Assigned:** On-call engineer
  - **Status:** Pending

- [ ] Review performance metrics (first 48 hours)
  - **Metrics:** Latency, throughput, connection stability
  - **Assigned:** Performance Team
  - **Status:** Pending

- [ ] Collect user feedback
  - **Channels:** Support email, in-app feedback, social media
  - **Assigned:** Product Team
  - **Status:** Pending

- [ ] Post-launch retrospective meeting
  - **Date:** T+7 days
  - **Attendees:** All launch stakeholders
  - **Assigned:** Product Manager
  - **Status:** Pending

---

## Summary

**Total Items:** 105
**Critical Items:** 32
**Completed:** 0
**In Progress:** 0
**Pending:** 105

**Estimated Completion Date:** _________
**Planned Launch Date:** _________
**Actual Launch Date:** _________

---

## Sign-Off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| DevOps Lead | _________ | _________ | _____ |
| Backend Lead | _________ | _________ | _____ |
| Frontend Lead | _________ | _________ | _____ |
| QA Lead | _________ | _________ | _____ |
| Security Lead | _________ | _________ | _____ |
| Product Manager | _________ | _________ | _____ |
| Engineering Manager | _________ | _________ | _____ |

---

## Notes

**IMPORTANT:**
- This checklist should be reviewed weekly leading up to launch
- All CRITICAL items are blocking for launch and must be completed
- For any item that cannot be completed, document a mitigation plan
- Update status and verification notes as items are completed
- Keep this document under version control (Git) for audit trail

**Contact for Questions:**
- **DevOps:** devops@planningpoker.example.com
- **Support:** support@planningpoker.example.com
- **Security:** security@planningpoker.example.com
