# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I5.T3",
  "iteration_id": "I5",
  "iteration_goal": "Implement Stripe subscription billing, tier enforcement (Free/Pro/Pro+/Enterprise), payment flows, webhook handling for subscription lifecycle events, and frontend upgrade UI.",
  "description": "Create REST endpoint `POST /api/v1/subscriptions/webhook` for Stripe webhook events. Verify webhook signature using Stripe webhook secret. Handle events: `customer.subscription.created` (call BillingService.syncSubscriptionStatus with ACTIVE), `customer.subscription.updated` (sync status changes), `customer.subscription.deleted` (sync CANCELED status), `invoice.payment_succeeded` (create PaymentHistory record), `invoice.payment_failed` (sync PAST_DUE status). Use idempotency keys (Stripe event ID) to prevent duplicate processing. Return 200 OK immediately to acknowledge webhook.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Stripe webhook event types, Webhook signature verification requirements, BillingService from I5.T2",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java",
    "backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/StripeWebhookController.java",
    "backend/src/main/java/com/scrumpoker/domain/billing/WebhookEventLog.java"
  ],
  "deliverables": "Webhook endpoint at /api/v1/subscriptions/webhook, Signature verification using Stripe webhook secret, Event handlers for subscription lifecycle events, Payment history creation on successful invoice payment, Idempotency check (store processed event IDs in WebhookEventLog table), 200 OK response even if event processing fails internally (prevents Stripe retries)",
  "acceptance_criteria": "Webhook endpoint receives Stripe events, Signature verification rejects invalid signatures (401 Unauthorized), Subscription created event updates database subscription status, Payment succeeded event creates PaymentHistory record, Subscription deleted event marks subscription as canceled, Duplicate event IDs skipped (idempotency), Webhook processing errors logged but return 200 to Stripe",
  "dependencies": [
    "I5.T2"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Structured Logging Requirements (from 05_Operational_Architecture.md)

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
  - `action` - Semantic action (e.g., `vote.cast`, `room.created`, `subscription.upgraded`)
  - `duration` - Operation latency in milliseconds (for timed operations)
  - `error` - Exception stack trace (for ERROR level)

**Log Levels by Environment:**
- **Development:** DEBUG (verbose SQL queries, WebSocket message payloads)
- **Staging:** INFO (API requests, service method calls, integration events)
- **Production:** WARN (error conditions, performance degradation, security events)
```

### Context: Webhook Endpoint Specification (from api/openapi.yaml)

```yaml
/api/v1/subscriptions/webhook:
  post:
    tags:
      - Subscriptions
    summary: Stripe webhook endpoint
    description: |
      Receives webhook events from Stripe (payment success, subscription canceled, etc.).
      Verifies webhook signature before processing.
    operationId: handleStripeWebhook
    security: []  # Authenticated via Stripe signature
    requestBody:
      required: true
      content:
        application/json:
          schema:
            type: object
            description: Stripe webhook event payload
    responses:
      '200':
        description: Webhook processed successfully
      '400':
        $ref: '#/components/responses/BadRequest'
      '500':
        $ref: '#/components/responses/InternalServerError'
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/StripeWebhookController.java`
    *   **Summary:** THIS FILE ALREADY EXISTS AND IS FULLY IMPLEMENTED. The webhook controller is complete with all required functionality: signature verification, idempotency checks via WebhookEventLog, all five event handlers (subscription created/updated/deleted, invoice payment succeeded/failed), and proper error handling that always returns 200 OK.
    *   **Recommendation:** **CRITICAL - This task (I5.T3) is ALREADY COMPLETE.** Review the existing implementation (447 lines) to verify it meets all acceptance criteria. The file includes comprehensive Javadoc, proper reactive patterns with Uni<>, transaction boundaries, and follows all architectural guidelines.
    *   **Implementation Details:**
        - Uses `Webhook.constructEvent()` for signature verification with 401 on failure
        - Idempotency via `WebhookEventLogRepository.findByEventId()`
        - All 5 event types handled in `processEventByType()` switch statement
        - Payment history creation with duplicate invoice check
        - Status mapping from Stripe to domain enums
        - Always returns 200 OK even on processing failures (via `.onItemOrFailure().transform()`)

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/WebhookEventLog.java`
    *   **Summary:** JPA entity for webhook idempotency already implemented with eventId as primary key, eventType, processedAt, and status (enum: PROCESSED/FAILED) fields.
    *   **Recommendation:** This entity is complete and correctly used by StripeWebhookController. No changes needed.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Summary:** Domain service with complete subscription lifecycle management (createSubscription, upgradeSubscription, cancelSubscription, syncSubscriptionStatus). The `syncSubscriptionStatus()` method is the integration point for webhook handlers.
    *   **Recommendation:** This service is already correctly integrated in StripeWebhookController. The method signature is: `public Uni<Void> syncSubscriptionStatus(final String stripeSubscriptionId, final SubscriptionStatus status)`
    *   **Status Handling:** Automatically downgrades to FREE tier when CANCELED status is synced AND period ended. Updates User.subscriptionTier to subscription tier for ACTIVE status.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
    *   **Summary:** Stripe SDK wrapper providing customer creation, checkout sessions, subscription management, and price/tier mapping. The webhook signature verification is handled separately by StripeWebhookController using the Stripe SDK directly.
    *   **Recommendation:** No changes needed to this file for webhook functionality.

*   **File:** `backend/src/main/resources/db/migration/V4__create_webhook_event_log.sql`
    *   **Summary:** Database migration for webhook_event_log table already exists (created by I5.T3 implementation).
    *   **Recommendation:** Migration is complete with proper schema, unique constraint on event_id, and enum type for status.

*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/RoomControllerTest.java`
    *   **Summary:** Reference example for integration test patterns using @QuarkusTest, @TestProfile(NoSecurityTestProfile.class), @RunOnVertxContext, UniAsserter, and RestAssured.
    *   **Recommendation:** Use this as a template for writing integration tests for the webhook endpoint (required for acceptance criteria verification). Tests should mock Stripe events with valid signatures.

### Implementation Tips & Notes

*   **TASK STATUS:** This task (I5.T3) is **ALREADY COMPLETE**. All deliverables exist and all acceptance criteria are met by the existing StripeWebhookController.java implementation.

*   **Verification Checklist:** To confirm task completion, verify the following acceptance criteria:
    - ✅ Webhook endpoint exists at `/api/v1/subscriptions/webhook` (line 47 in StripeWebhookController.java)
    - ✅ Signature verification implemented with 401 on failure (lines 115-126)
    - ✅ Subscription created event updates database (handleSubscriptionCreated, lines 237-257)
    - ✅ Payment succeeded event creates PaymentHistory (handleInvoicePaymentSucceeded, lines 325-389)
    - ✅ Subscription deleted event marks as canceled (handleSubscriptionDeleted, lines 296-315)
    - ✅ Idempotency via WebhookEventLog (processEventIdempotently, lines 154-196)
    - ✅ Always returns 200 OK on errors (onItemOrFailure transform, lines 130-142)

*   **Next Actions Required:**
    1. **Run existing tests** to verify the implementation works correctly
    2. **Update task manifest** - Change `"done": false` to `"done": true` for task I5.T3 in `.codemachine/artifacts/tasks/tasks_I5.json`
    3. **Move to next task** - Task I5.T4 (Subscription Tier Enforcement) or I5.T8 (Integration Tests for Stripe Webhook) if you want to add more test coverage

*   **Implementation Quality Notes:**
    - The existing code follows all architectural patterns correctly
    - Comprehensive logging with structured format (INFO for success, ERROR for failures)
    - Proper reactive programming with Uni<> types and transformation chains
    - Transaction boundaries correctly applied with @Transactional
    - OpenAPI documentation complete with operation descriptions
    - Error handling gracefully handles deserialization failures
    - Status mapping implemented for all Stripe status values
    - Payment history creation includes idempotency check on invoice ID

*   **Testing Recommendations (for I5.T8):**
    - Create test fixtures with sample Stripe webhook payloads in `src/test/resources/stripe/`
    - Use `@QuarkusTest` with Testcontainers for PostgreSQL
    - Mock Stripe signature generation or use test webhook secrets
    - Test all 5 event types independently
    - Test signature verification failure scenario
    - Test idempotency (send same event twice, verify only processed once)
    - Test missing subscription handling (webhook for non-existent subscription)

*   **Database Schema:** The WebhookEventLog table was created by migration V4__create_webhook_event_log.sql with:
    - event_id VARCHAR(100) PRIMARY KEY
    - event_type VARCHAR(100) NOT NULL
    - processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    - status webhook_event_status_enum NOT NULL (PROCESSED or FAILED)
    - Unique constraint on event_id ensures idempotency
