# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create `BillingService` domain service managing subscription lifecycle. Methods: `createSubscription(userId, tier)` (create Subscription entity, call StripeAdapter to create Stripe subscription), `upgradeSubscription(userId, newTier)` (update Subscription entity, call Stripe update), `cancelSubscription(userId)` (soft cancel, sets `canceled_at`, subscription active until period end), `getActiveSubscription(userId)`, `syncSubscriptionStatus(stripeSubscriptionId, status)` (called by webhook handler). Use `SubscriptionRepository`. Handle tier transitions (Free → Pro, Pro → Pro+). Update User.subscription_tier on tier change.

---

## Issues Detected

*   **CRITICAL: Missing Implementation:** The `BillingService` class has NOT been created at all. The target file `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java` does not exist.
*   **Missing Methods:** None of the 5 required methods have been implemented: `createSubscription`, `upgradeSubscription`, `cancelSubscription`, `getActiveSubscription`, `syncSubscriptionStatus`.
*   **No Integration with StripeAdapter:** The service needs to call methods from the existing `StripeAdapter` class but no code exists.
*   **No User.subscriptionTier Updates:** The service must update the User entity's `subscriptionTier` field on subscription changes, but no code exists to do this.
*   **No Tier Transition Validation:** The service must validate allowed tier transitions (e.g., Enterprise → Free not allowed directly), but no validation logic exists.

---

## Best Approach to Fix

You MUST create the file `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java` from scratch with a complete implementation. Follow these specific instructions:

### 1. Class Structure and Dependencies

Create an `@ApplicationScoped` CDI bean with the following injected dependencies:

```java
@ApplicationScoped
public class BillingService {

    @Inject
    StripeAdapter stripeAdapter;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    UserRepository userRepository;

    private static final Logger LOG = Logger.getLogger(BillingService.class);
}
```

### 2. Implement createSubscription Method

This method creates a NEW subscription for a FREE tier user upgrading to a paid tier:

**Requirements:**
- Accept `UUID userId` and `SubscriptionTier tier` parameters (mark as `final`)
- Return `Uni<Subscription>`
- Add `@Transactional` annotation for database atomicity
- Validate that the user exists
- Create a Stripe customer if needed (call `stripeAdapter.createCustomer(userId, user.email)`)
- Create a Stripe subscription (you will need to create a checkout session OR directly create a subscription - for simplicity, create a subscription directly by retrieving it after the user completes checkout)
- **IMPORTANT:** For this initial implementation, you should return a Subscription entity that is NOT yet linked to a Stripe subscription. The actual Stripe subscription will be created when the user completes the checkout process (handled by webhook in I5.T3). So create a TRIALING subscription with a temporary stripeSubscriptionId.
- Create and persist a Subscription entity with:
  - `entityId = userId`
  - `entityType = EntityType.USER`
  - `tier = tier` (the target tier)
  - `status = SubscriptionStatus.TRIALING` (initially trialing)
  - `stripeSubscriptionId = "[pending-checkout]"` (placeholder until webhook confirms)
  - `currentPeriodStart = Instant.now()`
  - `currentPeriodEnd = Instant.now().plus(30, ChronoUnit.DAYS)` (temporary)
  - `canceledAt = null`
- Update User.subscriptionTier to the new tier
- Use reactive chaining: fetch user → create Stripe customer → persist Subscription → update User tier → return Subscription

**Reactive Pattern Example:**
```java
@Transactional
public Uni<Subscription> createSubscription(final UUID userId, final SubscriptionTier tier) {
    return userRepository.findById(userId)
        .onItem().ifNull().failWith(() -> new IllegalArgumentException("User not found: " + userId))
        .onItem().transformToUni(user -> {
            // Validate tier is not FREE
            if (tier == SubscriptionTier.FREE) {
                return Uni.createFrom().failure(new IllegalArgumentException("Cannot create subscription for FREE tier"));
            }

            // Check if user already has an active subscription
            return subscriptionRepository.findActiveByEntityIdAndType(userId, EntityType.USER)
                .onItem().transformToUni(existingSubscription -> {
                    if (existingSubscription != null) {
                        return Uni.createFrom().failure(new IllegalStateException("User already has an active subscription"));
                    }

                    // Create Subscription entity (checkout session will be created by controller)
                    Subscription subscription = new Subscription();
                    subscription.stripeSubscriptionId = "pending-checkout-" + UUID.randomUUID();
                    subscription.entityId = userId;
                    subscription.entityType = EntityType.USER;
                    subscription.tier = tier;
                    subscription.status = SubscriptionStatus.TRIALING;
                    subscription.currentPeriodStart = Instant.now();
                    subscription.currentPeriodEnd = Instant.now().plus(30, ChronoUnit.DAYS);

                    return subscriptionRepository.persist(subscription);
                });
        })
        .onItem().transformToUni(subscription -> {
            // Update User.subscriptionTier
            return updateUserTier(userId, tier)
                .replaceWith(subscription);
        })
        .onItem().invoke(subscription ->
            LOG.infof("Created subscription %s for user %s (tier: %s)", subscription.subscriptionId, userId, tier)
        );
}
```

### 3. Implement upgradeSubscription Method

This method upgrades an EXISTING paid subscription to a higher tier:

**Requirements:**
- Accept `UUID userId` and `SubscriptionTier newTier` parameters (mark as `final`)
- Return `Uni<Subscription>`
- Add `@Transactional` annotation
- Fetch the user's active subscription
- Validate that the user has an active subscription (if not, fail with error)
- Validate that the tier transition is allowed (use helper method `validateTierUpgrade`)
- Call `stripeAdapter.updateSubscription(subscription.stripeSubscriptionId, newTier)` (wrap in Uni)
- Update the Subscription entity's tier field
- Update User.subscriptionTier
- Persist and return the updated Subscription

**Tier Transition Validation:**
Valid upgrades are:
- FREE → PRO, PRO_PLUS, ENTERPRISE
- PRO → PRO_PLUS, ENTERPRISE
- PRO_PLUS → ENTERPRISE

Invalid transitions (downgrades):
- Any tier → lower tier (must use cancel flow instead)
- ENTERPRISE → anything (must cancel)

Create a private helper method:
```java
private boolean isValidUpgrade(final SubscriptionTier currentTier, final SubscriptionTier newTier) {
    return switch (currentTier) {
        case FREE -> newTier == SubscriptionTier.PRO
                  || newTier == SubscriptionTier.PRO_PLUS
                  || newTier == SubscriptionTier.ENTERPRISE;
        case PRO -> newTier == SubscriptionTier.PRO_PLUS
                 || newTier == SubscriptionTier.ENTERPRISE;
        case PRO_PLUS -> newTier == SubscriptionTier.ENTERPRISE;
        case ENTERPRISE -> false; // Cannot upgrade from highest tier
    };
}
```

### 4. Implement cancelSubscription Method

This method performs a soft cancel (sets canceledAt, subscription remains active until period end):

**Requirements:**
- Accept `UUID userId` parameter (mark as `final`)
- Return `Uni<Void>`
- Add `@Transactional` annotation
- Fetch the user's active subscription
- If no active subscription exists, return successfully (idempotent)
- Call `stripeAdapter.cancelSubscription(subscription.stripeSubscriptionId)` (wrap in Uni)
- Set `subscription.canceledAt = Instant.now()`
- Persist the updated Subscription entity
- **IMPORTANT:** Do NOT update User.subscriptionTier yet - it stays active until period end. The webhook handler will update it when the subscription actually ends.

### 5. Implement getActiveSubscription Method

This method retrieves the active subscription for a user:

**Requirements:**
- Accept `UUID userId` parameter (mark as `final`)
- Return `Uni<Subscription>`
- No `@Transactional` needed (read-only)
- Call `subscriptionRepository.findActiveByEntityIdAndType(userId, EntityType.USER)`
- Return the result (may be null if user is on FREE tier)

### 6. Implement syncSubscriptionStatus Method

This method syncs subscription status from Stripe webhook events:

**Requirements:**
- Accept `String stripeSubscriptionId` and `SubscriptionStatus status` parameters (mark as `final`)
- Return `Uni<Void>`
- Add `@Transactional` annotation
- Fetch subscription by stripeSubscriptionId
- If subscription not found, log warning and return successfully
- Update subscription status and related fields:
  - If status is CANCELED, ensure canceledAt is set (if not already set)
  - If status is ACTIVE and subscription has canceledAt set but current period has ended, this means subscription has truly ended - update User.subscriptionTier to FREE
- Persist the updated Subscription entity
- Update User.subscriptionTier based on the new status:
  - If status is ACTIVE → set User.subscriptionTier to subscription.tier
  - If status is CANCELED and period has ended → set User.subscriptionTier to FREE
  - If status is PAST_DUE → keep current tier (grace period)

### 7. Implement updateUserTier Helper Method

Create a private helper method to update User.subscriptionTier:

```java
private Uni<Void> updateUserTier(final UUID userId, final SubscriptionTier tier) {
    return userRepository.findById(userId)
        .onItem().ifNull().failWith(() -> new IllegalStateException("User not found: " + userId))
        .onItem().transformToUni(user -> {
            user.subscriptionTier = tier;
            return userRepository.persist(user);
        })
        .replaceWithVoid()
        .onItem().invoke(() ->
            LOG.infof("Updated user %s subscription tier to %s", userId, tier)
        );
}
```

### 8. Required Imports

You MUST include these imports at the top of the file:

```java
package com.scrumpoker.domain.billing;

import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.integration.stripe.StripeAdapter;
import com.scrumpoker.integration.stripe.StripeException;
import com.scrumpoker.repository.SubscriptionRepository;
import com.scrumpoker.repository.UserRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
```

### 9. Error Handling

- Wrap Stripe SDK calls in try-catch blocks to handle StripeException
- Use `Uni.createFrom().failure()` for error cases
- Add comprehensive logging for all operations (INFO for success, ERROR for failures)

### 10. Code Quality

- Add comprehensive Javadoc comments for all public methods
- Use `final` keyword for all method parameters
- Keep lines under 80 characters (break long lines)
- Follow existing code style in the codebase
- Ensure all fields are private with proper access modifiers

---

**CRITICAL NOTES:**

1. The SubscriptionTier enum is in `com.scrumpoker.domain.user` package, NOT `com.scrumpoker.domain.billing`. Do NOT create a new SubscriptionTier enum.
2. The StripeAdapter is fully implemented and ready to use. Call its methods from your service.
3. Use reactive patterns throughout - all methods should return `Uni<>` types.
4. The cancelSubscription method does NOT immediately downgrade the user - they keep access until period end.
5. The syncSubscriptionStatus method is called by the webhook handler (I5.T3) to update subscription state from Stripe events.

After implementing this service, run the Maven build to ensure there are no compilation errors:
```bash
mvn clean compile
```
