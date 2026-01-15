# Backend Project Structure

This document provides a comprehensive overview of the Scrum Poker backend project structure, created as part of iteration I1.T1.

## Directory Overview

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/scrumpoker/          # Application source code
│   │   └── resources/                    # Configuration and resources
│   └── test/
│       ├── java/com/scrumpoker/          # Test source code
│       └── resources/                    # Test configuration
├── target/                               # Build output (gitignored)
├── pom.xml                               # Maven project descriptor
├── generate-keys.sh                      # JWT key pair generator
├── verify-setup.sh                       # Setup verification script
└── I1.T1-IMPLEMENTATION-SUMMARY.md       # Task completion summary
```

## Java Package Structure

### `/src/main/java/com/scrumpoker/`

#### **api/** - Presentation Layer
External-facing HTTP and WebSocket endpoints.

- **rest/** - RESTful API controllers
  - **dto/** - Data Transfer Objects for API requests/responses
  - **mapper/** - MapStruct entity-to-DTO converters
  - **exception/** - Global exception handlers
  - Resource classes for authentication, users, rooms, billing, etc.

- **websocket/** - WebSocket endpoints and handlers
  - **handler/** - Protocol-specific message handlers
  - WebSocket server implementation for real-time room collaboration

#### **domain/** - Business Logic Layer
Core business entities, services, and domain logic.

- **user/** - User management domain
  - User, UserPreference entities
  - UserService with authentication and profile management
  - Subscription tier management

- **room/** - Estimation room domain
  - Room, Round, Vote entities
  - RoomService, VotingService
  - Room lifecycle management
  - Real-time voting orchestration

- **billing/** - Payment and subscription domain
  - Subscription, Payment entities
  - StripeService integration
  - Subscription lifecycle management

- **reporting/** - Analytics and export domain
  - SessionHistory entity
  - Report generation services
  - CSV/PDF export functionality

- **organization/** - Enterprise organization domain
  - Organization, OrgMember entities
  - Organization management service
  - Team and role management

#### **repository/** - Data Access Layer
Panache repository implementations for database operations.

- Reactive Panache repositories for all entities
- Custom query methods
- Transaction management

#### **integration/** - External Service Adapters
Third-party service integrations following Hexagonal Architecture.

- **oauth/** - OAuth2 provider clients
  - Google OAuth2 client
  - Microsoft OAuth2 client

- **sso/** - Single Sign-On adapters
  - OIDC integration
  - SAML2 integration

- **stripe/** - Stripe payment gateway
  - Stripe SDK wrapper
  - Webhook event processing

- **s3/** - AWS S3 storage client
  - Export file upload/download
  - Presigned URL generation

#### **event/** - Event-Driven Architecture
Redis Pub/Sub for real-time messaging and async processing.

- **RoomEventPublisher** - Publishes room events to Redis Pub/Sub
- **RoomEventSubscriber** - Consumes room events for WebSocket broadcast
- **AuditEvent** - Audit trail event model
- Event serialization/deserialization

#### **config/** - Application Configuration
Quarkus configuration and dependency injection setup.

- Database connection configuration
- Redis client configuration
- Security configuration
- Application-wide beans

#### **security/** - Authentication & Authorization
Security filters, JWT utilities, and access control.

- JWT token generation and validation
- Security context management
- Role-based access control (RBAC)
- Authentication filters

#### **metrics/** - Observability
Custom business metrics for Prometheus.

- WebSocket connection gauges
- Room/vote counters
- Latency histograms
- Business KPI metrics

#### **logging/** - Structured Logging
MDC (Mapped Diagnostic Context) utilities for correlation.

- Correlation ID management
- Request/WebSocket session tracking
- Structured JSON log formatting
- Log context propagation

#### **worker/** - Async Job Workers
Background job processing using Redis Streams.

- Report generation workers
- Email notification workers
- Cleanup/maintenance workers

## Resource Files

### `/src/main/resources/`

#### **application.properties**
Primary configuration file with 394 lines covering:
- Database connection (PostgreSQL reactive pool + Flyway)
- Redis connection and Pub/Sub
- JWT signing/verification
- OIDC providers (Google, Microsoft)
- SAML2 SSO configuration
- Stripe API integration
- AWS S3 credentials
- WebSocket settings
- HTTP/CORS configuration
- Health checks and metrics
- Structured logging
- Environment-specific profiles (dev, staging, prod, test)

**Configuration Philosophy:**
- All secrets externalized via environment variables
- Placeholder values for local development
- Production-ready defaults with tuning comments
- Comprehensive inline documentation

#### **db/migration/** (Flyway)
SQL migration scripts for database schema evolution.
- Version-controlled schema changes
- Incremental migrations
- Rollback support

**Note:** Migration scripts will be created in I1.T3.

## Test Structure

### `/src/test/java/com/scrumpoker/`

Mirror structure of main source tree:

- **api/** - Integration tests for REST/WebSocket endpoints
- **domain/** - Unit tests for business logic
- **repository/** - Repository tests with Testcontainers
- **integration/** - External service adapter tests

**Testing Stack:**
- JUnit 5 - Test framework
- Rest Assured - REST API testing
- AssertJ - Fluent assertions
- Mockito - Mocking framework
- Testcontainers - Containerized integration tests
- Quarkus Test - Quarkus-specific test utilities

### `/src/test/resources/`

- **application.properties** - Test-specific configuration
  - Overrides for test environment
  - Dev Services (Testcontainers) configuration

## Maven Build

### pom.xml Highlights

**Project Coordinates:**
- Group ID: `com.scrumpoker`
- Artifact ID: `scrum-poker-backend`
- Version: `1.0.0-SNAPSHOT`

**Technology:**
- Quarkus: 3.15.1
- Java: 17 (LTS)
- Encoding: UTF-8

**Key Dependencies:**
- Quarkus reactive stack (REST, WebSockets, Hibernate Reactive)
- PostgreSQL reactive driver
- Redis client
- JWT & OIDC security
- Prometheus metrics
- Stripe Java SDK
- MapStruct
- OpenSAML (SAML2)
- AWS S3

**Build Plugins:**
1. Quarkus Maven Plugin - Dev mode, code generation, native builds
2. Maven Compiler Plugin - Java 17, MapStruct processor
3. Maven Surefire - Unit tests
4. Maven Failsafe - Integration tests
5. JaCoCo - Code coverage (80% threshold)

**Build Profiles:**
- Default (JVM) - Fast development cycle
- Native - GraalVM native image compilation

## Architecture Principles

### Hexagonal Architecture (Ports & Adapters)

**Core Domain (domain/):**
- Pure business logic
- No external dependencies
- Framework-agnostic

**Primary Ports (api/):**
- REST controllers
- WebSocket handlers
- Inbound adapters

**Secondary Ports (integration/):**
- OAuth2 clients
- Stripe gateway
- S3 storage
- Email service
- Outbound adapters

**Infrastructure (repository/, event/, security/):**
- Database persistence
- Message bus
- Authentication

### Reactive Programming

**Benefits:**
- Non-blocking I/O for 5,000+ concurrent WebSocket connections
- Efficient resource utilization
- Backpressure handling

**Implementation:**
- Mutiny reactive streams
- Reactive Panache repositories
- Reactive PostgreSQL client
- Reactive Redis client

### Domain-Driven Design

**Bounded Contexts:**
- User & Authentication
- Room & Voting (core domain)
- Billing & Subscriptions
- Reporting & Analytics
- Organization & Teams

**Aggregates:**
- Room (aggregates Round, Vote, Participant)
- Organization (aggregates OrgMember, Team)
- User (aggregates UserPreference)

## Configuration Management

### Environment Variables

All sensitive configuration externalized via environment variables:

**Database:**
- `DB_USERNAME`, `DB_PASSWORD`
- `DB_JDBC_URL`, `DB_REACTIVE_URL`
- `DB_POOL_MAX_SIZE`

**Redis:**
- `REDIS_URL`
- `REDIS_POOL_MAX_SIZE`

**JWT:**
- `JWT_ISSUER`
- `JWT_PUBLIC_KEY_LOCATION` / `JWT_PRIVATE_KEY_LOCATION`
- `JWT_TOKEN_EXPIRATION`

**OAuth2:**
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`

**Stripe:**
- `STRIPE_API_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `STRIPE_PRICE_PRO`, `STRIPE_PRICE_PRO_PLUS`, `STRIPE_PRICE_ENTERPRISE`

**AWS:**
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
- `S3_REGION`, `S3_BUCKET_NAME`

### Profile-Specific Configuration

**%dev (Development):**
- Verbose logging (DEBUG level)
- SQL logging enabled
- Permissive CORS
- Redis health check disabled (for local dev without Redis)

**%test (Testing):**
- Testcontainers Dev Services
- In-memory configuration
- Isolated test data

**%staging (Staging):**
- INFO logging
- Structured JSON logs
- Production-like settings

**%prod (Production):**
- WARN logging (errors only)
- Structured JSON logs
- Optimized thread pools
- Connection pool sizing for 5,000 connections
- Health checks enabled

## Observability

### Metrics (Prometheus)

**Endpoint:** `/q/metrics`

**Custom Metrics:**
- `websocket_connections_active` - Active WebSocket connections
- `room_active_count` - Number of active rooms
- `vote_cast_total` - Total votes cast counter
- `vote_reveal_duration_seconds` - Voting round latency histogram

**JVM Metrics:**
- Heap/non-heap memory
- GC pauses
- Thread count
- Class loading

### Logging

**Format:** Structured JSON

**MDC Fields:**
- `correlationId` - Request/WebSocket session UUID
- `userId` - Authenticated user ID
- `roomId` - Room context
- `action` - Business action (e.g., "vote.cast", "room.created")

**Log Levels:**
- ERROR - Critical failures, exceptions
- WARN - Degraded service, recoverable errors
- INFO - API requests, business events
- DEBUG - Detailed diagnostic info

**Aggregation:**
- Grafana Loki (Kubernetes)
- AWS CloudWatch Logs
- GCP Cloud Logging

### Health Checks

**Endpoint:** `/q/health`

**Checks:**
- Database connectivity (PostgreSQL)
- Redis connectivity
- Disk space
- Memory usage

**Readiness vs. Liveness:**
- Liveness: Application process running
- Readiness: All dependencies available

## Development Workflow

### Local Development

```bash
# Start Quarkus in dev mode (hot reload)
cd backend
../mvnw quarkus:dev

# Application runs on http://localhost:8080
# Swagger UI: http://localhost:8080/q/swagger-ui
# Health: http://localhost:8080/q/health
# Metrics: http://localhost:8080/q/metrics
```

**Dev Mode Features:**
- Hot reload (code changes applied immediately)
- Dev UI at `/q/dev`
- Continuous testing
- Live coding

### Building

```bash
# Clean build
../mvnw clean compile

# Run tests
../mvnw test

# Package (creates uber-jar)
../mvnw package

# Skip tests
../mvnw package -DskipTests

# Integration tests
../mvnw verify
```

### Testing

```bash
# Unit tests only
../mvnw test

# Integration tests with Testcontainers
../mvnw verify

# Code coverage report
../mvnw jacoco:report
# Report: target/site/jacoco/index.html

# Continuous testing in dev mode
../mvnw quarkus:dev
# Press 'r' to run tests
```

### Docker Build

```bash
# JVM mode (faster startup)
docker build -f src/main/docker/Dockerfile.jvm -t scrum-poker-backend:jvm .

# Native mode (smaller image, faster runtime)
../mvnw package -Dnative -Dquarkus.native.container-build=true
docker build -f src/main/docker/Dockerfile.native -t scrum-poker-backend:native .
```

## Next Steps (Iteration I1)

### I1.T2 - Docker Compose Setup
- PostgreSQL container
- Redis container
- Local development environment

### I1.T3 - Database Schema
- Flyway migration scripts
- Entity-relationship schema
- Indexes and constraints

### I1.T4 - CI/CD Pipeline
- GitHub Actions workflows
- Automated testing
- Docker image builds
- Deployment automation

## References

- [Quarkus Documentation](https://quarkus.io/guides/)
- [Architecture Blueprint](../docs/02_Architecture_Overview.md)
- [Implementation Plan](../docs/plan/01_Plan_Overview_and_Setup.md)
- [API Specification](../api/openapi.yaml)
