# Project Plan: Scrum Poker Platform - Iteration 5

**Version:** 1.0
**Date:** 2025-10-17

---

<!-- anchor: iteration-5 -->
### Iteration 5: Subscription & Billing (Stripe Integration)

*   **Iteration ID:** `I5`

*   **Goal:** Implement Stripe subscription billing, tier enforcement (Free/Pro/Pro+/Enterprise), payment flows, webhook handling for subscription lifecycle events, and frontend upgrade UI.

*   **Prerequisites:** I3 (User authentication, UserService)

*   **Tasks:**

<!-- anchor: task-i5-t1 -->
*   **Task 5.1: Implement Stripe Integration Adapter**
    *   **Task ID:** `I5.T1`
    *   **Description:** Create `StripeAdapter` service wrapping Stripe Java SDK. Configure Stripe API key (secret key from environment variable). Implement methods: `createCheckoutSession(userId, tier)` (creates Stripe checkout session for subscription), `createCustomer(userId, email)` (creates Stripe customer), `getSubscription(stripeSubscriptionId)`, `cancelSubscription(stripeSubscriptionId)`, `updateSubscription(stripeSubscriptionId, newTier)`. Handle Stripe exceptions, map to domain exceptions. Use Stripe test mode for development/staging.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Stripe integration requirements from architecture blueprint
        *   Stripe Java SDK documentation
        *   Subscription tier pricing (Free: $0, Pro: $10/mo, Pro+: $30/mo, Enterprise: $100/mo)
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/05_Operational_Architecture.md` (billing section)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
        *   `backend/src/main/java/com/scrumpoker/integration/stripe/StripeException.java` (custom exception)
        *   `backend/src/main/resources/application.properties` (Stripe config: api_key, webhook_secret, price_ids)
    *   **Deliverables:**
        *   StripeAdapter with 5 core methods wrapping Stripe SDK
        *   Stripe customer creation linked to User entity
        *   Checkout session creation for subscription upgrades
        *   Subscription retrieval, cancellation, update methods
        *   Exception handling for Stripe API errors
        *   Configuration properties for API key, webhook secret, price IDs
    *   **Acceptance Criteria:**
        *   Checkout session created successfully (returns session ID and URL)
        *   Stripe customer created and ID stored
        *   Subscription retrieved by Stripe ID
        *   Subscription cancellation marks subscription as canceled in Stripe
        *   Stripe exceptions mapped to domain exceptions
        *   Test mode API key used in dev/staging environments
    *   **Dependencies:** []
    *   **Parallelizable:** Yes

<!-- anchor: task-i5-t2 -->
*   **Task 5.2: Implement Billing Service (Subscription Management)**
    *   **Task ID:** `I5.T2`
    *   **Description:** Create `BillingService` domain service managing subscription lifecycle. Methods: `createSubscription(userId, tier)` (create Subscription entity, call StripeAdapter to create Stripe subscription), `upgradeSubscription(userId, newTier)` (update Subscription entity, call Stripe update), `cancelSubscription(userId)` (soft cancel, sets `canceled_at`, subscription active until period end), `getActiveSubscription(userId)`, `syncSubscriptionStatus(stripeSubscriptionId, status)` (called by webhook handler). Use `SubscriptionRepository`. Handle tier transitions (Free → Pro, Pro → Pro+). Update User.subscription_tier on tier change.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Subscription entity from I1
        *   StripeAdapter from I5.T1
        *   Subscription tier enforcement requirements
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/billing/Subscription.java`
        *   `backend/src/main/java/com/scrumpoker/repository/SubscriptionRepository.java`
        *   `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/billing/SubscriptionTier.java` (enum: FREE, PRO, PRO_PLUS, ENTERPRISE)
    *   **Deliverables:**
        *   BillingService with methods: createSubscription, upgradeSubscription, cancelSubscription, getActiveSubscription, syncSubscriptionStatus
        *   Subscription entity creation with Stripe subscription ID
        *   Tier transition logic (validate allowed transitions)
        *   User.subscription_tier update on subscription change
        *   Subscription status sync from Stripe webhooks
    *   **Acceptance Criteria:**
        *   Creating subscription persists to database and creates Stripe subscription
        *   Upgrading tier updates both database and Stripe
        *   Canceling subscription sets `canceled_at`, subscription remains active until period end
        *   Tier enforcement prevents invalid transitions (e.g., Enterprise → Free not allowed directly)
        *   User.subscription_tier reflects current subscription status
        *   Sync method updates subscription status from webhook events
    *   **Dependencies:** [I5.T1]
    *   **Parallelizable:** No (depends on StripeAdapter)

<!-- anchor: task-i5-t3 -->
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

<!-- anchor: task-i5-t4 -->
*   **Task 5.4: Implement Subscription Tier Enforcement**
    *   **Task ID:** `I5.T4`
    *   **Description:** Create `FeatureGate` service enforcing tier-based feature access. Methods: `canCreateInviteOnlyRoom(User)` (Pro+ or Enterprise), `canAccessAdvancedReports(User)` (Pro or higher), `canRemoveAds(User)` (Pro or higher), `canManageOrganization(User)` (Enterprise only). Inject into REST controllers and services. Throw `FeatureNotAvailableException` when user attempts unavailable feature. Implement `@RequiresTier(SubscriptionTier.PRO)` custom annotation for declarative enforcement on REST endpoints. Create interceptor validating tier requirements.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Feature tier matrix from product spec (Free vs. Pro vs. Enterprise)
        *   Subscription tier enum from I5.T2
    *   **Input Files:**
        *   Product specification (tier feature comparison table)
        *   `backend/src/main/java/com/scrumpoker/domain/billing/SubscriptionTier.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
        *   `backend/src/main/java/com/scrumpoker/security/RequiresTier.java` (annotation)
        *   `backend/src/main/java/com/scrumpoker/security/TierEnforcementInterceptor.java`
        *   `backend/src/main/java/com/scrumpoker/security/FeatureNotAvailableException.java`
    *   **Deliverables:**
        *   FeatureGate service with tier check methods
        *   Custom annotation @RequiresTier for declarative enforcement
        *   Interceptor validating tier on annotated endpoints
        *   FeatureNotAvailableException (403 Forbidden + upgrade prompt in message)
        *   Integration in RoomService (check tier before creating invite-only room)
    *   **Acceptance Criteria:**
        *   Free tier user cannot create invite-only room (403 error)
        *   Pro tier user can create invite-only room
        *   Free tier user accessing advanced reports returns 403
        *   Interceptor enforces @RequiresTier annotation on endpoints
        *   Exception message includes upgrade CTA (e.g., "Upgrade to Pro to access this feature")
    *   **Dependencies:** [I5.T2]
    *   **Parallelizable:** Yes (can work parallel with I5.T3)

<!-- anchor: task-i5-t5 -->
*   **Task 5.5: Create Subscription REST Controllers**
    *   **Task ID:** `I5.T5`
    *   **Description:** Implement REST controllers for subscription management per OpenAPI spec. Endpoints: `GET /api/v1/subscriptions/{userId}` (get current subscription), `POST /api/v1/subscriptions/checkout` (create Stripe checkout session, return session URL for redirect), `POST /api/v1/subscriptions/{subscriptionId}/cancel` (cancel subscription), `GET /api/v1/billing/invoices` (list payment history). Use BillingService. Return DTOs matching OpenAPI schemas. Authorize users can only access own subscription data.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   OpenAPI spec for subscription endpoints from I2.T1
        *   BillingService from I5.T2
    *   **Input Files:**
        *   `api/openapi.yaml` (subscription endpoints)
        *   `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/SubscriptionController.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/SubscriptionDTO.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/CreateCheckoutRequest.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/CheckoutSessionResponse.java`
    *   **Deliverables:**
        *   SubscriptionController with 4 endpoints
        *   DTOs for subscription data and checkout session
        *   Checkout endpoint creates Stripe session and returns URL
        *   Cancel endpoint calls BillingService.cancelSubscription
        *   Invoice list endpoint retrieves PaymentHistory records
        *   Authorization checks (user can only access own data)
    *   **Acceptance Criteria:**
        *   GET /subscriptions/{userId} returns current subscription status
        *   POST /subscriptions/checkout returns Stripe checkout session URL
        *   Redirecting to session URL displays Stripe payment page
        *   POST /cancel marks subscription as canceled
        *   GET /invoices returns user's payment history
        *   Unauthorized access returns 403
    *   **Dependencies:** [I5.T2]
    *   **Parallelizable:** No (depends on BillingService)

<!-- anchor: task-i5-t6 -->
*   **Task 5.6: Create Frontend Pricing & Upgrade UI**
    *   **Task ID:** `I5.T6`
    *   **Description:** Implement React components for subscription management. `PricingPage`: display tier comparison table (Free, Pro, Pro+, Enterprise), feature lists, pricing, "Upgrade" buttons calling checkout API. `UpgradeModal`: modal prompting user to upgrade when hitting tier limit (e.g., trying to create invite-only room as Free user), displays tier benefits, "Upgrade Now" button. `SubscriptionSettingsPage`: show current subscription tier, billing status, "Cancel Subscription" button, payment history table. Integrate with subscription API hooks (`useSubscription`, `useCreateCheckout`, `useCancelSubscription`).
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   Subscription tier features from product spec
        *   OpenAPI spec for subscription endpoints
        *   Stripe checkout flow
    *   **Input Files:**
        *   `api/openapi.yaml` (subscription endpoints)
        *   Product specification (pricing and tier features)
    *   **Target Files:**
        *   `frontend/src/pages/PricingPage.tsx`
        *   `frontend/src/components/subscription/UpgradeModal.tsx`
        *   `frontend/src/pages/SubscriptionSettingsPage.tsx`
        *   `frontend/src/components/subscription/TierComparisonTable.tsx`
        *   `frontend/src/services/subscriptionApi.ts` (API hooks)
    *   **Deliverables:**
        *   PricingPage with responsive tier comparison table
        *   Upgrade buttons initiating Stripe checkout (redirect to Stripe)
        *   UpgradeModal triggered on feature gate 403 errors
        *   SubscriptionSettingsPage showing current tier, status, cancel button
        *   Payment history table (invoices, dates, amounts)
        *   React Query hooks for subscription API calls
    *   **Acceptance Criteria:**
        *   PricingPage displays all tiers with features
        *   Clicking "Upgrade" button calls checkout API and redirects to Stripe
        *   Stripe checkout completes, user returned to app with success message
        *   UpgradeModal appears when 403 FeatureNotAvailable error
        *   SubscriptionSettingsPage shows correct tier badge
        *   Cancel subscription button triggers confirmation modal, then API call
        *   Payment history table lists past invoices
    *   **Dependencies:** [I5.T5]
    *   **Parallelizable:** No (depends on API endpoints)

<!-- anchor: task-i5-t7 -->
*   **Task 5.7: Write Unit Tests for Billing Service**
    *   **Task ID:** `I5.T7`
    *   **Description:** Create comprehensive unit tests for `BillingService` using mocked `SubscriptionRepository` and `StripeAdapter`. Test scenarios: create subscription (verify Stripe called, entity persisted), upgrade tier (verify tier transition, Stripe update called), cancel subscription (verify `canceled_at` set), sync subscription status (verify entity updated from webhook event). Test edge cases: duplicate subscription creation, invalid tier transitions, canceled subscription upgrades.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   BillingService from I5.T2
        *   Mockito testing patterns
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/domain/billing/BillingServiceTest.java`
    *   **Deliverables:**
        *   BillingServiceTest with 15+ test methods
        *   Tests for happy paths (create, upgrade, cancel)
        *   Tests for edge cases (duplicate creation, invalid transitions)
        *   Mocked StripeAdapter verifying correct Stripe calls
        *   AssertJ assertions for entity state
    *   **Acceptance Criteria:**
        *   `mvn test` runs billing service tests successfully
        *   Test coverage >90% for BillingService
        *   Subscription creation test verifies Stripe customer created
        *   Tier upgrade test verifies Stripe subscription updated
        *   Cancel test verifies `canceled_at` timestamp set
        *   Invalid transition test throws exception
    *   **Dependencies:** [I5.T2]
    *   **Parallelizable:** Yes (can work parallel with other tasks)

<!-- anchor: task-i5-t8 -->
*   **Task 5.8: Write Integration Tests for Stripe Webhook**
    *   **Task ID:** `I5.T8`
    *   **Description:** Create integration test for Stripe webhook endpoint using `@QuarkusTest`. Mock Stripe webhook events (signature included), send POST to `/api/v1/subscriptions/webhook`, verify database updates. Test events: subscription.created (subscription entity created), invoice.payment_succeeded (PaymentHistory created), subscription.deleted (subscription canceled). Test signature verification (invalid signature rejected). Use Testcontainers for PostgreSQL.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   StripeWebhookController from I5.T3
        *   Stripe webhook event JSON examples
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/StripeWebhookController.java`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/api/rest/StripeWebhookControllerTest.java`
        *   `backend/src/test/resources/stripe/webhook_subscription_created.json` (sample event)
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
    *   **Dependencies:** [I5.T3]
    *   **Parallelizable:** No (depends on webhook handler)

---

**Iteration 5 Summary:**

*   **Deliverables:**
    *   Stripe integration adapter (checkout, subscription management)
    *   BillingService for subscription lifecycle
    *   Stripe webhook handler with signature verification
    *   Subscription tier enforcement (FeatureGate)
    *   Subscription REST API endpoints
    *   Frontend pricing page and upgrade UI
    *   Unit and integration tests for billing logic

*   **Acceptance Criteria (Iteration-Level):**
    *   Users can upgrade subscription via Stripe checkout
    *   Stripe webhooks update subscription status automatically
    *   Tier enforcement prevents free users from accessing premium features
    *   Subscription settings page shows current tier and billing info
    *   Webhook signature verification rejects unauthorized requests
    *   Tests verify subscription lifecycle and payment processing

*   **Estimated Duration:** 2.5 weeks
