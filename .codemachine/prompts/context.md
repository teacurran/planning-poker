# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I5.T2",
  "iteration_id": "I5",
  "iteration_goal": "Implement Stripe subscription billing, tier enforcement (Free/Pro/Pro+/Enterprise), payment flows, webhook handling for subscription lifecycle events, and frontend upgrade UI.",
  "description": "Create `BillingService` domain service managing subscription lifecycle. Methods: `createSubscription(userId, tier)` (create Subscription entity, call StripeAdapter to create Stripe subscription), `upgradeSubscription(userId, newTier)` (update Subscription entity, call Stripe update), `cancelSubscription(userId)` (soft cancel, sets `canceled_at`, subscription active until period end), `getActiveSubscription(userId)`, `syncSubscriptionStatus(stripeSubscriptionId, status)` (called by webhook handler). Use `SubscriptionRepository`. Handle tier transitions (Free → Pro, Pro → Pro+). Update User.subscription_tier on tier change.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Subscription entity from I1, StripeAdapter from I5.T1, Subscription tier enforcement requirements",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/billing/Subscription.java",
    "backend/src/main/java/com/scrumpoker/repository/SubscriptionRepository.java",
    "backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java",
    "backend/src/main/java/com/scrumpoker/domain/billing/SubscriptionTier.java"
  ],
  "deliverables": "BillingService with methods: createSubscription, upgradeSubscription, cancelSubscription, getActiveSubscription, syncSubscriptionStatus, Subscription entity creation with Stripe subscription ID, Tier transition logic (validate allowed transitions), User.subscription_tier update on subscription change, Subscription status sync from Stripe webhooks",
  "acceptance_criteria": "Creating subscription persists to database and creates Stripe subscription, Upgrading tier updates both database and Stripe, Canceling subscription sets `canceled_at`, subscription remains active until period end, Tier enforcement prevents invalid transitions (e.g., Enterprise → Free not allowed directly), User.subscription_tier reflects current subscription status, Sync method updates subscription status from webhook events",
  "dependencies": ["I5.T1"],
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

### Context: subscription-tier-pricing (from 02_Iteration_I5.md)

```markdown
Subscription tier pricing (Free: $0, Pro: $10/mo, Pro+: $30/mo, Enterprise: $100/mo)
```

### Context: task-i5-t2 (from 02_Iteration_I5.md)

```markdown
**Task 5.2: Implement Billing Service (Subscription Management)**
    *   **Task ID:** `I5.T2`
    *   **Description:** Create `BillingService` domain service managing subscription lifecycle. Methods: `createSubscription(userId, tier)` (create Subscription entity, call StripeAdapter to create Stripe subscription), `upgradeSubscription(userId, newTier)` (update Subscription entity, call Stripe update), `cancelSubscription(userId)` (soft cancel, sets `canceled_at`, subscription active until period end), `getActiveSubscription(userId)`, `syncSubscriptionStatus(stripeSubscriptionId, status)` (called by webhook handler). Use `SubscriptionRepository`. Handle tier transitions (Free → Pro, Pro → Pro+). Update User.subscription_tier on tier change.
    *   **Acceptance Criteria:**
        *   Creating subscription persists to database and creates Stripe subscription
        *   Upgrading tier updates both database and Stripe
        *   Canceling subscription sets `canceled_at`, subscription remains active until period end
        *   Tier enforcement prevents invalid transitions (e.g., Enterprise → Free not allowed directly)
        *   User.subscription_tier reflects current subscription status
        *   Sync method updates subscription status from webhook events
```

### Context: authentication-and-authorization (from 05_Operational_Architecture.md)

```markdown
**Role-Based Access Control (RBAC):**
- **Roles:** `ANONYMOUS`, `USER`, `PRO_USER`, `ORG_ADMIN`, `ORG_MEMBER`
- **Implementation:** Quarkus Security annotations (`@RolesAllowed`) on REST endpoints and service methods
- **JWT Claims:** Access token includes `roles` array for authorization decisions
- **Dynamic Role Mapping:** Subscription tier (`FREE`, `PRO`, `PRO_PLUS`, `ENTERPRISE`) mapped to roles during token generation
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
    *   **Summary:** This is a fully implemented Stripe integration adapter that wraps the Stripe Java SDK. It provides methods for creating checkout sessions, creating Stripe customers, retrieving subscriptions, canceling subscriptions, and updating subscriptions. The adapter is already configured with Stripe API keys and price IDs for each tier (PRO, PRO_PLUS, ENTERPRISE). All methods are synchronous/blocking as the Stripe SDK is blocking.
    *   **Recommendation:** You MUST import and use this `StripeAdapter` class in your `BillingService`. Call its methods to interact with Stripe. The adapter methods throw `StripeException` which you should catch and handle appropriately.
    *   **Key Methods to Use:**
        *   `createCustomer(UUID userId, String email)` - Returns Stripe customer ID (String)
        *   `createCheckoutSession(UUID userId, SubscriptionTier tier, String successUrl, String cancelUrl)` - Returns `CheckoutSessionResult` with sessionId and URL
        *   `getSubscription(String stripeSubscriptionId)` - Returns `StripeSubscriptionInfo` DTO
        *   `cancelSubscription(String stripeSubscriptionId)` - Cancels subscription at period end (void)
        *   `updateSubscription(String stripeSubscriptionId, SubscriptionTier newTier)` - Updates subscription to new tier with proration (void)

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/Subscription.java`
    *   **Summary:** This is the JPA entity for subscription records. It uses polymorphic entity references (can be linked to User OR Organization via `entityId` and `entityType`). The entity includes fields for `stripeSubscriptionId`, `tier`, `status`, `currentPeriodStart`, `currentPeriodEnd`, `canceledAt`, and timestamps.
    *   **Recommendation:** You MUST use this entity when creating/updating subscription records. Create new instances with `new Subscription()`, set all required fields, and use the repository to persist. The `canceledAt` field MUST be set when canceling subscriptions.
    *   **Important Fields:**
        *   `subscriptionId` (UUID) - Primary key, auto-generated
        *   `stripeSubscriptionId` (String) - Links to Stripe subscription (required, unique)
        *   `entityId` (UUID) - Foreign key to User or Organization
        *   `entityType` (EntityType enum) - USER or ORG
        *   `tier` (SubscriptionTier enum) - FREE, PRO, PRO_PLUS, ENTERPRISE
        *   `status` (SubscriptionStatus enum) - ACTIVE, CANCELED, PAST_DUE, TRIALING
        *   `canceledAt` (Instant) - Timestamp when subscription was canceled (null if active)

*   **File:** `backend/src/main/java/com/scrumpoker/repository/SubscriptionRepository.java`
    *   **Summary:** This is the reactive Panache repository for Subscription entities. It provides custom finder methods for common queries, including `findActiveByEntityIdAndType`, `findByStripeSubscriptionId`, and `findByStatus`. All methods return reactive `Uni<>` types.
    *   **Recommendation:** You MUST inject this repository using `@Inject SubscriptionRepository subscriptionRepository;`. Use its reactive methods to query and persist subscriptions. The most important method for this task is `findActiveByEntityIdAndType(UUID entityId, EntityType entityType)` which returns the active subscription for a user.
    *   **Key Methods:**
        *   `findActiveByEntityIdAndType(UUID entityId, EntityType entityType)` - Returns active subscription for user/org
        *   `findByStripeSubscriptionId(String stripeSubscriptionId)` - Finds subscription by Stripe ID (for webhook sync)
        *   `persist(Subscription)` - Saves new or updated subscription (inherited from Panache)

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** This is the User JPA entity with OAuth authentication fields. It includes a `subscriptionTier` field (enum) that must be kept in sync with the Subscription entity. Default value is `SubscriptionTier.FREE`.
    *   **Recommendation:** You MUST update the `User.subscriptionTier` field whenever a subscription is created, upgraded, or canceled. Use the `UserRepository` to fetch the user and update this field. This field is used for JWT claims and authorization decisions.
    *   **Critical Field:** `subscriptionTier` (SubscriptionTier enum) - MUST be updated to match the active subscription tier

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/SubscriptionTier.java`
    *   **Summary:** This is the SubscriptionTier enum with values: FREE, PRO, PRO_PLUS, ENTERPRISE. This enum is already defined and matches the database subscription_tier_enum type.
    *   **Recommendation:** NOTE that the target file `backend/src/main/java/com/scrumpoker/domain/billing/SubscriptionTier.java` is INCORRECT. The SubscriptionTier enum already exists in the `domain.user` package, NOT in `domain.billing`. You do NOT need to create this file. Use the existing enum from `com.scrumpoker.domain.user.SubscriptionTier`.

### Implementation Tips & Notes

*   **Tip:** The StripeAdapter methods are synchronous/blocking. You MUST wrap them in reactive `Uni` types using `Uni.createFrom().item(() -> stripeAdapter.someMethod())` or use `@Blocking` annotation on service methods to run them on a worker thread.

*   **Note:** The task requires tier transition validation. You SHOULD implement logic to prevent invalid transitions (e.g., Enterprise → Free is not allowed directly, must cancel subscription instead). Valid upgrade paths are: FREE → PRO, FREE → PRO_PLUS, FREE → ENTERPRISE, PRO → PRO_PLUS, PRO → ENTERPRISE, PRO_PLUS → ENTERPRISE. Downgrades should use the cancel flow.

*   **Note:** The `Subscription.canceledAt` timestamp MUST be set when `cancelSubscription` is called, but the subscription status remains ACTIVE until the current period ends. This allows users to continue using premium features until their billing period expires.

*   **Tip:** For the `syncSubscriptionStatus` method (used by webhook handler), you should update the Subscription entity status and related timestamps based on the Stripe webhook event. This method should also update the User.subscriptionTier to reflect the current state.

*   **Warning:** The Subscription entity uses a polymorphic design with `entityId` + `entityType`. For this task, focus on USER subscriptions (set `entityType = EntityType.USER` and `entityId = userId`). Organization subscriptions will be handled in a later iteration.

*   **Tip:** You will need to inject `UserRepository` to fetch and update the User entity's `subscriptionTier` field. Use the reactive pattern: fetch user → update tier → persist user.

*   **Note:** The project uses Quarkus reactive patterns with Mutiny. All service methods SHOULD return `Uni<>` for single results or `Multi<>` for streams. Chain operations using `.onItem().transform()`, `.onItem().transformToUni()`, and `.flatMap()`.

*   **Tip:** Use `@ApplicationScoped` annotation on the BillingService class to make it a CDI bean. Use `@Inject` for dependency injection of StripeAdapter, SubscriptionRepository, and UserRepository.

*   **Warning:** The StripeAdapter already handles mapping between Stripe subscription objects and domain DTOs (see `StripeSubscriptionInfo` class). You should use this DTO when working with Stripe subscription data.

*   **Tip:** For error handling, catch `StripeException` from the adapter and decide whether to propagate it or wrap it in a domain-specific exception. Consider creating a `BillingException` or `SubscriptionException` for domain-layer errors.

*   **Note:** The acceptance criteria requires that "User.subscription_tier reflects current subscription status". This means whenever subscription state changes (create, upgrade, cancel, sync from webhook), you MUST update the User entity's subscriptionTier field to match the Subscription entity's tier field.

*   **Tip:** The `getActiveSubscription` method should use `SubscriptionRepository.findActiveByEntityIdAndType(userId, EntityType.USER)` to fetch the active subscription. This method returns null if no active subscription exists (user is on FREE tier).

*   **Warning:** FREE tier users do not have a Subscription entity in the database. They only have `User.subscriptionTier = FREE`. When creating a subscription for a FREE tier user, you are creating their FIRST subscription record. This is different from upgrading between paid tiers.

*   **Best Practice:** Add transaction management using `@Transactional` annotation on methods that modify database state. This ensures atomic operations (e.g., creating subscription + updating user tier happens together or not at all).

*   **Best Practice:** Add comprehensive logging using `org.jboss.logging.Logger` to track subscription lifecycle events. Log at INFO level for successful operations and ERROR level for failures. Include userId and tier in log messages.

*   **Testing Consideration:** While you don't need to write tests in this task (I5.T7 handles testing), ensure your service methods are testable by using constructor or field injection for all dependencies.

### Method Signatures Expected

Based on the task description, your `BillingService` should have these method signatures:

```java
/**
 * Creates a new subscription for a user.
 * Creates Stripe subscription and persists to database.
 * Updates User.subscriptionTier to match new tier.
 *
 * @param userId The user's unique identifier
 * @param tier The target subscription tier (PRO, PRO_PLUS, or ENTERPRISE)
 * @return Uni containing the created Subscription entity
 */
public Uni<Subscription> createSubscription(UUID userId, SubscriptionTier tier);

/**
 * Upgrades an existing subscription to a higher tier.
 * Updates both database and Stripe subscription.
 * Validates tier transition is allowed.
 * Updates User.subscriptionTier to match new tier.
 *
 * @param userId The user's unique identifier
 * @param newTier The new subscription tier
 * @return Uni containing the updated Subscription entity
 */
public Uni<Subscription> upgradeSubscription(UUID userId, SubscriptionTier newTier);

/**
 * Cancels a user's subscription (soft cancel).
 * Sets canceledAt timestamp but subscription remains active until period end.
 * Does NOT immediately update User.subscriptionTier (stays active until period end).
 *
 * @param userId The user's unique identifier
 * @return Uni<Void> indicating completion
 */
public Uni<Void> cancelSubscription(UUID userId);

/**
 * Gets the active subscription for a user.
 * Returns null if user is on FREE tier (no subscription).
 *
 * @param userId The user's unique identifier
 * @return Uni containing the active Subscription or null
 */
public Uni<Subscription> getActiveSubscription(UUID userId);

/**
 * Syncs subscription status from Stripe webhook events.
 * Updates Subscription entity and User.subscriptionTier based on Stripe status.
 * Called by webhook handler (I5.T3).
 *
 * @param stripeSubscriptionId The Stripe subscription ID
 * @param status The new subscription status from Stripe
 * @return Uni<Void> indicating completion
 */
public Uni<Void> syncSubscriptionStatus(String stripeSubscriptionId, SubscriptionStatus status);
```

### Reactive Pattern Example

Here's how to properly chain reactive operations with blocking Stripe calls:

```java
public Uni<Subscription> createSubscription(UUID userId, SubscriptionTier tier) {
    return userRepository.findById(userId)
        .onItem().ifNull().failWith(() -> new UserNotFoundException(userId))
        .onItem().transformToUni(user -> {
            // Blocking Stripe call wrapped in Uni
            return Uni.createFrom().item(() -> {
                try {
                    String customerId = stripeAdapter.createCustomer(userId, user.email);
                    // ... create checkout session or subscription
                    return customerId;
                } catch (StripeException e) {
                    throw new RuntimeException("Failed to create Stripe customer", e);
                }
            });
        })
        .onItem().transformToUni(stripeData -> {
            // Create and persist Subscription entity
            Subscription subscription = new Subscription();
            // ... set fields
            return subscriptionRepository.persist(subscription);
        })
        .onItem().transformToUni(subscription -> {
            // Update User.subscriptionTier
            return updateUserTier(userId, tier)
                .replaceWith(subscription);
        });
}
```

---

**End of Task Briefing Package**

You now have all the information needed to implement the BillingService. Focus on:

1. Creating BillingService with @ApplicationScoped annotation
2. Injecting StripeAdapter, SubscriptionRepository, and UserRepository
3. Implementing the 5 core methods with proper reactive patterns
4. Adding tier transition validation logic
5. Ensuring User.subscriptionTier is always kept in sync
6. Adding @Transactional annotations for database operations
7. Including comprehensive error handling and logging

Good luck with the implementation!
