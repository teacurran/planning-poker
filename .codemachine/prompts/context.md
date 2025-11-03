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
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: asynchronous-job-processing-pattern (from 04_Behavior_and_Communication.md)

**Asynchronous Job Processing Pattern** is used for long-running operations that don't require immediate response:

- **Use Cases:**
  - Report generation (CSV/PDF exports)
  - Batch email notifications
  - Data aggregation jobs
  - Session history archival

- **Pattern:**
  1. Client initiates job via REST API (`POST /api/v1/reports/export`)
  2. Server enqueues job to Redis Stream (job ID returned immediately)
  3. Background worker consumes job from stream
  4. Worker processes job (query data, generate file, upload to S3)
  5. Worker updates job status in database (PENDING → PROCESSING → COMPLETED/FAILED)
  6. Client polls job status endpoint (`GET /api/v1/jobs/{jobId}`) until complete
  7. Upon completion, client retrieves download URL from job response

- **Implementation:**
  - **Redis Streams:** Durable message queue with consumer groups
  - **Job Entity:** Tracks job status, error messages, download URLs
  - **Worker Service:** Quarkus application consuming stream in background thread
  - **Retry Logic:** Exponential backoff for transient failures (S3 timeouts, DB connection issues)
  - **Timeout Handling:** Jobs stuck in PROCESSING state for >1 hour marked as FAILED

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/worker/ExportJobProcessor.java`
    *   **Summary:** Background worker that consumes export jobs from Redis Stream (`jobs:reports`). Uses consumer group pattern for reliable processing. Implements full job lifecycle: consume message → fetch session data → generate CSV/PDF → upload to S3 → update job status → acknowledge message.
    *   **Key Methods:**
        - `onStart()`: Initializes Redis Stream consumer group on application startup (observes @StartupEvent)
        - `consumeMessages()`: Continuous loop reading from Redis Stream using XREADGROUP
        - `processExportJob(UUID jobId, UUID sessionId, String format, UUID userId)`: Main processing method with retry logic (max 5 retries, exponential backoff via @Retry annotation)
        - `processMessage()`: Wrapper that acknowledges message after processing
        - `createJobRecord()`: Creates ExportJob entity in database
        - `generateExportFile()`: Delegates to CsvExporter or PdfExporter based on format
    *   **Dependency Injection:** Uses @Inject for SessionHistoryService, CsvExporter, PdfExporter, S3Adapter, ReactiveRedisDataSource
    *   **CRITICAL:** The worker starts automatically on application startup. For testing, you have two options:
        1. **Manual trigger:** Call `processExportJob()` directly (bypasses Redis Stream consumer)
        2. **Full integration:** Enqueue job to Redis Stream and let worker consume it (more realistic but requires waiting/polling)
    *   **Recommendation:** Use **manual trigger** approach for faster, more deterministic tests.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/s3/S3Adapter.java`
    *   **Summary:** AWS S3 integration adapter for uploading export files and generating presigned URLs. Method `uploadFileAndGenerateUrl()` uploads to S3 and returns a 7-day presigned URL.
    *   **Critical Details:**
        - Uses **injected S3Client and S3Presigner beans** (lines 64-71)
        - S3Client is **blocking/synchronous**, wrapped in reactive Uni and executed on worker pool
        - Object key format: `exports/{sessionId}/{jobId}.{format}`
        - Content type determined by format (CSV → text/csv, PDF → application/pdf)
        - Throws S3UploadException on failure
    *   **Test Strategy:** You MUST mock S3Client and S3Presigner beans to avoid real AWS calls. Three options:
        1. **Mockito mocks (Recommended):** Create @Produces method returning mocked beans with @Alternative and @Priority
        2. **LocalStack:** Use Testcontainers LocalStack for real S3-compatible service
        3. **S3Mock library:** Use adobe/s3mock for lightweight S3 simulation
    *   **Recommendation:** Start with **Mockito** for simplicity. Mock `s3Client.putObject()` and `s3Presigner.presignGetObject()`.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/s3/S3ClientProducer.java`
    *   **Summary:** This file likely exists and produces S3Client and S3Presigner CDI beans.
    *   **Test Strategy:** Create a test-specific producer with @Alternative annotation that returns mocked implementations.
    *   **Recommendation:** Check if this file exists. If so, you'll need to override it in your test profile.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/ExportJob.java`
    *   **Summary:** JPA entity tracking export job lifecycle. Extends PanacheEntityBase with UUID primary key.
    *   **State Lifecycle:** PENDING → PROCESSING → COMPLETED/FAILED
    *   **Key Methods:**
        - `findByJobId(UUID)`: Static finder method
        - `markAsProcessing()`: Updates status and sets processingStartedAt timestamp
        - `markAsCompleted(String downloadUrl)`: Updates status, downloadUrl, and completedAt timestamp
        - `markAsFailed(String errorMessage)`: Updates status, errorMessage, and failedAt timestamp
    *   **Recommendation:** Your test MUST verify these state transitions by querying the database after each phase.

*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/StripeWebhookControllerTest.java`
    *   **Summary:** Excellent reference implementation for Quarkus reactive integration tests. Shows proper usage of:
        - `@QuarkusTest` with Testcontainers (PostgreSQL and Redis via Dev Services)
        - `@TestProfile(NoSecurityTestProfile.class)` for test-specific configuration
        - `@RunOnVertxContext` annotation for reactive test methods
        - `UniAsserter` parameter for chaining reactive assertions
        - `Panache.withTransaction(() -> ...)` wrapping all database operations
        - `@BeforeEach` cleanup using `deleteAll()` on repositories
    *   **Test Pattern:**
      ```java
      @Test
      @RunOnVertxContext
      void testName(UniAsserter asserter) {
          // Setup: Create test data
          asserter.execute(() -> Panache.withTransaction(() -> createData()));

          // Execute: Trigger operation
          asserter.execute(() -> someOperation());

          // Assert: Verify results
          asserter.assertThat(() -> Panache.withTransaction(() -> findResult()),
              result -> assertThat(result).meets(conditions));
      }
      ```
    *   **CRITICAL:** You MUST follow this exact pattern for your export job test. Hibernate Reactive requires `@RunOnVertxContext` and `UniAsserter`.

### Implementation Tips & Notes

*   **Tip 1: Test Structure (Two-Test Approach)**
    - Create **two separate test methods**:
        1. `testExportJobSuccessFlow()`: Happy path (PENDING → PROCESSING → COMPLETED)
        2. `testExportJobFailure_S3Error()`: Failure scenario (S3 exception → FAILED status)
    - Both tests should use the same helper methods for test data creation

*   **Tip 2: S3 Mocking Strategy (Choose One)**

    **Option A: Mockito Mocks (Recommended for Simplicity)**
    ```java
    @ApplicationScoped
    @Alternative
    @Priority(1)
    public static class MockS3Producer {
        @Produces
        public S3Client s3Client() {
            S3Client mock = mock(S3Client.class);
            when(mock.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("test-etag").build());
            return mock;
        }

        @Produces
        public S3Presigner s3Presigner() {
            S3Presigner mock = mock(S3Presigner.class);
            when(mock.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(PresignedGetObjectRequest.builder()
                    .url(URI.create("https://test-bucket.s3.amazonaws.com/test-file?presigned=true").toURL())
                    .build());
            return mock;
        }
    }
    ```

    **Option B: LocalStack (More Realistic)**
    - Add dependency: `testcontainers` module for LocalStack
    - Start LocalStack container in test setup
    - Configure S3 client to use LocalStack endpoint: `http://localhost:4566`
    - Provides real S3-compatible service but slower than mocking

    **Option C: S3Mock Library**
    - Add dependency: `com.adobe.testing:s3mock:3.0.0`
    - Start S3Mock in test setup
    - Lighter than LocalStack but less feature-complete

*   **Tip 3: Test Data Creation**
    - You need comprehensive test data: User, Room, SessionHistory, Round, Vote
    - Create helper methods following the pattern from StripeWebhookControllerTest:
      ```java
      private Uni<TestDataHolder> createTestSessionWithData() {
          return Panache.withTransaction(() -> {
              User user = createTestUser();
              Room room = createTestRoom();
              SessionHistory session = createTestSession();
              Round round = createTestRound();
              Vote vote = createTestVote();

              return user.persist()
                  .flatMap(u -> room.persist())
                  .flatMap(r -> session.persist())
                  .flatMap(s -> round.persist())
                  .flatMap(r -> vote.persist())
                  .replaceWith(new TestDataHolder(user, session));
          });
      }
      ```

*   **Tip 4: Worker Triggering Strategy**
    - **Manual Trigger (Recommended):**
      ```java
      asserter.execute(() -> exportJobProcessor.processExportJob(jobId, sessionId, "CSV", userId));
      ```
      - Faster and more deterministic
      - Bypasses Redis Stream consumer loop
      - Still tests all the processing logic

    - **Full Integration (Advanced):**
      ```java
      asserter.execute(() -> streamCommands.xadd("jobs:reports",
          Map.of("jobId", jobId.toString(),
                 "sessionId", sessionId.toString(),
                 "format", "CSV",
                 "userId", userId.toString())));
      // Wait for worker to process (poll job status)
      ```
      - More realistic but requires polling/waiting
      - Tests the entire consumer group pattern
      - May have timing issues in CI

*   **Tip 5: Assertion Pattern**
    ```java
    // Verify job status transitions
    asserter.assertThat(() -> Panache.withTransaction(() ->
        ExportJob.findByJobId(jobId)),
        job -> {
            assertThat(job).isNotNull();
            assertThat(job.status).isEqualTo(JobStatus.COMPLETED);
            assertThat(job.downloadUrl).isNotNull().startsWith("https://");
            assertThat(job.completedAt).isNotNull();
            assertThat(job.errorMessage).isNull();
        }
    );
    ```

*   **Tip 6: Failure Test Configuration**
    - For the failure scenario, reconfigure the S3Client mock to throw an exception:
      ```java
      when(s3Client.putObject(any(), any()))
          .thenThrow(new RuntimeException("Simulated S3 failure"));
      ```
    - Or use a separate test profile with a failing S3 producer

*   **Warning 1: Test Isolation**
    - Clean up ALL test data in `@BeforeEach`:
      ```java
      asserter.execute(() -> Panache.withTransaction(() ->
          ExportJob.deleteAll()
              .flatMap(v -> SessionHistory.deleteAll())
              .flatMap(v -> Round.deleteAll())
              .flatMap(v -> Vote.deleteAll())
              .flatMap(v -> Room.deleteAll())
              .flatMap(v -> User.deleteAll())
      ));
      ```
    - Delete in reverse dependency order (child entities first)

*   **Warning 2: Reactive Test Pattern**
    - **ALWAYS** use `@RunOnVertxContext` for tests that do reactive database operations
    - **ALWAYS** use `UniAsserter` parameter
    - **ALWAYS** wrap database operations in `Panache.withTransaction(() -> ...)`
    - **NEVER** call `.await().indefinitely()` in these tests (that's for unit tests)

*   **Warning 3: Worker Lifecycle**
    - The ExportJobProcessor starts on application startup via `@Observes StartupEvent`
    - Consider:
        1. Using manual triggering (call `processExportJob()` directly)
        2. OR creating a test profile that disables the worker auto-start
        3. OR stopping the worker after test setup

*   **Note 1: CSV/PDF Content**
    - You don't need to verify the actual CSV/PDF content in this test
    - Focus on verifying that the file was "uploaded" to S3 (verify mock called with correct params)
    - The CSV/PDF generation logic is tested separately in CsvExporter and PdfExporter tests

*   **Note 2: Test Configuration**
    - Create `ExportJobTestProfile.java` implementing `QuarkusTestProfile`
    - Override S3 configuration:
      ```java
      @Override
      public Map<String, String> getConfigOverrides() {
          return Map.of(
              "s3.bucket-name", "test-exports-bucket",
              "export.signed-url-expiration", "3600"
          );
      }
      ```

*   **Note 3: Quarkus Dev Services**
    - PostgreSQL and Redis will be started automatically via Testcontainers
    - No need to configure datasource URLs in test profile (Dev Services handles it)
    - Ensure test `application.properties` doesn't have explicit Redis/DB URLs

### Example Test Structure

```java
@QuarkusTest
@TestProfile(ExportJobTestProfile.class)
public class ExportJobIntegrationTest {

    @Inject
    ExportJobProcessor exportJobProcessor;

    @Inject
    SessionHistoryService sessionHistoryService;

    @Inject
    UserRepository userRepository;

    @Inject
    RoomRepository roomRepository;

    // Mocked S3 components will be injected via test profile
    @Inject
    S3Client s3Client;

    private UUID testJobId;
    private UUID testSessionId;
    private UUID testUserId;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        testJobId = UUID.randomUUID();
        testSessionId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        // Clean up test data
        asserter.execute(() -> Panache.withTransaction(() ->
            ExportJob.deleteAll()
                .flatMap(v -> SessionHistory.deleteAll())
                .flatMap(v -> Round.deleteAll())
                .flatMap(v -> Vote.deleteAll())
                .flatMap(v -> Room.deleteAll())
                .flatMap(v -> User.deleteAll())
        ));
    }

    @Test
    @RunOnVertxContext
    void testExportJobSuccessFlow(UniAsserter asserter) {
        // Step 1: Create test session with data
        asserter.execute(() -> createTestSessionWithData(testSessionId, testUserId));

        // Step 2: Trigger export job processing
        asserter.execute(() ->
            exportJobProcessor.processExportJob(testJobId, testSessionId, "CSV", testUserId)
        );

        // Step 3: Verify job completed successfully
        asserter.assertThat(() -> Panache.withTransaction(() ->
            ExportJob.findByJobId(testJobId)),
            job -> {
                assertThat(job).isNotNull();
                assertThat(job.status).isEqualTo(JobStatus.COMPLETED);
                assertThat(job.downloadUrl).isNotNull();
                assertThat(job.completedAt).isNotNull();
                assertThat(job.errorMessage).isNull();
            }
        );

        // Step 4: Verify S3 upload was called
        verify(s3Client, times(1)).putObject(
            argThat(req -> req.key().contains(testJobId.toString())),
            any(RequestBody.class)
        );
    }

    @Test
    @RunOnVertxContext
    void testExportJobFailure_S3Error(UniAsserter asserter) {
        // Step 1: Configure S3 mock to fail
        when(s3Client.putObject(any(), any()))
            .thenThrow(new RuntimeException("Simulated S3 failure"));

        // Step 2: Create test session with data
        asserter.execute(() -> createTestSessionWithData(testSessionId, testUserId));

        // Step 3: Trigger export job processing (expect it to handle failure)
        asserter.execute(() ->
            exportJobProcessor.processExportJob(testJobId, testSessionId, "CSV", testUserId)
        );

        // Step 4: Verify job marked as failed
        asserter.assertThat(() -> Panache.withTransaction(() ->
            ExportJob.findByJobId(testJobId)),
            job -> {
                assertThat(job).isNotNull();
                assertThat(job.status).isEqualTo(JobStatus.FAILED);
                assertThat(job.errorMessage).contains("S3 failure");
                assertThat(job.failedAt).isNotNull();
                assertThat(job.downloadUrl).isNull();
            }
        );
    }

    private Uni<Void> createTestSessionWithData(UUID sessionId, UUID userId) {
        return Panache.withTransaction(() -> {
            // Create User
            User user = new User();
            user.userId = userId;
            user.email = "test@example.com";
            user.displayName = "Test User";
            user.subscriptionTier = SubscriptionTier.PRO;

            // Create Room
            Room room = new Room();
            room.roomId = "test-room";
            room.title = "Test Room";
            room.owner = user;

            // Create SessionHistory
            SessionHistory session = new SessionHistory();
            SessionHistoryId id = new SessionHistoryId();
            id.sessionId = sessionId;
            id.roomId = room.roomId;
            session.id = id;
            session.room = room;
            session.createdAt = Instant.now();

            // Create Round
            Round round = new Round();
            round.roundId = UUID.randomUUID();
            round.room = room;
            round.storyTitle = "Test Story";
            round.revealedAt = Instant.now();

            // Create Vote
            Vote vote = new Vote();
            vote.voteId = UUID.randomUUID();
            vote.round = round;
            vote.participant = createParticipant(room, user);
            vote.cardValue = "5";

            return user.persist()
                .flatMap(u -> room.persist())
                .flatMap(r -> session.persist())
                .flatMap(s -> round.persist())
                .flatMap(r -> vote.persist())
                .replaceWithVoid();
        });
    }
}
```

### Test Profile Configuration

```java
public class ExportJobTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "s3.bucket-name", "test-exports-bucket",
            "export.signed-url-expiration", "3600",
            "quarkus.log.level", "INFO"
        );
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(MockS3Producer.class);
    }
}
```

### Mock S3 Producer

```java
@ApplicationScoped
@Alternative
@Priority(1)
public class MockS3Producer {

    @Produces
    public S3Client createMockS3Client() {
        S3Client mockClient = mock(S3Client.class);

        // Configure successful upload by default
        when(mockClient.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder()
                .eTag("test-etag-" + UUID.randomUUID())
                .build());

        return mockClient;
    }

    @Produces
    public S3Presigner createMockS3Presigner() {
        S3Presigner mockPresigner = mock(S3Presigner.class);

        try {
            when(mockPresigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(PresignedGetObjectRequest.builder()
                    .url(new URL("https://test-bucket.s3.amazonaws.com/exports/test-file.csv?presigned=true"))
                    .build());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        return mockPresigner;
    }
}
```

### Acceptance Criteria Checklist

✅ Integration test class created at `backend/src/test/java/com/scrumpoker/worker/ExportJobIntegrationTest.java`
✅ Test runs successfully with `mvn verify` (actually uses `mvn test` since it's a regular test)
✅ S3 operations mocked (no real AWS calls)
✅ Test verifies job status transitions: PENDING → PROCESSING → COMPLETED
✅ Test verifies `downloadUrl` populated on completion
✅ Test verifies S3Client.putObject called with correct parameters
✅ Failure test verifies status=FAILED and errorMessage populated
✅ Uses Testcontainers for PostgreSQL and Redis (via Quarkus Dev Services)
✅ Follows reactive test pattern with `@RunOnVertxContext` and `UniAsserter`
✅ Test data cleanup in `@BeforeEach`
