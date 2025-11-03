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

### Context: Stripe Webhook Event Handling (from 02_Iteration_I5.md)

```markdown
*   **Task 5.3: Implement Stripe Webhook Handler**
    *   **Task ID:** `I5.T3`
    *   **Description:** Create REST endpoint `POST /api/v1/subscriptions/webhook` for Stripe webhook events. Verify webhook signature using Stripe webhook secret. Handle events: `customer.subscription.created` (call BillingService.syncSubscriptionStatus with ACTIVE), `customer.subscription.updated` (sync status changes), `customer.subscription.deleted` (sync CANCELED status), `invoice.payment_succeeded` (create PaymentHistory record), `invoice.payment_failed` (sync PAST_DUE status). Use idempotency keys (Stripe event ID) to prevent duplicate processing. Return 200 OK immediately to acknowledge webhook.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Stripe webhook event types
        *   Webhook signature verification requirements
        *   BillingService from I5.T2
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
        *   `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/StripeWebhookController.java`
        *   `backend/src/main/java/com/scrumpoker/domain/billing/WebhookEventLog.java` (entity for idempotency)
    *   **Deliverables:**
        *   Webhook endpoint at /api/v1/subscriptions/webhook
        *   Signature verification using Stripe webhook secret
        *   Event handlers for subscription lifecycle events
        *   Payment history creation on successful invoice payment
        *   Idempotency check (store processed event IDs in WebhookEventLog table)
        *   200 OK response even if event processing fails internally (prevents Stripe retries)
    *   **Acceptance Criteria:**
        *   Webhook endpoint receives Stripe events
        *   Signature verification rejects invalid signatures (401 Unauthorized)
        *   Subscription created event updates database subscription status
        *   Payment succeeded event creates PaymentHistory record
        *   Subscription deleted event marks subscription as canceled
        *   Duplicate event IDs skipped (idempotency)
        *   Webhook processing errors logged but return 200 to Stripe
    *   **Dependencies:** [I5.T2]
    *   **Parallelizable:** No (depends on BillingService)
```

### Context: risk-stripe-webhook-failures (from 06_Rationale_and_Future.md)

```markdown
<!-- anchor: risk-stripe-webhook-failures -->
#### Risk 4: Stripe Webhook Delivery Failures

**Risk Description:** Network issues or application downtime cause missed Stripe webhooks for subscription events (cancellations, payment failures), leading to stale subscription state.

**Probability:** Medium - Webhook delivery not guaranteed, requires idempotent handling and manual reconciliation.

**Mitigation Strategies:**

1. **Webhook Retry Logic:** Stripe retries failed webhooks automatically (up to 3 days), endpoint must return 200 OK only after processing
2. **Idempotency Keys:** Store Stripe event IDs in database to prevent duplicate processing on retries
3. **Manual Reconciliation:** Daily batch job queries Stripe API for subscription state, syncs discrepancies
4. **Monitoring:** Alert if webhook processing error rate >1%, manual investigation required
```

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

### Context: REST API Endpoint Pattern (from 04_Behavior_and_Communication.md)

```markdown
**Subscription & Billing Endpoints:**
- `GET /api/v1/subscriptions/{userId}` - Get current subscription (subscription tier, status, billing period)
- `POST /api/v1/subscriptions/checkout` - Create Stripe checkout session, redirect to payment
- `POST /api/v1/subscriptions/{subscriptionId}/cancel` - Cancel subscription (soft cancel, active until period end)
- `GET /api/v1/billing/invoices` - List user's payment history (tier-gated: Pro tier required)
- `POST /api/v1/subscriptions/webhook` - Stripe webhook endpoint (signature verification)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Summary:** Domain service implementing complete subscription lifecycle management. Contains the `syncSubscriptionStatus(stripeSubscriptionId, status)` method which MUST be called by webhook handlers to update subscription state.
    *   **Recommendation:** You MUST inject this service and call `syncSubscriptionStatus()` for subscription lifecycle events. The method is reactive (returns `Uni<Void>`) and handles all database updates including User.subscriptionTier changes.
    *   **Key Method Signature:** `public Uni<Void> syncSubscriptionStatus(final String stripeSubscriptionId, final SubscriptionStatus status)`
    *   **Status Handling Logic:** The service automatically downgrades users to FREE tier when CANCELED status is synced AND the current period has ended. For ACTIVE status, it updates User.subscriptionTier to the subscription tier.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
    *   **Summary:** Stripe SDK wrapper providing all Stripe API integration. Contains configuration for API key and webhook secret.
    *   **Recommendation:** You DO NOT need to create new Stripe API calls. The webhook signature verification is NOT yet implemented in this adapter, so you MUST add it to your webhook controller.
    *   **Configuration Properties:** Webhook secret is available via `@ConfigProperty(name = "stripe.webhook-secret")` which is already configured in application.properties as `${STRIPE_WEBHOOK_SECRET:whsec_test_1234567890abcdefghijklmnopqrstuvwxyz}`

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/Subscription.java`
    *   **Summary:** JPA entity for subscription records with fields: subscriptionId, stripeSubscriptionId, entityId, entityType, tier, status, currentPeriodStart, currentPeriodEnd, canceledAt.
    *   **Recommendation:** You DO NOT directly manipulate this entity. All subscription updates MUST go through BillingService.syncSubscriptionStatus() which handles the entity persistence.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/PaymentHistory.java`
    *   **Summary:** JPA entity for payment transaction records. Contains relationship to Subscription entity via @ManyToOne, fields: paymentId, subscription, stripeInvoiceId, amount, currency, status, paidAt.
    *   **Recommendation:** You MUST create PaymentHistory records for `invoice.payment_succeeded` events. Create the entity, set all required fields (subscription reference, stripeInvoiceId, amount from Stripe invoice data, status, paidAt), and persist via PaymentHistoryRepository.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/SubscriptionRepository.java`
    *   **Summary:** Panache repository with custom finder method `findByStripeSubscriptionId()` which is reactive.
    *   **Recommendation:** You SHOULD use this repository to find subscriptions when creating PaymentHistory records (need to link payment to subscription).

*   **File:** `backend/src/main/java/com/scrumpoker/repository/PaymentHistoryRepository.java`
    *   **Summary:** Panache repository with `findByStripeInvoiceId()` method for idempotency checks on invoice payments.
    *   **Recommendation:** You MUST check for duplicate invoice processing before creating PaymentHistory. Call `findByStripeInvoiceId()` first - if exists, skip creation (idempotency).

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** Reference implementation showing REST controller patterns: JAX-RS annotations, OpenAPI documentation, reactive return types (Uni<Response>), @PermitAll security, structured logging.
    *   **Recommendation:** You SHOULD follow the same patterns: use @Path, @POST, @PermitAll for webhook endpoint, use Uni<Response> return type, add comprehensive OpenAPI annotations, log at INFO level for successful events and ERROR for failures.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Application configuration with Stripe settings already configured.
    *   **Recommendation:** You can access `stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_test_1234567890abcdefghijklmnopqrstuvwxyz}` via `@ConfigProperty(name = "stripe.webhook-secret")` injection.

### Implementation Tips & Notes

*   **Tip:** The Stripe Java SDK provides a built-in webhook signature verification method: `Webhook.constructEvent(payload, sigHeader, endpointSecret)`. You MUST use this in your controller to verify the Stripe-Signature header. If verification fails, return 401 Unauthorized immediately.

*   **Note:** Stripe webhook event structure follows this pattern:
    ```json
    {
      "id": "evt_1234567890",
      "type": "customer.subscription.created",
      "data": {
        "object": { /* subscription or invoice object */ }
      }
    }
    ```
    You MUST extract the event type from `event.getType()` and handle it via a switch statement or similar routing mechanism.

*   **Critical:** The webhook endpoint MUST be annotated with `@PermitAll` because Stripe webhooks do not send JWT tokens. Authentication is handled via signature verification only.

*   **Critical:** You MUST return `Response.ok().build()` (200 OK) even if event processing fails internally. This prevents Stripe from retrying. Log errors but always return 200 to acknowledge receipt. Stripe retries webhooks up to 3 days if they receive non-200 responses.

*   **Idempotency Pattern:** You MUST create a WebhookEventLog entity to store processed Stripe event IDs. Schema should include:
    - `eventId` (String, unique) - Stripe event ID like "evt_1234567890"
    - `eventType` (String) - e.g., "customer.subscription.created"
    - `processedAt` (Instant) - timestamp when event was processed
    - `status` (enum: PROCESSED, FAILED) - processing outcome

    Before processing any event, check if the event ID already exists in WebhookEventLog. If it exists, skip processing (return 200 OK immediately). After successful processing, insert a new WebhookEventLog record.

*   **Event Type Mapping:**
    - `customer.subscription.created` → `BillingService.syncSubscriptionStatus(stripeSubId, SubscriptionStatus.ACTIVE)`
    - `customer.subscription.updated` → Extract status from event.data.object.status, map to SubscriptionStatus enum, call syncSubscriptionStatus
    - `customer.subscription.deleted` → `syncSubscriptionStatus(stripeSubId, SubscriptionStatus.CANCELED)`
    - `invoice.payment_succeeded` → Create PaymentHistory record with status SUCCEEDED
    - `invoice.payment_failed` → `syncSubscriptionStatus(stripeSubId, SubscriptionStatus.PAST_DUE)`

*   **Warning:** The Subscription entity may not exist in the database when `customer.subscription.created` is received if the subscription was created outside the normal flow. You SHOULD handle this gracefully by logging a warning and returning 200 OK (let manual reconciliation handle it).

*   **Status Mapping (Stripe → Domain):** When handling `customer.subscription.updated`, you MUST map Stripe status strings to the SubscriptionStatus enum:
    - "active" → SubscriptionStatus.ACTIVE
    - "past_due" → SubscriptionStatus.PAST_DUE
    - "canceled" → SubscriptionStatus.CANCELED
    - "trialing" → SubscriptionStatus.TRIALING
    - "incomplete", "incomplete_expired", "unpaid" → SubscriptionStatus.PAST_DUE

*   **Transaction Boundaries:** You MUST annotate the webhook processing method with `@Transactional` to ensure idempotency check and event processing are atomic. If processing fails, the transaction rolls back and the WebhookEventLog insert is also rolled back, allowing retry.

*   **Reactive Patterns:** All repository and service calls return `Uni<>` types. You MUST chain them using `.onItem().transformToUni()` or similar operators. The final return should be `Uni<Response>` which JAX-RS will handle asynchronously.

*   **Testing Consideration:** The acceptance criteria requires testing with mocked Stripe events. You will need to create test resources with sample Stripe webhook JSON payloads (e.g., `src/test/resources/stripe/webhook_subscription_created.json`). Use the Stripe SDK's test helpers to generate valid signatures for integration tests.

*   **Database Migration Required:** You MUST create a Flyway migration script for the WebhookEventLog table. This should be added as a new migration file (e.g., `V4__create_webhook_event_log.sql`) in `backend/src/main/resources/db/migration/`. The table needs:
    - Primary key: `event_id` (VARCHAR, unique)
    - `event_type` (VARCHAR)
    - `processed_at` (TIMESTAMP)
    - `status` (VARCHAR or enum)
    - Unique constraint on `event_id`
    - Index on `processed_at` for cleanup queries

*   **Payment Amount Extraction:** When processing `invoice.payment_succeeded`, you MUST extract the amount from the Stripe Invoice object. The Stripe API returns amounts in cents (e.g., 1999 = $19.99). Store this value directly in PaymentHistory.amount field which is Integer type. The currency is available from invoice.getCurrency() (ISO 4217 code like "usd").
