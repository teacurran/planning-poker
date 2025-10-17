# Project Plan: Scrum Poker Platform - Iteration 6

**Version:** 1.0
**Date:** 2025-10-17

---

<!-- anchor: iteration-6 -->
### Iteration 6: Reporting & Analytics

*   **Iteration ID:** `I6`

*   **Goal:** Implement session history tracking, tier-based reporting (basic summaries for Free, detailed analytics for Pro/Enterprise), export functionality (CSV/PDF), and frontend reporting UI.

*   **Prerequisites:** I4 (SessionHistory data from voting), I5 (tier enforcement)

*   **Tasks:**

<!-- anchor: task-i6-t1 -->
*   **Task 6.1: Implement Session History Tracking**
    *   **Task ID:** `I6.T1`
    *   **Description:** Extend `VotingService` to persist session summary data. When round completes (reveal called), update SessionHistory record with round count, participants JSONB array, summary stats (total votes, consensus rate). Create `SessionHistoryService` for querying past sessions: `getUserSessions(userId, from, to)` (date range), `getSessionById(sessionId)`, `getRoomSessions(roomId)`. Aggregate statistics: total rounds, average consensus rate, most active participants. Use `SessionHistoryRepository`. Handle partitioned table queries (specify partition key in WHERE clause).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   SessionHistory entity from I1
        *   Voting flow completion points
        *   Partitioning strategy (monthly partitions)
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/room/SessionHistory.java`
        *   `backend/src/main/java/com/scrumpoker/repository/SessionHistoryRepository.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java` (extend reveal method)
    *   **Deliverables:**
        *   SessionHistoryService with query methods (getUserSessions, getSessionById, getRoomSessions)
        *   VotingService.revealRound extended to update SessionHistory summary
        *   Session summary statistics (total rounds, consensus rate, participants)
        *   Partitioned table query optimization (partition pruning)
    *   **Acceptance Criteria:**
        *   Completing round updates SessionHistory record with round count
        *   getUserSessions returns user's past sessions within date range
        *   Session statistics correctly aggregated (consensus rate calculation)
        *   Queries use partition pruning (verify EXPLAIN plan)
        *   SessionHistory JSONB fields (participants, summary_stats) populated correctly
    *   **Dependencies:** [I4.T3]
    *   **Parallelizable:** No (depends on VotingService)

<!-- anchor: task-i6-t2 -->
*   **Task 6.2: Implement Reporting Service (Tier-Based Access)**
    *   **Task ID:** `I6.T2`
    *   **Description:** Create `ReportingService` implementing tier-gated analytics. Methods: `getBasicSessionSummary(sessionId)` (Free tier: story count, consensus rate, average vote), `getDetailedSessionReport(sessionId)` (Pro tier: round-by-round breakdown, individual votes, user consistency metrics), `generateExport(sessionId, format)` (Pro tier: enqueue export job for CSV/PDF generation). Inject `FeatureGate` to enforce tier requirements. Query SessionHistory and Round/Vote entities. Calculate user consistency (standard deviation of user's votes across rounds). Return tier-appropriate DTOs.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Reporting tier matrix from product spec (Free vs. Pro features)
        *   SessionHistoryService from I6.T1
        *   FeatureGate from I5.T4
    *   **Input Files:**
        *   Product specification (reporting feature comparison)
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java`
        *   `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/SessionSummaryDTO.java`
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/DetailedSessionReportDTO.java`
    *   **Deliverables:**
        *   ReportingService with tier-gated methods
        *   Basic summary for Free tier (limited fields)
        *   Detailed report for Pro tier (round breakdown, individual votes)
        *   User consistency metrics (vote variance calculation)
        *   Export job enqueuing (Redis Stream message)
        *   FeatureGate enforcement (403 if Free tier requests detailed report)
    *   **Acceptance Criteria:**
        *   getBasicSessionSummary returns story count, consensus rate
        *   Free tier user cannot access detailed report (403 error)
        *   Pro tier user gets detailed report with round-by-round data
        *   User consistency calculated correctly (standard deviation of votes)
        *   Export job enqueued to Redis Stream
        *   Tier enforcement integrated via FeatureGate
    *   **Dependencies:** [I6.T1, I5.T4]
    *   **Parallelizable:** No (depends on SessionHistoryService, FeatureGate)

<!-- anchor: task-i6-t3 -->
*   **Task 6.3: Implement Background Export Job Processor**
    *   **Task ID:** `I6.T3`
    *   **Description:** Create background worker consuming export jobs from Redis Stream. `ExportJobProcessor` listens to `jobs:reports` stream, processes job (query session data, generate CSV or PDF), upload to S3 bucket, update job status in database (JobStatus: PENDING → PROCESSING → COMPLETED/FAILED), generate time-limited signed URL for download. Use Apache Commons CSV for CSV generation, iText or Apache PDFBox for PDF. Handle errors (mark job FAILED, store error message). Implement exponential backoff retry for transient failures.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Export requirements (CSV/PDF formats)
        *   Redis Streams consumer patterns
        *   S3 upload and signed URL generation
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java` (job enqueue)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/worker/ExportJobProcessor.java`
        *   `backend/src/main/java/com/scrumpoker/worker/CsvExporter.java`
        *   `backend/src/main/java/com/scrumpoker/worker/PdfExporter.java`
        *   `backend/src/main/java/com/scrumpoker/integration/s3/S3Adapter.java`
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/ExportJob.java` (entity)
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
    *   **Dependencies:** [I6.T2]
    *   **Parallelizable:** Yes (can work parallel with API tasks)

<!-- anchor: task-i6-t4 -->
*   **Task 6.4: Create Reporting REST Controllers**
    *   **Task ID:** `I6.T4`
    *   **Description:** Implement REST endpoints for reporting per OpenAPI spec. Endpoints: `GET /api/v1/reports/sessions` (list user's sessions with pagination), `GET /api/v1/reports/sessions/{sessionId}` (get session report, tier-gated detail level), `POST /api/v1/reports/export` (create export job, returns job ID), `GET /api/v1/jobs/{jobId}` (poll export job status, returns download URL when complete). Use `ReportingService`. Return pagination metadata (total count, page, size). Enforce authorization (user can only access own sessions or rooms they participated in).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   OpenAPI spec for reporting endpoints from I2.T1
        *   ReportingService from I6.T2
    *   **Input Files:**
        *   `api/openapi.yaml` (reporting endpoints)
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/ReportingController.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/SessionListResponse.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/ExportJobResponse.java`
    *   **Deliverables:**
        *   ReportingController with 4 endpoints
        *   Pagination support (query params: page, size, sort)
        *   Session list endpoint with metadata (total, hasNext)
        *   Session detail endpoint with tier-based response
        *   Export endpoint enqueuing job and returning job ID
        *   Job status endpoint returning status and download URL
    *   **Acceptance Criteria:**
        *   GET /reports/sessions returns paginated list (default 20 per page)
        *   GET /reports/sessions/{id} returns basic summary for Free tier
        *   Pro tier user gets detailed report with round breakdown
        *   POST /reports/export creates job and returns job ID
        *   GET /jobs/{jobId} returns PENDING while processing, COMPLETED with URL when done
        *   Unauthorized access to other user's sessions returns 403
    *   **Dependencies:** [I6.T2, I6.T3]
    *   **Parallelizable:** No (depends on services)

<!-- anchor: task-i6-t5 -->
*   **Task 6.5: Create Frontend Session History Page**
    *   **Task ID:** `I6.T5`
    *   **Description:** Implement `SessionHistoryPage` component displaying user's past sessions. List sessions with: date, room title, round count, participants count, consensus rate. Pagination controls (previous/next, page numbers). Click session to navigate to detail page. Filter by date range (date picker). Sort by date (newest/oldest). Use `useSessions` React Query hook for data fetching. Display loading skeleton, empty state (no sessions), error state.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   Reporting API endpoints from I6.T4
        *   Session list requirements
    *   **Input Files:**
        *   `api/openapi.yaml` (reporting endpoints)
    *   **Target Files:**
        *   `frontend/src/pages/SessionHistoryPage.tsx`
        *   `frontend/src/components/reporting/SessionListTable.tsx`
        *   `frontend/src/components/reporting/PaginationControls.tsx`
        *   `frontend/src/services/reportingApi.ts` (React Query hooks)
    *   **Deliverables:**
        *   SessionHistoryPage with session list table
        *   Pagination controls (previous, next, page numbers)
        *   Date range filter (date picker inputs)
        *   Sort controls (newest/oldest)
        *   Session row click navigates to detail page
        *   Loading, empty, and error states
    *   **Acceptance Criteria:**
        *   Page loads user's sessions from API
        *   Table displays session metadata (date, room, rounds, participants)
        *   Pagination works (clicking next loads next page)
        *   Date filter applies (API called with from/to params)
        *   Sort changes order (newest first vs. oldest first)
        *   Clicking session navigates to /reports/sessions/{sessionId}
        *   Empty state shows "No sessions found" message
    *   **Dependencies:** [I6.T4]
    *   **Parallelizable:** No (depends on API)

<!-- anchor: task-i6-t6 -->
*   **Task 6.6: Create Frontend Session Detail Page (Tier-Gated)**
    *   **Task ID:** `I6.T6`
    *   **Description:** Implement `SessionDetailPage` component showing session report details. Free tier view: summary card (story count, consensus rate, average vote, participants list). Pro tier view: round-by-round table (story title, individual votes, average, median, consensus indicator), user consistency chart (bar chart showing each user's vote variance), export buttons (CSV, PDF). Use `useSessionDetail` hook fetching tier-appropriate data. Display UpgradeModal if Free tier user tries to view detailed data (403 response). Show download link when export job completes.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   Reporting tier features from product spec
        *   ReportingService detail levels
    *   **Input Files:**
        *   `api/openapi.yaml` (session detail endpoint)
        *   `frontend/src/components/subscription/UpgradeModal.tsx`
    *   **Target Files:**
        *   `frontend/src/pages/SessionDetailPage.tsx`
        *   `frontend/src/components/reporting/SessionSummaryCard.tsx`
        *   `frontend/src/components/reporting/RoundBreakdownTable.tsx`
        *   `frontend/src/components/reporting/UserConsistencyChart.tsx`
        *   `frontend/src/components/reporting/ExportControls.tsx`
    *   **Deliverables:**
        *   SessionDetailPage with tier-based content
        *   Summary card for Free tier (basic stats)
        *   Round breakdown table for Pro tier (detailed data)
        *   User consistency chart (Recharts bar chart)
        *   Export buttons (CSV, PDF) triggering export API
        *   Export job polling (check status every 2 seconds)
        *   Download link appears when job completes
        *   UpgradeModal on 403 error (Free tier trying to access Pro data)
    *   **Acceptance Criteria:**
        *   Free tier user sees summary card only
        *   Pro tier user sees round breakdown table and chart
        *   Export CSV button creates job, polls status, shows download link
        *   Download link opens exported CSV file
        *   PDF export works similarly
        *   403 error triggers UpgradeModal
        *   Chart displays user consistency correctly (variance bars)
    *   **Dependencies:** [I6.T5]
    *   **Parallelizable:** No (depends on session list page patterns)

<!-- anchor: task-i6-t7 -->
*   **Task 6.7: Write Unit Tests for Reporting Service**
    *   **Task ID:** `I6.T7`
    *   **Description:** Create unit tests for `ReportingService` with mocked repositories and FeatureGate. Test scenarios: basic summary generation (verify correct stats calculated), detailed report generation (verify round breakdown included), tier enforcement (Free tier accessing detailed report throws exception), user consistency calculation (test with known vote values), export job enqueuing (verify Redis Stream message). Use AssertJ for fluent assertions.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   ReportingService from I6.T2
        *   Mockito testing patterns
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/domain/reporting/ReportingServiceTest.java`
    *   **Deliverables:**
        *   ReportingServiceTest with 12+ test methods
        *   Tests for summary generation (Free tier)
        *   Tests for detailed report (Pro tier)
        *   Tier enforcement tests (403 for Free tier)
        *   Consistency calculation tests (variance formula)
        *   Export job enqueue tests
    *   **Acceptance Criteria:**
        *   `mvn test` runs reporting tests successfully
        *   Summary generation test verifies correct consensus rate
        *   Detailed report test includes round breakdown
        *   Tier enforcement test throws FeatureNotAvailableException
        *   Consistency calculation test verifies formula (σ²)
        *   Export enqueue test verifies Redis message published
    *   **Dependencies:** [I6.T2]
    *   **Parallelizable:** Yes (can work parallel with frontend tasks)

<!-- anchor: task-i6-t8 -->
*   **Task 6.8: Write Integration Tests for Export Job Processing**
    *   **Task ID:** `I6.T8`
    *   **Description:** Create integration test for export job end-to-end flow. Test: trigger export API, verify job enqueued to Redis Stream, worker processes job, CSV/PDF generated, file uploaded to S3 (use LocalStack or S3Mock), job status updated to COMPLETED, download URL returned. Test error scenario (S3 upload failure, job marked FAILED). Use Testcontainers for Redis and PostgreSQL.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   ExportJobProcessor from I6.T3
        *   Redis Streams testing patterns
        *   S3 mocking (LocalStack or S3Mock)
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/worker/ExportJobProcessor.java`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/worker/ExportJobIntegrationTest.java`
    *   **Deliverables:**
        *   Integration test for export flow
        *   Test: job enqueued → worker processes → file uploaded → status updated
        *   Test: S3 failure → job marked FAILED
        *   LocalStack or S3Mock for S3 testing
        *   Assertions on job status transitions
    *   **Acceptance Criteria:**
        *   `mvn verify` runs export integration test
        *   Export job processes successfully
        *   CSV file uploaded to mock S3
        *   Job status transitions: PENDING → PROCESSING → COMPLETED
        *   Download URL generated and accessible
        *   Failure test marks job FAILED with error message
    *   **Dependencies:** [I6.T3]
    *   **Parallelizable:** No (depends on worker implementation)

---

**Iteration 6 Summary:**

*   **Deliverables:**
    *   Session history tracking during voting
    *   ReportingService with tier-based access control
    *   Background export job processor (CSV/PDF generation)
    *   Reporting REST API endpoints with pagination
    *   Frontend session history and detail pages
    *   Export functionality with job status polling
    *   Unit and integration tests for reporting

*   **Acceptance Criteria (Iteration-Level):**
    *   Past sessions tracked and queryable
    *   Free tier users see basic summaries
    *   Pro tier users see detailed round breakdowns
    *   Export jobs generate CSV/PDF files
    *   Download links provided after export completes
    *   Tier enforcement prevents unauthorized report access
    *   Tests verify reporting logic and export processing

*   **Estimated Duration:** 2 weeks
