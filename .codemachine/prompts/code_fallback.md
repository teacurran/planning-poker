# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Implement REST endpoints for reporting per OpenAPI spec. Endpoints: `GET /api/v1/reports/sessions` (list user's sessions with pagination), `GET /api/v1/reports/sessions/{sessionId}` (get session report, tier-gated detail level), `POST /api/v1/reports/export` (create export job, returns job ID), `GET /api/v1/jobs/{jobId}` (poll export job status, returns download URL when complete). Use `ReportingService`. Return pagination metadata (total count, page, size). Enforce authorization (user can only access own sessions or rooms they participated in).

---

## Issues Detected

### 1. **Missing Dependencies**
*   The project `pom.xml` is missing critical dependencies that are required by existing services that the ReportingController depends on:
    *   **Redis dependencies** (`quarkus-redis-client`) - Required by `ReportingService` which injects `ReactiveRedisDataSource`
    *   **SmallRye JWT dependencies** (`quarkus-smallrye-jwt`, `quarkus-smallrye-jwt-build`) - Required by `JwtTokenService` and OAuth providers
    *   **WebSocket dependencies** (`quarkus-websockets`) - Required by `MessageRouter` and WebSocket handlers
    *   **Stripe SDK** (`stripe-java`) - Required by `StripeWebhookController` and payment services
    *   **PDFBox library** (`pdfbox`) - Required by `PdfExporter` for PDF generation

### 2. **Authorization Not Implemented**
*   The acceptance criteria states: "Unauthorized access to other user's sessions returns 403"
*   The controller has multiple `TODO` comments about getting the authenticated user from SecurityContext but doesn't actually implement this
*   Lines 88-90, 227-231, 291-295, 369-370 all have placeholder auth logic that returns `null` or allows all access
*   No verification is performed to ensure users can only access their own sessions or sessions they participated in

### 3. **Incorrect Export Job Creation Flow**
*   Line 316-329: The controller creates an `ExportJob` entity AFTER calling `reportingService.generateExport()`
*   According to the code analysis notes, the `ReportingService.generateExport()` method should be creating the job ID and including it in the Redis message
*   The controller should be creating the `ExportJob` entity with status PENDING **BEFORE** enqueuing to Redis, not after
*   The current implementation creates a race condition where the worker might start processing before the database record exists

### 4. **Pagination Implementation Issues**
*   Lines 132-141: The pagination logic fetches ALL sessions first, then applies pagination in memory
*   This is inefficient and won't scale - it should use database-level pagination (LIMIT/OFFSET)
*   The `SessionHistoryService` likely has methods that accept page/size parameters for efficient pagination
*   The current approach defeats the purpose of pagination (reducing memory usage and query time)

### 5. **Missing Validation for Export Job Authorization**
*   Lines 291-295: The export endpoint has a TODO for verifying user access but currently allows any user to export any session
*   This is a security vulnerability - users could export sessions they don't have access to

### 6. **Missing Validation for Job Status Authorization**
*   Lines 369-370: The job status endpoint has a TODO for verifying the user owns the job
*   Currently any authenticated user can poll any job's status, potentially accessing other users' export data

### 7. **Inconsistent Error Handling**
*   The session detail endpoint (lines 236-246) only catches `IllegalArgumentException` for "Session not found" cases
*   It doesn't handle the case where the session exists but the user doesn't have permission to access it (should return 403, not 404)
*   The authorization check needs to happen before attempting to fetch the session

### 8. **Missing Tests**
*   No test file was created for the ReportingController
*   The acceptance criteria includes multiple test scenarios that need verification:
    *   Pagination with default 20 per page
    *   Free tier users getting basic summaries
    *   Pro tier users getting detailed reports with round breakdown
    *   Export job creation returning 202 and job ID
    *   Job status polling returning correct status codes
    *   Unauthorized access returning 403

---

## Best Approach to Fix

### Step 1: Add Missing Dependencies to `pom.xml`

Add the following dependencies to `/Users/tea/dev/github/planning-poker/pom.xml` in the `<dependencies>` section:

```xml
<!-- Redis for job queuing -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-redis-client</artifactId>
</dependency>

<!-- JWT Authentication -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-jwt</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-jwt-build</artifactId>
</dependency>

<!-- WebSocket Support (classic API for existing handlers) -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-websockets</artifactId>
</dependency>

<!-- Stripe SDK for payments -->
<dependency>
    <groupId>com.stripe</groupId>
    <artifactId>stripe-java</artifactId>
    <version>24.16.0</version>
</dependency>

<!-- PDF generation -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.1</version>
</dependency>
```

### Step 2: Implement Authentication Context Extraction

Before fixing the controller, you need to determine how to get the authenticated user. Check the existing controllers (`UserController.java`, `RoomController.java`) to see if there's a pattern already established. If not, you have two options:

**Option A:** If the project uses SecurityContext injection:
```java
@Context
SecurityContext securityContext;

private User getAuthenticatedUser() {
    String userId = securityContext.getUserPrincipal().getName();
    return User.findById(UUID.fromString(userId)).await().indefinitely();
}
```

**Option B:** If the project uses a custom CDI qualifier like `@AuthenticatedUser`:
```java
@Inject
@AuthenticatedUser
User currentUser;
```

**For now (until auth is fully implemented):** Create a helper method that returns a mock user for testing purposes, but structure the code so it's easy to replace later:

```java
private Uni<User> getAuthenticatedUser() {
    // TODO: Replace with actual SecurityContext extraction when auth is implemented
    // For now, return a mock user for testing
    return User.findById(UUID.fromString("00000000-0000-0000-0000-000000000001"));
}
```

### Step 3: Fix Session List Endpoint Authorization and Pagination

Modify the `listSessions()` method in `ReportingController.java`:

1. Call `getAuthenticatedUser()` to get the actual user
2. Use database-level pagination by checking if `SessionHistoryService` has methods like `getUserSessions(userId, fromDate, toDate, page, size)` or `getUserSessionsPaginated()`
3. If the service doesn't have paginated methods, you need to add them to `SessionHistoryService` first
4. Remove the in-memory pagination logic (lines 146-161) and replace with proper database pagination

### Step 4: Fix Session Detail Endpoint Authorization

Modify the `getSessionReport()` method:

1. Get the authenticated user first
2. Load the `SessionHistory` record for the given `sessionId`
3. **Check authorization**: Verify that either:
   - The user is the owner of the room, OR
   - The user is in the list of participants for that session
4. If authorization fails, return 403 Forbidden (not 404)
5. If authorized, proceed with calling `reportingService.getDetailedSessionReport()` for Pro users or `getBasicSessionSummary()` for Free users

### Step 5: Fix Export Job Creation Flow

Modify the `createExportJob()` method:

1. Get authenticated user
2. Load the session and verify the user has access to it (same authorization check as session detail endpoint)
3. Create the `ExportJob` entity with status PENDING and persist it to the database FIRST
4. Extract the `jobId` from the persisted entity
5. Call `reportingService.generateExport()` passing the pre-generated `jobId`
6. If the Redis enqueue fails, update the job status to FAILED in the database
7. Return HTTP 202 with the job ID

**Note:** You may need to modify `ReportingService.generateExport()` to accept a `jobId` parameter instead of generating one internally. Check the current signature.

### Step 6: Fix Job Status Endpoint Authorization

Modify the `getJobStatus()` method:

1. Get authenticated user
2. Load the `ExportJob` using `ExportJob.findByJobId(jobId)`
3. Verify that `job.user.userId` matches the authenticated user's ID
4. If not, return 403 Forbidden
5. If authorized, return the job status response

### Step 7: Create Integration Tests

Create a new test file `/Users/tea/dev/github/planning-poker/backend/src/test/java/com/scrumpoker/api/rest/ReportingControllerTest.java` with the following test cases:

1. **testListSessions_DefaultPagination** - Verify default page=0, size=20
2. **testListSessions_CustomPagination** - Verify custom page/size parameters work
3. **testListSessions_InvalidPagination** - Verify 400 error for invalid page/size
4. **testGetSessionReport_FreeTier** - Verify Free tier user gets basic summary only
5. **testGetSessionReport_ProTier** - Verify Pro tier user gets detailed report
6. **testGetSessionReport_Unauthorized** - Verify 403 when accessing other user's session
7. **testGetSessionReport_NotFound** - Verify 404 for non-existent session
8. **testCreateExportJob_Success** - Verify 202 response and job ID returned
9. **testCreateExportJob_Unauthorized** - Verify 403 for unauthorized session access
10. **testGetJobStatus_Pending** - Verify PENDING status
11. **testGetJobStatus_Completed** - Verify COMPLETED status with download URL
12. **testGetJobStatus_Unauthorized** - Verify 403 when accessing other user's job
13. **testGetJobStatus_NotFound** - Verify 404 for non-existent job

Use `@QuarkusTest` annotation and inject `SessionHistoryService`, `ReportingService`, and `ExportJob` entity for test data setup.

### Step 8: Verify Compilation and Fix Any Errors

After making all changes:

1. Run `mvn clean compile -DskipTests` to verify the code compiles
2. Fix any compilation errors that arise
3. Run `mvn test -Dtest=ReportingControllerTest` to run your new tests
4. Fix any failing tests

### Step 9: Manual Testing Checklist

Once tests pass, verify the following manually (if possible):

1. Create test users with different subscription tiers (Free, Pro)
2. Create test sessions for those users
3. Call `GET /api/v1/reports/sessions` and verify pagination metadata is correct
4. Call `GET /api/v1/reports/sessions/{sessionId}` as Free user and verify you only get basic summary
5. Call same endpoint as Pro user and verify you get detailed round breakdown
6. Try accessing another user's session and verify 403 response
7. Call `POST /api/v1/reports/export` and verify job ID is returned
8. Call `GET /api/v1/jobs/{jobId}` and verify status is PENDING initially
9. After background worker processes job, verify status changes to COMPLETED with download URL

---

## Critical Notes

- **DO NOT** mock or skip the authorization checks - they are explicitly required in the acceptance criteria
- **DO NOT** use in-memory pagination - use database-level pagination for performance
- **DO ensure** the export job is persisted to the database BEFORE enqueuing to Redis
- **DO verify** all dependencies compile before submitting
- **DO create** comprehensive integration tests covering all acceptance criteria
