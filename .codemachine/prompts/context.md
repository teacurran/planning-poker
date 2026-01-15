# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T4",
  "iteration_id": "I2",
  "iteration_goal": "Implement foundational domain services (Room Service, basic User Service), define REST API contracts (OpenAPI specification), and establish WebSocket protocol specification to enable frontend integration and parallel feature development.",
  "description": "Create `UserService` domain service for user profile operations: create user (from OAuth profile), update profile (display name, avatar URL), get user by ID, find by email, update user preferences (default deck type, theme, notification settings). Use `UserRepository` and `UserPreferenceRepository`. Implement reactive methods. Handle JSONB serialization for UserPreference.notification_settings and default_room_config. Validate email format, display name length constraints. Implement soft delete for user accounts (GDPR compliance).",
  "agent_type_hint": "BackendAgent",
  "inputs": "*   User and UserPreference entities from I1\n        *   User repositories from I1\n        *   User management requirements",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   UserService with methods: `createUser()`, `updateProfile()`, `getUserById()`, `findByEmail()`, `updatePreferences()`, `deleteUser()` (soft delete)\n        *   UserPreferenceConfig POJO for JSONB fields\n        *   Email validation using regex or Bean Validation\n        *   Display name length validation (max 100 chars)\n        *   Soft delete implementation (sets `deleted_at`, excludes from queries)",
  "acceptance_criteria": "*   Service methods pass unit tests with mocked repositories\n        *   User creation from OAuth profile maps fields correctly (oauth_provider, oauth_subject, email)\n        *   Preference updates persist JSONB fields correctly\n        *   Soft delete marks user as deleted without data loss\n        *   Email validation rejects invalid formats\n        *   Service methods return reactive types (Uni, Multi)",
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
#### User Account Requirements
- **OAuth2 Authentication:** Google and Microsoft social login integration
- **Profile Management:** Display name, avatar, theme preferences, default room settings
- **Session History:** Persistent storage of past sessions with tier-based access controls
- **Preference Persistence:** User-specific defaults for deck type, room rules, reveal behavior
```

### Context: Key Components (from .codemachine/artifacts/plan/01_Plan_Overview_and_Setup.md)

```markdown
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

    **Key Diagrams Planned:**
    *   Component Diagram (PlantUML) - Visualizes internal Quarkus application structure (Created in Architecture Blueprint reference)
    *   Sequence Diagram - Vote casting and reveal flow (Created in Architecture Blueprint reference)
```

### Context: Task 2.4 Implement User Service (from .codemachine/artifacts/plan/02_Iteration_I2.md)

```markdown
*   **Task 2.4: Implement User Service (Profile Management)**
    *   **Task ID:** `I2.T4`
    *   **Description:** Create `UserService` domain service for user profile operations: create user (from OAuth profile), update profile (display name, avatar URL), get user by ID, find by email, update user preferences (default deck type, theme, notification settings). Use `UserRepository` and `UserPreferenceRepository`. Implement reactive methods. Handle JSONB serialization for UserPreference.notification_settings and default_room_config. Validate email format, display name length constraints. Implement soft delete for user accounts (GDPR compliance).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   User and UserPreference entities from I1
        *   User repositories from I1
        *   User management requirements
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/user/User.java`
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java`
        *   `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/UserPreferenceRepository.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserPreferenceConfig.java` (POJO for JSONB)
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserNotFoundException.java`
    *   **Deliverables:**
        *   UserService with methods: `createUser()`, `updateProfile()`, `getUserById()`, `findByEmail()`, `updatePreferences()`, `deleteUser()` (soft delete)
        *   UserPreferenceConfig POJO for JSONB fields
        *   Email validation using regex or Bean Validation
        *   Display name length validation (max 100 chars)
        *   Soft delete implementation (sets `deleted_at`, excludes from queries)
    *   **Acceptance Criteria:**
        *   Service methods pass unit tests with mocked repositories
        *   User creation from OAuth profile maps fields correctly (oauth_provider, oauth_subject, email)
        *   Preference updates persist JSONB fields correctly
        *   Soft delete marks user as deleted without data loss
        *   Email validation rejects invalid formats
        *   Service methods return reactive types (Uni, Multi)
    *   **Dependencies:** [I1.T4, I1.T7]
    *   **Parallelizable:** Yes (can work parallel with I2.T3)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Summary:** Already implements the full user domain service with Mutiny `Uni` pipelines, validation helpers, profile updates, preference retrieval/upserts, OAuth provisioning, JSONB serialization helpers, and soft-delete handling through `deletedAt`.
    *   **Recommendation:** Follow the established `@WithTransaction`/`@WithSession` usage and reuse helper methods like `createDefaultPreferences`, `serializeConfig`, and `deserializeConfig` when extending behavior. Keep validations inside the reactive chain via `Uni.createFrom().failure(...)` so existing exception mappers remain effective.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserPreferenceConfig.java`
    *   **Summary:** Jackson-friendly POJO describing all room/preference JSONB fields plus static factories for default/empty configs.
    *   **Recommendation:** Any preference changes should update this class and rely on `UserService` serialization helpers to persist. Avoid duplicating JSON handling elsewhere—deserialize into this type when REST controllers need structured values.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** Hibernate Reactive entity for registered users with OAuth identifiers, subscription tier enum, timestamps, and `deletedAt` soft-delete flag plus relationships to preferences, rooms, org membership, etc.
    *   **Recommendation:** Honor the validation annotations (`@Email`, `@Size`) when mapping DTOs to this entity. All mutations should go through `UserService` to preserve invariants like `subscriptionTier` defaults and cascade creation of `UserPreference`.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java`
    *   **Summary:** One-to-one companion entity storing theme/default deck plus JSONB columns (`defaultRoomConfig`, `notificationSettings`) with timestamps that must be managed manually.
    *   **Recommendation:** Whenever you upsert preferences, ensure `UserPreference.user` remains set and timestamps update (the service currently sets `updatedAt = Instant.now()`). Use the repository helper to fetch/create records, then manipulate JSON via Jackson rather than string concatenation.

### Implementation Tips & Notes
*   **Tip:** `UserRepository` and `UserPreferenceRepository` already expose reactive finder helpers (`findActiveByEmail`, `findByOAuthProviderAndSubject`, `findByUserId`). Inject and reuse them instead of writing ad-hoc queries so you benefit from central soft-delete filtering.
*   **Tip:** Preference creation defaults to `UserPreferenceConfig.defaultConfig()`; rely on `createDefaultPreferences` whenever a user lacks a preference row so JSONB columns always contain valid JSON instead of `null`.
*   **Tip:** `UserNotFoundException` is thrown whenever the user is missing or soft-deleted; keep using it so REST layer maps to 404 consistently.
*   **Tip:** Email and display-name validation is centralized via helper methods and regex constants—use those rather than repeating regex logic elsewhere.
*   **Note:** Serialization failures currently wrap `JsonProcessingException` into `IllegalArgumentException`. If you add new preference types or DTOs, ensure you propagate structured errors so controllers can signal `400 Bad Request` without leaking Jackson stack traces.
