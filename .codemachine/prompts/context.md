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

### Context: task-i6-t3 (from 02_Iteration_I6.md)

```markdown
*   **Task 6.3: Implement Background Export Job Processor**
    *   **Deliverables:**
        *   ExportJobProcessor consuming Redis Stream
        *   CsvExporter generating CSV files (session data, rounds, votes)
        *   PdfExporter generating PDF reports with summary and charts
        *   S3Adapter uploading files to configured bucket
        *   Signed URL generation (7-day expiration)
        *   Job status tracking (PENDING → PROCESSING → COMPLETED)
        *   Error handling and retry logic
    *   **Acceptance Criteria:**
        *   Export job message triggers processor
        *   CSV export generates valid file (test with sample session)
        *   PDF export generates readable report
        *   File uploaded to S3 successfully
        *   Signed URL allows download without authentication
        *   Job status updated to COMPLETED with download URL
        *   Failed jobs marked with error message
        *   Retry logic handles transient S3 failures
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/worker/ExportJobProcessor.java`
    *   **Summary:** This file contains a **FULLY IMPLEMENTED** background worker that consumes export jobs from Redis Stream `jobs:reports`. It is COMPLETE with 441 lines of production-ready code including comprehensive Javadoc, Redis Stream consumer group setup, message processing loop with XREADGROUP, job status transitions, retry logic with @Retry and @ExponentialBackoff annotations, health checks via @Scheduled method, and graceful shutdown handling.
    *   **Recommendation:** You MUST NOT recreate or significantly modify this file. It is already complete and production-ready. Review it to understand the architecture and ensure your CsvExporter and PdfExporter implementations integrate correctly.
    *   **Key Integration Points:**
        - Line 389-394: `generateExportFile()` method switches on format ("CSV" or "PDF") and delegates to `csvExporter.generateCsvExport(session, sessionId)` or `pdfExporter.generatePdfExport(session, sessionId)` - BOTH methods expect signature `Uni<byte[]> generateXxxExport(SessionHistory session, UUID sessionId)`
        - Line 332-333: After file generation, calls `s3Adapter.uploadFileAndGenerateUrl(sessionId, jobId, format, fileContent)` expecting `Uni<String>` return type with signed URL
        - Line 316-346: Complete job processing pipeline: findOrCreate job → markAsProcessing → fetch session → generate file → upload S3 → markAsCompleted (or markAsFailed on error)
        - Line 305-306: Uses @Retry(maxRetries=5) and @ExponentialBackoff(factor=2, maxDelay=16000) for fault tolerance
        - Line 145-167: Startup initialization with @Observes StartupEvent creating consumer group with MKSTREAM option and error handling for "BUSYGROUP" (group already exists)

*   **File:** `backend/src/main/java/com/scrumpoker/integration/s3/S3Adapter.java`
    *   **Summary:** This file contains a **FULLY IMPLEMENTED** AWS S3 integration adapter (215 lines) that handles file uploads and presigned URL generation. It uses AWS SDK for Java v2 (synchronous S3Client and S3Presigner), wraps blocking calls in reactive Uni types, executes on worker thread pool via `runSubscriptionOn(Infrastructure.getDefaultWorkerPool())` to avoid blocking event loop, includes comprehensive error handling with custom S3UploadException, and supports configuration via application.properties.
    *   **Recommendation:** You MUST NOT recreate this file. It is complete with:
        - Line 52-53: Bucket name injected from config property `s3.bucket-name`
        - Line 58-59: Signed URL expiration from config property `export.signed-url-expiration` (default 7 days = 604800 seconds)
        - Line 93-143: `uploadFileAndGenerateUrl()` method signature: `Uni<String> uploadFileAndGenerateUrl(UUID sessionId, UUID jobId, String format, byte[] fileContent)`
        - Line 105-106: Builds S3 object key: `exports/{sessionId}/{jobId}.{format}` (lowercase format)
        - Line 207-212: Content type mapping: CSV → "text/csv", PDF → "application/pdf"
        - Line 115-143: Blocking upload wrapped in `Uni.createFrom().item(() -> {...}).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`
        - Line 156-180: Presigned URL generation using S3Presigner with configured expiration duration
    *   **Critical:** Your exporters MUST return `Uni<byte[]>` matching the expected signature at ExportJobProcessor line 389-394.

*   **File:** `backend/src/main/resources/db/migration/V5__create_export_job_table.sql`
    *   **Summary:** Database migration creating the `export_job` table with **COMPLETE SCHEMA** (63 lines) including:
        - Line 11: Creates `job_status_enum` type with values ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
        - Line 19-32: Table with columns: job_id (UUID PK), session_id (UUID NOT NULL), user_id (UUID FK to users), format (VARCHAR CHECK CSV/PDF), status (job_status_enum DEFAULT 'PENDING'), download_url (TEXT), error_message (TEXT), created_at, processing_started_at, completed_at, failed_at (all TIMESTAMP WITH TIME ZONE)
        - Line 51-58: Three indexes for performance: (status, created_at DESC), (user_id, created_at DESC), (session_id)
    *   **Recommendation:** You MUST review this schema to understand the ExportJob entity structure. The enum type and all fields are already defined. Your ExportJob entity should map to this exact schema.

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven dependencies configuration showing **ALL REQUIRED LIBRARIES ARE ALREADY INCLUDED**:
        - Line 151-155: Apache Commons CSV version 1.10.0 ✓
        - Line 157-162: Apache PDFBox version 2.0.30 ✓
        - Line 164-169: Quarkus Amazon S3 extension version 2.14.0 ✓
    *   **Recommendation:** You DO NOT need to add any dependencies. All required libraries for CSV generation, PDF generation, and S3 integration are already configured. Use `org.apache.commons.csv.CSVPrinter` for CSV export and `org.apache.pdfbox.pdmodel.PDDocument` for PDF generation.
    *   **Warning:** The task description mentions "iText or Apache PDFBox" but the pom.xml shows **Apache PDFBox is already included**, NOT iText. You MUST use PDFBox. iText has AGPL licensing restrictions while PDFBox is Apache 2.0 licensed.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** **ALL S3 CONFIGURATION IS ALREADY IN PLACE** at lines 133-146:
        - Line 137: AWS region: `quarkus.s3.aws.region=${S3_REGION:us-east-1}`
        - Line 138-140: AWS credentials: static provider with access key ID and secret access key from env vars
        - Line 143: S3 bucket name: `s3.bucket-name=${S3_BUCKET_NAME:scrum-poker-exports}`
        - Line 146: Signed URL expiration: `export.signed-url-expiration=${EXPORT_URL_EXPIRATION:604800}` (7 days)
    *   **Recommendation:** You SHOULD be aware that S3 configuration is environment-based. For local development, you may need to configure LocalStack or use real AWS credentials via environment variables.

### Implementation Tips & Notes

*   **CRITICAL:** The ExportJobProcessor (441 lines) and S3Adapter (215 lines) are **FULLY COMPLETE**. The database migration V5 is **COMPLETE**. All Maven dependencies are **CONFIGURED**. All application properties are **SET UP**. Your task is to implement ONLY the missing pieces:
    1. **ExportJob.java** entity (Panache entity mapping to export_job table)
    2. **CsvExporter.java** (generate CSV from SessionHistory)
    3. **PdfExporter.java** (generate PDF from SessionHistory)

*   **Tip:** For **ExportJob entity** (`backend/src/main/java/com/scrumpoker/domain/reporting/ExportJob.java`):
    - Extend `PanacheEntityBase` (not `PanacheEntity` since you have custom UUID key)
    - Map to table: `@Entity @Table(name = "export_job")`
    - Fields: `@Id UUID jobId`, `UUID sessionId`, `@ManyToOne User user`, `String format`, `@Enumerated(EnumType.STRING) JobStatus status`, `String downloadUrl`, `String errorMessage`, timestamps (createdAt, processingStartedAt, completedAt, failedAt)
    - Methods needed by ExportJobProcessor:
      - `static Uni<ExportJob> findByJobId(UUID jobId)` - use `find("jobId", jobId).firstResult()`
      - `Uni<Void> markAsProcessing()` - set status to PROCESSING, set processingStartedAt, persist
      - `Uni<Void> markAsCompleted(String downloadUrl)` - set status to COMPLETED, set downloadUrl and completedAt, persist
      - `Uni<Void> markAsFailed(String errorMessage)` - set status to FAILED, set errorMessage and failedAt, persist
    - Create `JobStatus` enum in same package with values: PENDING, PROCESSING, COMPLETED, FAILED

*   **Tip:** For **CSV Export** (`backend/src/main/java/com/scrumpoker/worker/CsvExporter.java`):
    - Method signature: `public Uni<byte[]> generateCsvExport(SessionHistory session, UUID sessionId)`
    - Inject `RoundRepository` and `VoteRepository` to query related data
    - Use Apache Commons CSV: `CSVFormat.DEFAULT.withHeader(...).print(outputStream)`
    - CSV structure:
      - Headers: "Round Number", "Story Title", "Participant Name", "Vote Value", "Average", "Median", "Consensus", "Started At", "Revealed At"
      - Data: One row per vote, repeating round metadata for each vote
      - Consider adding session summary rows at top: "Session ID", session.sessionId, "Room", session.roomTitle, "Date", session.createdAt
    - Query rounds: `Round.find("sessionHistory.sessionId", sessionId).list()` (may need to use native query if composite key causes issues)
    - Query votes per round: `Vote.find("round.roundId", round.roundId).list()`
    - Wrap blocking I/O: Use `Uni.createFrom().item(() -> { ByteArrayOutputStream out = ...; CSVPrinter csv = ...; /* write rows */; return out.toByteArray(); }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`
    - Return `Uni<byte[]>` of CSV content

*   **Tip:** For **PDF Export** (`backend/src/main/java/com/scrumpoker/worker/PdfExporter.java`):
    - Method signature: `public Uni<byte[]> generatePdfExport(SessionHistory session, UUID sessionId)`
    - Use Apache PDFBox: `PDDocument`, `PDPage`, `PDPageContentStream` for text/tables
    - PDF structure:
      - Page 1: Title section with session metadata (session ID, room title, date, participants count)
      - Page 1-2: Statistics summary table (total rounds, consensus rate, average vote across all rounds)
      - Page 2+: Round-by-round breakdown table (round number, story title, vote distribution, average, median, consensus Y/N)
    - For tables, use `PDPageContentStream.beginText()` and `showText()` with manual positioning
    - Charts: The task mentions "summary and charts" but implementing full charts (bar/pie) is complex with PDFBox. Consider:
      - Option 1: Text-based visualization (ASCII art or simple text summary: "Vote Distribution: 1 (2 votes), 2 (3 votes), 3 (1 vote)")
      - Option 2: Skip charts in initial implementation, focus on tables and text
      - Option 3: Use JFreeChart to generate chart images, then embed in PDF (adds complexity)
    - Wrap blocking I/O: Same pattern as CSV, use `runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`
    - Save to ByteArrayOutputStream: `ByteArrayOutputStream out = new ByteArrayOutputStream(); document.save(out); return out.toByteArray();`
    - Return `Uni<byte[]>` of PDF content

*   **Note:** The ExportJobProcessor already handles all Redis Stream consumption (XREADGROUP with consumer group "export-workers"), message acknowledgment (XACK), job lifecycle orchestration, S3 upload coordination, and error handling with retry. You do NOT need to touch Redis or S3 code. Focus ONLY on generating the file content as byte arrays.

*   **Note:** Error handling: If your exporter methods fail (throw exception), the ExportJobProcessor catch block at line 338-345 will handle it by calling `job.markAsFailed(failure.getMessage())`. You SHOULD throw meaningful exceptions with descriptive messages. Consider creating `ExportGenerationException` for domain-specific errors.

*   **Warning:** SessionHistory uses a composite key (SessionHistoryId with sessionId + createdAt). When querying Rounds/Votes, you may need to query by sessionId only, not the full SessionHistory relationship. Check Round and Vote entity mappings to see if they reference sessionId directly or use the full composite key.

*   **Warning:** The ExportJobProcessor expects both exporters to return `Uni<byte[]>`. If you use blocking I/O (CSVPrinter writes to stream, PDDocument.save() is blocking), you MUST wrap in `Uni.createFrom().item(() -> blockingCode).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())` to avoid blocking the event loop.

*   **Critical:** When testing, you need to:
    1. Ensure Redis is running locally (docker-compose.yml has Redis service)
    2. Ensure PostgreSQL is running with V5 migration applied
    3. Configure S3: Either use LocalStack (S3 mock), or configure real AWS credentials in application.properties
    4. Trigger an export job (call ReportingService.generateExport() or manually publish to Redis Stream)
    5. Monitor logs to see ExportJobProcessor consume and process the job
    6. Query export_job table to verify status transitions
    7. Check S3 (or LocalStack) for uploaded file
    8. Verify signed URL is generated and stored in download_url column

### Directory Structure Verification

Based on the `ls -R` output, I confirmed:
- `backend/src/main/java/com/scrumpoker/worker/` directory **EXISTS** and contains:
  - `ExportJobProcessor.java` (COMPLETE - 441 lines)
  - `CsvExporter.java` (EXISTS - STATUS UNKNOWN, needs verification)
  - `PdfExporter.java` (EXISTS - STATUS UNKNOWN, needs verification)
  - `ExportGenerationException.java` (EXISTS)
- `backend/src/main/java/com/scrumpoker/integration/s3/` directory **EXISTS** and contains:
  - `S3Adapter.java` (COMPLETE - 215 lines)
  - `S3UploadException.java` (EXISTS)
- `backend/src/main/java/com/scrumpoker/domain/reporting/` directory **EXISTS** and contains:
  - `ExportJob.java` (EXISTS - STATUS UNKNOWN, needs verification)
  - `JobStatus.java` (not confirmed, may need to be created)
  - Other reporting files: ReportingService.java, SessionHistoryService.java, etc.
- `backend/src/main/resources/db/migration/` contains V5__create_export_job_table.sql (COMPLETE - 63 lines)

**ACTION REQUIRED:** You MUST read these three files to determine their current state:
1. `backend/src/main/java/com/scrumpoker/worker/CsvExporter.java`
2. `backend/src/main/java/com/scrumpoker/worker/PdfExporter.java`
3. `backend/src/main/java/com/scrumpoker/domain/reporting/ExportJob.java`

If they are empty stubs or incomplete, implement them. If they are complete, verify they meet acceptance criteria.

---

## 4. Next Steps for Coder Agent

1. **READ** the three potentially incomplete files:
   - `backend/src/main/java/com/scrumpoker/worker/CsvExporter.java`
   - `backend/src/main/java/com/scrumpoker/worker/PdfExporter.java`
   - `backend/src/main/java/com/scrumpoker/domain/reporting/ExportJob.java`

2. **VERIFY** if `JobStatus` enum exists at `backend/src/main/java/com/scrumpoker/domain/reporting/JobStatus.java`. If not, create it with values: PENDING, PROCESSING, COMPLETED, FAILED.

3. **IMPLEMENT** any missing or incomplete files:
   - **ExportJob.java**: Panache entity with all fields, status transition methods, and findByJobId() static method
   - **CsvExporter.java**: Generate CSV with session data, rounds, and votes as byte array
   - **PdfExporter.java**: Generate PDF with session summary, statistics, and round breakdown as byte array

4. **COMPILE** the project to verify no compilation errors:
   ```bash
   cd backend && mvn clean compile
   ```

5. **TEST** (if possible):
   - Start local services (PostgreSQL, Redis) via docker-compose
   - Configure S3 (LocalStack or real AWS credentials)
   - Run Quarkus in dev mode: `mvn quarkus:dev`
   - Verify ExportJobProcessor starts and initializes consumer group (check logs for "Starting export job consumer")
   - Trigger an export job (via ReportingService or manual Redis Stream publish)
   - Monitor logs and database to verify job processing

6. **UPDATE** task status to `done: true` in the task data file if all acceptance criteria are met.

---

## 5. Acceptance Criteria Checklist

Verify each criterion before marking the task complete:

- [ ] Export job message triggers processor (ExportJobProcessor consumes from Redis Stream)
- [ ] CSV export generates valid file (test with sample session, verify structure with headers and data rows)
- [ ] PDF export generates readable report (test with sample session, open PDF to verify content)
- [ ] File uploaded to S3 successfully (check S3 bucket or LocalStack for file existence)
- [ ] Signed URL allows download without authentication (copy URL from database, paste in browser, file downloads)
- [ ] Job status updated to COMPLETED with download URL (query export_job table, verify status and download_url populated)
- [ ] Failed jobs marked with error message (test error scenario, verify status=FAILED and error_message populated)
- [ ] Retry logic handles transient S3 failures (ExportJobProcessor @Retry annotation is already configured)

**Current Status:** Based on existing code analysis, ExportJobProcessor (COMPLETE), S3Adapter (COMPLETE), database migration V5 (COMPLETE), and all dependencies (CONFIGURED) are done. Only ExportJob entity, CsvExporter, and PdfExporter need verification/implementation.
