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
  "inputs": "OpenAPI specification from I2.T1 (endpoint definitions), RoomService from I2.T3, JAX-RS reactive patterns",
  "input_files": [
    "api/openapi.yaml",
    "backend/src/main/java/com/scrumpoker/domain/room/RoomService.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/RoomController.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/RoomDTO.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/CreateRoomRequest.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/UpdateRoomConfigRequest.java",
    "backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java"
  ],
  "deliverables": "RoomController with 5 endpoint methods matching OpenAPI spec, DTO classes for requests and responses, MapStruct mapper for entity ↔ DTO conversion, Exception handlers for 404, 400 errors, Authorization annotations (`@RolesAllowed(\"USER\")`), Reactive return types (Uni<Response>)",
  "acceptance_criteria": "Endpoints accessible via `curl` or Postman against running Quarkus dev server, POST creates room, returns 201 Created with RoomDTO body, GET retrieves room by ID, returns 200 OK or 404 Not Found, PUT updates config, returns 200 OK with updated RoomDTO, DELETE soft deletes room, returns 204 No Content, GET user's rooms returns paginated list (if many rooms), DTOs match OpenAPI schema definitions exactly, Authorization prevents unauthorized users from deleting other users' rooms",
  "dependencies": [
    "I2.T1",
    "I2.T3"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: REST API Endpoints Overview (from 04_Behavior_and_Communication.md)

```markdown
#### REST API Endpoints Overview

**Room Management:**
- `POST /api/v1/rooms` - Create new room (authenticated or anonymous)
- `GET /api/v1/rooms/{roomId}` - Get room configuration and current state
- `PUT /api/v1/rooms/{roomId}/config` - Update room settings (host only)
- `DELETE /api/v1/rooms/{roomId}` - Delete room (owner only)
- `GET /api/v1/users/{userId}/rooms` - List user's owned rooms
```

### Context: Synchronous REST Pattern (from 04_Behavior_and_Communication.md)

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
- `POST /api/v1/rooms` - Create new estimation room
- `GET /api/v1/rooms/{roomId}` - Retrieve room configuration
```

### Context: API Style (from 04_Behavior_and_Communication.md)

```markdown
#### API Style

**Primary API Style:** **RESTful JSON API (OpenAPI 3.1 Specification)**

**Rationale:**
- **Simplicity & Familiarity:** REST over HTTPS provides a well-understood contract for CRUD operations on resources (users, rooms, subscriptions)
- **Tooling Ecosystem:** OpenAPI specification enables automatic client SDK generation (TypeScript for React frontend), API documentation (Swagger UI), and contract testing
- **Caching Support:** HTTP semantics (ETags, Cache-Control headers) enable browser and CDN caching for read-heavy endpoints (room configurations, user profiles)
- **Versioning Strategy:** URL-based versioning (`/api/v1/`) for backward compatibility during iterative releases
```

### Context: Room Management Endpoints from OpenAPI (from api/openapi.yaml)

```yaml
# POST /api/v1/rooms - Create new estimation room
# Response: 201 Created with RoomDTO body
# Security: BearerAuth OR anonymous (empty security)
# Request Body: CreateRoomRequest (title required, privacyMode and config optional)

# GET /api/v1/rooms/{roomId} - Get room configuration and state
# Response: 200 OK with RoomDTO, or 404 Not Found
# Security: BearerAuth OR anonymous for public rooms
# Parameters: roomId (path, 6-char nanoid pattern)

# DELETE /api/v1/rooms/{roomId} - Soft delete room
# Response: 204 No Content, or 401/403/404
# Security: BearerAuth required
# Only owner can delete

# PUT /api/v1/rooms/{roomId}/config - Update room configuration
# Response: 200 OK with updated RoomDTO, or 400/401/403/404
# Security: BearerAuth required
# Only host can modify
# Request Body: UpdateRoomConfigRequest (all fields optional: title, privacyMode, config)

# GET /api/v1/users/{userId}/rooms - List user's owned rooms
# Response: 200 OK with RoomListResponse (paginated), or 401/403
# Security: BearerAuth required
# Parameters: userId (path, UUID), page (query, default 0), size (query, default 20, max 100)
```

### Context: DTO Schemas from OpenAPI (from api/openapi.yaml)

```yaml
# RoomDTO (lines 1339-1392)
properties:
  roomId: string (pattern '^[a-z0-9]{6}$', 6 chars)
  ownerId: UUID (nullable)
  organizationId: UUID (nullable)
  title: string (max 255 chars)
  privacyMode: enum (PUBLIC, INVITE_ONLY, ORG_RESTRICTED)
  config: RoomConfigDTO
  createdAt: date-time
  lastActiveAt: date-time
  participants: array of RoomParticipantDTO

# CreateRoomRequest (lines 1433-1446)
required: [title]
properties:
  title: string (max 255 chars)
  privacyMode: PrivacyMode enum
  config: RoomConfigDTO

# UpdateRoomConfigRequest (lines 1448-1457)
properties: (all optional)
  title: string (max 255 chars)
  privacyMode: PrivacyMode enum
  config: RoomConfigDTO

# RoomListResponse (lines 1496-1524)
required: [rooms, page, size, totalElements, totalPages]
properties:
  rooms: array of RoomDTO
  page: integer (0-indexed)
  size: integer
  totalElements: integer (total count)
  totalPages: integer
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** This is the complete domain service implementing all room business logic including create, update config, update title, soft delete, find by ID, and find by owner ID. It uses reactive Uni/Multi return types, handles JSONB serialization for room configuration, validates business rules (title length, non-null privacy mode), and generates unique 6-character nanoid room IDs.
    *   **Recommendation:** You MUST inject this `RoomService` class into your `RoomController` using `@Inject`. All business logic is already implemented - your controller should simply delegate to these service methods and convert domain entities to DTOs.
    *   **Key Methods to Use:**
        - `createRoom(String title, PrivacyMode privacyMode, User owner, RoomConfig config)` returns `Uni<Room>`
        - `findById(String roomId)` returns `Uni<Room>` (throws `RoomNotFoundException` if not found or deleted)
        - `updateRoomConfig(String roomId, RoomConfig config)` returns `Uni<Room>`
        - `updateRoomTitle(String roomId, String title)` returns `Uni<Room>`
        - `deleteRoom(String roomId)` returns `Uni<Room>` (soft delete)
        - `findByOwnerId(UUID ownerId)` returns `Multi<Room>`

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/mapper/RoomMapper.java`
    *   **Summary:** This mapper converts between Room entities and RoomDTOs. It handles JSONB config deserialization and provides `toDTO(Room)` and `toConfig(RoomConfigDTO)` methods.
    *   **Recommendation:** You MUST inject and use this existing `RoomMapper` for all entity ↔ DTO conversions. The `toDTO()` method already handles all Room entity fields including owner ID, organization ID, config JSONB parsing, and timestamp formatting. The `toConfig()` method converts RoomConfigDTO to domain RoomConfig objects.
    *   **Note:** The mapper already exists and is complete - DO NOT create a new mapper class. The target_files list mentions mapper but you should VERIFY it exists and use it as-is.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/RoomDTO.java`
    *   **Summary:** This DTO is already fully implemented matching the OpenAPI schema exactly, with all required fields (roomId, ownerId, organizationId, title, privacyMode, config, createdAt, lastActiveAt, participants).
    *   **Recommendation:** This DTO already exists - DO NOT recreate it. Import and use it in your controller for response bodies.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/CreateRoomRequest.java`
    *   **Summary:** This DTO already exists (based on directory structure).
    *   **Recommendation:** You SHOULD verify this file exists and matches the OpenAPI spec requirements (title required, optional privacyMode and config). If it doesn't exist, create it matching the OpenAPI `CreateRoomRequest` schema.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/UpdateRoomConfigRequest.java`
    *   **Summary:** This DTO already exists (based on directory structure).
    *   **Recommendation:** You SHOULD verify this file exists and matches OpenAPI spec (optional title, privacyMode, config fields). If missing, create it matching the OpenAPI `UpdateRoomConfigRequest` schema.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/exception/RoomNotFoundExceptionMapper.java`
    *   **Summary:** This JAX-RS exception mapper is already fully implemented. It automatically converts `RoomNotFoundException` thrown by `RoomService` into 404 HTTP responses with a standardized ErrorResponse body.
    *   **Recommendation:** This exception handler is already in place - your controller does NOT need to handle RoomNotFoundException explicitly. Just let the exception bubble up and the mapper will convert it to 404 automatically.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/ErrorResponse.java`
    *   **Summary:** Standardized error response DTO (already exists based on exception mapper usage).
    *   **Recommendation:** For 400 validation errors (e.g., invalid title length), you SHOULD throw `IllegalArgumentException` which should already have a mapper, OR manually construct ErrorResponse and return Response.status(400).entity(errorResponse).build().

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** JPA entity for Room with all required fields including nanoid roomId, owner (nullable User), organization (nullable), title, privacyMode enum, config JSONB string, createdAt, lastActiveAt, and deletedAt for soft deletes.
    *   **Recommendation:** You DO NOT need to interact with this entity directly in your controller - always go through RoomService and use the mapper to convert to DTOs.

### Implementation Tips & Notes

*   **Tip:** The OpenAPI spec is located at `api/openapi.yaml` and defines all 5 endpoints you need to implement. Review lines 313-456 which specify:
    - POST /api/v1/rooms (lines 313-349) - returns 201 Created
    - GET /api/v1/rooms/{roomId} (lines 351-374) - returns 200 OK or 404
    - DELETE /api/v1/rooms/{roomId} (lines 376-395) - returns 204 No Content
    - PUT /api/v1/rooms/{roomId}/config (lines 397-430) - returns 200 OK
    - GET /api/v1/users/{userId}/rooms (lines 432-456) - returns paginated RoomListResponse

*   **Tip:** All RoomService methods return reactive types (Uni or Multi). Your JAX-RS controller endpoints MUST also return these reactive types. The pattern is:
    ```java
    @POST
    public Uni<Response> createRoom(CreateRoomRequest request) {
        return roomService.createRoom(...)
            .onItem().transform(room -> Response.status(201).entity(roomMapper.toDTO(room)).build());
    }
    ```

*   **Tip:** For authorization, the task mentions `@RolesAllowed("USER")` but authentication is not yet implemented (that's iteration I3). For NOW, you SHOULD add the annotation for future compatibility but it won't be enforced yet. The important authorization check is ensuring room owners can delete their own rooms - you'll need to verify `room.owner.userId` matches the authenticated user ID (when auth is implemented).

*   **Warning:** The OpenAPI spec shows POST /api/v1/rooms allows BOTH authenticated users AND anonymous users (security: `- BearerAuth: []` and `- {}`). For now, since auth isn't implemented, all requests are effectively anonymous. You SHOULD create rooms with `owner = null` for now.

*   **Note:** The GET /api/v1/users/{userId}/rooms endpoint requires pagination parameters (page, size) per OpenAPI spec (lines 442-443). You MUST return a `RoomListResponse` DTO with pagination metadata (page, size, totalElements, totalPages). The response schema is defined at lines 1496-1524. You'll need to implement pagination logic when calling `roomService.findByOwnerId()`.

*   **Note:** The OpenAPI spec defines `RoomParticipantDTO` in the participants field of RoomDTO, but the existing mapper sets this to an empty list with a TODO comment. You SHOULD keep this as empty list for now - participants will be populated via WebSocket state in iteration I4.

*   **Critical:** For the PUT /api/v1/rooms/{roomId}/config endpoint, the OpenAPI UpdateRoomConfigRequest schema (lines 1448-1457) allows updating BOTH title AND config together OR separately (all fields optional). The RoomService has separate methods `updateRoomConfig()` and `updateRoomTitle()`. You MUST call the appropriate service method(s) based on which fields are present in the request.

*   **Performance:** For GET /api/v1/users/{userId}/rooms pagination, the `Multi<Room>` returned by `findByOwnerId()` returns ALL rooms. You will need to collect this to a List and implement pagination logic manually (skip/take based on page/size params), or enhance RoomRepository to support pagination queries. For MVP, manual pagination is acceptable.

*   **Error Handling Pattern:** The existing codebase has exception mappers for 404 (RoomNotFoundException) and likely 400 (IllegalArgumentException). For validation errors, you SHOULD throw IllegalArgumentException with descriptive messages and let the mapper handle it, OR catch exceptions and manually create Response objects with ErrorResponse bodies.

### Authorization Strategy (for future reference)

*   **Current State:** Authentication/authorization is NOT yet implemented (iteration I3). User context and JWT validation don't exist yet.
*   **What to do NOW:** Add `@RolesAllowed("USER")` annotations where specified in the task, but they won't be enforced. For owner checks (DELETE endpoint), add TODO comments like `// TODO: Verify room.owner.userId matches authenticated user when auth is implemented`
*   **Future Implementation:** In I3, a JWT filter will extract user ID from token claims and set security context. The `@RolesAllowed` annotations will then be enforced by Quarkus Security.

### JAX-RS Reactive Patterns

*   **Pattern 1 - Simple transformation:**
    ```java
    return roomService.findById(roomId)
        .onItem().transform(room -> Response.ok(roomMapper.toDTO(room)).build());
    ```

*   **Pattern 2 - Chained operations:**
    ```java
    return roomService.createRoom(title, privacyMode, null, config)
        .onItem().transform(roomMapper::toDTO)
        .onItem().transform(dto -> Response.status(201).entity(dto).build());
    ```

*   **Pattern 3 - Multi to List (for pagination):**
    ```java
    return roomService.findByOwnerId(ownerId)
        .collect().asList()
        .onItem().transform(rooms -> {
            // Apply pagination logic here
            List<RoomDTO> dtoList = rooms.stream()
                .skip(page * size)
                .limit(size)
                .map(roomMapper::toDTO)
                .collect(Collectors.toList());

            int totalElements = rooms.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);

            RoomListResponse response = new RoomListResponse();
            response.rooms = dtoList;
            response.page = page;
            response.size = size;
            response.totalElements = totalElements;
            response.totalPages = totalPages;

            return Response.ok(response).build();
        });
    ```

*   **Pattern 4 - Error handling (if needed):**
    ```java
    return roomService.findById(roomId)
        .onItem().transform(...)
        .onFailure(RoomNotFoundException.class).recoverWithItem(
            failure -> Response.status(404).entity(new ErrorResponse("NOT_FOUND", failure.getMessage())).build()
        );
    ```
    Note: With exception mappers in place, you usually DON'T need explicit error handling - just let exceptions bubble up.

*   **Pattern 5 - Conditional logic in update:**
    ```java
    @PUT
    @Path("/{roomId}/config")
    public Uni<Response> updateRoomConfig(@PathParam("roomId") String roomId, UpdateRoomConfigRequest request) {
        Uni<Room> updateChain = roomService.findById(roomId); // Start with find

        // If title present, chain title update
        if (request.title != null) {
            updateChain = updateChain.flatMap(room -> roomService.updateRoomTitle(roomId, request.title));
        }

        // If config present, chain config update
        if (request.config != null) {
            RoomConfig config = roomMapper.toConfig(request.config);
            updateChain = updateChain.flatMap(room -> roomService.updateRoomConfig(roomId, config));
        }

        // If privacyMode present, need to update (note: RoomService doesn't have updatePrivacyMode method - may need to add)

        return updateChain
            .onItem().transform(roomMapper::toDTO)
            .onItem().transform(dto -> Response.ok(dto).build());
    }
    ```

### Required DTOs Status

*   **CreateRoomRequest.java** - Already exists, verify structure matches OpenAPI
*   **UpdateRoomConfigRequest.java** - Already exists, verify structure matches OpenAPI
*   **RoomListResponse.java** - NEEDS TO BE CREATED with fields: rooms (List<RoomDTO>), page, size, totalElements, totalPages

---

## 4. Quick Reference Checklist

Before you start coding, verify:

- [x] RoomService is fully implemented and injectable
- [x] RoomMapper exists and handles entity ↔ DTO conversion
- [x] RoomDTO, RoomConfigDTO, RoomParticipantDTO already exist
- [ ] CreateRoomRequest DTO exists (verify/create)
- [ ] UpdateRoomConfigRequest DTO exists (verify/create)
- [ ] RoomListResponse DTO exists (CREATE - lines 1496-1524 from OpenAPI)
- [x] Exception mappers for 404 and 400 are in place
- [x] OpenAPI spec reviewed for exact endpoint signatures
- [x] Reactive patterns (Uni/Multi) understood and ready to use

## 5. Step-by-Step Implementation Plan

1. **Verify/Create DTOs** (5 minutes)
   - Check CreateRoomRequest.java exists and matches OpenAPI
   - Check UpdateRoomConfigRequest.java exists and matches OpenAPI
   - Create RoomListResponse.java with pagination fields

2. **Create RoomController skeleton** (10 minutes)
   - Add @Path("/api/v1/rooms") to class
   - Inject RoomService and RoomMapper
   - Create 5 empty endpoint method signatures

3. **Implement POST /api/v1/rooms** (15 minutes)
   - Extract request fields
   - Convert PrivacyMode string to enum
   - Call roomService.createRoom()
   - Map to DTO and return 201 Created

4. **Implement GET /api/v1/rooms/{roomId}** (10 minutes)
   - Call roomService.findById()
   - Map to DTO and return 200 OK
   - Let exception mapper handle 404

5. **Implement DELETE /api/v1/rooms/{roomId}** (10 minutes)
   - Call roomService.deleteRoom()
   - Return 204 No Content
   - Add TODO for owner authorization check

6. **Implement PUT /api/v1/rooms/{roomId}/config** (20 minutes)
   - Handle optional title, privacyMode, config fields
   - Chain updates conditionally
   - Return 200 OK with updated DTO

7. **Implement GET /api/v1/users/{userId}/rooms** (20 minutes)
   - Extract pagination params
   - Call roomService.findByOwnerId()
   - Implement manual pagination (skip/limit)
   - Build RoomListResponse with metadata
   - Return 200 OK

8. **Test all endpoints** (30 minutes)
   - Start Quarkus dev mode
   - Use curl or Postman to test each endpoint
   - Verify responses match OpenAPI spec
   - Check error scenarios (404, 400)

Total estimated time: ~2 hours
