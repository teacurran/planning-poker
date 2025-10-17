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
  "description": "Create integration tests for `RoomController` and `UserController` using `@QuarkusTest` and Rest Assured. Test HTTP endpoints end-to-end: request → controller → service → repository → database → response. Use Testcontainers for PostgreSQL. Test CRUD operations, DTO mapping, error responses (404, 400), authorization (403 for unauthorized access). Validate response JSON against OpenAPI schema where possible.",
  "agent_type_hint": "BackendAgent",
  "inputs": "REST controllers from I2.T5, I2.T6, OpenAPI specification for expected responses",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/RoomController.java",
    "backend/src/main/java/com/scrumpoker/api/rest/UserController.java",
    "api/openapi.yaml"
  ],
  "target_files": [
    "backend/src/test/java/com/scrumpoker/api/rest/RoomControllerTest.java",
    "backend/src/test/java/com/scrumpoker/api/rest/UserControllerTest.java"
  ],
  "deliverables": "RoomControllerTest with tests for all 5 endpoints, UserControllerTest with tests for all 4 endpoints, Testcontainers PostgreSQL setup for integration tests, Rest Assured assertions for status codes, headers, response bodies, Tests for error scenarios (404, 400, 403)",
  "acceptance_criteria": "`mvn verify` runs integration tests successfully, POST /api/v1/rooms creates room in database, returns valid JSON, GET /api/v1/rooms/{roomId} retrieves persisted room, PUT endpoints update database and return updated DTOs, DELETE endpoints soft delete (verify `deleted_at` set), Unauthorized access returns 403 Forbidden, Response JSON structure matches OpenAPI spec",
  "dependencies": ["I2.T5", "I2.T6"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: rest-api-endpoints (from 04_Behavior_and_Communication.md)

```markdown
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

### Context: synchronous-rest-pattern (from 04_Behavior_and_Communication.md)

```markdown
##### Synchronous REST (Request/Response)

**Use Cases:**
- User authentication and registration
- Room creation and configuration updates
- Subscription management (upgrade, cancellation, payment method updates)
- Report generation triggers and export downloads
- Organization settings management

**Pattern Characteristics:**
- Client blocks waiting for server response (typically <500ms)
- Transactional consistency guaranteed within single database transaction
- Idempotency keys for payment operations to prevent duplicate charges
- Error responses use standard HTTP status codes (4xx client errors, 5xx server errors)

**Example Endpoints:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth2 code for JWT token
- `POST /api/v1/rooms` - Create new estimation room
- `GET /api/v1/rooms/{roomId}` - Retrieve room configuration
- `PUT /api/v1/users/{userId}/preferences` - Update user preferences
- `POST /api/v1/subscriptions/{subscriptionId}/upgrade` - Upgrade subscription tier
- `GET /api/v1/reports/sessions?from=2025-01-01&to=2025-01-31` - Query session history
```

### Context: OpenAPI Specification Excerpts (from openapi.yaml)

Key endpoint definitions from the OpenAPI specification:

**Room Endpoints:**
- `POST /api/v1/rooms` - Returns 201 Created with RoomDTO
- `GET /api/v1/rooms/{roomId}` - Returns 200 OK with RoomDTO or 404 Not Found
- `PUT /api/v1/rooms/{roomId}/config` - Returns 200 OK with updated RoomDTO or 400/404
- `DELETE /api/v1/rooms/{roomId}` - Returns 204 No Content or 403/404
- `GET /api/v1/users/{userId}/rooms` - Returns 200 OK with RoomListResponse (paginated)

**User Endpoints:**
- `GET /api/v1/users/{userId}` - Returns 200 OK with UserDTO or 404
- `PUT /api/v1/users/{userId}` - Returns 200 OK with updated UserDTO or 400/403/404
- `GET /api/v1/users/{userId}/preferences` - Returns 200 OK with UserPreferenceDTO or 403/404
- `PUT /api/v1/users/{userId}/preferences` - Returns 200 OK with updated UserPreferenceDTO or 400/403/404

**Error Response Schema:**
```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message",
  "timestamp": "2025-01-15T10:30:00Z",
  "details": {}
}
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** This file implements the REST controller for room management with 5 endpoints (createRoom, getRoom, updateRoomConfig, deleteRoom, getUserRooms). It uses reactive Uni<Response> return types and includes OpenAPI annotations. Authentication is currently not enforced (marked with TODOs for Iteration 3).
    *   **Recommendation:** You MUST test all 5 endpoints in this controller. The controller returns reactive Uni types, so your tests should work with Rest Assured's support for async responses. Pay special attention to the pagination logic in getUserRooms endpoint.
    *   **Key Observations:**
        - Creates rooms with nanoid 6-character IDs
        - Supports anonymous room creation (owner can be null)
        - Uses RoomMapper to convert between entities and DTOs
        - Validates privacy mode enum values
        - Implements manual pagination for getUserRooms
        - Soft delete sets `deleted_at` timestamp

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/UserController.java`
    *   **Summary:** This file implements the REST controller for user profile and preference management with 4 endpoints (getUserProfile, updateUserProfile, getUserPreferences, updateUserPreferences). Uses UserService and UserMapper for business logic and DTO conversion.
    *   **Recommendation:** You MUST test all 4 endpoints. Note that authorization checks (403 Forbidden) are marked as TODOs and not yet enforced, so your tests should document this expected behavior for future implementation. Test JSONB serialization/deserialization for UserPreference fields.
    *   **Key Observations:**
        - Uses UUID for userId path parameter
        - UserPreference contains JSONB fields (notification_settings, default_room_config)
        - Currently allows any user to view/update any profile (TODO for Iteration 3)

*   **File:** `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
    *   **Summary:** This existing repository test demonstrates the project's Testcontainers setup pattern using `@QuarkusTest` annotation with PostgreSQL container. It shows how to use Quarkus's reactive testing with `Uni.await()` pattern and `UniAsserter` for asynchronous assertions.
    *   **Recommendation:** You SHOULD follow the same Testcontainers pattern established in repository tests. The test profile (`application-test.properties`) already configures the PostgreSQL container. Use `UniAsserter` or `.await().indefinitely()` for reactive assertions as shown in repository tests.

*   **File:** `backend/src/test/resources/application-test.properties`
    *   **Summary:** Contains test-specific configuration for database connection and Testcontainers setup.
    *   **Recommendation:** You do NOT need to modify this file - Testcontainers is already configured. The test database will be automatically provisioned.

*   **File:** `backend/pom.xml`
    *   **Summary:** Contains project dependencies including Rest Assured (`io.rest-assured:rest-assured`) and Quarkus test dependencies.
    *   **Recommendation:** You do NOT need to add any new dependencies. Rest Assured and Testcontainers are already configured.

### Implementation Tips & Notes

*   **Tip:** The project already has comprehensive integration tests for repositories (I1.T8 completed). You SHOULD examine `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java` to understand the established testing patterns, particularly how to use `UniAsserter` or `.await().indefinitely()` for reactive assertions.

*   **Tip:** Rest Assured is the standard tool for testing REST APIs in Quarkus projects. You MUST use Rest Assured's `given().when().then()` pattern. For Quarkus integration tests, use the pattern:
```java
given()
    .contentType(ContentType.JSON)
    .body(requestObject)
.when()
    .post("/api/v1/rooms")
.then()
    .statusCode(201)
    .body("roomId", notNullValue())
    .body("title", equalTo("Test Room"));
```

*   **Note:** The controllers currently have `@RolesAllowed("USER")` annotations but authentication is NOT enforced yet (JWT implementation is in Iteration 3). Your tests should NOT attempt to test authorization failures (403) at this stage since the security layer is not implemented. You can document in comments that authorization tests will be added in Iteration 3.

*   **Note:** Both controllers use exception mappers for error handling (RoomNotFoundExceptionMapper, UserNotFoundExceptionMapper, IllegalArgumentExceptionMapper, ValidationExceptionMapper). These mappers convert exceptions to proper HTTP responses with ErrorResponse DTOs. Your tests MUST verify that 404 errors return the correct ErrorResponse structure with "error", "message", and "timestamp" fields.

*   **Warning:** The RoomController's `getUserRooms` endpoint implements manual pagination in memory (loads all rooms then pages them). This is not optimal for production but is acceptable for this iteration. Your test should verify pagination parameters are validated (size max 100, page >= 0) and that the response includes pagination metadata (page, size, totalElements, totalPages).

*   **Tip:** For testing room creation, you should verify that the generated room ID is a valid 6-character lowercase alphanumeric nanoid. You can use a regex pattern check: `matchesPattern("^[a-z0-9]{6}$")`.

*   **Tip:** For testing soft delete, you MUST query the database directly after calling the DELETE endpoint to verify that `deleted_at` timestamp is set and the room is not physically removed. You can inject the RoomRepository into your test class and use it to verify the database state.

*   **Note:** DTO serialization is handled by Jackson. The project uses standard Jackson annotations in DTO classes. Response JSON structure MUST match the OpenAPI schema definitions in `api/openapi.yaml`. Use Rest Assured's body matchers to verify field presence and types.

*   **Critical:** Testcontainers for PostgreSQL is already configured in the test profile. You do NOT need to manually start containers. The `@QuarkusTest` annotation handles this automatically. Each test method will share the same database instance but you should ensure test data doesn't conflict between tests (use unique IDs, clean up after tests, or use transactions that roll back).

*   **Tip:** For JSONB field testing (RoomConfig, UserPreferenceConfig), you should verify that complex nested objects serialize/deserialize correctly. Create requests with custom deck configurations and notification settings to ensure JSONB handling works end-to-end. Example:
```java
RoomConfigDTO config = new RoomConfigDTO();
config.deckType = "fibonacci";
config.timerEnabled = true;
config.timerDurationSeconds = 120;
```

*   **Best Practice:** Use descriptive test method names following the pattern `test<Endpoint><Scenario>()` (e.g., `testCreateRoomReturnsCreatedStatus()`, `testGetRoomReturns404WhenNotFound()`). Each test should focus on one specific scenario.

*   **Best Practice:** For each endpoint, you should test at minimum: happy path (successful response), 404 not found (for GET/PUT/DELETE), 400 bad request (for invalid input), and verify response DTO structure matches expected schema from OpenAPI spec.

*   **Important:** Use `@QuarkusTest` annotation at the class level. This will start Quarkus in test mode with Testcontainers. Each test method runs against a real HTTP server and database. No mocking is needed for integration tests - you're testing the full stack.

*   **Transaction Handling:** Quarkus test methods are NOT automatically transactional. If you need to verify database state, you can inject repositories and query them directly, or use `@TestTransaction` annotation to wrap test methods in transactions that roll back after each test.

### Testing Strategy Summary

For **RoomControllerTest**, implement tests for:
1. POST /api/v1/rooms - Create room with valid request, returns 201 + RoomDTO with valid 6-char ID
2. POST /api/v1/rooms - Create room with custom config (JSONB), verify config persisted correctly
3. POST /api/v1/rooms - Create room with invalid privacy mode (not in enum), returns 400
4. GET /api/v1/rooms/{roomId} - Retrieve existing room, returns 200 + RoomDTO
5. GET /api/v1/rooms/{roomId} - Room not found, returns 404 + ErrorResponse
6. PUT /api/v1/rooms/{roomId}/config - Update title only, returns 200 + updated RoomDTO
7. PUT /api/v1/rooms/{roomId}/config - Update privacy mode, returns 200
8. PUT /api/v1/rooms/{roomId}/config - Update with invalid privacy mode, returns 400
9. PUT /api/v1/rooms/{roomId}/config - Room not found, returns 404
10. DELETE /api/v1/rooms/{roomId} - Soft delete, returns 204, verify `deleted_at` set in DB
11. DELETE /api/v1/rooms/{roomId} - Room not found, returns 404
12. GET /api/v1/users/{userId}/rooms - List rooms with default pagination, returns 200 + RoomListResponse
13. GET /api/v1/users/{userId}/rooms - Validate page size limit (>100), returns 400

For **UserControllerTest**, implement tests for:
1. GET /api/v1/users/{userId} - Retrieve existing user, returns 200 + UserDTO
2. GET /api/v1/users/{userId} - User not found, returns 404 + ErrorResponse
3. PUT /api/v1/users/{userId} - Update profile (displayName + avatarUrl), returns 200 + updated UserDTO
4. PUT /api/v1/users/{userId} - Update with invalid displayName (too long >100 chars), returns 400
5. PUT /api/v1/users/{userId} - User not found, returns 404
6. GET /api/v1/users/{userId}/preferences - Retrieve preferences, returns 200 + UserPreferenceDTO with JSONB
7. GET /api/v1/users/{userId}/preferences - User not found, returns 404
8. PUT /api/v1/users/{userId}/preferences - Update preferences with JSONB fields (notification settings, default room config), returns 200 + updated DTO
9. PUT /api/v1/users/{userId}/preferences - Verify JSONB persistence in database
10. PUT /api/v1/users/{userId}/preferences - User not found, returns 404

### Example Test Structure

```java
package com.scrumpoker.api.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class RoomControllerTest {

    @Test
    public void testCreateRoom_ValidInput_Returns201() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.title = "Test Room";
        request.privacyMode = "PUBLIC";

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
            .body("roomId", matchesPattern("^[a-z0-9]{6}$"))
            .body("title", equalTo("Test Room"))
            .body("privacyMode", equalTo("PUBLIC"))
            .body("createdAt", notNullValue());
    }

    @Test
    public void testGetRoom_NotFound_Returns404() {
        given()
        .when()
            .get("/api/v1/rooms/nonexistent")
        .then()
            .statusCode(404)
            .body("error", notNullValue())
            .body("message", notNullValue())
            .body("timestamp", notNullValue());
    }
}
```

---

## Summary

You are implementing integration tests for the RoomController and UserController REST endpoints. These tests will verify end-to-end functionality from HTTP request through to database persistence and response generation. Use Rest Assured for HTTP testing, leverage the existing Testcontainers setup, and ensure all test scenarios cover both happy paths and error conditions. Your tests must validate that responses match the OpenAPI specification structure. Focus on functional correctness - authentication and authorization testing will be added in Iteration 3.
