# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I5.T5",
  "iteration_id": "I5",
  "iteration_goal": "Implement Stripe subscription billing, tier enforcement (Free/Pro/Pro+/Enterprise), payment flows, webhook handling for subscription lifecycle events, and frontend upgrade UI.",
  "description": "Implement REST controllers for subscription management per OpenAPI spec. Endpoints: `GET /api/v1/subscriptions/{userId}` (get current subscription), `POST /api/v1/subscriptions/checkout` (create Stripe checkout session, return session URL for redirect), `POST /api/v1/subscriptions/{subscriptionId}/cancel` (cancel subscription), `GET /api/v1/billing/invoices` (list payment history). Use BillingService. Return DTOs matching OpenAPI schemas. Authorize users can only access own subscription data.",
  "agent_type_hint": "BackendAgent",
  "inputs": "OpenAPI spec for subscription endpoints from I2.T1, BillingService from I5.T2",
  "input_files": [
    "api/openapi.yaml",
    "backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/SubscriptionController.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/SubscriptionDTO.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/CreateCheckoutRequest.java",
    "backend/src/main/java/com/scrumpoker/api/rest/dto/CheckoutSessionResponse.java"
  ],
  "deliverables": "SubscriptionController with 4 endpoints, DTOs for subscription data and checkout session, Checkout endpoint creates Stripe session and returns URL, Cancel endpoint calls BillingService.cancelSubscription, Invoice list endpoint retrieves PaymentHistory records, Authorization checks (user can only access own data)",
  "acceptance_criteria": "GET /subscriptions/{userId} returns current subscription status, POST /subscriptions/checkout returns Stripe checkout session URL, Redirecting to session URL displays Stripe payment page, POST /cancel marks subscription as canceled, GET /invoices returns user's payment history, Unauthorized access returns 403",
  "dependencies": ["I5.T2"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Subscription & Billing Endpoints (from openapi.yaml - Lines 458-595)

```markdown
## Subscription Management API Specification

### GET /api/v1/subscriptions/{userId}
- **Summary**: Get current subscription status
- **Description**: Returns current subscription tier, billing status, and feature limits.
- **Parameters**: userId (path, UUID, required)
- **Responses**:
  - 200: SubscriptionDTO
  - 401: Unauthorized
  - 403: Forbidden (users can only access own data)
  - 404: Not Found
  - 500: Internal Server Error

### POST /api/v1/subscriptions/checkout
- **Summary**: Create Stripe checkout session for upgrade
- **Description**: Creates a Stripe Checkout session for upgrading to Pro or Pro Plus tier. Returns checkout URL for redirect.
- **Request Body**: CheckoutRequest (tier, successUrl, cancelUrl)
- **Responses**:
  - 200: CheckoutResponse (sessionId, checkoutUrl)
  - 400: Bad Request
  - 401: Unauthorized
  - 500: Internal Server Error

### POST /api/v1/subscriptions/{subscriptionId}/cancel
- **Summary**: Cancel subscription (end of billing period)
- **Description**: Cancels subscription at end of current billing period. Access continues until period end.
- **Parameters**: subscriptionId (path, UUID, required)
- **Responses**:
  - 200: SubscriptionDTO
  - 401: Unauthorized
  - 403: Forbidden
  - 404: Not Found
  - 500: Internal Server Error

### GET /api/v1/billing/invoices
- **Summary**: List payment history
- **Description**: Returns paginated list of payment invoices for the authenticated user.
- **Query Parameters**:
  - page (integer, default: 0, min: 0)
  - size (integer, default: 20, min: 1, max: 100)
- **Responses**:
  - 200: InvoiceListResponse (invoices array, pagination metadata)
  - 401: Unauthorized
  - 500: Internal Server Error

## Schema Definitions

### SubscriptionDTO
```yaml
type: object
required: [subscriptionId, entityId, entityType, tier, status, currentPeriodStart, currentPeriodEnd]
properties:
  subscriptionId: {type: string, format: uuid}
  stripeSubscriptionId: {type: string, maxLength: 100}
  entityId: {type: string, format: uuid}
  entityType: {$ref: '#/components/schemas/EntityType'}  # USER or ORG
  tier: {$ref: '#/components/schemas/SubscriptionTier'}  # FREE, PRO, PRO_PLUS, ENTERPRISE
  status: {$ref: '#/components/schemas/SubscriptionStatus'}  # ACTIVE, PAST_DUE, CANCELED, TRIALING
  currentPeriodStart: {type: string, format: date-time}
  currentPeriodEnd: {type: string, format: date-time}
  canceledAt: {type: string, format: date-time, nullable: true}
  createdAt: {type: string, format: date-time}
```

### CheckoutRequest
```yaml
type: object
required: [tier, successUrl, cancelUrl]
properties:
  tier: {type: string, enum: [PRO, PRO_PLUS]}
  successUrl: {type: string, format: uri}
  cancelUrl: {type: string, format: uri}
```

### CheckoutResponse
```yaml
type: object
required: [sessionId, checkoutUrl]
properties:
  sessionId: {type: string}
  checkoutUrl: {type: string, format: uri}
```

### PaymentHistoryDTO
```yaml
type: object
required: [paymentId, subscriptionId, amount, currency, status, paidAt]
properties:
  paymentId: {type: string, format: uuid}
  subscriptionId: {type: string, format: uuid}
  stripeInvoiceId: {type: string, maxLength: 100}
  amount: {type: integer}  # cents
  currency: {type: string, maxLength: 3}  # ISO 4217
  status: {$ref: '#/components/schemas/PaymentStatus'}  # SUCCEEDED, PENDING, FAILED
  paidAt: {type: string, format: date-time}
```

### InvoiceListResponse
```yaml
type: object
required: [invoices, page, size, totalElements, totalPages]
properties:
  invoices: {type: array, items: {$ref: '#/components/schemas/PaymentHistoryDTO'}}
  page: {type: integer}
  size: {type: integer}
  totalElements: {type: integer}
  totalPages: {type: integer}
```
```

### Context: BillingService Domain Service Architecture

```markdown
## BillingService Overview

The `BillingService` is a domain service managing subscription lifecycle for users and organizations, coordinating between Stripe payment processing and local subscription state.

### Key Methods Available

1. **createSubscription(userId, tier)**
   - Creates new subscription for user upgrading from FREE to paid tier
   - Creates Subscription entity with TRIALING status
   - Uses placeholder Stripe subscription ID: "pending-checkout-{UUID}"
   - Updates User.subscriptionTier to target tier
   - Returns: Uni<Subscription>
   - Validates: tier is not FREE, user has no active subscription

2. **upgradeSubscription(userId, newTier)**
   - Upgrades existing paid subscription to higher tier
   - Validates tier upgrade is valid (not a downgrade)
   - Calls StripeAdapter.updateSubscription()
   - Updates both Subscription entity and User.subscriptionTier
   - Returns: Uni<Subscription>

3. **cancelSubscription(userId)**
   - Soft cancels subscription (active until period end)
   - Sets canceledAt timestamp
   - Calls StripeAdapter.cancelSubscription()
   - User tier NOT updated immediately (webhook will handle at period end)
   - Returns: Uni<Void>

4. **getActiveSubscription(userId)**
   - Retrieves active subscription for tier enforcement
   - Returns null if user is FREE tier
   - Returns: Uni<Subscription>

5. **syncSubscriptionStatus(stripeSubscriptionId, status)**
   - Called by webhook handler to sync Stripe state
   - Updates subscription status
   - Handles tier updates based on status changes
   - Returns: Uni<Void>

### Service Behavior Notes
- All methods are @Transactional
- All methods return reactive Uni types
- Stripe API calls are synchronous (blocking) wrapped in Uni
- User.subscriptionTier is always kept in sync with subscription state
- Trial period: 30 days (DEFAULT_TRIAL_PERIOD_DAYS constant)
```

### Context: StripeAdapter Integration

```markdown
## StripeAdapter Methods

The StripeAdapter provides synchronous (blocking) methods for Stripe API operations:

### createCheckoutSession(userId, tier, successUrl, cancelUrl)
- Creates Stripe checkout session for subscription
- Parameters:
  - userId: User UUID
  - tier: SubscriptionTier (PRO, PRO_PLUS, ENTERPRISE)
  - successUrl: Redirect URL on success
  - cancelUrl: Redirect URL on cancel
- Returns: CheckoutSessionResult(sessionId, checkoutUrl)
- Throws: StripeException on failure
- Configuration: Uses price IDs from application.properties:
  - stripe.price.pro
  - stripe.price.pro-plus
  - stripe.price.enterprise

### createCustomer(userId, email)
- Creates Stripe customer record
- Returns: Stripe customer ID (cus_...)
- Stores userId in metadata

### getSubscription(stripeSubscriptionId)
- Retrieves Stripe subscription by ID
- Returns: StripeSubscriptionInfo

### cancelSubscription(stripeSubscriptionId)
- Cancels Stripe subscription with cancel_at_period_end=true
- Throws: StripeException on failure

### updateSubscription(stripeSubscriptionId, newTier)
- Updates Stripe subscription to new price tier
- Handles proration automatically
- Throws: StripeException on failure

## Important Notes
- All methods are synchronous/blocking
- Service layer must wrap in Uni.createFrom().item(() -> ...)
- Stripe SDK initialized in @PostConstruct with API key
- Test mode detected by sk_test_ prefix
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/UserController.java`
    *   **Summary**: This is the reference implementation for REST controllers in this project. It demonstrates the standard patterns for JAX-RS endpoints, reactive Uni return types, OpenAPI annotations, error handling, and authorization checks.
    *   **Recommendation**: You MUST follow the same patterns used in UserController for your SubscriptionController. Key patterns to replicate:
        - `@Path("/api/v1")` at class level
        - `@Produces(MediaType.APPLICATION_JSON)` and `@Consumes(MediaType.APPLICATION_JSON)` at class level
        - `@Tag` annotation for OpenAPI documentation
        - Method signatures returning `Uni<Response>`
        - Use of `@Operation`, `@APIResponse`, `@Parameter` annotations
        - Service injection via `@Inject`
        - Error handling delegated to exception mappers (don't catch exceptions, let them propagate)
        - Authorization via `@RolesAllowed("USER")` where needed

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Summary**: This is the service layer you will interact with. It provides all the subscription management methods you need for your controller endpoints.
    *   **Recommendation**: You MUST inject and use this service. Do NOT implement business logic in the controller. Key usage patterns:
        - Inject via `@Inject BillingService billingService;`
        - All service methods return `Uni<T>` reactive types
        - Use `.onItem().transform()` to convert entities to DTOs
        - Use `.onItem().transformToUni()` to chain service calls
        - StripeAdapter calls are already wrapped in Uni by BillingService
        - For checkout endpoint, you'll need to call BillingService.createSubscription() first, then StripeAdapter.createCheckoutSession()

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/Subscription.java`
    *   **Summary**: This is the JPA entity representing subscriptions in the database. It extends PanacheEntityBase and contains all subscription fields.
    *   **Recommendation**: You MUST create a SubscriptionDTO that mirrors this entity's public fields. Map these fields:
        - subscriptionId (UUID)
        - stripeSubscriptionId (String)
        - entityId (UUID)
        - entityType (EntityType enum)
        - tier (SubscriptionTier enum)
        - status (SubscriptionStatus enum)
        - currentPeriodStart (Instant)
        - currentPeriodEnd (Instant)
        - canceledAt (Instant, nullable)
        - createdAt (Instant)
        - Do NOT include updatedAt in DTO (not in OpenAPI spec)

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/mapper/UserMapper.java`
    *   **Summary**: This demonstrates the mapper pattern used for entity-to-DTO conversion. It's an ApplicationScoped bean with manual mapping logic.
    *   **Recommendation**: You SHOULD create a SubscriptionMapper following the same pattern:
        - `@ApplicationScoped` annotation
        - Manual field-by-field mapping (not MapStruct in this project)
        - Methods like `toDTO(Subscription)` → `SubscriptionDTO`
        - Handle null checks
        - Map enums directly (no conversion needed)

*   **File:** `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
    *   **Summary**: This provides the Stripe SDK integration. Methods are synchronous/blocking.
    *   **Recommendation**: For the checkout endpoint, you will need to:
        1. Call `billingService.createSubscription(userId, tier)` first to create the subscription entity
        2. Then call `stripeAdapter.createCheckoutSession(userId, tier, successUrl, cancelUrl)` to get the Stripe checkout URL
        3. Wrap the StripeAdapter call in `Uni.createFrom().item(() -> stripeAdapter.createCheckoutSession(...))`
        4. Return CheckoutSessionResponse with sessionId and checkoutUrl

*   **File:** `api/openapi.yaml`
    *   **Summary**: This is the source of truth for your API contract. All endpoint signatures, DTOs, error responses, and documentation MUST match this specification exactly.
    *   **Recommendation**: Reference lines 461-595 for subscription endpoints specification. Your implementation MUST match:
        - Endpoint paths exactly
        - HTTP methods (GET, POST)
        - Request/response body schemas
        - Status codes (200, 400, 401, 403, 404, 500)
        - Parameter names and types

### Implementation Tips & Notes

*   **Tip**: For the GET /subscriptions/{userId} endpoint, you need to call `billingService.getActiveSubscription(userId)`. This returns `Uni<Subscription>` which may be null if the user is FREE tier. Handle the null case by returning a default SubscriptionDTO with tier=FREE and status=ACTIVE.

*   **Tip**: For the POST /subscriptions/checkout endpoint, the workflow is:
    1. Extract tier, successUrl, cancelUrl from CreateCheckoutRequest
    2. Get authenticated userId from security context (for now, accept as parameter until auth is fully implemented)
    3. Call `billingService.createSubscription(userId, tier)`
    4. On success, call `stripeAdapter.createCheckoutSession(userId, tier, successUrl, cancelUrl)`
    5. Map CheckoutSessionResult to CheckoutSessionResponse DTO
    6. Return 200 OK with checkout URL

*   **Tip**: For the POST /subscriptions/{subscriptionId}/cancel endpoint, you need to:
    1. Validate the subscriptionId belongs to the authenticated user (authorization check)
    2. Call `billingService.cancelSubscription(userId)` (note: it takes userId, not subscriptionId)
    3. Fetch the updated subscription and return SubscriptionDTO
    4. Return 200 OK (not 204, per OpenAPI spec)

*   **Tip**: For the GET /billing/invoices endpoint, you will need to:
    1. Query PaymentHistoryRepository (you'll need to inject it)
    2. Implement pagination using `findAll().page(page, size)` Panache methods
    3. Count total elements for pagination metadata
    4. Create a PaymentHistoryMapper to convert PaymentHistory entities to PaymentHistoryDTO
    5. Build InvoiceListResponse with invoices array and pagination fields

*   **Note**: Authorization is partially implemented in this iteration. Add `@RolesAllowed("USER")` to protected endpoints, but you may need TODO comments about full authorization checks (verifying user can only access their own data) similar to UserController pattern.

*   **Note**: The project uses reactive programming with Smallrye Mutiny. All database operations return `Uni<T>` or `Multi<T>`. Chain operations using `.onItem().transform()` and `.onItem().transformToUni()`. Do NOT block or call `.await()`.

*   **Note**: Error handling is done via exception mappers. Do NOT catch exceptions in controller methods. Let them propagate and the appropriate ExceptionMapper will handle them:
    - `IllegalArgumentException` → 400 Bad Request
    - `UserNotFoundException` → 404 Not Found
    - `FeatureNotAvailableException` → 403 Forbidden
    - `StripeException` → 500 Internal Server Error (you may need to create StripeExceptionMapper)

*   **Warning**: The BillingService.createSubscription() method creates a subscription with status=TRIALING and a placeholder stripeSubscriptionId. The actual Stripe subscription will be created by the checkout endpoint, and the webhook handler (I5.T3, already implemented) will update the subscription when the user completes payment. Do NOT try to create the Stripe subscription in BillingService - that's the controller's responsibility.

*   **Warning**: Pagination in Quarkus Panache uses `Page` objects. Example: `repository.findAll().page(Page.of(page, size))` returns a paginated list. Use `.count()` to get total elements.

*   **Warning**: The OpenAPI spec shows subscriptionId in the cancel endpoint path, but BillingService.cancelSubscription() takes userId. You'll need to look up the subscription by subscriptionId first to get the entityId (userId), then call the service method.

### Required New Files

Based on the task requirements, you need to create these NEW files:

1. **SubscriptionController.java** - Main REST controller with 4 endpoints
2. **SubscriptionDTO.java** - Maps Subscription entity (11 fields per OpenAPI spec)
3. **CreateCheckoutRequest.java** - Request body for checkout endpoint (3 required fields)
4. **CheckoutSessionResponse.java** - Response for checkout endpoint (2 fields)
5. **PaymentHistoryDTO.java** - Maps PaymentHistory entity for invoice list
6. **InvoiceListResponse.java** - Paginated response wrapper (5 required fields)
7. **SubscriptionMapper.java** - Entity-to-DTO mapper (ApplicationScoped bean)
8. **PaymentHistoryMapper.java** - Entity-to-DTO mapper for payment history

### Testing Guidance

- The acceptance criteria requires manual testing with curl or Postman
- Ensure Stripe test API keys are configured in application.properties
- Test checkout URL redirect displays Stripe payment page
- Verify cancel endpoint persists canceledAt timestamp
- Verify authorization prevents users from accessing other users' data

---

## Summary

You are implementing the SubscriptionController REST API layer that provides HTTP endpoints for subscription management. This controller orchestrates calls to BillingService (domain logic) and StripeAdapter (payment processing) and returns properly formatted DTOs matching the OpenAPI specification.

**Critical Success Factors:**
1. Follow UserController patterns exactly for consistency
2. All reactive operations using Uni types (no blocking)
3. DTOs must match OpenAPI spec field-for-field
4. Proper authorization checks (user can only access own data)
5. Error handling delegated to exception mappers
6. Checkout endpoint creates local subscription THEN Stripe session
7. Cancel endpoint uses subscriptionId lookup but calls service with userId
8. Invoice endpoint implements proper pagination
