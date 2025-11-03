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

### Context: API Design & REST Communication Patterns (from 04_Behavior_and_Communication.md)

```markdown
<!-- anchor: api-design-and-communication -->
### 3.7. API Design & Communication

<!-- anchor: api-style -->
#### API Style

**Primary API Style:** **RESTful JSON API (OpenAPI 3.1 Specification)**

**Rationale:**
- **Simplicity & Familiarity:** REST over HTTPS provides a well-understood contract for CRUD operations on resources (users, rooms, subscriptions)
- **Tooling Ecosystem:** OpenAPI specification enables automatic client SDK generation (TypeScript for React frontend), API documentation (Swagger UI), and contract testing
- **Caching Support:** HTTP semantics (ETags, Cache-Control headers) enable browser and CDN caching for read-heavy endpoints (room configurations, user profiles)
- **Versioning Strategy:** URL-based versioning (`/api/v1/`) for backward compatibility during iterative releases

<!-- anchor: synchronous-rest-pattern -->
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

### Context: OpenAPI Specification for Reporting Endpoints (from openapi.yaml)

```yaml
/api/v1/reports/sessions:
  get:
    tags:
      - Reports
    summary: List session history
    description: |
      Returns paginated session history with filters.
      **Tier Requirements:**
      - Free tier: Last 30 days, max 10 results
      - Pro tier: Last 90 days, max 100 results
      - Pro Plus/Enterprise: Unlimited history
    operationId: listSessions
    parameters:
      - name: from
        in: query
        schema:
          type: string
          format: date
        description: Start date (ISO 8601 format)
        example: "2025-01-01"
      - name: to
        in: query
        schema:
          type: string
          format: date
        description: End date (ISO 8601 format)
        example: "2025-01-31"
      - name: roomId
        in: query
        schema:
          type: string
          pattern: '^[a-z0-9]{6}$'
        description: Filter by room ID
        example: "abc123"
      - $ref: '#/components/parameters/PageParam'
      - $ref: '#/components/parameters/SizeParam'
    responses:
      '200':
        description: Session list retrieved
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/SessionListResponse'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '403':
        description: Insufficient subscription tier
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ErrorResponse'
      '500':
        $ref: '#/components/responses/InternalServerError'

/api/v1/reports/sessions/{sessionId}:
  get:
    tags:
      - Reports
    summary: Get detailed session report
    description: |
      Returns detailed session report including all rounds and votes.
      **Tier Requirements:**
      - Free tier: Summary only (average, median)
      - Pro tier and above: Full round-by-round detail with individual votes
    operationId: getSessionReport
    parameters:
      - name: sessionId
        in: path
        required: true
        schema:
          type: string
          format: uuid
        description: Session ID
        example: "123e4567-e89b-12d3-a456-426614174000"
    responses:
      '200':
        description: Session report retrieved
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/SessionDetailDTO'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '403':
        description: Insufficient subscription tier for detailed report
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ErrorResponse'
      '404':
        $ref: '#/components/responses/NotFound'
      '500':
        $ref: '#/components/responses/InternalServerError'

/api/v1/reports/export:
  post:
    tags:
      - Reports
    summary: Generate export job (CSV/PDF)
    description: |
      Creates an asynchronous export job for session data. Returns job ID for polling status.
      **Tier Requirements:** Pro tier or higher
    operationId: createExportJob
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ExportRequest'
    responses:
      '202':
        description: Export job created
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ExportJobResponse'
      '400':
        $ref: '#/components/responses/BadRequest'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '403':
        description: Export feature requires Pro tier or higher
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ErrorResponse'
      '500':
        $ref: '#/components/responses/InternalServerError'

/api/v1/jobs/{jobId}:
  get:
    tags:
      - Reports
    summary: Poll export job status
    description: |
      Returns job status (PENDING, PROCESSING, COMPLETED, FAILED). When COMPLETED, includes download URL (expires in 24h).
    operationId: getJobStatus
    parameters:
      - name: jobId
        in: path
        required: true
        schema:
          type: string
          format: uuid
        description: Job ID returned from export endpoint
        example: "123e4567-e89b-12d3-a456-426614174000"
    responses:
      '200':
        description: Job status retrieved
```

**Standard Parameters:**
```yaml
PageParam:
  name: page
  in: query
  schema:
    type: integer
    minimum: 0
    default: 0
  description: Page number (0-indexed)
  example: 0

SizeParam:
  name: size
  in: query
  schema:
    type: integer
    minimum: 1
    maximum: 100
    default: 20
  description: Page size
  example: 20
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java`
    *   **Summary:** Core service implementing tier-gated analytics with three main capabilities: `getBasicSessionSummary()` for Free tier, `getDetailedSessionReport()` for Pro tier with round-by-round breakdown, and `generateExport()` for CSV/PDF export job enqueuing via Redis Stream.
    *   **Recommendation:** You MUST inject this service into your ReportingController. The service already handles all tier enforcement via `FeatureGate`. Methods return `Uni<SessionSummaryDTO>` and `Uni<DetailedSessionReportDTO>`.
    *   **Key Methods:**
        - `getBasicSessionSummary(UUID sessionId)` - Available to all tiers, returns basic stats
        - `getDetailedSessionReport(UUID sessionId, User user)` - Requires PRO tier, throws FeatureNotAvailableException for Free users
        - `generateExport(UUID sessionId, String format, User user)` - Requires PRO tier, returns Redis Stream message ID (job ID)

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java`
    *   **Summary:** Service for querying session history records with methods like `getUserSessions(userId, from, to)` for date range queries.
    *   **Recommendation:** You MUST inject this service for the session list endpoint. Use it to fetch paginated sessions and convert to DTOs.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/SessionSummaryDTO.java`
    *   **Summary:** DTO for basic session summary (Free tier). Contains fields: sessionId, roomTitle, startedAt, endedAt, totalStories, totalRounds, consensusRate, averageVote, participantCount, totalVotes.
    *   **Recommendation:** This DTO should be used for the session list response and for Free tier session detail responses.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/DetailedSessionReportDTO.java`
    *   **Summary:** DTO for detailed session report (Pro tier). Extends basic summary with nested classes `RoundDetailDTO` (contains round metadata and list of `VoteDetailDTO`) and `userConsistency` map.
    *   **Recommendation:** This DTO should be returned for Pro tier users on the session detail endpoint.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/ExportJob.java`
    *   **Summary:** Hibernate entity tracking export job lifecycle. Has static method `findByJobId(UUID)` for lookup. Contains fields: jobId, sessionId, user, format, status (enum), downloadUrl, errorMessage, timestamps (createdAt, processingStartedAt, completedAt, failedAt).
    *   **Recommendation:** You MUST use `ExportJob.findByJobId(jobId)` in the job status endpoint to retrieve job details. The entity already has all the fields needed for the response DTO.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/RoomController.java`
    *   **Summary:** Example REST controller demonstrating the project's patterns: JAX-RS annotations (`@Path`, `@GET`, `@POST`, `@PUT`), reactive Uni return types, OpenAPI annotations (`@Operation`, `@APIResponse`), service injection, DTO mapping, and exception handling via exception mappers.
    *   **Recommendation:** You SHOULD follow this exact pattern for your ReportingController. Key patterns: Use `@Path("/api/v1")`, `@Produces(MediaType.APPLICATION_JSON)`, inject services via `@Inject`, return `Uni<Response>`, use `Response.ok()` / `Response.status()` builders.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/UserController.java`
    *   **Summary:** Another controller example showing authentication patterns (currently `@RolesAllowed("USER")` with TODOs for actual enforcement), parameter validation, and authorization checks.
    *   **Recommendation:** You SHOULD use `@RolesAllowed("USER")` on protected endpoints. The task requires authorization checks to prevent users from accessing other users' sessions.

*   **File:** `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
    *   **Summary:** Service for tier-based feature access control. Provides methods like `hasSufficientTier()`, `requireCanAccessAdvancedReports()` that throw `FeatureNotAvailableException`.
    *   **Recommendation:** The ReportingService already uses FeatureGate internally, so you do NOT need to call it directly in the controller. However, be aware that 403 responses come from FeatureNotAvailableException which is handled by `FeatureNotAvailableExceptionMapper`.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/` (directory)
    *   **Summary:** Contains existing DTO classes like `ErrorResponse`, `RoomDTO`, `UserDTO`, request/response DTOs.
    *   **Recommendation:** You MUST create new DTOs in this directory: `SessionListResponse.java` and `ExportJobResponse.java`. Follow the naming convention and use Jackson annotations (`@JsonProperty`) for field naming.

### Implementation Tips & Notes

*   **Tip:** For pagination, you'll need to add query parameters `@QueryParam("page")` and `@QueryParam("size")` with default values matching the OpenAPI spec (page=0, size=20).
*   **Tip:** The session list response needs to include pagination metadata. Create a `SessionListResponse` DTO that contains: `List<SessionSummaryDTO> sessions`, `int totalCount`, `int page`, `int size`, `boolean hasNext`.
*   **Tip:** For the export endpoint, you need to create a request DTO (e.g., `ExportRequest`) with fields: `sessionId` (UUID) and `format` (String: "CSV" or "PDF"). The response DTO `ExportJobResponse` should contain the `jobId` (String or UUID).
*   **Tip:** For the job status endpoint, you need to create a response DTO that includes: `jobId`, `status` (String enum: PENDING/PROCESSING/COMPLETED/FAILED), `downloadUrl` (nullable), `errorMessage` (nullable), `createdAt`, `completedAt`.
*   **Tip:** When calling `SessionHistoryService.getUserSessions()`, you'll need to convert the date query parameters (Strings in ISO format) to `Instant` objects. Use `LocalDate.parse(dateString).atStartOfDay(ZoneOffset.UTC).toInstant()`.
*   **Tip:** For the session detail endpoint that returns tier-based content, you need to check the user's tier and call either `getBasicSessionSummary()` or `getDetailedSessionReport()` accordingly. However, the acceptance criteria suggests that `getDetailedSessionReport()` already handles this by throwing FeatureNotAvailableException for Free tier users, so you might just always call the detailed method and let the exception mapper handle the 403 response.
*   **Note:** The task mentions authorization enforcement ("user can only access own sessions or rooms they participated in"). You'll need to get the authenticated user from the security context and verify they own the session or participated in it. The current codebase has TODOs for getting user from SecurityContext, so you may need to implement a helper method or use a pattern similar to the existing controllers.
*   **Note:** The `generateExport()` method in ReportingService returns a `Uni<String>` containing the job ID (which is the Redis Stream message ID). You should create an ExportJob entity record with status PENDING and persist it to the database, then return the job ID. Actually, looking at the ExportJob entity, it has a `jobId` UUID field. The ReportingService generates a UUID for `jobId` and includes it in the Redis message. You need to create the ExportJob entity with that same jobId BEFORE enqueuing to Redis.
*   **Warning:** The export endpoint should return HTTP 202 Accepted (not 201 Created) because the job is asynchronous. Use `Response.status(Response.Status.ACCEPTED)`.
*   **Warning:** Make sure to handle the case where a user tries to access a session they don't own. You'll need to query the SessionHistory to check if the authenticated user's ID matches the room owner or is in the participants list.

### Critical Integration Points

1. **ReportingService Integration:** Your controller is a thin wrapper around ReportingService. The service does all the heavy lifting (tier checks, data fetching, export job enqueuing). Your job is to handle HTTP concerns (request parsing, response building, status codes).

2. **SessionHistoryService Integration:** For the session list endpoint, inject SessionHistoryService and use `getUserSessions()` method with pagination. The service returns `List<SessionHistory>` which you need to convert to `List<SessionSummaryDTO>`.

3. **ExportJob Entity Integration:** For the export endpoint, after calling `ReportingService.generateExport()`, you need to create an ExportJob entity with the returned job ID, persist it to the database, then return the job ID in the response. For the job status endpoint, use `ExportJob.findByJobId()` to retrieve the job and return its current status.

4. **Authentication Context:** You need to extract the authenticated User from the security context (likely via `@Context SecurityContext` injection or a custom `@AuthenticatedUser` CDI qualifier if the project has one). Check existing controllers for the pattern.

5. **Error Handling:** The project uses exception mappers (in `backend/src/main/java/com/scrumpoker/api/rest/exception/`). You don't need to catch exceptions in your controller methods; let them propagate and be handled by the mappers. Relevant mappers: `UserNotFoundExceptionMapper`, `IllegalArgumentExceptionMapper`, `FeatureNotAvailableExceptionMapper`.

### Testing Strategy

*   The acceptance criteria require testing that Free tier users get basic summaries while Pro tier users get detailed reports. You'll need to mock or set the user's subscriptionTier in your tests.
*   Test pagination by creating multiple sessions and verifying the page/size parameters work correctly.
*   Test authorization by verifying 403 responses when users try to access other users' sessions.
*   For the export endpoint, verify that it returns 202 Accepted and that an ExportJob record is created in the database.
*   For the job status endpoint, test all four job statuses (PENDING, PROCESSING, COMPLETED, FAILED) and verify the correct fields are returned.

---

## 4. Additional Context

### Dependencies Status
- **I6.T2 (ReportingService):** ✅ DONE - Service is fully implemented and tested
- **I6.T3 (ExportJobProcessor):** ✅ DONE - Background worker implemented, ExportJob entity exists

### Next Steps After This Task
- I6.T5: Frontend SessionHistoryPage (depends on this task)
- I6.T6: Frontend SessionDetailPage (depends on this task)

### Suggested Implementation Order
1. Create DTOs: `SessionListResponse`, `ExportJobResponse`, `ExportRequest`, `JobStatusResponse`
2. Create `ReportingController` class with basic structure and annotations
3. Implement `GET /api/v1/reports/sessions` (session list with pagination)
4. Implement `GET /api/v1/reports/sessions/{sessionId}` (session detail with tier checks)
5. Implement `POST /api/v1/reports/export` (create export job)
6. Implement `GET /api/v1/jobs/{jobId}` (poll job status)
7. Add OpenAPI annotations to all endpoints
8. Write integration tests

### Architecture References
- REST API patterns: `.codemachine/artifacts/architecture/04_Behavior_and_Communication.md`
- Tier-based reporting requirements: `.codemachine/artifacts/architecture/01_Context_and_Drivers.md` (reporting-requirements section)
- OpenAPI specification: `api/openapi.yaml`
