# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create Playwright end-to-end tests for complete authentication flow: user visits /login, clicks "Sign in with Google", OAuth redirect to Google (mock in test), consent granted, redirect to callback, tokens received, dashboard displayed. Test scenarios: successful login, token refresh on expiration, logout, unauthorized access redirects to login. Use Playwright to intercept OAuth redirect, mock OAuth provider responses, verify token storage in localStorage, assert dashboard elements rendered.

---

## Issues Detected

*   **Test Failure:** The test `should complete successful OAuth login flow with Google` is failing because the dashboard page shows a blank screen instead of rendering with the h1 "Dashboard" text.
*   **Test Failure:** The test `should logout by clearing localStorage and redirecting` is failing for the same reason - dashboard doesn't render.
*   **Test Failure:** The test `should refresh expired token automatically and continue` is failing - dashboard doesn't render.
*   **Test Failure:** The test `should redirect to login when refresh token is invalid` is failing because the page stays on `/dashboard` instead of redirecting to `/login`.

**Root Cause Analysis:**

The tests are setting up route mocks BEFORE navigating to pages, but Playwright's route interception might not be properly catching all requests. Additionally, the `clearAllStorage` function navigates to `/` which might be causing issues with the route mocks. The main problem is that:

1. Route mocks may not be active when the OAuth callback page makes its API call
2. The timing of when mocks are set up vs when pages navigate/make requests is causing race conditions
3. The dashboard is stuck in loading state because API requests aren't being fulfilled

---

## Best Approach to Fix

You MUST modify the E2E test file `frontend/e2e/auth.spec.ts` to ensure proper mock setup and timing:

1. **Move route mock setup to a global beforeEach hook**: Set up ALL common route mocks (user, rooms, OAuth callback) in the test's beforeEach hook BEFORE clearing storage, so they're always active.

2. **Fix the clearAllStorage helper**: Modify `frontend/e2e/fixtures/mockOAuthResponse.ts` to ensure `clearAllStorage` doesn't navigate to `/` if we're already on a page, to avoid disrupting route mocks.

3. **Add explicit wait for network idle**: After navigating to the callback URL, wait for the network to be idle before asserting on the dashboard URL.

4. **Fix mock routing patterns**: Ensure the glob patterns for API routes properly match all API calls. The current pattern `**/api/v1/users/${mockUser.userId}` might not match if there are query parameters.

5. **Add debugging**: Temporarily add route logging to understand which API calls are NOT being mocked.

6. **Fix token refresh test**: The issue with the "redirect to login when refresh token is invalid" test is that the clearAuth() call doesn't automatically redirect - you need to verify that the PrivateRoute component is properly handling the auth state change.

**Specific Changes Required:**

1. In `frontend/e2e/auth.spec.ts`:
   - Set up route mocks in beforeEach BEFORE clearAllStorage
   - Add `await page.waitForLoadState('networkidle')` after navigating to callback
   - Use more flexible route patterns like `**/api/v1/users/**` instead of specific IDs

2. In `frontend/e2e/fixtures/mockOAuthResponse.ts`:
   - Fix `clearAllStorage` to check if already on a valid page before navigating

3. Add error handling to verify mock routes are being hit (use route.fulfill() callbacks to log).
