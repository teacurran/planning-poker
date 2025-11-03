# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I6.T3",
  "iteration_id": "I6",
  "iteration_goal": "Implement session history tracking, tier-based reporting (basic summaries for Free, detailed analytics for Pro/Enterprise), export functionality (CSV/PDF), and frontend reporting UI.",
  "description": "Create background worker consuming export jobs from Redis Stream. `ExportJobProcessor` listens to `jobs:reports` stream, processes job (query session data, generate CSV or PDF), upload to S3 bucket, update job status in database (JobStatus: PENDING → PROCESSING → COMPLETED/FAILED), generate time-limited signed URL for download. Use Apache Commons CSV for CSV generation, iText or Apache PDFBox for PDF. Handle errors (mark job FAILED, store error message). Implement exponential backoff retry for transient failures.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Export requirements (CSV/PDF formats), Redis Streams consumer patterns, S3 upload and signed URL generation",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/worker/ExportJobProcessor.java",
    "backend/src/main/java/com/scrumpoker/worker/CsvExporter.java",
    "backend/src/main/java/com/scrumpoker/worker/PdfExporter.java",
    "backend/src/main/java/com/scrumpoker/integration/s3/S3Adapter.java",
    "backend/src/main/java/com/scrumpoker/domain/reporting/ExportJob.java"
  ],
  "deliverables": "ExportJobProcessor consuming Redis Stream, CsvExporter generating CSV files (session data, rounds, votes), PdfExporter generating PDF reports with summary and charts, S3Adapter uploading files to configured bucket, Signed URL generation (7-day expiration), Job status tracking (PENDING → PROCESSING → COMPLETED), Error handling and retry logic",
  "acceptance_criteria": "Export job message triggers processor, CSV export generates valid file (test with sample session), PDF export generates readable report, File uploaded to S3 successfully, Signed URL allows download without authentication, Job status updated to COMPLETED with download URL, Failed jobs marked with error message, Retry logic handles transient S3 failures",
  "dependencies": ["I6.T2"],
  "parallelizable": true,
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

### Context: Technology Stack Summary (from 02_Architecture_Overview.md)

```markdown
| **Message Queue** | **Redis Streams** | Leverages existing Redis infrastructure, sufficient for asynchronous job processing (report generation, email notifications), simpler than dedicated message brokers |
```

### Context: Reliability and Availability (from 05_Operational_Architecture.md)

```markdown
- **Email Service Down:** Notification emails queued in Redis Stream, retried with exponential backoff (max 24 hours), admin alerted if queue depth exceeds threshold
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java`
    *   **Summary:** This file implements the ReportingService which has already implemented the export job enqueuing mechanism. It uses `ReactiveRedisDataSource` to publish jobs to the Redis Stream `jobs:reports` with job data including jobId, sessionId, format, userId, and requestedAt timestamp.
    *   **Recommendation:** You MUST study the `enqueueExportJob()` method (lines 592-618) to understand the exact message format being published to Redis. The job data structure is:
        ```java
        Map.of(
            "jobId", jobId,
            "sessionId", sessionId.toString(),
            "format", format,
            "userId", user.userId.toString(),
            "requestedAt", Instant.now().toString()
        )
        ```
    *   **Recommendation:** You SHOULD use the same `ReactiveStreamCommands` API from Quarkus Redis for consuming the stream. The stream key is `"jobs:reports"` (constant defined as `EXPORT_JOBS_STREAM`).


*   **File:** `backend/src/main/java/com/scrumpoker/event/RoomEventPublisher.java`
    *   **Summary:** This file demonstrates the project's pattern for using Redis Pub/Sub. While your task uses Redis Streams (not Pub/Sub), this shows how the project initializes Redis connections using `ReactiveRedisDataSource` with `@PostConstruct` initialization.
    *   **Recommendation:** You SHOULD follow the same initialization pattern: inject `ReactiveRedisDataSource`, create a `@PostConstruct` method to initialize stream commands, and use reactive `Uni` types for all operations.
    *   **Note:** This file shows proper error handling with `onFailure().invoke()` for logging and structured error messages using `Log.errorf()`.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/PaymentHistory.java`
    *   **Summary:** This entity demonstrates the project's JPA entity conventions using Quarkus Panache: extends `PanacheEntityBase`, uses `@GeneratedValue(strategy = GenerationType.AUTO)` for UUIDs, `@CreationTimestamp` for timestamps, and enum types with `@Enumerated(EnumType.STRING)`.
    *   **Recommendation:** You MUST follow this exact pattern when creating the `ExportJob` entity. Your entity should have:
        - `@Id UUID jobId` with `@GeneratedValue(strategy = GenerationType.AUTO)`
        - A status enum field with `@Enumerated(EnumType.STRING)` (you need to create `JobStatus` enum: PENDING, PROCESSING, COMPLETED, FAILED)
        - Timestamp fields with `@CreationTimestamp` for `createdAt`
        - Reference to the session (consider `sessionId` as UUID, not a full relationship since SessionHistory uses composite key)

*   **File:** `backend/pom.xml`
    *   **Summary:** Project uses Quarkus 3.15.1 with reactive extensions (hibernate-reactive-panache, reactive-pg-client, redis-client).
    *   **Warning:** The pom.xml does NOT currently include Apache Commons CSV, Apache PDFBox, iText, or AWS S3 SDK dependencies. You MUST add these dependencies to the pom.xml file before implementing the exporters and S3 adapter.
    *   **Recommendation:** Add these dependencies in the `<dependencies>` section:
        - `commons-csv` (Apache Commons CSV) - use version 1.10.0
        - `pdfbox` (Apache PDFBox) - use version 2.0.30 or 3.x (check Quarkus compatibility)
        - `quarkus-amazon-s3` (Quarkus S3 extension) OR `software.amazon.awssdk:s3` (AWS SDK for S3)

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Configuration file shows Redis is configured at `quarkus.redis.hosts=${REDIS_URL:redis://localhost:6379}` with timeout, pool size, and health check settings already in place.
    *   **Recommendation:** You SHOULD add S3 configuration properties:
        - `s3.bucket-name=${S3_BUCKET_NAME:scrum-poker-exports}`
        - `s3.region=${S3_REGION:us-east-1}`
        - `s3.access-key-id=${AWS_ACCESS_KEY_ID:your-access-key}` (for dev, use env vars in prod)
        - `s3.secret-access-key=${AWS_SECRET_ACCESS_KEY:your-secret-key}`
        - `export.signed-url-expiration=${EXPORT_URL_EXPIRATION:604800}` (7 days in seconds)

### Implementation Tips & Notes

*   **Tip:** For consuming Redis Streams, you need to use `ReactiveStreamCommands<String, String, String>` from `redisDataSource.stream(String.class, String.class, String.class)`. Use the `xreadgroup()` method to consume messages with consumer groups for reliability and load balancing. Stream key: `"jobs:reports"`, consumer group: `"export-workers"`, consumer name: use pod hostname or UUID.

*   **Tip:** Redis Streams `xreadgroup` is a blocking operation. You should run the consumer in a separate thread or use Quarkus `@Scheduled` with `@ApplicationScoped` lifecycle. Consider using `io.quarkus.runtime.StartupEvent` to start the consumer when the application boots.

*   **Tip:** The `ReportingService.getDetailedSessionReport()` method (lines 160-177) shows how to query session data. You SHOULD reuse similar patterns: call `sessionHistoryService.getSessionById()`, then fetch rounds with `roundRepository.findByRoomId()`, and votes with `voteRepository.findByRoundId()`. This gives you all the data needed for CSV/PDF export.

*   **Tip:** For CSV generation, the structure should include:
    - Header row: "Round Number", "Story Title", "Participant Name", "Vote", "Average", "Median", "Consensus", "Started At", "Revealed At"
    - Data rows: one row per vote, repeating round metadata for each vote in that round
    - Consider also adding a summary section at the top with session-level stats

*   **Tip:** For PDF generation, use PDFBox or iText to create:
    - Title page with session metadata (room name, date, participants)
    - Summary statistics table (total rounds, consensus rate, average vote)
    - Round-by-round breakdown with vote details
    - Optional: Simple bar chart showing vote distribution (you can use ASCII art or basic shapes if full charting is too complex)

*   **Note:** S3 signed URL generation requires AWS SDK's `presignGetObject()` method. The URL should be valid for 7 days (604800 seconds). The object key should follow pattern: `exports/{sessionId}/{jobId}.{format}` (e.g., `exports/123e4567-e89b-12d3-a456-426614174000/abc-def-123.csv`)

*   **Note:** Job status transitions must be atomic. Use Hibernate transactions (`@Transactional`) when updating the ExportJob entity status. The flow is:
    1. Receive message from Redis Stream → Load job by jobId or create if not exists (status: PENDING)
    2. Update status to PROCESSING, set `processingStartedAt` timestamp
    3. Generate export file (CSV or PDF)
    4. Upload to S3, get signed URL
    5. Update status to COMPLETED, set `completedAt` timestamp, store `downloadUrl`
    6. If any step fails, update status to FAILED, set `failedAt` timestamp, store `errorMessage`

*   **Warning:** Redis Stream consumer group creation (`xgroup create`) must handle "BUSYGROUP" error (group already exists). Wrap in try-catch and ignore if group exists, or use MKSTREAM option.

*   **Tip:** For exponential backoff retry, you can use Quarkus's built-in `@Retry` annotation from MicroProfile Fault Tolerance, or implement manually with delays: 1s, 2s, 4s, 8s, 16s (max). Only retry on transient errors (S3 connection failures, timeouts), NOT on data validation errors.

*   **Tip:** The project uses `io.quarkus.logging.Log` for logging (as seen in ReportingService and RoomEventPublisher). Use structured logging:
    - `Log.infof("Processing export job %s for session %s (format: %s)", jobId, sessionId, format)`
    - `Log.errorf(exception, "Failed to upload export file for job %s", jobId)`

*   **Note:** Since this is a background worker, you need to ensure it runs continuously. Use either:
    - `@ApplicationScoped` bean with `@Observes StartupEvent` to start a consumer loop
    - OR `@Scheduled(every = "5s")` to poll for new messages periodically
    - The consumer loop should run indefinitely with proper error handling to prevent crashes

*   **Warning:** You need to create a database migration script for the `export_job` table. Based on existing migrations (V1, V2, V3, V4), create `V5__create_export_job_table.sql` with:
    - `job_id UUID PRIMARY KEY`
    - `session_id UUID NOT NULL` (no FK since SessionHistory has composite key)
    - `user_id UUID NOT NULL` (FK to users table)
    - `format VARCHAR(10) NOT NULL` (CSV or PDF)
    - `status VARCHAR(20) NOT NULL` (enum: PENDING, PROCESSING, COMPLETED, FAILED)
    - `download_url TEXT` (nullable, set when completed)
    - `error_message TEXT` (nullable, set when failed)
    - `created_at TIMESTAMP NOT NULL DEFAULT now()`
    - `processing_started_at TIMESTAMP`
    - `completed_at TIMESTAMP`
    - `failed_at TIMESTAMP`
    - Indexes: `(status, created_at DESC)`, `(user_id, created_at DESC)`

### Architectural Decisions to Respect

*   **Decision:** The project uses reactive programming throughout (Mutiny `Uni` and `Multi`). All database operations return `Uni<>` types. You MUST maintain this pattern in your S3 operations and job processing. If AWS SDK v2 doesn't have built-in reactive support, wrap blocking calls with `Uni.createFrom().item(() -> blockingOperation()).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`.

*   **Decision:** The project uses PostgreSQL for persistent state, Redis for caching and messaging, and now S3 for object storage. The ExportJob status MUST be stored in PostgreSQL (source of truth), NOT just in Redis, to survive restarts and enable status polling via REST API.

*   **Decision:** All errors should use domain exceptions and be properly mapped. Create `ExportJobNotFoundException`, `ExportGenerationException`, and `S3UploadException` following the pattern in `security/FeatureNotAvailableException.java` and `room/RoomNotFoundException.java`.

---

## 4. Next Steps Checklist

Before you begin coding, ensure you:

1. ✓ Read and understand the `ReportingService.enqueueExportJob()` method to know the exact job message format
2. ✓ Add Maven dependencies: commons-csv, pdfbox (or itext), quarkus-amazon-s3 (or AWS SDK S3)
3. ✓ Create database migration `V5__create_export_job_table.sql` with all required fields
4. ✓ Create `ExportJob` entity following the pattern in `PaymentHistory.java`
5. ✓ Create `JobStatus` enum with values: PENDING, PROCESSING, COMPLETED, FAILED
6. ✓ Add S3 configuration properties to `application.properties`
7. ✓ Study Redis Streams consumer API documentation for Quarkus
8. ✓ Plan the consumer loop lifecycle (startup event or scheduled task)

Then implement in this order:

1. **ExportJob entity and migration** (persistence layer)
2. **S3Adapter** (infrastructure integration)
3. **CsvExporter** (business logic)
4. **PdfExporter** (business logic)
5. **ExportJobProcessor** (orchestration and consumer loop)
6. **Error handling and retry logic**
7. **Integration tests** (use Testcontainers for Redis and LocalStack for S3)

Good luck! Remember to follow the reactive patterns, use structured logging, and handle errors gracefully with proper status transitions.
