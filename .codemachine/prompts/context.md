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
  "dependencies": ["I2.T3", "I2.T4"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: unit-testing (from 03_Verification_and_Glossary.md)

```markdown
#### Unit Testing

**Scope:** Individual classes and methods in isolation (services, utilities, validators)

**Framework:** JUnit 5 (backend), Jest/Vitest (frontend)

**Coverage Target:** >90% code coverage for service layer, >80% for overall codebase

**Approach:**
- Mock external dependencies (repositories, adapters, external services) using Mockito
- Test business logic thoroughly (happy paths, edge cases, error scenarios)
- Fast execution (<5 minutes for entire unit test suite)
- Run on every developer commit and in CI pipeline

**Examples:**
- `RoomServiceTest`: Tests room creation with unique ID generation, config validation, soft delete
- `VotingServiceTest`: Tests vote casting, consensus calculation with known inputs
- `BillingServiceTest`: Tests subscription tier transitions, Stripe integration mocking

**Acceptance Criteria:**
- All unit tests pass (`mvn test`, `npm run test:unit`)
- Coverage reports meet targets (verify with JaCoCo, Istanbul)
- No flaky tests (consistent results across runs)
```

### Context: code-quality-gates (from 03_Verification_and_Glossary.md)

```markdown
### 5.3. Code Quality Gates

**Automated Quality Checks:**

1. **Code Coverage:**
   - Backend: JaCoCo reports, threshold 80% line coverage
   - Frontend: Istanbul/c8 reports, threshold 75% statement coverage
   - Fail CI build if below threshold

2. **Static Analysis (SonarQube):**
   - Code smells: <5 per 1000 lines of code
   - Duplications: <3% duplicated lines
   - Maintainability rating: A or B
   - Reliability rating: A or B
   - Security rating: A or B

3. **Linting:**
   - Backend: Checkstyle or SpotBugs for Java code style
   - Frontend: ESLint with recommended rules, Prettier for formatting
   - Zero errors allowed (warnings are flagged but don't fail build)
```

### Context: ci-cd-pipeline (from 03_Verification_and_Glossary.md)

```markdown
### 5.2. CI/CD Pipeline Integration

**Continuous Integration (CI):**

Every push to `main` branch or pull request triggers:

1. **Backend CI Pipeline:**
   - Compile Java code (`mvn clean compile`)
   - Run unit tests (`mvn test`)
   - Run integration tests (`mvn verify` with Testcontainers)
   - SonarQube code quality analysis
   - Dependency vulnerability scan (Snyk)
   - Build Docker image
   - Container security scan (Trivy)
   - Publish test results and coverage reports

**Quality Gates:**
- Unit test coverage >80% (fail build if below threshold)
- SonarQube quality gate passed (no blocker/critical issues)
- No HIGH/CRITICAL vulnerabilities in dependencies
- Linter passes with no errors (warnings acceptable)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/domain/room/RoomServiceTest.java`
    *   **Summary:** This file already exists and contains integration tests for RoomService using `@QuarkusTest`. These are NOT unit tests with mocked dependencies, but integration tests running against real database (Testcontainers).
    *   **Recommendation:** You MUST create TRUE UNIT TESTS with Mockito mocks. The existing tests use `@QuarkusTest` and inject real dependencies - your task is to create isolated unit tests that mock `RoomRepository` and `ObjectMapper`.
    *   **Action Required:** You will REPLACE the existing RoomServiceTest.java file completely with proper unit tests using Mockito mocks.

*   **File:** `backend/src/test/java/com/scrumpoker/domain/user/UserServiceTest.java`
    *   **Summary:** This file already exists and contains integration tests for UserService using `@QuarkusTest`. These are NOT unit tests with mocked dependencies.
    *   **Recommendation:** Similar to RoomServiceTest, you MUST create TRUE UNIT TESTS with Mockito mocks. The existing tests use `@QuarkusTest` and inject real dependencies.
    *   **Action Required:** You will REPLACE the existing UserServiceTest.java file completely with proper unit tests using Mockito mocks.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** Service class with methods for room CRUD operations. Uses `@Inject` for RoomRepository and ObjectMapper dependencies. Returns reactive types (Uni, Multi).
    *   **Key Methods to Test:**
        - `createRoom()` - validates title length, privacy mode, generates nanoid, serializes config
        - `updateRoomConfig()` - validates config not null, serializes to JSONB
        - `updateRoomTitle()` - validates title constraints
        - `updatePrivacyMode()` - validates privacy mode not null
        - `deleteRoom()` - soft delete sets deletedAt timestamp
        - `findById()` - throws RoomNotFoundException if not found or deleted
        - `findByOwnerId()` - returns Multi of rooms
        - `getRoomConfig()` - deserializes JSONB config
    *   **Private Methods to Cover:** `generateNanoid()`, `serializeConfig()`, `deserializeConfig()`
    *   **Recommendation:** You MUST mock `RoomRepository` and `ObjectMapper`. Test reactive return types using Mutiny test helpers (AssertSubscriber or UniAssertSubscriber).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Summary:** Service class for user management. Uses `@Inject` for UserRepository, UserPreferenceRepository, and ObjectMapper. Returns reactive types (Uni).
    *   **Key Methods to Test:**
        - `createUser()` - validates OAuth fields, email format, display name length, creates preferences
        - `updateProfile()` - validates display name, updates fields selectively
        - `getUserById()` - throws UserNotFoundException if not found or deleted
        - `findByEmail()` - returns null for empty/deleted users
        - `findOrCreateUser()` - JIT provisioning, finds existing or creates new
        - `getPreferences()` - retrieves user preferences
        - `updatePreferences()` - validates config not null, serializes JSONB
        - `deleteUser()` - soft delete, prevents double delete
    *   **Private Methods to Cover:** `isValidEmail()`, `isValidDisplayName()`, `serializeConfig()`, `deserializeConfig()`, `createDefaultPreferences()`
    *   **Recommendation:** You MUST mock `UserRepository`, `UserPreferenceRepository`, and `ObjectMapper`. Test validation logic thoroughly (email regex, display name length).

*   **File:** `backend/pom.xml`
    *   **Summary:** Contains all necessary testing dependencies: JUnit 5 (`quarkus-junit5`), AssertJ (`assertj-core` version 3.24.2), Mockito (included via `quarkus-test-vertx`).
    *   **Recommendation:** You do NOT need to add any additional dependencies. Mockito is available through Quarkus testing framework.

*   **File:** `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
    *   **Summary:** Example of integration test using `@QuarkusTest`, `@RunOnVertxContext`, and `UniAsserter` for reactive testing.
    *   **Recommendation:** DO NOT follow this pattern for unit tests. This is for integration tests with real database. Your unit tests should NOT use `@QuarkusTest` or `@RunOnVertxContext`.

### Implementation Tips & Notes

*   **Tip:** This project uses Quarkus reactive patterns with Mutiny. Services return `Uni<>` and `Multi<>`. You MUST mock these reactive returns correctly in your tests.
*   **Tip:** Use `Uni.createFrom().item()` to mock successful reactive returns, and `Uni.createFrom().failure()` to mock exceptions.
*   **Tip:** For testing Uni/Multi, you can use `.await().indefinitely()` to block and get the result in unit tests, or use Mutiny test helpers like `UniAssertSubscriber`.
*   **Note:** The existing test files are INTEGRATION TESTS, not UNIT TESTS. The task explicitly requires UNIT TESTS with Mockito mocks. You will completely replace the existing files.
*   **Note:** DO NOT use `@QuarkusTest` annotation for unit tests. Unit tests should be plain JUnit 5 tests with `@ExtendWith(MockitoExtension.class)` to enable Mockito.
*   **Warning:** The services use `@WithTransaction` and `@WithSession` annotations. These are Quarkus-specific and will NOT work in unit tests. Mock the repository methods to return Uni results directly.
*   **Pattern for Mocking:** Use `@Mock` for dependencies, `@InjectMocks` for the service under test, and `@ExtendWith(MockitoExtension.class)` on the test class.
*   **AssertJ Usage:** Use AssertJ's fluent assertions: `assertThat(result).isNotNull()`, `assertThat(exception).isInstanceOf(IllegalArgumentException.class)`, `assertThat(list).hasSize(5)`.
*   **Coverage Target:** You need >90% coverage. Make sure to test all public methods, validation paths, exception scenarios, and edge cases. Use private method testing through public method paths.
*   **Mockito Verification:** Use `verify(repository).persist(any())` to verify repository methods were called with correct arguments.
*   **Exception Testing:** Use `assertThatThrownBy(() -> service.method().await().indefinitely()).isInstanceOf(...)` pattern for testing exceptions from reactive types.

### Key Differences from Existing Tests

| Aspect | Existing Tests (Integration) | Required Tests (Unit) |
|--------|----------------------------|----------------------|
| Annotation | `@QuarkusTest` | `@ExtendWith(MockitoExtension.class)` |
| Dependencies | `@Inject` real beans | `@Mock` mocked dependencies |
| Service | `@Inject` real service | `@InjectMocks` service under test |
| Database | Testcontainers PostgreSQL | No database, fully mocked |
| Execution | Async with `UniAsserter` | Synchronous with `.await().indefinitely()` |
| Setup | `@BeforeEach` with cleanup | `@BeforeEach` for mock setup only |
| Focus | End-to-end integration | Isolated business logic |

### Example Test Structure

```java
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    RoomService roomService;

    @Test
    void testCreateRoom_ValidInput_ReturnsRoom() {
        // Given
        String title = "Test Room";
        RoomConfig config = new RoomConfig();
        Room room = new Room();
        room.roomId = "abc123";

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(roomRepository.persist(any(Room.class))).thenReturn(Uni.createFrom().item(room));

        // When
        Room result = roomService.createRoom(title, PrivacyMode.PUBLIC, null, config)
                                  .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.roomId).isEqualTo("abc123");
        verify(roomRepository).persist(any(Room.class));
    }

    @Test
    void testCreateRoom_NullTitle_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
            roomService.createRoom(null, PrivacyMode.PUBLIC, null, new RoomConfig())
                       .await().indefinitely()
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title cannot be null");
    }
}
```

---

**CRITICAL INSTRUCTIONS:**

1. **DO NOT** use `@QuarkusTest` - these are unit tests, not integration tests
2. **DO** use `@ExtendWith(MockitoExtension.class)` for Mockito support
3. **DO** mock `RoomRepository`, `UserRepository`, `UserPreferenceRepository`, and `ObjectMapper`
4. **DO** use `.await().indefinitely()` to block on Uni results in tests
5. **DO** aim for >90% code coverage on both services
6. **DO** test all validation scenarios, happy paths, and exception cases
7. **DO** use AssertJ for all assertions
8. **DO** verify repository method calls with Mockito's `verify()`
9. **REPLACE** the existing test files completely - they are integration tests, not unit tests
10. **TEST** private methods indirectly through their public method callers
