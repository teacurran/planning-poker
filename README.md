# Planning Poker

A real-time Scrum Planning Poker application built with Quarkus Reactive, Hibernate Reactive with Panache, Vue.js 3, and PrimeVue.

## Features

- Create and join planning poker rooms via unique room IDs
- Real-time card selection and reveal using WebSockets
- Fibonacci sequence voting values (0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, ?, ☕)
- Observer mode for non-voting participants
- Instant vote updates visible to all participants
- Statistics display after cards are revealed (average, consensus, distribution)
- Responsive design with PrimeVue components

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

Wait for all services to be healthy (approximately 30 seconds):

```bash
docker-compose ps
```

All services should show status as "healthy".

### 3. Verify Infrastructure

Check that all services are running correctly:

```bash
# Check PostgreSQL
docker exec -it planning-poker-db pg_isready -U scrumpoker

# Check Redis cluster status
docker exec -it planning-poker-redis-1 redis-cli -a redispassword cluster info

# Check Prometheus targets
# Open http://localhost:9090/targets in your browser

# Check Grafana
# Open http://localhost:3000 in your browser (login: admin/admin)
```

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

## Development

### Backend Development

The backend uses Quarkus reactive stack. Main packages:
- `entity` - JPA entities with Panache
- `resource` - REST endpoints
- `service` - Business logic
- `websocket` - WebSocket endpoints and messages
- `dto` - Data transfer objects

### Frontend Development

For frontend-only development with hot reload:

```bash
cd src/main/webui
npm run dev
```

This will start Vite dev server on http://localhost:3000 with proxy to backend.

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

## Project Structure

```
planning-poker/
├── src/
│   ├── main/
│   │   ├── java/               # Quarkus backend
│   │   ├── resources/          # Configuration and migrations
│   │   └── webui/              # Vue.js frontend
│   └── test/
├── docker-compose.yml          # PostgreSQL setup
├── pom.xml                     # Maven configuration
└── README.md
```