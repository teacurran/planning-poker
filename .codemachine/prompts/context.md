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

---
```

### Context: Synchronous REST (Request/Response) Pattern (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

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

---
```

### Context: API Contract Style (from .codemachine/artifacts/plan/01_Plan_Overview_and_Setup.md)

```markdown
*   **API Contract Style:**
    *   **REST API:** RESTful JSON API documented with OpenAPI 3.1 specification
        *   URL versioning: `/api/v1/`
        *   Standard HTTP semantics (GET, POST, PUT, DELETE)
        *   Error responses with consistent structure (4xx client errors, 5xx server errors)
    *   **WebSocket Protocol:** Custom JSON-RPC style over WebSocket
        *   Message format: `{\"type\": \"vote.cast.v1\", \"requestId\": \"uuid\", \"payload\": {...}}`
        *   Versioned message types (e.g., `v1`, `v2`) for protocol evolution
        *   Request/response correlation via `requestId`

    **Planned Specification Files:**
    *   OpenAPI 3.1 Specification (YAML) - Documents all REST endpoints (Created in I2.T1)
    *   WebSocket Protocol Specification (Markdown) - Message type catalog with JSON schemas (Created in I2.T2)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** Provides the five REST endpoints (create, read, update config, delete, and list user rooms) using Mutiny `Uni<Response>` wrappers, `SecurityContextImpl` helpers to resolve user IDs, manual pagination logic for user rooms, and helper methods for privacy-mode parsing plus status-specific error payloads.
    *   **Recommendation:** Keep delegating business logic to `RoomService` and DTO conversions to `RoomMapper`; rely on the existing helper methods (`resolveOwner`, `requireCurrentUserId`, `ensureRoomOwner`, pagination validation) rather than reimplementing them when expanding behavior or adjusting responses.
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java`
    *   **Summary:** MapStruct-based mapper that injects `ObjectMapper` to handle JSONB conversions, enforces sensible defaults for deck/timer/reveal fields, and converts owner/organization relationships into UUIDs for the DTO.
    *   **Recommendation:** Always round-trip `RoomConfigDTO` through this mapper (`toConfig`, `toConfigDTO`) to preserve defaulting rules and to avoid duplicating Jackson serialization; letting it set defaults keeps controller code lean.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** Houses all transactional room operations with validation (title length, privacy mode, config deck type), tier enforcement via `FeatureGate`, owner assignment, soft deletion, and reactive lookups via Panache.
    *   **Recommendation:** Use the provided service methods (`createRoom`, `updateRoomTitle`, `updateRoomConfig`, `updatePrivacyMode`, `deleteRoom`, `findByOwnerId`) exactly as-is to ensure invariants (e.g., deck type required, soft delete semantics) remain enforced—do not attempt to mutate entities directly in the controller.
*   **File:** `api/openapi.yaml`
    *   **Summary:** Documents the REST contract; for rooms it specifies that `POST /api/v1/rooms` and `GET /api/v1/rooms/{roomId}` allow anonymous access, while config updates, deletion, and listing are secured and must return `RoomDTO`/`RoomListResponse` bodies matching schemas.
    *   **Recommendation:** Align status codes and body shapes with the spec (201 + `RoomDTO` on creation, 200 on reads/updates, 204 on deletes, paginated payload on list) and honor the documented security posture (Bearer + optional anonymous for certain endpoints) when adjusting annotations.
*   **Files:** `backend/src/main/java/com/scrumpoker/api/rest/dto/{CreateRoomRequest, UpdateRoomConfigRequest, RoomDTO, RoomListResponse}.java`
    *   **Summary:** DTOs mirror the OpenAPI schemas, including validation limits for titles and JSON property names expected by the frontend.
    *   **Recommendation:** Use these DTOs verbatim; if request/response shapes need tweaks, update both DTOs and `RoomMapper` simultaneously to keep API-consumer expectations aligned.

### Implementation Tips & Notes
*   **Tip:** Let `RoomService` throw `RoomNotFoundException`/`FeatureNotAvailableException`; the registered `ExceptionMapper`s already translate them into the standardized `ErrorResponse`, so the controller can stay focused on happy-path transformations.
*   **Tip:** `RoomService.findByOwnerId(UUID)` returns a `Multi<Room>`; continue collecting it with `.collect().asList()` before applying pagination so you can reuse the existing manual paging math with proper bounds checks and early `badRequest` responses.
*   **Tip:** Privacy mode strings should flow through `resolvePrivacyMode` so invalid values trigger a consistent `IllegalArgumentException` and `400` response listing the allowed enum values.
*   **Note:** Authentication is currently permissive for create/get endpoints (per OpenAPI security array); keep the annotations (`@PermitAll`, `@RolesAllowed("USER")`) consistent so that Iteration 3's JWT filter can enforce them without code churn.
*   **Note:** Use the builder-style helpers (`badRequest`, `forbiddenResponse`, `unauthorizedException`) when you must short-circuit requests (e.g., pagination bounds) to keep error payloads uniform with the rest of the API.
