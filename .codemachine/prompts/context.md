# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I6.T7",
  "iteration_id": "I6",
  "iteration_goal": "Implement session history tracking, tier-based reporting (basic summaries for Free, detailed analytics for Pro/Enterprise), export functionality (CSV/PDF), and frontend reporting UI.",
  "description": "Create unit tests for `ReportingService` with mocked repositories and FeatureGate. Test scenarios: basic summary generation (verify correct stats calculated), detailed report generation (verify round breakdown included), tier enforcement (Free tier accessing detailed report throws exception), user consistency calculation (test with known vote values), export job enqueuing (verify Redis Stream message). Use AssertJ for fluent assertions.",
  "agent_type_hint": "BackendAgent",
  "inputs": "ReportingService from I6.T2, Mockito testing patterns",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java"
  ],
  "target_files": [
    "backend/src/test/java/com/scrumpoker/domain/reporting/ReportingServiceTest.java"
  ],
  "deliverables": "ReportingServiceTest with 12+ test methods, Tests for summary generation (Free tier), Tests for detailed report (Pro tier), Tier enforcement tests (403 for Free tier), Consistency calculation tests (variance formula), Export job enqueue tests",
  "acceptance_criteria": "`mvn test` runs reporting tests successfully, Summary generation test verifies correct consensus rate, Detailed report test includes round breakdown, Tier enforcement test throws FeatureNotAvailableException, Consistency calculation test verifies formula (σ²), Export enqueue test verifies Redis message published",
  "dependencies": [
    "I6.T2"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: reporting-requirements (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: reporting-requirements -->
#### Reporting Requirements
- **Free Tier:** Basic session summaries (story count, consensus rate, average vote)
- **Pro Tier:** Round-level detail, user consistency metrics, CSV/JSON/PDF export
- **Enterprise Tier:** Organizational dashboards, team trends, SSO-filtered reports, audit logs
```

### Context: unit-testing (from 03_Verification_and_Glossary.md)

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

### Context: code-quality-gates (from 03_Verification_and_Glossary.md)

```markdown
<!-- anchor: code-quality-gates -->
### 5.3. Code Quality Gates

**Automated Quality Checks:**

1. **Code Coverage:**
   - Backend: JaCoCo reports, threshold 80% line coverage
   - Frontend: Istanbul/c8 reports, threshold 75% statement coverage
   - Fail CI build if below threshold

2. **Static Analysis (SonarQube):**
   - Code smells: <5 per 1000 lines of code
```

### Context: performance-nfrs (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: performance-nfrs -->
#### Performance
- **Latency:** <200ms round-trip time for WebSocket messages within region
- **Throughput:** Support 500 concurrent sessions with 6,000 active WebSocket connections
- **Response Time:** REST API endpoints respond within <500ms for p95
- **Real-time Updates:** State synchronization across clients within 100ms
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java`
    *   **Summary:** This file contains the ReportingService implementation with three main capabilities: basic session summaries (Free tier), detailed analytics (Pro tier), and export job enqueuing (Pro tier). The service uses FeatureGate for tier enforcement and publishes jobs to Redis Streams.
    *   **Key Methods:**
        *   `getBasicSessionSummary(UUID sessionId)` - Returns SessionSummaryDTO with story count, consensus rate, average vote
        *   `getDetailedSessionReport(UUID sessionId, User user)` - Returns DetailedSessionReportDTO with round-by-round breakdown and user consistency metrics (requires PRO tier)
        *   `generateExport(UUID sessionId, String format, User user)` - Enqueues export job to Redis Stream (requires PRO tier)
        *   `calculateUserConsistency(Map<UUID, List<String>> votesByParticipant, Map<UUID, String> participantNames)` - Private method calculating standard deviation of votes per participant
    *   **Recommendation:** You MUST test all three public methods and verify the consistency calculation algorithm.

*   **File:** `backend/src/test/java/com/scrumpoker/domain/reporting/ReportingServiceTest.java`
    *   **Summary:** **CRITICAL FINDING:** This test file ALREADY EXISTS and contains comprehensive unit tests for ReportingService with 728 lines of code covering all major scenarios.
    *   **Current Test Coverage:**
        *   Basic Session Summary Tests (3 tests): Valid session, session not found, null sessionId
        *   Detailed Session Report Tests (5 tests): Pro user access, Free user rejection, null parameters, empty rounds, non-numeric votes
        *   Export Job Generation Tests (6 tests): CSV export, PDF export, Free user rejection, invalid format, session not found, null parameters
        *   User Consistency Calculation Tests (3 tests): Perfect consistency (σ=0), single vote exclusion, known variance calculation
    *   **Total Test Count:** 17 test methods (exceeds the 12+ requirement)
    *   **Recommendation:** The test file is comprehensive and well-structured. Your task is to REVIEW, ENHANCE, or VERIFY this existing test suite rather than create it from scratch.

*   **File:** `backend/src/test/java/com/scrumpoker/domain/billing/BillingServiceTest.java`
    *   **Summary:** This is an excellent reference implementation showing the project's testing patterns and conventions. It demonstrates proper use of Mockito, reactive types (Uni), and AssertJ assertions.
    *   **Key Patterns Demonstrated:**
        *   `@ExtendWith(MockitoExtension.class)` for Mockito integration
        *   `@Mock` for dependency injection mocks
        *   `@InjectMocks` for the service under test
        *   `@BeforeEach` setup method creating test fixtures
        *   Comprehensive test organization with section comments (`// ===== Create Subscription Tests =====`)
        *   Testing reactive code with `.await().indefinitely()` pattern
        *   `ArgumentCaptor` for verifying method arguments
        *   AssertJ fluent assertions (`assertThat()`, `assertThatThrownBy()`)
    *   **Recommendation:** You SHOULD follow the exact same patterns, conventions, and structure demonstrated in this file.

*   **File:** `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
    *   **Summary:** This service enforces tier-based feature access control. Critical for testing the reporting service's tier enforcement.
    *   **Key Methods:**
        *   `requireCanAccessAdvancedReports(User user)` - Throws FeatureNotAvailableException if user is FREE tier
        *   `canAccessAdvancedReports(User user)` - Returns boolean (PRO or higher)
    *   **Recommendation:** You MUST mock FeatureGate in your tests and verify both the `doNothing()` path (PRO user) and the `doThrow()` path (FREE user).

### Implementation Tips & Notes

*   **Tip:** The existing ReportingServiceTest.java file already has comprehensive coverage. I counted **17 test methods** covering all the scenarios specified in the task deliverables. The task requires 12+ test methods, and the existing file has 17.

*   **Note:** The test file uses the **exact same patterns** as BillingServiceTest.java:
    *   Mockito for dependency mocking
    *   Reactive types (Uni) with `.await().indefinitely()`
    *   AssertJ for fluent assertions
    *   ArgumentCaptor for verifying method calls
    *   Comprehensive test organization with clear section comments

*   **Critical Analysis:** Based on my review of the existing test file, here's what's already covered:
    1. ✅ **Basic summary generation** - Test verifies correct consensus rate calculation (line 218-244)
    2. ✅ **Detailed report generation** - Test verifies round breakdown included (line 280-330)
    3. ✅ **Tier enforcement** - Test verifies Free tier throws FeatureNotAvailableException (line 333-351)
    4. ✅ **User consistency calculation** - Three tests verify variance formula with known values (lines 589-701)
    5. ✅ **Export job enqueuing** - Tests verify Redis Stream message published with correct data (lines 444-584)

*   **Coverage Analysis:** The existing test file has:
    *   3 tests for getBasicSessionSummary()
    *   5 tests for getDetailedSessionReport()
    *   6 tests for generateExport()
    *   3 tests for user consistency calculation logic
    *   **Total: 17 test methods** (exceeds requirement of 12+)

*   **Recommendation for Task Completion:**
    Since comprehensive tests already exist, you should:
    1. **VERIFY** the tests are passing by running `mvn test`
    2. **ANALYZE** the test coverage report to ensure it meets the >90% threshold
    3. **ENHANCE** any gaps if coverage is below 90%
    4. **DOCUMENT** your findings and any enhancements made

*   **Warning:** The task description says "Create unit tests" but the file already exists. This could mean:
    1. The tests were created in a previous session and you need to verify/enhance them
    2. The task status is incorrect and should be marked as done=true
    3. There are specific test scenarios missing that need to be added

*   **JaCoCo Configuration:** The project uses JaCoCo Maven Plugin (version 0.8.11) for code coverage reporting. You can generate coverage reports with `mvn test jacoco:report` and check the results in `target/site/jacoco/index.html`.

### Key Test Scenarios to Verify

Based on the acceptance criteria and my code review, ensure these scenarios are covered:

1. **Summary Generation (Free Tier)**
   - ✅ Valid session returns correct stats (consensus rate, average vote)
   - ✅ Session not found throws IllegalArgumentException
   - ✅ Null sessionId throws IllegalArgumentException

2. **Detailed Report (Pro Tier)**
   - ✅ Pro user gets full round-by-round breakdown
   - ✅ Free user gets FeatureNotAvailableException
   - ✅ Report includes individual votes for each round
   - ✅ User consistency metrics calculated correctly

3. **Tier Enforcement**
   - ✅ Free tier accessing detailed reports → FeatureNotAvailableException
   - ✅ Free tier accessing export → FeatureNotAvailableException
   - ✅ Pro tier can access all features

4. **User Consistency Calculation**
   - ✅ Perfect consistency (all same votes) → σ = 0
   - ✅ Known variance values verified with formula
   - ✅ Non-numeric votes (?, ∞, ☕) excluded from calculation
   - ✅ Single vote per user excluded (need ≥2 for std dev)

5. **Export Job Enqueuing**
   - ✅ CSV export enqueues job with correct format
   - ✅ PDF export enqueues job with correct format
   - ✅ Redis Stream message contains required fields (jobId, sessionId, format, userId)
   - ✅ Invalid format throws IllegalArgumentException

### Running Tests

To execute the tests and verify coverage:

```bash
# Run all tests
mvn test

# Run only ReportingServiceTest
mvn test -Dtest=ReportingServiceTest

# Generate coverage report
mvn test jacoco:report

# View coverage report
open backend/target/site/jacoco/index.html
```

### Expected Test Output

When you run `mvn test`, you should see output like:

```
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
```

The JaCoCo coverage report should show >90% coverage for ReportingService.java.

---

## Summary

**Task Status:** The ReportingServiceTest.java file **ALREADY EXISTS** with comprehensive coverage (17 test methods vs. 12+ required). Your primary task is to:

1. **Verify** the tests are passing
2. **Analyze** code coverage to ensure ≥90% threshold is met
3. **Enhance** any missing test scenarios if needed
4. **Document** your findings

The existing tests follow all project conventions and cover all specified scenarios in the acceptance criteria. This is likely a task verification/enhancement rather than creation from scratch.
