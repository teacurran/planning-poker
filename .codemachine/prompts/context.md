# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T5",
  "iteration_id": "I2",
  "iteration_goal": "Implement foundational domain services (Room Service, basic User Service), define REST API contracts (OpenAPI specification), and establish WebSocket protocol specification to enable frontend integration and parallel feature development.",
  "description": "Implement JAX-RS REST controllers for room CRUD operations following OpenAPI specification from I2.T1. Create `RoomController` with endpoints: `POST /api/v1/rooms` (create room), `GET /api/v1/rooms/{roomId}` (get room), `PUT /api/v1/rooms/{roomId}/config` (update config), `DELETE /api/v1/rooms/{roomId}` (delete), `GET /api/v1/users/{userId}/rooms` (list user's rooms). Inject `RoomService`, convert entities to DTOs, handle exceptions (404 for room not found, 400 for validation errors). Add `@RolesAllowed` annotations for authorization (room owner can delete, authenticated users can create). Return reactive `Uni<>` types for non-blocking I/O.",
  "agent_type_hint": "BackendAgent",
  "inputs": "*   OpenAPI specification from I2.T1 (endpoint definitions)\n        *   RoomService from I2.T3\n        *   JAX-RS reactive patterns",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   RoomController with 5 endpoint methods matching OpenAPI spec\n        *   DTO classes for requests and responses\n        *   MapStruct mapper for entity ↔ DTO conversion\n        *   Exception handlers for 404, 400 errors\n        *   Authorization annotations (`@RolesAllowed(\"USER\")`)\n        *   Reactive return types (Uni<Response>)",
  "acceptance_criteria": "*   Endpoints accessible via `curl` or Postman against running Quarkus dev server\n        *   POST creates room, returns 201 Created with RoomDTO body\n        *   GET retrieves room by ID, returns 200 OK or 404 Not Found\n        *   PUT updates config, returns 200 OK with updated RoomDTO\n        *   DELETE soft deletes room, returns 204 No Content\n        *   GET user's rooms returns paginated list (if many rooms)\n        *   DTOs match OpenAPI schema definitions exactly\n        *   Authorization prevents unauthorized users from deleting other users' rooms",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Task 2.5 – Create REST Controllers for Room Management (from .codemachine/artifacts/plan/02_Iteration_I2.md)

```markdown
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

**Subscription & Billing:**
- `GET /api/v1/subscriptions/{userId}` - Get current subscription status
- `POST /api/v1/subscriptions/checkout` - Create Stripe checkout session for upgrade
- `POST /api/v1/subscriptions/{subscriptionId}/cancel` - Cancel subscription (end of period)
- `POST /api/v1/subscriptions/webhook` - Stripe webhook endpoint (signature verification)
- `GET /api/v1/billing/invoices` - List payment history

**Reporting & Analytics:**
- `GET /api/v1/reports/sessions` - List session history (tier-gated pagination, filters)
- `GET /api/v1/reports/sessions/{sessionId}` - Detailed session report (tier-gated round detail)
- `POST /api/v1/reports/export` - Generate export job (CSV/PDF), returns job ID
- `GET /api/v1/jobs/{jobId}` - Poll export job status, retrieve download URL

**Organization Management (Enterprise):**
- `POST /api/v1/organizations` - Create organization workspace
- `GET /api/v1/organizations/{orgId}` - Get org settings
- `PUT /api/v1/organizations/{orgId}/sso` - Configure OIDC/SAML2 settings
- `POST /api/v1/organizations/{orgId}/members` - Invite member
- `DELETE /api/v1/organizations/{orgId}/members/{userId}` - Remove member
- `GET /api/v1/organizations/{orgId}/audit-logs` - Query audit trail

---
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** Implements all five room endpoints with Mutiny `Uni<Response>` pipelines, `SecurityContextImpl` helpers for authentication data, manual pagination for `GET /users/{userId}/rooms`, and validation/error helpers that wrap consistent `ErrorResponse` payloads.
    *   **Recommendation:** Delegate every mutation to `RoomService` and every DTO conversion to `RoomMapper`; keep leveraging the shared helper methods (`resolveOwner`, `requireCurrentUserId`, `ensureRoomOwner`, pagination validation) so authorization and error semantics remain uniform.
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java`
    *   **Summary:** MapStruct mapper that injects `ObjectMapper` to handle JSONB config serialization while defaulting deck/timer/reveal settings and flattening owner/organization IDs.
    *   **Recommendation:** Always pass request configs through `toConfig` and emit responses via `toDTO`; this preserves defaults, avoids manual JSON handling, and keeps DTOs aligned with MapStruct generation.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** Owns room lifecycle logic (nanoid generation, validation, tier enforcement, config serialization, soft delete) and exposes reactive CRUD operations consumed by the controller.
    *   **Recommendation:** Use the service methods (`createRoomWithOwnerId`, `updateRoomConfig`, `updateRoomTitle`, `updatePrivacyMode`, `deleteRoom`, `findByOwnerId`) exactly as defined, letting it raise domain exceptions that the registered `ExceptionMapper`s already translate for HTTP responses.
*   **Files:** `backend/src/main/java/com/scrumpoker/api/rest/dto/{CreateRoomRequest.java, UpdateRoomConfigRequest.java, RoomDTO.java, RoomListResponse.java}`
    *   **Summary:** DTOs mirror the OpenAPI schemas with validation annotations (title length, required fields) and Jackson property names expected by the frontend.
    *   **Recommendation:** Reuse these DTOs verbatim; when adjusting schema fields ensure you update both DTOs and `RoomMapper` so API and documentation stay consistent.

### Implementation Tips & Notes
*   **Tip:** Let `RoomService` throw `RoomNotFoundException`/`FeatureNotAvailableException`; the existing JAX-RS `ExceptionMapper`s already convert them to the standardized `ErrorResponse`, so controller methods can stay lean.
*   **Tip:** Continue collecting `roomService.findByOwnerId(UUID)` into a list before manual pagination so you can reuse the bounds checks and build `RoomListResponse` consistently.
*   **Tip:** Feed privacy-mode strings through `resolvePrivacyMode` so invalid modes bubble up as `IllegalArgumentException`, which the mapper turns into a `400` with a helpful "Valid values" list.
*   **Tip:** Authentication is currently permissive on create/get endpoints; keep annotations (`@PermitAll` vs. `@RolesAllowed("USER")`) aligned with the OpenAPI security definitions so the Iteration 3 JWT filter can enforce them without extra churn.
*   **Note:** Always use the helper builders (`badRequest`, `forbiddenResponse`, `unauthorizedException`) when short-circuiting requests (e.g., pagination overflow) to keep error payloads uniform for frontend consumers.
