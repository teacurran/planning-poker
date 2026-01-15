# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T8",
  "iteration_id": "I2",
  "iteration_goal": "Implement foundational domain services (Room Service, basic User Service), define REST API contracts (OpenAPI specification), and establish WebSocket protocol specification to enable frontend integration and parallel feature development.",
  "description": "Create integration tests for `RoomController` and `UserController` using `@QuarkusTest` and Rest Assured. Test HTTP endpoints end-to-end: request → controller → service → repository → database → response. Use Testcontainers for PostgreSQL. Test CRUD operations, DTOmapping, error responses (404, 400), authorization (403 for unauthorized access). Validate response JSON against OpenAPI schema where possible.",
  "agent_type_hint": "BackendAgent",
  "inputs": "*   REST controllers from I2.T5, I2.T6\n        *   OpenAPI specification for expected responses",
  "target_files": [
    "backend/src/test/java/com/scrumpoker/api/rest/RoomControllerTest.java",
    "backend/src/test/java/com/scrumpoker/api/rest/UserControllerTest.java"
  ],
  "input_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/RoomController.java",
    "backend/src/main/java/com/scrumpoker/api/rest/UserController.java",
    "api/openapi.yaml"
  ],
  "deliverables": "*   RoomControllerTest with tests for all 5 endpoints\n        *   UserControllerTest with tests for all 4 endpoints\n        *   Testcontainers PostgreSQL setup for integration tests\n        *   Rest Assured assertions for status codes, headers, response bodies\n        *   Tests for error scenarios (404, 400, 403)",
  "acceptance_criteria": "*   `mvn verify` runs integration tests successfully\n        *   POST /api/v1/rooms creates room in database, returns valid JSON\n        *   GET /api/v1/rooms/{roomId} retrieves persisted room\n        *   PUT endpoints update database and return updated DTOs\n        *   DELETE endpoints soft delete (verify `deleted_at` set)\n        *   Unauthorized access returns 403 Forbidden\n        *   Response JSON structure matches OpenAPI spec",
  "dependencies": [
    "I2.T5",
    "I2.T6"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Task 2.8 – Write Integration Tests for REST Controllers (from .codemachine/artifacts/plan/02_Iteration_I2.md)

```markdown
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
```

### Context: REST API Endpoints Overview (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

```markdown
<!-- anchor: rest-api-endpoints -->
#### REST API Endpoints Overview

**Authentication & User Management:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth2 code for JWT tokens
- `POST /api/v1/auth/refresh` - Refresh expired access token
- `POST /api/v1/auth/logout` - Revoke refresh token
- `GET /api/v1/users/{userId}` - Retrieve user profile
- `PUT /api/v1/users/{userId}` - Update profile (display name, avatar)
- `GET /api/v1/users/{userId}/preferences` - Get user preferences
- `PUT /api/v1/users/{userId}/preferences` - Update default room settings, theme

**Room Management:**
- `POST /api/v1/rooms` - Create new room (authenticated or anonymous)
- `GET /api/v1/rooms/{roomId}` - Get room configuration and current state
- `PUT /api/v1/rooms/{roomId}/config` - Update room settings (host only)
- `DELETE /api/v1/rooms/{roomId}` - Delete room (owner only)
- `GET /api/v1/users/{userId}/rooms` - List user's owned rooms
```

### Context: Integration Testing Strategy (from .codemachine/artifacts/plan/03_Verification_and_Glossary.md)

```markdown
<!-- anchor: integration-testing -->
#### Integration Testing

**Scope:** Multiple components working together with real infrastructure (database, cache, message queue)

**Framework:** Quarkus Test (`@QuarkusTest`), Testcontainers, REST Assured

**Coverage Target:** Critical integration points (API → Service → Repository → Database)

**Approach:**
- Use Testcontainers for PostgreSQL and Redis (real instances, not mocks)
- Test REST endpoints end-to-end (request → response with database persistence)
- Test WebSocket flows (connection → message handling → database → Pub/Sub broadcast)
- Verify transaction boundaries and data consistency
- Run in CI pipeline (longer execution time acceptable: 10-15 minutes)

**Examples:**
- `RoomControllerTest`: POST /rooms creates database record, GET retrieves it
- `VotingFlowIntegrationTest`: WebSocket vote message → database insert → Redis Pub/Sub → client broadcast
- `StripeWebhookControllerTest`: Webhook event → signature verification → database update

**Acceptance Criteria:**
- All integration tests pass (`mvn verify`)
- Testcontainers start successfully (PostgreSQL, Redis)
- Database schema migrations execute correctly in tests
- No test pollution (each test isolated with database cleanup)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** Reactive JAX-RS controller that exposes POST/GET/PUT/DELETE room endpoints plus `/users/{userId}/rooms` pagination. It enforces ownership rules through `SecurityContextImpl`, validates privacy modes, and orchestrates `RoomService` mutations before mapping responses with `RoomMapper`.
    *   **Recommendation:** Integration tests should drive all five routes, covering success and validation branches, verifying pagination metadata, and asserting the error payloads returned by helper methods like `badRequest`/`forbiddenResponse`.
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/UserController.java`
    *   **Summary:** Provides four endpoints (profile + preferences CRUD) guarded by `@RolesAllowed` and `authorizeUserAccess`. It relies on `UserService` + `UserMapper` to fetch entities and transform JSONB-backed preference data.
    *   **Recommendation:** Target both happy paths (existing profiles/preferences) and guardrails (403 when hitting other users, 404 when entities missing). Ensure tests seed/persist users before hitting the endpoints.
*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/RoomControllerTest.java`
    *   **Summary:** Already contains comprehensive Rest Assured-based integration cases executed under `@QuarkusTest` + `NoSecurityTestProfile`. It uses `TestUserData` to create the default owner, cleans tables through `Panache` transactions, and validates DB state via `RoomRepository`.
    *   **Recommendation:** When extending or refactoring, keep the `@RunOnVertxContext` + `UniAsserter` patterns for DB assertions, reuse helper builders for payloads, and make sure each of the five controller endpoints has both success and failure coverage.
*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/UserControllerTest.java`
    *   **Summary:** Exercises all four user endpoints with Rest Assured, persisting users/preferences via repositories and asserting JSONB fields plus authorization behavior.
    *   **Recommendation:** Follow the existing structure—set up data via `Panache.withTransaction`, leverage `TestSecurityIdentityAugmentor.setTestUserId` to impersonate request principals, and make sure response assertions cover DTO shape as defined in `api/openapi.yaml`.

### Implementation Tips & Notes
*   **Tip:** `NoSecurityTestProfile` disables authentication while still applying role annotations through `TestSecurityIdentityAugmentor`; use this profile for every controller integration test to avoid wiring the OAuth stack.
*   **Tip:** `backend/src/test/resources/application.properties` is configured for Quarkus Dev Services, so you don’t need manual Testcontainers bootstrapping—just keep the tests reactive-friendly and let Flyway run migrations automatically.
*   **Tip:** Use `TestUserData.ensureTestUser(userRepository)` when room tests need an owner; for user tests, call `persistUserAndAuthenticate` to seed the repository and align `TestSecurityIdentityAugmentor` with the user ID under test.
*   **Tip:** Keep assertions strict: validate both status codes and payload structure (including `error`/`message` fields on 4xx responses) to satisfy the OpenAPI contract referenced in the iteration plan.
