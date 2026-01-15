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
  "inputs": "*   Room entity and repository from I1\n        *   Room management requirements from product spec\n        *   Nanoid generation pattern (6 characters, a-z0-9)",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   RoomService class with methods: `createRoom()`, `updateRoomConfig()`, `deleteRoom()`, `findById()`, `findByOwnerId()`\n        *   Nanoid generation utility for unique room IDs\n        *   RoomConfig POJO with fields: deckType, timerEnabled, timerDurationSeconds, revealBehavior\n        *   Business validation (title max 200 chars, valid privacy enum)\n        *   Reactive return types (Uni, Multi)\n        *   Custom exception for room not found scenarios",
  "acceptance_criteria": "*   Service methods compile and pass unit tests (mocked repository)\n        *   Room creation generates unique 6-character IDs (test collision resistance with 1000 iterations)\n        *   JSONB config serialization/deserialization works correctly\n        *   Soft delete sets `deleted_at` timestamp without removing database row\n        *   Business validation throws appropriate exceptions (e.g., `IllegalArgumentException` for invalid title)\n        *   Service transactional boundaries configured correctly",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Task 2.3 Implement Room Service (from .codemachine/artifacts/plan/02_Iteration_I2.md)

```markdown
<!-- anchor: task-i2-t3 -->
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

### Context: Key Components Emphasizing Room Service (from .codemachine/artifacts/plan/01_Plan_Overview_and_Setup.md)

```markdown
<!-- anchor: key-components -->
*   **Key Components/Services:**
    *   **REST Controllers:** HTTP endpoints for user management, room CRUD, subscriptions, reporting
    *   **WebSocket Handlers:** Real-time connection managers for `/ws/room/{roomId}` endpoints
    *   **Domain Services:**
        *   User Service (registration, profile, preferences)
        *   Room Service (creation, configuration, join logic)
        *   Voting Service (vote casting, reveal, consensus calculation)
        *   Billing Service (subscription tier enforcement, Stripe integration)
        *   Reporting Service (session aggregation, analytics, export)
        *   Organization Service (SSO config, member management, admin controls)
    *   **Repository Layer:** Panache repositories for User, Room, Vote, Session, Subscription, Organization entities
    *   **Integration Adapters:** OAuth2 client, SSO adapter, Stripe adapter, Email adapter
    *   **Event Publisher/Subscriber:** Redis Pub/Sub client for WebSocket message broadcasting
    *   **Background Worker:** Async job processor for report generation, email dispatch
```

### Context: Component Diagram – Domain & Repository Responsibilities (from .codemachine/artifacts/architecture/03_System_Structure_and_Data.md)

```markdown
<!-- anchor: component-diagram -->
### 3.5. Component Diagram(s) (C4 Level 3 or UML)

This Component Diagram zooms into the **Quarkus Application** container to reveal its internal modular structure. The application follows a hexagonal (ports and adapters) architecture with clear separation between domain logic, infrastructure, and API layers.

**Key Modules:**
- **REST Controllers:** HTTP endpoint handlers exposing RESTful APIs for user management, room CRUD, subscriptions, and reporting
- **WebSocket Handlers:** Real-time connection managers processing vote events, room state changes, and participant actions
- **Domain Services:** Core business logic implementing estimation rules, room lifecycle, user preferences, billing logic
- **Repository Layer:** Data access abstractions using Hibernate Reactive Panache for PostgreSQL interactions
- **Integration Adapters:** External service clients (OAuth2, Stripe, email) following the adapter pattern
- **Event Publisher:** Redis Pub/Sub integration for broadcasting WebSocket messages across application nodes

PlantUML excerpt:
```
Component(room_service, "Room Service", "Domain Logic", "Room creation, join logic, deck configuration, privacy controls")
Component(room_repository, "Room Repository", "Panache Repository", "Room, RoomConfig, Vote entity persistence")
Rel(rest_controllers, room_service, "Invokes")
Rel(room_service, room_repository, "Persists via")
```
```

### Context: Core Gameplay Requirements – Room Controls (from .codemachine/artifacts/architecture/01_Context_and_Drivers.md)

```markdown
<!-- anchor: core-gameplay-requirements -->
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
*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** Fully implemented domain service using Mutiny `Uni`/`Multi`, Quarkus `@WithTransaction`/`@WithSession`, and JSON serialization via Jackson to persist `Room` instances with validation, feature gating for privacy modes, plus helper methods for config handling and nanoid generation.
    *   **Recommendation:** Reuse the existing helpers (e.g., `serializeConfig`, `generateNanoid`, `findById`) when extending behavior. The service already depends on `RoomRepository`, `FeatureGate`, and `ObjectMapper`; any new operations should follow the same validation + persistence flow and leverage Mutiny transformations rather than blocking code.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomConfig.java`
    *   **Summary:** Jackson-mapped POJO describing the JSONB config payload (deck type, timer controls, reveal behavior, observer flag) with defaults and getters/setters.
    *   **Recommendation:** When modifying config semantics, update this POJO and ensure `RoomService` serialization/deserialization covers new properties. Preserve the existing snake_case `@JsonProperty` names to align with stored JSON.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** Hibernate Reactive entity using String primary key (6-character nanoid) with relationships to `User`, `Organization`, `RoomParticipant`, and others. Includes soft-delete support via `deletedAt` and JSONB config stored as a raw string.
    *   **Recommendation:** Respect entity constraints when creating/updating rooms—especially `@Size` on `roomId` and `title`, enum mapping for `privacyMode`, and the expectation that `config` holds serialized JSON. Avoid bypassing these annotations by always using `RoomService` for modifications.
*   **File:** `backend/src/main/java/com/scrumpoker/repository/RoomRepository.java`
    *   **Summary:** Panache repository encapsulating reactive queries for owner/org-specific rooms, privacy filters, activity-based lookups, and counts.
    *   **Recommendation:** Use these methods inside higher-level features (listing rooms, analytics) instead of writing ad-hoc queries. Ensure new `RoomService` APIs compose results through Mutiny (e.g., `findActiveByOwnerId` -> `Multi` conversion) for consistency.

### Implementation Tips & Notes
*   **Tip:** `RoomService` already enforces subscription tier requirements through the injected `FeatureGate`; when adding new privacy options or business checks, route through the gate’s helper methods (`requireCanCreateInviteOnlyRoom`, `requireCanManageOrganization`) to keep billing logic centralized.
*   **Tip:** JSONB persistence is handled by `serializeConfig`/`deserializeConfig`; if a workflow needs structured access to config fields, convert to `RoomConfig` rather than manipulating the raw JSON string to avoid schema drift.
*   **Tip:** Mutiny error handling is used for validation—service methods return `Uni.createFrom().failure(...)` for invalid inputs. Follow that pattern so REST controllers can map exceptions uniformly via registered exception mappers.
*   **Tip:** The repository and service are reactive; avoid blocking operations (e.g., `Thread.sleep`, classic JDBC). Keep transformations within `Uni`/`Multi` pipelines and prefer method references or lambdas as shown.
*   **Note:** Because `Room` uses a human-friendly nanoid as its primary key, collision resistance matters. If you introduce batch room creation or import features, consider enhancing `generateNanoid` to check for existing IDs before persisting (there’s no guard now).
