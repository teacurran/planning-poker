# Deployment Guide

**Last Updated:** 2026-01-18
**Application Version:** 1.0.0
**Target Environment:** AWS EKS (Production)

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Pre-Deployment Checklist](#pre-deployment-checklist)
- [Deployment Workflow](#deployment-workflow)
- [Post-Deployment Verification](#post-deployment-verification)
- [Rollback Procedures](#rollback-procedures)
- [Environment-Specific Configurations](#environment-specific-configurations)
- [Troubleshooting Deployment Issues](#troubleshooting-deployment-issues)

## Overview

This guide provides step-by-step instructions for deploying the Planning Poker application to production on AWS EKS. It is designed for DevOps engineers and new team members who need to deploy or update the application.

**Deployment Architecture:**
- **Cloud Platform:** AWS (Amazon Web Services)
- **Compute:** Amazon EKS (Elastic Kubernetes Service)
- **Database:** Amazon RDS PostgreSQL 15+ (Multi-AZ)
- **Cache:** Amazon ElastiCache for Redis (cluster mode)
- **Load Balancing:** AWS Application Load Balancer (ALB)
- **Deployment Strategy:** Rolling updates (zero downtime)

For detailed Kubernetes manifest structure and resource specifications, see [infra/kubernetes/README.md](../../infra/kubernetes/README.md).

## Prerequisites

### Required Tools

Verify you have the following tools installed:

```bash
# Check kubectl version (required: >=1.25)
kubectl version --client

# Check AWS CLI version (required: >=2.0)
aws --version

# Check Docker version (required: >=20.10)
docker --version

# Check kustomize version (required: >=4.0)
kustomize version

# Optional: helm for monitoring stack
helm version
```

**Installation Links:**
- kubectl: https://kubernetes.io/docs/tasks/tools/
- AWS CLI: https://aws.amazon.com/cli/
- Docker: https://docs.docker.com/get-docker/
- kustomize: https://kubectl.docs.kubernetes.io/installation/kustomize/

### Required Access

- [ ] AWS account access with EKS, RDS, ElastiCache permissions
- [ ] AWS credentials configured (`aws configure` or environment variables)
- [ ] Kubernetes cluster access (`kubectl` configured for target cluster)
- [ ] Docker registry access (AWS ECR or Docker Hub)
- [ ] OAuth2 client credentials (Google, Microsoft Azure AD)
- [ ] PagerDuty/Slack webhook URLs for alerting (optional)

### Infrastructure Requirements

- [ ] **EKS Cluster:** Running with at least 2 nodes across multiple AZs
- [ ] **RDS PostgreSQL:** Instance running, database created, credentials available
- [ ] **ElastiCache Redis:** Cluster running, endpoint available
- [ ] **S3 Bucket:** Created for export files and backups
- [ ] **ACM Certificate:** SSL/TLS certificate for domain (e.g., `*.planningpoker.example.com`)
- [ ] **ALB Ingress Controller:** Installed on EKS cluster
- [ ] **DNS:** Domain configured, ready to point to ALB

## Pre-Deployment Checklist

Run these validation steps before deploying to ensure infrastructure is ready.

### Step 1: Verify Kubernetes Cluster Health

```bash
# Check cluster info
kubectl cluster-info

# Check node status
kubectl get nodes

# Expected: All nodes show status "Ready"
```

**Verification:**
- [ ] All cluster nodes show status "Ready"
- [ ] No nodes in NotReady or Unknown state
- [ ] At least 2 nodes available for Multi-AZ deployment
- [ ] Nodes have sufficient resources (CPU: 4+ cores, Memory: 8+ GB per node)

**If Failed:** See [Troubleshooting Deployment Issues](#troubleshooting-deployment-issues) → "Kubernetes Node Failures"

### Step 2: Verify Infrastructure Services

```bash
# Check RDS database
aws rds describe-db-instances \
  --db-instance-identifier scrumpoker-prod \
  --query 'DBInstances[0].[DBInstanceStatus,Endpoint.Address,Endpoint.Port]'

# Expected: ["available", "scrumpoker-prod.xxxxx.us-east-1.rds.amazonaws.com", 5432]

# Check ElastiCache Redis
aws elasticache describe-replication-groups \
  --replication-group-id scrumpoker-prod-redis \
  --query 'ReplicationGroups[0].[Status,ConfigurationEndpoint.Address,ConfigurationEndpoint.Port]'

# Expected: ["available", "scrumpoker-prod-redis.xxxxx.cache.amazonaws.com", 6379]
```

**Verification:**
- [ ] RDS instance status is "available"
- [ ] RDS Multi-AZ is enabled
- [ ] ElastiCache cluster status is "available"
- [ ] ElastiCache cluster mode is enabled (3+ nodes)
- [ ] S3 bucket exists and is accessible

**If Failed:** Create missing infrastructure using Terraform/CloudFormation or AWS console

### Step 3: Prepare Secrets

Generate and store sensitive credentials in Kubernetes secrets.

**Required Secrets:**

1. **Database Connection**
2. **Redis Connection**
3. **JWT Signing Keys (RS256)**
4. **OAuth2 Credentials**
5. **Alert Manager Webhook URLs**

```bash
# Navigate to Kubernetes directory
cd infra/kubernetes

# Create namespace (if not exists)
kubectl create namespace production

# Generate JWT RSA key pair (if not already generated)
# See backend/README.md lines 54-72 for detailed instructions
openssl genpkey -algorithm RSA -out jwt-private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem

# Create secret with database credentials
kubectl create secret generic scrum-poker-secrets \
  --from-literal=DB_JDBC_URL='jdbc:postgresql://scrumpoker-prod.xxxxx.us-east-1.rds.amazonaws.com:5432/scrumpoker' \
  --from-literal=DB_USERNAME='scrumpoker_app' \
  --from-literal=DB_PASSWORD='<STRONG_PASSWORD>' \
  --from-literal=REDIS_URI='redis://scrumpoker-prod-redis.xxxxx.cache.amazonaws.com:6379' \
  --from-file=JWT_PRIVATE_KEY=jwt-private.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public.pem \
  --from-literal=OAUTH_GOOGLE_CLIENT_ID='<GOOGLE_CLIENT_ID>' \
  --from-literal=OAUTH_GOOGLE_CLIENT_SECRET='<GOOGLE_CLIENT_SECRET>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_ID='<MICROSOFT_CLIENT_ID>' \
  --from-literal=OAUTH_MICROSOFT_CLIENT_SECRET='<MICROSOFT_CLIENT_SECRET>' \
  -n production \
  --dry-run=client -o yaml | kubectl apply -f -

# Verify secret created
kubectl get secret scrum-poker-secrets -n production

# Create monitoring secrets (if using external alerting)
kubectl create secret generic alertmanager-config \
  --from-literal=SLACK_WEBHOOK_URL='<SLACK_WEBHOOK>' \
  --from-literal=PAGERDUTY_SERVICE_KEY='<PAGERDUTY_KEY>' \
  -n monitoring \
  --dry-run=client -o yaml | kubectl apply -f -
```

**Verification:**
- [ ] Secret `scrum-poker-secrets` exists in `production` namespace
- [ ] Secret contains all required keys (DB_JDBC_URL, JWT_PRIVATE_KEY, etc.)
- [ ] JWT keys are valid RSA 2048-bit keys
- [ ] OAuth client IDs/secrets match OAuth provider configuration

**Security Note:** Store JWT private key (`jwt-private.pem`) in AWS Secrets Manager for disaster recovery. See [DISASTER_RECOVERY.md](./DISASTER_RECOVERY.md) for backup procedures.

### Step 4: Configure OAuth2 Redirect URIs

Update OAuth provider configuration with correct redirect URIs.

**Google Cloud Console:**
1. Navigate to: https://console.cloud.google.com/apis/credentials
2. Select OAuth 2.0 Client ID for Planning Poker
3. Add Authorized redirect URIs:
   - `https://planningpoker.example.com/api/auth/callback/google`
   - `https://planningpoker.example.com/api/auth/callback` (fallback)

**Microsoft Azure Portal:**
1. Navigate to: https://portal.azure.com → Azure Active Directory → App registrations
2. Select Planning Poker application
3. Add Redirect URIs (Web):
   - `https://planningpoker.example.com/api/auth/callback/microsoft`

**Verification:**
- [ ] Redirect URIs match production domain
- [ ] HTTPS protocol used (not HTTP)
- [ ] No trailing slashes in URIs

## Deployment Workflow

Follow these steps to deploy the application to production.

### Step 1: Build and Push Docker Image

```bash
# Navigate to backend directory
cd backend

# Build Docker image (JVM mode for production)
docker build -f src/main/docker/Dockerfile.jvm -t planning-poker-backend:v1.0.0 .

# Tag image for AWS ECR
docker tag planning-poker-backend:v1.0.0 <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/planning-poker:v1.0.0

# Login to ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Push image
docker push <AWS_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/planning-poker:v1.0.0
```

**Verification:**
- [ ] Docker build completed without errors
- [ ] Image size is reasonable (~150-250 MB for JVM mode)
- [ ] Image successfully pushed to ECR
- [ ] Image tag matches version in Kubernetes manifests

**If Failed:** Check Dockerfile syntax, build logs, ECR permissions

### Step 2: Update Kubernetes Manifests

```bash
# Navigate to Kubernetes overlays directory
cd ../infra/kubernetes/overlays/production

# Update image tag in kustomization.yaml
# Edit overlays/production/kustomization.yaml and update:
# images:
#   - name: planning-poker-backend
#     newTag: v1.0.0

# Verify kustomize build (dry-run)
kubectl kustomize . | head -50

# Expected: See Deployment manifest with correct image tag
```

**Verification:**
- [ ] Image tag matches pushed Docker image
- [ ] Environment variables configured correctly
- [ ] Resource limits appropriate for production (see infra/kubernetes/README.md lines 40-67)
- [ ] Replicas set to 2 (minimum for HA)

### Step 3: Apply Database Migrations (First Deployment Only)

**CAUTION:** Only run migrations on first deployment or when schema changes are introduced.

```bash
# Run Flyway migrations from backend directory
cd backend

# Set environment variables
export DB_JDBC_URL='jdbc:postgresql://scrumpoker-prod.xxxxx.us-east-1.rds.amazonaws.com:5432/scrumpoker'
export DB_USERNAME='scrumpoker_app'
export DB_PASSWORD='<STRONG_PASSWORD>'

# Run migrations
./mvnw flyway:migrate -Dflyway.url=$DB_JDBC_URL -Dflyway.user=$DB_USERNAME -Dflyway.password=$DB_PASSWORD

# Verify migration success
./mvnw flyway:info -Dflyway.url=$DB_JDBC_URL -Dflyway.user=$DB_USERNAME -Dflyway.password=$DB_PASSWORD
```

**Verification:**
- [ ] All migrations executed successfully
- [ ] No pending migrations
- [ ] Database schema matches application version

**If Failed:** Rollback migrations using Flyway repair/undo or restore database snapshot

### Step 4: Deploy Application to Kubernetes

```bash
# Navigate to Kubernetes overlays directory
cd ../infra/kubernetes/overlays/production

# Apply manifests (production environment)
kubectl apply -k .

# Watch rollout status
kubectl rollout status deployment/scrum-poker-backend -n production

# Expected: "deployment "scrum-poker-backend" successfully rolled out"
```

**Verification:**
- [ ] Deployment created successfully
- [ ] Pods transitioning to Running state
- [ ] No CrashLoopBackOff or ImagePullBackOff errors
- [ ] HPA (Horizontal Pod Autoscaler) created

**Monitor Deployment:**
```bash
# Watch pods come up
kubectl get pods -n production -l app=scrum-poker-backend -w

# Check pod logs for startup
kubectl logs -n production -l app=scrum-poker-backend --tail=100 -f
```

**Expected Log Output:**
```
__  ____  __  _____   ___  __ ____  ______
 --/ __ \/ / / / _ | / _ \/ //_/ / / / __/
 -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \
--\___\_\____/_/ |_/_/|_/_/|_|\____/___/
INFO  [io.quarkus] (main) planning-poker 1.0.0 on JVM started in 3.456s
INFO  [io.quarkus] (main) Profile prod activated
INFO  [io.quarkus] (main) Installed features: [cdi, hibernate-orm, rest, websockets, ...]
```

### Step 5: Deploy Monitoring Stack (Optional - First Deployment)

```bash
# Navigate to monitoring directory
cd ../../../infra/monitoring

# Create monitoring namespace
kubectl create namespace monitoring

# Deploy Prometheus
kubectl apply -f prometheus/

# Deploy Grafana
kubectl apply -f grafana/

# Verify monitoring stack
kubectl get pods -n monitoring
```

**Verification:**
- [ ] Prometheus pod running
- [ ] Grafana pod running
- [ ] ServiceMonitor created for application
- [ ] Dashboards imported (see [infra/monitoring/README.md](../../infra/monitoring/README.md))

For detailed monitoring setup, see [MONITORING_GUIDE.md](./MONITORING_GUIDE.md).

### Step 6: Create Ingress/ALB

```bash
# Apply ingress manifest
cd ../infra/kubernetes/overlays/production
kubectl apply -f ingress.yaml

# Wait for ALB to provision (2-5 minutes)
kubectl get ingress scrum-poker-backend -n production -w

# Get ALB address
ALB_ADDRESS=$(kubectl get ingress scrum-poker-backend -n production -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
echo "ALB Address: $ALB_ADDRESS"
```

**Verification:**
- [ ] Ingress created successfully
- [ ] ALB provisioned (status shows hostname)
- [ ] ALB health checks passing (target group healthy)
- [ ] SSL/TLS certificate attached (HTTPS enabled)

**Update DNS:**
```bash
# Create DNS CNAME record pointing to ALB
# Example using AWS Route 53:
aws route53 change-resource-record-sets \
  --hosted-zone-id <ZONE_ID> \
  --change-batch '{
    "Changes": [{
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "planningpoker.example.com",
        "Type": "CNAME",
        "TTL": 300,
        "ResourceRecords": [{"Value": "'$ALB_ADDRESS'"}]
      }
    }]
  }'
```

**Verification:**
- [ ] DNS resolves to ALB address (`nslookup planningpoker.example.com`)
- [ ] DNS propagation complete (5-15 minutes)

## Post-Deployment Verification

Run these checks to verify the deployment is healthy.

### Step 1: Health Check Endpoints

```bash
# Check application health
curl https://planningpoker.example.com/q/health/live
# Expected: {"status":"UP"}

curl https://planningpoker.example.com/q/health/ready
# Expected: {"status":"UP","checks":[{"name":"Database","status":"UP"},...]}

# Check metrics endpoint (Prometheus scraping)
curl https://planningpoker.example.com/q/metrics
# Expected: Prometheus-formatted metrics
```

**Verification:**
- [ ] `/q/health/live` returns HTTP 200 with status UP
- [ ] `/q/health/ready` returns HTTP 200 with all checks UP
- [ ] `/q/metrics` returns HTTP 200 with metrics data

### Step 2: Smoke Test - Create Session

```bash
# Test session creation API
curl -X POST https://planningpoker.example.com/api/sessions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Deployment Smoke Test",
    "deckType": "FIBONACCI",
    "votingMode": "ASYNC"
  }'

# Expected: {"id":"<session-id>","name":"Deployment Smoke Test",...}
```

**Verification:**
- [ ] API returns HTTP 200/201 with session object
- [ ] Session created in database
- [ ] No errors in application logs

### Step 3: Smoke Test - WebSocket Connection

```bash
# Test WebSocket connectivity (using wscat or browser dev tools)
# Install wscat: npm install -g wscat

wscat -c wss://planningpoker.example.com/ws/<session-id>

# Expected: WebSocket connection established
# Send message: {"action":"join","userName":"TestUser"}
# Expected: Receive join confirmation message
```

**Verification:**
- [ ] WebSocket connection established (HTTP 101 Switching Protocols)
- [ ] Can send and receive messages
- [ ] No disconnections or errors

### Step 4: Verify Monitoring Data

```bash
# Port-forward Grafana (if not exposed publicly)
kubectl port-forward -n monitoring svc/grafana 3000:3000

# Open Grafana: http://localhost:3000
# Default credentials: admin/admin (change on first login)
```

**Verification:**
- [ ] Application Overview dashboard shows metrics
- [ ] Request rate > 0 (from smoke tests)
- [ ] Error rate = 0%
- [ ] All panels loading data
- [ ] No alerts firing

For detailed monitoring verification, see [MONITORING_GUIDE.md](./MONITORING_GUIDE.md).

### Step 5: Verify Autoscaling

```bash
# Check HPA status
kubectl get hpa -n production

# Expected output:
# NAME                     REFERENCE                           TARGETS   MINPODS   MAXPODS   REPLICAS
# scrum-poker-backend-hpa  Deployment/scrum-poker-backend      15%/70%   2         10        2
```

**Verification:**
- [ ] HPA shows current CPU usage
- [ ] REPLICAS matches MINPODS (2) initially
- [ ] TARGETS shows current/target CPU percentage

### Step 6: End-to-End User Journey Test

Perform complete user flow:

1. **Navigate to Application:** `https://planningpoker.example.com`
2. **Login with OAuth:** Click "Login with Google" or "Login with Microsoft"
3. **Create Session:** Create a new planning poker room
4. **Share Link:** Copy session link
5. **Join as Second User:** Open link in incognito window
6. **Cast Votes:** Both users vote on a story
7. **Reveal Votes:** Moderator reveals votes
8. **Export Results:** Export session to CSV

**Verification:**
- [ ] All steps complete without errors
- [ ] WebSocket real-time updates working
- [ ] OAuth login successful
- [ ] Export file generated and downloadable

**If Failed:** See [TROUBLESHOOTING_GUIDE.md](./TROUBLESHOOTING_GUIDE.md) for common issues

## Rollback Procedures

If deployment fails or introduces issues, follow these rollback steps.

### Option 1: Rollback to Previous Deployment

```bash
# View rollout history
kubectl rollout history deployment/scrum-poker-backend -n production

# Rollback to previous version
kubectl rollout undo deployment/scrum-poker-backend -n production

# Watch rollback progress
kubectl rollout status deployment/scrum-poker-backend -n production

# Verify rollback
kubectl get pods -n production -l app=scrum-poker-backend
kubectl logs -n production -l app=scrum-poker-backend --tail=50
```

**Verification:**
- [ ] Pods running previous image version
- [ ] Application health checks passing
- [ ] No errors in logs

### Option 2: Rollback to Specific Revision

```bash
# View available revisions
kubectl rollout history deployment/scrum-poker-backend -n production

# Rollback to specific revision
kubectl rollout undo deployment/scrum-poker-backend -n production --to-revision=3

# Verify rollback
kubectl rollout status deployment/scrum-poker-backend -n production
```

### Option 3: Rollback Database Migrations

**CAUTION:** Database rollbacks can cause data loss. Test in staging first.

```bash
# View migration history
./mvnw flyway:info

# Undo last migration (if using Flyway Pro)
./mvnw flyway:undo

# Or restore from database snapshot (see DISASTER_RECOVERY.md)
```

### Post-Rollback Verification

After rollback, repeat [Post-Deployment Verification](#post-deployment-verification) steps to ensure system stability.

## Environment-Specific Configurations

### Development Environment

```bash
# Deploy to dev namespace
kubectl apply -k infra/kubernetes/overlays/dev

# Configuration differences:
# - Replicas: 1
# - Resource limits: CPU 500m, Memory 512Mi
# - No HPA
# - No ingress (NodePort service)
```

### Staging Environment

```bash
# Deploy to staging namespace
kubectl apply -k infra/kubernetes/overlays/staging

# Configuration differences:
# - Replicas: 2
# - Resource limits: CPU 1000m, Memory 1Gi
# - HPA: min 2, max 5
# - Ingress with staging domain (staging.planningpoker.example.com)
```

### Production Environment

```bash
# Deploy to production namespace
kubectl apply -k infra/kubernetes/overlays/production

# Configuration:
# - Replicas: 2
# - Resource limits: CPU 2000m, Memory 2Gi
# - HPA: min 2, max 10
# - Ingress with production domain (planningpoker.example.com)
# - Multi-AZ pod distribution
```

For detailed environment configurations, see [infra/kubernetes/README.md](../../infra/kubernetes/README.md) lines 40-67.

## Troubleshooting Deployment Issues

### Issue: Pods in CrashLoopBackOff

**Symptoms:**
```bash
kubectl get pods -n production
# NAME                                   READY   STATUS             RESTARTS
# scrum-poker-backend-7d8f9c5b6d-abc12   0/1     CrashLoopBackOff   5
```

**Diagnosis:**
```bash
# Check pod logs
kubectl logs -n production scrum-poker-backend-7d8f9c5b6d-abc12

# Check previous container logs (if restarted)
kubectl logs -n production scrum-poker-backend-7d8f9c5b6d-abc12 --previous

# Describe pod for events
kubectl describe pod -n production scrum-poker-backend-7d8f9c5b6d-abc12
```

**Common Causes:**
1. **Database connection failure:** Check `DB_JDBC_URL`, credentials in secret
2. **Redis connection failure:** Check `REDIS_URI` in secret
3. **Missing JWT keys:** Verify `JWT_PRIVATE_KEY` exists in secret
4. **OOM (Out of Memory):** Increase memory limits in deployment manifest

**Resolution:**
```bash
# Fix secret and redeploy
kubectl delete secret scrum-poker-secrets -n production
kubectl create secret generic scrum-poker-secrets ... # (recreate with correct values)
kubectl rollout restart deployment/scrum-poker-backend -n production
```

### Issue: ImagePullBackOff

**Symptoms:**
```bash
kubectl get pods -n production
# NAME                                   READY   STATUS             RESTARTS
# scrum-poker-backend-7d8f9c5b6d-abc12   0/1     ImagePullBackOff   0
```

**Diagnosis:**
```bash
# Describe pod to see image pull error
kubectl describe pod -n production scrum-poker-backend-7d8f9c5b6d-abc12
# Events:
#   Failed to pull image "xxxx.dkr.ecr.us-east-1.amazonaws.com/planning-poker:v1.0.0": rpc error: code = Unknown desc = Error response from daemon: pull access denied
```

**Common Causes:**
1. **ECR authentication failure:** Nodes can't authenticate with ECR
2. **Image doesn't exist:** Tag mismatch or push failed
3. **Wrong registry:** Incorrect AWS account ID or region

**Resolution:**
```bash
# Verify image exists in ECR
aws ecr describe-images --repository-name planning-poker --region us-east-1

# Check EKS node IAM role has ECR pull permissions
# Policy: AmazonEC2ContainerRegistryReadOnly

# Update image tag in manifest and redeploy
```

### Issue: Ingress/ALB Not Provisioning

**Symptoms:**
```bash
kubectl get ingress -n production
# NAME                     CLASS   HOSTS                          ADDRESS   PORTS   AGE
# scrum-poker-backend      alb     planningpoker.example.com      <none>    80      5m
```

**Diagnosis:**
```bash
# Check ingress events
kubectl describe ingress scrum-poker-backend -n production

# Check ALB controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

**Common Causes:**
1. **ALB controller not installed:** Install AWS Load Balancer Controller
2. **Insufficient IAM permissions:** Controller IAM role missing ELB permissions
3. **Invalid ingress annotations:** Syntax error in ALB annotations

**Resolution:**
```bash
# Install ALB controller (if not installed)
helm repo add eks https://aws.github.io/eks-charts
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=<cluster-name>

# Verify controller running
kubectl get deployment -n kube-system aws-load-balancer-controller
```

### Issue: Health Checks Failing

**Symptoms:**
```bash
curl https://planningpoker.example.com/q/health/ready
# HTTP 503 Service Unavailable
# {"status":"DOWN","checks":[{"name":"Database","status":"DOWN"}]}
```

**Diagnosis:**
```bash
# Check detailed health status
curl https://planningpoker.example.com/q/health | jq .

# Check pod logs for database errors
kubectl logs -n production -l app=scrum-poker-backend | grep -i database
```

**Common Causes:**
1. **Database connectivity:** RDS security group blocking EKS
2. **Database credentials:** Wrong username/password
3. **Redis connectivity:** ElastiCache security group blocking EKS

**Resolution:**
```bash
# Verify RDS security group allows inbound from EKS nodes
# Verify ElastiCache security group allows inbound from EKS nodes

# Test database connection from pod
kubectl exec -it -n production <pod-name> -- /bin/bash
$ psql -h scrumpoker-prod.xxxxx.rds.amazonaws.com -U scrumpoker_app -d scrumpoker
```

For more troubleshooting scenarios, see [TROUBLESHOOTING_GUIDE.md](./TROUBLESHOOTING_GUIDE.md).

## Next Steps

After successful deployment:

1. **Configure Monitoring:** Set up alert receivers and dashboards ([MONITORING_GUIDE.md](./MONITORING_GUIDE.md))
2. **Test Disaster Recovery:** Perform DR drill to validate backup/restore procedures ([DISASTER_RECOVERY.md](./DISASTER_RECOVERY.md))
3. **Review Operations:** Familiarize team with operational tasks ([OPERATIONS_RUNBOOK.md](./OPERATIONS_RUNBOOK.md))
4. **Performance Testing:** Run load tests to validate autoscaling ([docs/performance-benchmarks.md](../performance-benchmarks.md))
5. **Security Review:** Audit security configurations ([docs/security-assessment.md](../security-assessment.md))

## Support

For deployment issues:
- **Kubernetes:** See [infra/kubernetes/README.md](../../infra/kubernetes/README.md) for detailed manifest documentation
- **Monitoring:** See [infra/monitoring/README.md](../../infra/monitoring/README.md) for metrics and dashboards
- **Troubleshooting:** See [TROUBLESHOOTING_GUIDE.md](./TROUBLESHOOTING_GUIDE.md) for common issues
- **Disaster Recovery:** See [DISASTER_RECOVERY.md](./DISASTER_RECOVERY.md) for backup/restore procedures
