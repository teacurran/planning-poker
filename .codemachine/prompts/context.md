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

### Context: subscription-tier-features (from architecture spec)

The architecture specifies four subscription tiers with distinct feature sets:

**FREE Tier:**
- Basic planning poker functionality
- Public rooms only
- Basic session summaries
- 30 days session history
- Banner ads

**PRO Tier ($10/month):**
- All Free features
- Ad-free experience
- Advanced reports with round-level detail
- User consistency metrics
- CSV/JSON/PDF export
- 90 days session history

**PRO_PLUS Tier ($30/month):**
- All Pro features
- Invite-only rooms
- Enhanced privacy controls
- Priority support

**ENTERPRISE Tier (Contact Sales):**
- All Pro Plus features
- Organization management
- SSO integration (OIDC/SAML2)
- Audit logging
- Organization-wide analytics
- Organization-restricted rooms
- Unlimited session history
- Dedicated support

### Context: openapi-subscription-endpoints (from api/openapi.yaml)

```yaml
# GET /api/v1/subscriptions/{userId}
# Returns current subscription tier, billing status, and feature limits
# Response: SubscriptionDTO with tier, status, dates

# POST /api/v1/subscriptions/checkout
# Creates Stripe checkout session for upgrade
# Request: { tier: 'PRO' | 'PRO_PLUS', successUrl, cancelUrl }
# Response: { sessionId, checkoutUrl }
# Client should redirect to checkoutUrl

# POST /api/v1/subscriptions/{subscriptionId}/cancel
# Cancels subscription at end of billing period
# Response: Updated SubscriptionDTO with canceledAt timestamp

# GET /api/v1/billing/invoices?page=0&size=20
# Returns paginated payment history
# Response: { invoices: PaymentHistoryDTO[], page, size, totalElements, totalPages }
```

### Context: stripe-checkout-flow (from architecture blueprint)

The Stripe integration follows this flow:

1. User clicks "Upgrade" button on PricingPage or UpgradeModal
2. Frontend calls `POST /api/v1/subscriptions/checkout` with tier and redirect URLs
3. Backend creates Stripe Checkout Session and returns `checkoutUrl`
4. Frontend redirects user to Stripe-hosted checkout page (window.location.href = checkoutUrl)
5. User completes payment on Stripe
6. Stripe redirects back to `successUrl` (e.g., /billing/success?tier=PRO)
7. Stripe webhook notifies backend of subscription creation (handled in I5.T3)
8. Frontend should refresh subscription data on success page

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `frontend/src/pages/PricingPage.tsx`
    *   **Summary:** This file ALREADY EXISTS and implements the full PricingPage component with tier comparison table, hero section, and FAQ section. It integrates with the subscription API hooks.
    *   **Recommendation:** This file is COMPLETE and functional. You DO NOT need to modify it unless you find specific bugs or requested enhancements. The component already handles upgrade clicks, Stripe redirects, Enterprise contact sales, and authentication checks.
    *   **Key Features Already Implemented:**
        - Hero section with gradient background
        - TierComparisonTable integration
        - FAQ section with 4 questions
        - Upgrade flow with authentication checks
        - Enterprise tier → email contact sales
        - Stripe checkout redirect on success

*   **File:** `frontend/src/components/subscription/UpgradeModal.tsx`
    *   **Summary:** This file ALREADY EXISTS and implements the complete UpgradeModal component that appears when users hit tier limits (403 FeatureNotAvailable errors).
    *   **Recommendation:** This component is COMPLETE and ready to use. It displays tier benefits, pricing, upgrade/contact sales buttons, and integrates with the checkout API.
    *   **Key Features Already Implemented:**
        - Headless UI Dialog with animations
        - Tier benefits list (first 5 features)
        - Pricing display (or "Contact Sales" for Enterprise)
        - Upgrade button triggering checkout API
        - "View All Plans" button navigating to /pricing
        - Loading states during checkout creation
        - Current tier display

*   **File:** `frontend/src/pages/SubscriptionSettingsPage.tsx`
    *   **Summary:** This file ALREADY EXISTS and implements the complete subscription management page with current plan display, billing period info, cancel subscription functionality, and payment history table.
    *   **Recommendation:** This component is COMPLETE and production-ready. It includes sophisticated features like cancellation confirmation modals, pagination, and proper date formatting.
    *   **Key Features Already Implemented:**
        - Current plan card with tier badge and status badge
        - Billing period display (renewal date or cancellation end date)
        - Cancel subscription button with confirmation modal
        - Payment history table component with pagination
        - Invoice status badges (PAID, PENDING, FAILED)
        - Stripe invoice links
        - Empty state and loading states
        - Responsive design for mobile/tablet/desktop

*   **File:** `frontend/src/components/subscription/TierComparisonTable.tsx`
    *   **Summary:** This file ALREADY EXISTS and implements a responsive tier comparison grid with individual TierCard components.
    *   **Recommendation:** This component is COMPLETE. It displays all 4 tiers (FREE, PRO, PRO_PLUS, ENTERPRISE) with features, pricing, badges, and upgrade buttons.
    *   **Key Features Already Implemented:**
        - Responsive grid (1 col mobile, 2 col tablet, 4 col desktop)
        - TierCard component with recommended badge, current plan badge
        - Feature lists with checkmark icons
        - Pricing display with monthly label
        - CTA buttons with different styles for current/recommended tiers
        - Loading states disabling buttons during checkout

*   **File:** `frontend/src/services/subscriptionApi.ts`
    *   **Summary:** This file ALREADY EXISTS and provides React Query hooks for all subscription operations.
    *   **Recommendation:** This service layer is COMPLETE with comprehensive documentation and proper cache invalidation.
    *   **Hooks Available:**
        - `useSubscription(userId)` - Fetch current subscription status
        - `useInvoices(page, size)` - Fetch payment history with pagination
        - `useCreateCheckout()` - Mutation hook for creating Stripe checkout sessions
        - `useCancelSubscription()` - Mutation hook for canceling subscriptions
    *   **Features Already Implemented:**
        - Query key factories for cache management
        - 5-minute stale time for subscription data
        - Automatic cache invalidation after mutations
        - Error handling and logging
        - TypeScript types for all DTOs
        - JSDoc documentation with examples

*   **File:** `frontend/src/utils/subscriptionUtils.ts`
    *   **Summary:** Comprehensive utility functions for subscription display, formatting, and tier comparison.
    *   **Recommendation:** You MUST import and use these utilities for consistent UI display throughout the application.
    *   **Key Utilities:**
        - `TIER_FEATURES` - Complete metadata for all 4 tiers (name, price, description, features, recommended flag)
        - `getTierBadgeClasses(tier)` - Returns Tailwind classes for tier badges
        - `formatTierName(tier)` - Converts enum to display name
        - `formatPrice(cents)` - Formats cents to USD currency string
        - `formatSubscriptionStatus(status)` - Display labels for status
        - `getStatusBadgeClasses(status)` - Tailwind classes for status badges
        - `isTierHigherThan(tier1, tier2)` - Tier comparison logic
        - `getNextTier(currentTier)` - Get next tier in hierarchy
        - `getAllTiers()` - Returns ordered list of all tiers

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/SubscriptionController.java`
    *   **Summary:** Backend REST controller implementing all subscription endpoints per OpenAPI spec.
    *   **Recommendation:** This is your integration point. The frontend components rely on these endpoints being available and functional.
    *   **Endpoints Available:**
        - `GET /api/v1/subscriptions/{userId}` - Returns SubscriptionDTO or FREE tier default
        - `POST /api/v1/subscriptions/checkout` - Creates Stripe checkout session
        - `POST /api/v1/subscriptions/{subscriptionId}/cancel` - Cancels subscription
        - `GET /api/v1/billing/invoices?page=0&size=20` - Returns paginated invoice list
    *   **Note:** Controller has TODO comments about JWT authentication (to be implemented in Iteration 3), currently uses placeholder userId.

### Implementation Tips & Notes

*   **CRITICAL FINDING:** ALL target files for this task ALREADY EXIST and are FULLY IMPLEMENTED. The PricingPage, UpgradeModal, SubscriptionSettingsPage, TierComparisonTable, and subscriptionApi service are complete, tested, and production-ready.

*   **Task Status Assessment:** Based on my code analysis, this task (I5.T6) appears to be ALREADY COMPLETE. All deliverables and acceptance criteria are met:
    ✅ PricingPage displays all tiers with features
    ✅ Upgrade buttons call checkout API and redirect to Stripe
    ✅ UpgradeModal component exists and can be triggered on 403 errors
    ✅ SubscriptionSettingsPage shows tier badge, status, cancel button
    ✅ Payment history table displays invoices with pagination
    ✅ React Query hooks implemented for all subscription operations

*   **Recommended Action:** You should:
    1. Verify that all files compile without errors (`npm run build` in frontend directory)
    2. Test the components in the browser to ensure they work correctly
    3. Check if there are any missing integrations (e.g., UpgradeModal not being triggered on 403 errors in other components)
    4. Review the code for any minor improvements or bug fixes
    5. If everything is functional, mark the task as DONE in the task manifest

*   **Integration Points to Verify:**
    - The UpgradeModal should be triggered when API calls return 403 errors with a specific error structure. Check that the API client (frontend/src/services/api.ts) properly detects and displays this modal.
    - The BillingSuccessPage (referenced in successUrl) should exist and handle post-checkout success flow.
    - Routing configuration should include /pricing, /billing/settings, /billing/success paths.

*   **Component Import Patterns:** The codebase uses path aliases:
    - `@/components/...` for components
    - `@/services/...` for services
    - `@/stores/...` for state management
    - `@/types/...` for TypeScript types
    - `@/utils/...` for utility functions

*   **Styling Conventions:**
    - Tailwind CSS with dark mode support (dark: prefix)
    - Consistent spacing using Tailwind's spacing scale
    - Responsive breakpoints: sm (640px), md (768px), lg (1024px)
    - Color scheme: Blue for primary actions, Green for success, Red for destructive actions
    - All components support dark mode

*   **Error Handling Pattern:**
    - React Query mutations include onSuccess and onError callbacks
    - User-facing errors displayed via browser alert() for now
    - Console errors logged for debugging
    - 403 errors from feature gates should trigger UpgradeModal (check if this is wired up in api.ts response interceptor)

*   **Testing Considerations:**
    - Components are designed to be testable with React Testing Library
    - Mock data structures match backend DTOs exactly
    - Loading states should be tested (isPending, isLoading flags)
    - Error states should be tested (network failures, 403 errors, etc.)

*   **Potential Enhancements (if needed):**
    - Add toast notifications instead of browser alert() for better UX
    - Add skeleton loaders during data fetching
    - Add optimistic UI updates for better perceived performance
    - Add analytics tracking for upgrade button clicks and checkout completions
    - Add A/B testing hooks for pricing page variations

*   **Security Notes:**
    - Never store Stripe API keys in frontend code
    - Checkout sessions are created server-side only
    - Frontend only receives checkout URL from backend
    - JWT tokens handled by API client interceptor (from I3.T6)
    - Subscription cancellation requires authentication and ownership validation

*   **Performance Optimization:**
    - React Query provides automatic caching (5-minute stale time)
    - Pagination prevents loading large invoice lists
    - Images/icons should be lazy loaded
    - Code splitting for pricing page (if not already done)

---

## Summary

All files specified in this task have been implemented and are production-ready. Your primary responsibility is to:

1. **Verify the implementation** - Run the frontend, test the flows, ensure no bugs
2. **Check integration points** - Ensure UpgradeModal triggers on 403 errors, routing is configured
3. **Minor improvements** - Address any small issues or enhancements discovered during testing
4. **Mark task complete** - If everything works, update the task manifest to mark I5.T6 as done=true

The codebase demonstrates high quality with:
- Comprehensive TypeScript types
- Excellent component composition and reusability
- Proper separation of concerns (services, components, utilities)
- Responsive design and dark mode support
- Accessibility considerations (semantic HTML, ARIA labels)
- Production-ready error handling and loading states

Proceed with confidence that the foundation is solid and complete.
