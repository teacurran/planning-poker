# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T7",
  "iteration_id": "I2",
  "iteration_goal": "Implement foundational domain services (Room Service, basic User Service), define REST API contracts (OpenAPI specification), and establish WebSocket protocol specification to enable frontend integration and parallel feature development.",
  "description": "Create comprehensive unit tests for `RoomService` and `UserService` using JUnit 5 and Mockito. Mock repository dependencies. Test business logic: room creation with unique ID generation, config validation, soft delete behavior, user profile updates, preference persistence. Test exception scenarios (e.g., room not found, invalid email format). Use AssertJ for fluent assertions. Aim for >90% code coverage on service classes.",
  "agent_type_hint": "BackendAgent",
  "inputs": "RoomService and UserService from I2.T3, I2.T4, JUnit 5 and Mockito testing patterns",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/room/RoomService.java",
    "backend/src/main/java/com/scrumpoker/domain/user/UserService.java"
  ],
  "target_files": [
    "backend/src/test/java/com/scrumpoker/domain/room/RoomServiceTest.java",
    "backend/src/test/java/com/scrumpoker/domain/user/UserServiceTest.java"
  ],
  "deliverables": "RoomServiceTest with 10+ test methods covering create, update, delete, find operations, UserServiceTest with 10+ test methods covering profile, preferences, soft delete, Mocked repository interactions using Mockito, Exception scenario tests (assertThrows for custom exceptions), AssertJ assertions for fluent readability",
  "acceptance_criteria": "`mvn test` runs all unit tests successfully, Test coverage >90% for RoomService and UserService, All business validation scenarios tested (invalid input → exception), Happy path tests verify correct repository method calls, Exception tests verify custom exceptions thrown with correct messages",
  "dependencies": [
    "I2.T3",
    "I2.T4"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: api-design-and-communication (from 04_Behavior_and_Communication.md)

```markdown
### 3.7. API Design & Communication

#### API Style

**Primary API Style:** **RESTful JSON API (OpenAPI 3.1 Specification)**

**Rationale:**
- **Simplicity & Familiarity:** REST over HTTPS provides a well-understood contract for CRUD operations on resources (users, rooms, subscriptions)
- **Tooling Ecosystem:** OpenAPI specification enables automatic client SDK generation (TypeScript for React frontend), API documentation (Swagger UI), and contract testing
- **Caching Support:** HTTP semantics (ETags, Cache-Control headers) enable browser and CDN caching for read-heavy endpoints (room configurations, user profiles)
- **Versioning Strategy:** URL-based versioning (`/api/v1/`) for backward compatibility during iterative releases

**WebSocket Protocol:** **Custom JSON-RPC Style Over WebSocket**

**Rationale:**
- **Real-Time Bidirectional Communication:** WebSocket connections maintained for duration of estimation session, enabling sub-100ms latency for vote events and reveals
- **Message Format:** JSON envelopes with `type`, `requestId`, and `payload` fields for request/response correlation
- **Versioned Message Types:** Each message type (e.g., `vote.cast.v1`, `room.reveal.v1`) versioned independently for protocol evolution
- **Fallback Strategy:** Graceful degradation to HTTP long-polling for environments with WebSocket restrictions (corporate proxies)
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
- `POST /api/v1/rooms` - Create new estimation room
- `GET /api/v1/rooms/{roomId}` - Retrieve room configuration
- `PUT /api/v1/users/{userId}/preferences` - Update user preferences
- `POST /api/v1/subscriptions/{subscriptionId}/upgrade` - Upgrade subscription tier
- `GET /api/v1/reports/sessions?from=2025-01-01&to=2025-01-31` - Query session history
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** This file implements the complete RoomService domain service with 11 public methods for room CRUD operations. It uses reactive Uni/Multi return types, Mockito-injectable @Inject dependencies (RoomRepository, ObjectMapper), and comprehensive input validation. The service includes nanoid generation (6 chars, a-z0-9), JSONB serialization/deserialization, and soft delete functionality.
    *   **Recommendation:** You MUST import and mock the RoomRepository and ObjectMapper dependencies in your tests. Focus on testing all 11 public methods, particularly the edge cases around validation (null/empty/too-long titles max 255 chars, null privacy modes), JSONB serialization failures, and soft delete behavior.
    *   **Key Methods:** createRoom(), updateRoomConfig(), updateRoomTitle(), updatePrivacyMode(), deleteRoom(), findById(), findByOwnerId(), getRoomConfig()
    *   **Validation Rules:** title max 255 chars, privacy mode required, config JSONB serialization

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Summary:** This file implements the UserService with 9 public methods handling OAuth user provisioning, profile updates, preference management, and soft deletes. It uses regex-based email validation (EMAIL_PATTERN: `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$`), display name length validation (MAX_DISPLAY_NAME_LENGTH = 100), and JSONB config serialization. The service includes sophisticated logic like findOrCreateUser for JIT provisioning.
    *   **Recommendation:** You MUST import and mock UserRepository, UserPreferenceRepository, and ObjectMapper. Test all validation patterns (email regex, display name length), the findOrCreateUser flow (both existing user and new user paths), preference JSONB serialization, and soft delete behavior with deletedAt timestamps.
    *   **Key Methods:** createUser(), updateProfile(), getUserById(), findByEmail(), findOrCreateUser(), getPreferences(), updatePreferences(), deleteUser(), createDefaultPreferences() (private)
    *   **Validation Rules:** email regex pattern, displayName max 100 chars, OAuth provider/subject required

*   **File:** `backend/src/test/java/com/scrumpoker/domain/room/RoomServiceTest.java`
    *   **Summary:** This is an EXCELLENT reference test file demonstrating the exact testing patterns you should follow. It uses @ExtendWith(MockitoExtension.class), @Mock/@InjectMocks annotations, comprehensive test coverage with 40+ test methods organized into logical sections (Create Room, Update Config, Update Title, etc.), AssertJ assertions, and proper Mockito verification patterns.
    *   **Recommendation:** You MUST follow this exact pattern for UserServiceTest. Key patterns to replicate: (1) Use @BeforeEach setUp() to initialize test data, (2) Organize tests into sections with comments like `// ===== Create User Tests =====`, (3) Name tests descriptively: `testMethodName_Scenario_ExpectedOutcome()`, (4) Use `.await().indefinitely()` to unwrap Uni reactive types, (5) Verify repository method calls with `verify()`, (6) Use `assertThatThrownBy()` for exception tests.
    *   **Pattern Example:** See lines 31-50 for setUp(), lines 61-82 for createRoom happy path test, lines 105-115 for null validation test with assertThatThrownBy()

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java` (entity class)
    *   **Summary:** Entity class defining User fields including userId (UUID), email, displayName, oauthProvider, oauthSubject, subscriptionTier (enum), deletedAt (Instant), createdAt, updatedAt.
    *   **Recommendation:** You SHOULD create helper methods in your test class to instantiate User entities with test data, similar to the `createTestRoom()` helper in RoomServiceTest at line 650.

### Implementation Tips & Notes

*   **Tip:** I confirmed that RoomServiceTest already has comprehensive coverage (40+ test methods covering all public methods). Your UserServiceTest MUST achieve similar comprehensiveness. Target at least 15-20 test methods to cover all UserService methods and their edge cases.

*   **Tip:** The project uses reactive Mutiny types (Uni, Multi). All service methods return Uni<> or Multi<>. In tests, you MUST call `.await().indefinitely()` to unwrap the reactive result for synchronous assertion. Example: `User result = userService.getUserById(userId).await().indefinitely();`

*   **Tip:** When mocking repository persist methods, use the pattern from RoomServiceTest line 89-92: `when(repository.persist(any(Entity.class))).thenAnswer(invocation -> { Entity entity = invocation.getArgument(0); return Uni.createFrom().item(entity); });` This returns the exact entity that was passed in, allowing you to verify field values.

*   **Tip:** For testing JSONB serialization failures, throw JsonProcessingException from mocked ObjectMapper methods: `when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("error") {});` This is demonstrated in RoomServiceTest line 215.

*   **Warning:** The UserService has a complex `findOrCreateUser` method (lines 243-271 in UserService.java) that conditionally creates a new user OR updates an existing one. You MUST test BOTH paths: (1) User exists → updates profile fields (lines 251-264), (2) User doesn't exist → creates new user with default preferences (lines 266-269). Mock `userRepository.findByOAuthProviderAndSubject()` to return `Uni.createFrom().item(existingUser)` for path 1, and `Uni.createFrom().nullItem()` for path 2.

*   **Warning:** The `createDefaultPreferences` private method in UserService (lines 127-155) creates a UserPreference entity with default JSONB fields. When testing user creation, you MUST mock `userPreferenceRepository.persist()` to handle the chained preference creation. Use: `when(userPreferenceRepository.persist(any())).thenAnswer(invocation -> Uni.createFrom().item(invocation.getArgument(0)));`

*   **Critical Pattern:** Soft delete tests MUST verify that the `deletedAt` timestamp is set correctly. Use: `assertThat(result.deletedAt).isNotNull();` and verify that subsequent calls to find methods throw UserNotFoundException when the entity is soft-deleted. See RoomServiceTest lines 432-451 for deleteRoom soft delete test pattern.

*   **Coverage Target:** The acceptance criteria requires >90% coverage for RoomService and UserService. Given that RoomServiceTest already exists with excellent coverage, you only need to complete UserServiceTest. Ensure every public method has at least 2-3 test cases (happy path + edge cases).

### Testing Checklist for UserServiceTest

Based on the service methods, you MUST include tests for:

1. **createUser() (lines 72-119)**: Valid input returns user, null/empty oauthProvider throws exception, null/empty oauthSubject throws exception, invalid email format throws exception, invalid displayName (null/empty/too long >100 chars) throws exception, JSONB serialization success, default preferences created and persisted
2. **updateProfile() (lines 168-195)**: Valid update (displayName + avatarUrl), null displayName (should not update field), invalid displayName (too long >100 chars) throws exception, user not found throws UserNotFoundException, soft-deleted user throws UserNotFoundException
3. **getUserById() (lines 204-215)**: Existing user returns correctly, non-existent user (null from repo) throws UserNotFoundException, soft-deleted user (deletedAt not null) throws UserNotFoundException
4. **findByEmail() (lines 224-230)**: Valid email returns user, null/empty email returns null, non-existent email returns null
5. **findOrCreateUser() (lines 243-271)**: Existing user path updates profile fields (email, displayName, avatarUrl), new user path creates user + preferences, profile fields updated correctly on existing user
6. **getPreferences() (lines 280-288)**: Existing preferences returns correctly, user not found throws UserNotFoundException, preferences not found throws UserNotFoundException
7. **updatePreferences() (lines 299-337)**: Valid update with JSONB serialization, null config throws IllegalArgumentException, user not found throws UserNotFoundException, JSONB serialization failure throws exception
8. **deleteUser() (lines 347-362)**: Valid user soft delete sets deletedAt, user not found throws UserNotFoundException, already deleted user throws IllegalArgumentException
9. **Helper methods**: isValidEmail() tested via createUser validation failures, isValidDisplayName() tested via validation failures

### Code Style Requirements

*   Use Mockito's `@ExtendWith(MockitoExtension.class)` at class level
*   Use `@Mock` for repository dependencies, `@InjectMocks` for the service under test
*   Group tests logically with comment separators: `// ===== Method Name Tests =====`
*   Name tests: `testMethodName_Scenario_ExpectedOutcome()`
*   Use AssertJ's `assertThat()` for fluent assertions (never use JUnit's assertEquals)
*   Use `assertThatThrownBy()` for exception testing with `.isInstanceOf()` and `.hasMessageContaining()`
*   Verify mock interactions with `verify(repository, times(n)).method()` or `verify(repository).method()` for once
*   Use `never()` to verify methods NOT called in failure scenarios: `verify(repository, never()).persist(any())`
*   Import static methods: `import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;`
*   Follow line reference from RoomServiceTest for exact patterns

### Specific Test Patterns from RoomServiceTest

**Pattern 1: Happy Path Test (lines 61-82)**
```java
@Test
void testCreateRoom_ValidInput_ReturnsRoom() throws JsonProcessingException {
    // Given
    String title = "Test Room";
    String configJson = "{\"deckType\":\"FIBONACCI\"}";
    Room expectedRoom = new Room();
    expectedRoom.roomId = "abc123";
    expectedRoom.title = title;

    when(objectMapper.writeValueAsString(any(RoomConfig.class))).thenReturn(configJson);
    when(roomRepository.persist(any(Room.class))).thenReturn(Uni.createFrom().item(expectedRoom));

    // When
    Room result = roomService.createRoom(title, PrivacyMode.PUBLIC, null, testConfig)
            .await().indefinitely();

    // Then
    assertThat(result).isNotNull();
    assertThat(result.roomId).isEqualTo("abc123");
    assertThat(result.title).isEqualTo(title);
    verify(roomRepository).persist(any(Room.class));
    verify(objectMapper).writeValueAsString(testConfig);
}
```

**Pattern 2: Null Validation Test (lines 105-115)**
```java
@Test
void testCreateRoom_NullTitle_ThrowsException() {
    // When/Then
    assertThatThrownBy(() ->
            roomService.createRoom(null, PrivacyMode.PUBLIC, null, testConfig)
                    .await().indefinitely()
    )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("title cannot be null");

    verify(roomRepository, never()).persist(any(Room.class));
}
```

**Pattern 3: Soft Delete Test (lines 432-451)**
```java
@Test
void testDeleteRoom_ValidRoom_SoftDeletes() {
    // Given
    String roomId = "room123";
    Room existingRoom = createTestRoom(roomId, "Test Room");

    when(roomRepository.findById(roomId)).thenReturn(Uni.createFrom().item(existingRoom));
    when(roomRepository.persist(any(Room.class))).thenAnswer(invocation -> {
        Room room = invocation.getArgument(0);
        return Uni.createFrom().item(room);
    });

    // When
    Room result = roomService.deleteRoom(roomId)
            .await().indefinitely();

    // Then
    assertThat(result.deletedAt).isNotNull();
    verify(roomRepository).findById(roomId);
    verify(roomRepository).persist(any(Room.class));
}
```

---

**EXECUTION CHECKLIST:**

1. ✓ RoomServiceTest already complete with 40+ tests
2. **TODO:** Create comprehensive UserServiceTest with 15-20 tests
3. **TODO:** Test all 9 public methods in UserService
4. **TODO:** Test email validation with various formats (valid, invalid, null)
5. **TODO:** Test display name validation (null, empty, too long >100)
6. **TODO:** Test findOrCreateUser both paths (existing user, new user)
7. **TODO:** Test createDefaultPreferences chain via createUser
8. **TODO:** Test soft delete behavior (deletedAt timestamp)
9. **TODO:** Test JSONB serialization success and failure scenarios
10. **TODO:** Run `mvn test` and verify >90% coverage for UserService
