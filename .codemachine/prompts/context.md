# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I5.T7",
  "iteration_id": "I5",
  "iteration_goal": "Implement Stripe subscription billing, tier enforcement (Free/Pro/Pro+/Enterprise), payment flows, webhook handling for subscription lifecycle events, and frontend upgrade UI.",
  "description": "Create comprehensive unit tests for `BillingService` using mocked `SubscriptionRepository` and `StripeAdapter`. Test scenarios: create subscription (verify Stripe called, entity persisted), upgrade tier (verify tier transition, Stripe update called), cancel subscription (verify `canceled_at` set), sync subscription status (verify entity updated from webhook event). Test edge cases: duplicate subscription creation, invalid tier transitions, canceled subscription upgrades.",
  "agent_type_hint": "BackendAgent",
  "inputs": "BillingService from I5.T2, Mockito testing patterns",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java"
  ],
  "target_files": [
    "backend/src/test/java/com/scrumpoker/domain/billing/BillingServiceTest.java"
  ],
  "deliverables": "BillingServiceTest with 15+ test methods, Tests for happy paths (create, upgrade, cancel), Tests for edge cases (duplicate creation, invalid transitions), Mocked StripeAdapter verifying correct Stripe calls, AssertJ assertions for entity state",
  "acceptance_criteria": "`mvn test` runs billing service tests successfully, Test coverage >90% for BillingService, Subscription creation test verifies Stripe customer created, Tier upgrade test verifies Stripe subscription updated, Cancel test verifies `canceled_at` timestamp set, Invalid transition test throws exception",
  "dependencies": [
    "I5.T2"
  ],
  "parallelizable": true,
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

### Context: security-nfrs (from 01_Context_and_Drivers.md)

```markdown
#### Security
- **Transport Security:** HTTPS/TLS 1.3 for all communications, WSS for WebSocket connections
- **Authentication:** JWT tokens with 1-hour expiration, refresh token rotation
- **Authorization:** Role-based access control (RBAC) for organization features
- **Data Protection:** Encryption at rest for sensitive data (PII, payment info), GDPR compliance
- **Session Isolation:** Anonymous session data segregated by room ID, automatic cleanup after 24 hours
```

### Context: maintainability-nfrs (from 01_Context_and_Drivers.md)

```markdown
#### Maintainability
- **Code Organization:** Domain-driven design with clear bounded contexts
- **API Versioning:** Semantic versioning for REST APIs, backward-compatible WebSocket protocol
- **Logging:** Structured JSON logging with correlation IDs across distributed traces
- **Monitoring:** Prometheus metrics, Grafana dashboards, alerting for critical failures
```

### Context: technology-constraints (from 01_Context_and_Drivers.md)

```markdown
#### Technology Constraints
- **Backend Framework:** Quarkus with Hibernate Reactive (specified requirement)
- **Database:** PostgreSQL for relational data integrity and JSONB support
- **Cache/Message Bus:** Redis for session state distribution and Pub/Sub messaging
- **Payment Provider:** Stripe for subscription billing and payment processing
- **Containerization:** Docker containers orchestrated via Kubernetes
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Summary:** This is the primary service class containing all subscription lifecycle management logic. It has 5 public methods: `createSubscription()`, `upgradeSubscription()`, `cancelSubscription()`, `getActiveSubscription()`, and `syncSubscriptionStatus()`. The class uses reactive programming with Mutiny Uni types and includes comprehensive Javadoc documentation.
    *   **Recommendation:** You MUST create unit tests covering ALL 5 public methods. Each method has multiple execution paths that need testing (happy path and error cases).
    *   **Key Implementation Details:**
        - All methods are `@Transactional` and return reactive `Uni<>` types
        - Uses `@Inject` for `StripeAdapter`, `SubscriptionRepository`, and `UserRepository`
        - Has helper methods: `updateUserTier()` (private), `isValidUpgrade()` (private) that need indirect testing
        - Validates tier transitions: FREE→PRO/PRO_PLUS/ENTERPRISE, PRO→PRO_PLUS/ENTERPRISE, PRO_PLUS→ENTERPRISE
        - Default trial period is 30 days (constant: `DEFAULT_TRIAL_PERIOD_DAYS = 30`)
        - Creates placeholder Stripe subscription ID "pending-checkout-{uuid}" during initial creation

*   **File:** `backend/src/main/java/com/scrumpoker/repository/SubscriptionRepository.java`
    *   **Summary:** Reactive Panache repository with custom finder methods for subscription queries. Extends `PanacheRepositoryBase<Subscription, UUID>`.
    *   **Recommendation:** You MUST mock this repository in your tests using Mockito. Key methods to mock: `findActiveByEntityIdAndType()`, `findByStripeSubscriptionId()`, `persist()`.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
    *   **Summary:** Stripe SDK wrapper with synchronous methods for checkout session creation, customer creation, subscription retrieval, cancellation, and updates. All methods throw `StripeException` on failure.
    *   **Recommendation:** You MUST mock this adapter and verify the correct Stripe methods are called with correct parameters. Key methods to mock: `createCustomer()`, `updateSubscription()`, `cancelSubscription()`.

*   **File:** `backend/src/test/java/com/scrumpoker/domain/room/RoomServiceTest.java`
    *   **Summary:** Excellent reference implementation showing the project's unit testing patterns. Uses `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`, Mockito `when()`/`verify()`, and AssertJ assertions.
    *   **Recommendation:** You SHOULD follow this exact testing pattern for BillingServiceTest. Note the test organization: grouped by method with descriptive test names following pattern `test{MethodName}_{Scenario}_{ExpectedOutcome}`.
    *   **Key Patterns to Replicate:**
        - Use `@BeforeEach` to set up common test fixtures (User objects, tier enums)
        - Use `Uni.createFrom().item()` to wrap mock responses for reactive types
        - Use `.await().indefinitely()` to block and extract reactive results
        - Use `assertThatThrownBy()` for exception testing
        - Group tests with comment headers like `// ===== Create Subscription Tests =====`

### Implementation Tips & Notes

*   **Tip:** The BillingService uses reactive Uni types throughout. When mocking repository responses, you MUST wrap return values in `Uni.createFrom().item(entity)` for successful cases and `Uni.createFrom().nullItem()` for not-found cases. For exceptions, use `Uni.createFrom().failure(exception)`.
*   **Tip:** For testing Stripe adapter calls, you need to wrap blocking Stripe operations in `Uni.createFrom().item(() -> { ... })` since the actual BillingService does this. Your mocks should simulate this pattern.
*   **Tip:** The validation logic `isValidUpgrade()` is private, but you can test it indirectly by calling `upgradeSubscription()` with invalid tier combinations and verifying it throws `IllegalArgumentException`.
*   **Tip:** When testing `createSubscription()`, verify that the Subscription entity has `status = TRIALING`, `stripeSubscriptionId` starting with "pending-checkout-", and period dates set correctly (30 days for trial).
*   **Tip:** The `syncSubscriptionStatus()` method has special logic for `CANCELED` status - it checks if the period has ended before downgrading the user to FREE tier. Test both cases: canceled but still active (period not ended) and canceled with period ended.
*   **Note:** All subscription operations also update the User.subscriptionTier field via the private `updateUserTier()` method. Your tests MUST verify UserRepository is called to persist the updated user with the correct tier.
*   **Note:** The project uses AssertJ for assertions (imported as `org.assertj.core.api.Assertions.assertThat`). Use this instead of JUnit's assertEquals for better error messages and fluent syntax.
*   **Warning:** The BillingService wraps StripeAdapter exceptions (which are checked exceptions) in RuntimeException. Your tests should verify the correct exception type is thrown when Stripe operations fail.
*   **Critical:** Test MUST verify mocked method calls using Mockito's `verify()`. For example, after testing `createSubscription()`, verify that `subscriptionRepository.persist()` was called exactly once with a Subscription entity matching expected values. Use `verify(mock, times(1)).method()` or `verify(mock, never()).method()` as appropriate.

### Required Test Coverage

Based on the acceptance criteria requiring >90% coverage and 15+ test methods, here's the minimum test suite structure you MUST implement:

**createSubscription() tests (5 tests minimum):**
1. Valid subscription creation - verify Subscription entity persisted with correct fields (TRIALING status, pending-checkout ID, 30-day period)
2. User not found - verify IllegalArgumentException thrown
3. FREE tier requested - verify IllegalArgumentException thrown
4. User already has active subscription - verify IllegalStateException thrown
5. Repository persist failure - verify exception propagation

**upgradeSubscription() tests (5 tests minimum):**
1. Valid upgrade (e.g., PRO → PRO_PLUS) - verify tier updated in both Subscription and User, StripeAdapter.updateSubscription() called
2. User not found - verify IllegalArgumentException thrown
3. User has no active subscription - verify IllegalStateException thrown
4. Invalid tier transition (downgrade or lateral) - verify IllegalArgumentException with message about downgrades not allowed
5. StripeAdapter.updateSubscription() throws StripeException - verify RuntimeException thrown

**cancelSubscription() tests (3 tests minimum):**
1. Valid cancellation - verify canceledAt timestamp set, StripeAdapter.cancelSubscription() called, subscription persisted
2. User not found - verify IllegalArgumentException thrown
3. User has no subscription (idempotent case) - verify method returns successfully without error

**syncSubscriptionStatus() tests (4 tests minimum):**
1. Sync to ACTIVE status - verify Subscription.status updated and User.subscriptionTier updated to subscription tier
2. Sync to CANCELED with period ended (currentPeriodEnd in past) - verify User.subscriptionTier downgraded to FREE
3. Sync to CANCELED but period not ended (currentPeriodEnd in future) - verify User.subscriptionTier NOT changed, canceledAt set
4. Unknown stripeSubscriptionId - verify method returns successfully without error (logs warning but doesn't fail)

**getActiveSubscription() tests (1 test minimum):**
1. Valid subscription retrieval - verify repository.findActiveByEntityIdAndType() called and subscription returned

This totals 18 test scenarios, exceeding the 15+ requirement and providing excellent coverage.

### Testing Anti-Patterns to AVOID

*   ❌ DO NOT test private methods directly - test them through public method invocations
*   ❌ DO NOT use real database or Stripe API - all dependencies must be mocked
*   ❌ DO NOT forget to verify mock interactions - use `verify()` after every test
*   ❌ DO NOT use `any()` matchers when you can verify specific values - use `eq()` or actual values
*   ❌ DO NOT forget to test `.await().indefinitely()` to extract results from Uni
*   ❌ DO NOT use assertTrue/assertEquals - use AssertJ's fluent assertions
