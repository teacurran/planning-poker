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
