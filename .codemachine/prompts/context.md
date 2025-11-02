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

### Context: Monetization Requirements (from 01_Context_and_Drivers.md)

```markdown
#### Monetization Requirements
- **Stripe Integration:** Subscription management, payment processing, webhook handling
- **Tier Enforcement:** Feature gating based on subscription level (ads, reports, room privacy, branding)
- **Upgrade Flows:** In-app prompts, modal CTAs, settings panel upsells
- **Billing Dashboard:** Subscription status, payment history, plan management
```

### Context: Technology Constraints (from 01_Context_and_Drivers.md)

```markdown
#### Technology Constraints
- **Backend Framework:** Quarkus with Hibernate Reactive (specified requirement)
- **Database:** PostgreSQL for relational data integrity and JSONB support
- **Cache/Message Bus:** Redis for session state distribution and Pub/Sub messaging
- **Payment Provider:** Stripe for subscription billing and payment processing
- **Containerization:** Docker containers orchestrated via Kubernetes
```

### Context: Security and Data Protection (from 05_Operational_Architecture.md)

```markdown
**Data Protection:**
- **Encryption at Rest:** PostgreSQL Transparent Data Encryption (TDE) for sensitive columns (email, payment metadata)
- **PII Handling:** User emails hashed in logs, full values only in database and audit logs
- **Secrets Management:** Kubernetes Secrets for database credentials, OAuth client secrets, JWT signing keys
- **Payment Security:** Stripe tokenization for card details, no PCI-sensitive data stored in application database
```

### Context: Task I5.T1 - Stripe Integration Adapter (from 02_Iteration_I5.md)

```markdown
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
```

### Context: OpenAPI Subscription Endpoints (from api/openapi.yaml)

```yaml
  /api/v1/subscriptions/checkout:
    post:
      tags:
        - Subscriptions
      summary: Create Stripe checkout session for upgrade
      description: |
        Creates a Stripe Checkout session for upgrading to Pro or Pro Plus tier. Returns checkout URL for redirect.
      operationId: createCheckoutSession
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CheckoutRequest'
      responses:
        '200':
          description: Checkout session created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CheckoutResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '500':
          $ref: '#/components/responses/InternalServerError'

    CheckoutRequest:
      type: object
      required:
        - tier
        - successUrl
        - cancelUrl
      properties:
        tier:
          type: string
          enum: [PRO, PRO_PLUS]
          description: Target subscription tier
          example: "PRO"
        successUrl:
          type: string
          format: uri
          description: Redirect URL on successful payment
          example: "https://planningpoker.example.com/billing/success"
        cancelUrl:
          type: string
          format: uri
          description: Redirect URL on canceled payment
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/Subscription.java`
    *   **Summary:** This is the JPA entity representing a Stripe subscription. It stores the subscription's Stripe ID, tier, status, billing period dates, and cancellation timestamp. Uses polymorphic entity references (entityId + entityType) to support both User and Organization subscriptions.
    *   **Recommendation:** You MUST reference this entity to understand the data model. The `stripeSubscriptionId` field (max 100 chars) will store the Stripe subscription ID returned from your adapter. Note the `tier` field uses the `SubscriptionTier` enum from `com.scrumpoker.domain.user.SubscriptionTier`.
    *   **Key Fields:**
        - `subscriptionId` (UUID) - primary key
        - `stripeSubscriptionId` (String, max 100) - Stripe's subscription ID
        - `entityId` (UUID) - references User.userId or Organization.orgId
        - `entityType` (EntityType enum) - USER or ORG
        - `tier` (SubscriptionTier enum) - FREE, PRO, PRO_PLUS, ENTERPRISE
        - `status` (SubscriptionStatus enum) - TRIALING, ACTIVE, PAST_DUE, CANCELED, INCOMPLETE
        - `currentPeriodStart`, `currentPeriodEnd` (Instant) - billing period
        - `canceledAt` (Instant) - soft cancellation timestamp

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/SubscriptionTier.java`
    *   **Summary:** This enum defines the four subscription tier levels.
    *   **Recommendation:** You MUST use this enum when mapping tier parameters to Stripe price IDs. The enum values are: FREE, PRO, PRO_PLUS, ENTERPRISE.
    *   **Note:** FREE tier does not have a Stripe price ID (no charge). Only PRO, PRO_PLUS, and ENTERPRISE need Stripe price IDs in configuration.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** The User entity has a `subscriptionTier` field (SubscriptionTier enum, default FREE) that tracks the current tier. You'll need the User's `userId` and `email` when creating Stripe customers.
    *   **Recommendation:** When creating a Stripe customer, you SHOULD use the User's `email` field as the customer email, and store the User's `userId` in Stripe customer metadata for reference.
    *   **Important:** The User entity uses `userId` as the primary key (UUID), not an auto-increment ID.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/SubscriptionRepository.java`
    *   **Summary:** Reactive Panache repository for Subscription entities with custom finder methods including `findByStripeSubscriptionId(String)` which you'll use for webhook processing.
    *   **Recommendation:** You do NOT need to implement repository methods in this task. The repository is already complete. However, you SHOULD understand it exists for future tasks (I5.T2 will use it extensively).

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** This is the Quarkus application configuration file. It already has sections for database, Redis, JWT, and OIDC configuration.
    *   **Recommendation:** You MUST add a new section for Stripe configuration following the existing patterns. Use environment variable placeholders (e.g., `${STRIPE_API_KEY:sk_test_...}`) with sensible test-mode defaults for development. Follow the pattern used for Google/Microsoft OAuth config.
    *   **Suggested Configuration Keys:**
        - `stripe.api-key=${STRIPE_API_KEY:sk_test_default_key}`
        - `stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_test_default}`
        - `stripe.price.pro=${STRIPE_PRICE_PRO:price_pro_test_id}`
        - `stripe.price.pro-plus=${STRIPE_PRICE_PRO_PLUS:price_proplus_test_id}`
        - `stripe.price.enterprise=${STRIPE_PRICE_ENTERPRISE:price_enterprise_test_id}`

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven POM file with Quarkus dependencies already configured (reactive, database, Redis, OAuth).
    *   **Recommendation:** You MUST add the Stripe Java SDK dependency to the `<dependencies>` section. The latest Stripe Java SDK version is `com.stripe:stripe-java:24.x.x` (check Maven Central for exact version). Add it AFTER the MapStruct dependency and BEFORE the Testing section for good organization.

### Implementation Tips & Notes

*   **Tip:** The project follows a reactive pattern using Quarkus and Mutiny. Your StripeAdapter methods should return synchronous values (not `Uni<>`) because the Stripe SDK is blocking. The service layer (I5.T2) will handle wrapping your adapter calls in `Uni.createFrom().item()` for reactive composition.

*   **Tip:** The existing codebase uses CDI `@ApplicationScoped` beans for service-layer components. Your `StripeAdapter` should be annotated with `@ApplicationScoped` to make it injectable.

*   **Tip:** Configuration injection in Quarkus uses `@ConfigProperty`. You SHOULD inject your Stripe configuration properties like this:
    ```java
    @ConfigProperty(name = "stripe.api-key")
    String stripeApiKey;
    ```

*   **Tip:** For exception handling, the project follows a pattern of creating domain-specific exceptions that extend `RuntimeException`. Your `StripeException` should follow this pattern. Look at `backend/src/main/java/com/scrumpoker/domain/room/RoomNotFoundException.java` as a reference.

*   **Note:** The Stripe Java SDK initializes the API key globally via `Stripe.apiKey = "sk_...";`. You SHOULD do this in a `@PostConstruct` method of your adapter to ensure it's set once when the bean is created.

*   **Note:** When creating checkout sessions, Stripe requires:
    1. Customer ID (create customer first if doesn't exist)
    2. Price ID (from your configuration)
    3. Success and cancel URLs (provided by the controller/caller)
    4. Payment mode (subscription mode for recurring billing)

*   **Warning:** The Stripe SDK throws checked exceptions (`com.stripe.exception.StripeException`). You MUST catch these and wrap them in your custom `com.scrumpoker.integration.stripe.StripeException` (unchecked) to maintain the reactive call chain.

*   **Warning:** NEVER commit real Stripe API keys to source control. The application.properties file should only contain test-mode defaults (keys starting with `sk_test_`). Production keys MUST be provided via environment variables.

*   **Best Practice:** Add a `package-info.java` file in the `com.scrumpoker.integration.stripe` package with JavaDoc describing the integration module, similar to what exists in other packages like `com.scrumpoker.integration.oauth`.

*   **Best Practice:** For the checkout session creation, you should include metadata in the Stripe customer and subscription objects with `userId` and `tier` to facilitate debugging and webhook processing in future tasks.

*   **Testing Consideration:** You don't need to write tests in this task (I5.T7 handles testing), but you SHOULD make your adapter testable by ensuring all Stripe SDK calls can be mocked. Consider using constructor injection for testability, though field injection is acceptable in Quarkus CDI.

### Directory Structure Note

The `backend/src/main/java/com/scrumpoker/integration/` directory already exists with an `oauth` subdirectory. You MUST create a parallel `stripe` subdirectory for consistency:

```
backend/src/main/java/com/scrumpoker/integration/
├── oauth/
│   ├── GoogleOAuthProvider.java
│   ├── MicrosoftOAuthProvider.java
│   ├── OAuth2Adapter.java
│   ├── OAuth2AuthenticationException.java
│   ├── OAuthUserInfo.java
│   └── package-info.java
└── stripe/
    ├── StripeAdapter.java         (YOU CREATE THIS)
    ├── StripeException.java        (YOU CREATE THIS)
    └── package-info.java           (YOU SHOULD CREATE THIS)
```

### Configuration Example

Based on the existing OAuth configuration pattern in application.properties, your Stripe section should look like this:

```properties
# ==========================================
# Stripe Configuration (Billing & Subscriptions)
# ==========================================
# Stripe API Key (secret key for server-side API calls)
# PRODUCTION NOTE: Use test mode key (sk_test_...) for dev/staging
# Set STRIPE_API_KEY environment variable with live key (sk_live_...) in production
stripe.api-key=${STRIPE_API_KEY:sk_test_51234567890abcdefghijklmnopqrstuvwxyz}

# Stripe Webhook Secret for signature verification
# Obtain from Stripe Dashboard → Developers → Webhooks → Signing secret
stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_test_1234567890abcdefghijklmnopqrstuvwxyz}

# Stripe Price IDs for subscription tiers
# Create prices in Stripe Dashboard → Products → Create price
# Each tier maps to a Stripe Price object with recurring billing
stripe.price.pro=${STRIPE_PRICE_PRO:price_1234567890ProMonthly}
stripe.price.pro-plus=${STRIPE_PRICE_PRO_PLUS:price_1234567890ProPlusMonthly}
stripe.price.enterprise=${STRIPE_PRICE_ENTERPRISE:price_1234567890EnterpriseMonthly}

# Note: FREE tier has no Stripe price (no charge)
```

### Method Signatures Expected

Based on the task description and OpenAPI spec, your `StripeAdapter` should have these method signatures:

```java
/**
 * Creates a Stripe checkout session for subscription upgrade.
 *
 * @param userId The user's unique identifier (stored in customer metadata)
 * @param tier The target subscription tier (PRO, PRO_PLUS, or ENTERPRISE)
 * @param successUrl URL to redirect on successful payment
 * @param cancelUrl URL to redirect on canceled payment
 * @return CheckoutSessionResult containing sessionId and checkoutUrl
 * @throws StripeException if Stripe API call fails
 */
public CheckoutSessionResult createCheckoutSession(UUID userId, SubscriptionTier tier, String successUrl, String cancelUrl);

/**
 * Creates a Stripe customer for the user.
 *
 * @param userId The user's unique identifier (stored in metadata)
 * @param email The user's email address (customer email)
 * @return The Stripe customer ID (cus_...)
 * @throws StripeException if customer creation fails
 */
public String createCustomer(UUID userId, String email);

/**
 * Retrieves a Stripe subscription by ID.
 *
 * @param stripeSubscriptionId The Stripe subscription ID (sub_...)
 * @return StripeSubscriptionInfo with subscription details
 * @throws StripeException if retrieval fails
 */
public StripeSubscriptionInfo getSubscription(String stripeSubscriptionId);

/**
 * Cancels a Stripe subscription at period end.
 *
 * @param stripeSubscriptionId The Stripe subscription ID to cancel
 * @throws StripeException if cancellation fails
 */
public void cancelSubscription(String stripeSubscriptionId);

/**
 * Updates a Stripe subscription to a new tier/price.
 *
 * @param stripeSubscriptionId The Stripe subscription ID to update
 * @param newTier The new subscription tier
 * @throws StripeException if update fails
 */
public void updateSubscription(String stripeSubscriptionId, SubscriptionTier newTier);
```

You SHOULD create supporting DTOs/records for `CheckoutSessionResult` and `StripeSubscriptionInfo` to encapsulate the Stripe response data cleanly.

### Maven Dependency to Add

Add this to `backend/pom.xml` in the `<dependencies>` section:

```xml
<!-- Stripe Java SDK for payment processing -->
<dependency>
    <groupId>com.stripe</groupId>
    <artifactId>stripe-java</artifactId>
    <version>24.18.0</version>
</dependency>
```

Place it after the MapStruct dependency and before the Testing dependencies section.

---

**End of Task Briefing Package**

You now have all the information needed to implement the Stripe integration adapter. Focus on:

1. Adding the Stripe dependency to pom.xml
2. Creating the StripeAdapter with the 5 core methods
3. Creating the StripeException custom exception
4. Adding Stripe configuration to application.properties
5. Creating supporting DTOs (CheckoutSessionResult, StripeSubscriptionInfo)
6. Adding package-info.java for documentation

Good luck with the implementation!
