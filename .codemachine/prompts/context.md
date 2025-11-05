# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I8.T1",
  "iteration_id": "I8",
  "iteration_goal": "Prepare application for production deployment including Kubernetes manifests, monitoring setup, performance optimization, security hardening, documentation, and final end-to-end testing.",
  "description": "Create comprehensive Kubernetes manifests for production deployment. Resources: Deployment (backend application with replicas, resource limits, liveness/readiness probes), Service (ClusterIP for internal traffic), Ingress (ALB/Nginx with TLS termination, sticky sessions), ConfigMap (environment-specific configuration: database URL, Redis URL, feature flags), Secret (sensitive data: database password, OAuth secrets, JWT signing key, Stripe API key), HorizontalPodAutoscaler (target CPU 70%, custom metric for WebSocket connections). Use Kustomize for environment overlays (dev, staging, production). Configure rolling update strategy (maxSurge: 1, maxUnavailable: 0 for zero-downtime).",
  "agent_type_hint": "SetupAgent",
  "inputs": "Deployment architecture from architecture blueprint, Kubernetes best practices (resource limits, health checks, secrets), Environment configuration requirements",
  "input_files": [
    ".codemachine/artifacts/architecture/05_Operational_Architecture.md"
  ],
  "target_files": [
    "infra/kubernetes/base/deployment.yaml",
    "infra/kubernetes/base/service.yaml",
    "infra/kubernetes/base/ingress.yaml",
    "infra/kubernetes/base/configmap.yaml",
    "infra/kubernetes/base/secret.yaml",
    "infra/kubernetes/base/hpa.yaml",
    "infra/kubernetes/overlays/dev/kustomization.yaml",
    "infra/kubernetes/overlays/staging/kustomization.yaml",
    "infra/kubernetes/overlays/production/kustomization.yaml"
  ],
  "deliverables": "Deployment manifest with 2 replicas, resource requests/limits (1GB mem, 500m CPU), Liveness probe: `/q/health/live`, readiness probe: `/q/health/ready`, Service exposing port 8080, Ingress with sticky session annotation, TLS certificate config, ConfigMap with database/Redis URLs, log level, Secret template for sensitive values (to be populated by CI/CD or external secrets operator), HPA scaling 2-10 pods based on CPU, Kustomize overlays for each environment",
  "acceptance_criteria": "`kubectl apply -k infra/kubernetes/overlays/dev` deploys to dev cluster, Deployment creates 2 pods initially, Liveness/readiness probes configured correctly (verify pod status), Ingress routes traffic to service, HPA created with correct scaling thresholds, ConfigMap mounted as environment variables in pods, Secrets referenced in deployment (values from external secret manager in prod)",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: deployment-strategy (from 05_Operational_Architecture.md)

```markdown
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
```

### Context: horizontal-scaling (from 05_Operational_Architecture.md)

```markdown
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
```

### Context: fault-tolerance (from 05_Operational_Architecture.md)

```markdown
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
```

### Context: deployment-environments (from 05_Operational_Architecture.md)

```markdown
**Deployment Environments:**

| Environment | Purpose | Infrastructure | Data |
|-------------|---------|----------------|------|
| **Development** | Local developer machines | Docker Compose, local PostgreSQL/Redis | Synthetic test data |
| **Staging** | Pre-production testing | EKS cluster (2 nodes, t3.medium), RDS (db.t3.small), ElastiCache (cache.t3.micro) | Anonymized production data subset |
| **Production** | Live user traffic | EKS cluster (3+ nodes, t3.large), RDS (db.r6g.large with read replica), ElastiCache Redis Cluster (cache.r6g.large, 3 shards) | Real user data |
```

### Context: task-i8-t1 (from 02_Iteration_I8.md)

```markdown
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
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** This is the comprehensive Quarkus application configuration file containing all environment-specific settings.
    *   **Recommendation:** You MUST extract all environment-specific configuration values from this file to create the Kubernetes ConfigMap. Key properties to extract include:
        - Database connection settings (DB_USERNAME, DB_PASSWORD, DB_JDBC_URL, DB_REACTIVE_URL)
        - Redis configuration (REDIS_URL, REDIS_POOL_MAX_SIZE)
        - JWT settings (JWT_ISSUER, JWT_PUBLIC_KEY_LOCATION, JWT_PRIVATE_KEY_LOCATION, JWT_TOKEN_EXPIRATION)
        - OAuth2 credentials (GOOGLE_CLIENT_ID, MICROSOFT_CLIENT_ID, GOOGLE_CLIENT_SECRET, MICROSOFT_CLIENT_SECRET)
        - Stripe API configuration (STRIPE_API_KEY, STRIPE_WEBHOOK_SECRET, STRIPE_PRICE_PRO, STRIPE_PRICE_PRO_PLUS, STRIPE_PRICE_ENTERPRISE)
        - S3 configuration (S3_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, S3_BUCKET_NAME)
        - HTTP/CORS settings (HTTP_PORT, CORS_ORIGINS, CORS_METHODS, CORS_HEADERS)
        - Log configuration (LOG_LEVEL, LOG_JSON_FORMAT)
        - Feature flags and timeouts
    *   **Critical:** Database pool sizes (DB_POOL_MAX_SIZE, line 13), Redis pool settings (REDIS_POOL_MAX_SIZE, line 44), and WebSocket max frame size (WS_MAX_FRAME_SIZE, line 178) are already configurable via environment variables in this file. Your ConfigMap should set these to production-appropriate values.

*   **File:** `docker-compose.yml`
    *   **Summary:** This file contains the local development Docker Compose setup with PostgreSQL, Redis cluster (3 nodes), Prometheus, and Grafana.
    *   **Recommendation:** Use this as a reference for understanding service dependencies and health check configurations. The Redis cluster setup (3 nodes with cluster mode enabled) mirrors the production Redis architecture described in the blueprint.
    *   **Note:** The health check commands for PostgreSQL (line 14-18) and Redis (line 42-46) defined here can inform your Kubernetes readiness probe strategy, though Quarkus provides built-in health endpoints at `/q/health/ready` and `/q/health/live` that should be preferred (enabled at line 198 in application.properties).

*   **File:** `infra/local/prometheus.yml`
    *   **Summary:** Local Prometheus configuration for scraping metrics from Quarkus.
    *   **Recommendation:** This file already configures Prometheus to scrape the Quarkus metrics endpoint at `http://host.docker.internal:8080/q/metrics` (lines in prometheus.yml). You SHOULD reference this configuration when creating the Kubernetes ServiceMonitor in I8.T2 (future task), but it's not directly used in this task.
    *   **Note:** The metrics path is configured as `/q/metrics` which matches line 205 in application.properties (`quarkus.micrometer.export.prometheus.path=/q/metrics`)

### Implementation Tips & Notes

*   **Tip: Application Name and Labels**
    - The application name is configured as `scrum-poker-backend` in application.properties (line 4).
    - You SHOULD use consistent labels across all Kubernetes resources: `app: scrum-poker-backend`, `version: <from-image-tag>`, `component: backend`, `tier: application`.
    - Use these labels for pod selectors, service selectors, and HPA target references.
    - Standard Kubernetes recommended labels: `app.kubernetes.io/name`, `app.kubernetes.io/instance`, `app.kubernetes.io/version`, `app.kubernetes.io/component`, `app.kubernetes.io/part-of`, `app.kubernetes.io/managed-by`

*   **Tip: Health Check Endpoints**
    - Quarkus provides built-in health check endpoints via SmallRye Health extension:
        - Liveness: `/q/health/live` (confirms JVM responsive, no external dependencies)
        - Readiness: `/q/health/ready` (checks database, Redis, essential services)
    - These are already enabled in application.properties (line 198: `quarkus.health.openapi.included=true`)
    - You MUST configure readiness probe with `initialDelaySeconds: 30, periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 3` (per architecture blueprint)
    - Liveness probe should have `initialDelaySeconds: 60` (allow slower JVM startup), `periodSeconds: 10, timeoutSeconds: 5, failureThreshold: 3`

*   **Tip: Resource Limits and Requests**
    - Production pods MUST have resource requests: `memory: 1Gi, cpu: 500m`
    - Resource limits SHOULD be set to: `memory: 2Gi, cpu: 1000m` (allows for burst traffic and prevents OOMKill)
    - These values align with the architecture requirement for 1GB heap + overhead
    - JVM heap size is NOT explicitly set in application.properties, so you SHOULD add JVM options via JAVA_OPTS environment variable in the Deployment: `-Xmx1g -Xms1g` (set min and max to same value to prevent heap resizing)
    - Consider adding JVM GC tuning for production: `-XX:+UseG1GC -XX:MaxGCPauseMillis=200` (as mentioned in plan I8.T4)

*   **Tip: Container Image**
    - The architecture specifies Red Hat UBI9 with OpenJDK 17 as the base image: `registry.access.redhat.com/ubi9/openjdk-17-runtime`
    - Image registry is AWS ECR (referenced in blueprint)
    - You SHOULD use a placeholder image reference in the base deployment that will be overridden by Kustomize: `image: <IMAGE_PLACEHOLDER>` or use a comment showing the expected format
    - Image tag format: semantic version + git SHA (e.g., `v1.0.0-abc1234`)
    - Kustomize overlays will patch the image field with environment-specific values using `newName` and `newTag` directives

*   **Tip: Sticky Sessions for WebSocket**
    - The architecture requires sticky sessions for WebSocket connections based on `room_id` hash for optimal Redis Pub/Sub efficiency
    - For AWS ALB Ingress Controller, use annotations:
        - `alb.ingress.kubernetes.io/target-type: ip`
        - `alb.ingress.kubernetes.io/target-group-attributes: stickiness.enabled=true,stickiness.type=lb_cookie,stickiness.lb_cookie.duration_seconds=86400`
        - `alb.ingress.kubernetes.io/scheme: internet-facing`
        - `alb.ingress.kubernetes.io/listen-ports: '[{"HTTP": 80}, {"HTTPS": 443}]'`
        - `alb.ingress.kubernetes.io/certificate-arn: <ACM_CERTIFICATE_ARN_PLACEHOLDER>` (for TLS)
        - `alb.ingress.kubernetes.io/ssl-redirect: '443'` (redirect HTTP to HTTPS)
    - For Nginx Ingress Controller (alternative), use annotations:
        - `nginx.ingress.kubernetes.io/affinity: cookie`
        - `nginx.ingress.kubernetes.io/session-cookie-name: INGRESSCOOKIE`
        - `nginx.ingress.kubernetes.io/session-cookie-expires: "86400"`
        - `nginx.ingress.kubernetes.io/session-cookie-max-age: "86400"`
    - The base ingress.yaml should include both sets of annotations with comments explaining which controller to use

*   **Tip: Secret Management**
    - The Secret manifest should be a TEMPLATE with placeholder values for documentation purposes
    - Sensitive values from application.properties that MUST go in Secret (never in ConfigMap):
        - `DB_PASSWORD` (line 18)
        - `GOOGLE_CLIENT_SECRET` (line 94)
        - `MICROSOFT_CLIENT_SECRET` (line 107)
        - `STRIPE_API_KEY` (line 144)
        - `STRIPE_WEBHOOK_SECRET` (line 148)
        - `AWS_SECRET_ACCESS_KEY` (line 166)
        - `JWT_PRIVATE_KEY_LOCATION` - you should store the CONTENT of privateKey.pem file, not just the path
    - Use `stringData` field in Secret manifest for readability (Kubernetes will base64 encode automatically)
    - Add a prominent comment at the top of secret.yaml:
      ```yaml
      # ⚠️  SECURITY WARNING: This is a TEMPLATE file with placeholder values.
      # In production, use one of these approaches to inject real secrets:
      # 1. External Secrets Operator (https://external-secrets.io/)
      # 2. AWS Secrets Manager CSI Driver
      # 3. Sealed Secrets (https://github.com/bitnami-labs/sealed-secrets)
      # 4. CI/CD pipeline secret injection
      # NEVER commit real secrets to version control!
      ```

*   **Tip: HPA Custom Metrics**
    - The architecture specifies TWO metrics for HPA:
        1. CPU utilization: target 70% average
        2. Custom metric: `scrumpoker_websocket_connections_total / pod_count` target: 1000 connections/pod
    - Custom metrics require Prometheus Adapter to be installed in the cluster to expose metrics to Kubernetes Metrics API
    - For I8.T1, you SHOULD create the HPA with:
        - Primary metric: CPU at 70% (this works out-of-the-box with metrics-server)
        - Add a TODO comment in hpa.yaml explaining custom WebSocket metrics will be added after Prometheus Adapter setup
        - Example TODO comment structure:
      ```yaml
      # TODO: Add custom WebSocket connection metric after Prometheus Adapter is configured
      # Custom metric configuration (future):
      # - type: Pods
      #   pods:
      #     metric:
      #       name: scrumpoker_websocket_connections_total
      #     target:
      #       type: AverageValue
      #       averageValue: "1000"
      ```
    - Scale-up policy: add pod when metric exceeds target for 2 minutes (use `behavior` field)
    - Scale-down policy: remove pod when metric below 50% of target for 10 minutes (conservative to avoid thrashing)

*   **Tip: Rolling Update Strategy**
    - Configure `strategy.type: RollingUpdate` with `maxSurge: 1` and `maxUnavailable: 0`
    - This ensures zero-downtime deployments (at least 2 pods always running during updates with the min 2 replicas)
    - Combine with readiness probe to ensure new pods are healthy before old pods are terminated
    - Set `minReadySeconds: 10` to give new pods time to warm up before receiving traffic

*   **Tip: Kustomize Overlays**
    - Base manifests should contain sensible defaults that work for all environments (use placeholders where env-specific)
    - **Dev overlay** should reduce resources:
        - Replicas: 1 (dev doesn't need HA)
        - Requests: `memory: 512Mi, cpu: 250m`
        - Limits: `memory: 1Gi, cpu: 500m`
        - Log level: DEBUG
        - JSON logging: false (human-readable for dev)
    - **Staging overlay** should match production specs for realistic testing:
        - Replicas: 2
        - Resources same as production
        - Log level: INFO
        - JSON logging: true
    - **Production overlay** should have full resources and production logging:
        - Replicas: 2 (HPA will scale beyond this)
        - Requests: `memory: 1Gi, cpu: 500m`
        - Limits: `memory: 2Gi, cpu: 1000m`
        - Log level: WARN (line 217 in application.properties defaults to INFO, so override to WARN)
        - JSON logging: true (for structured log aggregation)
    - Each overlay should patch the ConfigMap with environment-specific values:
        - Database URLs (dev: localhost, staging: staging-db.example.com, prod: prod-db.example.com)
        - Redis URLs (dev: localhost:6379, staging: redis-cluster-staging, prod: redis-cluster-prod)
        - CORS origins (dev: `http://localhost:3000,http://localhost:5173`, staging: `https://staging.scrumpoker.com`, prod: `https://scrumpoker.com`)
        - Feature flags if any

*   **Warning: Database Connection Strings**
    - The application.properties uses BOTH JDBC URL and reactive URL for the database:
        - JDBC URL (line 19): `jdbc:postgresql://localhost:5432/scrumpoker` - used by Flyway migrations
        - Reactive URL (line 20): `postgresql://localhost:5432/scrumpoker` - used by Hibernate Reactive for queries
    - Your ConfigMap MUST provide both `DB_JDBC_URL` and `DB_REACTIVE_URL` environment variables
    - Reactive URL format: `postgresql://host:port/database` (NO "jdbc:" prefix)
    - JDBC URL format: `jdbc:postgresql://host:port/database` (WITH "jdbc:" prefix)
    - Both URLs should point to the same database but use different drivers

*   **Warning: CORS Configuration**
    - Current CORS origins in application.properties are set for local development (line 189): `http://localhost:3000,http://localhost:5173`
    - Your production ConfigMap MUST override `CORS_ORIGINS` with actual frontend domain URLs (e.g., `https://scrumpoker.com,https://www.scrumpoker.com`)
    - Staging should have staging frontend URL: `https://staging.scrumpoker.com`
    - Dev can keep localhost origins
    - CRITICAL: Never use wildcard `*` in production CORS for security reasons

*   **Warning: Port Configuration**
    - Application listens on port 8080 (line 183: `quarkus.http.port=${HTTP_PORT:8080}`)
    - Service should expose port 80 (external) → targetPort 8080 (pod container port)
    - Ingress routes traffic to Service port 80
    - Container must expose containerPort 8080 in the Deployment spec

*   **Note: Namespace Configuration**
    - The architecture specifies namespaces: `production`, `staging`, `development`
    - You SHOULD NOT hard-code namespace in base manifests (makes them reusable)
    - Instead, use Kustomize `namespace` directive in each overlay's kustomization.yaml to set namespace
    - Example overlay kustomization.yaml:
      ```yaml
      namespace: production
      bases:
        - ../../base
      ```

*   **Note: Missing Metrics Server**
    - HPA requires Kubernetes Metrics Server to be installed in the cluster for CPU metrics
    - Add a note in the deployment documentation (future I8.T6) that metrics-server must be installed
    - For now, add a comment in hpa.yaml:
      ```yaml
      # Prerequisites: Kubernetes metrics-server must be installed in the cluster
      # Install: kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
      ```

*   **Note: ConfigMap vs. Environment Variables**
    - ConfigMap should only contain NON-sensitive configuration
    - Secrets should contain ALL sensitive data
    - Mount both ConfigMap and Secret as environment variables using `envFrom` in the Deployment
    - Example:
      ```yaml
      envFrom:
        - configMapRef:
            name: scrum-poker-config
        - secretRef:
            name: scrum-poker-secrets
      ```

### Directory Structure Guidance

Based on my analysis, the `infra/` directory currently only contains `local/` subdirectory for Docker Compose. You will be creating a new `kubernetes/` directory structure:

```
infra/
├── kubernetes/
│   ├── base/
│   │   ├── kustomization.yaml          # Base kustomization file listing all resources
│   │   ├── deployment.yaml             # Main application deployment
│   │   ├── service.yaml                # ClusterIP service
│   │   ├── ingress.yaml                # ALB/Nginx ingress with both controller annotations
│   │   ├── configmap.yaml              # Non-sensitive environment configuration
│   │   ├── secret.yaml                 # TEMPLATE for sensitive data (with warnings)
│   │   └── hpa.yaml                    # HorizontalPodAutoscaler
│   └── overlays/
│       ├── dev/
│       │   ├── kustomization.yaml      # Dev environment patches + namespace
│       │   ├── configmap-patch.yaml    # Dev-specific config (localhost URLs, DEBUG logs)
│       │   └── resources-patch.yaml    # Reduced resource limits for dev
│       ├── staging/
│       │   ├── kustomization.yaml      # Staging environment patches + namespace
│       │   └── configmap-patch.yaml    # Staging-specific config (staging URLs, INFO logs)
│       └── production/
│           ├── kustomization.yaml      # Production environment patches + namespace
│           ├── configmap-patch.yaml    # Production-specific config (prod URLs, WARN logs)
│           └── hpa-patch.yaml          # Production HPA tuning (optional, if different from base)
└── local/                               # Existing local Docker Compose setup
```

### Kustomize Best Practices

*   **Base kustomization.yaml must list all resources:**
    ```yaml
    apiVersion: kustomize.config.k8s.io/v1beta1
    kind: Kustomization

    resources:
      - deployment.yaml
      - service.yaml
      - ingress.yaml
      - configmap.yaml
      - secret.yaml
      - hpa.yaml

    commonLabels:
      app: scrum-poker-backend
      app.kubernetes.io/name: scrum-poker-backend
      app.kubernetes.io/part-of: scrum-poker-platform
    ```

*   **Overlay kustomization.yaml structure:**
    ```yaml
    apiVersion: kustomize.config.k8s.io/v1beta1
    kind: Kustomization

    namespace: production  # or staging, development

    bases:
      - ../../base

    # Override image (if different per env)
    images:
      - name: <IMAGE_PLACEHOLDER>
        newName: 123456789012.dkr.ecr.us-east-1.amazonaws.com/scrum-poker-backend
        newTag: v1.0.0-abc1234

    # Apply patches
    patchesStrategicMerge:
      - configmap-patch.yaml
      - resources-patch.yaml  # (dev only)

    # Add env-specific labels
    commonLabels:
      environment: production
    ```

*   **ConfigMap patch uses Strategic Merge:**
    ```yaml
    apiVersion: v1
    kind: ConfigMap
    metadata:
      name: scrum-poker-config
    data:
      # Only include values that differ from base
      DB_JDBC_URL: "jdbc:postgresql://prod-rds.us-east-1.rds.amazonaws.com:5432/scrumpoker"
      DB_REACTIVE_URL: "postgresql://prod-rds.us-east-1.rds.amazonaws.com:5432/scrumpoker"
      LOG_LEVEL: "WARN"
      LOG_JSON_FORMAT: "true"
      CORS_ORIGINS: "https://scrumpoker.com,https://www.scrumpoker.com"
    ```

### Final Checklist

Before completing this task, ensure:

1. ✅ All 6 base Kubernetes manifests created (deployment, service, ingress, configmap, secret template, hpa)
2. ✅ Base kustomization.yaml lists all resources with commonLabels
3. ✅ 3 overlay directories created (dev, staging, production) each with kustomization.yaml
4. ✅ Deployment has correct resource limits (requests: 1Gi/500m, limits: 2Gi/1000m), health probes, rolling update strategy
5. ✅ Service targets port 8080 (matches application.properties HTTP_PORT)
6. ✅ Ingress has BOTH ALB and Nginx annotations for sticky sessions + TLS placeholder
7. ✅ ConfigMap includes all non-sensitive environment variables from application.properties
8. ✅ Secret template has placeholders for all sensitive values with security WARNING
9. ✅ HPA configured with min: 2, max: 10, CPU target: 70%, scale-up/down behavior
10. ✅ All manifests use consistent labels (app: scrum-poker-backend, etc.)
11. ✅ Dev overlay reduces resources and sets DEBUG logging
12. ✅ Staging overlay matches production specs
13. ✅ Production overlay sets WARN logging and JSON format
14. ✅ All database URLs use BOTH JDBC and reactive formats
15. ✅ CORS origins are environment-specific (localhost for dev, domains for staging/prod)
16. ✅ JVM heap size set via JAVA_OPTS environment variable in Deployment
17. ✅ Comments explain prerequisites (metrics-server) and future enhancements (custom metrics)
18. ✅ Image placeholder clearly marked for Kustomize overlay patching
