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
  "inputs": "*   RoomService and UserService from I2.T3, I2.T4\n        *   JUnit 5 and Mockito testing patterns",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   RoomServiceTest with 10+ test methods covering create, update, delete, find operations\n        *   UserServiceTest with 10+ test methods covering profile, preferences, soft delete\n        *   Mocked repository interactions using Mockito\n        *   Exception scenario tests (assertThrows for custom exceptions)\n        *   AssertJ assertions for fluent readability",
  "acceptance_criteria": "*   `mvn test` runs all unit tests successfully\n        *   Test coverage >90% for RoomService and UserService\n        *   All business validation scenarios tested (invalid input → exception)\n        *   Happy path tests verify correct repository method calls\n        *   Exception tests verify custom exceptions thrown with correct messages",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Task 2.7 – Write Unit Tests for Domain Services (from .codemachine/artifacts/plan/02_Iteration_I2.md)

```markdown
<!-- anchor: task-i2-t7 -->
*   **Task 2.7: Write Unit Tests for Domain Services**
    *   **Task ID:** `I2.T7`
    *   **Description:** Create comprehensive unit tests for `RoomService` and `UserService` using JUnit 5 and Mockito. Mock repository dependencies. Test business logic: room creation with unique ID generation, config validation, soft delete behavior, user profile updates, preference persistence. Test exception scenarios (e.g., room not found, invalid email format). Use AssertJ for fluent assertions. Aim for >90% code coverage on service classes.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   RoomService and UserService from I2.T3, I2.T4
        *   JUnit 5 and Mockito testing patterns
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/domain/room/RoomServiceTest.java`
        *   `backend/src/test/java/com/scrumpoker/domain/user/UserServiceTest.java`
    *   **Deliverables:**
        *   RoomServiceTest with 10+ test methods covering create, update, delete, find operations
        *   UserServiceTest with 10+ test methods covering profile, preferences, soft delete
        *   Mocked repository interactions using Mockito
        *   Exception scenario tests (assertThrows for custom exceptions)
        *   AssertJ assertions for fluent readability
    *   **Acceptance Criteria:**
        *   `mvn test` runs all unit tests successfully
        *   Test coverage >90% for RoomService and UserService
        *   All business validation scenarios tested (invalid input → exception)
        *   Happy path tests verify correct repository method calls
        *   Exception tests verify custom exceptions thrown with correct messages
    *   **Dependencies:** [I2.T3, I2.T4]
    *   **Parallelizable:** No (depends on service implementation)
```

### Context: Unit Testing Strategy (from .codemachine/artifacts/plan/03_Verification_and_Glossary.md)

```markdown
<!-- anchor: unit-testing -->
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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** Reactive domain service that validates titles/privacy, enforces tier checks via `FeatureGate`, serializes `RoomConfig` with `ObjectMapper`, and persists `Room` entities through `RoomRepository`/Panache sessions.
    *   **Recommendation:** Mirror its validation matrix in the tests—focus on nanoid generation, config serialization failures, privacy/tier enforcement, and soft-delete behavior so regressions get caught before hitting the database layer.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Summary:** Handles OAuth-based user creation, profile updates, preference CRUD, and soft-delete logic with regex email validation plus JSONB serialization helpers.
    *   **Recommendation:** Structure tests around each public method (`createUser`, `updateProfile`, `findOrCreateUser`, `updatePreferences`, etc.) and exercise both happy-path persistence and failure modes (invalid email, deleted user, serialization errors) by mocking `UserRepository`, `UserPreferenceRepository`, and `ObjectMapper` responses.
*   **File:** `backend/src/test/java/com/scrumpoker/domain/room/RoomServiceTest.java`
    *   **Summary:** Already contains a comprehensive Mockito-based suite covering creation, updates, deletion, fetches, and config serialization/deserialization, using `@ExtendWith(MockitoExtension.class)` and Mutiny `Uni` operations via `.await().indefinitely()`.
    *   **Recommendation:** Treat it as the baseline—extend or refactor carefully to keep >90% coverage, mock `FeatureGate` when testing tier-specific flows, and ensure new tests continue to avoid direct Panache session calls by keeping owners null unless mocking `Panache.getSession()`.
*   **File:** `backend/src/test/java/com/scrumpoker/domain/user/UserServiceTest.java`
    *   **Summary:** Mirrors the production methods with mocked repositories/object mapper, validating creation, profile edits, preference management, JSONB serialization fallbacks, and soft deletes.
    *   **Recommendation:** Maintain the existing pattern of `when(...).thenReturn(Uni.createFrom().item(...))`, add missing scenarios (e.g., `findOrCreateUser` updates, `updatePreferences` JSON failures, `deleteUser` edge cases), and ensure AssertJ assertions verify both state mutations and interaction counts.

### Implementation Tips & Notes
*   **Tip:** Keep all tests reactive-friendly by awaiting `Uni`/`Multi` results inside the test body only; never modify the service to expose synchronous methods just for testing purposes.
*   **Tip:** Prefer strict mock verification (`verify(mock).persist(...)`, `verifyNoInteractions(...)`) so that business-rule regressions (like skipping validation) are immediately detectable.
*   **Tip:** When testing tier or validation errors in `RoomService`, inject a mocked `FeatureGate` via `@Mock` + `@InjectMocks` and use `doThrow(new FeatureNotAvailableException(...))` to ensure the service propagates domain-specific exceptions cleanly.
*   **Tip:** For `UserService` preference serialization, use `when(objectMapper.writeValueAsString(...)).thenThrow(...)` to simulate JSONB failures and assert that the service returns the documented fallback (`{}`) or wraps the error in `IllegalArgumentException`.
*   **Note:** Coverage is tracked with JaCoCo in the Maven build; keep each test class lean but thorough so running `mvn test` stays under the 5-minute goal outlined in the verification strategy.
