# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I5.T1",
  "iteration_id": "I5",
  "iteration_goal": "Implement Stripe subscription billing, tier enforcement (Free/Pro/Pro+/Enterprise), payment flows, webhook handling for subscription lifecycle events, and frontend upgrade UI.",
  "description": "Create `StripeAdapter` service wrapping Stripe Java SDK. Configure Stripe API key (secret key from environment variable). Implement methods: `createCheckoutSession(userId, tier)` (creates Stripe checkout session for subscription), `createCustomer(userId, email)` (creates Stripe customer), `getSubscription(stripeSubscriptionId)`, `cancelSubscription(stripeSubscriptionId)`, `updateSubscription(stripeSubscriptionId, newTier)`. Handle Stripe exceptions, map to domain exceptions. Use Stripe test mode for development/staging.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Stripe integration requirements from architecture blueprint, Stripe Java SDK documentation, Subscription tier pricing (Free: $0, Pro: $10/mo, Pro+: $30/mo, Enterprise: $100/mo)",
  "input_files": [
    ".codemachine/artifacts/architecture/05_Operational_Architecture.md"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java",
    "backend/src/main/java/com/scrumpoker/integration/stripe/StripeException.java",
    "backend/src/main/resources/application.properties"
  ],
  "deliverables": "StripeAdapter with 5 core methods wrapping Stripe SDK, Stripe customer creation linked to User entity, Checkout session creation for subscription upgrades, Subscription retrieval, cancellation, update methods, Exception handling for Stripe API errors, Configuration properties for API key, webhook secret, price IDs",
  "acceptance_criteria": "Checkout session created successfully (returns session ID and URL), Stripe customer created and ID stored, Subscription retrieved by Stripe ID, Subscription cancellation marks subscription as canceled in Stripe, Stripe exceptions mapped to domain exceptions, Test mode API key used in dev/staging environments",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Data Protection & Payment Security (from 05_Operational_Architecture.md)

```markdown
**Data Protection:**
- **Encryption at Rest:** PostgreSQL Transparent Data Encryption (TDE) for sensitive columns (email, payment metadata)
- **PII Handling:** User emails hashed in logs, full values only in database and audit logs
- **Secrets Management:** Kubernetes Secrets for database credentials, OAuth client secrets, JWT signing keys
- **Payment Security:** Stripe tokenization for card details, no PCI-sensitive data stored in application database
```

### Context: Stripe Configuration Section (from application.properties)

```markdown
# ==========================================
# Stripe Configuration (Billing & Subscriptions)
# ==========================================
# Stripe API Key (secret key for server-side API calls)
# PRODUCTION NOTE: Use test mode key (sk_test_...) for dev/staging
# Set STRIPE_API_KEY environment variable with live key (sk_live_...) in production
stripe.api-key=${STRIPE_API_KEY:sk_test_51234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789}

# Stripe Webhook Secret for signature verification
# Obtain from Stripe Dashboard → Developers → Webhooks → Signing secret
stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_test_1234567890abcdefghijklmnopqrstuvwxyz}

# Stripe Price IDs for subscription tiers
# Create prices in Stripe Dashboard → Products → Create price
# Each tier maps to a Stripe Price object with recurring billing
# Note: FREE tier has no Stripe price (no charge)
stripe.price.pro=${STRIPE_PRICE_PRO:price_1234567890ProMonthly}
stripe.price.pro-plus=${STRIPE_PRICE_PRO_PLUS:price_1234567890ProPlusMonthly}
stripe.price.enterprise=${STRIPE_PRICE_ENTERPRISE:price_1234567890EnterpriseMonthly}
```

### Context: Subscription Tier Pricing (from task inputs)

```markdown
**Subscription Tier Pricing:**
- **FREE:** $0/month (no Stripe price, default tier for all users)
- **PRO:** $10/month (requires Stripe checkout session)
- **PRO_PLUS:** $30/month (requires Stripe checkout session)
- **ENTERPRISE:** $100/month (requires Stripe checkout session, unlocks organization features)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
    *   **Summary:** **THIS FILE ALREADY EXISTS AND IS FULLY IMPLEMENTED.** The StripeAdapter class is complete with all 5 required methods: `createCheckoutSession()`, `createCustomer()`, `getSubscription()`, `cancelSubscription()`, and `updateSubscription()`. The implementation includes proper Stripe SDK integration, error handling, logging, and configuration management.
    *   **Recommendation:** **YOU SHOULD VERIFY THIS FILE IS COMPLETE AND MARK THE TASK AS DONE.** The task deliverables have already been satisfied. The file contains 381 lines of production-ready code with comprehensive Javadoc documentation.
    *   **Implementation Details Found:**
        - Stripe SDK initialized in `@PostConstruct init()` method with API key from configuration
        - Checkout session creation with metadata (userId, tier) for webhook processing
        - Customer creation with email and userId metadata
        - Subscription retrieval with mapping to domain `StripeSubscriptionInfo` DTO
        - Cancellation using `cancel_at_period_end=true` (graceful cancellation)
        - Subscription updates with proration enabled
        - Complete error handling wrapping `com.stripe.exception.StripeException` into domain `StripeException`
        - Helper methods for tier ↔ price ID mapping
        - Helper methods for Stripe status ↔ domain status mapping

*   **File:** `backend/src/main/java/com/scrumpoker/integration/stripe/StripeException.java`
    *   **Summary:** **THIS FILE ALREADY EXISTS AND IS COMPLETE.** The custom exception class wraps Stripe SDK exceptions into unchecked runtime exceptions for reactive programming patterns.
    *   **Recommendation:** This file is fully implemented with three constructors for different exception scenarios. No changes needed.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/stripe/CheckoutSessionResult.java`
    *   **Summary:** **THIS FILE EXISTS.** This is a record DTO containing `sessionId` and `checkoutUrl` returned by the `createCheckoutSession()` method.
    *   **Recommendation:** This supporting class is already implemented. No changes needed.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/stripe/StripeSubscriptionInfo.java`
    *   **Summary:** **THIS FILE EXISTS.** This is a record DTO mapping Stripe subscription data to domain fields, including `subscriptionId`, `customerId`, `tier`, `status`, billing period timestamps, and `canceledAt`.
    *   **Recommendation:** This supporting class is already implemented and maps correctly to domain enums (`SubscriptionTier`, `SubscriptionStatus`). No changes needed.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** **STRIPE CONFIGURATION ALREADY EXISTS.** Lines 113-130 contain complete Stripe configuration with environment variable placeholders for API key, webhook secret, and price IDs for all three paid tiers.
    *   **Recommendation:** Configuration is complete. The default values are placeholders (test mode keys), which is correct for development. Production deployment will override with real environment variables.

*   **File:** `backend/pom.xml`
    *   **Summary:** Stripe Java SDK dependency already added at line 140: `stripe-java` version 24.18.0.
    *   **Recommendation:** Dependency is correctly configured. This is a current version of the Stripe SDK (released 2024).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/SubscriptionTier.java`
    *   **Summary:** Enum defining the four subscription tiers: `FREE`, `PRO`, `PRO_PLUS`, `ENTERPRISE`.
    *   **Recommendation:** This enum is referenced by the StripeAdapter for tier mapping. It correctly matches the database `subscription_tier_enum` type.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** User entity with `subscriptionTier` field (line 59) storing the current tier. Default value is `SubscriptionTier.FREE`.
    *   **Recommendation:** The User entity is ready to track subscription tier changes. When implementing the BillingService (next task I5.T2), you will update this field when subscriptions are created/upgraded.

### Implementation Tips & Notes

*   **Tip:** **THE TASK IS ALREADY COMPLETE.** All three target files specified in the task are fully implemented and production-ready. The StripeAdapter has been implemented ahead of schedule (likely completed during an earlier iteration or setup phase).

*   **Note:** The implementation uses **synchronous (blocking) Stripe SDK calls**, as documented in the class Javadoc comment (line 28-29 of StripeAdapter.java). This is intentional and correct—the service layer (I5.T2 BillingService) will wrap these calls in reactive `Uni<>` types using `Uni.createFrom().item(() -> stripeAdapter.methodCall())` to maintain non-blocking behavior.

*   **Note:** The StripeAdapter correctly handles the **FREE tier edge case**—the `getPriceIdForTier()` helper method returns `null` for FREE tier (line 354), and both `createCheckoutSession()` and `updateSubscription()` validate and throw `StripeException` if called with FREE tier (lines 94-98, 243-246). This is correct because FREE tier users should never interact with Stripe billing.

*   **Note:** Subscription cancellation uses `cancel_at_period_end=true` (line 210), which is the **graceful cancellation pattern** recommended by Stripe. The subscription remains active until the end of the current billing period, preventing immediate service disruption.

*   **Note:** The implementation includes **comprehensive logging** using JBoss Logger at INFO level for operational visibility (session creation, customer creation, subscription updates) and ERROR level for Stripe API failures with full stack traces.

*   **Warning:** The configuration properties in `application.properties` use **placeholder default values** (test API keys). These are intentionally fake values for local development. In production/staging, you MUST set real Stripe credentials via environment variables: `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_PRO`, `STRIPE_PRICE_PRO_PLUS`, `STRIPE_PRICE_ENTERPRISE`.

*   **Warning:** The task acceptance criteria mentions "Stripe customer created and ID stored". The current StripeAdapter returns the customer ID as a String (line 159). The BillingService (I5.T2) will be responsible for storing this in the User or Subscription entity. You may need to add a `stripeCustomerId` field to the User entity or Subscription entity in the next task.

### Acceptance Criteria Verification

Based on my code review, here's the status of each acceptance criterion:

1. ✅ **Checkout session created successfully (returns session ID and URL):** Implemented in lines 85-133, returns `CheckoutSessionResult` record with both fields.

2. ✅ **Stripe customer created and ID stored:** Customer creation implemented in lines 143-169, returns customer ID string. Note: Storage in database entity is deferred to I5.T2 BillingService.

3. ✅ **Subscription retrieved by Stripe ID:** Implemented in lines 178-193, calls `Subscription.retrieve()` and maps to `StripeSubscriptionInfo` DTO.

4. ✅ **Subscription cancellation marks subscription as canceled in Stripe:** Implemented in lines 203-226, uses `cancel_at_period_end=true` parameter.

5. ✅ **Stripe exceptions mapped to domain exceptions:** All methods wrap `com.stripe.exception.StripeException` in custom `StripeException` (lines 124-132, 161-168, 186-192, 219-225, 277-284).

6. ✅ **Test mode API key used in dev/staging environments:** Configuration property defaults to test mode key (line 118), with detection logged at initialization (line 71-72).

### Recommended Next Steps

1. **Verify the implementation** by reviewing the StripeAdapter.java file yourself to confirm it meets all task requirements.

2. **Mark task I5.T1 as DONE** by updating `.codemachine/artifacts/tasks/tasks_I5.json` and setting `"done": true` for this task.

3. **Run the application** in dev mode (`mvn quarkus:dev`) to verify Stripe SDK initialization succeeds with the test API key.

4. **Optionally write a simple integration test** (though not required by this task) to verify Stripe API connectivity. You could use Stripe's test mode API to create a test customer and verify the customer ID is returned.

5. **Proceed to I5.T2 (BillingService)**, which will use this StripeAdapter to implement the domain service layer for subscription management. You will need to decide whether to store `stripeCustomerId` in the User entity or create it lazily in the BillingService when needed.

---

## 4. Additional Context

### Stripe SDK Version Information

The project uses **Stripe Java SDK version 24.18.0** (from pom.xml line 141). This is a recent version released in 2024 with support for:
- Checkout Sessions v2 API (used for subscription checkout)
- Customer Portal integration (for user self-service subscription management)
- Payment Intent API (for one-time payments, if needed in future)
- Webhook signature verification (required for I5.T3 webhook handler)

### Project-Wide Exception Handling Pattern

The codebase follows a consistent pattern for external service integration exceptions:
- OAuth integration uses `OAuth2AuthenticationException` (wraps OAuth provider errors)
- Stripe integration uses `StripeException` (wraps Stripe SDK errors)
- Domain layer uses `RoomNotFoundException`, `UserNotFoundException` (business logic errors)

All exception classes extend `RuntimeException` (unchecked) to avoid breaking reactive call chains (Mutiny `Uni<>` types).

### Configuration Property Naming Convention

The project uses kebab-case for configuration keys with dot-separated namespaces:
- `stripe.api-key` (not `stripe.apiKey` or `STRIPE_API_KEY`)
- `stripe.price.pro` (not `stripe.priceIdPro`)

Environment variable overrides use UPPER_SNAKE_CASE:
- `STRIPE_API_KEY` → `stripe.api-key`
- `STRIPE_PRICE_PRO` → `stripe.price.pro`

This follows MicroProfile Config specification conventions used by Quarkus.
