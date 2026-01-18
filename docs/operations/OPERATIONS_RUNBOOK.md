# Operations Runbook

**Last Updated:** 2026-01-18
**Application:** Planning Poker
**Environment:** Production (AWS EKS)

## Table of Contents

- [Overview](#overview)
- [Quick Reference](#quick-reference)
- [Scaling Operations](#scaling-operations)
- [Log Access and Analysis](#log-access-and-analysis)
- [Service Management](#service-management)
- [Database Operations](#database-operations)
- [Redis Operations](#redis-operations)
- [Secret Rotation](#secret-rotation)
- [Performance Tuning](#performance-tuning)
- [Incident Response](#incident-response)

## Overview

This runbook provides step-by-step procedures for common operational tasks in the Planning Poker application. It is designed for on-call engineers and support teams who need quick reference for day-to-day operations.

**Target Audience:**
- On-call engineers
- DevOps team
- Site reliability engineers (SRE)
- Support team

**Related Documentation:**
- [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Initial deployment procedures
- [MONITORING_GUIDE.md](./MONITORING_GUIDE.md) - Dashboard usage and alerting
- [TROUBLESHOOTING_GUIDE.md](./TROUBLESHOOTING_GUIDE.md) - Incident diagnosis and resolution
- [DISASTER_RECOVERY.md](./DISASTER_RECOVERY.md) - Backup and restore procedures

## Quick Reference

### Essential Commands

```bash
# Check pod status
kubectl get pods -n production -l app=scrum-poker-backend

# View recent logs
kubectl logs -n production -l app=scrum-poker-backend --tail=100 -f

# Scale deployment
kubectl scale deployment scrum-poker-backend --replicas=5 -n production

# Restart deployment
kubectl rollout restart deployment/scrum-poker-backend -n production

# Check HPA status
kubectl get hpa -n production

# Port-forward Grafana
kubectl port-forward -n monitoring svc/grafana 3000:3000
```

### Emergency Contacts

| Role | Contact | Escalation Time |
|------|---------|----------------|
| On-Call Engineer | PagerDuty rotation | Immediate |
| DevOps Lead | devops-lead@example.com | 15 minutes |
| Database Admin | dba@example.com | 30 minutes |
| CTO/Engineering VP | cto@example.com | 1 hour |

### Key Metrics Baselines

| Metric | Normal Range | Warning Threshold | Critical Threshold |
|--------|-------------|-------------------|-------------------|
| Request Rate | 100-500 req/min | >1000 req/min | >2000 req/min |
| Error Rate | <1% | 1-5% | >5% |
| P95 Latency | <500ms | 500-1000ms | >1000ms |
| WebSocket Connections | 10-100 | 100-200 | >200 |
| Database Connections | 5-20 | 20-40 | >40 |
| JVM Heap Usage | 40-70% | 70-85% | >85% |
| CPU Usage | 20-50% | 50-70% | >70% |

## Scaling Operations

### Manual Scaling (Temporary Load Spike)

**When to Use:**
- Traffic spike expected (product launch, marketing campaign)
- HPA scaling too slow for burst traffic
- Proactive scaling before planned event
- Black Friday, holiday shopping, conference demo

**Command:**
```bash
# Scale to 5 replicas
kubectl scale deployment scrum-poker-backend --replicas=5 -n production

# Verify scaling in progress
kubectl get deployment scrum-poker-backend -n production -w

# Check pod status
kubectl get pods -n production -l app=scrum-poker-backend
```

**Expected Behavior:**
- New pods created within 30-60 seconds
- All pods transition to Running state (check READY 1/1)
- Load balancer includes new pods in rotation
- Request distribution spreads across all pods

**Verification:**
```bash
# Verify all pods healthy
kubectl get pods -n production -l app=scrum-poker-backend
# Expected: All pods show READY 1/1, STATUS Running

# Check load distribution in Grafana
# Open Application Overview dashboard
# Verify requests distributed across all pod IPs
```

**Rollback to HPA-Managed Scaling:**
```bash
# Return to minimum replicas (HPA will manage from there)
kubectl scale deployment scrum-poker-backend --replicas=2 -n production

# Or delete manual replica count (let HPA take over)
kubectl autoscale deployment scrum-poker-backend --min=2 --max=10 --cpu-percent=70 -n production
```

**Caution:**
- Manual scaling overrides HPA until next HPA reconciliation (2-3 minutes)
- Monitor costs when scaling above normal levels
- Scale down after event to avoid unnecessary resource usage

### Adjust HPA Configuration

**When to Use:**
- Change autoscaling behavior permanently
- Adjust CPU threshold for different traffic patterns
- Increase max replicas for anticipated growth

**Command:**
```bash
# View current HPA configuration
kubectl get hpa scrum-poker-backend-hpa -n production -o yaml

# Edit HPA to change max replicas
kubectl edit hpa scrum-poker-backend-hpa -n production

# Or patch HPA with new values
kubectl patch hpa scrum-poker-backend-hpa -n production -p '{
  "spec": {
    "maxReplicas": 15,
    "metrics": [{
      "type": "Resource",
      "resource": {
        "name": "cpu",
        "target": {
          "type": "Utilization",
          "averageUtilization": 60
        }
      }
    }]
  }
}'
```

**HPA Configuration Parameters:**

| Parameter | Default | Recommended Range | Notes |
|-----------|---------|-------------------|-------|
| minReplicas | 2 | 2-3 | Multi-AZ requires ≥2 |
| maxReplicas | 10 | 5-20 | Based on load testing |
| CPU target | 70% | 60-80% | Lower = more aggressive scaling |
| Scale up stabilization | 0s | 0-60s | Delay before scaling up |
| Scale down stabilization | 300s | 180-600s | Delay before scaling down |

**Verification:**
```bash
# Check HPA status
kubectl get hpa scrum-poker-backend-hpa -n production

# Monitor scaling events
kubectl describe hpa scrum-poker-backend-hpa -n production | tail -20
```

### Vertical Scaling (Resource Limits)

**When to Use:**
- Pods consistently hitting CPU/memory limits
- OOMKilled errors in pod logs
- CPU throttling affecting performance

**Command:**
```bash
# Edit deployment to increase resource limits
kubectl edit deployment scrum-poker-backend -n production

# Update resources section:
# resources:
#   requests:
#     cpu: "2000m"      # Increase from 1000m
#     memory: "3Gi"     # Increase from 2Gi
#   limits:
#     cpu: "4000m"      # Increase from 2000m
#     memory: "4Gi"     # Increase from 2Gi

# Or use patch
kubectl patch deployment scrum-poker-backend -n production -p '{
  "spec": {
    "template": {
      "spec": {
        "containers": [{
          "name": "scrum-poker-backend",
          "resources": {
            "requests": {"cpu": "2000m", "memory": "3Gi"},
            "limits": {"cpu": "4000m", "memory": "4Gi"}
          }
        }]
      }
    }
  }
}'

# Restart deployment to apply changes
kubectl rollout restart deployment/scrum-poker-backend -n production
```

**Verification:**
```bash
# Check new pods have updated resources
kubectl describe pod -n production -l app=scrum-poker-backend | grep -A 5 "Limits\|Requests"

# Monitor resource usage in Grafana (Infrastructure dashboard)
```

**Caution:**
- Ensure cluster nodes have sufficient capacity
- Update HPA CPU target if CPU requests change
- Test in staging environment first

## Log Access and Analysis

### View Recent Logs

**Command:**
```bash
# View last 100 lines from all pods
kubectl logs -n production -l app=scrum-poker-backend --tail=100

# Follow logs in real-time (all pods)
kubectl logs -n production -l app=scrum-poker-backend --tail=100 -f

# View logs from specific pod
kubectl get pods -n production -l app=scrum-poker-backend
kubectl logs -n production scrum-poker-backend-7d8f9c5b6d-abc12 --tail=500

# View logs from previous container (if pod restarted)
kubectl logs -n production scrum-poker-backend-7d8f9c5b6d-abc12 --previous
```

### Search Logs for Errors

**Command:**
```bash
# Search for ERROR level logs
kubectl logs -n production -l app=scrum-poker-backend --tail=1000 | grep ERROR

# Search for specific exception
kubectl logs -n production -l app=scrum-poker-backend --tail=5000 | grep -i "NullPointerException"

# Search for database errors
kubectl logs -n production -l app=scrum-poker-backend --tail=5000 | grep -i "database\|postgresql\|jdbc"

# Search for WebSocket errors
kubectl logs -n production -l app=scrum-poker-backend --tail=5000 | grep -i "websocket\|stomp\|session"
```

### Filter Logs by Correlation ID

**When to Use:**
- Trace specific user request across multiple log entries
- Debug specific session or transaction

**Command:**
```bash
# Extract correlation ID from user report or error log
# Example: correlationId=f3a2b1c4-5d6e-7f8g-9h0i-1j2k3l4m5n6o

# Filter logs by correlation ID
kubectl logs -n production -l app=scrum-poker-backend --tail=10000 | \
  grep "correlationId=f3a2b1c4-5d6e-7f8g-9h0i-1j2k3l4m5n6o"

# Save filtered logs to file for analysis
kubectl logs -n production -l app=scrum-poker-backend --tail=10000 | \
  grep "correlationId=f3a2b1c4-5d6e-7f8g-9h0i-1j2k3l4m5n6o" > incident_logs.txt
```

### Export Logs for Long-Term Analysis

**Command:**
```bash
# Export last hour of logs
kubectl logs -n production -l app=scrum-poker-backend --since=1h > logs_last_hour.txt

# Export logs from specific time range (if using CloudWatch/Loki)
# For CloudWatch Logs:
aws logs filter-log-events \
  --log-group-name /aws/eks/production/scrum-poker \
  --start-time $(date -u -d '2 hours ago' +%s)000 \
  --end-time $(date -u +%s)000 \
  --filter-pattern "ERROR" \
  --output text > error_logs.txt
```

### Log Levels

| Level | Use Case | Command Filter |
|-------|----------|----------------|
| ERROR | Application errors, exceptions | `grep ERROR` |
| WARN | Warnings, degraded performance | `grep WARN` |
| INFO | Informational, request logs | `grep INFO` |
| DEBUG | Detailed debugging (not in prod) | `grep DEBUG` |

## Service Management

### Rolling Restart (Zero Downtime)

**When to Use:**
- Apply configuration changes from ConfigMap/Secret
- Clear stuck connections or memory leaks
- Recover from transient errors
- Force reload of environment variables

**Command:**
```bash
# Trigger rolling restart
kubectl rollout restart deployment/scrum-poker-backend -n production

# Watch rollout progress
kubectl rollout status deployment/scrum-poker-backend -n production

# Monitor pod replacement
kubectl get pods -n production -l app=scrum-poker-backend -w
```

**Expected Behavior:**
- New pods created one at a time (respecting maxSurge: 1)
- Old pods terminated only after new pods ready (respecting maxUnavailable: 0)
- Zero downtime (requests always served)
- Rollout completes in 2-5 minutes

**Verification:**
```bash
# Verify all pods running new version
kubectl get pods -n production -l app=scrum-poker-backend -o jsonpath='{.items[*].status.containerStatuses[*].restartCount}'
# Expected: All pods show restartCount incremented by 1

# Check health endpoints
curl https://planningpoker.example.com/q/health/live
curl https://planningpoker.example.com/q/health/ready
```

### Force Delete Pod (Last Resort)

**When to Use:**
- Pod stuck in Terminating state
- Pod unresponsive to normal restart
- Emergency recovery only

**Command:**
```bash
# Try graceful delete first
kubectl delete pod scrum-poker-backend-7d8f9c5b6d-abc12 -n production

# If stuck in Terminating, force delete
kubectl delete pod scrum-poker-backend-7d8f9c5b6d-abc12 -n production --grace-period=0 --force

# Verify new pod created by deployment controller
kubectl get pods -n production -l app=scrum-poker-backend
```

**Caution:**
- Force delete can cause data loss or orphaned resources
- Use only when graceful delete fails
- WebSocket connections will drop for users on that pod

### Rollback Deployment

**When to Use:**
- Recent deployment introduced bugs or errors
- Performance degradation after update
- Database migration issues
- Need to revert to stable version quickly

**Command:**
```bash
# View deployment history
kubectl rollout history deployment/scrum-poker-backend -n production

# Example output:
# REVISION  CHANGE-CAUSE
# 1         Initial deployment
# 2         Update to v1.1.0
# 3         Update to v1.2.0

# Rollback to previous version (revision 2)
kubectl rollout undo deployment/scrum-poker-backend -n production

# Or rollback to specific revision
kubectl rollout undo deployment/scrum-poker-backend -n production --to-revision=2

# Monitor rollback
kubectl rollout status deployment/scrum-poker-backend -n production
```

**Verification:**
```bash
# Check running image version
kubectl get deployment scrum-poker-backend -n production -o jsonpath='{.spec.template.spec.containers[0].image}'

# Verify application health
curl https://planningpoker.example.com/q/health/ready

# Check error rate in Grafana
```

**Post-Rollback:**
- Document incident and reason for rollback
- Investigate root cause of failed deployment
- Test fix in staging before redeploying to production

### Pause/Resume Deployment

**When to Use:**
- Apply multiple changes before triggering rollout
- Hold deployment during incident investigation
- Coordinate deployment with database maintenance

**Command:**
```bash
# Pause deployment (prevent rollouts)
kubectl rollout pause deployment/scrum-poker-backend -n production

# Make changes (won't trigger rollout)
kubectl set image deployment/scrum-poker-backend scrum-poker-backend=planning-poker:v1.3.0 -n production
kubectl set env deployment/scrum-poker-backend SOME_CONFIG=newvalue -n production

# Resume deployment (trigger rollout with all changes)
kubectl rollout resume deployment/scrum-poker-backend -n production

# Monitor rollout
kubectl rollout status deployment/scrum-poker-backend -n production
```

## Database Operations

### Database Backup

**Automated Backups (AWS RDS):**
- **Daily automated snapshots:** 7-day retention (configured in RDS)
- **Transaction logs:** Backed up every 5 minutes (point-in-time recovery)
- **Backup window:** 03:00-04:00 UTC (low traffic period)

**Manual Snapshot:**

**When to Use:**
- Before major schema migration
- Before bulk data import/export
- Ad-hoc backup for compliance
- Before testing potentially destructive operations

**Command:**
```bash
# Create manual RDS snapshot
aws rds create-db-snapshot \
  --db-instance-identifier scrumpoker-prod \
  --db-snapshot-identifier manual-backup-$(date +%Y%m%d-%H%M%S) \
  --region us-east-1

# List recent snapshots
aws rds describe-db-snapshots \
  --db-instance-identifier scrumpoker-prod \
  --region us-east-1 \
  --query 'DBSnapshots[*].[DBSnapshotIdentifier,SnapshotCreateTime,Status]' \
  --output table

# Check snapshot status
aws rds describe-db-snapshots \
  --db-snapshot-identifier manual-backup-20260118-120000 \
  --region us-east-1 \
  --query 'DBSnapshots[0].Status'
```

**Verification:**
- [ ] Snapshot status shows "available"
- [ ] Snapshot size matches database size
- [ ] Snapshot listed in AWS console

### Database Restore

**CRITICAL: This operation requires downtime. Test in staging first.**

See [DISASTER_RECOVERY.md](./DISASTER_RECOVERY.md) for detailed restore procedures.

**Quick Reference:**
```bash
# Restore from snapshot (creates NEW RDS instance)
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier scrumpoker-prod-restored \
  --db-snapshot-identifier manual-backup-20260118-120000 \
  --db-instance-class db.t3.large \
  --availability-zone us-east-1a \
  --region us-east-1

# Update application to use new database endpoint
# (Update Kubernetes secret with new DB_JDBC_URL)

# Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production
```

### Database Connection Monitoring

**Command:**
```bash
# Check HikariCP connection pool metrics in Grafana
# Or query Prometheus directly:
kubectl port-forward -n monitoring svc/prometheus 9090:9090

# Open http://localhost:9090 and run query:
# hikaricp_connections_active{application="planning-poker"}
# hikaricp_connections_max{application="planning-poker"}
# hikaricp_connections_idle{application="planning-poker"}
```

**Healthy Connection Pool:**
- Active connections: 5-20 (normal load)
- Idle connections: 5-10
- Max connections: 50 (configured in application)
- Connection wait time: <10ms

### Kill Long-Running Queries

**When to Use:**
- Database connection pool exhausted
- Slow query impacting performance
- Runaway query consuming resources

**Command:**
```bash
# Connect to database
kubectl run -it --rm postgres-client --image=postgres:15 --restart=Never -- \
  psql -h scrumpoker-prod.xxxxx.rds.amazonaws.com -U scrumpoker_app -d scrumpoker

# List active queries
SELECT pid, now() - query_start as duration, state, query
FROM pg_stat_activity
WHERE state = 'active' AND now() - query_start > interval '5 minutes'
ORDER BY duration DESC;

# Kill specific query
SELECT pg_terminate_backend(<pid>);

# Kill all queries from specific application
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE usename = 'scrumpoker_app' AND state = 'active' AND now() - query_start > interval '10 minutes';
```

**Caution:**
- Killing queries may cause application errors
- Monitor application logs after killing queries
- Investigate root cause of slow queries (missing indexes, inefficient queries)

## Redis Operations

### Check Redis Health

**Command:**
```bash
# Port-forward to Redis (if not using ElastiCache directly)
# For ElastiCache, use redis-cli with endpoint

# Using kubectl exec to Redis pod (if running in Kubernetes)
kubectl exec -it -n production <redis-pod-name> -- redis-cli

# Or connect to ElastiCache endpoint
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com -p 6379

# Check Redis info
INFO memory
INFO stats
INFO replication

# Check key count
DBSIZE

# Check memory usage
MEMORY USAGE <key>

# List large keys
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com --bigkeys
```

### Clear Redis Cache (Emergency)

**When to Use:**
- Redis out of memory (OOM errors)
- Corrupted cache data causing errors
- Need to force refresh of all cached data

**CAUTION: This will disrupt active sessions and rate limiting.**

**Command:**
```bash
# Connect to Redis
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com -p 6379

# Flush all keys in current database (usually DB 0)
FLUSHDB

# Or flush all databases
FLUSHALL

# Verify keys cleared
DBSIZE
# Expected: (integer) 0
```

**Impact:**
- **Active WebSocket sessions:** Will be disconnected (users see "Disconnected")
- **Rate limiting:** Reset (users can make requests again)
- **Session cache:** Lost (users need to re-authenticate)
- **Pub/Sub:** Subscriptions lost (reconnect on next message)

**Post-Flush:**
- Monitor application logs for reconnections
- Verify Grafana shows WebSocket reconnections
- Check for OOM errors resolved

### Monitor Redis Memory Usage

**Command:**
```bash
# Check Redis memory usage
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com INFO memory | grep used_memory_human

# Check eviction policy
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com CONFIG GET maxmemory-policy
# Expected: allkeys-lru (evict least recently used keys)

# Monitor eviction rate
redis-cli -h scrumpoker-prod-redis.xxxxx.cache.amazonaws.com INFO stats | grep evicted_keys
```

**Healthy Redis:**
- Memory usage: <80% of max memory
- Eviction rate: <100 keys/second
- Eviction policy: `allkeys-lru`

**If Memory High:**
1. Identify large keys: `redis-cli --bigkeys`
2. Check TTL on keys (rate limit keys should expire in 60s)
3. Consider scaling Redis to larger node type
4. Review WebSocket connection registry for memory leaks

## Secret Rotation

### Rotate Database Password

**When to Use:**
- Every 90 days (security policy)
- Password compromise suspected
- Compliance audit requirement

**Command:**
```bash
# Step 1: Change password in RDS
aws rds modify-db-instance \
  --db-instance-identifier scrumpoker-prod \
  --master-user-password '<NEW_STRONG_PASSWORD>' \
  --apply-immediately \
  --region us-east-1

# Step 2: Update Kubernetes secret
kubectl create secret generic scrum-poker-secrets \
  --from-literal=DB_JDBC_URL='jdbc:postgresql://scrumpoker-prod.xxxxx.us-east-1.rds.amazonaws.com:5432/scrumpoker' \
  --from-literal=DB_USERNAME='scrumpoker_app' \
  --from-literal=DB_PASSWORD='<NEW_STRONG_PASSWORD>' \
  --from-literal=REDIS_URI='redis://scrumpoker-prod-redis.xxxxx.cache.amazonaws.com:6379' \
  --from-file=JWT_PRIVATE_KEY=jwt-private.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public.pem \
  --from-literal=OAUTH_GOOGLE_CLIENT_ID='<GOOGLE_CLIENT_ID>' \
  --from-literal=OAUTH_GOOGLE_CLIENT_SECRET='<GOOGLE_CLIENT_SECRET>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_ID='<MICROSOFT_CLIENT_ID>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_SECRET='<MICROSOFT_CLIENT_SECRET>' \
  -n production \
  --dry-run=client -o yaml | kubectl apply -f -

# Step 3: Restart application to pick up new password
kubectl rollout restart deployment/scrum-poker-backend -n production

# Step 4: Verify application connects with new password
kubectl logs -n production -l app=scrum-poker-backend --tail=50 | grep -i "database\|connection"
```

**Verification:**
- [ ] RDS password change applied successfully
- [ ] Kubernetes secret updated
- [ ] Pods restarted without errors
- [ ] Application health checks passing
- [ ] No database connection errors in logs

### Rotate JWT Signing Keys

**When to Use:**
- Key compromise suspected
- Annual key rotation (security best practice)
- Moving to stronger key size (2048 → 4096 bit)

**Command:**
```bash
# Step 1: Generate new RSA key pair
openssl genpkey -algorithm RSA -out jwt-private-new.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in jwt-private-new.pem -out jwt-public-new.pem

# Step 2: Backup old keys to AWS Secrets Manager
aws secretsmanager create-secret \
  --name scrumpoker/jwt-keys/backup-$(date +%Y%m%d) \
  --secret-string file://jwt-private.pem \
  --region us-east-1

# Step 3: Update Kubernetes secret with new keys
kubectl create secret generic scrum-poker-secrets \
  --from-literal=DB_JDBC_URL='jdbc:postgresql://scrumpoker-prod.xxxxx.us-east-1.rds.amazonaws.com:5432/scrumpoker' \
  --from-literal=DB_USERNAME='scrumpoker_app' \
  --from-literal=DB_PASSWORD='<DB_PASSWORD>' \
  --from-literal=REDIS_URI='redis://scrumpoker-prod-redis.xxxxx.cache.amazonaws.com:6379' \
  --from-file=JWT_PRIVATE_KEY=jwt-private-new.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public-new.pem \
  --from-literal=OAUTH_GOOGLE_CLIENT_ID='<GOOGLE_CLIENT_ID>' \
  --from-literal=OAUTH_GOOGLE_CLIENT_SECRET='<GOOGLE_CLIENT_SECRET>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_ID='<MICROSOFT_CLIENT_ID>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_SECRET='<MICROSOFT_CLIENT_SECRET>' \
  -n production \
  --dry-run=client -o yaml | kubectl apply -f -

# Step 4: Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production
```

**Impact:**
- **Active user sessions:** All users logged out (JWT tokens invalidated)
- **Users must re-authenticate:** Login with OAuth again

**Post-Rotation:**
- Notify users of planned logout (maintenance window)
- Monitor login errors
- Keep old keys backed up for 30 days in case of rollback

### Rotate OAuth2 Client Secrets

**When to Use:**
- Every 90 days (security policy)
- Secret compromise suspected
- Moving to new OAuth application

**Command:**
```bash
# Step 1: Generate new OAuth client secret in provider console
# Google: https://console.cloud.google.com/apis/credentials
# Microsoft: https://portal.azure.com → App registrations

# Step 2: Update Kubernetes secret with new OAuth secrets
kubectl create secret generic scrum-poker-secrets \
  --from-literal=DB_JDBC_URL='jdbc:postgresql://scrumpoker-prod.xxxxx.us-east-1.rds.amazonaws.com:5432/scrumpoker' \
  --from-literal=DB_USERNAME='scrumpoker_app' \
  --from-literal=DB_PASSWORD='<DB_PASSWORD>' \
  --from-literal=REDIS_URI='redis://scrumpoker-prod-redis.xxxxx.cache.amazonaws.com:6379' \
  --from-file=JWT_PRIVATE_KEY=jwt-private.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public.pem \
  --from-literal=OAUTH_GOOGLE_CLIENT_ID='<GOOGLE_CLIENT_ID>' \
  --from-literal=OAUTH_GOOGLE_CLIENT_SECRET='<NEW_GOOGLE_SECRET>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_ID='<MICROSOFT_CLIENT_ID>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_SECRET='<NEW_MICROSOFT_SECRET>' \
  -n production \
  --dry-run=client -o yaml | kubectl apply -f -

# Step 3: Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production

# Step 4: Test OAuth login
# Open browser: https://planningpoker.example.com
# Click "Login with Google" and "Login with Microsoft"
# Verify successful authentication
```

**Verification:**
- [ ] New secrets generated in OAuth provider consoles
- [ ] Kubernetes secret updated
- [ ] Application restarted
- [ ] Google OAuth login working
- [ ] Microsoft OAuth login working
- [ ] No authentication errors in logs

## Performance Tuning

### Adjust Database Connection Pool

**When to Use:**
- Connection pool exhaustion (all connections in use)
- Database connections idle (pool too large)
- Optimize resource usage

**Command:**
```bash
# View current connection pool configuration
kubectl get configmap scrum-poker-config -n production -o yaml | grep -A 5 "quarkus.datasource"

# Edit ConfigMap to adjust pool size
kubectl edit configmap scrum-poker-config -n production

# Update:
# quarkus.datasource.jdbc.max-size=50        # Max connections (default: 20)
# quarkus.datasource.jdbc.min-size=5         # Min connections (default: 2)
# quarkus.datasource.jdbc.acquisition-timeout=10s  # Wait time (default: 5s)

# Restart application to apply changes
kubectl rollout restart deployment/scrum-poker-backend -n production
```

**Recommended Pool Sizes:**

| Environment | Min Connections | Max Connections | Notes |
|-------------|----------------|-----------------|-------|
| Development | 2 | 10 | Single pod |
| Staging | 5 | 20 | 2 pods |
| Production | 5 | 50 | 2-10 pods (limit per pod) |

**Verification:**
```bash
# Monitor connection pool metrics in Grafana
# Open Infrastructure dashboard
# Check: hikaricp_connections_active, hikaricp_connections_idle
```

### Tune JVM Memory Settings

**When to Use:**
- Frequent garbage collection pauses
- OutOfMemoryError in logs
- High JVM heap usage (>85%)

**Command:**
```bash
# Edit deployment to adjust JVM options
kubectl edit deployment scrum-poker-backend -n production

# Update environment variables:
# - name: JAVA_OPTS
#   value: "-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Explanation:
# -Xms1g: Initial heap size (1 GB)
# -Xmx2g: Max heap size (2 GB)
# -XX:+UseG1GC: Use G1 garbage collector (low latency)
# -XX:MaxGCPauseMillis=200: Target max GC pause time (200ms)

# Restart deployment
kubectl rollout restart deployment/scrum-poker-backend -n production
```

**Recommended JVM Settings:**

| Pod Memory Limit | Xms (Initial) | Xmx (Max) | Notes |
|-----------------|---------------|-----------|-------|
| 512Mi | 256m | 384m | Development |
| 1Gi | 512m | 768m | Staging |
| 2Gi | 1g | 1.5g | Production (leave 500MB for non-heap) |
| 4Gi | 2g | 3g | High load |

**Verification:**
```bash
# Monitor JVM memory metrics in Grafana (Infrastructure dashboard)
# Check: jvm_memory_used_bytes, jvm_gc_pause_seconds
```

## Incident Response

### Incident Severity Levels

| Severity | Description | Response Time | Example |
|----------|-------------|---------------|---------|
| SEV1 (Critical) | Complete outage, all users impacted | <15 minutes | Application down, database unavailable |
| SEV2 (High) | Major functionality broken, many users impacted | <30 minutes | OAuth login failing, WebSocket disconnections |
| SEV3 (Medium) | Partial functionality impaired, some users impacted | <2 hours | Slow response times, occasional errors |
| SEV4 (Low) | Minor issue, few users impacted | <8 hours | UI bug, non-critical feature broken |

### Incident Response Workflow

**Step 1: Acknowledge and Assess (2 minutes)**
```bash
# Check Grafana for alerts
kubectl port-forward -n monitoring svc/grafana 3000:3000
# Open: http://localhost:3000

# Check application health
curl https://planningpoker.example.com/q/health/ready

# Check pod status
kubectl get pods -n production -l app=scrum-poker-backend
```

**Step 2: Notify Stakeholders (5 minutes)**
- Post in #incidents Slack channel
- Page on-call DevOps lead (if SEV1/SEV2)
- Create incident ticket in issue tracker

**Step 3: Diagnose Root Cause (10-30 minutes)**
- Check recent deployments: `kubectl rollout history deployment/scrum-poker-backend -n production`
- Review logs: `kubectl logs -n production -l app=scrum-poker-backend --tail=500 | grep ERROR`
- Check metrics: Open Grafana dashboards
- Review [TROUBLESHOOTING_GUIDE.md](./TROUBLESHOOTING_GUIDE.md) for known issues

**Step 4: Implement Fix (variable)**
- Rollback deployment (if recent deploy caused issue)
- Scale pods (if resource exhaustion)
- Restart pods (if transient error)
- Apply hotfix (if code bug identified)

**Step 5: Verify Resolution (5 minutes)**
- Check application health endpoints
- Verify metrics returning to normal
- Test affected functionality
- Monitor for 15 minutes to ensure stability

**Step 6: Post-Incident (24 hours)**
- Document root cause and resolution in incident ticket
- Update [TROUBLESHOOTING_GUIDE.md](./TROUBLESHOOTING_GUIDE.md) if new issue
- Schedule post-mortem (for SEV1/SEV2)
- Implement preventive measures

### Emergency Escalation Path

1. **On-Call Engineer** (PagerDuty) - Immediate
2. **DevOps Lead** (Slack/Email) - 15 minutes
3. **Database Admin** (if database issue) - 30 minutes
4. **Engineering Manager** (if prolonged outage) - 1 hour
5. **CTO/VP Engineering** (if business-critical) - 2 hours

## Support and Resources

**Internal Documentation:**
- [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Deployment procedures
- [MONITORING_GUIDE.md](./MONITORING_GUIDE.md) - Monitoring and alerting
- [TROUBLESHOOTING_GUIDE.md](./TROUBLESHOOTING_GUIDE.md) - Common issues and solutions
- [DISASTER_RECOVERY.md](./DISASTER_RECOVERY.md) - Backup and restore

**External Resources:**
- Kubernetes Docs: https://kubernetes.io/docs/
- AWS RDS Docs: https://docs.aws.amazon.com/rds/
- Prometheus Docs: https://prometheus.io/docs/
- Grafana Docs: https://grafana.com/docs/

**Team Contacts:**
- DevOps Team: #devops Slack channel
- On-Call Rotation: PagerDuty schedule
- Emergency Hotline: See company wiki
