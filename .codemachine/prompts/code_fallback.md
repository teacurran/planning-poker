# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

**Task 5.2: Implement Billing Service (Subscription Management)**

Create `BillingService` domain service managing subscription lifecycle. Methods: `createSubscription(userId, tier)` (create Subscription entity, call StripeAdapter to create Stripe subscription), `upgradeSubscription(userId, newTier)` (update Subscription entity, call Stripe update), `cancelSubscription(userId)` (soft cancel, sets `canceled_at`, subscription active until period end), `getActiveSubscription(userId)`, `syncSubscriptionStatus(stripeSubscriptionId, status)` (called by webhook handler). Use `SubscriptionRepository`. Handle tier transitions (Free → Pro, Pro → Pro+). Update User.subscription_tier on tier change.

**Deliverables:**
- BillingService with methods: createSubscription, upgradeSubscription, cancelSubscription, getActiveSubscription, syncSubscriptionStatus
- Subscription entity creation with Stripe subscription ID
- Tier transition logic (validate allowed transitions)
- User.subscription_tier update on subscription change
- Subscription status sync from Stripe webhooks

**Acceptance Criteria:**
- Creating subscription persists to database and creates Stripe subscription
- Upgrading tier updates both database and Stripe
- Canceling subscription sets `canceled_at`, subscription remains active until period end
- Tier enforcement prevents invalid transitions (e.g., Enterprise → Free not allowed directly)
- User.subscription_tier reflects current subscription status
- Sync method updates subscription status from webhook events

---

## Issues Detected

The BillingService implementation is **functionally complete** and meets all acceptance criteria. However, there are **checkstyle violations** that must be fixed:

### Checkstyle Violations in BillingService.java:

1. **Line 47:** Variable 'stripeAdapter' must be private and have accessor methods (should be `private` not package-private with @Inject)
2. **Line 53:** Variable 'subscriptionRepository' must be private and have accessor methods (should be `private` not package-private with @Inject)
3. **Line 59:** Variable 'userRepository' must be private and have accessor methods (should be `private` not package-private with @Inject)
4. **Line 110:** '+' operator should be on a new line (string concatenation formatting)
5. **Line 125:** '30' is a magic number (should be extracted as a constant)
6. **Lines 140, 194, 195, 230, 269, 298, 322, 361, 368, 408, 412:** '+' operators should be on new lines (string concatenation formatting)

---

## Best Approach to Fix

You MUST modify the `BillingService.java` file at `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java` to fix all checkstyle violations:

### 1. Fix Visibility Modifiers (Lines 47, 53, 59)

Change all three injected fields from package-private to `private`:

```java
@Inject
private StripeAdapter stripeAdapter;

@Inject
private SubscriptionRepository subscriptionRepository;

@Inject
private UserRepository userRepository;
```

### 2. Extract Magic Number (Line 125)

Add a constant at the top of the class and use it:

```java
private static final int DEFAULT_TRIAL_PERIOD_DAYS = 30;
```

Then on line 125, change:
```java
subscription.currentPeriodEnd = Instant.now().plus(30, ChronoUnit.DAYS);
```
to:
```java
subscription.currentPeriodEnd =
    Instant.now().plus(DEFAULT_TRIAL_PERIOD_DAYS, ChronoUnit.DAYS);
```

### 3. Fix Operator Wrap Issues (Multiple Lines)

All string concatenations with the '+' operator must be reformatted so the '+' is on a new line at the beginning.

For example, line 110:
```java
// BEFORE (INCORRECT):
"User already has an active " +
"subscription"

// AFTER (CORRECT):
"User already has an active "
    + "subscription"
```

Apply this pattern to ALL string concatenations on lines: 110, 140, 194, 195, 230, 269, 298, 322, 361, 368, 408, 412.

**Important:** Make sure you maintain proper indentation (4 spaces for continuation lines) when moving the '+' operator to the new line.

---

## Functional Verification

After fixing the checkstyle violations:

1. Run `mvn checkstyle:check` to verify all checkstyle issues are resolved
2. Run `mvn clean compile` to ensure the code still compiles
3. Verify all acceptance criteria are still met (they should be, as you're only changing formatting)

Do NOT make any functional changes to the code - only fix the style violations listed above.