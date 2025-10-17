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

### Context: key-interaction-flow-oauth-login (from 04_Behavior_and_Communication.md)

```markdown
#### Key Interaction Flow: OAuth2 Authentication (Google/Microsoft)

##### Description

This sequence demonstrates the OAuth2 authorization code flow for user authentication via Google or Microsoft identity providers, JWT token generation, and session establishment.

##### Diagram (PlantUML)

~~~plantuml
@startuml

title OAuth2 Authentication Flow - Google/Microsoft Login

actor "User" as User
participant "SPA\n(React App)" as SPA
participant "Quarkus API\n(/api/v1/auth)" as API
participant "OAuth2 Adapter" as OAuth
participant "User Service" as UserService
participant "PostgreSQL" as DB
participant "Google/Microsoft\nOAuth2 Provider" as Provider

User -> SPA : Clicks "Sign in with Google"
activate SPA

SPA -> SPA : Generate PKCE code_verifier & code_challenge,\nstore in sessionStorage
SPA -> Provider : Redirect to authorization URL:\nhttps://accounts.google.com/o/oauth2/v2/auth\n?client_id=...&redirect_uri=...&code_challenge=...
deactivate SPA

User -> Provider : Grants permission
Provider -> SPA : Redirect to callback:\nhttps://app.scrumpoker.com/auth/callback?code=AUTH_CODE
activate SPA

SPA -> API : POST /api/v1/auth/oauth/callback\n{"provider":"google", "code":"AUTH_CODE", "codeVerifier":"..."}
deactivate SPA

activate API
API -> OAuth : exchangeCodeForToken(provider, code, codeVerifier)
activate OAuth

OAuth -> Provider : POST /token\n{code, client_id, client_secret, code_verifier}
Provider --> OAuth : {"access_token":"...", "id_token":"..."}

OAuth -> OAuth : Validate id_token signature (JWT),\nextract claims: {sub, email, name, picture}
OAuth --> API : OAuthUserInfo{subject, email, name, avatarUrl}
deactivate OAuth

API -> UserService : findOrCreateUser(provider="google", subject="...", email="...", name="...")
activate UserService

UserService -> DB : SELECT * FROM user WHERE oauth_provider='google' AND oauth_subject='...'
alt User exists
  DB --> UserService : User{user_id, email, subscription_tier, ...}
else New user
  DB --> UserService : NULL
  UserService -> DB : INSERT INTO user (oauth_provider, oauth_subject, email, display_name, avatar_url, subscription_tier)\nVALUES ('google', '...', '...', '...', '...', 'FREE')
  DB --> UserService : User{user_id, ...}
  UserService -> UserService : Create default UserPreference record
  UserService -> DB : INSERT INTO user_preference (user_id, default_deck_type, theme) VALUES (...)
end

UserService --> API : User{user_id, email, displayName, subscriptionTier}
deactivate UserService

API -> API : Generate JWT access token:\n{sub: user_id, email, tier, exp: now+1h}
API -> API : Generate refresh token (UUID),\nstore in Redis with 30-day TTL

API --> SPA : 200 OK\n{"accessToken":"...", "refreshToken":"...", "user":{...}}
deactivate API

activate SPA
SPA -> SPA : Store tokens in localStorage,\nstore user in Zustand state
SPA -> User : Redirect to Dashboard
deactivate SPA

@enduml
~~~
```

### Context: end-to-end-testing (from 03_Verification_and_Glossary.md)

```markdown
#### End-to-End (E2E) Testing

**Scope:** Complete user journeys from browser through entire backend stack

**Framework:** Playwright (browser automation)

**Coverage Target:** Top 10 critical user flows

**Approach:**
- Simulate real user interactions (clicks, form submissions, navigation)
- Test against running application (frontend + backend + database)
- Mock external services where necessary (OAuth providers, Stripe)
- Visual regression testing for UI components (optional, future enhancement)
- Run in CI pipeline on staging environment before production deployment

**Examples:**
- `auth.spec.ts`: OAuth login flow → callback → token storage → dashboard redirect
- `voting.spec.ts`: Create room → join → cast vote → reveal → see results
- `subscription.spec.ts`: Upgrade to Pro → Stripe checkout → webhook → tier updated

**Acceptance Criteria:**
- All E2E tests pass (`npm run test:e2e`)
- Tests run headless in CI (no UI required)
- Screenshots captured on failure for debugging
- Test execution time <10 minutes for full suite
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `frontend/e2e/auth.spec.ts`
    *   **Summary:** This file ALREADY EXISTS and contains a comprehensive E2E test suite for the OAuth authentication flow with 7 test scenarios.
    *   **Current State:** The test file is COMPLETE and implements ALL required test scenarios:
        1. ✅ Successful OAuth login flow with Google
        2. ✅ OAuth error callback handling
        3. ✅ Logout functionality (clear localStorage and redirect)
        4. ✅ Redirect to login when accessing dashboard without authentication
        5. ✅ Automatic token refresh on expiration
        6. ✅ Redirect to login when refresh token is invalid
        7. ✅ Handle missing PKCE session on callback
    *   **Recommendation:** The task appears to be ALREADY COMPLETED. The file contains all required test scenarios with proper mocking, assertions, and error handling.

*   **File:** `frontend/e2e/fixtures/mockOAuthResponse.ts`
    *   **Summary:** This fixture file provides comprehensive mock data and helper functions for authentication E2E tests.
    *   **Current State:** COMPLETE with:
        - Mock user data (`mockUser`)
        - Mock token responses (`mockTokenResponse`, `mockRefreshedTokenResponse`)
        - Mock PKCE session data (`mockPKCESession`)
        - Mock room data for dashboard testing (`mockRooms`, `mockRoomListResponse`)
        - Helper functions: `setupAuthenticatedState()`, `setupPKCESession()`, `clearAllStorage()`, `setupCommonRouteMocks()`
    *   **Recommendation:** This file is fully functional and provides all necessary mocking infrastructure. It properly intercepts API calls and provides realistic test data.

*   **File:** `frontend/playwright.config.ts`
    *   **Summary:** Playwright configuration file with proper setup for E2E testing.
    *   **Current State:** Correctly configured with:
        - Test directory pointing to `./e2e`
        - Base URL set to `http://localhost:5173` (Vite dev server)
        - Web server configuration to auto-start dev server
        - Headless browser support for CI
        - Screenshots and videos on failure
        - HTML and list reporters
    *   **Recommendation:** Configuration is production-ready and follows best practices.

*   **File:** `frontend/src/pages/LoginPage.tsx`
    *   **Summary:** Login page component implementing OAuth2 PKCE flow with Google and Microsoft providers.
    *   **Recommendation:** This component is the starting point for E2E tests. Tests use this page to initiate OAuth flow.

*   **File:** `frontend/src/pages/OAuthCallbackPage.tsx`
    *   **Summary:** OAuth callback handler that exchanges authorization code for tokens.
    *   **Recommendation:** Tests verify this page correctly handles success/error scenarios and redirects appropriately.

*   **File:** `frontend/src/pages/DashboardPage.tsx`
    *   **Summary:** Protected dashboard page that requires authentication.
    *   **Recommendation:** Tests verify successful authentication results in dashboard access with correct user data display.

*   **File:** `frontend/src/stores/authStore.ts`
    *   **Summary:** Zustand store managing authentication state with localStorage persistence.
    *   **Recommendation:** Tests verify tokens are correctly stored in localStorage via this store.

### Implementation Tips & Notes

*   **CRITICAL FINDING:** The task I3.T8 appears to be **ALREADY COMPLETE**. The file `frontend/e2e/auth.spec.ts` exists and contains all 7 required test scenarios with comprehensive mocking and assertions.

*   **Test Coverage Analysis:**
    - ✅ Successful OAuth login: Test exists at lines 35-88
    - ✅ OAuth error handling: Test exists at lines 90-100
    - ✅ Logout flow: Test exists at lines 102-144
    - ✅ Unauthorized access redirect: Test exists at lines 146-157
    - ✅ Token refresh on expiration: Test exists at lines 159-262
    - ✅ Redirect when refresh fails: Test exists at lines 264-309
    - ✅ Missing PKCE session handling: Test exists at lines 311-318

*   **Mock Strategy:** The tests use Playwright's `page.route()` to intercept all `/api/**` requests and return mock responses. This is the correct approach for isolated E2E testing without requiring a running backend.

*   **PKCE Flow Simulation:** Tests properly simulate the PKCE flow by:
    1. Setting up PKCE session data in sessionStorage via `setupPKCESession()`
    2. Skipping the actual OAuth provider redirect (cannot be tested)
    3. Directly navigating to callback with mock auth code
    4. Verifying tokens are received and stored

*   **Execution Status:** To verify the task is complete, you should:
    1. Run `npm run test:e2e` to confirm all tests pass
    2. Check if tests run in headless mode for CI compatibility
    3. Verify test execution completes within reasonable time (<10 minutes)

*   **Potential Action Required:** If the task is marked as incomplete (`"done": false`) but the code exists, you may need to:
    1. **VERIFY** tests actually pass by running them
    2. **UPDATE** the task status to `"done": true` if tests pass
    3. **FIX** any failing tests if found
    4. **DOCUMENT** test execution results

*   **CI Integration Note:** The `playwright.config.ts` has proper CI configuration:
    - `forbidOnly: !!process.env.CI` prevents accidental `.only` tests in CI
    - `retries: process.env.CI ? 2 : 0` enables retries in CI for flaky test resilience
    - `workers: process.env.CI ? 1 : undefined` runs tests serially in CI for stability

### Verification Checklist

Before marking this task complete, verify:

1. ✅ Test file exists at `frontend/e2e/auth.spec.ts`
2. ✅ Fixture file exists at `frontend/e2e/fixtures/mockOAuthResponse.ts`
3. ⚠️  **PENDING:** Run `npm run test:e2e` to confirm tests pass
4. ⚠️  **PENDING:** Verify tests run in headless mode (`npm run test:e2e -- --headed` should also work)
5. ⚠️  **PENDING:** Check test execution time is reasonable
6. ⚠️  **PENDING:** Confirm no test failures or flaky tests
7. ✅ Mock OAuth providers properly (no real external calls)
8. ✅ localStorage token storage verified in tests
9. ✅ Dashboard redirect and content assertions present
10. ✅ All acceptance criteria scenarios covered

### Recommended Next Steps

1. **RUN THE TESTS:** Execute `npm run test:e2e` to verify all tests pass
2. **REVIEW OUTPUT:** Check for any failures, warnings, or flaky behavior
3. **FIX IF NEEDED:** Address any test failures found
4. **UPDATE TASK STATUS:** Mark task as complete if all tests pass
5. **DOCUMENT:** Add test execution summary to task completion notes

---

**SUMMARY:** Task I3.T8 implementation is COMPLETE. The E2E test suite for authentication flow exists with comprehensive coverage of all required scenarios. The primary action needed is to VERIFY tests pass by executing them and update the task status accordingly.
