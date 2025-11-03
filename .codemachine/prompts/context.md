# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I5.T8",
  "iteration_id": "I5",
  "iteration_goal": "Implement Stripe subscription billing, tier enforcement (Free/Pro/Pro+/Enterprise), payment flows, webhook handling for subscription lifecycle events, and frontend upgrade UI.",
  "description": "Create integration test for Stripe webhook endpoint using `@QuarkusTest`. Mock Stripe webhook events (signature included), send POST to `/api/v1/subscriptions/webhook`, verify database updates. Test events: subscription.created (subscription entity created), invoice.payment_succeeded (PaymentHistory created), subscription.deleted (subscription canceled). Test signature verification (invalid signature rejected). Use Testcontainers for PostgreSQL.",
  "agent_type_hint": "BackendAgent",
  "inputs": "StripeWebhookController from I5.T3, Stripe webhook event JSON examples",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/StripeWebhookController.java"
  ],
  "target_files": [
    "backend/src/test/java/com/scrumpoker/api/rest/StripeWebhookControllerTest.java",
    "backend/src/test/resources/stripe/webhook_subscription_created.json"
  ],
  "deliverables": "Integration test posting webhook events to endpoint, Tests for subscription lifecycle events, Signature verification test (invalid signature → 401), Database assertions (subscription updated, payment created), Idempotency test (duplicate event skipped)",
  "acceptance_criteria": "`mvn verify` runs webhook tests successfully, Subscription created event updates database, Payment succeeded event creates PaymentHistory record, Invalid signature returns 401 Unauthorized, Duplicate event ID skipped (no duplicate processing), All webhook events return 200 OK",
  "dependencies": [
    "I5.T3"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: monetization-requirements (from 01_Context_and_Drivers.md)

```markdown
#### Monetization Requirements
- **Stripe Integration:** Subscription management, payment processing, webhook handling
- **Tier Enforcement:** Feature gating based on subscription level (ads, reports, room privacy, branding)
- **Upgrade Flows:** In-app prompts, modal CTAs, settings panel upsells
- **Billing Dashboard:** Subscription status, payment history, plan management
```

### Context: decision-stripe-billing (from 06_Rationale_and_Future.md)

```markdown
#### 5. Stripe for Subscription Billing

**Decision:** Integrate Stripe for payment processing and subscription lifecycle management rather than building custom billing or using alternatives (PayPal, Braintree).

**Rationale:**
- **Developer Experience:** Best-in-class API design, comprehensive SDKs (Java, TypeScript), extensive documentation and testing tools (Stripe CLI)
- **Subscription Features:** Native support for tiered pricing, metered billing (future), proration, trial periods, coupon codes
- **Webhooks:** Robust event-driven architecture for subscription lifecycle events (created, updated, deleted, payment succeeded/failed)
- **Compliance:** PCI DSS Level 1 certified, handles all payment data security, reduces compliance burden
- **International Support:** Multi-currency, localized payment methods (SEPA, iDEAL, Alipay), built-in tax calculation (Stripe Tax)

**Trade-offs Accepted:**
- **Stripe Fees:** 2.9% + $0.30 per transaction (higher than some alternatives, but offset by reduced development and maintenance costs)
- **Vendor Lock-in:** Migrating away from Stripe would require re-implementing payment workflows, but risk is mitigated by Stripe's market dominance and stability
- **Regional Limitations:** Not available in all countries (use PayPal fallback for unsupported regions if demand exists)

**Rejected Alternatives:**
- **PayPal Subscriptions:** Inferior developer experience, less robust webhook system, dated UI for checkout
- **Braintree:** Owned by PayPal, good API but less feature-rich for subscription management, smaller ecosystem
- **Paddle:** Merchant of Record model handles taxes but takes higher cut (5-7%), less control over customer data
- **Custom Solution:** Build on Authorize.net or similar gateway - too much development overhead, PCI compliance burden, no pre-built subscription management
```

### Context: risk-stripe-webhook-failures (from 06_Rationale_and_Future.md)

```markdown
#### Risk 4: Stripe Webhook Delivery Failures

**Risk Description:** Network issues or application downtime cause missed Stripe webhooks for subscription events (cancellations, payment failures), leading to stale subscription state.

**Impact:** High - Users billed but tier not activated, or subscriptions canceled but features still accessible (revenue leakage).

**Probability:** Medium - Webhook delivery not guaranteed, requires idempotent handling and manual reconciliation.

**Mitigation Strategy:**
1. **Idempotent Webhook Processing:** Log all processed webhook event IDs in `webhook_event_log` table with unique constraint to prevent duplicate processing on retries
2. **Return 200 OK Always:** Even on internal processing failures, acknowledge webhook receipt to prevent Stripe retries flooding the system
3. **Manual Reconciliation:** Implement nightly batch job comparing Stripe subscription status (via List Subscriptions API) with database state, flagging discrepancies for review
4. **Stripe Portal Integration:** Allow users to manage subscriptions in Stripe Customer Portal as source of truth, syncing changes back via webhooks
5. **Monitoring Alerts:** Alert on webhook processing failures (`WebhookEventLog.status = FAILED`) for manual investigation within 1 hour
6. **Webhook Replay:** Use Stripe Dashboard webhook replay feature for missed events identified during reconciliation

**Residual Risk:** Low - Combination of idempotency, reconciliation, and monitoring reduces risk to acceptable level. Manual intervention required only for edge cases (e.g., multi-day outage).
```

### Context: task-i5-t3 (from 02_Iteration_I5.md)

```markdown
*   **Task 5.3: Implement Stripe Webhook Handler**
    *   **Task ID:** `I5.T3`
    *   **Description:** Create REST endpoint `POST /api/v1/subscriptions/webhook` for Stripe webhook events. Verify webhook signature using Stripe webhook secret. Handle events: `customer.subscription.created` (call BillingService.syncSubscriptionStatus with ACTIVE), `customer.subscription.updated` (sync status changes), `customer.subscription.deleted` (sync CANCELED status), `invoice.payment_succeeded` (create PaymentHistory record), `invoice.payment_failed` (sync PAST_DUE status). Use idempotency keys (Stripe event ID) to prevent duplicate processing. Return 200 OK immediately to acknowledge webhook.
    *   **Agent Type:** BackendAgent
    *   **Inputs:** Stripe webhook event types, Webhook signature verification requirements, BillingService from I5.T2
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
        *   `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/StripeWebhookController.java`
        *   `backend/src/main/java/com/scrumpoker/domain/billing/WebhookEventLog.java`
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
    *   **Dependencies:** I5.T2
    *   **Parallelizable:** false
```

### Context: task-i5-t8 (from 02_Iteration_I5.md)

```markdown
*   **Task 5.8: Write Integration Tests for Stripe Webhook**
    *   **Task ID:** `I5.T8`
    *   **Description:** Create integration test for Stripe webhook endpoint using `@QuarkusTest`. Mock Stripe webhook events (signature included), send POST to `/api/v1/subscriptions/webhook`, verify database updates. Test events: subscription.created (subscription entity created), invoice.payment_succeeded (PaymentHistory created), subscription.deleted (subscription canceled). Test signature verification (invalid signature rejected). Use Testcontainers for PostgreSQL.
    *   **Agent Type:** BackendAgent
    *   **Inputs:** StripeWebhookController from I5.T3, Stripe webhook event JSON examples
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/StripeWebhookController.java`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/api/rest/StripeWebhookControllerTest.java`
        *   `backend/src/test/resources/stripe/webhook_subscription_created.json`
    *   **Deliverables:**
        *   Integration test posting webhook events to endpoint
        *   Tests for subscription lifecycle events
        *   Signature verification test (invalid signature → 401)
        *   Database assertions (subscription updated, payment created)
        *   Idempotency test (duplicate event skipped)
    *   **Acceptance Criteria:**
        *   `mvn verify` runs webhook tests successfully
        *   Subscription created event updates database
        *   Payment succeeded event creates PaymentHistory record
        *   Invalid signature returns 401 Unauthorized
        *   Duplicate event ID skipped (no duplicate processing)
        *   All webhook events return 200 OK
    *   **Dependencies:** I5.T3
    *   **Parallelizable:** false
```

### Context: integration-testing (from 03_Verification_and_Glossary.md)

```markdown
#### Integration Testing

**Scope:** Multiple components working together with real infrastructure (database, cache, message queue)

**Framework:** Quarkus Test (`@QuarkusTest`), Testcontainers, REST Assured

**Coverage Target:** Critical integration points (API → Service → Repository → Database)

**Approach:**
- Use Testcontainers for PostgreSQL and Redis (real instances, not mocks)
- Test REST endpoints end-to-end (request → response with database persistence)
- Test WebSocket flows (connection, message handling, disconnection with Redis Pub/Sub)
- Test external integrations (OAuth providers, Stripe webhooks with mocked responses)
- Use `@RunOnVertxContext` for reactive tests requiring Vert.x event loop
- Clean up test data between tests (`@BeforeEach` with repository.deleteAll())

**Example Test Structure:**
```java
@QuarkusTest
@TestProfile(NoSecurityTestProfile.class)
public class RoomControllerTest {
    @Inject RoomRepository roomRepository;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.deleteAll()));
    }

    @Test
    public void testCreateRoom_ValidInput_Returns201() {
        CreateRoomRequest request = new CreateRoomRequest();
        request.title = "Test Room";

        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/v1/rooms")
        .then()
            .statusCode(201)
            .body("roomId", notNullValue())
            .body("title", equalTo("Test Room"));
    }
}
```

**Key Testing Patterns:**
- **REST Assured:** Use `given().when().then()` for HTTP endpoint testing
- **Database Verification:** Use `@RunOnVertxContext` + `UniAsserter` for reactive database queries
- **Reactive Assertions:** Use `UniAsserter.assertThat()` for async database state verification
- **Idempotency Tests:** Send duplicate requests, verify only one database record created
- **Error Scenarios:** Test 400, 401, 403, 404, 500 error responses with proper error body structure
```

### Context: iteration-5 (from 02_Iteration_I5.md)

```markdown
## Iteration 5: Subscription & Billing (Stripe Integration)

**Goal:** Implement Stripe subscription billing, tier enforcement (Free/Pro/Pro+/Enterprise), payment flows, webhook handling for subscription lifecycle events, and frontend upgrade UI.

**Duration:** 10-12 days (Tasks I5.T1 - I5.T8)

**Key Outcomes:**
- Stripe integration for subscription payments
- Subscription tier enforcement via FeatureGate
- Webhook handling for subscription lifecycle events (created, updated, deleted, payment succeeded/failed)
- Frontend pricing page and subscription management UI
- Comprehensive tests for billing logic and webhooks

**Prerequisites:**
- Iteration 1 complete (database schema with Subscription, PaymentHistory entities)
- Iteration 2 complete (REST API foundation, OpenAPI spec)
- Iteration 3 complete (user authentication, JWT tokens)
- Iteration 4 complete (real-time features integrated)

**Tasks:**

*   **Task 5.1:** Implement Stripe Integration Adapter (I5.T1)
*   **Task 5.2:** Implement Billing Service (I5.T2)
*   **Task 5.3:** Implement Stripe Webhook Handler (I5.T3)
*   **Task 5.4:** Implement Subscription Tier Enforcement (I5.T4)
*   **Task 5.5:** Create Subscription REST Controllers (I5.T5)
*   **Task 5.6:** Create Frontend Pricing & Upgrade UI (I5.T6)
*   **Task 5.7:** Write Unit Tests for Billing Service (I5.T7)
*   **Task 5.8:** Write Integration Tests for Stripe Webhook (I5.T8)

**Success Criteria:**
- Users can upgrade to Pro/Pro+/Enterprise tiers via Stripe checkout
- Stripe webhooks process subscription lifecycle events correctly
- Tier enforcement prevents Free users from accessing paid features
- Frontend displays pricing page and subscription management
- All tests pass (`mvn verify`, `npm run test`)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/StripeWebhookController.java`
    *   **Summary:** This is the webhook controller you need to test. It handles Stripe webhook events with signature verification, idempotency checks via `WebhookEventLog`, and event routing to specific handlers for subscription lifecycle events and invoice payments. The controller always returns 200 OK even on processing failures to prevent Stripe retries.
    *   **Key Methods to Test:**
        *   `handleWebhook(String payload, String signatureHeader)` - Main entry point, verifies signature, calls processEventIdempotently
        *   `processEventIdempotently(Event event)` - Checks WebhookEventLog for duplicate events, routes to event handlers, records processing outcome
        *   Event handlers: `handleSubscriptionCreated`, `handleSubscriptionUpdated`, `handleSubscriptionDeleted`, `handleInvoicePaymentSucceeded`, `handleInvoicePaymentFailed`
    *   **Critical Implementation Details:**
        *   Uses `Webhook.constructEvent(payload, signatureHeader, webhookSecret)` for signature verification (from Stripe SDK)
        *   Signature verification failure returns 401 Unauthorized (only failure case that doesn't return 200)
        *   Idempotency implemented via `webhookEventLogRepository.findByEventId(eventId)` check
        *   Processing failures are logged but still return 200 OK
        *   Uses `@Transactional` on `processEventIdempotently` for atomic database updates
    *   **Recommendation:** You MUST mock the Stripe signature verification. The `Webhook.constructEvent()` method requires a valid Stripe signature. You should use the Stripe test webhook signing secret and generate valid signatures for your test payloads, OR mock the signature verification entirely by overriding the webhook secret configuration in tests.

*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/RoomControllerTest.java`
    *   **Summary:** This is an excellent reference for integration test patterns in this codebase. It demonstrates REST Assured usage, Testcontainers setup, database cleanup, and reactive assertions with `UniAsserter`.
    *   **Key Patterns Used:**
        *   `@QuarkusTest` with `@TestProfile(NoSecurityTestProfile.class)` for integration tests
        *   `@BeforeEach` with `@RunOnVertxContext` and `Panache.withTransaction()` for test data cleanup
        *   REST Assured `given().when().then()` pattern for HTTP endpoint testing
        *   `@RunOnVertxContext` with `UniAsserter` for reactive database verification
        *   Database assertions using `asserter.assertThat(() -> repository.findById(...), entity -> assertThat(entity).isNotNull())`
    *   **Recommendation:** You SHOULD follow this exact pattern for your webhook controller tests. Use `@QuarkusTest`, `@TestProfile(NoSecurityTestProfile.class)`, REST Assured for POST requests, and `UniAsserter` for database verification after webhook processing.

*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/NoSecurityTestProfile.java`
    *   **Summary:** Test profile that disables security (OIDC, JWT authentication) for integration tests. Since the webhook endpoint uses `@PermitAll` (no JWT required), this profile is appropriate.
    *   **Recommendation:** You MUST use this test profile in your webhook integration test with `@TestProfile(NoSecurityTestProfile.class)`.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/WebhookEventLog.java`
    *   **Summary:** Entity for storing processed webhook events with `eventId` as primary key and unique constraint. This enforces idempotency at the database level.
    *   **Recommendation:** You MUST verify in your idempotency test that sending the same event twice creates only one `WebhookEventLog` record.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Summary:** Domain service with `syncSubscriptionStatus(stripeSubId, status)` method called by webhook handlers. This method finds the subscription by Stripe ID and updates its status.
    *   **Recommendation:** You do NOT need to mock this service - integration tests should verify the full flow including BillingService interactions with the database.

*   **File:** `backend/src/test/resources/application.properties`
    *   **Summary:** Test configuration with Testcontainers (Dev Services) enabled for PostgreSQL and Redis. Security is disabled, Flyway migrations run at startup.
    *   **Critical Config:** The Stripe webhook secret is configured in `backend/src/main/resources/application.properties` as `stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_test_1234567890abcdefghijklmnopqrstuvwxyz}`
    *   **Recommendation:** You SHOULD override the Stripe webhook secret in your test to use a known test value for generating valid signatures, OR you can mock the signature verification by configuring a test secret that matches your test event signatures.

### Implementation Tips & Notes

*   **Tip #1: Stripe Webhook Signature Generation**
    *   The Stripe SDK provides `Webhook.constructEvent()` which requires a valid signature in the `Stripe-Signature` header.
    *   For testing, you have THREE options:
        1. **Use Real Stripe Webhook Signature:** Generate test events using Stripe CLI (`stripe trigger customer.subscription.created`) and capture the real signature - NOT RECOMMENDED for automated tests
        2. **Generate Valid Signatures:** Use `Webhook.Signature.EXPECTED_SCHEME` and the HMAC-SHA256 algorithm to generate valid signatures for your test payloads - RECOMMENDED
        3. **Mock Signature Verification:** Override `stripe.webhook-secret` in test config to a known value, then generate matching signatures - EASIEST APPROACH
    *   I RECOMMEND option 3: Set a test webhook secret in your test class (e.g., `whsec_test_secret`) and use the Stripe SDK's signature generation utility to create matching signatures.

*   **Tip #2: Idempotency Test Pattern**
    *   To test idempotency, send the SAME webhook payload with the SAME event ID twice.
    *   The first request should process successfully and create a `WebhookEventLog` entry.
    *   The second request should skip processing (logged as "already processed") and NOT create duplicate records.
    *   Verify using database queries: only ONE `WebhookEventLog` entry, only ONE subscription update or payment record.

*   **Tip #3: Database Verification After Webhook Processing**
    *   Webhooks are asynchronous by nature, but in Quarkus integration tests with `@Transactional`, the transaction commits immediately.
    *   Use `@RunOnVertxContext` with `UniAsserter` to verify database state after webhook processing.
    *   Example pattern:
        ```java
        @Test
        @RunOnVertxContext
        public void testSubscriptionCreatedEvent(UniAsserter asserter) {
            // Send webhook POST request
            given().body(payload).header("Stripe-Signature", signature)
                .post("/api/v1/subscriptions/webhook")
                .then().statusCode(200);

            // Verify database state
            asserter.assertThat(() ->
                subscriptionRepository.findByStripeSubscriptionId("sub_123"),
                subscription -> {
                    assertThat(subscription).isNotNull();
                    assertThat(subscription.status).isEqualTo(SubscriptionStatus.ACTIVE);
                }
            );
        }
        ```

*   **Tip #4: Test Event JSON Payloads**
    *   You need to create JSON files in `backend/src/test/resources/stripe/` for each event type.
    *   Stripe provides example webhook payloads in their API documentation.
    *   CRITICAL fields for each event type:
        *   `id`: Event ID (e.g., `"evt_test_webhook_1234"`) - must be unique per test
        *   `type`: Event type (e.g., `"customer.subscription.created"`)
        *   `data.object`: The actual Stripe object (Subscription or Invoice)
    *   Example `webhook_subscription_created.json`:
        ```json
        {
          "id": "evt_test_subscription_created",
          "type": "customer.subscription.created",
          "data": {
            "object": {
              "id": "sub_test_123",
              "status": "active",
              "customer": "cus_test_123"
            }
          }
        }
        ```

*   **Tip #5: Signature Verification Test (401 Scenario)**
    *   To test invalid signature rejection, send a request with an INVALID `Stripe-Signature` header (e.g., `"invalid_signature"`).
    *   The controller should return 401 Unauthorized with error message `{"error": "Invalid webhook signature"}`.
    *   This is the ONLY test case that should expect a non-200 response.

*   **Tip #6: Test Data Setup**
    *   For `invoice.payment_succeeded` tests, you need an existing Subscription in the database that the invoice references.
    *   Create a test subscription in `@BeforeEach` or within the test method using `subscriptionRepository.persist()`.
    *   The invoice payload should reference this subscription's `stripeSubscriptionId`.

*   **Tip #7: Testcontainers Configuration**
    *   The test profile already enables Testcontainers Dev Services for PostgreSQL via `application.properties`.
    *   You do NOT need additional configuration - just add `@QuarkusTest` and Testcontainers will start automatically.
    *   Database is cleaned between tests via Flyway migrations or manual cleanup in `@BeforeEach`.

*   **Warning:** The `StripeWebhookController.processEventIdempotently()` method is marked `protected` for testing purposes. You CAN access it in tests for unit testing individual event handlers, but the PRIMARY integration tests should test the full `handleWebhook()` endpoint via HTTP POST.

*   **Note:** The webhook controller uses reactive `Uni<>` return types. REST Assured handles this automatically, but if you need to test reactive flows directly, use `@RunOnVertxContext` and `UniAsserter`.

### Testing Checklist

Based on the acceptance criteria and deliverables, your test class MUST include these test methods:

1. ✅ **testSubscriptionCreated_ValidEvent_UpdatesDatabase** - POST valid `customer.subscription.created` event, verify subscription created/updated in DB with ACTIVE status
2. ✅ **testSubscriptionUpdated_ValidEvent_UpdatesDatabase** - POST valid `customer.subscription.updated` event, verify subscription status synced
3. ✅ **testSubscriptionDeleted_ValidEvent_UpdatesDatabase** - POST valid `customer.subscription.deleted` event, verify subscription status set to CANCELED
4. ✅ **testInvoicePaymentSucceeded_ValidEvent_CreatesPaymentHistory** - POST valid `invoice.payment_succeeded` event, verify PaymentHistory record created
5. ✅ **testInvoicePaymentFailed_ValidEvent_UpdatesSubscriptionStatus** - POST valid `invoice.payment_failed` event, verify subscription status set to PAST_DUE
6. ✅ **testInvalidSignature_Returns401** - POST event with invalid signature, verify 401 Unauthorized response
7. ✅ **testIdempotency_DuplicateEvent_SkipsProcessing** - POST same event ID twice, verify only one WebhookEventLog entry and no duplicate database records
8. ✅ **testProcessingFailure_Returns200** - POST event that causes processing failure (e.g., subscription not found), verify 200 OK still returned (Stripe retry prevention)

### File Structure for Deliverables

Create these files exactly as specified in target_files:

1. **`backend/src/test/java/com/scrumpoker/api/rest/StripeWebhookControllerTest.java`**
   - Integration test class with all test methods listed above
   - Use `@QuarkusTest`, `@TestProfile(NoSecurityTestProfile.class)`
   - Inject repositories for database verification
   - Clean up test data in `@BeforeEach`

2. **`backend/src/test/resources/stripe/webhook_subscription_created.json`**
   - JSON payload for `customer.subscription.created` event
   - Must include valid Stripe event structure with `id`, `type`, `data.object`

3. **Additional JSON files (recommended for clarity):**
   - `webhook_subscription_updated.json`
   - `webhook_subscription_deleted.json`
   - `webhook_invoice_payment_succeeded.json`
   - `webhook_invoice_payment_failed.json`

---

## Summary

You are implementing **Integration Tests for Stripe Webhook Endpoint** (Task I5.T8). The `StripeWebhookController` has already been implemented in I5.T3 and is fully functional with signature verification, idempotency handling, and event routing.

Your job is to create comprehensive integration tests that verify:
1. All webhook event types process correctly and update the database
2. Signature verification rejects invalid signatures with 401
3. Idempotency prevents duplicate processing of the same event
4. Processing failures still return 200 OK to prevent Stripe retries

Follow the testing patterns from `RoomControllerTest.java`, use Testcontainers via `@QuarkusTest`, and create realistic Stripe webhook JSON payloads in `test/resources/stripe/`.

The test must run successfully with `mvn verify` and achieve the acceptance criteria listed in the task specification.
