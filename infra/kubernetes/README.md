# Kubernetes Deployment Manifests

This directory contains comprehensive Kubernetes manifests for deploying the Scrum Poker backend application to production using Kustomize.

## Directory Structure

```
kubernetes/
├── base/                           # Base manifests (environment-agnostic)
│   ├── deployment.yaml             # Application deployment with health probes
│   ├── service.yaml                # ClusterIP service
│   ├── ingress.yaml                # Ingress with ALB/Nginx annotations
│   ├── configmap.yaml              # Non-sensitive configuration
│   ├── secret.yaml                 # Secret template (placeholders only)
│   ├── hpa.yaml                    # HorizontalPodAutoscaler
│   └── kustomization.yaml          # Base kustomization
└── overlays/                       # Environment-specific overlays
    ├── dev/                        # Development environment
    │   ├── kustomization.yaml
    │   ├── deployment-patch.yaml   # Reduced resources (512Mi/250m CPU)
    │   └── configmap-patch.yaml    # Dev URLs, DEBUG logging
    ├── staging/                    # Staging environment
    │   ├── kustomization.yaml
    │   └── configmap-patch.yaml    # Staging URLs, INFO logging
    └── production/                 # Production environment
        ├── kustomization.yaml
        └── configmap-patch.yaml    # Production URLs, WARN logging
```

## Prerequisites

1. **Kubernetes Cluster**: EKS cluster (AWS) or compatible Kubernetes 1.20+
2. **kubectl**: Version 1.20+ with built-in kustomize support
3. **Metrics Server**: Required for HPA CPU metrics
   ```bash
   kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
   ```
4. **Ingress Controller**: Either AWS ALB Ingress Controller or Nginx Ingress Controller
5. **Secret Management**: External Secrets Operator, AWS Secrets Manager CSI Driver, or Sealed Secrets

## Resource Specifications

### Base Configuration (Production)

| Resource | Value |
|----------|-------|
| Replicas | 2 (initial) |
| Memory Request | 1Gi |
| Memory Limit | 2Gi |
| CPU Request | 500m |
| CPU Limit | 1000m |
| HPA Min Replicas | 2 |
| HPA Max Replicas | 10 |
| HPA CPU Target | 70% |

### Development Configuration

| Resource | Value |
|----------|-------|
| Replicas | 1 |
| Memory Request | 512Mi |
| Memory Limit | 1Gi |
| CPU Request | 250m |
| CPU Limit | 500m |
| Log Level | DEBUG |
| JSON Logging | false |

## Deployment Instructions

### 1. Configure Secrets

**CRITICAL**: The `secret.yaml` file contains placeholder values only. You MUST configure real secrets before deployment.

#### Option A: External Secrets Operator (Recommended)
```bash
# Install External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets-system --create-namespace

# Create SecretStore pointing to AWS Secrets Manager
kubectl apply -f - <<EOF
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secrets-manager
  namespace: production
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
EOF

# Replace base/secret.yaml with ExternalSecret manifest
```

#### Option B: Manual Secret Creation (Dev/Test Only)
```bash
kubectl create secret generic scrum-poker-secrets \
  --from-literal=DB_USERNAME=postgres \
  --from-literal=DB_PASSWORD=your-db-password \
  --from-literal=GOOGLE_CLIENT_SECRET=your-google-secret \
  --from-literal=MICROSOFT_CLIENT_SECRET=your-microsoft-secret \
  --from-literal=STRIPE_API_KEY=sk_live_... \
  --from-literal=STRIPE_WEBHOOK_SECRET=whsec_... \
  --from-literal=AWS_ACCESS_KEY_ID=AKIA... \
  --from-literal=AWS_SECRET_ACCESS_KEY=your-secret-key \
  --from-file=JWT_PRIVATE_KEY_LOCATION=privateKey.pem \
  -n production
```

### 2. Update Image References

Edit the overlay kustomization files to reference your ECR registry:

**Dev**: `overlays/dev/kustomization.yaml`
```yaml
images:
  - name: <IMAGE_PLACEHOLDER>
    newName: 123456789012.dkr.ecr.us-east-1.amazonaws.com/scrum-poker-backend
    newTag: dev-latest
```

**Production**: `overlays/production/kustomization.yaml`
```yaml
images:
  - name: <IMAGE_PLACEHOLDER>
    newName: 123456789012.dkr.ecr.us-east-1.amazonaws.com/scrum-poker-backend
    newTag: v1.0.0-abc1234
```

### 3. Update Environment-Specific Configuration

Edit the ConfigMap patches in each overlay to update:
- Database URLs (RDS endpoints)
- Redis URLs (ElastiCache endpoints)
- CORS origins (frontend domain)
- S3 bucket names
- SSO URLs

### 4. Deploy to Cluster

#### Development Environment
```bash
# Validate manifests
kubectl kustomize infra/kubernetes/overlays/dev

# Apply to cluster
kubectl apply -k infra/kubernetes/overlays/dev

# Verify deployment
kubectl get pods -n development -l app=scrum-poker-backend
kubectl get svc -n development -l app=scrum-poker-backend
kubectl get ingress -n development -l app=scrum-poker-backend
kubectl get hpa -n development -l app=scrum-poker-backend
```

#### Staging Environment
```bash
kubectl apply -k infra/kubernetes/overlays/staging

# Verify deployment
kubectl get pods -n staging -l app=scrum-poker-backend -w
```

#### Production Environment
```bash
# Validate before applying
kubectl kustomize infra/kubernetes/overlays/production > /tmp/prod-manifest.yaml
kubectl apply --dry-run=server -f /tmp/prod-manifest.yaml

# Apply to production
kubectl apply -k infra/kubernetes/overlays/production

# Monitor rollout
kubectl rollout status deployment/scrum-poker-backend -n production
kubectl get pods -n production -l app=scrum-poker-backend -w
```

## Health Checks

The application exposes Quarkus SmallRye Health endpoints:

- **Liveness Probe**: `GET /q/health/live` (port 8080)
  - Confirms JVM is responsive
  - No external dependencies checked
  - Initial delay: 60s, period: 10s

- **Readiness Probe**: `GET /q/health/ready` (port 8080)
  - Checks database connectivity
  - Checks Redis availability
  - Checks essential service health
  - Initial delay: 30s, period: 10s

- **Startup Probe**: `GET /q/health/live` (port 8080)
  - Allows up to 60 seconds for initial startup
  - Prevents premature liveness probe failures

## Horizontal Pod Autoscaling

The HPA is configured with:

- **Metrics**: CPU utilization (70% target)
- **Scale-Up**: Add pods when CPU exceeds 70% for 2 minutes
  - Add 50% of current replicas OR 2 pods (whichever is larger)
- **Scale-Down**: Remove pods when CPU below 35% for 10 minutes (conservative)
  - Remove 25% of current replicas OR 1 pod (whichever is smaller)

**Future Enhancement**: Custom WebSocket connection metric after Prometheus Adapter is installed.

## Ingress Configuration

The ingress supports both AWS ALB Ingress Controller and Nginx Ingress Controller.

### AWS ALB Configuration
- Sticky sessions enabled (24 hours)
- TLS termination via ACM certificate
- Health checks on `/q/health/ready`
- Update `alb.ingress.kubernetes.io/certificate-arn` annotation with your ACM certificate ARN

### Nginx Configuration
- Cookie-based session affinity
- TLS via cert-manager
- WebSocket support (3600s timeout)

Uncomment the appropriate `ingressClassName` in `base/ingress.yaml`:
```yaml
spec:
  ingressClassName: alb  # For AWS ALB
  # ingressClassName: nginx  # For Nginx
```

## Troubleshooting

### Pods Not Starting
```bash
# Check pod events
kubectl describe pod <pod-name> -n <namespace>

# Check logs
kubectl logs <pod-name> -n <namespace>

# Check readiness probe
kubectl exec <pod-name> -n <namespace> -- curl localhost:8080/q/health/ready
```

### HPA Not Scaling
```bash
# Verify metrics-server is running
kubectl get deployment metrics-server -n kube-system

# Check HPA status
kubectl describe hpa scrum-poker-backend -n <namespace>

# Check current metrics
kubectl top pods -n <namespace> -l app=scrum-poker-backend
```

### Secret Not Found
```bash
# List secrets
kubectl get secrets -n <namespace>

# Verify secret data (keys only, not values)
kubectl describe secret scrum-poker-secrets -n <namespace>

# Check if External Secrets Operator is syncing
kubectl get externalsecret -n <namespace>
kubectl describe externalsecret scrum-poker-secrets -n <namespace>
```

### Ingress Not Routing Traffic
```bash
# Check ingress status
kubectl describe ingress scrum-poker-backend -n <namespace>

# For ALB, check AWS load balancer
kubectl get ingress scrum-poker-backend -n <namespace> -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

# Test service directly (port-forward)
kubectl port-forward svc/scrum-poker-backend 8080:80 -n <namespace>
curl localhost:8080/q/health/ready
```

## Acceptance Criteria Verification

### ✅ I8.T1 Acceptance Criteria

1. **Deploy to dev cluster**:
   ```bash
   kubectl apply -k infra/kubernetes/overlays/dev
   # Expected: All resources created successfully
   ```

2. **Deployment creates 2 pods initially (production)**:
   ```bash
   kubectl get deployment scrum-poker-backend -n production -o jsonpath='{.spec.replicas}'
   # Expected output: 2
   ```

3. **Liveness/readiness probes configured correctly**:
   ```bash
   kubectl get deployment scrum-poker-backend -n production -o yaml | grep -A 5 "livenessProbe:\|readinessProbe:"
   # Expected: Probes on /q/health/live and /q/health/ready
   ```

4. **Ingress routes traffic to service**:
   ```bash
   kubectl get ingress scrum-poker-backend -n production
   # Expected: Ingress created with ALB/Nginx annotations
   ```

5. **HPA created with correct scaling thresholds**:
   ```bash
   kubectl get hpa scrum-poker-backend -n production -o yaml | grep -E "minReplicas:|maxReplicas:|averageUtilization:"
   # Expected: min=2, max=10, CPU=70%
   ```

6. **ConfigMap mounted as environment variables**:
   ```bash
   kubectl exec deployment/scrum-poker-backend -n production -- env | grep DB_JDBC_URL
   # Expected: Environment variables from ConfigMap visible
   ```

7. **Secrets referenced in deployment**:
   ```bash
   kubectl get deployment scrum-poker-backend -n production -o yaml | grep secretRef
   # Expected: secretRef to scrum-poker-secrets
   ```

## CI/CD Integration

### GitHub Actions Example
```yaml
- name: Deploy to Production
  run: |
    kubectl apply -k infra/kubernetes/overlays/production
    kubectl rollout status deployment/scrum-poker-backend -n production --timeout=10m
```

### Image Tag Management
Use semantic versioning with Git SHA:
```bash
IMAGE_TAG="v1.0.0-$(git rev-parse --short HEAD)"
cd infra/kubernetes/overlays/production
kustomize edit set image <IMAGE_PLACEHOLDER>=123456789012.dkr.ecr.us-east-1.amazonaws.com/scrum-poker-backend:${IMAGE_TAG}
```

## Security Considerations

1. **Secrets**: Never commit real secrets to Git. Use External Secrets Operator or equivalent.
2. **RBAC**: Create service accounts with minimal required permissions.
3. **Network Policies**: Implement NetworkPolicy to restrict pod-to-pod communication.
4. **Pod Security Standards**: Run containers as non-root (already configured in deployment).
5. **Image Scanning**: Enable ECR vulnerability scanning and block deployment of high-severity vulnerabilities.

## Future Enhancements

- [ ] Add custom WebSocket metrics to HPA (requires Prometheus Adapter)
- [ ] Configure ServiceMonitor for Prometheus metrics scraping
- [ ] Add NetworkPolicy for pod network isolation
- [ ] Implement PodDisruptionBudget for high availability
- [ ] Add resource quotas and limit ranges per namespace
- [ ] Configure pod anti-affinity for multi-AZ distribution

## Support

For issues or questions:
- Documentation: https://github.com/your-org/planning-poker
- Contact: devops@scrumpoker.com
