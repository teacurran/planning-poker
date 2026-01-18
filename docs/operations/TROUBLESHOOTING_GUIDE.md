# Troubleshooting Guide

**Last Updated:** 2026-01-18
**Application:** Planning Poker
**Environment:** Production (AWS EKS)

## Table of Contents

- [Overview](#overview)
- [Quick Diagnostic Commands](#quick-diagnostic-commands)
- [Top 5 Common Issues](#top-5-common-issues)
- [WebSocket Issues](#websocket-issues)
- [Database Issues](#database-issues)
- [Redis Issues](#redis-issues)
- [Authentication Issues](#authentication-issues)
- [Performance Issues](#performance-issues)
- [Kubernetes Issues](#kubernetes-issues)
- [Known Issues and Workarounds](#known-issues-and-workarounds)

## Overview

This guide provides diagnostic procedures and solutions for common issues in the Planning Poker application. It is designed for on-call engineers and support teams to quickly diagnose and resolve incidents.

**Target Audience:**
- On-call engineers
- Support team
- Site reliability engineers (SRE)

**How to Use This Guide:**
1. Identify symptoms from user reports or monitoring alerts
2. Find matching issue in table of contents
3. Follow diagnostic steps to confirm root cause
4. Apply resolution steps
5. Verify fix with verification commands
6. Document incident for future reference

**Related Documentation:**
- [OPERATIONS_RUNBOOK.md](./OPERATIONS_RUNBOOK.md) - Operational procedures
- [MONITORING_GUIDE.md](./MONITORING_GUIDE.md) - Alert triage procedures
- [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Deployment troubleshooting

## Quick Diagnostic Commands

Use these commands for fast initial diagnosis.

```bash
# Check pod status
kubectl get pods -n production -l app=scrum-poker-backend

# View recent logs
kubectl logs -n production -l app=scrum-poker-backend --tail=100

# Check application health
curl https://planningpoker.example.com/q/health/ready

# Check recent deployments
kubectl rollout history deployment/scrum-poker-backend -n production

# Check HPA status
kubectl get hpa -n production

# Check ingress/ALB status
kubectl get ingress -n production

# View recent pod events
kubectl get events -n production --sort-by='.lastTimestamp' | tail -20
```

## Top 5 Common Issues

### 1. WebSocket Connection Failures (35% of incidents)

**Symptoms:**
- Frontend shows "Disconnected" status
- Users can't join rooms or vote
- Logs show "WebSocket connection refused" or "Connection closed"
- Monitoring shows spike in disconnection rate

**Quick Diagnosis:**
```bash
# Check pod health
kubectl get pods -n production -l app=scrum-poker-backend
# Expected: All pods Running with READY 1/1

# Check WebSocket errors in logs
kubectl logs -n production -l app=scrum-poker-backend --tail=500 | grep -i "websocket\|stomp"

# Check ingress configuration
kubectl describe ingress scrum-poker-backend -n production | grep -A 10 "Annotations"
```

**Root Cause → Resolution:** See [WebSocket Issues](#websocket-issues) section below.

### 2. Database Connection Exhaustion (25% of incidents)

**Symptoms:**
- Application logs: "Unable to acquire JDBC Connection"
- 500 Internal Server Error on API endpoints
- Health check shows database DOWN
- HikariCP metrics show connections_active = connections_max

**Quick Diagnosis:**
```bash
# Check connection pool metrics in Grafana
# Open Infrastructure dashboard → Database Connection Pool panel

# Or query Prometheus:
kubectl port-forward -n monitoring svc/prometheus 9090:9090
# Query: hikaricp_connections_active{application="planning-poker"}
```

**Root Cause → Resolution:** See [Database Issues](#database-issues) section below.

### 3. High Error Rate After Deployment (20% of incidents)

**Symptoms:**
- Monitoring alert: "HighErrorRate" triggered
- Users experience 500 Internal Server Error
- Metrics show >5% error rate
- Errors started immediately after deployment

**Quick Diagnosis:**
```bash
# Check recent deployments
kubectl rollout history deployment/scrum-poker-backend -n production

# Check error logs from new pods
kubectl logs -n production -l app=scrum-poker-backend --tail=200 | grep ERROR

# Check for common errors:
# - ClassNotFoundException: Missing dependency
# - NullPointerException: Code bug
# - ConfigurationException: Invalid configuration
```

**Resolution:**
```bash
# Rollback to previous version
kubectl rollout undo deployment/scrum-poker-backend -n production

# Monitor rollback progress
kubectl rollout status deployment/scrum-poker-backend -n production

# Verify error rate decreases in Grafana (Application Overview dashboard)
```

**Post-Rollback:**
- Document error details in incident ticket
- Test fix in staging environment
- Create hotfix branch and retest before redeploying

### 4. Redis Out of Memory (10% of incidents)

**Symptoms:**
- Application logs: "OOM command not allowed when used memory > 'maxmemory'"
- Rate limiting stops working (users bypass limits)
- WebSocket events not broadcasting to all users
- Redis memory usage >90%

**Quick Diagnosis:**
```bash
# Check Redis memory usage
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com INFO memory | grep used_memory_human
# Compare to maxmemory

# Check eviction stats
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com INFO stats | grep evicted_keys
```

**Root Cause → Resolution:** See [Redis Issues](#redis-issues) section below.

### 5. OAuth Authentication Failures (10% of incidents)

**Symptoms:**
- Users can't log in with Google/Microsoft
- Callback returns "401 Unauthorized" or "Invalid OAuth code"
- Logs show "OAuth code exchange failed" or "Invalid state parameter"
- Frontend shows "Authentication failed" error

**Quick Diagnosis:**
```bash
# Check OAuth error logs
kubectl logs -n production -l app=scrum-poker-backend --tail=500 | grep -i "oauth\|authentication"

# Common errors:
# - "Invalid redirect_uri": Mismatch between app and provider config
# - "Invalid code": PKCE verification failed
# - "Token exchange failed": Client secret incorrect
```

**Root Cause → Resolution:** See [Authentication Issues](#authentication-issues) section below.

## WebSocket Issues

### Issue 1: WebSocket Connection Refused

**Symptoms:**
```
Frontend error: "WebSocket connection to 'wss://planningpoker.example.com/ws/abc123' failed"
Browser console: "Error during WebSocket handshake: Unexpected response code: 502"
```

**Diagnostic Decision Tree:**

```
[WebSocket Connection Refused]
         |
         v
   Are pods running and healthy?
         |
         ├─ NO ──> Check pod status
         |          kubectl get pods -n production
         |          └─> Pods CrashLoopBackOff? See "Kubernetes Issues"
         |          └─> Pods ImagePullBackOff? See DEPLOYMENT_GUIDE.md
         |
         └─ YES ──> Check ingress/ALB configuration
                    |
                    ├─> ALB timeout too short?
                    |   └─> Update to ≥3600s (see Resolution A)
                    |
                    ├─> Sticky sessions disabled?
                    |   └─> Enable sticky sessions (see Resolution B)
                    |
                    └─> CORS blocking WSS?
                        └─> Add WSS origin to CORS config (see Resolution C)
```

**Resolution A: Fix ALB Timeout**

**Problem:** ALB timeout (default 60s) is shorter than WebSocket idle timeout (3600s), causing connections to drop.

**Command:**
```bash
# Check current ALB timeout
kubectl describe ingress scrum-poker-backend -n production | grep timeout

# Update ingress with annotation
kubectl annotate ingress scrum-poker-backend -n production \
  alb.ingress.kubernetes.io/load-balancer-attributes=idle_timeout.timeout_seconds=3600 \
  --overwrite

# Verify ALB updated (takes 2-3 minutes)
kubectl describe ingress scrum-poker-backend -n production | grep timeout
```

**Verification:**
```bash
# Test WebSocket connection
wscat -c wss://planningpoker.example.com/ws/<session-id>
# Expected: Connection established, can send/receive messages
```

**Resolution B: Enable Sticky Sessions**

**Problem:** Load balancer distributing WebSocket requests across pods without session affinity, breaking connection state.

**Command:**
```bash
# Check if sticky sessions enabled
kubectl describe ingress scrum-poker-backend -n production | grep -i sticky

# Add sticky session annotation
kubectl annotate ingress scrum-poker-backend -n production \
  alb.ingress.kubernetes.io/target-group-attributes=stickiness.enabled=true,stickiness.lb_cookie.duration_seconds=3600 \
  --overwrite

# Verify annotation applied
kubectl get ingress scrum-poker-backend -n production -o yaml | grep -A 5 annotations
```

**Resolution C: Fix CORS Configuration**

**Problem:** CORS policy blocking WebSocket upgrade from WSS origin.

**Command:**
```bash
# Check CORS configuration in application
kubectl exec -it -n production <pod-name> -- env | grep CORS

# Update CORS allowed origins in ConfigMap (if using)
kubectl edit configmap scrum-poker-config -n production
# Add: quarkus.http.cors.origins=https://planningpoker.example.com,wss://planningpoker.example.com

# Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production
```

### Issue 2: WebSocket Frequent Disconnections

**Symptoms:**
- Users report being disconnected every 1-2 minutes
- Monitoring shows high disconnection rate (>10/min)
- Logs show repeated "WebSocket session closed" messages

**Diagnostic Decision Tree:**

```
[Frequent WebSocket Disconnections]
         |
         v
   Check disconnection pattern in logs
         |
         ├─> All users disconnecting at same time ──> Pod restart or network issue
         |                                              └─> Check pod events
         |
         ├─> Disconnections every ~60 seconds ──────> Timeout issue
         |                                              └─> Check idle timeout config
         |
         └─> Random disconnections ─────────────────> Client-side network or app bug
                                                        └─> Review client logs
```

**Resolution: Adjust WebSocket Idle Timeout**

**Command:**
```bash
# Check current idle timeout configuration
kubectl exec -it -n production <pod-name> -- env | grep IDLE

# Update timeout in ConfigMap
kubectl edit configmap scrum-poker-config -n production
# Add/update: quarkus.websocket.idle-timeout=3600s

# Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production

# Verify disconnection rate decreases in Grafana
```

### Issue 3: WebSocket Messages Not Broadcasting

**Symptoms:**
- User A votes, but User B doesn't see vote in real-time
- Users must refresh page to see updates
- Logs show message sent but not received

**Diagnostic Decision Tree:**

```
[Messages Not Broadcasting]
         |
         v
   Check Redis Pub/Sub
         |
         ├─> Redis connection failed? ──────> Check Redis connectivity
         |                                     └─> See "Redis Issues"
         |
         ├─> Redis out of memory? ──────────> Check Redis memory
         |                                     └─> See "Redis Issues"
         |
         └─> Application not subscribing? ──> Check subscription logs
                                               └─> Restart pods to re-establish
```

**Resolution: Re-establish Redis Pub/Sub**

**Command:**
```bash
# Check Redis connectivity
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com PING
# Expected: PONG

# Check active Pub/Sub channels
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com PUBSUB CHANNELS

# Restart application to re-establish subscriptions
kubectl rollout restart deployment/scrum-poker-backend -n production

# Monitor logs for subscription confirmation
kubectl logs -n production -l app=scrum-poker-backend --tail=100 | grep -i "subscribe\|pubsub"
```

## Database Issues

### Issue 1: Database Connection Exhaustion

**Symptoms:**
- Application logs: "Unable to acquire JDBC Connection"
- 500 errors on API endpoints requiring database
- Health check: `{"name":"Database","status":"DOWN"}`
- HikariCP metrics: `connections_active` = `connections_max` (50)

**Diagnostic Decision Tree:**

```
[Database Connection Exhaustion]
         |
         v
   Check connection pool metrics
         |
         ├─> Connections active = max ──────> Check for connection leaks
         |                                     |
         |                                     ├─> Long-running queries?
         |                                     |   └─> Kill slow queries (see Resolution A)
         |                                     |
         |                                     └─> Connections not released?
         |                                         └─> Code bug (missing .close())
         |
         └─> Connections < max ─────────────> Database unreachable
                                               └─> Check RDS status, security groups
```

**Resolution A: Kill Long-Running Queries**

**Command:**
```bash
# Connect to database
kubectl run -it --rm postgres-client --image=postgres:15 --restart=Never -- \
  psql -h scrumpoker-prod.xxxxx.rds.amazonaws.com -U scrumpoker_app -d scrumpoker

# List long-running queries (>5 minutes)
SELECT pid, now() - query_start as duration, state, query
FROM pg_stat_activity
WHERE state = 'active' AND now() - query_start > interval '5 minutes'
ORDER BY duration DESC;

# Kill specific query
SELECT pg_terminate_backend(<pid>);

# Kill all long-running queries (CAUTION: may impact active users)
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE usename = 'scrumpoker_app' AND state = 'active' AND now() - query_start > interval '10 minutes';

# Exit psql
\q
```

**Verification:**
```bash
# Check connection pool metrics in Grafana
# Expected: connections_active drops below max
# Expected: 500 errors stop

# Verify application health
curl https://planningpoker.example.com/q/health/ready
# Expected: {"status":"UP"}
```

**Resolution B: Increase Connection Pool Size**

**When to Use:** If connections legitimately needed (high load, not leaks).

**Command:**
```bash
# Edit ConfigMap to increase max connections
kubectl edit configmap scrum-poker-config -n production
# Update: quarkus.datasource.jdbc.max-size=75  (increase from 50)

# Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production

# Monitor connection pool metrics
# Ensure RDS can handle increased connections (max_connections setting)
```

**Resolution C: Scale Application Pods**

**When to Use:** High traffic, connections distributed across pods.

**Command:**
```bash
# Scale to more pods (distributes connection load)
kubectl scale deployment scrum-poker-backend --replicas=5 -n production

# Verify pods healthy
kubectl get pods -n production -l app=scrum-poker-backend

# Monitor connection distribution
# Each pod has own connection pool, reducing per-pod usage
```

### Issue 2: Slow Database Queries

**Symptoms:**
- High P95/P99 latency (>1 second)
- Database query duration metrics elevated
- Logs show slow query warnings
- Users report slow page loads

**Diagnostic Decision Tree:**

```
[Slow Database Queries]
         |
         v
   Identify slow queries
         |
         ├─> Missing index? ─────────────────> Add index (see Resolution A)
         |
         ├─> N+1 query problem? ────────────> Optimize query (batch fetching)
         |
         ├─> Large table scan? ─────────────> Add WHERE clause, limit results
         |
         └─> Database overloaded? ──────────> Scale RDS instance
```

**Resolution A: Identify and Index Slow Queries**

**Command:**
```bash
# Enable slow query logging (if not already)
# In RDS parameter group, set: log_min_duration_statement = 1000 (1 second)

# Connect to database
kubectl run -it --rm postgres-client --image=postgres:15 --restart=Never -- \
  psql -h scrumpoker-prod.xxxxx.rds.amazonaws.com -U scrumpoker_app -d scrumpoker

# Check active slow queries
SELECT pid, now() - query_start as duration, state, query
FROM pg_stat_activity
WHERE state = 'active'
ORDER BY duration DESC
LIMIT 10;

# Analyze query plan (example for slow session fetch)
EXPLAIN ANALYZE SELECT * FROM sessions WHERE created_at > NOW() - INTERVAL '7 days';

# If sequential scan shown, create index:
CREATE INDEX idx_sessions_created_at ON sessions(created_at);

# Verify index created
\d sessions

# Exit psql
\q
```

**Verification:**
```bash
# Check query duration metrics in Grafana
# Expected: P95 latency decreases
# Expected: Database query duration improves
```

### Issue 3: Database Deadlocks

**Symptoms:**
- Application logs: "deadlock detected" or "PSQLException: ERROR: deadlock detected"
- Intermittent 500 errors on write operations (POST, PUT, DELETE)
- Transaction rollback errors

**Diagnostic Decision Tree:**

```
[Database Deadlocks]
         |
         v
   Check deadlock frequency
         |
         ├─> Frequent (>10/hour) ───────────> Application bug (lock ordering)
         |                                     └─> Review transaction code
         |
         └─> Rare (<5/hour) ────────────────> Transient issue
                                               └─> Add retry logic
```

**Resolution: Investigate Deadlock Details**

**Command:**
```bash
# Connect to database
kubectl run -it --rm postgres-client --image=postgres:15 --restart=Never -- \
  psql -h scrumpoker-prod.xxxxx.rds.amazonaws.com -U scrumpoker_app -d scrumpoker

# Check for current locks
SELECT * FROM pg_locks WHERE NOT granted;

# Review recent deadlock errors in PostgreSQL logs
# (Access via AWS RDS Console → Logs)

# Exit psql
\q
```

**Mitigation:**
- Add application-level retry logic for deadlock errors
- Review transaction code for consistent lock ordering
- Reduce transaction scope (shorter transactions)
- Consider optimistic locking instead of pessimistic

## Redis Issues

### Issue 1: Redis Out of Memory

**Symptoms:**
- Application logs: "OOM command not allowed when used memory > 'maxmemory'"
- Rate limiting stops working
- WebSocket events not broadcasting
- Redis memory usage >90%

**Diagnostic Decision Tree:**

```
[Redis Out of Memory]
         |
         v
   Check Redis memory usage
         |
         ├─> Memory 90-100% ────────────────> Immediate eviction needed
         |                                     └─> FLUSHDB (see Resolution A - CAUTION)
         |
         ├─> Memory 80-90% ─────────────────> Identify large keys
         |                                     └─> Use --bigkeys (see Resolution B)
         |
         └─> Memory <80% but growing ───────> Memory leak or inefficient TTL
                                               └─> Review key expiration
```

**Resolution A: Emergency Cache Flush (CAUTION)**

**CRITICAL: This will disrupt active sessions. Use only in emergency.**

**Command:**
```bash
# Connect to Redis
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com -p 6379

# Check memory before flush
INFO memory

# Flush current database (DB 0)
FLUSHDB

# Verify memory cleared
INFO memory
DBSIZE
# Expected: used_memory significantly decreased, DBSIZE = 0

# Exit redis-cli
exit
```

**Impact:**
- Active WebSocket sessions disconnected
- Rate limiting reset (users can make new requests)
- Session cache lost (users re-authenticate)

**Post-Flush:**
```bash
# Monitor application logs for reconnections
kubectl logs -n production -l app=scrum-poker-backend --tail=100 -f

# Verify WebSocket reconnections in Grafana
# Check: scrumpoker_websocket_connections_total recovering
```

**Resolution B: Identify and Remove Large Keys**

**Command:**
```bash
# Find large keys
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com --bigkeys

# Example output:
# Biggest string found: 'rate_limit:user:abc123' (512 KB)
# Biggest hash found: 'session:xyz789' (2 MB)

# Inspect specific key
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com
MEMORY USAGE rate_limit:user:abc123
TTL rate_limit:user:abc123

# If key has no TTL but should expire:
EXPIRE rate_limit:user:abc123 60

# If key is orphaned/stale:
DEL rate_limit:user:abc123
```

**Resolution C: Scale Redis Instance**

**When to Use:** Legitimate memory usage growth, not leaks.

**Command:**
```bash
# Upgrade ElastiCache node type (AWS console or CLI)
aws elasticache modify-replication-group \
  --replication-group-id scrumpoker-prod-redis \
  --cache-node-type cache.m5.large \
  --apply-immediately \
  --region us-east-1

# Monitor upgrade progress
aws elasticache describe-replication-groups \
  --replication-group-id scrumpoker-prod-redis \
  --region us-east-1 \
  --query 'ReplicationGroups[0].Status'

# Expected: "modifying" → "available" (5-10 minutes)
```

### Issue 2: Redis Connection Failures

**Symptoms:**
- Application logs: "Unable to connect to Redis" or "RedisConnectionException"
- Rate limiting not working
- WebSocket broadcasting fails
- Health check may show Redis DOWN

**Diagnostic Decision Tree:**

```
[Redis Connection Failures]
         |
         v
   Can application reach Redis?
         |
         ├─ NO ──> Check network/security groups
         |          |
         |          ├─> ElastiCache security group allows EKS?
         |          └─> Correct Redis endpoint in config?
         |
         └─ YES ──> Check Redis status
                    |
                    ├─> Redis cluster unhealthy?
                    |   └─> Check AWS ElastiCache console
                    |
                    └─> Connection pool exhausted?
                        └─> Increase pool size
```

**Resolution A: Verify Redis Connectivity**

**Command:**
```bash
# Check Redis endpoint in secret
kubectl get secret scrum-poker-secrets -n production -o jsonpath='{.data.REDIS_URI}' | base64 -d
# Expected: redis://scrumpoker-prod-redis.xxxxx.cache.amazonaws.com:6379

# Test connectivity from pod
kubectl exec -it -n production <pod-name> -- /bin/bash
$ curl -v telnet://scrumpoker-prod-redis.xxxxx.cache.amazonaws.com:6379
# Expected: Connection established

# Or use redis-cli
$ redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com PING
# Expected: PONG
```

**Resolution B: Fix Security Group**

**When to Use:** ElastiCache security group blocking EKS node access.

**Command:**
```bash
# Get EKS node security group
kubectl get nodes -o wide
# Note node IP addresses

# Get ElastiCache security group
aws elasticache describe-replication-groups \
  --replication-group-id scrumpoker-prod-redis \
  --region us-east-1 \
  --query 'ReplicationGroups[0].NodeGroups[0].PrimaryEndpoint.Address'

# Update ElastiCache security group to allow inbound on port 6379 from EKS node security group
# (Use AWS Console → EC2 → Security Groups or AWS CLI)
```

## Authentication Issues

### Issue 1: OAuth Login Failures

**Symptoms:**
- Users click "Login with Google/Microsoft" but get error
- Callback returns "401 Unauthorized" or "Invalid OAuth code"
- Logs show "OAuth code exchange failed"
- Frontend shows "Authentication failed, please try again"

**Diagnostic Decision Tree:**

```
[OAuth Login Failures]
         |
         v
   Check OAuth error in logs
         |
         ├─> "Invalid redirect_uri" ────────> Redirect URI mismatch
         |                                     └─> Update OAuth provider config (see Resolution A)
         |
         ├─> "Invalid code" ────────────────> PKCE verification failed
         |                                     └─> Check code_verifier/challenge (see Resolution B)
         |
         ├─> "Invalid client_secret" ───────> Wrong secret in Kubernetes
         |                                     └─> Update secret (see Resolution C)
         |
         └─> "Token exchange timeout" ──────> Network issue or provider outage
                                               └─> Check OAuth provider status
```

**Resolution A: Fix Redirect URI Mismatch**

**Problem:** Redirect URI configured in application doesn't match OAuth provider.

**Command:**
```bash
# Check configured redirect URI in application logs
kubectl logs -n production -l app=scrum-poker-backend --tail=500 | grep -i "redirect_uri"

# Expected redirect URIs:
# Google: https://planningpoker.example.com/api/auth/callback/google
# Microsoft: https://planningpoker.example.com/api/auth/callback/microsoft

# Update in OAuth provider console:
# Google: https://console.cloud.google.com/apis/credentials
#   → Select OAuth client → Add Authorized redirect URIs
# Microsoft: https://portal.azure.com → App registrations
#   → Select app → Redirect URIs → Add Web redirect URI
```

**Verification:**
```bash
# Test OAuth login
# Open browser: https://planningpoker.example.com
# Click "Login with Google" or "Login with Microsoft"
# Expected: Successful authentication and redirect
```

**Resolution B: Fix PKCE Verification**

**Problem:** Frontend generates code_challenge but backend can't verify code_verifier.

**Diagnostic:**
```bash
# Check OAuth logs for PKCE error
kubectl logs -n production -l app=scrum-poker-backend --tail=500 | grep -i "pkce\|code_verifier\|code_challenge"

# Common errors:
# - "code_challenge does not match": Frontend/backend PKCE mismatch
# - "code_verifier missing": Frontend not sending verifier in callback
```

**Resolution:**
- Verify frontend generates PKCE correctly (SHA-256 of code_verifier)
- Ensure code_verifier sent in token exchange request
- Check OAuth provider supports PKCE (Google/Microsoft both support it)

**Resolution C: Update OAuth Client Secret**

**Problem:** Client secret in Kubernetes doesn't match OAuth provider.

**Command:**
```bash
# Get new client secret from OAuth provider console
# Google: https://console.cloud.google.com/apis/credentials
# Microsoft: https://portal.azure.com → App registrations → Certificates & secrets

# Update Kubernetes secret
kubectl create secret generic scrum-poker-secrets \
  --from-literal=DB_JDBC_URL='jdbc:postgresql://...' \
  --from-literal=DB_USERNAME='scrumpoker_app' \
  --from-literal=DB_PASSWORD='...' \
  --from-literal=REDIS_URI='redis://...' \
  --from-file=JWT_PRIVATE_KEY=jwt-private.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public.pem \
  --from-literal=OAUTH_GOOGLE_CLIENT_ID='<GOOGLE_CLIENT_ID>' \
  --from-literal=OAUTH_GOOGLE_CLIENT_SECRET='<NEW_GOOGLE_SECRET>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_ID='<MICROSOFT_CLIENT_ID>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_SECRET='<NEW_MICROSOFT_SECRET>' \
  -n production \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production
```

**Verification:**
```bash
# Test OAuth login
# Expected: Successful authentication
```

### Issue 2: JWT Validation Failures

**Symptoms:**
- Users logged in but API returns 401 Unauthorized
- Logs show "JWT signature verification failed" or "JWT expired"
- Frontend shows user as logged in but API calls fail

**Diagnostic Decision Tree:**

```
[JWT Validation Failures]
         |
         v
   Check JWT error in logs
         |
         ├─> "Signature verification failed" ───> Public key mismatch
         |                                          └─> Verify JWT keys in secret
         |
         ├─> "JWT expired" ─────────────────────> Token TTL too short
         |                                          └─> Check token expiration config
         |
         └─> "Invalid issuer" ──────────────────> Issuer claim mismatch
                                                    └─> Verify issuer configuration
```

**Resolution: Verify JWT Keys**

**Command:**
```bash
# Check JWT keys in secret
kubectl get secret scrum-poker-secrets -n production -o jsonpath='{.data.JWT_PUBLIC_KEY}' | base64 -d
# Expected: Valid RSA public key (-----BEGIN PUBLIC KEY-----)

# If key invalid or missing, regenerate and update secret
openssl genpkey -algorithm RSA -out jwt-private-new.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in jwt-private-new.pem -out jwt-public-new.pem

# Update secret (see OPERATIONS_RUNBOOK.md → Secret Rotation)
# CAUTION: This will log out all users

# Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production
```

## Performance Issues

### Issue 1: High Latency (P95 >1 second)

**Symptoms:**
- Monitoring alert: "HighLatency" triggered
- Users report slow page loads
- P95 latency >1000ms in Application Overview dashboard

**Diagnostic Decision Tree:**

```
[High Latency]
         |
         v
   Check latency by endpoint
         |
         ├─> All endpoints slow ────────────> System-wide issue
         |                                     |
         |                                     ├─> Database slow?
         |                                     |   └─> Check slow queries
         |                                     |
         |                                     ├─> CPU throttling?
         |                                     |   └─> Scale pods or increase CPU
         |                                     |
         |                                     └─> Redis slow?
         |                                         └─> Check Redis latency
         |
         └─> Specific endpoint slow ────────> Endpoint-specific issue
                                               └─> Review endpoint code
                                                   Check for N+1 queries
```

**Resolution A: Identify Slow Endpoints**

**Command:**
```bash
# Check "Top Endpoints by Latency" in Grafana Application Overview dashboard
# Or query Prometheus:
kubectl port-forward -n monitoring svc/prometheus 9090:9090
# Query: histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))

# Check logs for slow requests
kubectl logs -n production -l app=scrum-poker-backend --tail=1000 | grep -i "slow\|latency"
```

**Resolution B: Optimize Database Queries**

**When to Use:** Database query duration metrics high.

See [Database Issues → Slow Database Queries](#issue-2-slow-database-queries) above.

**Resolution C: Scale Application**

**When to Use:** CPU/memory high, system-wide slowness.

```bash
# Check CPU/memory in Infrastructure dashboard
# If CPU >70%, scale horizontally:
kubectl scale deployment scrum-poker-backend --replicas=5 -n production

# Or increase CPU limits (vertical scaling):
kubectl edit deployment scrum-poker-backend -n production
# Update: resources.limits.cpu to higher value (e.g., 2000m → 3000m)
```

## Kubernetes Issues

### Issue 1: Pods in CrashLoopBackOff

**Symptoms:**
```bash
kubectl get pods -n production
# NAME                                   READY   STATUS             RESTARTS
# scrum-poker-backend-7d8f9c5b6d-abc12   0/1     CrashLoopBackOff   5
```

**Diagnostic Decision Tree:**

```
[Pods in CrashLoopBackOff]
         |
         v
   Check pod logs
         |
         ├─> Database connection error ─────> Fix DB credentials/connectivity
         |
         ├─> Redis connection error ────────> Fix Redis credentials/connectivity
         |
         ├─> OutOfMemoryError ──────────────> Increase memory limits
         |
         ├─> Missing JWT keys ──────────────> Add JWT keys to secret
         |
         └─> Other error ───────────────────> Review stack trace
```

**Resolution: Diagnose and Fix Root Cause**

**Command:**
```bash
# Check current pod logs
kubectl logs -n production scrum-poker-backend-7d8f9c5b6d-abc12

# Check previous container logs (if restarted)
kubectl logs -n production scrum-poker-backend-7d8f9c5b6d-abc12 --previous

# Describe pod for events
kubectl describe pod -n production scrum-poker-backend-7d8f9c5b6d-abc12

# Common fixes:
# - Database error: Update DB_JDBC_URL in secret
# - Redis error: Update REDIS_URI in secret
# - OOM: Increase resources.limits.memory
# - Missing config: Add to ConfigMap/Secret

# After fix, delete pod to force restart with new config
kubectl delete pod scrum-poker-backend-7d8f9c5b6d-abc12 -n production
```

### Issue 2: ImagePullBackOff

**Symptoms:**
```bash
kubectl get pods -n production
# NAME                                   READY   STATUS             RESTARTS
# scrum-poker-backend-7d8f9c5b6d-abc12   0/1     ImagePullBackOff   0
```

**Resolution:**

See [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) → "Troubleshooting: ImagePullBackOff"

## Known Issues and Workarounds

### Known Issue 1: WebSocket Disconnection During Pod Restart

**Problem:** Users disconnected when pod restarted (rolling update, manual restart).

**Impact:** Brief disconnection (5-10 seconds), users auto-reconnect.

**Workaround:**
- Frontend implements automatic reconnection with exponential backoff
- Use rolling updates (maxUnavailable: 0) to minimize impact
- Schedule maintenance during low-traffic periods

**Future Fix:** Implement connection draining before pod termination.

### Known Issue 2: Rate Limiting Reset After Redis Flush

**Problem:** Rate limits reset after Redis FLUSHDB, users can bypass limits temporarily.

**Impact:** Brief window (1-2 minutes) where rate limits ineffective.

**Workaround:**
- Avoid FLUSHDB unless emergency
- Monitor for abuse after Redis flush
- Consider persistent rate limiting (database-backed)

**Future Fix:** Implement hybrid rate limiting (Redis + database fallback).

### Known Issue 3: OAuth Redirect Loop on Safari

**Problem:** Some Safari users experience redirect loop during OAuth login.

**Impact:** Users can't log in with OAuth on Safari (rare, <1% of users).

**Workaround:**
- Ask users to clear cookies and try again
- Use different browser (Chrome, Firefox work fine)
- Check Safari 3rd-party cookie settings (should be enabled)

**Root Cause:** Safari Intelligent Tracking Prevention blocking cookies.

**Future Fix:** Investigate SameSite cookie settings.

## Support and Resources

**Related Documentation:**
- [OPERATIONS_RUNBOOK.md](./OPERATIONS_RUNBOOK.md) - Operational procedures
- [MONITORING_GUIDE.md](./MONITORING_GUIDE.md) - Monitoring and alerting
- [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Deployment procedures
- [DISASTER_RECOVERY.md](./DISASTER_RECOVERY.md) - Backup and restore

**External Resources:**
- Kubernetes Troubleshooting: https://kubernetes.io/docs/tasks/debug/
- PostgreSQL Performance: https://www.postgresql.org/docs/current/performance-tips.html
- Redis Troubleshooting: https://redis.io/docs/manual/admin/

**Team Contacts:**
- On-Call Engineer: PagerDuty rotation
- DevOps Lead: devops-lead@example.com
- Support Team: #support Slack channel
