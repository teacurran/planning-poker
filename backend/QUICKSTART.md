# Backend Quick Start Guide

Get the Scrum Poker backend up and running in minutes.

## Prerequisites

- **Java 17** (LTS) - [Download OpenJDK](https://adoptium.net/)
- **Maven 3.8+** (or use included wrapper `./mvnw`)
- **Docker & Docker Compose** (for PostgreSQL and Redis)
- **Git**

## Verify Installation

```bash
# Check Java version (must be 17+)
java -version

# Check Maven version
mvn -version

# Check Docker
docker --version
docker-compose --version
```

## Quick Start (5 Minutes)

### 1. Clone the Repository

```bash
git clone <repository-url>
cd planning-poker/backend
```

### 2. Generate JWT Keys

```bash
# Generate RSA key pair for JWT signing
chmod +x generate-keys.sh
./generate-keys.sh

# This creates:
# - privateKey.pem (gitignored)
# - publicKey.pem (gitignored)
```

### 3. Verify Setup

```bash
# Run automated verification script
chmod +x verify-setup.sh
./verify-setup.sh

# Expected output:
# ✓ Maven clean compile successful
# ✓ All required Quarkus extensions found
# ✓ Package structure verified
# ✓ Application properties configured
```

### 4. Build the Project

```bash
# Build without running tests
../mvnw clean compile -DskipTests

# Or build with tests
../mvnw clean verify
```

### 5. Start Dependencies (Docker Compose)

**Note:** Docker Compose configuration will be created in iteration I1.T2.

Once available:

```bash
# Start PostgreSQL and Redis
docker-compose up -d

# Check logs
docker-compose logs -f

# Stop services
docker-compose down
```

### 6. Run in Development Mode

```bash
# Start Quarkus dev mode with hot reload
../mvnw quarkus:dev

# Application starts on http://localhost:8080
# Press 'h' for help, 'q' to quit
```

### 7. Access Endpoints

Once running:

- **Swagger UI:** http://localhost:8080/q/swagger-ui
- **Health Check:** http://localhost:8080/q/health
- **Metrics:** http://localhost:8080/q/metrics
- **Dev UI:** http://localhost:8080/q/dev (dev mode only)

## Development Workflow

### Hot Reload (Dev Mode)

Quarkus dev mode provides instant code reload:

```bash
../mvnw quarkus:dev

# Make changes to Java files
# Changes are applied automatically
# No restart required!
```

### Running Tests

```bash
# Unit tests
../mvnw test

# Integration tests (requires Docker)
../mvnw verify

# Continuous testing in dev mode
../mvnw quarkus:dev
# Press 'r' to run all tests
# Press 'f' to run failed tests
```

### Code Coverage

```bash
# Generate coverage report
../mvnw clean verify

# Open report
open target/site/jacoco/index.html
```

## Configuration

### Environment Variables

For local development, set these environment variables (or use defaults):

```bash
# Database
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export DB_JDBC_URL=jdbc:postgresql://localhost:5432/scrumpoker
export DB_REACTIVE_URL=postgresql://localhost:5432/scrumpoker

# Redis
export REDIS_URL=redis://localhost:6379

# JWT (keys generated via generate-keys.sh)
export JWT_ISSUER=https://localhost:8080
export JWT_PUBLIC_KEY_LOCATION=/path/to/publicKey.pem
export JWT_PRIVATE_KEY_LOCATION=/path/to/privateKey.pem

# OAuth2 (optional for local dev)
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret

# Stripe (use test keys)
export STRIPE_API_KEY=sk_test_...
export STRIPE_WEBHOOK_SECRET=whsec_test_...
```

**Tip:** Create a `.env` file (gitignored) and source it:

```bash
# .env
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
# ... other variables

# Load environment
source .env
```

### Application Profiles

Quarkus supports multiple configuration profiles:

```bash
# Development (default)
../mvnw quarkus:dev

# Test profile
../mvnw test -Dquarkus.profile=test

# Production profile
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod
```

## Common Tasks

### Add a New Dependency

Edit `pom.xml` and add to `<dependencies>`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-new-extension</artifactId>
</dependency>
```

Then refresh:

```bash
../mvnw clean compile
```

### Create a New REST Endpoint

1. Create controller in `src/main/java/com/scrumpoker/api/rest/`:

```java
@Path("/api/v1/example")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExampleController {

    @GET
    public Uni<Response> getExample() {
        return Uni.createFrom().item(Response.ok("Hello").build());
    }
}
```

2. Restart dev mode (or it auto-reloads)

3. Test at http://localhost:8080/api/v1/example

### Create a Database Migration

Create a new file in `src/main/resources/db/migration/`:

```sql
-- V3__add_example_table.sql
CREATE TABLE example (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

Flyway will apply automatically on startup.

### Add a Custom Metric

```java
@Inject
MeterRegistry registry;

public void trackVote() {
    registry.counter("votes.cast").increment();
}
```

View at http://localhost:8080/q/metrics

## Troubleshooting

### Build Fails

```bash
# Clear Maven cache and rebuild
rm -rf ~/.m2/repository/com/scrumpoker
../mvnw clean install
```

### Port Already in Use

```bash
# Change port in application.properties
quarkus.http.port=8081

# Or via environment variable
export HTTP_PORT=8081
../mvnw quarkus:dev
```

### Database Connection Fails

```bash
# Check PostgreSQL is running
docker ps | grep postgres

# Check connection details
psql -h localhost -U postgres -d scrumpoker

# Verify application.properties has correct URL
```

### Redis Connection Fails

```bash
# Check Redis is running
docker ps | grep redis

# Test connection
redis-cli -h localhost -p 6379 ping

# Dev mode can run without Redis (health check disabled)
```

### Hot Reload Not Working

```bash
# Ensure you're in dev mode
../mvnw quarkus:dev

# Check IDE auto-compile is enabled
# IntelliJ: Build > Build Project Automatically

# Force rebuild
../mvnw compile
```

## IDE Setup

### IntelliJ IDEA

1. **Import Project:**
   - File > Open > Select `backend/pom.xml`
   - Import as Maven project

2. **Enable Annotation Processing:**
   - Settings > Build, Execution, Deployment > Compiler > Annotation Processors
   - Enable annotation processing

3. **Run Configuration:**
   - Run > Edit Configurations
   - Add new "Maven" configuration
   - Command line: `quarkus:dev`
   - Working directory: `backend/`

### VS Code

1. **Install Extensions:**
   - Java Extension Pack
   - Quarkus Tools

2. **Open Folder:**
   - File > Open Folder > Select `backend/`

3. **Run:**
   - Terminal > New Terminal
   - `../mvnw quarkus:dev`

## Project Structure Quick Reference

```
backend/
├── src/main/java/com/scrumpoker/
│   ├── api/           # REST & WebSocket endpoints
│   ├── domain/        # Business logic
│   ├── repository/    # Database access
│   ├── integration/   # External services
│   ├── event/         # Pub/Sub events
│   ├── config/        # Configuration
│   └── security/      # Auth & JWT
├── src/main/resources/
│   ├── application.properties    # Configuration
│   └── db/migration/             # SQL migrations
└── src/test/         # Tests
```

See [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) for detailed documentation.

## Next Steps

1. **Read the Architecture:**
   - [Architecture Overview](../docs/02_Architecture_Overview.md)
   - [Implementation Plan](../docs/plan/01_Plan_Overview_and_Setup.md)

2. **Explore the API:**
   - Start dev mode: `../mvnw quarkus:dev`
   - Open Swagger UI: http://localhost:8080/q/swagger-ui
   - Review [API Specification](../api/openapi.yaml)

3. **Run Tests:**
   - `../mvnw verify`
   - Check coverage: `open target/site/jacoco/index.html`

4. **Set Up Docker Compose:**
   - Wait for I1.T2 completion
   - `docker-compose up -d`

## Useful Commands Cheat Sheet

```bash
# Build
../mvnw clean compile              # Compile only
../mvnw clean package             # Build JAR
../mvnw clean verify              # Build + tests

# Run
../mvnw quarkus:dev               # Dev mode (hot reload)
java -jar target/quarkus-app/quarkus-run.jar  # Run JAR

# Test
../mvnw test                      # Unit tests
../mvnw verify                    # Integration tests
../mvnw test -Dtest=MyTest        # Run specific test

# Code Quality
../mvnw jacoco:report             # Coverage report
../mvnw dependency:tree           # Dependency tree

# Docker
docker build -f src/main/docker/Dockerfile.jvm -t backend:jvm .
docker run -p 8080:8080 backend:jvm

# Verification
./verify-setup.sh                 # Verify setup
```

## Getting Help

- **Quarkus Guides:** https://quarkus.io/guides/
- **Project Documentation:** See `docs/` directory
- **Team Chat:** [Your team chat link]
- **Issue Tracker:** [Your issue tracker link]

## License

[Your license information]
