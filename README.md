# Planning Poker

# This is a prompt-enginnered codebase

A real-time Scrum Planning Poker application built with Quarkus Reactive, Hibernate Reactive with Panache, Vue.js 3, and PrimeVue.

## CI/CD Status

- **Backend CI**: Validates Java 17 compilation, runs unit and integration tests with Testcontainers, performs SonarQube code quality analysis, and executes security scanning.
- **Frontend CI**: Validates Node.js 18 build, runs ESLint checks, executes test suite, builds production bundle, and uploads build artifacts.

## Features

- **OAuth2 Authentication**: Secure login with Google and Microsoft accounts
- **Session Management**: JWT-based authentication with automatic token refresh
- **Real-time Collaboration**: WebSocket-powered real-time card selection and reveal
- Create and join planning poker rooms via unique room IDs
- Fibonacci sequence voting values (0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, ?, ☕)
- Observer mode for non-voting participants
- Statistics display after cards are revealed (average, consensus, distribution)
- Responsive design with Tailwind CSS

## Tech Stack

- **Backend**: Quarkus with Reactive extensions
  - Hibernate Reactive with Panache for ORM
  - WebSockets for real-time communication
  - Flyway for database migrations
  - PostgreSQL (Reactive driver)
  
- **Frontend**: Vue.js 3
  - PrimeVue component library
  - Vite for build tooling
  - Quinoa for frontend/backend integration

## Prerequisites

- Java 17+
- Maven 3.8+
- Node.js 18+
- Docker and Docker Compose (for local infrastructure)

## Getting Started

### 1. Configure Environment Variables

Copy the environment template and customize it for your local setup:

```bash
cp .env.example .env
```

Edit `.env` and update the following critical values:
- `POSTGRES_PASSWORD` - PostgreSQL database password
- `REDIS_PASSWORD` - Redis authentication password
- `JWT_SECRET` - Must be at least 32 characters (generate with `openssl rand -base64 32`)
- `VITE_GOOGLE_CLIENT_ID` - Google OAuth2 client ID for frontend authentication
- `VITE_MICROSOFT_CLIENT_ID` - Microsoft OAuth2 client ID for frontend authentication

#### RSA Key Pair Generation for JWT Signing

The application uses RSA-256 asymmetric signing for JWT tokens. RSA key pairs should already exist in `backend/src/main/resources/`, but if you need to regenerate them:

```bash
# Generate RSA private key (2048-bit)
openssl genpkey -algorithm RSA -out backend/src/main/resources/privateKey.pem -pkeyopt rsa_keygen_bits:2048

# Extract public key from private key
openssl rsa -pubout -in backend/src/main/resources/privateKey.pem -out backend/src/main/resources/publicKey.pem
```

**Security Notes:**
- The `privateKey.pem` file is already added to `.gitignore` and should NEVER be committed to version control
- For production deployments, load the private key from Kubernetes Secrets or a secure vault (e.g., AWS Secrets Manager, HashiCorp Vault)
- Set the `JWT_PRIVATE_KEY` environment variable in production with the key content instead of using file location
- Public keys can be safely committed to the repository as they are only used for signature verification
- Consider rotating RSA keys periodically (recommended: every 90 days for production systems)

**Production Deployment Example (Kubernetes Secret):**

```bash
# Create Kubernetes Secret with private key
kubectl create secret generic jwt-keys \
  --from-file=privateKey.pem=backend/src/main/resources/privateKey.pem \
  --namespace=planning-poker

# Mount the secret in your deployment and set environment variable
# JWT_PRIVATE_KEY_LOCATION=/secrets/privateKey.pem
```

#### OAuth2 Provider Configuration

The application supports OAuth2 authentication via Google and Microsoft. You'll need to configure OAuth2 clients for both providers.

**Google OAuth2 Setup:**

1. Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Create a new project or select an existing one
3. Navigate to **APIs & Services** > **Credentials**
4. Click **Create Credentials** > **OAuth 2.0 Client ID**
5. Configure the OAuth consent screen if prompted
6. Select **Web application** as the application type
7. Add authorized redirect URIs:
   - For local development: `http://localhost:8080/auth/callback`
   - For production: `https://yourdomain.com/auth/callback`
8. Copy the **Client ID** and update `VITE_GOOGLE_CLIENT_ID` in your `.env` file
9. Required scopes: `openid`, `email`, `profile` (configured automatically)

**Microsoft OAuth2 Setup:**

1. Go to [Azure Portal](https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps)
2. Navigate to **Azure Active Directory** > **App registrations**
3. Click **New registration**
4. Enter a name for your application
5. Under **Supported account types**, select "Accounts in any organizational directory and personal Microsoft accounts"
6. Add redirect URI:
   - Platform: **Web**
   - URI: `http://localhost:8080/auth/callback` (for local dev) or `https://yourdomain.com/auth/callback` (for production)
7. Click **Register**
8. Copy the **Application (client) ID** and update `VITE_MICROSOFT_CLIENT_ID` in your `.env` file
9. Navigate to **API permissions** and ensure the following Microsoft Graph permissions are granted:
   - `openid`
   - `email`
   - `profile`

**Important Notes:**
- The frontend uses **Authorization Code Flow with PKCE**, which doesn't require a client secret in the browser
- Backend OAuth client credentials (`OIDC_CLIENT_ID` and `OIDC_CLIENT_SECRET`) are separate from frontend credentials and are used for token validation
- Ensure redirect URIs match exactly (including protocol and trailing slashes) between your OAuth provider configuration and application settings
- For production, use HTTPS redirect URIs only

### 2. Start Infrastructure Services

Start all infrastructure services (PostgreSQL, Redis cluster, Prometheus, Grafana):

```bash
docker-compose up -d
```

This will start the following services:
- **PostgreSQL 15**: Database server at `localhost:5432`
- **Redis Cluster**: 3-node cluster at `localhost:6379`, `localhost:6380`, `localhost:6381`
- **Prometheus**: Metrics collection at `http://localhost:9090`
- **Grafana**: Monitoring dashboards at `http://localhost:3000` (default credentials: admin/admin)
- **Flyway Migrator**: One-shot container that applies SQL migrations from `backend/src/main/resources/db/migration`

Wait for all services to be healthy (approximately 30 seconds):

```bash
docker-compose ps
```

All services should show status as "healthy". One-shot helpers (`flyway-migrator`, `redis-cluster-init`) will show `Exit 0` after they complete successfully.

### 3. Verify Infrastructure

Check that all services are running correctly:

```bash
# Check PostgreSQL
docker exec -it planning-poker-db pg_isready -U scrumpoker

# Check Redis cluster status
docker exec -it planning-poker-redis-1 redis-cli -a <REDIS_PASSWORD> cluster info

# Check Redis cluster nodes
docker exec -it planning-poker-redis-1 redis-cli -a <REDIS_PASSWORD> cluster nodes

# Verify Redis connectivity from each node
docker exec -it planning-poker-redis-1 redis-cli -a <REDIS_PASSWORD> ping  # Should return PONG
docker exec -it planning-poker-redis-2 redis-cli -p 6380 -a <REDIS_PASSWORD> ping
docker exec -it planning-poker-redis-3 redis-cli -p 6381 -a <REDIS_PASSWORD> ping

# Check Prometheus targets
# Open http://localhost:9090/targets in your browser
# The "quarkus-application" target should show as UP once the backend is running

# Check Grafana
# Open http://localhost:3000 in your browser (login: admin/admin)

# Verify Flyway migrations finished
docker-compose logs flyway-migrator
```

> Replace `<REDIS_PASSWORD>` with the password set in your `.env`. The `.env.example` default is `change_me_in_production_use_strong_password`.

#### Expected Service Ports

| Service | Port(s) | URL | Credentials |
|---------|---------|-----|-------------|
| PostgreSQL | 5432 | `postgresql://localhost:5432/scrumpoker` | scrumpoker / scrumpoker |
| Redis Node 1 | 6379, 16379 | `redis://localhost:6379` | Password: value from `.env` |
| Redis Node 2 | 6380, 16380 | `redis://localhost:6380` | Password: value from `.env` |
| Redis Node 3 | 6381, 16381 | `redis://localhost:6381` | Password: value from `.env` |
| Prometheus | 9090 | http://localhost:9090 | None |
| Grafana | 3000 | http://localhost:3000 | admin / admin |

### 4. Install frontend dependencies (if using frontend)

```bash
cd src/main/webui
npm install
cd ../../..
```

### 5. Run the application in development mode

```bash
./mvnw quarkus:dev
```

The application will be available at:
- **Backend API**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/q/swagger-ui
- **OpenAPI Spec**: http://localhost:8080/q/openapi
- **Health Check**: http://localhost:8080/q/health
- **Prometheus Metrics**: http://localhost:8080/q/metrics
- **Frontend** (via Quinoa): http://localhost:8080

On first startup, Flyway migrations will automatically execute to create the database schema.

### 6. Access Monitoring & Observability

- **Grafana Dashboard**: http://localhost:3000
  - Login: `admin` / `admin` (change on first login)
  - Pre-configured dashboard: "Planning Poker - Quarkus Application Dashboard"
  - Metrics include: HTTP request rates, latency percentiles, JVM memory/threads, WebSocket connections, active rooms, vote rates

- **Prometheus UI**: http://localhost:9090
  - Query metrics directly
  - Check target health at http://localhost:9090/targets
  - Quarkus application should appear as "quarkus-application" target

### 7. Stop Infrastructure Services

To stop all services:

```bash
docker-compose down
```

To stop and remove all data volumes (WARNING: deletes all data):

```bash
docker-compose down -v
```

### Troubleshooting

#### Services Not Starting

If services fail to start, check the logs:

```bash
# View logs for all services
docker-compose logs

# View logs for a specific service
docker-compose logs postgres
docker-compose logs redis-node-1
docker-compose logs prometheus
docker-compose logs grafana
```

#### Port Conflicts

If you see "port already allocated" errors, another service is using the required ports. Check which process is using the port:

```bash
# On macOS/Linux
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis
lsof -i :9090  # Prometheus
lsof -i :3000  # Grafana

# On Windows
netstat -ano | findstr :5432
```

You can change the host port mappings in your `.env` file:

```bash
POSTGRES_PORT=5433  # Change PostgreSQL to port 5433
```

#### Redis Cluster Not Forming

If Redis cluster doesn't form properly:

```bash
# Check if cluster init container ran
docker-compose ps redis-cluster-init

# Re-run cluster initialization
docker-compose up redis-cluster-init

# Verify cluster state
docker exec -it planning-poker-redis-1 redis-cli -a <REDIS_PASSWORD> cluster info
```

#### Prometheus Not Scraping Quarkus Metrics

If Prometheus shows the "quarkus-application" target as DOWN:

1. Ensure the Quarkus backend is running (`./mvnw quarkus:dev`)
2. Verify metrics endpoint is accessible: `curl http://localhost:8080/q/metrics`
3. Check Prometheus logs: `docker-compose logs prometheus`

#### Grafana Dashboard Not Showing Data

If the Grafana dashboard is empty:

1. Verify Prometheus datasource is configured: Go to Configuration → Data Sources
2. Test the Prometheus connection: Click "Test" button on the Prometheus datasource
3. Check that Quarkus is exposing metrics: `curl http://localhost:8080/q/metrics`
4. Wait a few minutes for metrics to be collected

#### Database Migration Failures

If Flyway migrations fail on startup:

```bash
# Check PostgreSQL logs
docker-compose logs postgres

# Check Flyway migrator logs
docker-compose logs flyway-migrator

# Connect to database and check migration status
docker exec -it planning-poker-db psql -U scrumpoker -d scrumpoker -c "SELECT * FROM flyway_schema_history;"

# Re-run migrations if needed
docker-compose up flyway-migrator

# If needed, reset the database (WARNING: deletes all data)
docker-compose down -v
docker-compose up -d postgres
```

## Authentication Flow

The application implements OAuth2 Authorization Code Flow with PKCE for enhanced security:

### OAuth2 Flow Overview

1. **User clicks "Sign in with Google/Microsoft"**
   - Frontend generates a cryptographically random PKCE code verifier
   - Computes SHA-256 hash of verifier to create code challenge
   - Stores verifier in sessionStorage for later use
   - Redirects user to OAuth provider with code challenge

2. **User authenticates at OAuth provider**
   - User grants permission to the application
   - OAuth provider redirects back with authorization code

3. **Frontend exchanges code for tokens**
   - Callback page extracts authorization code from URL
   - Retrieves PKCE verifier from sessionStorage
   - Sends code + verifier to backend `/api/v1/auth/oauth/callback`

4. **Backend validates and issues JWT tokens**
   - Exchanges code for OAuth provider tokens
   - Validates ID token signature and claims
   - Creates or updates user record in database
   - Issues application JWT access and refresh tokens
   - Returns tokens and user data to frontend

5. **Frontend stores authentication state**
   - Stores tokens in localStorage
   - Updates Zustand auth store with user data
   - Redirects to dashboard

### Protected Routes

Routes requiring authentication are wrapped with `<PrivateRoute>` component:
- `/dashboard` - User dashboard
- `/billing/settings` - Subscription settings
- `/reports/sessions` - Session history
- `/org/:orgId/*` - Organization management

Unauthenticated users are automatically redirected to `/login`.

### Token Management

- **Access Token**: JWT with 1-hour expiration, stored in localStorage
- **Refresh Token**: 30-day expiration, stored in localStorage
- Tokens are automatically included in API requests via Authorization header
- Token refresh is handled automatically when access token expires

## Development

### Backend Development

The backend uses Quarkus reactive stack. Main packages:
- `entity` - JPA entities with Panache
- `resource` - REST endpoints (including `AuthController`)
- `service` - Business logic (including `AuthService`, `UserService`)
- `websocket` - WebSocket endpoints and messages
- `dto` - Data transfer objects

### Frontend Development

The frontend is built with React, TypeScript, and Tailwind CSS. Key directories:
- `frontend/src/pages/` - Page components (LoginPage, OAuthCallbackPage, DashboardPage, etc.)
- `frontend/src/components/` - Reusable components (PrivateRoute, common UI components)
- `frontend/src/stores/` - Zustand state management (authStore, roomStore)
- `frontend/src/hooks/` - Custom React hooks (useAuth, useWebSocket)
- `frontend/src/utils/` - Utility functions (PKCE implementation)
- `frontend/src/types/` - TypeScript type definitions

For frontend-only development with hot reload:

```bash
cd frontend
npm run dev
```

This will start Vite dev server on http://localhost:5173 with proxy to backend at http://localhost:8080.

### Database Migrations

Flyway migrations are located in `src/main/resources/db/migration/`. They run automatically on startup.

## Building for Production

```bash
./mvnw clean package
```

This will:
1. Build the Vue.js frontend
2. Package it with Quarkus using Quinoa
3. Create an executable JAR

Run the production build:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## How to Use

1. Navigate to the home page
2. Create a new room with a name, or join an existing room with its ID
3. Enter your username when joining a room
4. Select your estimation card (if not an observer)
5. Wait for all participants to vote
6. Click "Reveal Cards" to show all votes
7. Review statistics and discuss
8. Click "New Round" to start another estimation

## Smoke Testing

Smoke tests verify critical user journeys work end-to-end in production-like environments. They run automatically after deployment to catch regressions before they impact users.

### What Are Smoke Tests?

Smoke tests are a subset of automated tests that verify the most critical functionality of the application:

- **Fast Execution**: Complete in <5 minutes (vs full E2E test suite which takes longer)
- **Critical Paths Only**: Test happy paths for core features (login, voting, payments)
- **Environment Agnostic**: Run against staging or production with configurable URLs
- **Deployment Safety**: Integrated into CI/CD pipeline with automatic rollback on failure

### Test Coverage

The smoke test suite covers 6+ critical user journeys:

**Frontend Smoke Tests (Playwright):**
1. **OAuth Login Journey** - User authentication via Google/Microsoft OAuth
2. **Room Creation + Voting Flow** - Multi-user voting with WebSocket synchronization
3. **Subscription Upgrade Journey** - Stripe checkout and webhook processing (placeholder)

**Backend Smoke Tests (REST Assured):**
4. **Room Creation via API** - Room persistence and retrieval
5. **Multi-Room Management** - List rooms, pagination, soft delete
6. **Report Export Journey** - Async job processing, CSV download (placeholder)

### Running Smoke Tests Locally

**Frontend smoke tests:**

```bash
cd frontend

# Run against local dev server
npm run test:smoke

# Run against staging environment
npm run test:smoke:staging

# Run against production (requires auth credentials)
npm run test:smoke:prod
```

**Backend smoke tests:**

```bash
cd backend

# Run all smoke tests
mvn test -Dtest=SmokeTestSuite

# Run specific smoke test
mvn test -Dtest=SmokeTestSuite#smokeCriticalJourney_RoomCreation
```

### Environment Configuration

Smoke tests use environment variables for configuration:

**Frontend (Playwright):**
- `BASE_URL` - Base URL of the application (default: `http://localhost:5173`)
- `SMOKE_TEST_MOCK_OAUTH` - Use mocked OAuth (`true` for staging, `false` for prod)
- `STRIPE_TEST_MODE` - Use Stripe test mode (default: `true`)

**Backend (REST Assured):**
- `SMOKE_BASE_URL` - Base URL of the API (default: `http://localhost:8080`)

**Example:**

```bash
# Run frontend smoke tests against staging
BASE_URL=https://staging.planningpoker.example.com \
SMOKE_TEST_MOCK_OAUTH=true \
npm run test:smoke

# Run backend smoke tests against production API
SMOKE_BASE_URL=https://api.planningpoker.example.com \
mvn test -Dtest=SmokeTestSuite
```

### CI/CD Integration

Smoke tests are integrated into the deployment pipeline:

**Staging Deployment (`deploy-staging.yml`):**
1. Build and push Docker images
2. Deploy to staging Kubernetes cluster
3. Run smoke tests against staging
4. If smoke tests fail → Block production deployment
5. If smoke tests pass → Allow production deployment

**Production Deployment (`deploy-production.yml`):**
1. Build and push Docker images
2. Deploy to production Kubernetes cluster
3. Run smoke tests against production
4. If smoke tests fail → **Automatic rollback** + Slack alert
5. If smoke tests pass → Deployment successful

**Automatic Rollback:**

When smoke tests fail in production:
- Kubernetes deployment is automatically rolled back to previous version
- DevOps team receives Slack alert with test failure details
- Workflow exits with error status (visible in GitHub Actions)
- Test results are uploaded as artifacts for investigation

### Troubleshooting Smoke Tests

**Smoke tests fail locally but pass in CI:**
- Verify backend is running (`http://localhost:8080`)
- Check WebSocket connection (port 8080 should be accessible)
- Ensure test database has required schema (run migrations)

**OAuth login test fails:**
- For staging: Ensure `SMOKE_TEST_MOCK_OAUTH=true` (mocked OAuth)
- For production: Requires real OAuth test account credentials
- Verify OAuth redirect URIs match environment

**Voting flow test fails:**
- Backend must be running for WebSocket connection
- Check that room `e2e-test-room` exists or test creates new room
- Verify Redis is running (required for WebSocket message broker)

**Smoke tests timeout:**
- Increase timeout in `playwright.config.ts` (actionTimeout, navigationTimeout)
- Check network latency to staging/production environment
- Verify application is healthy (not under heavy load)

**Backend smoke tests fail to connect:**
- Verify `SMOKE_BASE_URL` points to correct API endpoint
- Check API is accessible (not behind VPN or firewall)
- Ensure test user exists in target environment

### Test Execution Reports

After running smoke tests, view detailed reports:

**Frontend (Playwright):**
```bash
cd frontend
npm run test:smoke

# Open HTML report
npx playwright show-report
```

**Backend (REST Assured):**
```bash
cd backend
mvn test -Dtest=SmokeTestSuite

# View surefire reports
open target/surefire-reports/index.html
```

**CI/CD Artifacts:**
- Test results are automatically uploaded as GitHub Actions artifacts
- Retention: 7 days for staging, 30 days for production
- Download artifacts from workflow run page

### Adding New Smoke Tests

**Frontend (Playwright):**

1. Open `frontend/e2e/smoke-tests.spec.ts`
2. Add new test in `test.describe('Smoke Tests @smoke', () => { ... })`
3. Follow existing patterns (use helpers from `smokeTestHelpers.ts`)
4. Keep tests fast (<30 seconds each)
5. Test happy path only (no edge cases)

**Backend (REST Assured):**

1. Open `backend/src/test/java/com/scrumpoker/smoke/SmokeTestSuite.java`
2. Add new test method with `@Test` annotation
3. Use `@Tag("smoke")` for filtering
4. Follow REST Assured patterns from existing tests
5. Focus on API happy paths

**Best Practices:**
- Keep smoke tests independent (no shared state)
- Use unique identifiers (timestamps, UUIDs) for test data
- Clean up test data if possible (or use unique prefixes)
- Add clear logging for debugging (`console.log`, `System.out.println`)
- Verify critical assertions only (don't test every field)

## Operations Documentation

For production deployment and operations, see the comprehensive operations guides:

- **[Deployment Guide](docs/operations/DEPLOYMENT_GUIDE.md)** - Step-by-step production deployment procedures
- **[Operations Runbook](docs/operations/OPERATIONS_RUNBOOK.md)** - Common administrative tasks (scaling, logs, restarts, backups)
- **[Monitoring Guide](docs/operations/MONITORING_GUIDE.md)** - Dashboard usage, alert triage, and metrics interpretation
- **[Troubleshooting Guide](docs/operations/TROUBLESHOOTING_GUIDE.md)** - Diagnostic procedures and solutions for common issues
- **[Disaster Recovery](docs/operations/DISASTER_RECOVERY.md)** - Backup, restore, and disaster recovery procedures

Additional technical documentation:

- **[Security Assessment](docs/security-assessment.md)** - Security hardening and production configurations
- **[Performance Benchmarks](docs/performance-benchmarks.md)** - Load testing results and performance tuning

## Project Structure

```
planning-poker/
├── backend/                    # Quarkus backend
│   ├── src/main/java/          # Application code
│   ├── src/main/resources/     # Configuration and migrations
│   └── src/test/               # Backend tests
├── frontend/                   # Vue.js frontend
│   ├── src/                    # Frontend source code
│   └── public/                 # Static assets
├── infra/                      # Infrastructure as code
│   ├── kubernetes/             # Kubernetes manifests
│   └── monitoring/             # Prometheus and Grafana configs
├── docs/                       # Documentation
│   └── operations/             # Operations guides
├── docker-compose.yml          # Local development infrastructure
└── README.md
```
