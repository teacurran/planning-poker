# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T1",
  "iteration_id": "I1",
  "iteration_goal": "Establish project scaffolding, configure development environment, define database schema, and set up CI/CD foundation to enable parallel backend and frontend development in subsequent iterations.",
  "description": "Create Quarkus 3.x Maven project with reactive extensions (hibernate-reactive-panache, reactive-pg-client, redis-client, websockets, oidc, smallrye-jwt, micrometer-prometheus). Configure `application.properties` with database connection placeholders, Redis configuration, and JWT settings. Set up Maven build with compiler plugin (Java 17 target), Quarkus plugin, and Surefire for testing. Create package structure: `api`, `domain`, `repository`, `integration`, `event`, `config`, `security`.",
  "agent_type_hint": "SetupAgent",
  "inputs": "Directory structure specification from Section 3 of plan overview, Technology stack requirements (Quarkus 3.x, Java 17, reactive extensions), Maven dependency list from architecture blueprint",
  "input_files": [],
  "target_files": [
    "backend/pom.xml",
    "backend/src/main/resources/application.properties",
    "backend/src/main/java/com/scrumpoker/",
    "backend/.gitignore"
  ],
  "deliverables": "Working Maven project buildable with `mvn clean compile`, Configured Quarkus extensions in `pom.xml`, Application properties with placeholder values for database, Redis, JWT secret, Package directory structure following hexagonal architecture",
  "acceptance_criteria": "`mvn clean compile` executes without errors, `mvn quarkus:dev` starts Quarkus in dev mode and serves health check at `/q/health`, All required Quarkus extensions listed in `pom.xml` dependencies, Package structure matches specification (6+ top-level packages created)",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: technology-stack-summary (from 02_Architecture_Overview.md)

```markdown
### 3.2. Technology Stack Summary

| **Category** | **Technology Choice** | **Justification** |
|--------------|----------------------|-------------------|
| **Backend Framework** | **Quarkus 3.x (Reactive)** | Specified requirement, optimized for cloud-native deployment, reactive runtime for WebSocket concurrency, fast startup times |
| **Language** | **Java 17+ (LTS)** | Native Quarkus support, strong type system, mature ecosystem, team expertise |
| **ORM/Data Access** | **Hibernate Reactive + Panache** | Specified requirement, reactive database access with Mutiny streams, simplified repository pattern via Panache |
| **Database** | **PostgreSQL 15+** | ACID compliance, JSONB for flexible room configuration storage, proven scalability, strong community support |
| **Cache/Session Store** | **Redis 7+ (Cluster mode)** | In-memory performance for session state, Pub/Sub for WebSocket message broadcasting, horizontal scaling via cluster mode |
| **Authentication** | **OAuth2/OIDC (Google, Microsoft)** | Leverages existing identity providers, reduces password management risk, Quarkus OIDC extension for SSO integration |
| **Authorization** | **Quarkus Security (RBAC)** | Built-in role-based access control, annotation-driven security, JWT token validation |
| **Observability - Metrics** | **Prometheus + Grafana** | Cloud-native standard, Quarkus Micrometer extension, custom business metrics (active sessions, vote latency) |

#### Key Libraries & Extensions

**Backend (Quarkus):**
- `quarkus-resteasy-reactive-jackson` - Reactive REST endpoints with JSON serialization
- `quarkus-hibernate-reactive-panache` - Reactive database access layer
- `quarkus-reactive-pg-client` - Non-blocking PostgreSQL driver
- `quarkus-redis-client` - Redis integration for caching and Pub/Sub
- `quarkus-websockets` - WebSocket server implementation
- `quarkus-oidc` - OAuth2/OIDC authentication and SSO support
- `quarkus-smallrye-jwt` - JWT token generation and validation
- `quarkus-micrometer-registry-prometheus` - Metrics export
- `quarkus-flyway` - Database migrations
- `quarkus-smallrye-health` - Health checks
- `quarkus-smallrye-openapi` - OpenAPI documentation
```

### Context: directory-structure (from 01_Plan_Overview_and_Setup.md)

```markdown
## 3. Directory Structure

The project follows a standard Maven multi-module structure for the backend and modern React SPA conventions for the frontend, with clear separation of concerns and dedicated locations for documentation, API specifications, and infrastructure-as-code.

~~~
scrum-poker-platform/
├── backend/                          # Quarkus backend application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/scrumpoker/
│   │   │   │       ├── api/          # REST controllers, WebSocket handlers
│   │   │   │       ├── domain/       # Domain services and business logic
│   │   │   │       ├── repository/   # Panache repositories
│   │   │   │       ├── integration/  # External service adapters
│   │   │   │       ├── event/        # Redis Pub/Sub publisher/subscriber
│   │   │   │       ├── config/       # Application configuration classes
│   │   │   │       └── security/     # Authentication filters, JWT utilities
│   │   │   └── resources/
│   │   │       ├── application.properties  # Quarkus configuration
│   │   │       └── db/
│   │   │           └── migration/    # Flyway SQL migration scripts
│   │   └── test/
│   │       ├── java/
│   │       │   └── com/scrumpoker/
│   │       │       ├── api/          # REST/WebSocket integration tests
│   │       │       ├── domain/       # Unit tests for services
│   │       │       └── repository/   # Repository tests with Testcontainers
│   │       └── resources/
│   │           └── application-test.properties
│   ├── pom.xml                       # Maven project descriptor
│   └── Dockerfile                    # Multi-stage Docker build
~~~
```

### Context: architectural-style (from 01_Plan_Overview_and_Setup.md)

```markdown
## 2. Core Architecture

*   **Architectural Style:** Modular Monolith with Event-Driven Reactive Patterns

    **Rationale:**
    *   **Modular Monolith:** Balances operational simplicity (single deployable artifact) with clear module boundaries (User Management, Room Management, Billing, Reporting) for future microservice extraction if needed
    *   **Event-Driven Reactive:** Quarkus reactive runtime (Vert.x-based) enables non-blocking I/O for handling thousands of concurrent WebSocket connections efficiently
    *   **Redis Pub/Sub:** Enables stateless application nodes to broadcast room events across all connected clients in distributed deployment
    *   **CQRS-Lite:** Separate read models for reporting queries to avoid impacting transactional write performance
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/pom.xml`
    *   **Summary:** This file already exists and contains a properly configured Quarkus 3.15.1 Maven project with all the required reactive extensions. The project is already set up with the correct groupId (`com.scrumpoker`), artifactId (`scrum-poker-backend`), and Java 17 compiler configuration.
    *   **Status:** ✅ COMPLETE - The pom.xml already contains all required dependencies including:
        - `quarkus-hibernate-reactive-panache`
        - `quarkus-reactive-pg-client`
        - `quarkus-redis-client`
        - `quarkus-websockets`
        - `quarkus-oidc`
        - `quarkus-smallrye-jwt`
        - `quarkus-micrometer-registry-prometheus`
        - `quarkus-flyway` for database migrations
        - `quarkus-smallrye-health` for health checks
        - `quarkus-smallrye-openapi` for API documentation
    *   **Recommendation:** You DO NOT need to create or modify this file. It is already correctly configured.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** This file already exists and is comprehensively configured with all required settings including database configuration (both JDBC and reactive), Redis connection settings, JWT configuration, OIDC settings, WebSocket configuration, CORS configuration, health checks, Prometheus metrics, OpenAPI documentation, and environment-specific profiles for dev and test.
    *   **Status:** ✅ COMPLETE - All required configuration sections are present:
        - Database (PostgreSQL with Flyway migrations enabled)
        - Redis (standalone mode with health checks)
        - JWT (issuer, public/private key locations, token expiration)
        - OIDC (Google OAuth2 placeholders)
        - WebSocket configuration
        - HTTP/CORS settings
        - Logging configuration
    *   **Recommendation:** You DO NOT need to create or modify this file. It is already correctly configured with all necessary placeholders.

*   **File:** `backend/src/main/java/com/scrumpoker/`
    *   **Summary:** The package structure has been created with placeholder `package-info.java` files in each of the required directories: `api`, `config`, `domain`, `event`, `integration`, `repository`, and `security`.
    *   **Status:** ✅ COMPLETE - All 7 required top-level packages exist:
        - `com.scrumpoker.api` (package-info.java exists)
        - `com.scrumpoker.config` (package-info.java exists)
        - `com.scrumpoker.domain` (package-info.java exists)
        - `com.scrumpoker.event` (package-info.java exists)
        - `com.scrumpoker.integration` (package-info.java exists)
        - `com.scrumpoker.repository` (package-info.java exists)
        - `com.scrumpoker.security` (package-info.java exists)
    *   **Recommendation:** The package structure is already complete. You DO NOT need to create additional directories.

*   **File:** `backend/src/test/resources/application-test.properties`
    *   **Summary:** Test configuration file exists with proper test database and Redis configuration.
    *   **Status:** ✅ COMPLETE - Test configuration is properly set up.

### Implementation Tips & Notes

*   **CRITICAL NOTE:** This task (I1.T1) is **ALREADY COMPLETE**. All deliverables have been satisfied:
    1. ✅ Working Maven project is buildable with `mvn clean compile`
    2. ✅ All required Quarkus extensions are configured in `pom.xml`
    3. ✅ Application properties file exists with all required placeholders for database, Redis, JWT settings
    4. ✅ Package directory structure follows hexagonal architecture with all 7 required packages

*   **Verification Steps:** To verify task completion, you should:
    1. Run `mvn clean compile` in the `backend` directory - it should execute without errors
    2. Check that `backend/pom.xml` contains all required Quarkus dependencies
    3. Verify `backend/src/main/resources/application.properties` has configuration sections for database, Redis, JWT, OIDC, WebSocket, health checks, and Prometheus
    4. Confirm all 7 package directories exist under `backend/src/main/java/com/scrumpoker/`: api, config, domain, event, integration, repository, security

*   **Missing Item:** The task specifies creating `backend/.gitignore`, but this file does not currently exist. This is the ONLY missing deliverable. You should create a standard Java/Maven `.gitignore` file that excludes:
    - `target/` directory
    - IDE-specific files (`.idea/`, `*.iml`, `.vscode/`, `.DS_Store`)
    - Build artifacts and logs

*   **Project Context:** This is a Scrum Poker estimation platform with real-time WebSocket-based collaborative gameplay. The backend uses Quarkus reactive patterns to handle thousands of concurrent WebSocket connections efficiently. The architecture follows hexagonal/ports-and-adapters pattern with clear separation between domain logic, infrastructure, and API layers.

*   **Next Steps After This Task:** Once this task is verified as complete, the next tasks will be:
    - I1.T2: Initialize Frontend Project Structure (React TypeScript Vite)
    - I1.T3: Define Database Schema & Create Migration Scripts
    - These tasks can proceed in parallel since I1.T1 has no blocking dependencies

---

## 4. Acceptance Criteria Verification

To mark this task as complete, verify the following acceptance criteria:

1. ✅ `mvn clean compile` executes without errors
2. ✅ `mvn quarkus:dev` starts Quarkus in dev mode and serves health check at `/q/health`
3. ✅ All required Quarkus extensions listed in `pom.xml` dependencies
4. ✅ Package structure matches specification (7 top-level packages created with package-info.java files)
5. ⚠️ MISSING: `backend/.gitignore` file needs to be created

**Action Required:** Create the `backend/.gitignore` file to complete this task, then verify all acceptance criteria are met.
