# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I6.T8",
  "iteration_id": "I6",
  "iteration_goal": "Implement session history tracking, tier-based reporting (basic summaries for Free, detailed analytics for Pro/Enterprise), export functionality (CSV/PDF), and frontend reporting UI.",
  "description": "Create integration test for export job end-to-end flow. Test: trigger export API, verify job enqueued to Redis Stream, worker processes job, CSV/PDF generated, file uploaded to S3 (use LocalStack or S3Mock), job status updated to COMPLETED, download URL returned. Test error scenario (S3 upload failure, job marked FAILED). Use Testcontainers for Redis and PostgreSQL.",
  "agent_type_hint": "BackendAgent",
  "inputs": "ExportJobProcessor from I6.T3, Redis Streams testing patterns, S3 mocking (LocalStack or S3Mock)",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/worker/ExportJobProcessor.java"
  ],
  "target_files": [
    "backend/src/test/java/com/scrumpoker/worker/ExportJobIntegrationTest.java"
  ],
  "deliverables": "Integration test for export flow, Test: job enqueued → worker processes → file uploaded → status updated, Test: S3 failure → job marked FAILED, LocalStack or S3Mock for S3 testing, Assertions on job status transitions",
  "acceptance_criteria": "`mvn verify` runs export integration test, Export job processes successfully, CSV file uploaded to mock S3, Job status transitions: PENDING → PROCESSING → COMPLETED, Download URL generated and accessible, Failure test marks job FAILED with error message",
  "dependencies": [
    "I6.T3"
  ],
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: asynchronous-job-processing-pattern (from 04_Behavior_and_Communication.md)

```markdown
<!-- anchor: asynchronous-job-processing-pattern -->
##### Asynchronous Job Processing (Fire-and-Forget)

**Use Cases:**
- Report export generation (CSV, PDF) for large datasets
- Email notifications (subscription confirmations, payment receipts)
- Analytics aggregation for organizational dashboards
- Audit log archival to object storage

**Pattern Characteristics:**
- REST endpoint returns `202 Accepted` immediately with job ID
- Job message enqueued to Redis Stream
- Background worker consumes stream, processes job
- Client polls status endpoint or receives WebSocket notification on completion
- Job results stored in object storage (S3) with time-limited signed URLs

**Flow Example (Report Export):**
1. Client: `POST /api/v1/reports/export` → Server: `202 Accepted` + `{"jobId": "uuid", "status": "pending"}`
2. Server enqueues job to Redis Stream: `jobs:reports`
3. Background worker consumes job, queries PostgreSQL, generates CSV
4. Worker uploads file to S3, updates job status in database
5. Client polls: `GET /api/v1/jobs/{jobId}` → `{"status": "completed", "downloadUrl": "https://..."}`
```

### Context: integration-testing (from 03_Verification_and_Glossary.md)

```markdown
<!-- anchor: integration-testing -->
#### Integration Testing

**Scope:** Multiple components working together with real infrastructure (database, cache, message queue)

**Framework:** Quarkus Test (`@QuarkusTest`), Testcontainers, REST Assured

**Coverage Target:** Critical integration points (API → Service → Repository → Database)

**Approach:**
- Use Testcontainers for PostgreSQL and Redis (real instances, not mocks)
- Test REST endpoints end-to-end (request → response with database persistence)
- Test WebSocket flows (connection → message handling → database → Pub/Sub broadcast)
- Verify transaction boundaries and data consistency
- Run in CI pipeline (longer execution time acceptable: 10-15 minutes)

**Examples:**
- `RoomControllerTest`: POST /rooms creates database record, GET retrieves it
- `VotingFlowIntegrationTest`: WebSocket vote message → database insert → Redis Pub/Sub → client broadcast
- `StripeWebhookControllerTest`: Webhook event → signature verification → database update

**Acceptance Criteria:**
- All integration tests pass (`mvn verify`)
- Testcontainers start successfully (PostgreSQL, Redis)
- Database schema migrations execute correctly in tests
- No test pollution (each test isolated with database cleanup)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/worker/ExportJobIntegrationTest.java`
    *   **Summary:** This file ALREADY EXISTS and contains a comprehensive integration test for the export job flow. It includes tests for successful export flow and failure scenarios.
    *   **Current State:** The test file is complete with two test methods:
        1. `testExportJobSuccessFlow()` - Tests PENDING → PROCESSING → COMPLETED flow
        2. `testExportJobFailure_SessionNotFound()` - Tests FAILED status when session doesn't exist
    *   **Recommendation:** **CRITICAL - This task appears to be ALREADY COMPLETE.** Review the existing implementation to verify it meets all acceptance criteria. The file contains exactly what was requested in the task description.

*   **File:** `backend/src/test/java/com/scrumpoker/worker/ExportJobTestProfile.java`
    *   **Summary:** Test profile configuration that enables MockS3Producer and overrides configuration for test environment.
    *   **Key Configuration:**
        - S3 bucket name: "test-exports-bucket"
        - Signed URL expiration: 3600 seconds (1 hour for testing)
        - Enables MockS3Producer as CDI alternative
    *   **Recommendation:** This profile is properly configured to support the integration test. The MockS3Producer approach is already implemented and functional.

*   **File:** `backend/src/test/java/com/scrumpoker/worker/MockS3Producer.java`
    *   **Summary:** CDI producer that provides mocked S3Client and S3Presigner beans using Mockito.
    *   **Mock Behavior:**
        - S3Client.putObject() returns success with random ETag
        - S3Presigner.presignGetObject() returns valid presigned URL
    *   **Recommendation:** The S3 mocking strategy is already implemented and tested. No changes needed here.

*   **File:** `backend/src/main/java/com/scrumpoker/worker/ExportJobProcessor.java`
    *   **Summary:** Background worker that processes export jobs from Redis Stream. Handles job lifecycle: PENDING → PROCESSING → COMPLETED/FAILED.
    *   **Key Methods:**
        - `processExportJob()`: Main processing logic with retry support
        - Uses CsvExporter and PdfExporter for file generation
        - Uploads to S3 via S3Adapter
        - Updates ExportJob entity status
    *   **Recommendation:** The processor has a public `processExportJob()` method that the test calls directly (line 122 in test), bypassing Redis Stream for faster, more deterministic testing.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/ExportJob.java`
    *   **Summary:** JPA entity tracking export job status with helper methods for state transitions.
    *   **State Transition Methods:**
        - `markAsProcessing()`: Sets status to PROCESSING, records start time
        - `markAsCompleted(downloadUrl)`: Sets status to COMPLETED with URL
        - `markAsFailed(errorMessage)`: Sets status to FAILED with error details
    *   **Recommendation:** Use the entity's built-in state transition methods. They handle timestamp updates automatically.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/s3/S3Adapter.java`
    *   **Summary:** AWS S3 integration adapter that uploads files and generates presigned URLs.
    *   **Key Details:**
        - Object key format: `exports/{sessionId}/{jobId}.{format}`
        - Uses blocking S3Client but wraps in Uni on worker thread pool
        - Presigned URLs expire after configured duration (7 days in prod, 1 hour in tests)
    *   **Recommendation:** S3Adapter is injected into ExportJobProcessor. In tests, the mocked S3Client is injected automatically via MockS3Producer.

### Implementation Tips & Notes

*   **CRITICAL TIP:** The task description asks you to "Create integration test for export job end-to-end flow" but this test **ALREADY EXISTS** and is fully functional. Before writing any code, carefully review `ExportJobIntegrationTest.java` to confirm it meets all acceptance criteria listed in the task.

*   **Test Strategy:** The existing test uses a **manual trigger approach** - it calls `ExportJobProcessor.processExportJob()` directly rather than enqueuing to Redis Stream. This is documented in the test's JavaDoc (lines 47-52) as providing "faster, more deterministic tests while still covering all the core processing logic."

*   **Data Setup Pattern:** The test follows this setup pattern:
    1. Clean up all test data in `@BeforeEach` (lines 80-99)
    2. Create test entities: User → Room → SessionHistory → RoomParticipant → Round → Vote
    3. Use `Panache.withTransaction()` to wrap entity creation
    4. Use `.persist()` followed by single `.flush()` at end (not `persistAndFlush()` for each)

*   **Assertion Pattern:** Tests use `@RunOnVertxContext` and `UniAsserter` for reactive assertions:
    - `asserter.execute()` runs reactive operations
    - `asserter.assertThat()` for assertions on reactive results
    - Follows pattern: execute setup → execute action → assert result

*   **Test Coverage:** The existing tests cover:
    - ✅ Success flow: PENDING → PROCESSING → COMPLETED
    - ✅ Job status transitions with timestamps
    - ✅ Download URL generation
    - ✅ S3 upload (mocked)
    - ✅ Failure scenario: Session not found → FAILED status
    - ⚠️ **MISSING:** The task description mentions "S3 upload failure" test, but current failure test only covers "session not found" scenario

*   **Potential Gap:** If the task requires a SPECIFIC test for S3 upload failures (not just session-not-found failures), you may need to add one more test method that:
    1. Creates valid session data
    2. Reconfigures MockS3Producer to throw exception on putObject()
    3. Verifies job marked as FAILED with S3-related error message

*   **Note on Testcontainers:** The test uses `@QuarkusTest` which automatically starts Testcontainers for PostgreSQL and Redis via Quarkus Dev Services. No explicit Testcontainers configuration is needed in the test class.

*   **Acceptance Criteria Verification:**
    - ✅ "`mvn verify` runs export integration test" - Test is in src/test, will run on verify
    - ✅ "Export job processes successfully" - Covered by testExportJobSuccessFlow
    - ✅ "CSV file uploaded to mock S3" - MockS3Client configured to accept uploads
    - ✅ "Job status transitions: PENDING → PROCESSING → COMPLETED" - Assertions on lines 129-141
    - ✅ "Download URL generated and accessible" - Asserted on lines 131-134
    - ✅ "Failure test marks job FAILED with error message" - Covered by testExportJobFailure_SessionNotFound (lines 159-200)
    - ⚠️ "S3 failure → job marked FAILED" - Only session-not-found failure tested, not explicit S3 failure

### Recommendation Summary

**Before writing any code:** Run the existing test to verify it passes:
```bash
cd backend
mvn test -Dtest=ExportJobIntegrationTest
```

If the test passes and covers all requirements, **mark the task as done and update the task manifest**. If there's a gap (specifically S3 upload failure testing), add ONE additional test method to the existing file rather than rewriting everything.
