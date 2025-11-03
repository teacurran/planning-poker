# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I6.T4",
  "iteration_id": "I6",
  "iteration_goal": "Implement session history tracking, tier-based reporting (basic summaries for Free, detailed analytics for Pro/Enterprise), export functionality (CSV/PDF), and frontend reporting UI.",
  "description": "Implement REST endpoints for reporting per OpenAPI spec. Endpoints: `GET /api/v1/reports/sessions` (list user's sessions with pagination), `GET /api/v1/reports/sessions/{sessionId}` (get session report, tier-gated detail level), `POST /api/v1/reports/export` (create export job, returns job ID), `GET /api/v1/jobs/{jobId}` (poll export job status, returns download URL when complete). Use `ReportingService`. Return pagination metadata (total count, page, size). Enforce authorization (user can only access own sessions or rooms they participated in).",
  "agent_type_hint": "BackendAgent",
  "inputs": "OpenAPI spec for reporting endpoints from I2.T1, ReportingService from I6.T2",
  "input_files": [
    "api/openapi.yaml",
    "backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/ReportingController.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/SessionListResponse.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/ExportJobResponse.java"
  ],
  "deliverables": "ReportingController with 4 endpoints, Pagination support (query params: page, size, sort), Session list endpoint with metadata (total, hasNext), Session detail endpoint with tier-based response, Export endpoint enqueuing job and returning job ID, Job status endpoint returning status and download URL",
  "acceptance_criteria": "GET /reports/sessions returns paginated list (default 20 per page), GET /reports/sessions/{id} returns basic summary for Free tier, Pro tier user gets detailed report with round breakdown, POST /reports/export creates job and returns job ID, GET /jobs/{jobId} returns PENDING while processing, COMPLETED with URL when done, Unauthorized access to other user's sessions returns 403",
  "dependencies": [
    "I6.T2",
    "I6.T3"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: rest-api-endpoints (from 04_Behavior_and_Communication.md)

```markdown
#### REST API Endpoints Overview

**Reporting & Analytics:**
- `GET /api/v1/reports/sessions` - List session history (tier-gated pagination, filters)
- `GET /api/v1/reports/sessions/{sessionId}` - Detailed session report (tier-gated round detail)
- `POST /api/v1/reports/export` - Generate export job (CSV/PDF), returns job ID
- `GET /api/v1/jobs/{jobId}` - Poll export job status, retrieve download URL
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
- `GET /api/v1/reports/sessions?from=2025-01-01&to=2025-01-31` - Query session history
```

### Context: asynchronous-job-processing-pattern (from 04_Behavior_and_Communication.md)

```markdown
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

### Context: reporting-requirements (from 01_Context_and_Drivers.md)

```markdown
#### Reporting Requirements
- **Free Tier:** Basic session summaries (story count, consensus rate, average vote)
- **Pro Tier:** Round-level detail, user consistency metrics, CSV/JSON/PDF export
- **Enterprise Tier:** Organizational dashboards, team trends, SSO-filtered reports, audit logs
```

### Context: performance-nfrs (from 01_Context_and_Drivers.md)

```markdown
#### Performance
- **Latency:** <200ms round-trip time for WebSocket messages within region
- **Throughput:** Support 500 concurrent sessions with 6,000 active WebSocket connections
- **Response Time:** REST API endpoints respond within <500ms for p95
- **Real-time Updates:** State synchronization across clients within 100ms
```

### Context: task-i6-t4 (from 02_Iteration_I6.md)

```markdown
*   **Task 6.4: Create Reporting REST Controllers**
    *   **Acceptance Criteria:**
        *   GET /reports/sessions returns paginated list (default 20 per page)
        *   GET /reports/sessions/{id} returns basic summary for Free tier
        *   Pro tier user gets detailed report with round breakdown
        *   POST /reports/export creates job and returns job ID
        *   GET /jobs/{jobId} returns PENDING while processing, COMPLETED with URL when done
        *   Unauthorized access to other user's sessions returns 403
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java`
    *   **Summary:** This is the core service you MUST inject and use. It provides three critical methods:
        - `getBasicSessionSummary(UUID sessionId)` - Returns `Uni<SessionSummaryDTO>` for Free tier users (no tier enforcement at this level)
        - `getDetailedSessionReport(UUID sessionId, User user)` - Returns `Uni<DetailedSessionReportDTO>` for Pro tier users (enforces tier via FeatureGate internally)
        - `generateExport(UUID sessionId, String format, User user)` - Returns `Uni<String>` (jobId as String, not UUID) for Pro tier users (enforces tier via FeatureGate internally)
    *   **Recommendation:** You MUST use these exact methods in your controller. Do NOT attempt to bypass the service layer or call repositories directly. The service handles all tier enforcement and business logic.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java`
    *   **Summary:** This service handles session history queries with proper partition optimization for PostgreSQL. It exposes:
        - `getUserSessions(UUID userId, Instant from, Instant to)` - Returns `Uni<List<SessionHistory>>` with partition-optimized queries
        - `getSessionById(UUID sessionId)` - Returns `Uni<SessionHistory>` (may scan multiple partitions)
    *   **Recommendation:** You MUST inject this service for the session list endpoint. Use `getUserSessions()` for date-range queries to leverage partition pruning. The service already returns reactive `Uni<>` types compatible with your controller methods.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/SessionListResponse.java`
    *   **Summary:** This DTO is ALREADY created and ready to use. It contains:
        - `List<SessionSummaryDTO> sessions` - The session summaries for the current page
        - `int page` - Current page number (0-indexed)
        - `int size` - Page size (number of items per page)
        - `int total` - Total number of sessions matching the query
        - `boolean hasNext` - Whether there are more pages available
    *   **Recommendation:** You MUST import and use this exact class for the session list endpoint response. Do NOT create a new DTO class. The file already exists at line 1-70.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/ExportJobResponse.java`
    *   **Summary:** This DTO is ALREADY created and ready to use. It contains:
        - `UUID jobId` - Unique job identifier for polling status
    *   **Recommendation:** You MUST import and use this exact class for the export endpoint response. Do NOT create a new DTO class. The file already exists at line 1-33.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/JobStatusResponse.java`
    *   **Summary:** This DTO is ALREADY created and ready to use. It contains:
        - `UUID jobId` - Job identifier
        - `String status` - Job status (PENDING, PROCESSING, COMPLETED, FAILED)
        - `String downloadUrl` - Download URL when completed (nullable)
        - `String errorMessage` - Error message when failed (nullable)
        - `Instant createdAt` - Job creation timestamp
        - `Instant completedAt` - Job completion timestamp (nullable)
    *   **Recommendation:** You MUST import and use this exact class for the job status endpoint response. Do NOT create a new DTO class. The file already exists at line 1-84.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/ExportJob.java`
    *   **Summary:** This is the Panache entity for export jobs. It provides static query methods:
        - `ExportJob.findByJobId(UUID jobId)` - Returns `Uni<ExportJob>` (see line 169-171)
        - `ExportJob.findByUserId(UUID userId)` - Returns `Uni<List<ExportJob>>` (see line 179-181)
        - Important fields: jobId (UUID), sessionId (UUID), user (User entity), format (String), status (JobStatus enum), downloadUrl (String), errorMessage (String), createdAt/completedAt timestamps
    *   **Recommendation:** You MUST use `ExportJob.findByJobId()` in the job status endpoint to retrieve job details from the database. For the export endpoint, you need to create a NEW ExportJob entity and persist it.

*   **File:** `backend/src/main/java/com/scrumpoker/security/SecurityContextImpl.java`
    *   **Summary:** This is the CRITICAL service for extracting the authenticated user from the security context. It provides:
        - `getCurrentUserId()` - Returns `UUID` of authenticated user (see line 87-90)
        - `getCurrentClaims()` - Returns full `JwtClaims` object (see line 139-159)
        - `isCurrentUser(UUID resourceUserId)` - Validates user owns a resource (see line 206-212)
    *   **Recommendation:** You MUST inject this service and use it to:
        1. Get the current user's ID for querying their sessions
        2. Validate authorization (user accessing only their own sessions)
        3. Retrieve the User object from UserRepository using the userId

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** This is an EXISTING controller demonstrating the exact patterns you should follow:
        - Uses `@Path("/api/v1")` for base path (line 33)
        - Uses JAX-RS annotations (`@GET`, `@POST`, `@Path`, `@PathParam`, `@QueryParam`)
        - Returns reactive `Uni<Response>` types (line 57, 93)
        - Uses OpenAPI annotations (`@Operation`, `@APIResponse`)
        - Injects services and mappers using `@Inject` (line 39-42)
    *   **Recommendation:** You SHOULD copy this controller's structure, package declaration, imports, and annotation patterns for consistency.

### Implementation Tips & Notes

*   **Tip:** The session list endpoint MUST implement pagination. For this task, you should:
    1. Accept query parameters: `from`, `to`, `page` (default 0), `size` (default 20), `sort` (default "desc")
    2. Parse `from` and `to` as ISO date strings and convert to `Instant` (use `LocalDate.parse().atStartOfDay(ZoneOffset.UTC).toInstant()`)
    3. Call `sessionHistoryService.getUserSessions(userId, from, to)` to get ALL sessions in date range
    4. Implement manual pagination by slicing the list: `sessions.subList(skip, Math.min(skip + size, sessions.size()))`
    5. Calculate `total` count and `hasNext` flag: `hasNext = (page + 1) * size < total`
    6. For each SessionHistory in the page, call `reportingService.getBasicSessionSummary(sessionHistory.id.sessionId)` to get the DTO
    7. Collect all DTOs using `Multi.createFrom().iterable()` and `transformToUniAndConcatenate()`

*   **Tip:** For the session detail endpoint, you MUST:
    1. Extract the authenticated user from SecurityContextImpl: `UUID userId = securityContext.getCurrentUserId()`
    2. Fetch the User entity from UserRepository: `userRepository.findById(userId)`
    3. Call `reportingService.getDetailedSessionReport(sessionId, user)` which will:
        - Return detailed report for Pro/Enterprise users
        - Throw `FeatureNotAvailableException` for Free tier users (which becomes 403 via exception mapper)
    4. The service ALREADY checks tiers internally, so you do NOT need manual tier checking

*   **Tip:** For the export job endpoint, you MUST:
    1. Accept a JSON request body with `sessionId` (UUID) and `format` (String) fields
    2. Create a DTO class `ExportRequest` in the dto package to parse this body (has fields: `@JsonProperty("session_id") UUID sessionId` and `@JsonProperty("format") String format`)
    3. Extract the authenticated user from SecurityContextImpl and fetch User entity
    4. Call `reportingService.generateExport(sessionId, format, user)` which returns a String jobId
    5. Parse the String jobId as UUID: `UUID jobUuid = UUID.fromString(jobId)`
    6. Create an ExportJob entity: `ExportJob job = new ExportJob(); job.jobId = jobUuid; job.sessionId = sessionId; job.user = user; job.format = format; job.status = JobStatus.PENDING; job.persist()`
    7. Return `202 Accepted` with `ExportJobResponse(jobUuid)`

*   **Tip:** For the job status endpoint, you MUST:
    1. Parse the jobId from the path parameter as UUID
    2. Call `ExportJob.findByJobId(jobId)` to retrieve the job
    3. If job is null, return 404 Not Found: `Response.status(404).entity(new ErrorResponse(...)).build()`
    4. Map the ExportJob entity to JobStatusResponse: `new JobStatusResponse(job.jobId, job.status.name(), job.downloadUrl, job.errorMessage, job.createdAt, job.completedAt)`
    5. Return 200 OK with the response

*   **Warning:** The ReportingService methods for detailed reports and exports throw `FeatureNotAvailableException` if the user lacks the required tier. You MUST let this exception propagate to the exception mapper (found in `backend/src/main/java/com/scrumpoker/api/rest/exception/FeatureNotAvailableExceptionMapper.java`), which will convert it to a 403 Forbidden response. Do NOT catch this exception in the controller.

*   **Warning:** You MUST enforce authorization for session access. A user should only be able to:
    1. List their own sessions (sessions where their userId matches the query)
    2. View session details for their own sessions
    3. Export their own sessions
    For this iteration, you can enforce this by ONLY querying sessions for the authenticated user's ID (from SecurityContextImpl). Do NOT allow userId as a query parameter.

*   **Note:** The project uses Hibernate Reactive Panache with reactive Mutiny patterns. All controller methods MUST return `Uni<Response>` for non-blocking I/O. Use `.onItem().transform()` for synchronous transformations and `.onItem().transformToUni()` for async operations that return Uni.

*   **Note:** All endpoints MUST be secured with JWT authentication. Use `@RolesAllowed("USER")` annotation on controller class or methods that require authentication. The JwtAuthenticationFilter will automatically validate tokens and populate the SecurityContext.

*   **Note:** For the export endpoint, you need to create a new DTO file `ExportRequest.java` in the dto package because it doesn't exist yet. It should have:
    - `@JsonProperty("session_id") public UUID sessionId`
    - `@JsonProperty("format") public String format`
    - Standard constructors (default + all-args)

*   **Note:** Exception handling is already configured via exception mappers in `backend/src/main/java/com/scrumpoker/api/rest/exception/`. You do NOT need to manually catch exceptions for standard error cases (IllegalArgumentException, FeatureNotAvailableException). Let them propagate to the mappers.

*   **Note:** The UserRepository is a Panache repository that provides `findById(UUID userId)` returning `Uni<User>`. You MUST use this to fetch the authenticated user entity after extracting the userId from SecurityContextImpl.

*   **Note:** When converting SessionHistory entities to SessionSummaryDTOs, you MUST call `reportingService.getBasicSessionSummary()` for EACH session in the paginated list. Use `Multi.createFrom().iterable(paginatedSessions).onItem().transformToUniAndConcatenate(session -> reportingService.getBasicSessionSummary(session.id.sessionId)).collect().asList()` to process them reactively in parallel.

*   **Reminder:** Your controller MUST be placed at `backend/src/main/java/com/scrumpoker/api/rest/ReportingController.java` as specified in the task target files. Follow the exact package structure `package com.scrumpoker.api.rest;`.

*   **Reminder:** The DTOs `SessionListResponse`, `ExportJobResponse`, and `JobStatusResponse` ALREADY EXIST. Do NOT recreate them. Import them from `com.scrumpoker.api.rest.dto` package.
