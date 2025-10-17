# Project Plan: Scrum Poker Platform - Iteration 2

**Version:** 1.0
**Date:** 2025-10-17

---

<!-- anchor: iteration-2 -->
### Iteration 2: Core Backend Services & API Contracts

*   **Iteration ID:** `I2`

*   **Goal:** Implement foundational domain services (Room Service, basic User Service), define REST API contracts (OpenAPI specification), and establish WebSocket protocol specification to enable frontend integration and parallel feature development.

*   **Prerequisites:** I1 (complete database schema, entity classes, repositories)

*   **Tasks:**

<!-- anchor: task-i2-t1 -->
*   **Task 2.1: Generate OpenAPI 3.1 Specification for REST API**
    *   **Task ID:** `I2.T1`
    *   **Description:** Create comprehensive OpenAPI 3.1 YAML specification documenting all planned REST API endpoints. Define schemas for DTOs (UserDTO, RoomDTO, SubscriptionDTO, etc.), request bodies, response structures, error codes (400, 401, 403, 404, 500 with standardized error schema). Document endpoints for: user management (`/api/v1/users/*`), room CRUD (`/api/v1/rooms/*`), authentication (`/api/v1/auth/*`), subscriptions (`/api/v1/subscriptions/*`), reporting (`/api/v1/reports/*`), organizations (`/api/v1/organizations/*`). Include security schemes (Bearer JWT, OAuth2 flows). Add descriptions, examples, and validation rules (min/max lengths, patterns, required fields).
    *   **Agent Type Hint:** `DocumentationAgent`
    *   **Inputs:**
        *   REST API endpoint overview from architecture blueprint (Section 4 - API Design)
        *   Entity models from I1.T4 (for DTO schema definitions)
        *   Authentication/authorization requirements
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/04_Behavior_and_Communication.md` (REST API section)
        *   `backend/src/main/java/com/scrumpoker/domain/**/*.java` (entity files for reference)
    *   **Target Files:**
        *   `api/openapi.yaml`
        *   `docs/api-design.md` (supplementary documentation explaining design decisions)
    *   **Deliverables:**
        *   OpenAPI 3.1 YAML file with 30+ endpoint definitions
        *   Complete schema definitions for all DTOs (User, Room, Vote, Subscription, Organization, etc.)
        *   Error response schema with standardized structure (`{"error": "...", "message": "...", "timestamp": "..."}`)
        *   Security scheme definitions (JWT Bearer, OAuth2 authorization code flow)
        *   Request/response examples for critical endpoints
        *   Validation rules in schemas (string formats, numeric ranges, enum values)
    *   **Acceptance Criteria:**
        *   OpenAPI file validates against OpenAPI 3.1 schema (use Swagger Editor or spectral)
        *   All CRUD endpoints for core entities documented
        *   Security requirements specified for protected endpoints
        *   DTO schemas match database entity structure (field names, types, nullability)
        *   Error responses follow consistent structure across all endpoints
        *   File imports successfully into Swagger UI or Redoc for documentation rendering
    *   **Dependencies:** []
    *   **Parallelizable:** Yes (can start early based on architecture blueprint)

<!-- anchor: task-i2-t2 -->
*   **Task 2.2: Define WebSocket Protocol Specification**
    *   **Task ID:** `I2.T2`
    *   **Description:** Create comprehensive Markdown document specifying WebSocket communication protocol. Define message envelope structure (`{"type": "message_type.v1", "requestId": "uuid", "payload": {...}}`). Document all message types: client-to-server (`room.join.v1`, `vote.cast.v1`, `chat.message.v1`, `round.reveal.v1`), server-to-client (`vote.recorded.v1`, `round.revealed.v1`, `room.participant_joined.v1`, `error.v1`). Provide JSON schema for each payload type. Define error codes (4000-4999 for application errors). Specify connection lifecycle (handshake with JWT token, heartbeat protocol, graceful/ungraceful disconnection). Document versioning strategy for message types.
    *   **Agent Type Hint:** `DocumentationAgent`
    *   **Inputs:**
        *   WebSocket communication patterns from architecture blueprint (Section 4)
        *   Vote casting sequence diagram
        *   WebSocket message types overview
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/04_Behavior_and_Communication.md` (WebSocket section)
    *   **Target Files:**
        *   `api/websocket-protocol.md`
        *   `api/websocket-message-schemas.json` (JSON schemas for validation)
    *   **Deliverables:**
        *   Markdown specification document (10+ pages)
        *   Message envelope definition with required/optional fields
        *   20+ message type definitions with JSON schema payloads
        *   Error code catalog (4000: Unauthorized, 4001: Room not found, 4002: Invalid vote, etc.)
        *   Connection lifecycle diagram (PlantUML or Mermaid)
        *   Versioning policy explanation (backward compatibility guarantees)
    *   **Acceptance Criteria:**
        *   All message types from architecture blueprint documented
        *   JSON schemas validate sample messages (test with AJV or similar validator)
        *   Error codes cover common failure scenarios (auth, validation, server error)
        *   Connection lifecycle clearly explains handshake, heartbeat, reconnection
        *   Versioning strategy enables protocol evolution without breaking clients
        *   Document reviewed by backend and frontend leads for completeness
    *   **Dependencies:** []
    *   **Parallelizable:** Yes (can work parallel with I2.T1)

<!-- anchor: task-i2-t3 -->
*   **Task 2.3: Implement Room Service (CRUD Operations)**
    *   **Task ID:** `I2.T3`
    *   **Description:** Create `RoomService` domain service implementing core room operations: create room (generate 6-character nanoid, validate privacy mode, initialize config JSONB), update room configuration (deck type, rules, title), delete room (soft delete with `deleted_at`), find room by ID, list rooms by owner. Use `RoomRepository` for database operations. Implement reactive methods returning `Uni<>` for single results, `Multi<>` for lists. Validate business rules (room title length, valid privacy modes, deck type enum). Handle JSONB serialization for room configuration. Add transaction boundaries with `@Transactional`.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Room entity and repository from I1
        *   Room management requirements from product spec
        *   Nanoid generation pattern (6 characters, a-z0-9)
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
        *   `backend/src/main/java/com/scrumpoker/repository/RoomRepository.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/RoomConfig.java` (POJO for JSONB mapping)
        *   `backend/src/main/java/com/scrumpoker/domain/room/RoomNotFoundException.java` (custom exception)
    *   **Deliverables:**
        *   RoomService class with methods: `createRoom()`, `updateRoomConfig()`, `deleteRoom()`, `findById()`, `findByOwnerId()`
        *   Nanoid generation utility for unique room IDs
        *   RoomConfig POJO with fields: deckType, timerEnabled, timerDurationSeconds, revealBehavior
        *   Business validation (title max 200 chars, valid privacy enum)
        *   Reactive return types (Uni, Multi)
        *   Custom exception for room not found scenarios
    *   **Acceptance Criteria:**
        *   Service methods compile and pass unit tests (mocked repository)
        *   Room creation generates unique 6-character IDs (test collision resistance with 1000 iterations)
        *   JSONB config serialization/deserialization works correctly
        *   Soft delete sets `deleted_at` timestamp without removing database row
        *   Business validation throws appropriate exceptions (e.g., `IllegalArgumentException` for invalid title)
        *   Service transactional boundaries configured correctly
    *   **Dependencies:** [I1.T4, I1.T7]
    *   **Parallelizable:** No (depends on entity and repository)

<!-- anchor: task-i2-t4 -->
*   **Task 2.4: Implement User Service (Profile Management)**
    *   **Task ID:** `I2.T4`
    *   **Description:** Create `UserService` domain service for user profile operations: create user (from OAuth profile), update profile (display name, avatar URL), get user by ID, find by email, update user preferences (default deck type, theme, notification settings). Use `UserRepository` and `UserPreferenceRepository`. Implement reactive methods. Handle JSONB serialization for UserPreference.notification_settings and default_room_config. Validate email format, display name length constraints. Implement soft delete for user accounts (GDPR compliance).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   User and UserPreference entities from I1
        *   User repositories from I1
        *   User management requirements
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/user/User.java`
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java`
        *   `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/UserPreferenceRepository.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserPreferenceConfig.java` (POJO for JSONB)
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserNotFoundException.java`
    *   **Deliverables:**
        *   UserService with methods: `createUser()`, `updateProfile()`, `getUserById()`, `findByEmail()`, `updatePreferences()`, `deleteUser()` (soft delete)
        *   UserPreferenceConfig POJO for JSONB fields
        *   Email validation using regex or Bean Validation
        *   Display name length validation (max 100 chars)
        *   Soft delete implementation (sets `deleted_at`, excludes from queries)
    *   **Acceptance Criteria:**
        *   Service methods pass unit tests with mocked repositories
        *   User creation from OAuth profile maps fields correctly (oauth_provider, oauth_subject, email)
        *   Preference updates persist JSONB fields correctly
        *   Soft delete marks user as deleted without data loss
        *   Email validation rejects invalid formats
        *   Service methods return reactive types (Uni, Multi)
    *   **Dependencies:** [I1.T4, I1.T7]
    *   **Parallelizable:** Yes (can work parallel with I2.T3)

<!-- anchor: task-i2-t5 -->
*   **Task 2.5: Create REST Controllers for Room Management**
    *   **Task ID:** `I2.T5`
    *   **Description:** Implement JAX-RS REST controllers for room CRUD operations following OpenAPI specification from I2.T1. Create `RoomController` with endpoints: `POST /api/v1/rooms` (create room), `GET /api/v1/rooms/{roomId}` (get room), `PUT /api/v1/rooms/{roomId}/config` (update config), `DELETE /api/v1/rooms/{roomId}` (delete), `GET /api/v1/users/{userId}/rooms` (list user's rooms). Inject `RoomService`, convert entities to DTOs, handle exceptions (404 for room not found, 400 for validation errors). Add `@RolesAllowed` annotations for authorization (room owner can delete, authenticated users can create). Return reactive `Uni<>` types for non-blocking I/O.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   OpenAPI specification from I2.T1 (endpoint definitions)
        *   RoomService from I2.T3
        *   JAX-RS reactive patterns
    *   **Input Files:**
        *   `api/openapi.yaml`
        *   `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/RoomDTO.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/CreateRoomRequest.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/UpdateRoomConfigRequest.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java` (MapStruct mapper)
    *   **Deliverables:**
        *   RoomController with 5 endpoint methods matching OpenAPI spec
        *   DTO classes for requests and responses
        *   MapStruct mapper for entity ↔ DTO conversion
        *   Exception handlers for 404, 400 errors
        *   Authorization annotations (`@RolesAllowed("USER")`)
        *   Reactive return types (Uni<Response>)
    *   **Acceptance Criteria:**
        *   Endpoints accessible via `curl` or Postman against running Quarkus dev server
        *   POST creates room, returns 201 Created with RoomDTO body
        *   GET retrieves room by ID, returns 200 OK or 404 Not Found
        *   PUT updates config, returns 200 OK with updated RoomDTO
        *   DELETE soft deletes room, returns 204 No Content
        *   GET user's rooms returns paginated list (if many rooms)
        *   DTOs match OpenAPI schema definitions exactly
        *   Authorization prevents unauthorized users from deleting other users' rooms
    *   **Dependencies:** [I2.T1, I2.T3]
    *   **Parallelizable:** No (depends on service and OpenAPI spec)

<!-- anchor: task-i2-t6 -->
*   **Task 2.6: Create REST Controllers for User Management**
    *   **Task ID:** `I2.T6`
    *   **Description:** Implement JAX-RS REST controllers for user profile and preference management per OpenAPI spec. Create `UserController` with endpoints: `GET /api/v1/users/{userId}` (get profile), `PUT /api/v1/users/{userId}` (update profile), `GET /api/v1/users/{userId}/preferences` (get preferences), `PUT /api/v1/users/{userId}/preferences` (update preferences). Inject `UserService`, use DTOs, handle exceptions, enforce authorization (users can only access their own data unless admin). Return reactive types.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   OpenAPI specification from I2.T1
        *   UserService from I2.T4
    *   **Input Files:**
        *   `api/openapi.yaml`
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/UserController.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/UserDTO.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/UpdateProfileRequest.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/UserPreferenceDTO.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/mapper/UserMapper.java`
    *   **Deliverables:**
        *   UserController with 4 endpoint methods
        *   DTO classes for User and UserPreference
        *   MapStruct mapper for conversions
        *   Authorization checks (user can only update own profile)
        *   Exception handlers (404, 403 Forbidden)
    *   **Acceptance Criteria:**
        *   GET /api/v1/users/{userId} returns 200 with UserDTO
        *   PUT /api/v1/users/{userId} updates profile, returns 200
        *   GET preferences returns UserPreferenceDTO with JSONB fields
        *   PUT preferences updates JSONB settings correctly
        *   Authorization prevents user A from accessing user B's data (403 Forbidden)
        *   DTOs match OpenAPI schemas
    *   **Dependencies:** [I2.T1, I2.T4]
    *   **Parallelizable:** Yes (can work parallel with I2.T5)

<!-- anchor: task-i2-t7 -->
*   **Task 2.7: Write Unit Tests for Domain Services**
    *   **Task ID:** `I2.T7`
    *   **Description:** Create comprehensive unit tests for `RoomService` and `UserService` using JUnit 5 and Mockito. Mock repository dependencies. Test business logic: room creation with unique ID generation, config validation, soft delete behavior, user profile updates, preference persistence. Test exception scenarios (e.g., room not found, invalid email format). Use AssertJ for fluent assertions. Aim for >90% code coverage on service classes.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   RoomService and UserService from I2.T3, I2.T4
        *   JUnit 5 and Mockito testing patterns
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/domain/room/RoomServiceTest.java`
        *   `backend/src/test/java/com/scrumpoker/domain/user/UserServiceTest.java`
    *   **Deliverables:**
        *   RoomServiceTest with 10+ test methods covering create, update, delete, find operations
        *   UserServiceTest with 10+ test methods covering profile, preferences, soft delete
        *   Mocked repository interactions using Mockito
        *   Exception scenario tests (assertThrows for custom exceptions)
        *   AssertJ assertions for fluent readability
    *   **Acceptance Criteria:**
        *   `mvn test` runs all unit tests successfully
        *   Test coverage >90% for RoomService and UserService
        *   All business validation scenarios tested (invalid input → exception)
        *   Happy path tests verify correct repository method calls
        *   Exception tests verify custom exceptions thrown with correct messages
    *   **Dependencies:** [I2.T3, I2.T4]
    *   **Parallelizable:** No (depends on service implementation)

<!-- anchor: task-i2-t8 -->
*   **Task 2.8: Write Integration Tests for REST Controllers**
    *   **Task ID:** `I2.T8`
    *   **Description:** Create integration tests for `RoomController` and `UserController` using `@QuarkusTest` and Rest Assured. Test HTTP endpoints end-to-end: request → controller → service → repository → database → response. Use Testcontainers for PostgreSQL. Test CRUD operations, DTOmapping, error responses (404, 400), authorization (403 for unauthorized access). Validate response JSON against OpenAPI schema where possible.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   REST controllers from I2.T5, I2.T6
        *   OpenAPI specification for expected responses
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/UserController.java`
        *   `api/openapi.yaml`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/api/rest/RoomControllerTest.java`
        *   `backend/src/test/java/com/scrumpoker/api/rest/UserControllerTest.java`
    *   **Deliverables:**
        *   RoomControllerTest with tests for all 5 endpoints
        *   UserControllerTest with tests for all 4 endpoints
        *   Testcontainers PostgreSQL setup for integration tests
        *   Rest Assured assertions for status codes, headers, response bodies
        *   Tests for error scenarios (404, 400, 403)
    *   **Acceptance Criteria:**
        *   `mvn verify` runs integration tests successfully
        *   POST /api/v1/rooms creates room in database, returns valid JSON
        *   GET /api/v1/rooms/{roomId} retrieves persisted room
        *   PUT endpoints update database and return updated DTOs
        *   DELETE endpoints soft delete (verify `deleted_at` set)
        *   Unauthorized access returns 403 Forbidden
        *   Response JSON structure matches OpenAPI spec
    *   **Dependencies:** [I2.T5, I2.T6]
    *   **Parallelizable:** No (depends on controller implementation)

---

**Iteration 2 Summary:**

*   **Deliverables:**
    *   Complete OpenAPI 3.1 specification (30+ endpoints documented)
    *   WebSocket protocol specification (20+ message types)
    *   RoomService and UserService domain services with business logic
    *   REST controllers for room and user management (9 endpoints total)
    *   DTOs and MapStruct mappers for entity ↔ DTO conversion
    *   Unit tests for services (>90% coverage)
    *   Integration tests for REST controllers

*   **Acceptance Criteria (Iteration-Level):**
    *   OpenAPI spec validates and renders in Swagger UI
    *   WebSocket protocol document reviewed and approved
    *   All REST endpoints functional via curl/Postman
    *   Unit tests achieve >90% service coverage
    *   Integration tests pass with Testcontainers
    *   CI pipeline runs all tests successfully

*   **Estimated Duration:** 2 weeks
