# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I5.T6",
  "iteration_id": "I5",
  "iteration_goal": "Implement Stripe subscription billing, tier enforcement (Free/Pro/Pro+/Enterprise), payment flows, webhook handling for subscription lifecycle events, and frontend upgrade UI.",
  "description": "Implement React components for subscription management. `PricingPage`: display tier comparison table (Free, Pro, Pro+, Enterprise), feature lists, pricing, \"Upgrade\" buttons calling checkout API. `UpgradeModal`: modal prompting user to upgrade when hitting tier limit (e.g., trying to create invite-only room as Free user), displays tier benefits, \"Upgrade Now\" button. `SubscriptionSettingsPage`: show current subscription tier, billing status, \"Cancel Subscription\" button, payment history table. Integrate with subscription API hooks (`useSubscription`, `useCreateCheckout`, `useCancelSubscription`).",
  "agent_type_hint": "FrontendAgent",
  "inputs": "Subscription tier features from product spec, OpenAPI spec for subscription endpoints, Stripe checkout flow",
  "input_files": [
    "api/openapi.yaml"
  ],
  "target_files": [
    "frontend/src/pages/PricingPage.tsx",
    "frontend/src/components/subscription/UpgradeModal.tsx",
    "frontend/src/pages/SubscriptionSettingsPage.tsx",
    "frontend/src/components/subscription/TierComparisonTable.tsx",
    "frontend/src/services/subscriptionApi.ts"
  ],
  "deliverables": "PricingPage with responsive tier comparison table, Upgrade buttons initiating Stripe checkout (redirect to Stripe), UpgradeModal triggered on feature gate 403 errors, SubscriptionSettingsPage showing current tier, status, cancel button, Payment history table (invoices, dates, amounts), React Query hooks for subscription API calls",
  "acceptance_criteria": "PricingPage displays all tiers with features, Clicking \"Upgrade\" button calls checkout API and redirects to Stripe, Stripe checkout completes, user returned to app with success message, UpgradeModal appears when 403 FeatureNotAvailable error, SubscriptionSettingsPage shows correct tier badge, Cancel subscription button triggers confirmation modal, then API call, Payment history table lists past invoices",
  "dependencies": [
    "I5.T5"
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
#### Monetization Requirements
- **Stripe Integration:** Subscription management, payment processing, webhook handling
- **Tier Enforcement:** Feature gating based on subscription level (ads, reports, room privacy, branding)
- **Upgrade Flows:** In-app prompts, modal CTAs, settings panel upsells
- **Billing Dashboard:** Subscription status, payment history, plan management
```

### Context: reporting-requirements (from 01_Context_and_Drivers.md)

```markdown
#### Reporting Requirements
- **Free Tier:** Basic session summaries (story count, consensus rate, average vote)
- **Pro Tier:** Round-level detail, user consistency metrics, CSV/JSON/PDF export
- **Enterprise Tier:** Organizational dashboards, team trends, SSO-filtered reports, audit logs
```

### Context: OpenAPI Subscription Endpoints (from api/openapi.yaml)

The REST API provides these subscription-related endpoints:

- **GET /api/v1/subscriptions/{userId}** - Get current subscription status
  - Returns: SubscriptionDTO with tier, status, billing period info

- **POST /api/v1/subscriptions/checkout** - Create Stripe checkout session
  - Request: CheckoutRequest (tier: PRO|PRO_PLUS, successUrl, cancelUrl)
  - Response: CheckoutResponse (sessionId, checkoutUrl for redirect)

- **POST /api/v1/subscriptions/{subscriptionId}/cancel** - Cancel subscription
  - Cancels at end of billing period, access continues until period end
  - Response: Updated SubscriptionDTO with canceledAt timestamp

- **GET /api/v1/billing/invoices** - List payment history
  - Query params: page, size (pagination)
  - Response: InvoiceListResponse with array of PaymentHistoryDTO

### Context: Subscription DTO Schemas (from api/openapi.yaml)

```typescript
// SubscriptionDTO structure
{
  subscriptionId: string (uuid),
  stripeSubscriptionId?: string,
  entityId: string (uuid),
  entityType: "USER" | "ORGANIZATION",
  tier: "FREE" | "PRO" | "PRO_PLUS" | "ENTERPRISE",
  status: "ACTIVE" | "TRIALING" | "PAST_DUE" | "CANCELED" | "PAUSED",
  currentPeriodStart: string (date-time),
  currentPeriodEnd: string (date-time),
  canceledAt?: string | null,
  createdAt: string (date-time)
}

// CheckoutRequest
{
  tier: "PRO" | "PRO_PLUS",
  successUrl: string (uri),
  cancelUrl: string (uri)
}

// CheckoutResponse
{
  sessionId: string,
  checkoutUrl: string (uri)
}

// PaymentHistoryDTO
{
  paymentId: string (uuid),
  subscriptionId: string (uuid),
  stripeInvoiceId?: string,
  amount: number (cents),
  currency: string (3 chars, e.g., "USD"),
  status: "PAID" | "PENDING" | "FAILED" | "REFUNDED",
  paidAt: string (date-time)
}

// InvoiceListResponse (paginated)
{
  invoices: PaymentHistoryDTO[],
  page: number,
  size: number,
  totalElements: number,
  totalPages: number
}
```

### Context: Feature Tier Matrix (from FeatureGate.java analysis)

The subscription tier hierarchy is: **FREE < PRO < PRO_PLUS < ENTERPRISE**

Higher tiers inherit all features from lower tiers.

**Free Tier:**
- Basic planning poker functionality
- Public rooms only
- Basic session summaries (story count, consensus rate)
- Shows banner ads

**Pro Tier ($10/month):**
- All Free features, plus:
- Ad-free experience
- Advanced reports with round-level detail
- User consistency metrics
- CSV/JSON/PDF export
- Enhanced session history (90 days vs 30 days)

**Pro Plus Tier ($30/month):**
- All Pro features, plus:
- Invite-only rooms (explicit participant whitelist)
- Enhanced privacy controls

**Enterprise Tier ($100/month):**
- All Pro Plus features, plus:
- Organization management
- SSO integration (OIDC/SAML2)
- Audit logging
- Organization-wide analytics
- Organization-restricted rooms
- Unlimited session history

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### File: `frontend/src/services/api.ts`
- **Summary:** This file contains the Axios API client with authentication interceptor and token refresh logic. It exports `apiClient` (configured Axios instance) and `getErrorMessage()` utility.
- **Recommendation:** You MUST import and use `apiClient` from this file for all subscription API calls. The request interceptor automatically adds the Authorization Bearer token.
- **Note:** The response interceptor handles 401 errors with automatic token refresh. When a 403 error occurs (FeatureNotAvailable), your UpgradeModal should detect this and trigger.

#### File: `frontend/src/services/apiHooks.ts`
- **Summary:** Contains React Query hooks following established patterns: `useUser`, `useRooms`, `useRoomById`, `useCreateRoom`, `useUpdateRoom`, `useDeleteRoom`. Includes centralized `queryKeys` factory.
- **Recommendation:** You MUST follow the same pattern when creating subscription hooks. Add new query keys to the `queryKeys` object:
  ```typescript
  subscriptions: {
    all: ['subscriptions'] as const,
    byUser: (userId: string) => ['subscriptions', 'user', userId] as const,
  },
  billing: {
    invoices: (userId: string, page: number, size: number) => ['billing', 'invoices', userId, page, size] as const,
  }
  ```
- **Note:** All existing hooks use `staleTime` for caching (5 minutes for users, 2 minutes for rooms). Use similar values for subscription data. Payment history can use longer cache (5 minutes) since it changes infrequently.

#### File: `frontend/src/stores/authStore.ts`
- **Summary:** Zustand store managing authentication state with localStorage persistence. Exposes `user`, `accessToken`, `refreshToken`, `isAuthenticated`, and actions `setAuth`, `clearAuth`.
- **Recommendation:** You MUST use `useAuthStore()` hook to access current user information. The user object includes `subscriptionTier` field that you'll need for displaying current tier badges.
- **Critical:** When the subscription changes (after successful Stripe checkout), the JWT will include updated tier information. Ensure the auth store refreshes user data after checkout completion.

#### File: `frontend/src/types/auth.ts`
- **Summary:** Defines core TypeScript types: `UserDTO`, `SubscriptionTier`, `TokenResponse`, `OAuthProvider`, `ErrorResponse`.
- **Recommendation:** You MUST import `SubscriptionTier` type from here. DO NOT redefine it.
- **Note:** The existing `ErrorResponse` interface has fields: `error`, `message`, `timestamp`, `details?`. When catching 403 errors for tier enforcement, parse this structure.

#### File: `backend/src/main/java/com/scrumpoker/api/rest/SubscriptionController.java`
- **Summary:** REST controller implementing all 4 subscription endpoints. Uses reactive `Uni<>` return types. Integrates with `BillingService` and `StripeAdapter`.
- **Recommendation:** Your frontend API calls will interact with these endpoints. The checkout endpoint returns a `checkoutUrl` that you must redirect to (window.location.href).
- **Critical Finding:** The checkout and cancel endpoints use `@RolesAllowed("USER")` which means they require JWT authentication. The GET subscription endpoint does not have this annotation in code but the OpenAPI spec says it requires auth - assume auth is enforced by JWT filter.

#### File: `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
- **Summary:** Service implementing tier-based feature access control. When backend detects insufficient tier, it throws `FeatureNotAvailableException` which returns 403 to frontend.
- **Recommendation:** Your `UpgradeModal` should trigger when API calls return 403 status with error message containing "Upgrade to {tier} to access this feature". Parse the error response to extract the required tier.
- **Note:** The tier hierarchy is enforced via enum ordinal: FREE(0) < PRO(1) < PRO_PLUS(2) < ENTERPRISE(3). Higher tiers inherit all lower tier features.

#### File: `frontend/src/components/dashboard/UserProfileCard.tsx`
- **Summary:** Displays user avatar, name, email, and subscription tier badge. Contains utility functions `getTierBadgeClasses()` and `formatTierName()` for tier UI rendering.
- **Recommendation:** You SHOULD reuse or extract these utility functions to a shared file (e.g., `frontend/src/utils/subscriptionUtils.ts`) so both UserProfileCard and your new subscription components use consistent tier badge styling.
- **Tip:** The tier badge color scheme is:
  - FREE: gray (`bg-gray-100 text-gray-800`)
  - PRO: blue (`bg-blue-100 text-blue-800`)
  - PRO_PLUS: purple (`bg-purple-100 text-purple-800`)
  - ENTERPRISE: yellow (`bg-yellow-100 text-yellow-800`)

### Implementation Tips & Notes

#### Tip 1: React Query Mutation Pattern for Checkout
When implementing `useCreateCheckout()` mutation:
1. Call `POST /api/v1/subscriptions/checkout` with tier, successUrl, cancelUrl
2. On success, receive `checkoutUrl` from response
3. Redirect user to Stripe: `window.location.href = checkoutUrl`
4. Stripe will handle payment, then redirect back to `successUrl` or `cancelUrl`
5. On the success page (likely `/billing/success`), you should refresh user data to get updated subscription tier from JWT

#### Tip 2: 403 Error Detection for UpgradeModal
The backend returns this error structure for tier restrictions:
```typescript
{
  error: "FEATURE_NOT_AVAILABLE",
  message: "Upgrade to PRO to access this feature: Advanced Reports",
  timestamp: "2025-01-15T10:30:00Z",
  details: {
    requiredTier: "PRO",
    currentTier: "FREE",
    feature: "Advanced Reports"
  }
}
```

You should create a global Axios response interceptor (or React Query error handler) that:
1. Detects 403 status with `error === "FEATURE_NOT_AVAILABLE"`
2. Extracts `requiredTier` from response
3. Triggers UpgradeModal with the required tier information
4. Displays benefit of upgrading and "Upgrade Now" button

#### Tip 3: Stripe Checkout Flow Success Handling
After Stripe redirects back to `successUrl`:
1. The URL may contain query params like `?session_id={id}`
2. Your success page should call `GET /api/v1/subscriptions/{userId}` to verify subscription was created
3. If subscription status is ACTIVE or TRIALING, show success message
4. Trigger auth store refresh to update user's subscription tier in local state
5. Redirect to dashboard or subscription settings page

#### Tip 4: Subscription Cancellation Confirmation
Before calling the cancel API:
1. Show a confirmation modal explaining "Subscription will remain active until {currentPeriodEnd}"
2. Only call `POST /api/v1/subscriptions/{subscriptionId}/cancel` after user confirms
3. On success, update UI to show "Cancels on {currentPeriodEnd}" instead of "Active"
4. The canceledAt field in SubscriptionDTO will be populated after cancellation

#### Tip 5: Payment History Pagination
The invoice list endpoint returns paginated results. Your payment history table should:
1. Use React Query's `keepPreviousData: true` option for smooth pagination
2. Show Previous/Next buttons based on `page` and `totalPages`
3. Display amount in user-friendly format: `$29.99` instead of `2999` cents
4. Format dates consistently using `date-fns` library (already in package.json)
5. Handle empty state: "No payment history yet"

#### Warning: Pricing Information Storage
DO NOT hardcode pricing in frontend code. Instead:
- Create a configuration object mapping tiers to prices: `{ PRO: 10, PRO_PLUS: 30, ENTERPRISE: 100 }`
- This allows easy price updates without code changes
- Consider fetching pricing from backend in future iterations for A/B testing

#### Note: Tailwind CSS and HeadlessUI Usage
The project uses Tailwind CSS and HeadlessUI. You SHOULD:
- Use HeadlessUI's `<Dialog>` component for UpgradeModal (already in dependencies)
- Use Tailwind responsive classes: `grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4` for tier comparison table
- Follow existing component patterns for dark mode support: `bg-white dark:bg-gray-800`
- Use existing color scheme: primary color is blue (check tailwind.config.js)

#### Critical: TypeScript Type Safety
You MUST create TypeScript interfaces for all new API types:
1. Create `frontend/src/types/subscription.ts` with:
   - `SubscriptionDTO` interface matching OpenAPI spec
   - `CheckoutRequest` interface
   - `CheckoutResponse` interface
   - `PaymentHistoryDTO` interface
   - `InvoiceListResponse` interface
   - `SubscriptionStatus` type
2. Import and use these types in all API hooks and components
3. The project uses strict TypeScript (check tsconfig.json) - ensure all types are properly defined

#### Test Considerations
While this task doesn't explicitly require tests, you should ensure:
1. All API hooks properly invalidate caches after mutations
2. Error states are handled (network failures, 500 errors, 403 errors)
3. Loading states are shown during API calls (use React Query's `isLoading` / `isPending`)
4. Empty states are handled (no invoices, free tier with no subscription record)

### Recommended Implementation Order

To maximize success, implement in this order:

1. **Create TypeScript types** (`frontend/src/types/subscription.ts`)
2. **Implement API service layer** (`frontend/src/services/subscriptionApi.ts` with React Query hooks)
3. **Create reusable utility functions** (tier formatting, badge colors, price formatting)
4. **Build TierComparisonTable component** (reusable, used by PricingPage)
5. **Build PricingPage** (uses TierComparisonTable, integrates checkout hook)
6. **Build UpgradeModal** (global modal triggered by 403 errors)
7. **Build SubscriptionSettingsPage** (shows current subscription, payment history, cancel button)
8. **Add global error interceptor** (detect 403 and trigger UpgradeModal)
9. **Test the full flow** (upgrade, checkout redirect, success handling, cancellation)

### Key Architectural Decisions to Respect

1. **Reactive API Client:** The backend uses reactive Quarkus with `Uni<>` return types. Your frontend should handle async responses with React Query.
2. **Subscription Entity Structure:** Subscriptions are linked to either USER or ORGANIZATION (entityType field). For now, only handle USER subscriptions. Organization subscriptions are Iteration 7.
3. **Tier Enforcement Location:** Tier checks happen on backend (FeatureGate service). Frontend should respect 403 errors, not duplicate enforcement logic.
4. **Stripe Integration:** Checkout sessions are created by backend. Frontend only redirects to checkoutUrl. Webhook handling is backend-only (already implemented in I5.T3).
5. **JWT Token Updates:** After subscription changes, the user should re-authenticate or refresh token to get updated tier in JWT claims. Consider triggering token refresh after successful checkout.

---

## Summary Checklist

Before you start coding, ensure you understand:

- ✅ Subscription tier hierarchy: FREE < PRO < PRO_PLUS < ENTERPRISE
- ✅ Four main endpoints: GET subscription, POST checkout, POST cancel, GET invoices
- ✅ UpgradeModal triggers on 403 errors from backend
- ✅ Stripe checkout is redirect-based (window.location.href to checkoutUrl)
- ✅ Must follow existing React Query patterns from apiHooks.ts
- ✅ Must use apiClient from api.ts for all API calls
- ✅ Must reuse tier badge styling from UserProfileCard
- ✅ Payment amounts are in cents (convert to dollars for display)
- ✅ Subscription cancellation is end-of-period (access continues until currentPeriodEnd)
- ✅ Must create TypeScript types matching OpenAPI schemas

Good luck! Follow the existing code patterns closely, and you'll deliver a high-quality subscription management UI.
