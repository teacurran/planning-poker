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

### Context: rest-api-endpoints (from 04_Behavior_and_Communication.md)

The REST API follows RESTful conventions with JSON request/response bodies. All endpoints are prefixed with `/api/v1/` for versioning. Key characteristics:

- **HTTP Methods**: GET (retrieve), POST (create), PUT (update), DELETE (soft delete)
- **Status Codes**: 200 OK, 201 Created, 204 No Content, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 500 Internal Server Error
- **Authentication**: Bearer JWT tokens via `Authorization` header for protected endpoints
- **Content Type**: `application/json` for all requests/responses
- **Error Format**: Standardized error response with `error` code, `message`, and `timestamp` fields

Room management endpoints include:
- `POST /api/v1/rooms` - Create new room (authenticated or anonymous)
- `GET /api/v1/rooms/{roomId}` - Get room details (public or member access)
- `PUT /api/v1/rooms/{roomId}/config` - Update room configuration (host only)
- `DELETE /api/v1/rooms/{roomId}` - Soft delete room (owner only)
- `GET /api/v1/users/{userId}/rooms` - List user's owned rooms (authenticated)

### Context: room-management-endpoints (from api/openapi.yaml)

#### POST /api/v1/rooms
Creates a new estimation room. Can be created by authenticated users (owned room) or anonymously (ephemeral room). Anonymous rooms are deleted after 24 hours of inactivity.

**Security**: Allows both authenticated (BearerAuth) and anonymous access.

**Request Body** (CreateRoomRequest):
- `title` (required, string, max 255 chars): Room display name
- `privacyMode` (optional, enum): PUBLIC | INVITE_ONLY | ORG_RESTRICTED
- `config` (optional, RoomConfigDTO): Room configuration settings

**Responses**:
- `201 Created`: Returns RoomDTO with generated 6-character room ID
- `400 Bad Request`: Invalid request parameters
- `403 Forbidden`: Room creation limit reached for subscription tier

#### GET /api/v1/rooms/{roomId}
Returns room metadata, configuration, and current participant list. Does not include vote data (use WebSocket).

**Security**: Allows both authenticated and anonymous for public rooms.

**Parameters**:
- `roomId` (path, required, string, pattern `^[a-z0-9]{6}$`): 6-character room identifier

**Responses**:
- `200 OK`: Returns RoomDTO
- `404 Not Found`: Room not found or deleted

#### PUT /api/v1/rooms/{roomId}/config
Updates room settings (deck type, timer, privacy mode). Only the room host can modify configuration. Changes take effect immediately for active session.

**Security**: Requires BearerAuth (authenticated users only).

**Request Body** (UpdateRoomConfigRequest):
- `title` (optional, string, max 255 chars)
- `privacyMode` (optional, enum)
- `config` (optional, RoomConfigDTO)

**Responses**:
- `200 OK`: Returns updated RoomDTO
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User is not the room host
- `404 Not Found`: Room not found

#### DELETE /api/v1/rooms/{roomId}
Soft deletes a room. Only the room owner can delete. Room becomes inaccessible but historical data is retained.

**Security**: Requires BearerAuth.

**Responses**:
- `204 No Content`: Room deleted successfully
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User is not the room owner
- `404 Not Found`: Room not found

#### GET /api/v1/users/{userId}/rooms
Returns paginated list of rooms owned by the user. Excludes soft-deleted rooms.

**Security**: Requires BearerAuth. Users can only access their own rooms.

**Parameters**:
- `userId` (path, required, UUID): User ID
- `page` (query, optional, integer, default 0): Page number (0-indexed)
- `size` (query, optional, integer, default 20, max 100): Page size

**Responses**:
- `200 OK`: Returns RoomListResponse with pagination metadata
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User accessing another user's rooms

### Context: room-dto-schemas (from api/openapi.yaml)

#### RoomDTO
```yaml
type: object
required: [roomId, title, privacyMode, config, createdAt, lastActiveAt]
properties:
  roomId: string (pattern ^[a-z0-9]{6}$)
  ownerId: UUID (nullable)
  organizationId: UUID (nullable)
  title: string (max 255 chars)
  privacyMode: enum (PUBLIC, INVITE_ONLY, ORG_RESTRICTED)
  config: RoomConfigDTO
  createdAt: date-time
  lastActiveAt: date-time
  participants: array of RoomParticipantDTO
```

#### RoomConfigDTO
```yaml
type: object
properties:
  deckType: enum (fibonacci, tshirt, powers_of_2, custom)
  customDeck: array of strings (max 10 chars each)
  timerEnabled: boolean
  timerDurationSeconds: integer (10-600)
  revealBehavior: enum (manual, automatic, timer)
  allowObservers: boolean
  allowAnonymousVoters: boolean
```

#### CreateRoomRequest
```yaml
type: object
required: [title]
properties:
  title: string (max 255 chars)
  privacyMode: PrivacyMode enum
  config: RoomConfigDTO
```

#### UpdateRoomConfigRequest
```yaml
type: object
properties:
  title: string (max 255 chars)
  privacyMode: PrivacyMode enum
  config: RoomConfigDTO
```

#### RoomListResponse
```yaml
type: object
required: [rooms, page, size, totalElements, totalPages]
properties:
  rooms: array of RoomDTO
  page: integer (current page, 0-indexed)
  size: integer (page size)
  totalElements: integer (total room count)
  totalPages: integer (total pages)
```

### Context: error-response-schema (from api/openapi.yaml)

All error responses follow this standardized format:

```yaml
ErrorResponse:
  type: object
  required: [error, message, timestamp]
  properties:
    error: string (machine-readable error code, e.g., "VALIDATION_ERROR")
    message: string (human-readable error message)
    timestamp: string (ISO 8601 date-time)
    details: object (optional additional context)
```

**Standard Error Codes**:
- `VALIDATION_ERROR` (400): Invalid request parameters or body
- `UNAUTHORIZED` (401): Missing or invalid authentication credentials
- `FORBIDDEN` (403): Insufficient permissions to access resource
- `NOT_FOUND` (404): Resource not found
- `INTERNAL_SERVER_ERROR` (500): Internal server error

### Context: component-diagram (from 03_System_Structure_and_Data.md)

The Quarkus application follows a hexagonal (ports and adapters) architecture:

**REST Controllers Layer** (`api.rest`):
- HTTP endpoint handlers exposing RESTful APIs
- Use JAX-RS annotations (`@Path`, `@POST`, `@GET`, `@PUT`, `@DELETE`)
- Inject domain services via CDI (`@Inject`)
- Convert domain entities to DTOs using mappers
- Return reactive types (`Uni<Response>`) for non-blocking I/O
- Handle exceptions with JAX-RS exception mappers

**Domain Services Layer** (`domain`):
- Core business logic implementing estimation rules, room lifecycle
- Use reactive return types (`Uni<>`, `Multi<>`)
- Validate business rules before persistence
- Handle JSONB serialization for configuration fields
- Throw custom domain exceptions (e.g., `RoomNotFoundException`)

**Repository Layer** (`repository`):
- Data access abstractions using Hibernate Reactive Panache
- Reactive methods returning `Uni<>` for single results, `Multi<>` for lists
- Custom finder methods with query optimization

### Context: data-model-room-entity (from 03_System_Structure_and_Data.md)

**Room Entity**:
- `room_id` (PK, VARCHAR(6)): 6-character nanoid generated by application
- `owner_id` (FK to User, nullable): Room owner, null for anonymous rooms
- `org_id` (FK to Organization, nullable): Organization workspace, null for personal rooms
- `title` (VARCHAR(255), not null): Room display name
- `privacy_mode` (ENUM: PUBLIC, INVITE_ONLY, ORG_RESTRICTED, not null): Access control
- `config` (JSONB): Room configuration (deck type, timer, rules)
- `created_at` (TIMESTAMP): Room creation timestamp
- `last_active_at` (TIMESTAMP): Last activity timestamp for cleanup
- `deleted_at` (TIMESTAMP, nullable): Soft delete timestamp

**Key Indexes**:
- `Room(owner_id, created_at DESC)` - User's recent rooms query
- `Room(org_id, last_active_at DESC)` - Organization room listing
- `Room(privacy_mode, last_active_at DESC) WHERE deleted_at IS NULL` - Public room discovery

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** This file contains the complete domain service for room management with all CRUD operations already implemented.
    *   **Recommendation:** You MUST import and use this service in your RoomController. It provides methods: `createRoom()`, `updateRoomConfig()`, `updateRoomTitle()`, `deleteRoom()`, `findById()`, `findByOwnerId()`, `getRoomConfig()`. All methods return reactive `Uni<>` types for non-blocking I/O.
    *   **Key Methods to Use**:
        - `createRoom(String title, PrivacyMode privacyMode, User owner, RoomConfig config)` - Returns `Uni<Room>`
        - `updateRoomConfig(String roomId, RoomConfig config)` - Returns `Uni<Room>`
        - `deleteRoom(String roomId)` - Returns `Uni<Room>`
        - `findById(String roomId)` - Returns `Uni<Room>`, throws `RoomNotFoundException` if not found
        - `findByOwnerId(UUID ownerId)` - Returns `Multi<Room>`

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** This is the JPA entity for Room with Panache EntityBase. It uses public fields for direct access (Panache pattern).
    *   **Recommendation:** You MUST import this entity to convert to DTOs using the mapper.
    *   **Key Fields**: `roomId` (String), `owner` (User), `title` (String), `privacyMode` (PrivacyMode enum), `config` (String - serialized JSONB), `createdAt`, `lastActiveAt`, `deletedAt`

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomConfig.java`
    *   **Summary:** This is the POJO for room configuration that gets serialized to JSONB.
    *   **Recommendation:** You MUST use this class when mapping from UpdateRoomConfigRequest to the domain model. The RoomService handles JSON serialization internally.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomNotFoundException.java`
    *   **Summary:** Custom exception thrown by RoomService when a room is not found.
    *   **Recommendation:** You MUST handle this exception in your controller and convert it to a 404 Not Found response with the standard ErrorResponse format.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/PrivacyMode.java`
    *   **Summary:** Enum defining room privacy modes: PUBLIC, INVITE_ONLY, ORG_RESTRICTED.
    *   **Recommendation:** You MUST use this enum in your DTOs and request classes to match the OpenAPI specification.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** This is the JPA entity for User with OAuth authentication fields.
    *   **Recommendation:** Your RoomService methods accept a User parameter for room ownership. You will need to get the current authenticated user from the security context (this will be implemented in Iteration 3, for now you can pass `null` for anonymous rooms or mock a user for testing).

*   **File:** `backend/src/main/java/com/scrumpoker/repository/RoomRepository.java`
    *   **Summary:** Panache repository for Room entity with custom finder methods.
    *   **Recommendation:** The RoomService already uses this repository internally. You DO NOT need to inject this directly into your controller.

### Implementation Tips & Notes

*   **Tip:** I have confirmed that the Quarkus Reactive extension is already configured in the project. Your controller MUST use JAX-RS reactive patterns with `Uni<Response>` return types instead of blocking types.

*   **Tip:** For DTO mapping, you are expected to use **MapStruct** (specified in deliverables). You SHOULD create a `RoomMapper` interface with `@Mapper(componentModel = "cdi")` annotation. MapStruct will generate the implementation at compile time. Example pattern:
    ```java
    @Mapper(componentModel = "cdi")
    public interface RoomMapper {
        RoomDTO toDTO(Room room);
        Room toEntity(CreateRoomRequest request);
        // Add methods for each conversion needed
    }
    ```

*   **Note:** The project follows Quarkus conventions where REST controllers are placed in `api/rest` package, DTOs in `api/rest/dto`, and mappers in `api/rest/mapper`. You MUST follow this package structure.

*   **Note:** For exception handling, you SHOULD create a JAX-RS `ExceptionMapper<RoomNotFoundException>` that converts the domain exception to a proper HTTP response. This provides centralized exception handling across all controllers.

*   **Warning:** The OpenAPI spec shows that `POST /api/v1/rooms` allows BOTH authenticated and anonymous access (security array includes both `BearerAuth` and empty `{}`). However, authentication is not yet implemented (that's Iteration 3). For now, create the endpoint assuming authentication will be added later, but don't enforce it yet.

*   **Warning:** The `DELETE` endpoint MUST call `RoomService.deleteRoom()` which performs a soft delete (sets `deleted_at` timestamp). It MUST NOT physically delete the room from the database. The service already handles this correctly.

*   **Tip:** For the pagination in `GET /api/v1/users/{userId}/rooms`, the RoomService returns a `Multi<Room>` which is a reactive stream. You will need to collect this into a list and implement pagination logic. Quarkus Panache provides `PanacheQuery.page(page, size)` methods that you can use, but since RoomService already returns Multi, you may need to modify the service or handle pagination in the controller layer.

*   **Tip:** The OpenAPI spec defines strict validation rules (e.g., title max 255 chars, roomId pattern `^[a-z0-9]{6}$`). You SHOULD use Bean Validation annotations (`@NotNull`, `@Size`, `@Pattern`) on your DTO classes to enable automatic validation by Quarkus. This will trigger 400 Bad Request responses automatically for invalid input.

*   **Note:** Your RoomController MUST use the `@Path("/api/v1/rooms")` annotation at the class level to match the OpenAPI spec. Individual endpoint methods use `@POST`, `@GET`, `@PUT`, `@DELETE` with path parameters where needed.

*   **Tip:** For reactive response building, use the pattern:
    ```java
    return roomService.findById(roomId)
        .onItem().transform(room -> Response.ok(roomMapper.toDTO(room)).build())
        .onFailure(RoomNotFoundException.class).recoverWithItem(
            ex -> Response.status(404).entity(new ErrorResponse(...)).build()
        );
    ```

*   **Tip:** The project uses Jackson for JSON serialization. Quarkus automatically handles DTO serialization/deserialization. You DO NOT need to manually convert DTOs to JSON strings.

*   **Note:** For the `RoomListResponse` pagination wrapper, you will need to create this DTO exactly as specified in the OpenAPI schema with fields: `rooms`, `page`, `size`, `totalElements`, `totalPages`. All fields are required per the spec.

*   **Warning:** The authorization requirement `@RolesAllowed("USER")` is specified in the deliverables, but authentication/authorization is not implemented until Iteration 3. You SHOULD add these annotations now (they will be ignored until the security filter is implemented), but they won't be enforced yet. This prepares the code for future security integration.
