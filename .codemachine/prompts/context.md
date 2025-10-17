# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T8",
  "iteration_id": "I3",
  "iteration_goal": "Implement OAuth2 authentication (Google, Microsoft), JWT token generation/validation, user registration/login flows, and frontend authentication UI to enable secured access to the application.",
  "description": "Create Playwright end-to-end tests for complete authentication flow: user visits /login, clicks \"Sign in with Google\", OAuth redirect to Google (mock in test), consent granted, redirect to callback, tokens received, dashboard displayed. Test scenarios: successful login, token refresh on expiration, logout, unauthorized access redirects to login. Use Playwright to intercept OAuth redirect, mock OAuth provider responses, verify token storage in localStorage, assert dashboard elements rendered.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Authentication flow from architecture blueprint, Playwright testing patterns for OAuth mocking, Frontend authentication components",
  "input_files": [
    "frontend/src/pages/LoginPage.tsx",
    "frontend/src/pages/OAuthCallbackPage.tsx",
    "frontend/src/pages/DashboardPage.tsx"
  ],
  "target_files": [
    "frontend/e2e/auth.spec.ts",
    "frontend/e2e/fixtures/mockOAuthResponse.ts"
  ],
  "deliverables": "Playwright test: successful OAuth login flow end-to-end, Mock OAuth provider responses (intercept network requests), Assertions: tokens in localStorage, user redirected to /dashboard, profile displayed, Test: logout clears tokens and redirects to /login, Test: accessing /dashboard without auth redirects to /login, Test: expired token triggers refresh (mock 401 response)",
  "acceptance_criteria": "`npm run test:e2e` executes Playwright tests successfully, OAuth login test completes without real OAuth provider (mocked), Dashboard displays after successful authentication, Logout test clears tokens and redirects correctly, Unauthorized access test verifies PrivateRoute behavior, Tests run in CI pipeline (headless mode)",
  "dependencies": [
    "I3.T5",
    "I3.T7"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: OAuth2 Authentication Flow (from 04_Behavior_and_Communication.md)

The complete OAuth2 authorization code flow with PKCE for Google/Microsoft authentication. Key steps:
1. User clicks "Sign in with Google" on LoginPage
2. PKCE code_verifier and code_challenge generated
3. User redirected to OAuth provider (Google/Microsoft)
4. User grants permission
5. Provider redirects back to callback URL with authorization code
6. Frontend calls `/api/v1/auth/oauth/callback` with code and codeVerifier
7. Backend exchanges code for tokens, performs JIT user provisioning
8. Backend returns JWT access token and refresh token
9. Frontend stores tokens in localStorage and redirects to dashboard

### Context: End-to-End Testing Strategy (from 03_Verification_and_Glossary.md)

**Framework:** Playwright (browser automation)

**Approach:**
- Simulate real user interactions (clicks, form submissions, navigation)
- Mock external services where necessary (OAuth providers, Stripe)
- Tests run headless in CI pipeline
- Screenshots captured on failure for debugging

**Acceptance Criteria:**
- All E2E tests pass (`npm run test:e2e`)
- Tests run headless in CI (no UI required)
- Screenshots captured on failure
- Test execution time <10 minutes for full suite

### Context: REST API Endpoints (from 04_Behavior_and_Communication.md)

**Authentication & User Management:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth2 code for JWT tokens
- `POST /api/v1/auth/refresh` - Refresh expired access token
- `POST /api/v1/auth/logout` - Revoke refresh token
- `GET /api/v1/users/{userId}` - Retrieve user profile

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `frontend/src/pages/LoginPage.tsx`
    *   **Summary:** This file implements the OAuth login page with "Sign in with Google" and "Sign in with Microsoft" buttons. It handles PKCE code generation, stores session data in sessionStorage, and redirects users to the OAuth provider authorization URL.
    *   **Recommendation:** Your E2E test MUST mock the OAuth provider redirect (Google/Microsoft authorization endpoints) since tests cannot interact with real OAuth providers. You'll need to intercept the `window.location.href` assignment or use Playwright's route interception to catch redirects to `accounts.google.com` and `login.microsoftonline.com`.
    *   **Critical Detail:** The PKCE session data is stored in `sessionStorage` with key `oauth_pkce_session`. Your test fixtures will need to simulate this storage for the callback page to work correctly.

*   **File:** `frontend/src/pages/OAuthCallbackPage.tsx`
    *   **Summary:** This page handles the OAuth callback after user grants consent. It extracts the authorization code from URL query parameters, retrieves PKCE session data from sessionStorage, calls the backend `/api/v1/auth/oauth/callback` endpoint, stores tokens in localStorage via the auth store, and redirects to dashboard.
    *   **Recommendation:** You MUST mock the backend API endpoint `/api/v1/auth/oauth/callback` in your tests. Use Playwright's `page.route()` to intercept this POST request and return a mock TokenResponse. The mock response structure must match the `TokenResponse` type from `frontend/src/types/auth.ts`.
    *   **Critical Detail:** The callback page expects three query parameters: `code` (authorization code), and potentially `error` and `error_description` for error scenarios. Your test should navigate to `/auth/callback?code=MOCK_AUTH_CODE` after setting up PKCE session data.

*   **File:** `frontend/src/pages/DashboardPage.tsx`
    *   **Summary:** This is the protected dashboard page that displays user profile and room list. It uses the `useAuth` hook to access authentication state and makes API calls via React Query hooks (`useUser`, `useRooms`).
    *   **Recommendation:** Your test assertions should verify that the dashboard renders successfully after login. You should check for specific UI elements like the user's display name, email, and the "Your Rooms" section. You'll need to mock the `/api/v1/users/{userId}` and `/api/v1/rooms` API endpoints to provide test data.
    *   **Implementation Tip:** The dashboard shows loading skeletons while fetching data. Your test should wait for these loading states to complete before asserting on the actual content. Use Playwright's `waitFor()` or locate elements with `has-text` selectors.

*   **File:** `frontend/src/stores/authStore.ts`
    *   **Summary:** Zustand store managing authentication state. It persists tokens and user data to localStorage using the key `auth_state`. The store provides `setAuth()` to update state after login and `clearAuth()` to remove tokens on logout.
    *   **Recommendation:** Your tests MUST verify that tokens are correctly stored in localStorage after successful login. Use `page.evaluate(() => localStorage.getItem('auth_state'))` to read the stored value and assert its structure. For logout tests, verify that this key is removed from localStorage.
    *   **Critical Detail:** The stored value is a JSON string containing `{user, accessToken, refreshToken, isAuthenticated}`. Your test fixtures should use this exact structure when setting up authenticated states.

*   **File:** `frontend/src/utils/pkce.ts`
    *   **Summary:** Contains PKCE utility functions for generating code verifier/challenge and managing session storage. The `storePKCESession()` function saves to `sessionStorage` with key `oauth_pkce_session`.
    *   **Recommendation:** Since you're mocking the OAuth flow, you don't need to test the actual PKCE cryptographic functions. However, you MUST ensure your test properly sets up the PKCE session data in sessionStorage before navigating to the callback page. This simulates what would happen after the LoginPage redirects to OAuth provider.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** Backend REST controller handling OAuth callback, token refresh, and logout. The `/oauth/callback` endpoint validates the code and codeVerifier, calls the OAuth2Adapter to exchange the code for user info, performs JIT user provisioning, and generates JWT tokens.
    *   **Recommendation:** You do NOT need to modify this file. However, you should understand the expected request/response format when mocking the API. The endpoint expects `OAuthCallbackRequest` with fields: `code`, `provider`, `redirectUri`, `codeVerifier`. It returns `TokenResponse` with `accessToken`, `refreshToken`, `expiresIn`, and `user` (UserDTO).

*   **File:** `frontend/src/types/auth.ts`
    *   **Summary:** TypeScript type definitions for authentication-related DTOs. Defines `TokenResponse`, `OAuthCallbackRequest`, `UserDTO`, and `ErrorResponse`.
    *   **Recommendation:** Your mock responses in Playwright test fixtures MUST conform to these types to ensure type safety and prevent runtime errors. Reference these types when creating mock data.

### Implementation Tips & Notes

*   **Tip: Playwright Setup Required:** The `frontend/package.json` currently shows `"test": "echo \"No tests implemented yet\""`, which means Playwright is not yet installed. You MUST first install Playwright as a dev dependency with `npm install -D @playwright/test`. Then create a `playwright.config.ts` file in the frontend directory with basic configuration (test directory, browser settings, base URL).

*   **Tip: Mock Strategy for OAuth Redirect:** The LoginPage redirects to external OAuth providers via `window.location.href`. In Playwright, you can intercept this using `page.route()` to catch requests to `accounts.google.com` or `login.microsoftonline.com`. Alternatively, you can use `page.waitForNavigation()` followed by programmatic navigation back to the callback URL with a mock authorization code. The second approach is simpler and recommended.

*   **Tip: Setting Up Test Fixtures:** Create a `frontend/e2e/fixtures/mockOAuthResponse.ts` file containing reusable mock data:
    - A mock `TokenResponse` with valid structure
    - A mock `UserDTO` with test user data
    - Helper functions to set up sessionStorage with PKCE session data

    This keeps your test code clean and DRY.

*   **Tip: Headless Mode Configuration:** Your Playwright config should enable headless mode by default for CI compatibility. However, during development, you may want to run with `headless: false` to see the browser interactions. Consider using environment variables to toggle this.

*   **Tip: Handling Async Storage Operations:** When verifying localStorage contents, wrap your assertions in `page.waitForFunction()` or use Playwright's auto-wait mechanism to ensure the storage has been updated before reading. The authStore saves to localStorage asynchronously.

*   **Tip: PrivateRoute Testing:** The task mentions testing unauthorized access redirects. Look for the `PrivateRoute` component referenced in the architecture. Your test should navigate to `/dashboard` without setting up authentication state and verify that the user is redirected to `/login`. You may need to clear localStorage first to simulate an unauthenticated state.

*   **Note: No Backend Required for E2E Tests:** Since you're mocking all API responses using Playwright's route interception, you don't need a running backend for these tests. This makes the tests faster and more reliable for CI environments. However, ensure your mocks accurately reflect the actual API contract defined in the OpenAPI spec.

*   **Note: Test Organization:** Follow the pattern from the testing strategy document. Your test file should have clearly named test scenarios (describe blocks): "successful OAuth login", "logout clears tokens", "unauthorized access redirects", etc. Each test should be independent and not rely on state from previous tests.

*   **Warning: Token Expiration Testing:** The acceptance criteria mentions testing "expired token triggers refresh". This is complex for an E2E test because the frontend API client (mentioned in I3.T6) handles token refresh automatically on 401 responses. You'll need to mock a 401 response from a protected endpoint, then verify that the client calls `/api/v1/auth/refresh`, and finally succeeds with the refreshed token. This requires careful orchestration of multiple mock responses.

*   **Critical: CI Pipeline Integration:** The acceptance criteria requires tests to run in CI with `npm run test:e2e`. You MUST add this script to `frontend/package.json` and configure it to run Playwright in headless mode. Ensure the script exits with a non-zero code on test failure so CI can detect failures.

*   **Critical: Screenshot on Failure:** Playwright automatically captures screenshots on test failure if configured. Enable this in your `playwright.config.ts` with `screenshot: 'only-on-failure'` to help with debugging failed CI runs.

---

## Implementation Checklist

Before you begin coding, ensure you understand:

1. ✅ The complete OAuth flow from LoginPage → OAuth Provider → Callback → Dashboard
2. ✅ How PKCE session data flows through sessionStorage
3. ✅ The structure of TokenResponse and how it's stored in localStorage
4. ✅ The difference between mocking OAuth providers (external redirect) vs. mocking backend API (network intercept)
5. ✅ The acceptance criteria requiring all 6 test scenarios to pass

Your implementation should:

1. Install Playwright and create configuration file
2. Create mock fixture file with reusable test data
3. Implement successful login test (main happy path)
4. Implement logout test
5. Implement unauthorized access redirect test
6. Implement token refresh test (advanced scenario)
7. Add `test:e2e` npm script
8. Verify tests run in headless mode

Good luck! Remember to follow the defensive coding guidelines and ensure your tests are deterministic and fast.
