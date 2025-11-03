# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Implement REST controllers for subscription management per OpenAPI spec. Endpoints: `GET /api/v1/subscriptions/{userId}` (get current subscription), `POST /api/v1/subscriptions/checkout` (create Stripe checkout session, return session URL for redirect), `POST /api/v1/subscriptions/{subscriptionId}/cancel` (cancel subscription), `GET /api/v1/billing/invoices` (list payment history). Use BillingService. Return DTOs matching OpenAPI schemas. Authorize users can only access own subscription data.

---

## Issues Detected

The code has been implemented correctly from a functional standpoint (all 4 endpoints, DTOs, mappers, and integration with BillingService are complete), but there are **122 checkstyle violations** across the new files that must be fixed:

### **SubscriptionController.java** - 77 violations

*   **Import Issues:**
    *   Line 3: Avoid star import for `com.scrumpoker.api.rest.dto.*`
    *   Line 8: Unused import `CheckoutSessionResult` (it's used inline, not imported at top)
    *   Line 16: Avoid star import for `jakarta.ws.rs.*`

*   **Field Visibility & Javadoc:**
    *   Lines 42-58: All 6 injected fields (`billingService`, `stripeAdapter`, etc.) are missing Javadoc comments
    *   Lines 43, 46, 49, 52, 55, 58: All injected fields must be `private` (not package-private)

*   **Method Javadoc:**
    *   Lines 60, 100, 151, 207: Method Javadoc first sentence must end with a period
    *   Lines 65, 106, 157, 213: Missing `@return` tag descriptions
    *   Lines 81, 119, 174, 226-228: Missing `@param` tags for method parameters

*   **Parameter Modifiers:**
    *   Lines 80, 119, 173, 225, 227: All method parameters must be marked `final`

*   **Line Length Violations (>80 chars):**
    *   60+ lines exceed 80 character limit - need to be wrapped or reformatted

*   **TODO Comments:**
    *   Lines 83, 84, 121, 176, 177, 230: TODO comments match the to-do format but should be suppressed or reworded (this is a false positive - TODOs are intentional until auth is implemented)

*   **Magic Numbers:**
    *   Lines 241-242: The number `100` (max page size) should be extracted as a constant

### **SubscriptionDTO.java** - 10 violations

*   **Field Visibility:**
    *   Lines 19, 24, 29, 34, 39, 44, 49, 54, 59, 64: All 10 public fields must be `private` with getter/setter methods
    *   **CRITICAL**: Check other DTOs in the project first! If the project uses public fields for DTOs (like many Quarkus projects), you may need to suppress this checkstyle rule for DTO classes instead of adding getters/setters.

### **CreateCheckoutRequest.java** - 3 violations

*   Lines 16, 22, 28: All 3 public fields must be `private` with getter/setter methods (or suppress rule)

### **CheckoutSessionResponse.java** - 4 violations

*   Lines 12, 17: Public fields must be `private` with getter/setter methods (or suppress rule)
*   Lines 31 (2 occurrences): Constructor parameters must be `final` and `HiddenField` warnings

### **PaymentHistoryDTO.java** - 7 violations

*   Lines 17, 22, 27, 32, 37, 42, 47: All 7 public fields must be `private` with getter/setter methods (or suppress rule)

### **InvoiceListResponse.java** - 9 violations

*   Lines 14, 19, 24, 29, 34: All 5 public fields must be `private` with getter/setter methods (or suppress rule)
*   Line 51: Line too long (84 chars)
*   Lines 51-52: Constructor parameters must be `final` and cause `HiddenField` warnings

### **SubscriptionMapper.java** - 4 violations

*   Line 1: Missing `package-info.java` for javadoc package documentation (can be ignored or suppressed)
*   Lines 26, 54: Method parameters must be `final`
*   Line 63: Line too long (91 chars)
*   Line 63: Magic number `365` should be extracted as a constant (FREE_TIER_PERIOD_DAYS)

### **PaymentHistoryMapper.java** - 2 violations

*   Line 15: Line too long (84 chars)
*   Line 20: Method parameter must be `final`

### **PaymentHistoryRepository.java** - 38+ violations (pre-existing modified file)

*   Line 6: Unused import `WithSession`
*   Line 23: Missing Javadoc for `sessionFactory` field
*   Line 24: Field must be `private`
*   Multiple parameters not marked `final`
*   Multiple line length violations
*   Operator wrap violations in multi-line strings

---

## Best Approach to Fix

You MUST fix all checkstyle violations following the existing project patterns. Here's the systematic approach:

### **Step 1: Check Existing DTO Pattern**

Before changing DTO visibility, examine `UserDTO.java` or other DTOs in `backend/src/main/java/com/scrumpoker/api/rest/dto/` to see if they use public fields or private fields with getters/setters.

*   **If existing DTOs use public fields:** Add checkstyle suppression comments to all DTO files:
    ```java
    @SuppressWarnings("checkstyle:VisibilityModifier")
    public class SubscriptionDTO {
        // public fields...
    }
    ```

*   **If existing DTOs use private fields:** Add private modifiers and generate getters/setters for all DTO fields.

### **Step 2: Fix SubscriptionController.java**

1. **Replace star imports with explicit imports:**
   ```java
   import com.scrumpoker.api.rest.dto.CheckoutSessionResponse;
   import com.scrumpoker.api.rest.dto.CreateCheckoutRequest;
   import com.scrumpoker.api.rest.dto.InvoiceListResponse;
   import com.scrumpoker.api.rest.dto.SubscriptionDTO;
   // ... (import all DTO classes explicitly)

   import jakarta.ws.rs.Consumes;
   import jakarta.ws.rs.DefaultValue;
   import jakarta.ws.rs.GET;
   // ... (import all JAX-RS annotations explicitly)
   ```

2. **Remove unused import:** Delete line 8 (`import com.scrumpoker.integration.stripe.CheckoutSessionResult;`) since it's used inline in the method body, not as a type reference.

3. **Change all injected fields to private and add Javadoc:**
   ```java
   /** Billing service for subscription lifecycle management. */
   @Inject
   private BillingService billingService;

   /** Stripe adapter for payment processing integration. */
   @Inject
   private StripeAdapter stripeAdapter;

   /** Repository for subscription persistence. */
   @Inject
   private SubscriptionRepository subscriptionRepository;

   /** Repository for payment history persistence. */
   @Inject
   private PaymentHistoryRepository paymentHistoryRepository;

   /** Mapper for subscription entity-to-DTO conversion. */
   @Inject
   private SubscriptionMapper subscriptionMapper;

   /** Mapper for payment history entity-to-DTO conversion. */
   @Inject
   private PaymentHistoryMapper paymentHistoryMapper;
   ```

4. **Fix method Javadoc to end with periods and include @param/@return tags:**
   ```java
   /**
    * GET /api/v1/subscriptions/{userId} - Get current subscription status.
    * Security: Requires authentication (will be enforced in Iteration 3).
    *
    * @param userId The user UUID
    * @return Response containing SubscriptionDTO or 404 Not Found
    */
   @GET
   @Path("/subscriptions/{userId}")
   public Uni<Response> getSubscription(
           @Parameter(description = "User UUID", required = true)
           @PathParam("userId") final UUID userId) {
   ```

5. **Mark all method parameters as `final`:** Add `final` keyword to all `@PathParam`, `@QueryParam`, and method body parameters.

6. **Extract magic numbers as constants:**
   ```java
   private static final int MAX_PAGE_SIZE = 100;

   // Then use in validation:
   if (size > MAX_PAGE_SIZE) {
       size = MAX_PAGE_SIZE;
   }
   ```

7. **Fix line length violations by wrapping long lines:**
   ```java
   // Before (110 chars):
   @APIResponse(responseCode = "403", description = "Forbidden - user accessing another user's subscription",

   // After (wrapped at 80 chars):
   @APIResponse(
       responseCode = "403",
       description = "Forbidden - user accessing another user's "
           + "subscription",
       content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
   ```

8. **Suppress TODO warnings:** Add checkstyle suppression for TODO comments since they're intentional:
   ```java
   // CHECKSTYLE:OFF TodoComment
   // TODO: Add authentication check when JWT is implemented in Iteration 3
   // CHECKSTYLE:ON TodoComment
   ```

### **Step 3: Fix Mapper Classes**

For `SubscriptionMapper.java` and `PaymentHistoryMapper.java`:

1. **Mark all method parameters as `final`:**
   ```java
   public SubscriptionDTO toDTO(final Subscription subscription) {
   ```

2. **Extract magic number in SubscriptionMapper:**
   ```java
   private static final int FREE_TIER_PERIOD_DAYS = 365;

   // Then use:
   dto.currentPeriodEnd = Instant.now()
       .plus(FREE_TIER_PERIOD_DAYS, java.time.temporal.ChronoUnit.DAYS);
   ```

3. **Wrap long lines to fit 80 character limit.**

### **Step 4: Fix PaymentHistoryRepository.java**

1. **Remove unused import:** Delete line 6 `import io.quarkus.hibernate.reactive.panache.common.WithSession;`

2. **Make `sessionFactory` field private and add Javadoc:**
   ```java
   /** Hibernate Reactive session factory for custom queries. */
   @Inject
   private Mutiny.SessionFactory sessionFactory;
   ```

3. **Mark all method parameters as `final`.**

4. **Fix operator wrap violations in multi-line strings:**
   ```java
   // Before:
   "SELECT p FROM PaymentHistory p " +
   "WHERE p.subscription.entityId = :userId " +

   // After:
   "SELECT p FROM PaymentHistory p "
       + "WHERE p.subscription.entityId = :userId "
       + "AND p.subscription.entityType = 'USER' "
       + "ORDER BY p.paidAt DESC",
   ```

5. **Wrap lines exceeding 80 characters.**

### **Step 5: Verify Build**

After making all fixes, run:

```bash
./mvnw checkstyle:check -Dcheckstyle.skip=false
```

Ensure there are **zero** checkstyle errors in the subscription-related files:
- `SubscriptionController.java`
- `SubscriptionDTO.java`
- `CreateCheckoutRequest.java`
- `CheckoutSessionResponse.java`
- `PaymentHistoryDTO.java`
- `InvoiceListResponse.java`
- `SubscriptionMapper.java`
- `PaymentHistoryMapper.java`
- `PaymentHistoryRepository.java` (only fix new violations you introduced)

---

## Important Notes

*   **DO NOT change the functional logic** - the implementation is correct. Only fix code style violations.
*   **Follow the existing project patterns** - check how `UserController.java` and `UserDTO.java` handle similar cases.
*   **Prioritize explicit imports over star imports** - this makes dependencies clearer.
*   **All public API classes need complete Javadoc** - controllers, DTOs, and mappers are part of the public API.
*   **The 80-character line limit is strict** - use line wrapping and string concatenation to stay within bounds.
*   **Magic numbers reduce code maintainability** - extract them as named constants.
*   **Final parameters prevent accidental reassignment** - this is a defensive programming practice enforced by checkstyle.
