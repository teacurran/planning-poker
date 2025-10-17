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
  "inputs": "User and UserPreference entities from I1, User repositories from I1, User management requirements",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/user/User.java",
    "backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java",
    "backend/src/main/java/com/scrumpoker/repository/UserRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/UserPreferenceRepository.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/domain/user/UserService.java",
    "backend/src/main/java/com/scrumpoker/domain/user/UserPreferenceConfig.java",
    "backend/src/main/java/com/scrumpoker/domain/user/UserNotFoundException.java"
  ],
  "deliverables": "UserService with methods: `createUser()`, `updateProfile()`, `getUserById()`, `findByEmail()`, `updatePreferences()`, `deleteUser()` (soft delete), UserPreferenceConfig POJO for JSONB fields, Email validation using regex or Bean Validation, Display name length validation (max 100 chars), Soft delete implementation (sets `deleted_at`, excludes from queries)",
  "acceptance_criteria": "Service methods pass unit tests with mocked repositories, User creation from OAuth profile maps fields correctly (oauth_provider, oauth_subject, email), Preference updates persist JSONB fields correctly, Soft delete marks user as deleted without data loss, Email validation rejects invalid formats, Service methods return reactive types (Uni, Multi)",
  "dependencies": ["I1.T4", "I1.T7"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: data-model-overview-erd (from 03_System_Structure_and_Data.md)

```markdown
### 3.6. Data Model Overview & ERD

#### Description

The data model follows a relational schema leveraging PostgreSQL's ACID properties for transactional consistency and JSONB columns for flexible configuration storage (room settings, deck definitions). The model is optimized for both transactional writes (vote casting, room creation) and analytical reads (session history, organizational reporting).

**Design Principles:**
1. **Normalized Core Entities:** Users, Rooms, Organizations follow 3NF to prevent update anomalies
2. **Denormalized Read Models:** SessionSummary and VoteStatistics tables precompute aggregations for reporting performance
3. **JSONB for Flexibility:** RoomConfig, DeckDefinition, UserPreferences stored as JSONB to support customization without schema migrations
4. **Soft Deletes:** Critical entities (Users, Rooms) use `deleted_at` timestamp for audit trail and GDPR compliance
5. **Partitioning Strategy:** SessionHistory and AuditLog partitioned by month for query performance and data lifecycle management

#### Key Entities

| Entity | Purpose | Key Attributes |
|--------|---------|----------------|
| **User** | Registered user account | `user_id` (PK), `email`, `oauth_provider`, `oauth_subject`, `display_name`, `avatar_url`, `subscription_tier`, `created_at` |
| **UserPreference** | Saved user defaults | `user_id` (FK), `default_deck_type`, `default_room_config` (JSONB), `theme`, `notification_settings` (JSONB) |
```

### Context: component-diagram (from 03_System_Structure_and_Data.md)

```markdown
### 3.5. Component Diagram(s) (C4 Level 3 or UML)

#### Description

This Component Diagram zooms into the **Quarkus Application** container to reveal its internal modular structure. The application follows a hexagonal (ports and adapters) architecture with clear separation between domain logic, infrastructure, and API layers.

**Key Modules:**
- **REST Controllers:** HTTP endpoint handlers exposing RESTful APIs for user management, room CRUD, subscriptions, and reporting
- **WebSocket Handlers:** Real-time connection managers processing vote events, room state changes, and participant actions
- **Domain Services:** Core business logic implementing estimation rules, room lifecycle, user preferences, billing logic
- **Repository Layer:** Data access abstractions using Hibernate Reactive Panache for PostgreSQL interactions
- **Integration Adapters:** External service clients (OAuth2, Stripe, email) following the adapter pattern
- **Event Publisher:** Redis Pub/Sub integration for broadcasting WebSocket messages across application nodes

**Service Responsibilities:**
- **User Service:** User registration, profile management, preference storage
- **Room Service:** Room creation, join logic, deck configuration, privacy controls
- **Voting Service:** Vote casting, reveal logic, consensus calculation, round lifecycle
```

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

### Context: key-interaction-flow-oauth-login (from 04_Behavior_and_Communication.md)

```markdown
#### Key Interaction Flow: OAuth2 Authentication (Google/Microsoft)

##### Description

This sequence demonstrates the OAuth2 authorization code flow for user authentication via Google or Microsoft identity providers, JWT token generation, and session establishment.

**Flow highlights:**
1. User clicks "Sign in with Google/Microsoft"
2. SPA redirects to OAuth2 provider with PKCE code_challenge
3. User grants permission, provider redirects with authorization code
4. Backend exchanges code for access_token and id_token
5. Backend validates id_token signature, extracts user info (sub, email, name, picture)
6. Backend calls UserService.findOrCreateUser() to provision user
7. If new user, creates User entity with oauth_provider='google', oauth_subject=sub, subscription_tier='FREE'
8. If new user, creates default UserPreference record (theme='light', default_deck_type='fibonacci')
9. Backend generates JWT access token and refresh token
10. SPA stores tokens and redirects to dashboard

**User JIT Provisioning Logic:**
```sql
SELECT * FROM user WHERE oauth_provider='google' AND oauth_subject='...'
-- If NULL:
INSERT INTO user (oauth_provider, oauth_subject, email, display_name, avatar_url, subscription_tier)
VALUES ('google', '...', '...', '...', '...', 'FREE')
INSERT INTO user_preference (user_id, default_deck_type, theme) VALUES (...)
```
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### File: `backend/src/main/java/com/scrumpoker/domain/user/User.java`
*   **Summary:** This is the JPA entity for registered users with OAuth authentication. It uses Hibernate Reactive Panache for persistence, has UUID primary key auto-generated, includes `deleted_at` for soft deletes, and uses `@Cacheable` for second-level caching.
*   **Recommendation:** You MUST import and reference this User class in your UserService. Note that all field properties are **public** (Panache pattern), not private with getters/setters.
*   **Critical Pattern:** The entity uses `@CreationTimestamp` and `@UpdateTimestamp` from Hibernate, so you do NOT need to manually set `createdAt` or `updatedAt` - Hibernate handles these automatically.
*   **JSONB Fields:** None on User entity - all JSONB storage is on UserPreference entity.

#### File: `backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java`
*   **Summary:** This entity has a 1:1 relationship with User, using `@MapsId` to share the same primary key. It contains JSONB columns for flexible configuration: `default_room_config` and `notification_settings`.
*   **Recommendation:** You MUST create UserPreference records when creating new users. The JSONB columns are stored as JSON **strings**, not objects - your service will need to serialize/deserialize.
*   **Critical Pattern:** The `user` field is a `@OneToOne` relationship with `@MapsId`, meaning `userId` is both FK and PK. When creating UserPreference, you MUST set both `userId` and `user` fields.
*   **Default Values:** `theme` defaults to "light" at database level. Your service should set sensible defaults for JSONB fields (e.g., empty JSON object `"{}"` or proper default config).

#### File: `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
*   **Summary:** Reactive Panache repository with custom finder methods: `findByEmail()`, `findByOAuthProviderAndSubject()`, `findActiveByEmail()`, `countActive()`.
*   **Recommendation:** You SHOULD use `findByOAuthProviderAndSubject()` for OAuth login flow. You SHOULD use `findActiveByEmail()` when you need to exclude soft-deleted users.
*   **Pattern:** All methods return `Uni<>` (single result) or `Multi<>` (stream). For nullable results, the Uni will contain null - check with `.onItem().ifNull()`.
*   **Soft Delete Queries:** Notice how `findActiveByEmail()` filters `deletedAt is null` - your service should follow this pattern when querying non-deleted users.

#### File: `backend/src/main/java/com/scrumpoker/repository/UserPreferenceRepository.java`
*   **Summary:** Simple repository with `findByUserId()` (which just delegates to `findById()` since userId is the PK).
*   **Recommendation:** You SHOULD use this repository to persist and retrieve UserPreference entities.
*   **Pattern:** Since UserPreference shares PK with User, you can `findById(userId)` directly.

#### File: `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
*   **Summary:** This is THE reference implementation for domain services in this project. It demonstrates all key patterns you MUST follow.
*   **Critical Patterns to Follow:**
    1. **Service Annotations:** Use `@ApplicationScoped` for CDI bean, use `@Inject` for repository dependencies
    2. **Transaction Management:** Use `@WithTransaction` on methods that modify data, `@WithSession` on read-only methods
    3. **Reactive Patterns:** Return `Uni<>` for single results, `Multi<>` for streams. Chain operations with `.flatMap()` for async composition
    4. **Validation:** Validate inputs early, return `Uni.createFrom().failure(new IllegalArgumentException(...))` for validation errors
    5. **JSONB Handling:** Use injected `ObjectMapper` to serialize/deserialize JSONB columns (`objectMapper.writeValueAsString()` and `objectMapper.readValue()`)
    6. **Custom Exceptions:** Throw domain exceptions (e.g., `UserNotFoundException`) for business errors, following the pattern from `RoomNotFoundException`
    7. **Soft Deletes:** Set `deletedAt = Instant.now()` and persist, don't call `repository.delete()`
*   **Recommendation:** You MUST follow the exact same patterns as RoomService - use it as your template. Pay special attention to:
    - How `createRoom()` uses `@WithTransaction` and returns `roomRepository.persist(room)`
    - How `deleteRoom()` sets `deletedAt` instead of deleting
    - How `findById()` checks `if (room.deletedAt != null)` and throws exception
    - How JSONB config is serialized/deserialized with try-catch wrapping JsonProcessingException

#### File: `backend/src/main/java/com/scrumpoker/domain/room/RoomNotFoundException.java`
*   **Summary:** Custom RuntimeException with roomId field and two constructors (with and without cause).
*   **Recommendation:** You MUST create `UserNotFoundException` following this **exact same pattern**. Replace "Room" with "User" and "roomId" with "userId" (UUID type).
*   **Pattern:** Extends `RuntimeException`, has single field for entity ID, two constructors, getter for ID.

#### File: `backend/pom.xml`
*   **Summary:** Quarkus 3.15.1 project with Jackson already configured via `quarkus-rest-jackson` dependency.
*   **Recommendation:** You DO NOT need to add any new dependencies. ObjectMapper is already available for injection. Bean Validation (`@Email`, `@Size`) is available via `quarkus-hibernate-validator`.
*   **Pattern:** The project uses Quarkus conventions - no need for manual configuration classes.

#### File: `backend/src/test/java/com/scrumpoker/repository/RoundRepositoryTest.java`
*   **Summary:** Integration test using `@QuarkusTest`, `@RunOnVertxContext`, and `UniAsserter` for reactive testing. Uses AssertJ for assertions.
*   **Critical Testing Patterns:**
    1. **Test Class:** Annotate with `@QuarkusTest` (no Mockito for repositories)
    2. **Test Methods:** Annotate with `@Test` and `@RunOnVertxContext`, add `UniAsserter asserter` parameter
    3. **Setup:** Use `@BeforeEach` to clean database: `Panache.withTransaction(() -> repository.deleteAll())`
    4. **Persistence:** Wrap in `Panache.withTransaction(() -> ...)`
    5. **Assertions:** Use `asserter.assertThat(() -> Panache.withTransaction(() -> ...), result -> { assertThat(result)... })`
    6. **Test Data:** Create helper methods like `createTestUser()` that do NOT set auto-generated IDs (userId, roundId, etc.)
    7. **Chaining:** Use reactive `.flatMap()` to chain persist operations in correct order
*   **Recommendation:** Since your task requires unit tests with **mocked repositories** (not integration tests like this), you will NOT follow this pattern exactly. However, you SHOULD follow the AssertJ assertion style and test organization.

### Implementation Tips & Notes

#### Tip 1: Service Structure
Your `UserService` should mirror `RoomService` structure exactly:
- `@ApplicationScoped` class
- `@Inject` for repositories and ObjectMapper
- Private helper methods for JSONB serialization/deserialization
- Public methods with `@WithTransaction` or `@WithSession` annotations
- All public methods return reactive types (`Uni<>` or `Multi<>`)

#### Tip 2: Email Validation
The task requires email format validation. You have two options:
1. **Bean Validation:** Use `@Email` annotation from Jakarta Validation (already available). Import from `jakarta.validation.constraints.Email`.
2. **Manual Regex:** Use a pattern like `^[A-Za-z0-9+_.-]+@(.+)$` and check with `Pattern.matches()`.
**Recommendation:** Use Bean Validation since it's already configured and the User entity already has `@Email` annotation on the field.

#### Tip 3: Display Name Validation
The task requires max 100 chars validation. The User entity already has `@Size(max = 100)` on displayName field, so database-level validation is covered. Your service should add runtime validation:
```java
if (displayName == null || displayName.trim().isEmpty()) {
    return Uni.createFrom().failure(new IllegalArgumentException("Display name cannot be null or empty"));
}
if (displayName.length() > 100) {
    return Uni.createFrom().failure(new IllegalArgumentException("Display name cannot exceed 100 characters"));
}
```

#### Tip 4: JSONB Field Handling
UserPreference has two JSONB fields: `default_room_config` and `notification_settings`. You MUST:
1. Create a `UserPreferenceConfig` POJO class with fields matching the JSON structure
2. Add Jackson serialization/deserialization methods in UserService (copy pattern from RoomService)
3. Store JSON strings in the entity fields (not Java objects)
4. Provide sensible defaults (e.g., `"{}"` or a properly initialized config object)

**Example structure for UserPreferenceConfig:**
```java
public class UserPreferenceConfig {
    public String deckType;
    public Boolean timerEnabled;
    public Integer timerDurationSeconds;
    public String revealBehavior;
    // Constructor, toString, etc.
}
```

#### Tip 5: Soft Delete Implementation
For `deleteUser()` method:
1. Use `@WithTransaction` annotation
2. Find user by ID (handle not found)
3. Check if already deleted (if `deletedAt != null`, throw exception or return)
4. Set `user.deletedAt = Instant.now()`
5. Persist and return: `return userRepository.persist(user)`
6. When querying users in other methods, filter out soft-deleted users: `find("deletedAt is null")`

#### Tip 6: User Creation from OAuth Profile
The `createUser()` method should:
1. Take parameters: `oauthProvider`, `oauthSubject`, `email`, `displayName`, `avatarUrl`
2. Create User entity, set all fields including `subscriptionTier = SubscriptionTier.FREE`
3. Do NOT set `userId` (auto-generated), `createdAt`, or `updatedAt` (auto-timestamps)
4. Persist User first
5. Create UserPreference entity with `userId = user.userId` and `user = user`
6. Set default JSONB values for `default_room_config` and `notification_settings`
7. Persist UserPreference
8. Return the User in a Uni

**Critical:** You must use `.flatMap()` to chain the operations:
```java
return userRepository.persist(user)
    .flatMap(savedUser -> {
        UserPreference pref = new UserPreference();
        pref.userId = savedUser.userId;
        pref.user = savedUser;
        pref.defaultRoomConfig = "{}"; // or serialize default config
        pref.notificationSettings = "{}";
        return userPreferenceRepository.persist(pref)
            .replaceWith(savedUser); // Return user, not preference
    });
```

#### Tip 7: Update Methods
For `updateProfile()` and `updatePreferences()`:
1. Both should use `@WithTransaction`
2. Fetch entity first with `.flatMap()`
3. Update fields
4. Persist and return
5. For preferences, deserialize JSONB, update Java object, serialize back to JSON string

#### Warning: Reactive Chain Gotchas
Common mistakes with reactive Mutiny code:
- **DON'T** call `.await().indefinitely()` - keep everything reactive with `.flatMap()` chains
- **DON'T** use blocking operations inside reactive chains
- **DO** use `.replaceWith()` to return a different value after an operation
- **DO** use `.onItem().transform()` for simple transformations
- **DO** use `.onItem().ifNull().failWith()` to handle null results with custom exceptions

#### Note: Method Signatures
Based on the task deliverables, you need these exact methods:
1. `createUser(String oauthProvider, String oauthSubject, String email, String displayName, String avatarUrl)` → `Uni<User>`
2. `updateProfile(UUID userId, String displayName, String avatarUrl)` → `Uni<User>`
3. `getUserById(UUID userId)` → `Uni<User>` (throws UserNotFoundException if not found or deleted)
4. `findByEmail(String email)` → `Uni<User>` (returns null if not found)
5. `updatePreferences(UUID userId, UserPreferenceConfig config)` → `Uni<UserPreference>`
6. `deleteUser(UUID userId)` → `Uni<User>` (soft delete)

Additionally, you may want helper methods like:
- `findOrCreateUser(...)` for OAuth flow (combines findByOAuthProviderAndSubject + createUser)
- Private JSONB serialization/deserialization helpers

#### Note: File Organization
You must create these three files in the exact paths specified in target_files:
1. `backend/src/main/java/com/scrumpoker/domain/user/UserService.java` - main service class
2. `backend/src/main/java/com/scrumpoker/domain/user/UserPreferenceConfig.java` - POJO for JSONB
3. `backend/src/main/java/com/scrumpoker/domain/user/UserNotFoundException.java` - custom exception

All files should be in the `com.scrumpoker.domain.user` package.

---

## Final Checklist for Implementation

Before you complete this task, verify:

- [ ] UserService class created with `@ApplicationScoped` annotation
- [ ] All six main methods implemented with correct signatures and return types
- [ ] All methods use `@WithTransaction` or `@WithSession` appropriately
- [ ] UserRepository and UserPreferenceRepository injected with `@Inject`
- [ ] ObjectMapper injected for JSONB serialization/deserialization
- [ ] UserPreferenceConfig POJO created with sensible structure
- [ ] UserNotFoundException created following RoomNotFoundException pattern
- [ ] Email validation implemented (Bean Validation or manual)
- [ ] Display name length validation implemented (max 100 chars)
- [ ] Soft delete implementation (sets `deletedAt`, doesn't physically delete)
- [ ] JSONB fields handled correctly (serialize to/from JSON strings)
- [ ] User creation creates both User and UserPreference entities
- [ ] Default values set for JSONB fields (empty objects or sensible defaults)
- [ ] Reactive chains use `.flatMap()` for async composition, no blocking calls
- [ ] All methods return `Uni<>` (no `Multi<>` needed for this service)
- [ ] Code follows exact patterns from RoomService (transaction handling, validation, error handling)
- [ ] Import statements correct (Jakarta, Quarkus, Mutiny, Jackson)
- [ ] No compilation errors, all required classes imported
