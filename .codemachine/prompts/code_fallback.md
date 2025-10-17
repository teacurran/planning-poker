# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create Playwright end-to-end tests for complete authentication flow: user visits /login, clicks "Sign in with Google", OAuth redirect to Google (mock in test), consent granted, redirect to callback, tokens received, dashboard displayed. Test scenarios: successful login, token refresh on expiration, logout, unauthorized access redirects to login. Use Playwright to intercept OAuth redirect, mock OAuth provider responses, verify token storage in localStorage, assert dashboard elements rendered.

---

## Issues Detected

*   **Test Failure:** The test `should complete successful OAuth login flow with Google` is failing with a timeout at line 62 (`page.waitForLoadState('networkidle')`). The test times out after 30 seconds waiting for the network to become idle.
*   **Root Cause:** The `page.waitForLoadState('networkidle')` call is problematic because:
    1. Vite's dev server keeps WebSocket connections open for hot module reloading, which prevents the page from ever reaching true "networkidle" state
    2. The test passes the URL assertion (line 55), meaning the redirect to `/dashboard` succeeds
    3. Six other tests in the suite pass successfully, including tests that DON'T use `waitForLoadState('networkidle')`
    4. The waitForLoadState('networkidle') is unnecessary because the test already waits for specific elements to be visible (lines 78-84), which is a more reliable indicator that the page has loaded

---

## Best Approach to Fix

You MUST remove the `page.waitForLoadState('networkidle')` call on line 62 of `frontend/e2e/auth.spec.ts`. This line is causing the timeout and is unnecessary because:

1. The test already asserts the URL is correct (line 55: `await expect(page).toHaveURL('/dashboard', { timeout: 10000 });`)
2. The test already waits for the dashboard heading to appear (line 78: `await expect(page.locator('h1')).toHaveText('Dashboard', { timeout: 10000 });`)
3. The test already waits for user data to be visible (lines 81-82)
4. These element assertions implicitly wait for the page to load and render, making `networkidle` redundant

**Specific Change Required:**

In `frontend/e2e/auth.spec.ts`, **DELETE** lines 57-62 (the debug logging and network idle wait):

```typescript
// REMOVE THESE LINES (57-62):
    // Log page content for debugging
    const bodyText = await page.evaluate(() => document.body.innerText);
    console.log('[DEBUG] Dashboard page content:', bodyText.substring(0, 200));

    // Wait for network to be idle to ensure all API calls complete
    await page.waitForLoadState('networkidle');
```

The test should flow directly from the URL assertion (line 55) to the localStorage verification (lines 64-75).

**Alternative (if you want to keep some waiting):** Replace the `networkidle` wait with a simple wait for the dashboard heading:

```typescript
    // Wait for redirect to dashboard after successful authentication
    await expect(page).toHaveURL('/dashboard', { timeout: 10000 });

    // Wait for dashboard to render
    await expect(page.locator('h1')).toHaveText('Dashboard', { timeout: 10000 });

    // Verify tokens are stored in localStorage
    const authState = await page.evaluate(() => {
      // ... rest of the test
```

This way, you're waiting for a specific, deterministic condition (the heading text) rather than an unreliable network state.

---

## Additional Context

- All other 6 tests in the suite pass successfully
- No linting errors were found
- The mock setup is correct and working (other tests prove this)
- The issue is purely with the use of `networkidle` in this specific test
- The `networkidle` state is generally problematic with modern dev servers that maintain persistent connections
