# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Implement React components for subscription management. `PricingPage`: display tier comparison table (Free, Pro, Pro+, Enterprise), feature lists, pricing, "Upgrade" buttons calling checkout API. `UpgradeModal`: modal prompting user to upgrade when hitting tier limit (e.g., trying to create invite-only room as Free user), displays tier benefits, "Upgrade Now" button. `SubscriptionSettingsPage`: show current subscription tier, billing status, "Cancel Subscription" button, payment history table. Integrate with subscription API hooks (`useSubscription`, `useCreateCheckout`, `useCancelSubscription`).

---

## Issues Detected

*   **Linting Error**: There are 2 TypeScript linting errors in `frontend/src/pages/BillingSuccessPage.tsx`:
    - Line 52: `Unexpected any. Specify a different type @typescript-eslint/no-explicit-any`
    - Line 94: `Unexpected any. Specify a different type @typescript-eslint/no-explicit-any`
*   **Type Safety Issue**: The code uses `as any` to cast the `tier` query parameter instead of properly typing it as `SubscriptionTier | null`.

---

## Best Approach to Fix

You MUST modify the `BillingSuccessPage.tsx` file to fix the TypeScript typing issues:

1. **Remove the `as any` type casts** on lines 52 and 94
2. **Properly type the tier parameter** - The `tier` comes from URL query params as a string, and should be validated as a valid `SubscriptionTier` or treated as `null` if invalid
3. **Add type guard validation** to ensure the tier string from URL is a valid `SubscriptionTier` before using it

Here's the specific fix needed:

```typescript
// Add this type guard function at the top of the file
function isValidTier(tier: string | null): tier is SubscriptionTier {
  if (!tier) return false;
  return ['FREE', 'PRO', 'PRO_PLUS', 'ENTERPRISE'].includes(tier);
}

// In the component, validate the tier:
const tierParam = searchParams.get('tier');
const tier = isValidTier(tierParam) ? tierParam : null;

// Then use the validated tier without any casts:
{tier
  ? `Welcome to ${formatTierName(tier)}! Your subscription has been successfully activated.`
  : 'Your subscription has been successfully activated.'}

// And later:
You now have access to all {tier ? formatTierName(tier) : 'premium'} features.
```

This approach ensures type safety without using `any` and will pass the linter.
