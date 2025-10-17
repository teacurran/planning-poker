# Project Plan: Scrum Poker Platform - Iteration 1

**Version:** 1.0
**Date:** 2025-10-17

---

<!-- anchor: iteration-1 -->
### Iteration 1: Project Setup & Database Foundation

*   **Iteration ID:** `I1`

*   **Goal:** Establish project scaffolding, configure development environment, define database schema, and set up CI/CD foundation to enable parallel backend and frontend development in subsequent iterations.

*   **Prerequisites:** None (initial setup iteration)

*   **Tasks:**

<!-- anchor: task-i1-t1 -->
*   **Task 1.1: Initialize Backend Project Structure**
    *   **Task ID:** `I1.T1`
    *   **Description:** Create Quarkus 3.x Maven project with reactive extensions (hibernate-reactive-panache, reactive-pg-client, redis-client, websockets, oidc, smallrye-jwt, micrometer-prometheus). Configure `application.properties` with database connection placeholders, Redis configuration, and JWT settings. Set up Maven build with compiler plugin (Java 17 target), Quarkus plugin, and Surefire for testing. Create package structure: `api`, `domain`, `repository`, `integration`, `event`, `config`, `security`.
    *   **Agent Type Hint:** `SetupAgent`
    *   **Inputs:**
        *   Directory structure specification from Section 3 of plan overview
        *   Technology stack requirements (Quarkus 3.x, Java 17, reactive extensions)
        *   Maven dependency list from architecture blueprint
    *   **Input Files:** []
    *   **Target Files:**
        *   `backend/pom.xml`
        *   `backend/src/main/resources/application.properties`
        *   `backend/src/main/java/com/scrumpoker/` (package structure directories)
        *   `backend/.gitignore`
    *   **Deliverables:**
        *   Working Maven project buildable with `mvn clean compile`
        *   Configured Quarkus extensions in `pom.xml`
        *   Application properties with placeholder values for database, Redis, JWT secret
        *   Package directory structure following hexagonal architecture
    *   **Acceptance Criteria:**
        *   `mvn clean compile` executes without errors
        *   `mvn quarkus:dev` starts Quarkus in dev mode and serves health check at `/q/health`
        *   All required Quarkus extensions listed in `pom.xml` dependencies
        *   Package structure matches specification (6+ top-level packages created)
    *   **Dependencies:** []
    *   **Parallelizable:** Yes

<!-- anchor: task-i1-t2 -->
*   **Task 1.2: Initialize Frontend Project Structure**
    *   **Task ID:** `I1.T2`
    *   **Description:** Create React 18 TypeScript project using Vite. Install dependencies: React, React Router, Tailwind CSS, Headless UI, Zustand, React Query, Zod, date-fns, recharts. Configure Tailwind CSS with custom theme (primary color, dark mode support). Set up directory structure: `components`, `pages`, `services`, `stores`, `types`, `utils`. Create placeholder components for routing (HomePage, RoomPage, DashboardPage). Configure TypeScript with strict mode, path aliases (`@/components`, `@/services`).
    *   **Agent Type Hint:** `SetupAgent`
    *   **Inputs:**
        *   Directory structure specification from Section 3
        *   Frontend technology stack (React 18, Vite, TypeScript, Tailwind)
        *   List of required npm packages
    *   **Input Files:** []
    *   **Target Files:**
        *   `frontend/package.json`
        *   `frontend/tsconfig.json`
        *   `frontend/vite.config.ts`
        *   `frontend/tailwind.config.js`
        *   `frontend/src/` (directory structure with placeholder files)
        *   `frontend/index.html`
    *   **Deliverables:**
        *   Working React application buildable with `npm run build`
        *   Development server runnable with `npm run dev`
        *   Tailwind CSS configured with custom theme
        *   TypeScript configuration with strict checks and path aliases
        *   Placeholder page components with basic routing
    *   **Acceptance Criteria:**
        *   `npm run dev` starts Vite dev server successfully
        *   Navigating to `http://localhost:5173` displays HomePage component
        *   Tailwind CSS classes render correctly (test with colored div)
        *   TypeScript compilation successful with no errors
        *   Path aliases work (import using `@/components/...`)
    *   **Dependencies:** []
    *   **Parallelizable:** Yes (can run parallel with I1.T1)

<!-- anchor: task-i1-t3 -->
*   **Task 1.3: Define Database Schema & Create Migration Scripts**
    *   **Task ID:** `I1.T3`
    *   **Description:** Create Flyway migration scripts for all 11 core entities: User, UserPreference, Organization, OrgMember, Room, RoomParticipant, Round, Vote, SessionHistory, Subscription, PaymentHistory, AuditLog. Define tables with proper column types (UUID primary keys, VARCHAR lengths, TIMESTAMP with timezone, JSONB for configurations), foreign key constraints, indexes (see indexing strategy in ERD section), and partitioning setup for SessionHistory and AuditLog (monthly range partitions). Include `deleted_at` timestamp for soft deletes on User and Room tables.
    *   **Agent Type Hint:** `DatabaseAgent`
    *   **Inputs:**
        *   Entity Relationship Diagram from architecture blueprint (Section 3.6)
        *   Data model overview with entity descriptions
        *   Indexing strategy specifications
        *   Partitioning requirements (monthly partitions for SessionHistory, AuditLog)
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/03_System_Structure_and_Data.md` (ERD section)
    *   **Target Files:**
        *   `backend/src/main/resources/db/migration/V1__initial_schema.sql`
        *   `backend/src/main/resources/db/migration/V2__create_partitions.sql`
        *   `backend/src/main/resources/db/migration/V3__create_indexes.sql`
    *   **Deliverables:**
        *   SQL DDL scripts creating all 11 tables with correct column definitions
        *   Foreign key constraints enforcing referential integrity
        *   Indexes on high-priority columns (User.email, Room.owner_id, Vote.round_id, etc.)
        *   Partition creation setup for SessionHistory and AuditLog tables
        *   Soft delete columns (`deleted_at`) on User and Room
    *   **Acceptance Criteria:**
        *   Migration scripts execute without errors on PostgreSQL 15
        *   All foreign key relationships validated (cascading deletes/nulls as specified)
        *   Query plan analysis confirms indexes used for common queries (e.g., `EXPLAIN SELECT * FROM room WHERE owner_id = ?`)
        *   Partitions created for current and next 3 months for SessionHistory
        *   Schema matches ERD entity specifications exactly
    *   **Dependencies:** [I1.T1]
    *   **Parallelizable:** No (depends on backend project structure)

<!-- anchor: task-i1-t4 -->
*   **Task 1.4: Create JPA Entity Classes with Panache**
    *   **Task ID:** `I1.T4`
    *   **Description:** Implement JPA entity classes for all domain entities using Hibernate Reactive Panache. Define entities: `User`, `UserPreference`, `Organization`, `OrgMember`, `Room`, `RoomParticipant`, `Round`, `Vote`, `SessionHistory`, `Subscription`, `PaymentHistory`, `AuditLog`. Use `@Entity` annotations, proper column mappings (including JSONB with `@Type(JsonBinaryType.class)` from hibernate-types), relationships (`@OneToMany`, `@ManyToOne`), and validation constraints (`@NotNull`, `@Size`). Extend `PanacheEntityBase` for custom ID types (UUID). Add `@Cacheable` annotations where appropriate.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Database migration scripts from I1.T3
        *   Entity attribute specifications from ERD
        *   JPA/Panache patterns from Quarkus documentation
    *   **Input Files:**
        *   `backend/src/main/resources/db/migration/V1__initial_schema.sql`
        *   `.codemachine/artifacts/architecture/03_System_Structure_and_Data.md` (data model section)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/user/User.java`
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java`
        *   `backend/src/main/java/com/scrumpoker/domain/organization/Organization.java`
        *   `backend/src/main/java/com/scrumpoker/domain/organization/OrgMember.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/RoomParticipant.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/Round.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/Vote.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/SessionHistory.java`
        *   `backend/src/main/java/com/scrumpoker/domain/billing/Subscription.java`
        *   `backend/src/main/java/com/scrumpoker/domain/billing/PaymentHistory.java`
        *   `backend/src/main/java/com/scrumpoker/domain/organization/AuditLog.java`
    *   **Deliverables:**
        *   12 JPA entity classes with correct annotations
        *   Bidirectional relationships configured (e.g., User ↔ Room ownership)
        *   JSONB column mappings for Room.config, UserPreference.default_room_config, Organization.sso_config
        *   Bean validation constraints matching database constraints
        *   UUID generators configured for primary keys
    *   **Acceptance Criteria:**
        *   Maven compilation successful
        *   Quarkus dev mode starts without JPA mapping errors
        *   Entities can be persisted and retrieved via Panache repository methods
        *   JSONB columns serialize/deserialize correctly (test with sample data)
        *   Foreign key relationships navigable in code (e.g., `room.getOwner()`)
    *   **Dependencies:** [I1.T3]
    *   **Parallelizable:** No (depends on migration scripts)

<!-- anchor: task-i1-t5 -->
*   **Task 1.5: Set Up Local Development Environment with Docker Compose**
    *   **Task ID:** `I1.T5`
    *   **Description:** Create `docker-compose.yml` defining services: PostgreSQL 15 (with initialized database `scrumpoker`), Redis 7 (cluster mode with 3 nodes), Prometheus (scraping Quarkus metrics), Grafana (preconfigured with Prometheus datasource and dashboard). Configure volume mounts for database persistence and Grafana dashboards. Create `.env.example` file with environment variable templates (database credentials, Redis URLs, JWT secret placeholder). Document startup commands in README.md.
    *   **Agent Type Hint:** `SetupAgent`
    *   **Inputs:**
        *   Technology stack requirements (PostgreSQL 15, Redis 7 cluster)
        *   Observability stack (Prometheus, Grafana)
        *   Environment variable needs from application.properties
    *   **Input Files:**
        *   `backend/src/main/resources/application.properties`
    *   **Target Files:**
        *   `docker-compose.yml`
        *   `.env.example`
        *   `README.md` (development setup section)
        *   `infra/local/prometheus.yml` (Prometheus configuration)
        *   `infra/local/grafana/dashboards/quarkus-dashboard.json`
    *   **Deliverables:**
        *   Docker Compose file with 4 services (PostgreSQL, Redis, Prometheus, Grafana)
        *   PostgreSQL container with automatic schema initialization (Flyway migrations)
        *   Redis cluster configuration (3 nodes with replication)
        *   Prometheus configured to scrape `http://host.docker.internal:8080/q/metrics`
        *   Grafana preconfigured with Prometheus datasource
        *   Environment variable template file
        *   README section documenting `docker-compose up`, connection strings, port mappings
    *   **Acceptance Criteria:**
        *   `docker-compose up` starts all services without errors
        *   PostgreSQL accessible at `localhost:5432` with credentials from `.env`
        *   Redis cluster accessible at `localhost:6379-6381`
        *   Prometheus UI at `http://localhost:9090` shows Quarkus target
        *   Grafana UI at `http://localhost:3000` displays preconfigured dashboard
        *   Flyway migrations execute automatically when PostgreSQL container starts
    *   **Dependencies:** [I1.T3]
    *   **Parallelizable:** No (needs migration scripts for database init)

<!-- anchor: task-i1-t6 -->
*   **Task 1.6: Configure CI/CD Pipeline (GitHub Actions)**
    *   **Task ID:** `I1.T6`
    *   **Description:** Create GitHub Actions workflows for backend CI (`backend-ci.yml`) and frontend CI (`frontend-ci.yml`). Backend workflow: checkout code, setup Java 17, run `mvn clean verify` (compile, unit tests, integration tests with Testcontainers), SonarQube analysis (code quality gate), Trivy container scan on built Docker image. Frontend workflow: checkout, setup Node.js 18, run `npm ci`, `npm run lint`, `npm run test`, `npm run build`, upload build artifacts. Configure workflow triggers (push to main, pull requests). Add workflow status badges to README.md.
    *   **Agent Type Hint:** `SetupAgent`
    *   **Inputs:**
        *   CI/CD requirements from architecture blueprint (Section 5.2 - CI/CD Pipeline Hardening)
        *   Maven build lifecycle for Quarkus
        *   npm script conventions (lint, test, build)
    *   **Input Files:**
        *   `backend/pom.xml`
        *   `frontend/package.json`
    *   **Target Files:**
        *   `.github/workflows/backend-ci.yml`
        *   `.github/workflows/frontend-ci.yml`
        *   `README.md` (add workflow badges)
    *   **Deliverables:**
        *   Backend CI workflow with Java 17 setup, Maven build, Testcontainers support
        *   Frontend CI workflow with Node.js 18 setup, npm tasks (lint, test, build)
        *   SonarQube integration for backend (quality gate check)
        *   Trivy security scan for backend Docker image
        *   Workflow status badges in README
        *   Workflows triggered on push to `main` and pull requests to `main`
    *   **Acceptance Criteria:**
        *   Backend workflow executes successfully on sample commit (even with minimal code)
        *   Frontend workflow executes successfully on sample commit
        *   SonarQube analysis uploads results (if SonarCloud token configured)
        *   Trivy scan completes without critical vulnerabilities in base image
        *   Workflow badges display in README (green checkmarks)
        *   Failed tests cause workflow to fail (red X)
    *   **Dependencies:** [I1.T1, I1.T2]
    *   **Parallelizable:** Yes (after initial project setup)

<!-- anchor: task-i1-t7 -->
*   **Task 1.7: Create Panache Repository Interfaces**
    *   **Task ID:** `I1.T7`
    *   **Description:** Implement Panache repository interfaces for all entities using `PanacheRepositoryBase` pattern. Create repositories: `UserRepository`, `UserPreferenceRepository`, `OrganizationRepository`, `OrgMemberRepository`, `RoomRepository`, `RoomParticipantRepository`, `RoundRepository`, `VoteRepository`, `SessionHistoryRepository`, `SubscriptionRepository`, `PaymentHistoryRepository`, `AuditLogRepository`. Add custom finder methods (e.g., `UserRepository.findByEmail()`, `RoomRepository.findActiveByOwnerId()`, `VoteRepository.findByRoundId()`). Use reactive return types (`Uni<>`, `Multi<>`).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Entity classes from I1.T4
        *   Common query patterns from architecture blueprint (e.g., user lookup by email, rooms by owner)
        *   Panache repository patterns from Quarkus docs
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/user/User.java` (and other entity files)
        *   `.codemachine/artifacts/architecture/03_System_Structure_and_Data.md` (indexing strategy shows common queries)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/UserPreferenceRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/OrganizationRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/OrgMemberRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/RoomRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/RoomParticipantRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/RoundRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/SessionHistoryRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/SubscriptionRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/PaymentHistoryRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/AuditLogRepository.java`
    *   **Deliverables:**
        *   12 Panache repository classes implementing `PanacheRepositoryBase<Entity, UUID>`
        *   Custom finder methods with reactive return types (`Uni<User>`, `Multi<Room>`)
        *   Query methods using Panache query syntax (e.g., `find("email", email).firstResult()`)
        *   ApplicationScoped CDI beans for dependency injection
    *   **Acceptance Criteria:**
        *   Maven compilation successful
        *   Repositories injectable via `@Inject` in service classes
        *   Custom finder methods return correct reactive types
        *   Query methods execute without errors against seeded database
        *   Integration test for each repository demonstrates CRUD operations work
    *   **Dependencies:** [I1.T4]
    *   **Parallelizable:** No (depends on entity classes)

<!-- anchor: task-i1-t8 -->
*   **Task 1.8: Write Integration Tests for Repositories**
    *   **Task ID:** `I1.T8`
    *   **Description:** Create integration tests for all Panache repositories using Testcontainers (PostgreSQL container). Write tests for: entity persistence (insert, update, delete), custom finder methods, relationship navigation, JSONB field serialization/deserialization, soft delete behavior (User, Room). Use Quarkus `@QuarkusTest` annotation with `@TestProfile` for test database configuration. Assert results using AssertJ or Rest Assured for fluent assertions.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Repository interfaces from I1.T7
        *   Testcontainers setup patterns for PostgreSQL
        *   Sample entity instances for testing
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/repository/*.java` (all repository files)
        *   `backend/src/main/java/com/scrumpoker/domain/**/*.java` (entity files)
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
        *   `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
        *   `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
        *   (... test files for each of 12 repositories)
        *   `backend/src/test/resources/application-test.properties`
    *   **Deliverables:**
        *   12 repository test classes with minimum 3 test methods each (create, findById, custom finder)
        *   Testcontainers PostgreSQL configuration in test profile
        *   Tests for JSONB field operations (Room.config, UserPreference.default_room_config)
        *   Soft delete tests verifying `deleted_at` timestamp behavior
        *   Foreign key relationship tests (e.g., deleting User cascades to UserPreference)
    *   **Acceptance Criteria:**
        *   `mvn test` executes all repository tests successfully
        *   Testcontainers starts PostgreSQL container automatically
        *   All CRUD operations pass (insert, select, update, delete)
        *   Custom finder methods return expected results
        *   JSONB fields round-trip correctly (save and retrieve complex objects)
        *   Soft delete tests confirm `deleted_at` set correctly
        *   Test coverage >80% for repository classes
    *   **Dependencies:** [I1.T7]
    *   **Parallelizable:** No (depends on repository implementation)

---

**Iteration 1 Summary:**

*   **Deliverables:**
    *   Working Quarkus backend project with reactive extensions
    *   Working React frontend project with TypeScript and Tailwind
    *   Complete database schema (11 tables, indexes, partitions)
    *   12 JPA entity classes with Panache repositories
    *   Local development environment (Docker Compose: PostgreSQL, Redis, Prometheus, Grafana)
    *   CI/CD pipelines (GitHub Actions for backend and frontend)
    *   Repository integration tests with >80% coverage

*   **Acceptance Criteria (Iteration-Level):**
    *   Both backend and frontend projects build successfully in CI
    *   `docker-compose up` provides fully functional local development environment
    *   Flyway migrations create complete database schema
    *   All repository tests pass with Testcontainers
    *   Developers can run backend (`mvn quarkus:dev`) and frontend (`npm run dev`) locally
    *   README.md documents complete setup process

*   **Estimated Duration:** 2 weeks (assuming 2-3 developers working in parallel)
