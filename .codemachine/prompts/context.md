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
  "dependencies": [
    "I1.T4",
    "I1.T7"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: api-style (from 04_Behavior_and_Communication.md)

```markdown
#### API Style

**Primary API Style:** **RESTful JSON API (OpenAPI 3.1 Specification)**

**Rationale:**
- **Simplicity & Familiarity:** REST over HTTPS provides a well-understood contract for CRUD operations on resources (users, rooms, subscriptions)
- **Tooling Ecosystem:** OpenAPI specification enables automatic client SDK generation (TypeScript for React frontend), API documentation (Swagger UI), and contract testing
- **Caching Support:** HTTP semantics (ETags, Cache-Control headers) enable browser and CDN caching for read-heavy endpoints (room configurations, user profiles)
- **Versioning Strategy:** URL-based versioning (`/api/v1/`) for backward compatibility during iterative releases
```

### Context: synchronous-rest-pattern (from 04_Behavior_and_Communication.md)

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
- `PUT /api/v1/users/{userId}/preferences` - Update user preferences
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

##### Key Points from Diagram:
- User initiates OAuth flow by clicking "Sign in with Google"
- SPA generates PKCE code_verifier & code_challenge
- Provider redirects back to callback with authorization code
- Backend exchanges code for access token and ID token
- Backend validates ID token signature and extracts claims (sub, email, name, picture)
- Backend calls UserService.findOrCreateUser() for JIT provisioning
- If user exists: retrieve from database
- If new user: INSERT into user table with oauth_provider, oauth_subject, email, display_name, avatar_url, subscription_tier='FREE'
- Create default UserPreference record for new users
- Generate JWT access token with user claims
- Generate refresh token (UUID), store in Redis with 30-day TTL
- Return tokens and user object to SPA
```

### Context: user-management-endpoints (from api/openapi.yaml)

```markdown
#### GET /api/v1/users/{userId}
Returns public profile information for a user. Users can view their own full profile or other users' public profiles.

**Parameters:**
- `userId` (path, required, UUID): User unique identifier

**Responses:**
- `200 OK`: Returns UserDTO
- `401 Unauthorized`: Missing or invalid authentication
- `404 Not Found`: User not found or deleted

#### PUT /api/v1/users/{userId}
Updates display name and avatar URL. Users can only update their own profile.

**Request Body** (UpdateUserRequest):
- `displayName` (optional, string, max 100 chars): Updated display name
- `avatarUrl` (optional, string, URI, max 500 chars): Updated avatar URL

**Responses:**
- `200 OK`: Returns updated UserDTO
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User attempting to update another user's profile
- `404 Not Found`: User not found

#### GET /api/v1/users/{userId}/preferences
Returns saved user preferences including default room settings, theme, and notification preferences.

**Responses:**
- `200 OK`: Returns UserPreferenceDTO
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User accessing another user's preferences

#### PUT /api/v1/users/{userId}/preferences
Updates user preferences for default room configuration, theme, and notifications.

**Request Body** (UpdateUserPreferenceRequest):
- `defaultDeckType` (optional, enum): fibonacci | tshirt | powers_of_2 | custom
- `defaultRoomConfig` (optional, RoomConfigDTO): Default room configuration
- `theme` (optional, enum): light | dark | system
- `notificationSettings` (optional, object): Email and reminder preferences

**Responses:**
- `200 OK`: Returns updated UserPreferenceDTO
- `400 Bad Request`: Invalid request parameters
- `401 Unauthorized`: Missing or invalid JWT
- `403 Forbidden`: User updating another user's preferences
```

### Context: user-dto-schemas (from api/openapi.yaml)

```markdown
#### UserDTO
```yaml
type: object
required: [userId, email, displayName, subscriptionTier, createdAt]
properties:
  userId: UUID
  email: string (format: email, max 255 chars, unique)
  oauthProvider: enum (google, microsoft)
  displayName: string (max 100 chars)
  avatarUrl: string (URI, max 500 chars, nullable)
  subscriptionTier: enum (FREE, PRO, PRO_PLUS, ENTERPRISE)
  createdAt: date-time
  updatedAt: date-time
```

#### UserPreferenceDTO
```yaml
type: object
required: [userId]
properties:
  userId: UUID
  defaultDeckType: enum (fibonacci, tshirt, powers_of_2, custom)
  defaultRoomConfig: RoomConfigDTO (JSONB object)
  theme: enum (light, dark, system)
  notificationSettings: object (JSONB with emailNotifications, sessionReminders booleans)
```
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### IMPORTANT: TASK ALREADY COMPLETE

**File:** `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
  * **Summary:** The UserService is ALREADY FULLY IMPLEMENTED with all required functionality. The file contains 335 lines of production code implementing all deliverables specified in the task.
  * **CRITICAL FINDING:** This task (I2.T4) has been completed but the task manifest still shows `"done": false`. The implementation includes:
    - `createUser()` method (lines 51-93)
    - `updateProfile()` method (lines 134-157)
    - `getUserById()` method (lines 166-176)
    - `findByEmail()` method (lines 184-190)
    - `findOrCreateUser()` method (lines 203-228) for OAuth JIT provisioning
    - `updatePreferences()` method (lines 238-267)
    - `deleteUser()` method (lines 277-290) with soft delete implementation
    - Email validation using regex pattern (lines 25-26, 297-299)
    - Display name validation with 100 char limit (lines 28, 304-307)
    - JSONB serialization helpers (lines 313-333)
    - All methods use reactive return types (Uni)
    - Proper @WithTransaction and @WithSession annotations

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** JPA entity class for User with Panache, includes fields: userId (UUID), oauthProvider, oauthSubject, email, displayName, avatarUrl, subscriptionTier, createdAt, updatedAt, deletedAt
    *   **Recommendation:** The UserService MUST use this entity as-is. All field mappings from OAuth profile are already implemented correctly in the existing UserService.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java`
    *   **Summary:** JPA entity for user preferences with JSONB fields: defaultRoomConfig, notificationSettings
    *   **Recommendation:** The UserService already handles JSONB serialization/deserialization correctly using Jackson ObjectMapper.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
    *   **Summary:** Panache repository with custom methods: findByEmail(), findByOAuthProviderAndSubject(), findActiveByEmail(), countActive()
    *   **Recommendation:** The existing UserService correctly uses all these repository methods via @Inject.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/UserPreferenceRepository.java`
    *   **Summary:** Panache repository for UserPreference entity
    *   **Recommendation:** The existing UserService correctly injects and uses this repository for preference operations.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserPreferenceConfig.java`
    *   **Summary:** This file already exists and is imported by UserService (line 4 shows import)
    *   **Recommendation:** Verify this file contains proper POJO structure matching JSONB requirements. Based on the service code, it should have fields: deckType, and static methods defaultConfig() and empty().

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserNotFoundException.java`
    *   **Summary:** This custom exception class is already being used in UserService (lines 169, 172, 249)
    *   **Recommendation:** Verify this exception class exists and extends RuntimeException or appropriate base exception.

### Implementation Tips & Notes

*   **CRITICAL ACTION REQUIRED:** This task is ALREADY COMPLETE. You should:
    1. **Verify helper classes exist:**
       - Check that `UserPreferenceConfig.java` exists with proper structure
       - Check that `UserNotFoundException.java` exists and is properly defined
    2. **Update task status:** Mark this task as `"done": true` in the appropriate task tracking system
    3. **Move to next task:** Proceed to Task I2.T5 (REST Controllers for Room Management) which depends on I2.T1 (OpenAPI spec - done) and I2.T3 (RoomService - done)

*   **Note:** The existing UserService implementation follows all Quarkus reactive patterns correctly:
    - Uses Uni<> return types for single results
    - Uses @WithTransaction for write operations (persist, update, delete)
    - Uses @WithSession for read operations (findById, findByEmail)
    - Properly chains reactive operations with flatMap and onItem
    - Includes comprehensive error handling and validation
    - Implements JIT (Just-In-Time) user provisioning for OAuth flow

*   **Warning:** Do NOT rewrite this service. It is production-ready code that meets all acceptance criteria:
    ✓ Service methods use reactive types (Uni)
    ✓ User creation from OAuth correctly maps all fields
    ✓ Email validation uses regex pattern
    ✓ Display name validation enforces 100 char max
    ✓ Preferences update persists JSONB fields correctly
    ✓ Soft delete sets deletedAt timestamp
    ✓ Soft deleted users excluded from queries

*   **Next Steps:** After verifying the two helper classes (UserPreferenceConfig and UserNotFoundException), update the task status to complete and inform the user that I2.T4 is already done. Then proceed to I2.T5 which is the actual next task that needs implementation.
