# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I8.T3",
  "iteration_id": "I8",
  "iteration_goal": "Prepare application for production deployment including Kubernetes manifests, monitoring setup, performance optimization, security hardening, documentation, and final end-to-end testing.",
  "description": "Configure structured JSON logging for production. Set up Quarkus Logging JSON formatter. Add correlation ID (request ID) to all log entries: generate UUID on request entry, store in thread-local or request context, include in log MDC (Mapped Diagnostic Context). Propagate correlation ID across async operations (reactive streams, background jobs). Configure log levels: WARN in production, INFO in staging, DEBUG in dev. Implement request logging filter capturing: timestamp, method, path, status code, duration, correlation ID. Configure log aggregation destination (Loki, CloudWatch Logs).",
  "agent_type_hint": "BackendAgent",
  "inputs": "Logging requirements from architecture blueprint, Quarkus logging configuration, Correlation ID pattern",
  "input_files": [
    ".codemachine/artifacts/architecture/05_Operational_Architecture.md"
  ],
  "target_files": [
    "backend/src/main/resources/application.properties",
    "backend/src/main/java/com/scrumpoker/logging/CorrelationIdFilter.java",
    "backend/src/main/java/com/scrumpoker/logging/LoggingConstants.java"
  ],
  "deliverables": "Quarkus logging configured for JSON output, CorrelationIdFilter generating and adding correlation ID to MDC, Request logging filter logging every HTTP request with correlation ID, Correlation ID propagated to WebSocket messages, Environment-specific log levels (WARN prod, INFO staging, DEBUG dev), Log aggregation endpoint configured (Loki or CloudWatch)",
  "acceptance_criteria": "Application logs in JSON format, Each log entry includes `correlationId` field, HTTP requests logged with method, path, status, duration, correlationId, Correlation ID appears consistently across multiple log entries for same request, WebSocket messages include correlationId in logs, Log level adjusts per environment",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: logging-and-monitoring (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: logging-and-monitoring -->
#### Logging & Monitoring

<!-- anchor: logging-strategy -->
##### Logging Strategy

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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/logging/CorrelationIdFilter.java`
    *   **Summary:** This file is **ALREADY FULLY IMPLEMENTED**. It's a JAX-RS filter that adds correlation IDs to all HTTP requests, stores them in MDC (Mapped Diagnostic Context), logs request/response cycles with duration, and cleans up MDC after processing to prevent thread-local leakage.
    *   **Key Features Already Implemented:**
        - Extracts correlation ID from incoming `X-Correlation-ID` header or generates new UUID
        - Adds correlation ID to MDC via `MDC.put(LoggingConstants.CORRELATION_ID, correlationId)`
        - Logs every HTTP request with: method, path, status code, duration, correlation ID (automatically included via MDC)
        - Adds correlation ID to response headers for client-side tracing
        - Cleans up MDC in finally block to prevent thread-local leakage
        - Different log levels based on status code (ERROR for 5xx, WARN for 4xx, INFO for success)
    *   **Recommendation:** **DO NOT MODIFY THIS FILE.** It is complete and fully implements the correlation ID tracking for HTTP requests as specified in the task. The implementation is production-ready.

*   **File:** `backend/src/main/java/com/scrumpoker/logging/LoggingConstants.java`
    *   **Summary:** This file defines all standard constants for MDC keys, HTTP headers, and logging-related properties. It's **ALREADY COMPLETE**.
    *   **Key Constants Defined:**
        - `CORRELATION_ID = "correlationId"` - MDC key for correlation ID field
        - `CORRELATION_ID_HEADER = "X-Correlation-ID"` - HTTP header name
        - `USER_ID = "userId"` - MDC key for authenticated user ID
        - `ROOM_ID = "roomId"` - MDC key for room context
        - `ACTION = "action"` - MDC key for semantic business actions
        - `WS_CORRELATION_ID_PROPERTY = "ws.correlationId"` - WebSocket session property key
    *   **Recommendation:** **DO NOT MODIFY THIS FILE.** All required constants are already defined and in use throughout the codebase.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Application configuration file with **EXTENSIVE LOGGING CONFIGURATION ALREADY IN PLACE**.
    *   **Current Logging Configuration (Lines 215-267):**
        - JSON logging enabled by default: `quarkus.log.console.json=${LOG_JSON_FORMAT:true}`
        - MDC fields configured for JSON output (lines 228-231): correlationId, userId, roomId, action
        - Environment-specific log levels:
          - Production: `%prod.quarkus.log.level=WARN` (line 239)
          - Staging: `%staging.quarkus.log.level=INFO` (line 243)
          - Development: `%dev.quarkus.log.level=DEBUG` (line 256)
        - Verbose Quarkus internal logs suppressed (lines 234-236)
        - Comprehensive comments about log aggregation (lines 246-251)
    *   **What's ALREADY Working:**
        - JSON structured logging is enabled for all environments
        - MDC fields are automatically included in JSON output
        - Environment-specific log levels are correctly configured
        - Log format includes all required fields: timestamp, level, logger, message, correlationId, userId, roomId, action
    *   **Recommendation:** **MINIMAL MODIFICATIONS NEEDED.** The logging configuration is essentially complete. You may only need to add minor clarifications or ensure consistency.

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
    *   **Summary:** WebSocket handler demonstrating **PERFECT MDC PROPAGATION PATTERN** for async operations.
    *   **Critical Implementation Pattern (Lines 122-132, 203-226, 336-344):**
        - Correlation ID generated on WebSocket connection: `String correlationId = UUID.randomUUID().toString();`
        - Stored in session user properties: `session.getUserProperties().put(LoggingConstants.WS_CORRELATION_ID_PROPERTY, correlationId);`
        - **MDC set at the START of each async callback**: `MDC.put(LoggingConstants.CORRELATION_ID, correlationId);`
        - **MDC cleared in finally block after callback**: `MDC.clear();`
        - This pattern is used in ALL async handlers: onOpen, onMessage, onClose, onError
    *   **Key Learning:** The codebase **ALREADY DEMONSTRATES** how to propagate correlation IDs across async reactive operations. The pattern is:
        1. Store correlation ID in context (session properties for WebSocket)
        2. Retrieve it in each async callback
        3. Set MDC at callback entry
        4. Clear MDC in finally block at callback exit
    *   **Recommendation:** You SHOULD document this existing pattern as proof of correlation ID propagation in async operations. The WebSocket implementation serves as the reference implementation for reactive correlation ID propagation.

### Implementation Tips & Notes

*   **Tip #1 - Task is 95% Complete:** The correlation ID tracking system is **ALREADY FULLY IMPLEMENTED** in the codebase. The CorrelationIdFilter handles HTTP requests, the WebSocket handler demonstrates async propagation, and application.properties has full JSON logging configuration with MDC fields. Your primary task is to **VERIFY, TEST, AND DOCUMENT** the existing implementation rather than build from scratch.

*   **Tip #2 - JSON Logging Verification:** The current configuration at line 218 of application.properties enables JSON logging: `quarkus.log.console.json=${LOG_JSON_FORMAT:true}`. This means JSON logging is **ENABLED BY DEFAULT** and can be overridden via the `LOG_JSON_FORMAT` environment variable. You should verify this works correctly by running the application and checking log output format.

*   **Tip #3 - MDC Configuration is Complete:** Lines 228-231 of application.properties configure MDC field inclusion in JSON logs:
    ```properties
    quarkus.log.console.json.additional-field."correlationId".value=${mdc:correlationId}
    quarkus.log.console.json.additional-field."userId".value=${mdc:userId}
    quarkus.log.console.json.additional-field."roomId".value=${mdc:roomId}
    quarkus.log.console.json.additional-field."action".value=${mdc:action}
    ```
    This ensures all MDC values are automatically serialized into JSON log output. **NO CHANGES NEEDED HERE.**

*   **Tip #4 - Reactive Context Propagation:** Quarkus has built-in support for MDC propagation across reactive chains via the Context Propagation mechanism. The WebSocket handler demonstrates the correct pattern: manually set MDC in each async callback (Uni/Multi subscribe handlers) and clear it in finally blocks. You do NOT need to add any special configuration for reactive propagation - it's handled automatically by Quarkus when you follow this pattern.

*   **Tip #5 - Log Aggregation Configuration:** The task requires "Configure log aggregation destination (Loki, CloudWatch Logs)". However, this is typically done at the **infrastructure level**, not in application code. The application.properties file already has detailed comments (lines 246-251) explaining the log aggregation strategy. You should clarify in your implementation whether this task requires:
    - **Option A:** Just document the log aggregation strategy (already done in comments)
    - **Option B:** Add actual integration code for Loki/CloudWatch (requires additional dependencies)
    - **Most Likely:** Option A is sufficient - structured JSON logs with indexed fields can be shipped to Loki/CloudWatch by external agents (Promtail, CloudWatch agent) without application-level changes.

*   **Tip #6 - Environment-Specific Log Levels:** The configuration already implements the required log levels:
    - Production: WARN (line 239: `%prod.quarkus.log.level=WARN`)
    - Staging: INFO (line 243: `%staging.quarkus.log.level=INFO`)
    - Development: DEBUG (line 256: `%dev.quarkus.log.level=DEBUG`)
    These match the task requirements exactly. **NO CHANGES NEEDED.**

*   **Tip #7 - Testing Strategy:** To verify the implementation meets acceptance criteria, you should:
    1. Start the application in dev mode (`mvn quarkus:dev`)
    2. Make HTTP requests and verify JSON log output includes correlationId
    3. Connect a WebSocket client and verify correlation ID is consistent across messages
    4. Check that MDC fields (userId, roomId) appear in logs when available
    5. Verify log levels change correctly by setting `LOG_LEVEL` or using profile-specific configuration

*   **Warning #1 - Don't Over-Engineer:** The task description might give the impression that significant work is needed, but the codebase analysis reveals the implementation is essentially complete. Focus on verification, testing, and potentially minor documentation improvements rather than rewriting working code.

*   **Warning #2 - MDC Cleanup is Critical:** The existing CorrelationIdFilter (line 168) and WebSocket handlers (multiple locations) properly call `MDC.clear()` in finally blocks. This is **CRITICAL** in thread pool environments to prevent thread-local leakage. Any new code that uses MDC MUST follow this pattern. Never leave MDC values set after a request/callback completes.

*   **Note #1 - Audit Logging is Separate:** The architecture document mentions audit logging, but this is a separate concern from structured application logging. The `AuditLogService` (seen in the codebase) handles compliance audit trails. Don't confuse it with the general application logging system you're configuring in this task.

*   **Note #2 - Production Log Configuration:** In production, logs will be in JSON format and written to stdout/stderr. The infrastructure (Kubernetes) will capture these logs and ship them to the aggregation system (Loki or CloudWatch). The application doesn't need direct integration with these systems - it just needs to output structured JSON logs, which it already does.

### Recommended Implementation Approach

Based on my analysis, here's what you should actually do for this task:

1. **Verification Phase:**
   - Review the existing CorrelationIdFilter implementation (it's complete)
   - Review the application.properties logging configuration (it's complete)
   - Review the WebSocket MDC propagation pattern (it's correct)

2. **Testing Phase:**
   - Write or run integration tests to verify:
     - HTTP requests generate correlation IDs
     - Correlation IDs appear in JSON log output
     - MDC fields are included in logs
     - Environment-specific log levels work correctly
     - WebSocket messages use consistent correlation IDs

3. **Documentation Phase:**
   - Add code comments if any are missing
   - Possibly update the architecture documentation to reflect implementation status
   - Document the log aggregation strategy (if not already sufficient)

4. **Minor Enhancements (Optional):**
   - If log aggregation configuration is truly required, add Loki or CloudWatch appender configuration
   - Add any missing MDC.put() calls in service layer methods (though existing implementation appears comprehensive)

**DO NOT:** Rewrite the CorrelationIdFilter, change the LoggingConstants, or significantly modify application.properties. These files are production-ready.
