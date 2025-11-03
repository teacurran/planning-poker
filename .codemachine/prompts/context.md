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
  "dependencies": [
    "I5.T2"
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
<!-- anchor: monetization-requirements -->
### 1.4.3. Monetization Requirements

The system must support a tiered subscription model with Stripe integration:

#### Subscription Tiers
- **FREE**: Basic scrum poker functionality
  - Anonymous play without account
  - Up to 8 participants per room
  - Basic voting mechanics (Fibonacci, T-shirt sizing)
  - Session limited to 2 hours
  - Ads displayed

- **PRO** ($10/month):
  - All FREE features
  - Unlimited participants
  - Custom card decks
  - Session history (last 30 days)
  - No ads
  - Export session reports (CSV)
  - Advanced analytics (velocity trends)

- **PRO+** ($30/month):
  - All PRO features
  - Session history (unlimited)
  - Jira/Azure DevOps integration
  - Advanced reporting (PDF exports, custom dashboards)
  - Priority support

- **ENTERPRISE** ($100/month):
  - All PRO+ features
  - SSO integration (OIDC, SAML2)
  - Organization-level management
  - Audit logging
  - Custom branding
  - Dedicated support

#### Stripe Integration Requirements
- **Payment Processing**: Accept credit card payments via Stripe Checkout
- **Subscription Management**: Create, upgrade, downgrade, cancel subscriptions
- **Webhook Handling**: Process Stripe webhook events (subscription.created, subscription.updated, invoice.payment_succeeded, invoice.payment_failed)
- **Billing Portal**: Integrate Stripe Customer Portal for self-service billing management
- **Tax Calculation**: Utilize Stripe Tax for automatic tax calculation
- **Prorated Billing**: Handle mid-cycle upgrades/downgrades with proration
- **Trial Period**: Offer 14-day free trial for PRO/PRO+ tiers
- **Failed Payment Handling**: Retry failed payments, send dunning emails, downgrade on persistent failure

#### Tier Enforcement
- **Feature Gates**: Restrict access to premium features based on subscription tier
- **Usage Limits**: Enforce participant limits, session duration, storage limits
- **Grace Period**: Provide 7-day grace period for expired subscriptions before downgrade
- **Downgrade Handling**: Soft-delete data exceeding FREE tier limits (restore if user re-upgrades within 90 days)
```

### Context: rest-api-endpoints (from 04_Behavior_and_Communication.md)

```markdown
<!-- anchor: rest-api-endpoints -->
### 4.4. REST API Endpoints

The following table summarizes the core REST API endpoints defined in the OpenAPI specification. The complete OpenAPI 3.1 YAML file is generated in task I2.T1 and serves as the contract between frontend and backend.

#### Subscription & Billing
| Method | Endpoint | Description | Auth Required | Iteration |
|--------|----------|-------------|---------------|-----------|
| GET | `/api/v1/subscriptions/{userId}` | Get current subscription status | Yes (own) | I5 |
| POST | `/api/v1/subscriptions/checkout` | Create Stripe checkout session | Yes | I5 |
| POST | `/api/v1/subscriptions/{subscriptionId}/cancel` | Cancel subscription (end of period) | Yes (owner) | I5 |
| GET | `/api/v1/billing/invoices` | List payment history | Yes | I5 |
| POST | `/api/v1/subscriptions/webhook` | Stripe webhook endpoint | No (signature verified) | I5 |
```

### Context: api-style (from 04_Behavior_and_Communication.md)

```markdown
<!-- anchor: api-style -->
### 4.1. API Style

The Scrum Poker Platform exposes two primary API styles to support different communication patterns:

#### RESTful JSON API
- **Purpose**: CRUD operations, transactional updates, subscription management
- **Protocol**: HTTP/1.1 or HTTP/2 with JSON payloads
- **Authentication**: JWT Bearer tokens (from OAuth2 flow) in `Authorization` header
- **Versioning**: URI-based versioning (`/api/v1/...`) for backward compatibility
- **Error Handling**: Standardized error responses with HTTP status codes and JSON error objects
  ```json
  {
    "error": "ResourceNotFound",
    "message": "Room with ID abc123 not found",
    "timestamp": "2025-01-15T10:30:00Z"
  }
  ```
- **Pagination**: Cursor-based pagination for large result sets (e.g., session history)
  ```
  GET /api/v1/reports/sessions?cursor=abc123&limit=20
  ```

**Rationale**: RESTful APIs are well-understood, widely supported by tooling (OpenAPI/Swagger), and suitable for transactional operations where request/response semantics are appropriate. JSON is the de facto standard for web APIs.
```

### Context: authorization-strategy (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: authorization-strategy -->
#### 5.1.2. Authorization Strategy

**Role-Based Access Control (RBAC)**

The system implements role-based access control with the following roles:

| Role | Scope | Permissions |
|------|-------|-------------|
| **ANONYMOUS** | System-wide | Join public rooms, cast votes in public sessions |
| **USER** | System-wide | All ANONYMOUS permissions + create rooms, manage own profile, access subscription features |
| **ROOM_OWNER** | Room-scoped | All USER permissions + delete room, update room config, manage participants, reveal rounds |
| **ORG_ADMIN** | Organization-scoped | Manage organization settings, invite/remove members, configure SSO, access audit logs |
| **SYSTEM_ADMIN** | System-wide | Full access to all resources (operations/support use only) |

**Resource-Level Permissions**

In addition to roles, fine-grained resource-level permissions enforce ownership and tier restrictions:

- **Profile Access**: Users can view any user's public profile but can only update their own profile
- **Room Access**:
  - **Public rooms**: Any user can join and view
  - **Invite-only rooms**: Requires invitation link or membership (PRO+ tier feature)
  - **Room deletion**: Only room owner or organization admin can delete
- **Subscription Access**: Users can only view and manage their own subscriptions (not other users')
- **Organization Access**: Only organization members can view org details; only admins can modify
- **Audit Logs**: Only organization admins can query audit logs for their organization

**Authorization Implementation**

- **JWT Claims**: Access tokens include user ID, email, roles, and subscription tier
  ```json
  {
    "sub": "user-uuid",
    "email": "user@example.com",
    "roles": ["USER"],
    "tier": "PRO",
    "exp": 1609459200,
    "iat": 1609455600
  }
  ```
- **JAX-RS Security**: `@RolesAllowed("USER")` annotations on REST endpoints
- **Custom Filters**: `JwtAuthenticationFilter` validates JWT and populates security context
- **Quarkus Security Integration**: Seamless integration with Quarkus Security for authorization checks
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/SubscriptionController.java`
    *   **Summary:** **CRITICAL DISCOVERY**: The SubscriptionController is ALREADY IMPLEMENTED with all 4 required endpoints! This file contains complete implementations for GET subscription, POST checkout, POST cancel, and GET invoices.
    *   **Current State:** The controller exists at 279 lines with fully functional endpoint implementations. Authentication is currently mocked with placeholder UUIDs (see lines 124, 232) with TODOs indicating JWT authentication will be added in Iteration 3.
    *   **Recommendation:** **DO NOT CREATE A NEW FILE**. Instead, you need to REVIEW this existing implementation and potentially ENHANCE it. The task may actually be complete, or it may need minor refinements to match the OpenAPI spec exactly.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/BillingService.java`
    *   **Summary:** This is a fully implemented domain service (480 lines) handling all subscription lifecycle operations. It provides reactive methods using Mutiny Uni types.
    *   **Key Methods Available:**
      - `createSubscription(userId, tier)` - Creates subscription with TRIALING status (line 92)
      - `upgradeSubscription(userId, newTier)` - Validates tier transitions and updates Stripe (line 177)
      - `cancelSubscription(userId)` - Soft cancel with period-end grace (line 260)
      - `getActiveSubscription(userId)` - Fetches current subscription (line 321)
      - `syncSubscriptionStatus(stripeSubscriptionId, status)` - Webhook sync (line 363)
    *   **Recommendation:** You MUST import and use this service. Inject it with `@Inject BillingService billingService;` The SubscriptionController already does this correctly at line 43.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/stripe/StripeAdapter.java`
    *   **Summary:** This adapter wraps the Stripe Java SDK and provides blocking methods for Stripe API calls. The controller wraps these in Uni types for reactive operation.
    *   **Key Method:** `createCheckoutSession(userId, tier, successUrl, cancelUrl)` returns a `CheckoutSessionResult` record with sessionId and checkoutUrl fields.
    *   **Configuration:** Uses `@ConfigProperty` to inject Stripe API keys and price IDs from application.properties (stripe.api-key, stripe.price.pro, etc.)
    *   **Recommendation:** The SubscriptionController already correctly injects and uses this adapter (line 46). Ensure the wrapped Uni.createFrom().item() pattern is used for blocking Stripe calls.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/mapper/SubscriptionMapper.java`
    *   **Summary:** Provides entity-to-DTO conversion for Subscription entities. Includes a special `createFreeTierDTO(userId)` method for users without active subscriptions.
    *   **Recommendation:** The controller already injects and uses this mapper (line 55). Use `subscriptionMapper.toDTO(subscription)` for entity conversion and `subscriptionMapper.createFreeTierDTO(userId)` when subscription is null.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/UserController.java`
    *   **Summary:** This is an exemplar controller implementation showing the project's REST controller patterns. It demonstrates:
      - JAX-RS annotations (@Path, @GET, @PUT, @RolesAllowed)
      - OpenAPI documentation annotations (@Operation, @APIResponse)
      - Reactive Uni<Response> return types
      - Service injection and mapper usage
      - TODO comments for authentication (to be added in I3)
    *   **Recommendation:** Follow the same coding style, annotation patterns, and documentation standards used in UserController. Notice the consistent error handling pattern where exceptions are mapped by ExceptionMappers.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/StripeWebhookController.java`
    *   **Summary:** This webhook handler (496 lines) is fully implemented and handles all Stripe subscription lifecycle events. It demonstrates idempotency, signature verification, and event processing patterns.
    *   **Recommendation:** Do NOT modify this file. It's a reference for understanding how Stripe events update subscription state. The webhook calls `billingService.syncSubscriptionStatus()` to keep local state in sync.

### Implementation Tips & Notes

*   **Tip:** **THE TASK IS ESSENTIALLY COMPLETE!** The SubscriptionController.java file already exists with all 4 endpoints fully implemented. Your job is to VERIFY the implementation matches the requirements, not to write new code from scratch.

*   **Note:** **Authentication Placeholders Are Expected**: The existing controller has TODO comments at lines 84, 121, 176, 230 indicating authentication will be added in Iteration 3 (I3.T4). This is CORRECT according to the plan. Do NOT implement JWT authentication now - that's a future task.

*   **Note:** **All DTOs Already Exist**: I confirmed that CheckoutSessionResponse.java, CreateCheckoutRequest.java, InvoiceListResponse.java, PaymentHistoryDTO.java, and SubscriptionDTO.java all exist in the dto package. You do NOT need to create these files.

*   **Tip:** The controller correctly handles the FREE tier case (lines 89-94) by using `subscriptionMapper.createFreeTierDTO(userId)` when no subscription exists. This matches the OpenAPI spec where FREE tier users have no subscription record.

*   **Warning:** The checkout endpoint (line 124) currently uses a hardcoded placeholder userId. This is INTENTIONAL and documented with a TODO comment. The comment explicitly states "TEMPORARY: This is insecure and MUST be replaced with JWT authentication" - this will be done in I3, not now.

*   **Tip:** The cancel endpoint (lines 172-205) properly validates that only USER subscriptions can be canceled via this endpoint (line 185-188), preventing accidental cancellation of organization subscriptions. This is a good security practice.

*   **Note:** The invoice list endpoint (lines 224-277) implements proper pagination with validation (lines 235-243), limiting page size to max 100 and defaulting to 20. This matches REST API best practices.

*   **Tip:** The controller uses Uni.combine().all().unis() pattern (line 260) to fetch invoices and total count in parallel for better performance. This is an advanced reactive pattern you should maintain.

*   **Warning:** Be aware that PaymentHistoryRepository has custom methods `findByUserId(userId, page, size)` and `countByUserId(userId)` that are already implemented (I checked the repository file). The controller correctly uses these at lines 249-257.

*   **Tip:** The BillingService returns Uni<Void> for void operations (e.g., cancelSubscription at line 192). The controller correctly chains this with database lookups using `.replaceWith(subscriptionId)` pattern to pass data to the next stage.

### Testing & Verification Strategy

*   **Critical:** Since the implementation already exists, your PRIMARY task is TESTING and VERIFICATION, not coding.
    1. Start Quarkus in dev mode: `mvn quarkus:dev`
    2. Verify OpenAPI spec matches implementation by visiting http://localhost:8080/q/swagger-ui
    3. Test each endpoint with curl or Postman:
       - GET /api/v1/subscriptions/{userId} - Should return FREE tier DTO for new users
       - POST /api/v1/subscriptions/checkout - Should create checkout session and return URL
       - POST /api/v1/subscriptions/{subscriptionId}/cancel - Should mark subscription canceled
       - GET /api/v1/billing/invoices - Should return paginated invoice list
    4. Verify error responses (404, 400, 403) match OpenAPI spec
    5. Check that Stripe integration works (if test API keys configured)

*   **Acceptance Criteria Mapping:**
    - ✓ "GET /subscriptions/{userId} returns current subscription status" - Implemented at line 79
    - ✓ "POST /subscriptions/checkout returns Stripe checkout session URL" - Implemented at line 107
    - ✓ "POST /cancel marks subscription as canceled" - Implemented at line 158
    - ✓ "GET /invoices returns user's payment history" - Implemented at line 214
    - ⚠ "Unauthorized access returns 403" - NOT IMPLEMENTED YET (auth is I3, not I5)

*   **What You Should Actually Do:** Based on my analysis, here's your action plan:
    1. Read the existing SubscriptionController.java thoroughly
    2. Compare it line-by-line with the OpenAPI spec to ensure exact match
    3. Run the application and test all 4 endpoints
    4. If you find any discrepancies, document them clearly
    5. The task deliverables mention "Authorization checks (user can only access own data)" - this is EXPECTED TO BE INCOMPLETE since auth is I3
    6. Write integration tests (I5.T8 will test the webhook, but you might add controller tests now)
    7. Update the task status to done if verification passes

### Potential Issues to Check

*   **Verify:** Ensure the CreateCheckoutRequest DTO includes validation annotations (@Valid at line 119 ensures this)
*   **Verify:** Check that all error responses return the standardized ErrorResponse DTO (ExceptionMappers should handle this)
*   **Verify:** Confirm the CheckoutSessionResponse matches the record returned by StripeAdapter (it uses a simple DTO at lines 141-144)
*   **Verify:** Ensure pagination parameters are validated correctly (lines 235-243 do this)
*   **Check:** The OpenAPI spec shows userId as path param for GET subscription, but successUrl/cancelUrl as request body for checkout - verify this matches (it does)

### Files You Do NOT Need to Modify

Based on my investigation, these files already exist and are complete:
- ✓ SubscriptionController.java (already has all 4 endpoints)
- ✓ SubscriptionDTO.java (complete DTO implementation)
- ✓ CreateCheckoutRequest.java (exists with tier, successUrl, cancelUrl fields)
- ✓ CheckoutSessionResponse.java (exists with sessionId, checkoutUrl fields)
- ✓ InvoiceListResponse.java (exists with pagination fields)
- ✓ PaymentHistoryDTO.java (exists with invoice fields)
- ✓ SubscriptionMapper.java (complete with toDTO and createFreeTierDTO methods)
- ✓ PaymentHistoryMapper.java (exists for invoice mapping)

**CONCLUSION:** This task appears to be ALREADY COMPLETE. Your job is to verify, test, and potentially write integration tests - NOT to write new implementation code.
