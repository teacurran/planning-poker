# Disaster Recovery Procedures

**Last Updated:** 2026-01-18
**Application:** Planning Poker
**Environment:** Production (AWS EKS)

## Table of Contents

- [Overview](#overview)
- [Recovery Objectives](#recovery-objectives)
- [Backup Strategy](#backup-strategy)
- [Disaster Scenarios](#disaster-scenarios)
- [Database Restore Procedures](#database-restore-procedures)
- [Secret Recovery Procedures](#secret-recovery-procedures)
- [Multi-AZ Failover](#multi-az-failover)
- [Region Failover](#region-failover)
- [DR Testing Schedule](#dr-testing-schedule)
- [Post-Recovery Validation](#post-recovery-validation)

## Overview

This document defines disaster recovery (DR) procedures for the Planning Poker application. It provides step-by-step instructions for recovering from catastrophic failures including data loss, regional outages, and infrastructure failures.

**Target Audience:**
- DevOps lead
- Database administrators
- CTO/Engineering VP
- Disaster recovery team

**When to Use This Guide:**
- Complete database loss or corruption
- Regional AWS outage (us-east-1 unavailable)
- Critical data loss requiring point-in-time recovery
- Security incident requiring secret rotation
- Quarterly DR drill exercises

**Related Documentation:**
- [OPERATIONS_RUNBOOK.md](./OPERATIONS_RUNBOOK.md) - Day-to-day operations
- [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Deployment procedures
- [TROUBLESHOOTING_GUIDE.md](./TROUBLESHOOTING_GUIDE.md) - Common issues

## Recovery Objectives

### RTO (Recovery Time Objective): 4 Hours

**Definition:** Maximum acceptable time to restore service after disaster.

**Measurement:** From incident detection to full service restoration with all features operational.

**Breakdown:**

| Phase | Task | Duration | Cumulative |
|-------|------|----------|------------|
| 1. Detection & Assessment | Confirm disaster, assess scope | 30 min | 30 min |
| 2. Team Mobilization | Notify team, assign roles | 15 min | 45 min |
| 3. Database Restore | Restore RDS from snapshot | 60 min | 1h 45min |
| 4. Application Deployment | Deploy application to cluster | 30 min | 2h 15min |
| 5. DNS/Traffic Cutover | Update DNS, verify routing | 15 min | 2h 30min |
| 6. Validation & Testing | Smoke tests, health checks | 30 min | 3h |
| 7. Buffer for Issues | Contingency time | 60 min | **4h** |

**SLA Impact:**
- RTO met: No SLA breach
- RTO missed: Calculate downtime credit (99.5% uptime = 3.6 hours/month allowed)

### RPO (Recovery Point Objective): 1 Hour

**Definition:** Maximum acceptable data loss after disaster.

**Implementation:**
- **Hourly RDS automated snapshots** (worst-case: 1 hour data loss)
- **Transaction logs backed up every 5 minutes** (best-case: <5 min data loss with point-in-time recovery)

**Data Loss Scenarios:**

| Scenario | RPO Achieved | Data Loss | Recovery Method |
|----------|--------------|-----------|-----------------|
| Database corruption | <5 minutes | Minimal | Point-in-time recovery from transaction logs |
| RDS instance failure | 0 minutes | None | Multi-AZ automatic failover |
| Regional disaster | 1 hour | Last hourly snapshot | Cross-region snapshot restore |
| Snapshot corruption | 24 hours | Last daily snapshot | Daily snapshot restore |

## Backup Strategy

### Database Backups (Amazon RDS)

**Automated Snapshots:**

| Type | Frequency | Retention | Storage | Recovery Time |
|------|-----------|-----------|---------|---------------|
| Hourly snapshots | Every hour | 7 days | RDS Multi-AZ (same region) | 30-60 minutes |
| Daily snapshots | 03:00 UTC daily | 30 days | RDS Multi-AZ (same region) | 30-60 minutes |
| Monthly snapshots | 1st of month | 90 days | S3 (cross-region: us-west-2) | 1-2 hours |
| Transaction logs | Every 5 minutes | 7 days | RDS Multi-AZ (same region) | 10-20 minutes |

**Manual Snapshots:**

Created before:
- Major schema migrations (Flyway migrations)
- Bulk data imports/exports
- Application upgrades with data model changes
- Compliance audits

**Retention:** Indefinite (delete manually when no longer needed)

**Verification:**
```bash
# List all snapshots
aws rds describe-db-snapshots \
  --db-instance-identifier scrumpoker-prod \
  --region us-east-1 \
  --query 'DBSnapshots[*].[DBSnapshotIdentifier,SnapshotCreateTime,Status,AllocatedStorage]' \
  --output table

# Verify latest automated snapshot
aws rds describe-db-snapshots \
  --db-instance-identifier scrumpoker-prod \
  --snapshot-type automated \
  --region us-east-1 \
  --query 'DBSnapshots[0].[DBSnapshotIdentifier,SnapshotCreateTime,Status]'

# Expected: Snapshot created within last hour
```

### Application Secrets Backup

**Critical Secrets to Backup:**

| Secret | Storage Location | Backup Frequency | Recovery Priority |
|--------|------------------|------------------|-------------------|
| JWT Private Key (RSA 2048-bit) | AWS Secrets Manager | On creation/rotation | CRITICAL (P0) |
| Database Credentials | AWS Secrets Manager | On rotation (90 days) | CRITICAL (P0) |
| OAuth Client Secrets | AWS Secrets Manager | On rotation (90 days) | HIGH (P1) |
| Redis Connection String | AWS Secrets Manager | On change | MEDIUM (P2) |
| TLS Certificates | AWS Certificate Manager | Auto-renewed | LOW (P3 - auto-managed) |

**Backup Procedure (JWT Private Key - CRITICAL):**

```bash
# After generating new JWT key pair, immediately backup to AWS Secrets Manager
aws secretsmanager create-secret \
  --name scrumpoker/jwt-private-key-$(date +%Y%m%d) \
  --description "JWT private key backup - created $(date)" \
  --secret-string file://jwt-private.pem \
  --region us-east-1

# Verify secret created
aws secretsmanager list-secrets \
  --region us-east-1 \
  --filters Key=name,Values=scrumpoker/jwt-private-key \
  --query 'SecretList[*].[Name,CreatedDate]' \
  --output table

# Tag as production secret
aws secretsmanager tag-resource \
  --secret-id scrumpoker/jwt-private-key-$(date +%Y%m%d) \
  --tags Key=Environment,Value=production Key=Application,Value=planning-poker \
  --region us-east-1
```

**CRITICAL:** Store corresponding public key (`jwt-public.pem`) alongside private key for reference.

### Export Files and User Data (S3)

**Backup Strategy:**
- **Primary Storage:** S3 bucket `scrumpoker-exports-prod` (us-east-1)
- **Cross-Region Replication:** Enabled to `scrumpoker-exports-prod-backup` (us-west-2)
- **Versioning:** Enabled (retain 30 days of versions)
- **Lifecycle Policy:** Transition to Glacier after 90 days, delete after 365 days

**Verification:**
```bash
# Check S3 bucket versioning enabled
aws s3api get-bucket-versioning --bucket scrumpoker-exports-prod

# Check cross-region replication status
aws s3api get-bucket-replication --bucket scrumpoker-exports-prod

# Verify files replicated to backup region
aws s3 ls s3://scrumpoker-exports-prod-backup/ --region us-west-2
```

### Kubernetes Manifests Backup

**Backup Strategy:**
- **Primary Storage:** Git repository (GitHub)
- **Backup Storage:** S3 bucket `scrumpoker-k8s-manifests-backup`
- **Frequency:** On every commit (automated via CI/CD)

**Manual Backup:**
```bash
# Export current Kubernetes manifests
kubectl get all -n production -o yaml > k8s-production-$(date +%Y%m%d).yaml
kubectl get configmap -n production -o yaml >> k8s-production-$(date +%Y%m%d).yaml
kubectl get secret -n production -o yaml >> k8s-production-$(date +%Y%m%d).yaml

# Upload to S3
aws s3 cp k8s-production-$(date +%Y%m%d).yaml s3://scrumpoker-k8s-manifests-backup/ \
  --region us-east-1
```

## Disaster Scenarios

### Scenario 1: Database Corruption or Accidental Data Deletion

**Severity:** CRITICAL (SEV1)
**RTO:** 2 hours
**RPO:** <5 minutes (point-in-time recovery) or 1 hour (snapshot)

**Symptoms:**
- Reports of missing data (sessions, users, votes)
- Database integrity check failures
- Application errors reading/writing data

**Recovery Procedure:** See [Database Restore Procedures](#database-restore-procedures) below.

### Scenario 2: RDS Instance Failure

**Severity:** CRITICAL (SEV1)
**RTO:** 5-10 minutes (automatic Multi-AZ failover)
**RPO:** 0 minutes (synchronous replication)

**Symptoms:**
- Database connection errors
- Health check shows database DOWN
- AWS RDS console shows instance unavailable

**Recovery Procedure:** See [Multi-AZ Failover](#multi-az-failover) below.

**Note:** This is typically automatic. Manual intervention only if failover fails.

### Scenario 3: Regional Disaster (AWS us-east-1 Outage)

**Severity:** CRITICAL (SEV1)
**RTO:** 4 hours
**RPO:** 1 hour (last hourly snapshot)

**Symptoms:**
- Complete AWS region unavailable
- Cannot reach EKS cluster, RDS, ElastiCache
- AWS status page reports regional outage

**Recovery Procedure:** See [Region Failover](#region-failover) below.

### Scenario 4: Kubernetes Cluster Failure

**Severity:** CRITICAL (SEV1)
**RTO:** 2 hours
**RPO:** 0 minutes (database unaffected)

**Symptoms:**
- Cannot kubectl to cluster
- All pods unreachable
- EKS control plane unavailable

**Recovery Procedure:**

1. **Verify Cluster Status:**
   ```bash
   aws eks describe-cluster --name scrumpoker-prod --region us-east-1
   # Check cluster status and health
   ```

2. **If Cluster Unrecoverable:**
   - Create new EKS cluster
   - Deploy application from Git (see DEPLOYMENT_GUIDE.md)
   - Point DNS to new cluster ALB
   - Verify database connectivity (database unaffected)

### Scenario 5: Secret Compromise (JWT Keys, OAuth Secrets)

**Severity:** HIGH (SEV2)
**RTO:** 1 hour
**RPO:** N/A (security incident)

**Symptoms:**
- Suspicious authentication activity
- Unauthorized access to user accounts
- Security alert from monitoring

**Recovery Procedure:** See [Secret Recovery Procedures](#secret-recovery-procedures) below.

## Database Restore Procedures

### Point-in-Time Recovery (RPO <5 minutes)

**When to Use:**
- Accidental data deletion or corruption
- Need to recover to specific timestamp
- Transaction logs available (last 7 days)

**Procedure:**

**Step 1: Identify Recovery Point**
```bash
# Determine target recovery time (when data was last good)
# Example: Restore to 2026-01-18 10:30:00 UTC

TARGET_TIME="2026-01-18T10:30:00Z"
```

**Step 2: Create New RDS Instance from Point-in-Time**
```bash
# Restore to new RDS instance (do NOT overwrite production)
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier scrumpoker-prod \
  --target-db-instance-identifier scrumpoker-prod-restored-$(date +%Y%m%d-%H%M) \
  --restore-time "$TARGET_TIME" \
  --db-instance-class db.t3.large \
  --multi-az \
  --region us-east-1

# Monitor restore progress (takes 30-60 minutes)
aws rds describe-db-instances \
  --db-instance-identifier scrumpoker-prod-restored-$(date +%Y%m%d-%H%M) \
  --region us-east-1 \
  --query 'DBInstances[0].[DBInstanceStatus,PercentProgress]'

# Wait for status: "available"
```

**Step 3: Verify Restored Data**
```bash
# Get endpoint of restored instance
RESTORED_ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier scrumpoker-prod-restored-$(date +%Y%m%d-%H%M) \
  --region us-east-1 \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text)

# Connect to restored database and verify data
kubectl run -it --rm postgres-client --image=postgres:15 --restart=Never -- \
  psql -h $RESTORED_ENDPOINT -U scrumpoker_app -d scrumpoker

# Run verification queries:
# SELECT COUNT(*) FROM sessions; -- Check session count
# SELECT * FROM sessions WHERE created_at > '2026-01-18 10:00:00' ORDER BY created_at DESC LIMIT 10;
# \q
```

**Step 4: Cutover to Restored Database**

**CRITICAL: This causes downtime. Schedule maintenance window.**

```bash
# Option A: Update application to use restored database (RECOMMENDED)
# Update Kubernetes secret with new endpoint
kubectl create secret generic scrum-poker-secrets \
  --from-literal=DB_JDBC_URL="jdbc:postgresql://$RESTORED_ENDPOINT:5432/scrumpoker" \
  --from-literal=DB_USERNAME='scrumpoker_app' \
  --from-literal=DB_PASSWORD='<DB_PASSWORD>' \
  --from-literal=REDIS_URI='redis://scrumpoker-prod-redis.xxxxx.cache.amazonaws.com:6379' \
  --from-file=JWT_PRIVATE_KEY=jwt-private.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public.pem \
  --from-literal=OAUTH_GOOGLE_CLIENT_ID='<GOOGLE_CLIENT_ID>' \
  --from-literal=OAUTH_GOOGLE_CLIENT_SECRET='<GOOGLE_CLIENT_SECRET>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_ID='<MICROSOFT_CLIENT_ID>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_SECRET='<MICROSOFT_CLIENT_SECRET>' \
  -n production \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production

# Verify application connects to new database
kubectl logs -n production -l app=scrum-poker-backend --tail=50 | grep -i "database\|connection"
```

```bash
# Option B: Rename RDS instances (requires downtime)
# 1. Delete original production database (AFTER verifying backup!)
# 2. Rename restored instance to production name
# This option is more complex and not recommended
```

**Step 5: Post-Recovery Validation**

See [Post-Recovery Validation](#post-recovery-validation) below.

### Snapshot Restore (RPO 1 Hour)

**When to Use:**
- Point-in-time recovery not available (logs older than 7 days)
- Need to restore from specific snapshot (pre-migration backup)
- Transaction logs corrupted

**Procedure:**

**Step 1: Select Snapshot**
```bash
# List available snapshots
aws rds describe-db-snapshots \
  --db-instance-identifier scrumpoker-prod \
  --region us-east-1 \
  --query 'DBSnapshots[*].[DBSnapshotIdentifier,SnapshotCreateTime,Status]' \
  --output table

# Choose snapshot closest to desired recovery point
SNAPSHOT_ID="rds:scrumpoker-prod-2026-01-18-10-00"
```

**Step 2: Restore from Snapshot**
```bash
# Restore to new RDS instance
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier scrumpoker-prod-restored-$(date +%Y%m%d-%H%M) \
  --db-snapshot-identifier $SNAPSHOT_ID \
  --db-instance-class db.t3.large \
  --multi-az \
  --region us-east-1

# Monitor restore progress
aws rds describe-db-instances \
  --db-instance-identifier scrumpoker-prod-restored-$(date +%Y%m%d-%H%M) \
  --region us-east-1 \
  --query 'DBInstances[0].[DBInstanceStatus]'

# Wait for status: "available" (30-60 minutes)
```

**Step 3: Follow Steps 3-5 from Point-in-Time Recovery above**

## Secret Recovery Procedures

### Recover JWT Private Key

**When to Use:**
- JWT private key lost or corrupted
- Kubernetes secret deleted
- Security incident requiring key rotation

**Procedure:**

**Option A: Recover from AWS Secrets Manager (if backed up)**

```bash
# List available JWT key backups
aws secretsmanager list-secrets \
  --region us-east-1 \
  --filters Key=name,Values=scrumpoker/jwt-private-key \
  --query 'SecretList[*].[Name,CreatedDate]' \
  --output table

# Retrieve latest JWT private key backup
aws secretsmanager get-secret-value \
  --secret-id scrumpoker/jwt-private-key-20260115 \
  --region us-east-1 \
  --query 'SecretString' \
  --output text > jwt-private-recovered.pem

# Extract public key from private key
openssl rsa -pubout -in jwt-private-recovered.pem -out jwt-public-recovered.pem

# Verify key validity
openssl rsa -in jwt-private-recovered.pem -check
# Expected: RSA key ok
```

**Option B: Generate New Key Pair (if backup unavailable)**

**CRITICAL: This will invalidate all existing JWT tokens. All users logged out.**

```bash
# Generate new RSA key pair
openssl genpkey -algorithm RSA -out jwt-private-new.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in jwt-private-new.pem -out jwt-public-new.pem

# Backup to AWS Secrets Manager IMMEDIATELY
aws secretsmanager create-secret \
  --name scrumpoker/jwt-private-key-$(date +%Y%m%d) \
  --description "JWT private key backup - created $(date)" \
  --secret-string file://jwt-private-new.pem \
  --region us-east-1
```

**Step 2: Update Kubernetes Secret**

```bash
# Update secret with recovered/new JWT keys
kubectl create secret generic scrum-poker-secrets \
  --from-literal=DB_JDBC_URL='jdbc:postgresql://scrumpoker-prod.xxxxx.us-east-1.rds.amazonaws.com:5432/scrumpoker' \
  --from-literal=DB_USERNAME='scrumpoker_app' \
  --from-literal=DB_PASSWORD='<DB_PASSWORD>' \
  --from-literal=REDIS_URI='redis://scrumpoker-prod-redis.xxxxx.cache.amazonaws.com:6379' \
  --from-file=JWT_PRIVATE_KEY=jwt-private-recovered.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public-recovered.pem \
  --from-literal=OAUTH_GOOGLE_CLIENT_ID='<GOOGLE_CLIENT_ID>' \
  --from-literal=OAUTH_GOOGLE_CLIENT_SECRET='<GOOGLE_CLIENT_SECRET>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_ID='<MICROSOFT_CLIENT_ID>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_SECRET='<MICROSOFT_CLIENT_SECRET>' \
  -n production \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production
```

**Step 3: Notify Users**

If new key pair generated:
- Post announcement: "All users logged out for security maintenance"
- Users must re-authenticate via OAuth

### Recover OAuth Secrets

**When to Use:**
- OAuth client secrets lost
- Kubernetes secret deleted

**Procedure:**

**Step 1: Retrieve Secrets from OAuth Providers**

**Google:**
1. Navigate to: https://console.cloud.google.com/apis/credentials
2. Select OAuth 2.0 Client ID for Planning Poker
3. If secret lost, generate new client secret
4. Copy client ID and client secret

**Microsoft:**
1. Navigate to: https://portal.azure.com → Azure Active Directory → App registrations
2. Select Planning Poker application
3. Navigate to "Certificates & secrets"
4. Generate new client secret (cannot retrieve existing secret)
5. Copy client ID and client secret

**Step 2: Update Kubernetes Secret**

```bash
# Update secret with OAuth credentials
kubectl create secret generic scrum-poker-secrets \
  --from-literal=DB_JDBC_URL='jdbc:postgresql://scrumpoker-prod.xxxxx.us-east-1.rds.amazonaws.com:5432/scrumpoker' \
  --from-literal=DB_USERNAME='scrumpoker_app' \
  --from-literal=DB_PASSWORD='<DB_PASSWORD>' \
  --from-literal=REDIS_URI='redis://scrumpoker-prod-redis.xxxxx.cache.amazonaws.com:6379' \
  --from-file=JWT_PRIVATE_KEY=jwt-private.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public.pem \
  --from-literal=OAUTH_GOOGLE_CLIENT_ID='<RECOVERED_GOOGLE_CLIENT_ID>' \
  --from-literal=OAUTH_GOOGLE_CLIENT_SECRET='<RECOVERED_GOOGLE_CLIENT_SECRET>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_ID='<RECOVERED_MICROSOFT_CLIENT_ID>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_SECRET='<RECOVERED_MICROSOFT_CLIENT_SECRET>' \
  -n production \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart application
kubectl rollout restart deployment/scrum-poker-backend -n production
```

**Step 3: Verify OAuth Login**

```bash
# Test Google OAuth login
# Open: https://planningpoker.example.com
# Click "Login with Google"
# Expected: Successful authentication

# Test Microsoft OAuth login
# Click "Login with Microsoft"
# Expected: Successful authentication
```

## Multi-AZ Failover

**Scenario:** Primary RDS instance fails in Availability Zone A.

**Automatic Failover (No Manual Intervention Required):**

Amazon RDS Multi-AZ automatically performs failover:
1. **Detection:** RDS detects primary instance failure (30-60 seconds)
2. **DNS Update:** RDS updates DNS CNAME to point to standby instance (1-2 minutes)
3. **Application Reconnect:** Application reconnects to new primary (automatic)

**Total Failover Time:** 2-5 minutes

**Monitoring Failover:**

```bash
# Check RDS events for failover
aws rds describe-events \
  --source-identifier scrumpoker-prod \
  --source-type db-instance \
  --region us-east-1 \
  --query 'Events[*].[Date,Message]' \
  --output table

# Check current primary AZ
aws rds describe-db-instances \
  --db-instance-identifier scrumpoker-prod \
  --region us-east-1 \
  --query 'DBInstances[0].[AvailabilityZone,SecondaryAvailabilityZone]'
```

**Post-Failover Actions:**

1. **Verify Application Health:**
   ```bash
   curl https://planningpoker.example.com/q/health/ready
   # Expected: {"status":"UP"}
   ```

2. **Check Application Logs:**
   ```bash
   kubectl logs -n production -l app=scrum-poker-backend --tail=100 | grep -i "database\|connection"
   # Expected: Connection re-established messages
   ```

3. **Monitor Metrics:**
   - Check Grafana Infrastructure dashboard
   - Verify database query latency normal
   - Verify no spike in errors

4. **Document Incident:**
   - Record failover time and duration
   - Document any application impact
   - Create post-mortem if needed

## Region Failover

**Scenario:** Complete AWS us-east-1 region outage.

**CRITICAL: This requires manual intervention and causes downtime (RTO: 4 hours).**

**Pre-Requisites:**
- Standby EKS cluster in us-west-2 (recommended but optional)
- Cross-region RDS snapshot replication enabled
- S3 cross-region replication enabled

**Procedure:**

**Step 1: Declare Regional Disaster (15 minutes)**

```bash
# Verify regional outage on AWS Status Page
# https://health.aws.amazon.com/health/status

# Confirm all services unavailable in us-east-1:
# - EKS cluster unreachable
# - RDS instance unreachable
# - ElastiCache unreachable

# Mobilize disaster recovery team
# Notify stakeholders of estimated downtime (4 hours)
```

**Step 2: Provision Infrastructure in us-west-2 (90 minutes)**

**Option A: Use Pre-Provisioned Standby Cluster (30 minutes)**

If standby cluster exists:
```bash
# Switch kubectl context to us-west-2 cluster
kubectl config use-context scrumpoker-standby-uswest2

# Verify cluster healthy
kubectl get nodes
# Expected: Nodes ready
```

**Option B: Create New EKS Cluster (90 minutes)**

If no standby cluster:
```bash
# Create new EKS cluster in us-west-2
# Use infrastructure-as-code (Terraform/CloudFormation) or eksctl
eksctl create cluster \
  --name scrumpoker-prod-uswest2 \
  --region us-west-2 \
  --nodegroup-name standard-workers \
  --node-type t3.large \
  --nodes 3 \
  --nodes-min 2 \
  --nodes-max 10

# This takes 60-90 minutes
```

**Step 3: Restore Database in us-west-2 (60 minutes)**

```bash
# Copy latest snapshot from us-east-1 to us-west-2 (if not already replicated)
aws rds copy-db-snapshot \
  --source-db-snapshot-identifier arn:aws:rds:us-east-1:ACCOUNT_ID:snapshot:scrumpoker-prod-2026-01-18-10-00 \
  --target-db-snapshot-identifier scrumpoker-prod-dr-$(date +%Y%m%d) \
  --region us-west-2

# Restore RDS instance in us-west-2
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier scrumpoker-prod-uswest2 \
  --db-snapshot-identifier scrumpoker-prod-dr-$(date +%Y%m%d) \
  --db-instance-class db.t3.large \
  --multi-az \
  --region us-west-2

# Wait for status: "available" (30-60 minutes)
aws rds describe-db-instances \
  --db-instance-identifier scrumpoker-prod-uswest2 \
  --region us-west-2 \
  --query 'DBInstances[0].[DBInstanceStatus]'
```

**Step 4: Deploy Application to us-west-2 (30 minutes)**

```bash
# Get new database endpoint
DB_ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier scrumpoker-prod-uswest2 \
  --region us-west-2 \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text)

# Create namespace
kubectl create namespace production

# Create secrets (with us-west-2 database endpoint)
kubectl create secret generic scrum-poker-secrets \
  --from-literal=DB_JDBC_URL="jdbc:postgresql://$DB_ENDPOINT:5432/scrumpoker" \
  --from-literal=DB_USERNAME='scrumpoker_app' \
  --from-literal=DB_PASSWORD='<DB_PASSWORD>' \
  --from-literal=REDIS_URI='redis://scrumpoker-prod-redis-uswest2.xxxxx.cache.amazonaws.com:6379' \
  --from-file=JWT_PRIVATE_KEY=jwt-private.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public.pem \
  --from-literal=OAUTH_GOOGLE_CLIENT_ID='<GOOGLE_CLIENT_ID>' \
  --from-literal=OAUTH_GOOGLE_CLIENT_SECRET='<GOOGLE_CLIENT_SECRET>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_ID='<MICROSOFT_CLIENT_ID>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_SECRET='<MICROSOFT_CLIENT_SECRET>' \
  -n production

# Deploy application
cd infra/kubernetes
kubectl apply -k overlays/production

# Wait for deployment
kubectl rollout status deployment/scrum-poker-backend -n production
```

**Step 5: Update DNS (15 minutes)**

```bash
# Get new ALB address in us-west-2
ALB_ADDRESS=$(kubectl get ingress scrum-poker-backend -n production -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

# Update DNS CNAME to point to new ALB
aws route53 change-resource-record-sets \
  --hosted-zone-id <ZONE_ID> \
  --change-batch '{
    "Changes": [{
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "planningpoker.example.com",
        "Type": "CNAME",
        "TTL": 60,
        "ResourceRecords": [{"Value": "'$ALB_ADDRESS'"}]
      }
    }]
  }'

# DNS propagation: 5-15 minutes
```

**Step 6: Verify Service Restoration (30 minutes)**

See [Post-Recovery Validation](#post-recovery-validation) below.

**Step 7: Communicate Recovery**

- Notify users service restored
- Post incident report summary
- Schedule detailed post-mortem

## DR Testing Schedule

**Quarterly DR Drills (every 3 months):**

| Quarter | Test Scenario | Scope | Duration |
|---------|---------------|-------|----------|
| Q1 | Database point-in-time recovery | Restore to test environment | 2 hours |
| Q2 | Multi-AZ failover simulation | Force failover in staging | 1 hour |
| Q3 | Regional failover (full DR) | Failover to us-west-2 (test env) | 4 hours |
| Q4 | Secret recovery | Rotate all secrets | 2 hours |

**Test Procedure:**

1. **Schedule Test:** Notify team 1 week in advance
2. **Execute Test:** Follow DR procedures (use test/staging environment)
3. **Document Results:** Record actual RTO/RPO achieved
4. **Identify Gaps:** Document issues encountered
5. **Update Procedures:** Revise DR guide based on learnings
6. **Post-Test Review:** Share results with team

**Success Criteria:**
- RTO <4 hours (actual recovery time)
- RPO <1 hour (actual data loss)
- All team members know their roles
- DR procedures accurate and up-to-date

## Post-Recovery Validation

**Execute these checks after ANY disaster recovery procedure.**

### Step 1: Health Check Endpoints (2 minutes)

```bash
# Check liveness
curl https://planningpoker.example.com/q/health/live
# Expected: {"status":"UP"}

# Check readiness
curl https://planningpoker.example.com/q/health/ready
# Expected: {"status":"UP","checks":[{"name":"Database","status":"UP"},...]}

# Check metrics endpoint
curl https://planningpoker.example.com/q/metrics
# Expected: Prometheus metrics output
```

### Step 2: Database Connectivity (5 minutes)

```bash
# Connect to database
kubectl run -it --rm postgres-client --image=postgres:15 --restart=Never -- \
  psql -h <DB_ENDPOINT> -U scrumpoker_app -d scrumpoker

# Run verification queries:
SELECT COUNT(*) FROM sessions;
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM votes;

# Check most recent data
SELECT * FROM sessions ORDER BY created_at DESC LIMIT 5;

# Exit
\q
```

### Step 3: Smoke Test - User Journey (10 minutes)

**End-to-End Test:**

1. **Navigate to Application:** `https://planningpoker.example.com`
2. **Login with OAuth:** Click "Login with Google"
   - Expected: Successful authentication
3. **Create Session:** Create new planning poker room
   - Expected: Session created, session ID generated
4. **Join as Second User:** Open incognito window, join session
   - Expected: User joins successfully
5. **Cast Votes:** Both users vote on story
   - Expected: Votes registered in real-time
6. **Reveal Votes:** Moderator reveals votes
   - Expected: Votes shown to all users
7. **Export Results:** Export session to CSV
   - Expected: CSV file downloads successfully

**Verification:**
- [ ] All steps completed without errors
- [ ] WebSocket real-time updates working
- [ ] Data persisted in database

### Step 4: Monitoring Verification (5 minutes)

```bash
# Port-forward Grafana
kubectl port-forward -n monitoring svc/grafana 3000:3000

# Open Grafana: http://localhost:3000
# Check dashboards:
# 1. Application Overview - Request rate > 0, error rate = 0%
# 2. Infrastructure - All pods running, metrics collecting
# 3. Business Metrics - Data matches expected (sessions, votes)
```

**Verification:**
- [ ] All dashboards loading data
- [ ] No alerts firing
- [ ] Metrics within normal baselines

### Step 5: Performance Test (10 minutes)

```bash
# Run light load test (simulate 10 concurrent users)
# Using Apache Bench or similar tool
ab -n 100 -c 10 https://planningpoker.example.com/api/sessions

# Check results:
# - Success rate: 100%
# - Mean response time: <500ms
# - No errors

# Check Grafana for latency spike
# P95 latency should be <1 second
```

### Step 6: Documentation (30 minutes)

**Create Incident Report:**

| Field | Value |
|-------|-------|
| Incident ID | INC-YYYY-MM-DD-XXXX |
| Date/Time | YYYY-MM-DD HH:MM UTC |
| Severity | SEV1/SEV2/SEV3 |
| Scenario | Database failure / Regional disaster / etc. |
| Root Cause | Brief description |
| Detection Time | HH:MM UTC |
| Recovery Start | HH:MM UTC |
| Service Restored | HH:MM UTC |
| Actual RTO | X hours Y minutes |
| Actual RPO | X minutes (data loss) |
| Impact | Number of users affected, services down |
| Recovery Procedure | Database restore / Failover / etc. |
| Lessons Learned | What went well, what could improve |
| Action Items | Follow-up tasks to prevent recurrence |

**Share with Team:**
- Post incident report to #incidents channel
- Schedule post-mortem (SEV1/SEV2 only)
- Update DR procedures if needed

## Emergency Contacts

| Role | Contact | Escalation Time |
|------|---------|----------------|
| On-Call Engineer | PagerDuty rotation | Immediate |
| DevOps Lead | devops-lead@example.com | 15 minutes |
| Database Admin | dba@example.com | 30 minutes |
| CTO/Engineering VP | cto@example.com | 1 hour |
| AWS Support (Enterprise) | AWS Console → Support | 15 minutes (SEV1) |

## Support and Resources

**Related Documentation:**
- [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Deployment procedures
- [OPERATIONS_RUNBOOK.md](./OPERATIONS_RUNBOOK.md) - Day-to-day operations
- [TROUBLESHOOTING_GUIDE.md](./TROUBLESHOOTING_GUIDE.md) - Common issues

**External Resources:**
- AWS RDS Backup: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_CommonTasks.BackupRestore.html
- AWS Disaster Recovery: https://docs.aws.amazon.com/whitepapers/latest/disaster-recovery-workloads-on-aws/
- Kubernetes Disaster Recovery: https://kubernetes.io/docs/tasks/administer-cluster/

**AWS Support:**
- Enterprise Support Plan: 15-minute response for SEV1
- Support Portal: https://console.aws.amazon.com/support/home
