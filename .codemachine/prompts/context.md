# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I7.T3",
  "iteration_id": "I7",
  "iteration_goal": "Implement enterprise-tier features including SSO integration (OIDC/SAML2), organization management, member administration, org-level branding, and audit logging.",
  "description": "Create `AuditLogService` recording security and administrative events for Enterprise compliance. Methods: `logEvent(orgId, userId, action, resourceType, resourceId, ipAddress, userAgent)`. Events to log: user authentication (SSO login), organization config changes (SSO settings updated), member management (user added/removed/role changed), room deletion, sensitive data access. Use `AuditLogRepository`. Async event publishing (fire-and-forget via CDI event or Redis Stream for performance). Store event in partitioned AuditLog table (partition by month). Include contextual data (IP address, user agent, timestamp, change details JSONB).",
  "agent_type_hint": "BackendAgent",
  "inputs": "Audit logging requirements from architecture blueprint, AuditLog entity from I1, Events requiring audit trail",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/organization/AuditLog.java",
    "backend/src/main/java/com/scrumpoker/repository/AuditLogRepository.java",
    ".codemachine/artifacts/architecture/05_Operational_Architecture.md"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/domain/organization/AuditLogService.java",
    "backend/src/main/java/com/scrumpoker/event/AuditEvent.java"
  ],
  "deliverables": "AuditLogService with logEvent method, Async audit event processing (CDI @ObservesAsync or Redis), Audit events for: SSO login, org config change, member add/remove, room delete, IP address and user agent extraction from HTTP request context, Change details stored in JSONB (before/after values for config changes), Integration in OrganizationService (log after member add/remove)",
  "acceptance_criteria": "SSO login creates audit log entry, Organization config change logged with before/after values, Member add event includes user ID and assigned role, Audit log query by orgId returns events sorted by timestamp, Async processing doesn't block main request thread, IP address and user agent correctly captured",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Audit Logging Requirements (from 05_Operational_Architecture.md)

```markdown
**Audit Logging:**
- **Scope:** Enterprise tier security and compliance events
- **Storage:** Dedicated `AuditLog` table (partitioned by month) + immutable S3 bucket for archival
- **Events:**
  - User authentication (SSO login, logout)
  - Organization configuration changes (SSO settings, branding)
  - Member management (invite, role change, removal)
  - Administrative actions (room deletion, user account suspension)
- **Attributes:** `timestamp`, `orgId`, `userId`, `action`, `resourceType`, `resourceId`, `ipAddress`, `userAgent`, `changeDetails` (JSONB)
```

### Context: Logging Strategy (from 05_Operational_Architecture.md)

```markdown
**Structured Logging (JSON Format):**
- **Library:** SLF4J with Quarkus Logging JSON formatter
- **Schema:** Each log entry includes:
  - `timestamp` - ISO8601 format
  - `level` - DEBUG, INFO, WARN, ERROR
  - `logger` - Java class name
  - `message` - Human-readable description
  - `correlationId` - Unique request/WebSocket session identifier for distributed tracing
  - `userId` - Authenticated user ID (omitted for anonymous)
  - `roomId` - Estimation room context
  - `action` - Semantic action (e.g., `vote.cast`, `room.created`, `subscription.upgraded`)
  - `duration` - Operation latency in milliseconds (for timed operations)
  - `error` - Exception stack trace (for ERROR level)

**Log Levels by Environment:**
- **Development:** DEBUG (verbose SQL queries, WebSocket message payloads)
- **Staging:** INFO (API requests, service method calls, integration events)
- **Production:** WARN (error conditions, performance degradation, security events)

**Log Aggregation:**
- **Stack:** Loki (log aggregation) + Promtail (log shipper) + Grafana (visualization)
- **Alternative:** AWS CloudWatch Logs or GCP Cloud Logging for managed service
- **Retention:** 30 days for application logs, 90 days for audit logs (compliance requirement)
- **Query Optimization:** Indexed fields: `correlationId`, `userId`, `roomId`, `action`, `level`
```

### Context: Task I7.T3 Details (from 02_Iteration_I7.md)

```markdown
**Task 7.3: Create Audit Logging Service**
- **Task ID:** `I7.T3`
- **Description:** Create `AuditLogService` recording security and administrative events for Enterprise compliance. Methods: `logEvent(orgId, userId, action, resourceType, resourceId, ipAddress, userAgent)`. Events to log: user authentication (SSO login), organization config changes (SSO settings updated), member management (user added/removed/role changed), room deletion, sensitive data access. Use `AuditLogRepository`. Async event publishing (fire-and-forget via CDI event or Redis Stream for performance). Store event in partitioned AuditLog table (partition by month). Include contextual data (IP address, user agent, timestamp, change details JSONB).
- **Agent Type Hint:** `BackendAgent`
- **Inputs:**
    - Audit logging requirements from architecture blueprint
    - AuditLog entity from I1
    - Events requiring audit trail
- **Input Files:**
    - `backend/src/main/java/com/scrumpoker/domain/organization/AuditLog.java`
    - `backend/src/main/java/com/scrumpoker/repository/AuditLogRepository.java`
    - `.codemachine/artifacts/architecture/05_Operational_Architecture.md`
- **Target Files:**
    - `backend/src/main/java/com/scrumpoker/domain/organization/AuditLogService.java`
    - `backend/src/main/java/com/scrumpoker/event/AuditEvent.java` (CDI event)
- **Deliverables:**
    - AuditLogService with logEvent method
    - Async audit event processing (CDI @ObservesAsync or Redis)
    - Audit events for: SSO login, org config change, member add/remove, room delete
    - IP address and user agent extraction from HTTP request context
    - Change details stored in JSONB (before/after values for config changes)
    - Integration in OrganizationService (log after member add/remove)
- **Acceptance Criteria:**
    - SSO login creates audit log entry
    - Organization config change logged with before/after values
    - Member add event includes user ID and assigned role
    - Audit log query by orgId returns events sorted by timestamp
    - Async processing doesn't block main request thread
    - IP address and user agent correctly captured
- **Dependencies:** []
- **Parallelizable:** Yes (can work parallel with other tasks)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/AuditLog.java`
    *   **Summary:** This is the JPA entity for audit logs. It uses a composite primary key (`AuditLogId` with `logId` UUID and `timestamp` Instant for partition support). Fields include `organization` (ManyToOne), `user` (ManyToOne), `action`, `resourceType`, `resourceId`, `ipAddress` (VARCHAR 45), `userAgent` (VARCHAR 500), and `metadata` (JSONB as String).
    *   **Recommendation:** You MUST import and use this entity class. Note that `ipAddress` was changed from PostgreSQL INET to VARCHAR due to Hibernate Reactive mapping issues (see comment in entity). The `metadata` field is stored as a JSON string and should be serialized/deserialized by application code.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/AuditLogId.java`
    *   **Summary:** Composite primary key for AuditLog with `logId` (UUID) and `timestamp` (Instant). This enables partition key support for monthly range partitions.
    *   **Recommendation:** When creating audit log entries, you MUST create a new `AuditLogId` instance with `UUID.randomUUID()` and `Instant.now()`.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/AuditLogRepository.java`
    *   **Summary:** Reactive Panache repository for AuditLog. Provides query methods: `findByOrgId()`, `findByDateRange()`, `findByOrgIdAndDateRange()`, `findByUserId()`, `findByAction()`, `findByResourceTypeAndId()`, `findRecentByOrgId()`, `countByOrgId()`, `countByActionAndDateRange()`.
    *   **Recommendation:** You MUST inject and use this repository in your AuditLogService for persisting audit log entries. All methods return reactive types (`Uni<>` or `Multi<>`).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
    *   **Summary:** Domain service for organization management. Methods include `createOrganization()`, `updateSsoConfig()`, `addMember()`, `removeMember()`, `updateBranding()`. This service is annotated with `@ApplicationScoped` and uses `@WithTransaction` for transactional operations.
    *   **Recommendation:** After you implement AuditLogService, you will need to integrate it into this service (task I7.T3 deliverable mentions "Integration in OrganizationService"). You should inject AuditLogService and call it after member add/remove operations. However, DO NOT modify OrganizationService in this task - that's for the integration phase.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Summary:** Example of a domain service following the project's patterns. Annotated with `@ApplicationScoped`, uses `@Inject` for dependencies, `@Transactional` for database operations, returns reactive `Uni<>` types, includes comprehensive Javadoc, and uses Logger for debugging.
    *   **Recommendation:** You SHOULD follow this service's structure and patterns when implementing AuditLogService. Note the use of `Logger.getLogger()`, detailed Javadoc comments, validation patterns, and reactive return types.

*   **File:** `backend/src/main/java/com/scrumpoker/event/RoomEvent.java`
    *   **Summary:** Event message envelope for Redis Pub/Sub room broadcasts. This is NOT a CDI event - it's a POJO used with Redis. Structure: `type` (String), `requestId` (String), `payload` (Map).
    *   **Note:** This file shows the project's event model for Redis-based events. For your AuditEvent, you should create a CDI event class instead, not a Redis event.

*   **File:** `backend/src/main/java/com/scrumpoker/security/TierEnforcementInterceptor.java`
    *   **Summary:** JAX-RS filter showing how to access HTTP request context. Uses `@Context ResourceInfo` to get resource metadata and `ContainerRequestContext` parameter to access request details.
    *   **Recommendation:** For extracting IP address and User Agent, you SHOULD use `ContainerRequestContext` which provides access to HTTP headers. IP address can be extracted from `X-Forwarded-For` header (for proxied requests) or from the request's remote address. User Agent is available via the `User-Agent` header.

*   **File:** `backend/src/main/java/com/scrumpoker/worker/ExportJobProcessor.java`
    *   **Summary:** Shows CDI event observer pattern using `@Observes` for `StartupEvent`. This demonstrates how to observe CDI events.
    *   **Note:** For async processing, you should use `@ObservesAsync` instead of `@Observes` to ensure non-blocking execution.

### Implementation Tips & Notes

*   **Tip:** The project uses CDI (Jakarta EE Contexts and Dependency Injection). For async audit logging, you should:
    1. Create an `AuditEvent` class (POJO) in the `event` package to carry audit data.
    2. In `AuditLogService`, use `jakarta.enterprise.event.Event<AuditEvent>` injected field to fire events.
    3. Create an async observer method (in the same service or separate class) annotated with `@ObservesAsync` to process the events and persist to database.
    4. This approach ensures fire-and-forget behavior without blocking the calling thread.

*   **Tip:** For IP address extraction, you should check multiple sources in order of precedence:
    1. `X-Forwarded-For` header (most accurate for proxied requests)
    2. `X-Real-IP` header (common in Nginx setups)
    3. Fall back to remote address from request context
    4. Handle cases where IP might be null or empty

*   **Tip:** For User Agent extraction, simply read the `User-Agent` header from the request. It can be quite long (up to 500 chars based on AuditLog.userAgent field size).

*   **Tip:** For JSONB metadata serialization, the project uses Jackson `ObjectMapper`. You SHOULD inject `ObjectMapper` and use `writeValueAsString()` to serialize metadata objects to JSON strings before storing in `AuditLog.metadata`. See `OrganizationService.updateSsoConfig()` for an example of this pattern.

*   **Warning:** The AuditLogRepository tests are partially disabled due to a Hibernate Reactive bug with `@EmbeddedId` composite keys in query results (see `AuditLogRepositoryTest.java:98`). This is a known issue, but basic persist/findById operations work correctly. Your service should focus on persisting audit logs - querying is already handled by the repository.

*   **Note:** The project uses reactive programming throughout. You MUST return `Uni<Void>` from the async observer method and use reactive repository methods. The `@WithTransaction` annotation works with reactive methods.

*   **Note:** The `action` field (VARCHAR 100) should follow a naming convention like `SSO_LOGIN`, `ORG_CONFIG_UPDATED`, `MEMBER_ADDED`, `MEMBER_REMOVED`, `MEMBER_ROLE_CHANGED`, `ROOM_DELETED`. Use uppercase with underscores for consistency.

*   **Note:** For change details in JSONB metadata, structure it as a JSON object with `before` and `after` keys for configuration changes. Example: `{"before": {"logoUrl": "old.png"}, "after": {"logoUrl": "new.png"}}`.

*   **Important:** The AuditLogService methods should NOT throw exceptions - audit logging is a cross-cutting concern that should never break the main business logic. Wrap repository operations in try-catch and log errors instead of propagating them.

*   **Important:** Since this is Enterprise-tier functionality, the service should only create audit logs for organizations (orgId is required). Don't create audit logs for non-enterprise users without an organization.

### Recommended Implementation Structure

1. **Create `AuditEvent.java` in `backend/src/main/java/com/scrumpoker/event/` package:**
   - POJO with fields: `orgId`, `userId`, `action`, `resourceType`, `resourceId`, `ipAddress`, `userAgent`, `metadata` (as String, already serialized JSON)
   - Include constructors, getters/setters, and builder pattern for fluent construction

2. **Create `AuditLogService.java` in `backend/src/main/java/com/scrumpoker/domain/organization/` package:**
   - Annotate with `@ApplicationScoped`
   - Inject: `AuditLogRepository`, `Event<AuditEvent>`, `OrganizationRepository`, `UserRepository`, `ObjectMapper`, `Logger`
   - Public method: `void logEvent(UUID orgId, UUID userId, String action, String resourceType, String resourceId, String ipAddress, String userAgent, Map<String, Object> changeDetails)`
     - This method should fire a CDI event (non-blocking)
   - Async observer method: `void processAuditEvent(@ObservesAsync AuditEvent event)`
     - This method should persist the audit log to database
     - Use `@WithTransaction` for transaction management
     - Return `Uni<Void>` and handle errors gracefully (catch and log, don't propagate)
   - Optional: Create overloaded `logEvent` methods for common scenarios (without metadata, without resource details, etc.)

3. **HTTP Context Extraction Utility:**
   - Consider adding a static utility method or helper class to extract IP and User Agent from `ContainerRequestContext`
   - This can be called from REST controllers before invoking business logic
   - Alternatively, create a JAX-RS filter to capture and store request context in a thread-local for service layer access

4. **Testing:**
   - You should create basic tests, but note that full integration tests with the repository are challenging due to the Hibernate bug
   - Focus on unit tests for the event firing logic
   - Test metadata JSON serialization/deserialization
