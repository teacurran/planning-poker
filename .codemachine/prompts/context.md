# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T6",
  "iteration_id": "I2",
  "iteration_goal": "Implement foundational domain services (Room Service, basic User Service), define REST API contracts (OpenAPI specification), and establish WebSocket protocol specification to enable frontend integration and parallel feature development.",
  "description": "Implement JAX-RS REST controllers for user profile and preference management per OpenAPI spec. Create `UserController` with endpoints: `GET /api/v1/users/{userId}` (get profile), `PUT /api/v1/users/{userId}` (update profile), `GET /api/v1/users/{userId}/preferences` (get preferences), `PUT /api/v1/users/{userId}/preferences` (update preferences). Inject `UserService`, use DTOs, handle exceptions, enforce authorization (users can only access their own data unless admin). Return reactive types.",
  "agent_type_hint": "BackendAgent",
  "inputs": "OpenAPI specification from I2.T1, UserService from I2.T4",
  "input_files": [
    "api/openapi.yaml",
    "backend/src/main/java/com/scrumpoker/domain/user/UserService.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/UserController.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/UserDTO.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/UpdateProfileRequest.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/UserPreferenceDTO.java",
    "backend/src/main/java/com/scrumpoker/api/rest/mapper/UserMapper.java"
  ],
  "deliverables": "UserController with 4 endpoint methods, DTO classes for User and UserPreference, MapStruct mapper for conversions, Authorization checks (user can only update own profile), Exception handlers (404, 403 Forbidden)",
  "acceptance_criteria": "GET /api/v1/users/{userId} returns 200 with UserDTO, PUT /api/v1/users/{userId} updates profile, returns 200, GET preferences returns UserPreferenceDTO with JSONB fields, PUT preferences updates JSONB settings correctly, Authorization prevents user A from accessing user B's data (403 Forbidden), DTOs match OpenAPI schemas",
  "dependencies": [
    "I2.T1",
    "I2.T4"
  ],
  "parallelizable": true,
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

### Context: api-style (from 04_Behavior_and_Communication.md)

```markdown
#### API Style

**Primary API Style:** **RESTful JSON API (OpenAPI 3.1 Specification)**

**Rationale:**
- **Simplicity & Familiarity:** REST over HTTPS provides a well-understood contract for CRUD operations on resources (users, rooms, subscriptions)
- **Tooling Ecosystem:** OpenAPI specification enables automatic client SDK generation (TypeScript for React frontend), API documentation (Swagger UI), and contract testing
- **Caching Support:** HTTP semantics (ETags, Cache-Control headers) enable browser and CDN caching for read-heavy endpoints (room configurations, user profiles)
- **Versioning Strategy:** URL-based versioning (`/api/v1/`) for backward compatibility during iterative releases
```

### Context: User Endpoints from OpenAPI Specification (lines 194-308 of openapi.yaml)

The OpenAPI specification defines the exact contract for user profile and preference endpoints:

**GET /api/v1/users/{userId}:**
- **Summary:** Retrieve user profile
- **Description:** Returns public profile information for a user. Users can view their own full profile or other users' public profiles.
- **Security:** Requires Bearer JWT authentication
- **Response 200:** UserDTO with fields: userId, email, oauthProvider, displayName, avatarUrl, subscriptionTier, createdAt, updatedAt
- **Response 401:** Unauthorized (missing or invalid token)
- **Response 404:** User not found
- **Response 500:** Internal server error

**PUT /api/v1/users/{userId}:**
- **Summary:** Update user profile
- **Description:** Updates display name and avatar URL. Users can only update their own profile.
- **Security:** Requires Bearer JWT authentication
- **Request Body:** UpdateUserRequest with optional fields: displayName (max 100 chars), avatarUrl (max 500 chars)
- **Response 200:** Updated UserDTO
- **Response 400:** Invalid request parameters (validation error)
- **Response 401:** Unauthorized
- **Response 403:** Forbidden (user trying to update another user's profile)
- **Response 404:** User not found
- **Response 500:** Internal server error

**GET /api/v1/users/{userId}/preferences:**
- **Summary:** Get user preferences
- **Description:** Returns saved user preferences including default room settings, theme, and notification preferences.
- **Security:** Requires Bearer JWT authentication
- **Response 200:** UserPreferenceDTO with fields: userId, defaultDeckType, defaultRoomConfig (JSONB), theme, notificationSettings (JSONB)
- **Response 401:** Unauthorized
- **Response 403:** Forbidden (user accessing another user's preferences)
- **Response 404:** User or preferences not found
- **Response 500:** Internal server error

**PUT /api/v1/users/{userId}/preferences:**
- **Summary:** Update user preferences
- **Description:** Updates user preferences for default room configuration, theme, and notifications.
- **Security:** Requires Bearer JWT authentication
- **Request Body:** UpdateUserPreferenceRequest with optional fields: defaultDeckType (enum: fibonacci, tshirt, powers_of_2, custom), defaultRoomConfig (RoomConfigDTO object), theme (enum: light, dark, system), notificationSettings (object with emailNotifications, sessionReminders booleans)
- **Response 200:** Updated UserPreferenceDTO
- **Response 400:** Invalid request parameters
- **Response 401:** Unauthorized
- **Response 403:** Forbidden
- **Response 404:** User or preferences not found
- **Response 500:** Internal server error

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** This file contains the complete implementation of the REST controller for room management operations. It serves as the **PRIMARY REFERENCE PATTERN** for implementing your UserController.
    *   **Recommendation:** You MUST follow the exact patterns used in this file:
        - Class structure: `@Path("/api/v1")`, `@Produces(MediaType.APPLICATION_JSON)`, `@Consumes(MediaType.APPLICATION_JSON)`
        - JAX-RS annotations (`@GET`, `@POST`, `@PUT`, `@DELETE`, `@Path`, `@PathParam`, `@QueryParam`)
        - Reactive types: ALL methods return `Uni<Response>`, never plain `Response`
        - Exception handling: Delegated to exception mappers (RoomNotFoundExceptionMapper, IllegalArgumentExceptionMapper)
        - DTO conversion: Using injected MapStruct mappers (`@Inject RoomMapper roomMapper`)
        - OpenAPI annotations: `@Tag`, `@Operation`, `@APIResponse`, `@Parameter` for documentation
        - Response building: `Response.ok(dto).build()`, `Response.status(Response.Status.CREATED).entity(dto).build()`
        - Authorization: `@RolesAllowed("USER")` annotations with TODO comments for actual enforcement in Iteration 3
    *   **Example Pattern for GET endpoint (lines 86-103):**
        ```java
        @GET
        @Path("/rooms/{roomId}")
        @Operation(summary = "Get room by ID", description = "...")
        @APIResponse(responseCode = "200", description = "Room found",
            content = @Content(schema = @Schema(implementation = RoomDTO.class)))
        @APIResponse(responseCode = "404", description = "Room not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
        public Uni<Response> getRoom(@PathParam("roomId") String roomId) {
            return roomService.findById(roomId)
                .onItem().transform(room -> {
                    RoomDTO dto = roomMapper.toDTO(room);
                    return Response.ok(dto).build();
                });
        }
        ```
    *   **Example Pattern for PUT endpoint (lines 110-164):** Shows how to handle optional request fields and chain service calls.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Summary:** This is the complete domain service for user operations. It contains ALL the business logic you need to call from your controller. **DO NOT duplicate any validation or business logic in the controller.**
    *   **Critical Methods to Call:**
        - `getUserById(UUID userId)` → `Uni<User>` - Throws `UserNotFoundException` if not found or soft-deleted
        - `updateProfile(UUID userId, String displayName, String avatarUrl)` → `Uni<User>` - Validates inputs, throws `IllegalArgumentException`
        - `updatePreferences(UUID userId, UserPreferenceConfig config)` → `Uni<UserPreference>` - Handles JSONB serialization
    *   **Important Notes:**
        - The service already validates email format, display name length (max 100 chars), and handles JSONB serialization/deserialization
        - Methods throw `UserNotFoundException` (should map to 404) and `IllegalArgumentException` (should map to 400)
        - All methods use reactive `Uni<>` return types and `@WithTransaction` or `@WithSession` annotations
        - The service has private helper methods: `serializeConfig()` and `deserializeConfig()` for JSONB handling - you don't need to replicate these
    *   **UserPreference Access:** The service's `updatePreferences()` method expects a `UserPreferenceConfig` object. You'll need to convert from your request DTO to this config type.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** JPA entity for User with all fields defined. This is your source of truth for the UserDTO schema.
    *   **Entity Fields:**
        - `userId` (UUID) - Primary key
        - `email` (String, max 255, not null, unique)
        - `oauthProvider` (String, max 50, not null)
        - `oauthSubject` (String, max 255, not null)
        - `displayName` (String, max 100, not null)
        - `avatarUrl` (String, max 500, nullable)
        - `subscriptionTier` (SubscriptionTier enum, not null, default FREE)
        - `createdAt` (Instant, not null, auto-set)
        - `updatedAt` (Instant, not null, auto-updated)
        - `deletedAt` (Instant, nullable) - Soft delete timestamp
    *   **Recommendation for UserDTO:** Include all fields EXCEPT:
        - **DO NOT include** `oauthSubject` (sensitive internal identifier)
        - **DO NOT include** `deletedAt` (internal implementation detail)
        - **DO include** `oauthProvider` as optional field (useful for debugging, but not sensitive like subject)
    *   **Field Validation:** The entity has `@NotNull`, `@Email`, `@Size` annotations that are enforced at the database/ORM level. Your DTO should match these constraints.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java`
    *   **Summary:** JPA entity for UserPreference with JSONB columns.
    *   **Entity Fields:**
        - `userId` (UUID) - Primary key, foreign key to User
        - `user` (User entity, @OneToOne, lazy loaded)
        - `defaultDeckType` (String, max 50, nullable)
        - `defaultRoomConfig` (String - JSONB column, nullable)
        - `theme` (String, max 20, default "light")
        - `notificationSettings` (String - JSONB column, nullable)
        - `createdAt` (Instant, not null)
        - `updatedAt` (Instant, not null)
    *   **CRITICAL for UserPreferenceDTO:** The JSONB fields (`defaultRoomConfig`, `notificationSettings`) are stored as JSON strings in the database. Your DTO should:
        - **Option 1 (Simpler):** Keep them as `String` type and let the frontend parse the JSON
        - **Option 2 (More structured):** Define them as nested objects matching the OpenAPI schema structure
        - **Recommendation:** Use Option 2 to match the OpenAPI spec exactly, where `defaultRoomConfig` is a `RoomConfigDTO` object and `notificationSettings` is an object with boolean fields.
    *   **Note:** The `user` field should NOT be included in the DTO (circular reference issue and not needed in the API response).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserPreferenceConfig.java`
    *   **Summary:** POJO used by UserService for JSONB serialization. This is the config object expected by `UserService.updatePreferences()`.
    *   **Recommendation:** Your `UpdateUserPreferenceRequest` needs to be convertible to this type. Check the fields in this file and ensure your request DTO has matching fields.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java`
    *   **Summary:** MapStruct mapper for entity to DTO conversion. This is your **TEMPLATE** for creating UserMapper.
    *   **Pattern to Follow:**
        ```java
        @Mapper(componentModel = "cdi")
        public interface UserMapper {
            UserDTO toDTO(User user);
            UserPreferenceDTO toPreferenceDTO(UserPreference preference);
            UserPreferenceConfig toConfig(UpdateUserPreferenceRequest request);
        }
        ```
    *   **MapStruct Notes:**
        - `componentModel = "cdi"` makes the mapper injectable via `@Inject`
        - MapStruct auto-generates implementations at compile time
        - For JSONB fields, you may need custom mapping methods (use `@Mapping` annotation) or handle them manually in the mapper
        - If field names don't match between entity and DTO, use `@Mapping(source = "...", target = "...")` annotation

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/exception/RoomNotFoundExceptionMapper.java`
    *   **Summary:** JAX-RS exception mapper converting domain exceptions to HTTP responses. You need a similar mapper for `UserNotFoundException`.
    *   **Pattern to Follow:**
        ```java
        @Provider
        public class UserNotFoundExceptionMapper implements ExceptionMapper<UserNotFoundException> {
            @Override
            public Response toResponse(UserNotFoundException exception) {
                ErrorResponse error = new ErrorResponse(
                    "USER_NOT_FOUND",
                    exception.getMessage(),
                    Instant.now()
                );
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(error)
                    .build();
            }
        }
        ```
    *   **Note:** The `IllegalArgumentExceptionMapper` already exists and will handle validation errors (400 Bad Request), so you don't need to create that.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/ErrorResponse.java`
    *   **Summary:** Standardized error response DTO used across all endpoints.
    *   **Structure:**
        ```java
        public class ErrorResponse {
            public String error;       // Error code (e.g., "USER_NOT_FOUND", "VALIDATION_ERROR")
            public String message;     // Human-readable message
            public Instant timestamp;  // When the error occurred
        }
        ```
    *   **Recommendation:** Use this for all error responses. Do NOT create custom error response structures.

*   **File:** `api/openapi.yaml` (lines 194-308)
    *   **Summary:** The OpenAPI specification for user endpoints is already defined and complete.
    *   **Critical Requirement:** Your controller implementation MUST exactly match the specification:
        - Endpoint paths must be exact: `/api/v1/users/{userId}`, `/api/v1/users/{userId}/preferences`
        - HTTP methods must match: GET for retrieve, PUT for update (not PATCH or POST)
        - Request/response schema names must match: `UserDTO`, `UpdateUserRequest`, `UserPreferenceDTO`, `UpdateUserPreferenceRequest`
        - HTTP status codes must match: 200 OK, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 500 Internal Server Error
        - Field names in DTOs must match OpenAPI exactly (camelCase: `displayName`, not `display_name`)
    *   **DTO Schema from OpenAPI:**
        - **UserDTO:** userId, email, oauthProvider (optional), displayName, avatarUrl (nullable), subscriptionTier, createdAt, updatedAt
        - **UpdateUserRequest:** displayName (optional, max 100), avatarUrl (optional, nullable, max 500)
        - **UserPreferenceDTO:** userId, defaultDeckType (enum), defaultRoomConfig (RoomConfigDTO), theme (enum), notificationSettings (object)
        - **UpdateUserPreferenceRequest:** All preference fields optional

### Implementation Tips & Notes

*   **Tip #1: Start with the DTOs** - Create UserDTO, UpdateProfileRequest, UserPreferenceDTO, UpdateUserPreferenceRequest first. Ensure field names and types match the OpenAPI spec EXACTLY. Use `@JsonProperty` if needed for serialization control.

*   **Tip #2: Create the Mapper** - Define the UserMapper interface using MapStruct. For simple field mappings, MapStruct will auto-generate the code. For JSONB fields, you may need to handle JSON parsing manually or define custom mapping methods.

*   **Tip #3: Handle JSONB in UserPreferenceDTO** - The OpenAPI spec defines `defaultRoomConfig` as a `RoomConfigDTO` object (with deckType, timerEnabled, etc.). However, in the database it's a JSON string. You have two options:
    - **Option A:** In UserPreferenceDTO, define `defaultRoomConfig` as `String` and let the frontend parse it
    - **Option B (Recommended):** In UserMapper, deserialize the JSON string to a `RoomConfigDTO` object for the DTO, and serialize it back when updating
    - The RoomMapper already has patterns for this - look at `toConfig()` and `toConfigDTO()` methods

*   **Tip #4: Authorization TODOs** - Since JWT authentication is not implemented until Iteration 3, add `@RolesAllowed("USER")` annotations to protected endpoints with TODO comments:
    ```java
    @PUT
    @Path("/users/{userId}")
    @RolesAllowed("USER")
    public Uni<Response> updateUserProfile(@PathParam("userId") UUID userId, ...) {
        // TODO: Verify user can only update their own profile when auth is implemented (Iteration 3)
        ...
    }
    ```
    - For GET /users/{userId}, you might make it public or require auth - check OpenAPI spec
    - For preferences endpoints, definitely require auth since they're private data

*   **Tip #5: UserNotFoundException Mapper** - Create this exception mapper following the RoomNotFoundExceptionMapper pattern. Place it in `backend/src/main/java/com/scrumpoker/api/rest/exception/UserNotFoundExceptionMapper.java`. Annotate with `@Provider` so Quarkus auto-discovers it.

*   **Tip #6: Response Building Pattern** - Follow the RoomController pattern exactly:
    ```java
    return userService.getUserById(userId)
        .onItem().transform(user -> {
            UserDTO dto = userMapper.toDTO(user);
            return Response.ok(dto).build();
        });
    ```
    - Use `.onItem().transform()` for mapping the result
    - Let exception mappers handle errors - don't catch exceptions in the controller

*   **Warning: UUID Path Parameters** - The OpenAPI spec defines userId as UUID format. Use `@PathParam("userId") UUID userId` in your method signature. JAX-RS will automatically parse the string to UUID and return 400 if invalid format.

*   **Warning: Optional Request Fields** - The `UpdateProfileRequest` and `UpdateUserPreferenceRequest` have all fields optional. Your service calls must handle nulls correctly. The UserService already does this - `updateProfile()` only updates fields if they're not null.

*   **Note on Preferences Update** - The OpenAPI spec defines `UpdateUserPreferenceRequest.defaultRoomConfig` as a `RoomConfigDTO` object (with deckType, timerEnabled, etc.), but the UserService expects a `UserPreferenceConfig`. Your mapper needs to bridge these:
    ```java
    @Mapping(source = "defaultRoomConfig", target = "deckType")  // Example mapping
    UserPreferenceConfig toConfig(UpdateUserPreferenceRequest request);
    ```
    Check the exact fields in UserPreferenceConfig and map them correctly.

*   **Testing Commands** - After implementation, test with curl:
    ```bash
    # Get user profile
    curl http://localhost:8080/api/v1/users/{uuid}

    # Update profile
    curl -X PUT http://localhost:8080/api/v1/users/{uuid} \
      -H "Content-Type: application/json" \
      -d '{"displayName": "New Name", "avatarUrl": "https://example.com/avatar.jpg"}'

    # Get preferences
    curl http://localhost:8080/api/v1/users/{uuid}/preferences

    # Update preferences
    curl -X PUT http://localhost:8080/api/v1/users/{uuid}/preferences \
      -H "Content-Type: application/json" \
      -d '{"theme": "dark", "defaultDeckType": "fibonacci"}'
    ```

*   **Error Response Consistency** - All error responses MUST use the `ErrorResponse` DTO:
    ```json
    {
      "error": "USER_NOT_FOUND",
      "message": "User not found: 123e4567-e89b-12d3-a456-426614174000",
      "timestamp": "2025-01-15T10:30:00Z"
    }
    ```

*   **Package Structure** - Follow the existing pattern:
    - Controller: `backend/src/main/java/com/scrumpoker/api/rest/UserController.java`
    - DTOs: `backend/src/main/java/com/scrumpoker/api/rest/dto/UserDTO.java`, etc.
    - Mapper: `backend/src/main/java/com/scrumpoker/api/rest/mapper/UserMapper.java`
    - Exception mappers: `backend/src/main/java/com/scrumpoker/api/rest/exception/UserNotFoundExceptionMapper.java`

---

## 4. Step-by-Step Implementation Checklist

Follow this sequence to ensure all deliverables are met:

1. **Create UserDTO.java**
   - Fields: userId, email, oauthProvider (optional), displayName, avatarUrl, subscriptionTier, createdAt, updatedAt
   - Match OpenAPI schema exactly
   - Use `@JsonProperty` for field naming if needed
   - Use `@JsonFormat` for Instant fields (pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")

2. **Create UpdateProfileRequest.java**
   - Fields: displayName (optional, max 100), avatarUrl (optional, max 500)
   - All fields optional (no @NotNull)
   - Add Bean Validation annotations (@Size)

3. **Create UserPreferenceDTO.java**
   - Fields: userId, defaultDeckType, defaultRoomConfig (as RoomConfigDTO object), theme, notificationSettings (as object)
   - Match OpenAPI schema structure
   - Handle JSONB → object mapping in UserMapper

4. **Create UpdateUserPreferenceRequest.java**
   - Fields: defaultDeckType, defaultRoomConfig, theme, notificationSettings
   - All fields optional
   - Match OpenAPI request schema

5. **Create UserMapper.java**
   - MapStruct interface with `@Mapper(componentModel = "cdi")`
   - Methods: `UserDTO toDTO(User)`, `UserPreferenceDTO toPreferenceDTO(UserPreference)`, `UserPreferenceConfig toConfig(UpdateUserPreferenceRequest)`
   - Handle JSONB deserialization for defaultRoomConfig and notificationSettings
   - Use `@Mapping` annotations for complex field mappings

6. **Create UserNotFoundExceptionMapper.java**
   - Implements `ExceptionMapper<UserNotFoundException>`
   - Returns 404 with ErrorResponse
   - Annotate with `@Provider`

7. **Create UserController.java**
   - Class-level annotations: `@Path("/api/v1")`, `@Produces(MediaType.APPLICATION_JSON)`, `@Consumes(MediaType.APPLICATION_JSON)`, `@Tag(name = "Users")`
   - Inject UserService and UserMapper
   - Implement 4 endpoints:
     - `GET /users/{userId}` - Returns 200 with UserDTO or 404
     - `PUT /users/{userId}` - Returns 200 with UserDTO, 400, 403, 404
     - `GET /users/{userId}/preferences` - Returns 200 with UserPreferenceDTO, 403, 404
     - `PUT /users/{userId}/preferences` - Returns 200 with UserPreferenceDTO, 400, 403, 404
   - All methods return `Uni<Response>`
   - Add OpenAPI annotations (`@Operation`, `@APIResponse`)
   - Add `@RolesAllowed("USER")` with TODO comments for auth enforcement

8. **Manual Testing**
   - Start Quarkus dev mode: `mvn quarkus:dev`
   - Use curl or Postman to test all 4 endpoints
   - Verify response structures match OpenAPI spec
   - Verify error codes (404, 400, 403) return correct ErrorResponse

9. **Verify Acceptance Criteria**
   - ✅ GET /api/v1/users/{userId} returns 200 with UserDTO
   - ✅ PUT /api/v1/users/{userId} updates profile, returns 200
   - ✅ GET preferences returns UserPreferenceDTO with JSONB fields
   - ✅ PUT preferences updates JSONB settings correctly
   - ✅ DTOs match OpenAPI schemas exactly
   - ⚠️ Authorization enforcement marked for Iteration 3 (TODOs added)

10. **Mark Task Complete**
    - Update task status in tasks JSON to `"done": true`
    - Commit changes with message: "feat(api): implement UserController with profile and preference endpoints (I2.T6)"
