# Scrum Poker Backend

Quarkus-based reactive backend for the Scrum Poker Platform.

## Technology Stack

- **Framework**: Quarkus 3.15.1 (Reactive)
- **Language**: Java 17
- **Database**: PostgreSQL 15+ with Hibernate Reactive + Panache
- **Cache/Messaging**: Redis 7+ for Pub/Sub and caching
- **Authentication**: OAuth2/OIDC + SmallRye JWT
- **Metrics**: Micrometer with Prometheus registry

## Project Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/scrumpoker/
│   │   │   ├── api/          # REST controllers & WebSocket handlers
│   │   │   ├── domain/       # Domain entities & business logic
│   │   │   ├── repository/   # Panache repositories
│   │   │   ├── integration/  # External service adapters
│   │   │   ├── event/        # Redis Pub/Sub components
│   │   │   ├── config/       # Application configuration
│   │   │   └── security/     # Authentication & authorization
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/ # Flyway SQL migrations
│   └── test/
│       ├── java/             # Unit & integration tests
│       └── resources/
│           └── application-test.properties
└── pom.xml
```

## Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 15+
- Redis 7+

## Getting Started

### 1. Build the Project

```bash
cd backend
mvn clean compile
```

### 2. Configure Environment Variables

The application uses environment variables for configuration. Default values are provided for local development:

```bash
# Database
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export DB_JDBC_URL=jdbc:postgresql://localhost:5432/scrumpoker
export DB_REACTIVE_URL=postgresql://localhost:5432/scrumpoker

# Redis
export REDIS_URL=redis://localhost:6379

# JWT (use proper keys in production)
export JWT_ISSUER=https://scrumpoker.com
export JWT_PRIVATE_KEY_LOCATION=/path/to/privateKey.pem
export JWT_PUBLIC_KEY_LOCATION=/path/to/publicKey.pem

# OIDC (OAuth2)
export OIDC_CLIENT_ID=your-client-id
export OIDC_CLIENT_SECRET=your-client-secret
export OIDC_AUTH_SERVER_URL=https://accounts.google.com
```

### 3. Start Development Server

```bash
mvn quarkus:dev
```

The application will be available at:
- **API**: http://localhost:8080/
- **Health Check**: http://localhost:8080/q/health
- **Metrics**: http://localhost:8080/q/metrics
- **OpenAPI**: http://localhost:8080/q/openapi
- **Swagger UI**: http://localhost:8080/q/swagger-ui

### 4. Run Tests

```bash
mvn test
```

## Development Features

- **Live Reload**: Code changes are automatically detected and reloaded
- **Dev UI**: Access at http://localhost:8080/q/dev
- **Hot Reload**: Frontend and backend changes trigger automatic recompilation

## Configuration Profiles

- **dev**: Development mode (debug logging, CORS enabled, detailed errors)
- **test**: Test environment (separate database, disabled external services)
- **prod**: Production mode (optimized logging, security hardened)

## Quarkus Extensions

This project includes the following Quarkus extensions:

- `quarkus-arc` - CDI dependency injection
- `quarkus-rest` / `quarkus-rest-jackson` - Reactive REST with JSON
- `quarkus-websockets` - WebSocket support
- `quarkus-hibernate-reactive-panache` - Reactive ORM
- `quarkus-reactive-pg-client` - Non-blocking PostgreSQL driver
- `quarkus-redis-client` - Redis integration
- `quarkus-oidc` - OAuth2/OIDC authentication
- `quarkus-smallrye-jwt` - JWT token support
- `quarkus-micrometer-registry-prometheus` - Prometheus metrics
- `quarkus-flyway` - Database migrations
- `quarkus-smallrye-health` - Health checks
- `quarkus-smallrye-openapi` - OpenAPI documentation

## Building for Production

### JVM Mode

```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

### Native Compilation

```bash
mvn clean package -Pnative
./target/scrum-poker-backend-1.0.0-SNAPSHOT-runner
```

## Architecture

This backend follows hexagonal architecture (ports and adapters) with clear separation of concerns:

- **Domain Layer**: Core business logic independent of infrastructure
- **API Layer**: REST and WebSocket endpoints (primary adapters)
- **Repository Layer**: Data access using Panache repositories (secondary adapters)
- **Integration Layer**: External service clients (secondary adapters)
- **Event Layer**: Asynchronous messaging via Redis Pub/Sub
- **Security Layer**: Authentication and authorization concerns

## Next Steps

1. Implement database schema migrations (Flyway)
2. Create domain entities and repositories
3. Build REST API endpoints
4. Implement WebSocket handlers for real-time features
5. Set up integration tests with Testcontainers

## Resources

- [Quarkus Documentation](https://quarkus.io/guides/)
- [Hibernate Reactive](https://hibernate.org/reactive/)
- [Mutiny Reactive Programming](https://smallrye.io/smallrye-mutiny/)
- [Redis Pub/Sub](https://redis.io/docs/manual/pubsub/)
