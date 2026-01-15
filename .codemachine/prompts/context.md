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

### Context: Task 2.3 – Implement Room Service (from .codemachine/artifacts/plan/02_Iteration_I2.md)

```markdown
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
```

### Context: REST API Endpoints Overview (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

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
```

### Context: Synchronous REST Pattern (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

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

### Context: Core Gameplay Requirements (from .codemachine/artifacts/architecture/01_Context_and_Drivers.md)

```markdown
#### Core Gameplay Requirements
- **Real-time Estimation:** WebSocket-based blind card selection with configurable deck types (Fibonacci, T-shirt, custom)
- **Session Management:** Host controls for round lifecycle (start, lock, reveal, reset), participant management (kick, mute)
- **Calculation Engine:** Automatic computation of average, median, and consensus indicators upon reveal
- **Room Controls:** Unique room ID generation (6-character nanoid), shareable links, privacy modes
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** Implements the five REST endpoints defined in the OpenAPI spec, using Mutiny `Uni<Response>` wrappers, manual pagination for listing rooms, and currently permits anonymous access until Iteration 3 security lands.
    *   **Recommendation:** Keep leveraging `RoomService` and `RoomMapper` inside this controller; when adding new behavior ensure you continue returning `Uni<Response>` with appropriate status codes and let the existing JAX-RS exception mappers surface `RoomNotFoundException`/`IllegalArgumentException` rather than catching them here.
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java`
    *   **Summary:** Handles conversions between domain `Room`/`RoomConfig` objects and their DTO counterparts, including JSONB serialization defaults for timer, reveal behavior, and optional deck settings.
    *   **Recommendation:** Always convert request DTOs via this mapper (e.g., `roomMapper.toConfig(request.config)`) to keep JSON handling consistent and avoid duplicating default configuration logic inside the controller.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** Provides transactional room CRUD operations (create, title/config/privacy updates, deletion, owner queries) with tier enforcement through `FeatureGate` and built-in validation for titles, privacy modes, and config payloads.
    *   **Recommendation:** Delegate all persistence work to this service; use its helper methods (`updateRoomTitle`, `updatePrivacyMode`, `updateRoomConfig`, `findByOwnerId`) rather than mutating entities directly so business validations remain centralized.
*   **Files:** `backend/src/main/java/com/scrumpoker/api/rest/dto/{CreateRoomRequest,UpdateRoomConfigRequest,RoomDTO}.java`
    *   **Summary:** DTOs align with the OpenAPI schemas, embed `jakarta.validation` annotations for title limits, and expose privacy/config fields expected by the frontend clients.
    *   **Recommendation:** Reuse these DTOs (plus `RoomListResponse`) to match schema contracts; if additional fields surface, update DTOs and mapper together to keep serialization symmetrical.

### Implementation Tips & Notes
*   **Tip:** Validation errors are already translated through `IllegalArgumentExceptionMapper`/`ValidationExceptionMapper` into `ErrorResponse`, so have the controller return `Uni<Response>` failures by simply letting exceptions propagate or by creating early `ErrorResponse` objects when pre-checks (like pagination bounds) fail.
*   **Tip:** `RoomService.findByOwnerId(UUID)` returns a `Multi<Room>` that is converted to a `Uni<List<Room>>` via `.collect().asList()` before pagination; maintain this pattern so you can keep using Mutiny's fluent operators when altering pagination logic.
*   **Note:** Privacy-mode strings arrive from clients in mixed case—normalize with `PrivacyMode.valueOf(request.privacyMode.toUpperCase())` and catch `IllegalArgumentException` to send a `400` with a clear enum list, as shown in the existing controller.
*   **Note:** When authentication is added later, placeholders marked `TODO` will be enforced; avoid baking owner checks directly into the controller until the security context is wired up to prevent conflicts with Iteration 3 scope.
