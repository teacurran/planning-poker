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
  "inputs": "*   OpenAPI specification from I2.T1\n        *   UserService from I2.T4",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   UserController with 4 endpoint methods\n        *   DTO classes for User and UserPreference\n        *   MapStruct mapper for conversions\n        *   Authorization checks (user can only update own profile)\n        *   Exception handlers (404, 403 Forbidden)",
  "acceptance_criteria": "*   GET /api/v1/users/{userId} returns 200 with UserDTO\n        *   PUT /api/v1/users/{userId} updates profile, returns 200\n        *   GET preferences returns UserPreferenceDTO with JSONB fields\n        *   PUT preferences updates JSONB settings correctly\n        *   Authorization prevents user A from accessing user B's data (403 Forbidden)\n        *   DTOs match OpenAPI schemas",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: User Account Requirements (from .codemachine/artifacts/architecture/01_Context_and_Drivers.md)

```markdown
<!-- anchor: user-account-requirements -->
#### User Account Requirements
- **OAuth2 Authentication:** Google and Microsoft social login integration
- **Profile Management:** Display name, avatar, theme preferences, default room settings
- **Session History:** Persistent storage of past sessions with tier-based access controls
- **Preference Persistence:** User-specific defaults for deck type, room rules, reveal behavior
```

### Context: REST API Endpoints Overview – Authentication & User Management (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

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
```

### Context: Task 2.6 – Create REST Controllers for User Management (from .codemachine/artifacts/plan/02_Iteration_I2.md)

```markdown
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
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/UserController.java:1`
    *   **Summary:** Defines all four user/profile endpoints with Mutiny `Uni<Response>` pipelines, OpenAPI annotations, and placeholder `@RolesAllowed("USER")` guards; comments note that authentication/authorization enforcement arrives in Iteration 3.
    *   **Recommendation:** Keep delegating to `UserService` for persistence and `UserMapper` for DTO conversion, and structure each endpoint to simply transform the service result into the correct HTTP status—domain exceptions are already translated by the registered `ExceptionMapper`s so avoid manual error handling.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserService.java:1`
    *   **Summary:** Owns validation, transactional persistence, and preference JSON serialization (e.g., `updateProfile`, `getPreferences`, `updatePreferences`, `deleteUser`) with `@WithTransaction`/`@WithSession` annotations and helpful helpers like `createDefaultPreferences`.
    *   **Recommendation:** Reuse its public methods exactly as-is instead of duplicating validation logic; the controller should just pass through DTO fields, rely on `UserService` to enforce constraints, and let `UserNotFoundException` or `IllegalArgumentException` bubble up.
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/mapper/UserMapper.java:1`
    *   **Summary:** Converts between domain entities/configs and DTOs, handling JSONB deserialization into `RoomConfigDTO`/`NotificationSettingsDTO` and composing a `UserPreferenceConfig` from `UpdateUserPreferenceRequest`.
    *   **Recommendation:** Always run responses through `toDTO`/`toPreferenceDTO` and build configs via `toConfig` rather than hand-rolling JSON, otherwise you risk drifting from the OpenAPI schema defaults and duplicating object-mapper work.
*   **File:** `api/openapi.yaml:194`
    *   **Summary:** Specifies the expected verbs, parameters, payload schemas, and success/error responses for `/api/v1/users/{userId}` and `/api/v1/users/{userId}/preferences`, referencing `UserDTO`, `UserPreferenceDTO`, `UpdateUserRequest`, and `UpdateUserPreferenceRequest`.
    *   **Recommendation:** Mirror these shapes precisely—ensure the controller returns `200 OK` bodies with the DTOs, raises `403/404` where called out, and validates request bodies according to the schema (e.g., `@Size` on `UpdateProfileRequest`, enum-friendly values in preference payloads).

### Implementation Tips & Notes
*   **Tip:** `UserController` already covers success-path piping; you can rely on the existing `UserNotFoundExceptionMapper`, `IllegalArgumentExceptionMapper`, and `ValidationExceptionMapper` (in `backend/src/main/java/com/scrumpoker/api/rest/exception`) to format failures uniformly, so keep controller methods minimal.
*   **Tip:** Authorization checks are currently TODOs—leave the annotations and comments in place and design the logic so that once the JWT filter lands, it only needs to inject the authenticated user ID to enforce the “only self-access” rule.
*   **Tip:** `UserService.updatePreferences` ensures a `UserPreference` row exists and serializes the JSONB fields using the injected `ObjectMapper`; don’t attempt manual serialization inside the controller—just forward DTO objects or use `UserMapper.toConfig` when the simplified overload is preferred.
*   **Tip:** Stick with Mutiny transformation style (`onItem().transform(...)`) when shaping responses so the controller stays non-blocking; avoid calling `.await().indefinitely()` or other blocking constructs.
*   **Note:** DTOs in `backend/src/main/java/com/scrumpoker/api/rest/dto` already match the OpenAPI schemas and include validation annotations—reuse them to keep the API contract and documentation synchronized.
