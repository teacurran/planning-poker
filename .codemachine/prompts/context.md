# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T3",
  "iteration_id": "I2",
  "iteration_goal": "Implement foundational domain services (Room Service, basic User Service), define REST API contracts (OpenAPI specification), and establish WebSocket protocol specification to enable frontend integration and parallel feature development.",
  "description": "Create `RoomService` domain service implementing core room operations: create room (generate 6-character nanoid, validate privacy mode, initialize config JSONB), update room configuration (deck type, rules, title), delete room (soft delete with `deleted_at`), find room by ID, list rooms by owner. Use `RoomRepository` for database operations. Implement reactive methods returning `Uni<>` for single results, `Multi<>` for lists. Validate business rules (room title length, valid privacy modes, deck type enum). Handle JSONB serialization for room configuration. Add transaction boundaries with `@Transactional`.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Room entity and repository from I1, Room management requirements from product spec, Nanoid generation pattern (6 characters, a-z0-9)",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/room/Room.java",
    "backend/src/main/java/com/scrumpoker/repository/RoomRepository.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/domain/room/RoomService.java",
    "backend/src/main/java/com/scrumpoker/domain/room/RoomConfig.java",
    "backend/src/main/java/com/scrumpoker/domain/room/RoomNotFoundException.java"
  ],
  "deliverables": "RoomService class with methods: `createRoom()`, `updateRoomConfig()`, `deleteRoom()`, `findById()`, `findByOwnerId()`, Nanoid generation utility for unique room IDs, RoomConfig POJO with fields: deckType, timerEnabled, timerDurationSeconds, revealBehavior, Business validation (title max 200 chars, valid privacy enum), Reactive return types (Uni, Multi), Custom exception for room not found scenarios",
  "acceptance_criteria": "Service methods compile and pass unit tests (mocked repository), Room creation generates unique 6-character IDs (test collision resistance with 1000 iterations), JSONB config serialization/deserialization works correctly, Soft delete sets `deleted_at` timestamp without removing database row, Business validation throws appropriate exceptions (e.g., `IllegalArgumentException` for invalid title), Service transactional boundaries configured correctly",
  "dependencies": ["I1.T4", "I1.T7"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Core Gameplay Requirements (from 01_Context_and_Drivers.md)

```markdown
#### Core Gameplay Requirements
- **Real-time Estimation:** WebSocket-based blind card selection with configurable deck types (Fibonacci, T-shirt, custom)
- **Session Management:** Host controls for round lifecycle (start, lock, reveal, reset), participant management (kick, mute)
- **Calculation Engine:** Automatic computation of average, median, and consensus indicators upon reveal
- **Room Controls:** Unique room ID generation (6-character nanoid), shareable links, privacy modes
```

### Context: Room Entity from Data Model (from 03_System_Structure_and_Data.md)

```markdown
| **Room** | Estimation session | `room_id` (PK, nanoid 6-char), `owner_id` (FK nullable for anonymous), `org_id` (FK nullable), `title`, `privacy_mode` (PUBLIC/INVITE_ONLY/ORG_RESTRICTED), `config` (JSONB: deck, rules, timer), `created_at`, `last_active_at` |
```

### Context: Data Model Design Principles (from 03_System_Structure_and_Data.md)

```markdown
**Design Principles:**
1. **Normalized Core Entities:** Users, Rooms, Organizations follow 3NF to prevent update anomalies
2. **Denormalized Read Models:** SessionSummary and VoteStatistics tables precompute aggregations for reporting performance
3. **JSONB for Flexibility:** RoomConfig, DeckDefinition, UserPreferences stored as JSONB to support customization without schema migrations
4. **Soft Deletes:** Critical entities (Users, Rooms) use `deleted_at` timestamp for audit trail and GDPR compliance
5. **Partitioning Strategy:** SessionHistory and AuditLog partitioned by month for query performance and data lifecycle management
```

### Context: Room Service Component (from 03_System_Structure_and_Data.md)

```markdown
Component(room_service, "Room Service", "Domain Logic", "Room creation, join logic, deck configuration, privacy controls")
```

### Context: Performance NFRs (from 01_Context_and_Drivers.md)

```markdown
#### Performance
- **Latency:** <200ms round-trip time for WebSocket messages within region
- **Throughput:** Support 500 concurrent sessions with 6,000 active WebSocket connections
- **Response Time:** REST API endpoints respond within <500ms for p95
- **Real-time Updates:** State synchronization across clients within 100ms
```

### Context: Task I2.T3 Implementation Plan (from 02_Iteration_I2.md)

```markdown
*   **Task 2.3: Implement Room Service (CRUD Operations)**
    *   **Description:** Create `RoomService` domain service implementing core room operations: create room (generate 6-character nanoid, validate privacy mode, initialize config JSONB), update room configuration (deck type, rules, title), delete room (soft delete with `deleted_at`), find room by ID, list rooms by owner. Use `RoomRepository` for database operations. Implement reactive methods returning `Uni<>` for single results, `Multi<>` for lists. Validate business rules (room title length, valid privacy modes, deck type enum). Handle JSONB serialization for room configuration. Add transaction boundaries with `@Transactional`.
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
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** This is the Room JPA entity class extending `PanacheEntityBase` with Hibernate Reactive. It uses a **String primary key** (NOT UUID) for the `roomId` field, which must be a 6-character nanoid. The entity includes soft delete support via `deletedAt` timestamp, privacy mode enum, and a JSONB `config` column stored as a String.
    *   **Recommendations:**
        *   You MUST generate a 6-character nanoid (a-z0-9) for new rooms and assign it to `room.roomId` before persisting.
        *   The `config` field is stored as a raw JSON String in the entity. You MUST create a `RoomConfig` POJO and implement JSON serialization/deserialization logic to convert between the POJO and String.
        *   For soft delete, you MUST set `room.deletedAt = Instant.now()` instead of calling repository delete methods.
        *   The `@CreationTimestamp` and `@UpdateTimestamp` annotations will handle `createdAt` and `lastActiveAt` automatically - do NOT manually set these fields on create/update.
        *   The entity has nullable `owner` and `organization` relationships - handle null checks appropriately.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/RoomRepository.java`
    *   **Summary:** This is the reactive Panache repository for Room entities. Note that it implements `PanacheRepositoryBase<Room, String>` (String ID, not UUID). It already includes several custom finder methods like `findActiveByOwnerId()`, `findByOrgId()`, `findPublicRooms()`, etc.
    *   **Recommendations:**
        *   You MUST use the `RoomRepository` for all database operations via dependency injection (`@Inject RoomRepository roomRepository`).
        *   The repository already has `findActiveByOwnerId(UUID ownerId)` which you SHOULD use in your `findByOwnerId()` service method.
        *   For finding by ID, use the standard Panache method: `roomRepository.findById(roomId)` which returns `Uni<Room>`.
        *   All repository methods return reactive types (`Uni<>` or `Multi<>`). Your service methods MUST also return reactive types and chain operations using Mutiny operators like `.map()`, `.flatMap()`, `.onItem()`, etc.
        *   The repository queries already filter out soft-deleted rooms using `deletedAt is null` - leverage this pattern in your service layer.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/PrivacyMode.java`
    *   **Summary:** This enum defines the three privacy modes: PUBLIC, INVITE_ONLY, ORG_RESTRICTED. These match the database privacy_mode_enum type.
    *   **Recommendations:**
        *   You MUST validate that the privacy mode parameter is one of these enum values when creating or updating a room.
        *   You SHOULD use standard Java enum validation rather than custom string matching.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
    *   **Summary:** This shows the project's repository pattern - it's an `@ApplicationScoped` CDI bean implementing `PanacheRepositoryBase<User, UUID>` with custom finder methods returning `Uni<>` types.
    *   **Recommendations:**
        *   You MUST follow the exact same pattern for your RoomService: make it `@ApplicationScoped` and inject the RoomRepository.
        *   Notice the pattern for active queries: `find("email = ?1 and deletedAt is null", email)` - this is the soft delete pattern you should understand.

*   **File:** `backend/pom.xml`
    *   **Summary:** The project uses Quarkus 3.15.1 with hibernate-reactive-panache, reactive-pg-client, quarkus-rest-jackson, and includes AssertJ for testing. No JSON library is explicitly added beyond Jackson (included with quarkus-rest-jackson).
    *   **Recommendations:**
        *   You SHOULD use Jackson's `ObjectMapper` for JSONB serialization/deserialization (available via CDI injection with `@Inject ObjectMapper objectMapper`).
        *   For nanoid generation, since there's no existing nanoid library, you MUST implement a simple utility using Java's `SecureRandom` to generate 6-character alphanumeric IDs.
        *   The project uses `@Transactional` from `jakarta.transaction.Transactional` - you MUST annotate service methods that modify data with this annotation.

### Implementation Tips & Notes

*   **Tip - Nanoid Generation:** I confirmed there is no nanoid library in the pom.xml. You MUST implement a simple utility method that generates 6-character alphanumeric strings using `SecureRandom`. The charset should be lowercase a-z and digits 0-9 (36 characters total). Here's a pattern to follow:
    ```java
    private static final String NANOID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private String generateNanoid() {
        StringBuilder id = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            id.append(NANOID_ALPHABET.charAt(RANDOM.nextInt(NANOID_ALPHABET.length())));
        }
        return id.toString();
    }
    ```

*   **Tip - JSONB Handling:** The Room entity stores config as a String in a JSONB column. You MUST:
    1. Create a `RoomConfig.java` POJO with fields: `String deckType`, `boolean timerEnabled`, `int timerDurationSeconds`, `String revealBehavior`, and any other configuration fields.
    2. Inject Jackson's `ObjectMapper` into your service: `@Inject ObjectMapper objectMapper`.
    3. Serialize: `String configJson = objectMapper.writeValueAsString(roomConfig)` before setting `room.config = configJson`.
    4. Deserialize: `RoomConfig config = objectMapper.readValue(room.config, RoomConfig.class)`.
    5. Wrap these operations in try-catch for `JsonProcessingException` and convert to runtime exceptions.

*   **Tip - Reactive Patterns:** All service methods MUST return `Uni<>` or `Multi<>` types. Chain operations using:
    - `.map()` for synchronous transformations
    - `.flatMap()` for async operations that return another `Uni`
    - `.onItem().transform()` for transformations
    - `.onFailure().transform()` for exception mapping
    - Example: `roomRepository.findById(id).onItem().ifNull().failWith(() -> new RoomNotFoundException(id))`

*   **Tip - Transaction Boundaries:** Methods that create, update, or delete rooms MUST be annotated with `@Transactional`. Read-only methods (find/get) do NOT need this annotation. Import from `jakarta.transaction.Transactional`.

*   **Tip - Validation:**
    - Room title: max 255 chars (from Room entity `@Size(max = 255)`)
    - The plan says "max 200 chars" but the entity says 255 - use 255 to match the database constraint
    - Privacy mode: MUST be one of the PrivacyMode enum values
    - Throw `IllegalArgumentException` for validation failures with descriptive messages

*   **Warning - String vs UUID:** The Room entity uses a **String primary key**, NOT UUID. This is critical - do NOT try to use UUID methods or conversions. The RoomRepository is typed as `PanacheRepositoryBase<Room, String>`.

*   **Note - Soft Delete Pattern:** To soft delete a room:
    ```java
    room.deletedAt = Instant.now();
    return roomRepository.persist(room);
    ```
    Do NOT use `roomRepository.delete(room)` as that would hard delete the row.

*   **Note - Owner Handling:** The Room entity has a nullable `owner` field. For anonymous rooms, this will be null. When finding rooms by owner, make sure to handle the UUID parameter correctly and use the existing `roomRepository.findActiveByOwnerId(ownerId)` method.

*   **Note - Exception Handling:** Create a `RoomNotFoundException` that extends `RuntimeException`. This should take the roomId as a constructor parameter and provide a clear message like: `"Room not found: " + roomId`.

### Project Structure Conventions

*   Service classes go in `backend/src/main/java/com/scrumpoker/domain/room/` (same package as Room entity)
*   POJOs for JSONB (like RoomConfig) go in the same package
*   Custom exceptions go in the same domain package
*   Services are `@ApplicationScoped` CDI beans
*   Use constructor injection or field injection with `@Inject` for dependencies
*   Follow the reactive programming model - all methods return `Uni<>` or `Multi<>`
