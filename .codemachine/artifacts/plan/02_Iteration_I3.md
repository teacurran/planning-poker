# Project Plan: Scrum Poker Platform - Iteration 3

**Version:** 1.0
**Date:** 2025-10-17

---

<!-- anchor: iteration-3 -->
### Iteration 3: Authentication & User Management

*   **Iteration ID:** `I3`

*   **Goal:** Implement OAuth2 authentication (Google, Microsoft), JWT token generation/validation, user registration/login flows, and frontend authentication UI to enable secured access to the application.

*   **Prerequisites:** I1 (database schema, User entity), I2 (UserService, OpenAPI spec)

*   **Tasks:**

<!-- anchor: task-i3-t1 -->
*   **Task 3.1: Implement OAuth2 Integration Adapter (Google & Microsoft)**
    *   **Task ID:** `I3.T1`
    *   **Description:** Create `OAuth2Adapter` service integrating with Google and Microsoft OAuth2 providers using Quarkus OIDC extension. Implement authorization code flow with PKCE: validate authorization code, exchange for access token, fetch user profile (ID token claims: sub, email, name, picture), return `OAuthUserInfo` DTO. Configure OAuth2 client IDs/secrets in `application.properties` (use environment variables for prod). Handle token validation (signature verification, expiration checks). Implement provider-specific logic (Google uses `accounts.google.com`, Microsoft uses `login.microsoftonline.com`).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   OAuth2 authentication flow from architecture blueprint
        *   Quarkus OIDC extension documentation
        *   Google and Microsoft OAuth2 endpoint URLs
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/04_Behavior_and_Communication.md` (OAuth2 sequence diagram)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/integration/oauth/OAuth2Adapter.java`
        *   `backend/src/main/java/com/scrumpoker/integration/oauth/OAuthUserInfo.java` (DTO)
        *   `backend/src/main/java/com/scrumpoker/integration/oauth/GoogleOAuthProvider.java`
        *   `backend/src/main/java/com/scrumpoker/integration/oauth/MicrosoftOAuthProvider.java`
        *   `backend/src/main/resources/application.properties` (OAuth config section)
    *   **Deliverables:**
        *   OAuth2Adapter with methods: `exchangeCodeForToken(provider, code, codeVerifier)`, `validateIdToken(idToken)`
        *   Provider-specific implementations for Google and Microsoft
        *   OAuthUserInfo DTO with fields: subject, email, name, avatarUrl, provider
        *   Configuration properties for client IDs, secrets, redirect URIs
        *   ID token signature validation using JWKS endpoints
    *   **Acceptance Criteria:**
        *   OAuth2 flow completes successfully for Google (test with real OAuth code)
        *   OAuth2 flow completes for Microsoft (test with real OAuth code)
        *   ID token validation rejects expired/invalid tokens
        *   OAuthUserInfo correctly populated from ID token claims
        *   Environment variables used for sensitive config (client secrets)
    *   **Dependencies:** []
    *   **Parallelizable:** Yes

<!-- anchor: task-i3-t2 -->
*   **Task 3.2: Implement JWT Token Service (Generation & Validation)**
    *   **Task ID:** `I3.T2`
    *   **Description:** Create `JwtTokenService` for JWT access token and refresh token management. Implement token generation: create access token with claims (sub: userId, email, roles, tier, exp: 1 hour), create refresh token (UUID stored in Redis with 30-day TTL). Implement token validation: verify signature (RSA key), check expiration, extract claims. Implement token refresh: validate refresh token from Redis, generate new access token, rotate refresh token. Use SmallRye JWT library. Store RSA private key in application config (production: Kubernetes Secret), public key for validation.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   JWT authentication requirements from architecture blueprint
        *   SmallRye JWT Quarkus extension patterns
        *   Token lifecycle (access 1 hour, refresh 30 days)
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/05_Operational_Architecture.md` (authentication section)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
        *   `backend/src/main/java/com/scrumpoker/security/TokenPair.java` (DTO: accessToken, refreshToken)
        *   `backend/src/main/java/com/scrumpoker/security/JwtClaims.java` (DTO for token claims)
        *   `backend/src/main/resources/privateKey.pem` (RSA private key, NOT committed to git)
        *   `backend/src/main/resources/publicKey.pem` (RSA public key)
    *   **Deliverables:**
        *   JwtTokenService with methods: `generateTokens(User)`, `validateAccessToken(String)`, `refreshTokens(String refreshToken)`
        *   RSA key pair generation script (openssl commands in README)
        *   Access token with claims: sub, email, roles, tier, exp, iat
        *   Refresh token storage in Redis with TTL
        *   Token rotation on refresh (invalidate old refresh token, issue new one)
    *   **Acceptance Criteria:**
        *   Generated access token validates successfully
        *   Token includes correct user claims (userId, email, subscription tier)
        *   Expired token validation throws JwtException
        *   Refresh token lookup succeeds from Redis
        *   Token rotation invalidates old refresh token
        *   Signature validation uses RSA public key correctly
    *   **Dependencies:** []
    *   **Parallelizable:** Yes (can work parallel with I3.T1)

<!-- anchor: task-i3-t3 -->
*   **Task 3.3: Implement Authentication REST Controller**
    *   **Task ID:** `I3.T3`
    *   **Description:** Create `AuthController` with endpoints per OpenAPI spec: `POST /api/v1/auth/oauth/callback` (exchange OAuth code for JWT tokens), `POST /api/v1/auth/refresh` (refresh access token), `POST /api/v1/auth/logout` (revoke refresh token). Inject `OAuth2Adapter`, `UserService`, `JwtTokenService`. OAuth callback flow: validate code, exchange for user info, find or create user in database, generate JWT tokens, return TokenPair. Refresh flow: validate refresh token, generate new tokens, rotate refresh token. Logout flow: delete refresh token from Redis.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   OAuth2 sequence diagram from architecture blueprint
        *   OpenAPI specification for auth endpoints
        *   OAuth2Adapter and JwtTokenService from I3.T1, I3.T2
    *   **Input Files:**
        *   `api/openapi.yaml` (auth endpoint specs)
        *   `backend/src/main/java/com/scrumpoker/integration/oauth/OAuth2Adapter.java`
        *   `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/OAuthCallbackRequest.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/TokenResponse.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/RefreshTokenRequest.java`
    *   **Deliverables:**
        *   AuthController with 3 endpoints: /oauth/callback, /refresh, /logout
        *   OAuth callback handler: code exchange → user provisioning → token generation
        *   User provisioning logic (find by oauth_provider + oauth_subject, create if new user)
        *   Refresh token rotation implementation
        *   Logout implementation (Redis DELETE refresh token key)
    *   **Acceptance Criteria:**
        *   POST /oauth/callback with valid code returns 200 with access + refresh tokens
        *   New user created in database on first OAuth login
        *   Existing user found and tokens issued on subsequent login
        *   POST /refresh with valid refresh token returns new token pair
        *   POST /logout deletes refresh token from Redis (subsequent refresh fails)
        *   Invalid codes/tokens return 401 Unauthorized
    *   **Dependencies:** [I3.T1, I3.T2, I2.T4]
    *   **Parallelizable:** No (depends on OAuth2Adapter, JwtTokenService, UserService)

<!-- anchor: task-i3-t4 -->
*   **Task 3.4: Implement JWT Authentication Filter**
    *   **Task ID:** `I3.T4`
    *   **Description:** Create JAX-RS request filter (`@Provider`) for JWT authentication. Intercept requests to protected endpoints, extract JWT from `Authorization: Bearer <token>` header, validate token using `JwtTokenService`, extract user claims, set security context (user ID, roles) for authorization checks. Skip authentication for public endpoints (/api/v1/auth/*, OPTIONS requests). Handle authentication failures with 401 Unauthorized response. Integrate with Quarkus Security for `@RolesAllowed` annotations.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   JWT validation logic from I3.T2
        *   JAX-RS filter patterns
        *   Quarkus Security integration
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/security/JwtAuthenticationFilter.java`
        *   `backend/src/main/java/com/scrumpoker/security/SecurityContextImpl.java`
    *   **Deliverables:**
        *   JwtAuthenticationFilter annotated with `@Provider` and `@Priority(AUTHENTICATION)`
        *   Bearer token extraction from Authorization header
        *   Token validation and claims extraction
        *   Security context population (userId, roles, email)
        *   Public endpoint exemption (auth endpoints, health checks)
        *   401 response for missing/invalid tokens
    *   **Acceptance Criteria:**
        *   Protected endpoints (e.g., GET /api/v1/users/{userId}) require valid JWT (401 if missing)
        *   Valid JWT allows request to proceed, populates security context
        *   Expired JWT returns 401 Unauthorized
        *   Public endpoints (/api/v1/auth/*) accessible without JWT
        *   `@RolesAllowed` annotations work correctly (use roles from JWT claims)
    *   **Dependencies:** [I3.T2]
    *   **Parallelizable:** No (depends on JwtTokenService)

<!-- anchor: task-i3-t5 -->
*   **Task 3.5: Create Frontend Authentication Components (Login, OAuth Callback)**
    *   **Task ID:** `I3.T5`
    *   **Description:** Implement React components for authentication flow: `LoginPage` with "Sign in with Google" and "Sign in with Microsoft" buttons (redirect to OAuth providers with PKCE), `OAuthCallbackPage` to handle OAuth redirect (extract code, call `/api/v1/auth/oauth/callback`, store tokens in localStorage, redirect to dashboard). Create `authStore` (Zustand) to manage authentication state (user, tokens, isAuthenticated). Implement `useAuth` hook for accessing auth state. Create `PrivateRoute` component requiring authentication. Generate and store PKCE code_verifier/code_challenge in sessionStorage.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   OAuth2 flow from architecture blueprint
        *   OpenAPI spec for auth endpoints
        *   React + TypeScript + Zustand patterns
    *   **Input Files:**
        *   `api/openapi.yaml` (auth endpoints)
        *   `.codemachine/artifacts/architecture/04_Behavior_and_Communication.md` (OAuth sequence diagram)
    *   **Target Files:**
        *   `frontend/src/pages/LoginPage.tsx`
        *   `frontend/src/pages/OAuthCallbackPage.tsx`
        *   `frontend/src/stores/authStore.ts`
        *   `frontend/src/hooks/useAuth.ts`
        *   `frontend/src/components/auth/PrivateRoute.tsx`
        *   `frontend/src/utils/pkce.ts` (PKCE generator utility)
    *   **Deliverables:**
        *   LoginPage with OAuth provider buttons styled with Tailwind
        *   PKCE code_verifier generation (crypto.randomBytes equivalent in browser)
        *   OAuth redirect URL construction with code_challenge
        *   OAuthCallbackPage: code extraction → API call → token storage
        *   authStore with state: user, accessToken, refreshToken, isAuthenticated
        *   useAuth hook for components to check authentication status
        *   PrivateRoute redirects unauthenticated users to /login
    *   **Acceptance Criteria:**
        *   Clicking "Sign in with Google" redirects to Google OAuth consent screen
        *   After consent, callback page receives code parameter
        *   Callback page successfully exchanges code for tokens (visible in Network tab)
        *   Tokens stored in localStorage
        *   authStore updates with user data
        *   Navigating to /dashboard (PrivateRoute) works when authenticated
        *   Unauthenticated users redirected to /login
    *   **Dependencies:** [I3.T3]
    *   **Parallelizable:** No (depends on AuthController API)

<!-- anchor: task-i3-t6 -->
*   **Task 3.6: Implement Frontend API Client with Authentication**
    *   **Task ID:** `I3.T6`
    *   **Description:** Create API client wrapper using React Query integrating authentication. Configure Axios instance with base URL, request interceptor to add `Authorization: Bearer <token>` header from authStore, response interceptor to handle 401 errors (refresh token or logout). Implement token refresh logic: on 401, call `/api/v1/auth/refresh`, update tokens in store, retry original request. Create React Query hooks for common API calls: `useUser(userId)`, `useRooms()`, `useRoomById(roomId)`. Handle loading and error states.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   OpenAPI spec for endpoint definitions
        *   React Query patterns
        *   Token refresh flow requirements
    *   **Input Files:**
        *   `api/openapi.yaml`
        *   `frontend/src/stores/authStore.ts`
    *   **Target Files:**
        *   `frontend/src/services/api.ts` (Axios instance with interceptors)
        *   `frontend/src/services/apiHooks.ts` (React Query hooks)
        *   `frontend/src/services/authApi.ts` (auth-specific API calls)
    *   **Deliverables:**
        *   Axios instance configured with baseURL, timeout
        *   Request interceptor adding Authorization header from authStore
        *   Response interceptor detecting 401, triggering token refresh
        *   Token refresh logic: call /refresh API, update authStore, retry request
        *   React Query hooks: useUser, useRooms, useRoomById
        *   Error handling: network errors, 500 server errors
    *   **Acceptance Criteria:**
        *   API requests include Authorization header when user authenticated
        *   Expired access token triggers refresh automatically
        *   After refresh, original request retries successfully
        *   If refresh fails (invalid refresh token), user logged out and redirected to login
        *   React Query hooks return loading/error/data states correctly
        *   Cache invalidation works (e.g., after room creation, useRooms refetches)
    *   **Dependencies:** [I3.T5]
    *   **Parallelizable:** No (depends on authStore)

<!-- anchor: task-i3-t7 -->
*   **Task 3.7: Create User Dashboard Page (Frontend)**
    *   **Task ID:** `I3.T7`
    *   **Description:** Implement `DashboardPage` component displaying user profile, list of owned rooms, recent session history, and quick actions (create new room, view preferences). Use `useUser` and `useRooms` hooks to fetch data. Display loading skeleton while fetching, error message on failure. Show user avatar, display name, email. List rooms in card grid with room title, privacy mode badge, last active timestamp, "Open Room" button. Add "Create New Room" button navigating to room creation form. Style with Tailwind CSS, responsive for mobile/tablet/desktop.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   Dashboard requirements from product spec
        *   API hooks from I3.T6
        *   Design system (Tailwind, Headless UI)
    *   **Input Files:**
        *   `frontend/src/services/apiHooks.ts`
        *   `frontend/src/stores/authStore.ts`
    *   **Target Files:**
        *   `frontend/src/pages/DashboardPage.tsx`
        *   `frontend/src/components/dashboard/UserProfileCard.tsx`
        *   `frontend/src/components/dashboard/RoomListCard.tsx`
        *   `frontend/src/components/dashboard/CreateRoomButton.tsx`
    *   **Deliverables:**
        *   DashboardPage with user profile section (avatar, name, email, tier badge)
        *   Room list grid (responsive, 1 col mobile, 2 col tablet, 3 col desktop)
        *   Room card component showing title, privacy mode, last active date
        *   Create room button with prominent styling
        *   Loading skeleton using Tailwind animate-pulse
        *   Error state UI (retry button, error message)
    *   **Acceptance Criteria:**
        *   Dashboard loads user data from API on mount
        *   User profile displays correct information (avatar, name, subscription tier)
        *   Room list shows user's owned rooms from API
        *   Clicking room card navigates to /room/{roomId}
        *   Create room button navigates to /rooms/new
        *   Loading state displayed while fetching data
        *   Error state shows message if API call fails
        *   Responsive layout works on mobile, tablet, desktop
    *   **Dependencies:** [I3.T6]
    *   **Parallelizable:** No (depends on API client hooks)

<!-- anchor: task-i3-t8 -->
*   **Task 3.8: Write End-to-End Tests for Authentication Flow**
    *   **Task ID:** `I3.T8`
    *   **Description:** Create Playwright end-to-end tests for complete authentication flow: user visits /login, clicks "Sign in with Google", OAuth redirect to Google (mock in test), consent granted, redirect to callback, tokens received, dashboard displayed. Test scenarios: successful login, token refresh on expiration, logout, unauthorized access redirects to login. Use Playwright to intercept OAuth redirect, mock OAuth provider responses, verify token storage in localStorage, assert dashboard elements rendered.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Authentication flow from architecture blueprint
        *   Playwright testing patterns for OAuth mocking
        *   Frontend authentication components
    *   **Input Files:**
        *   `frontend/src/pages/LoginPage.tsx`
        *   `frontend/src/pages/OAuthCallbackPage.tsx`
        *   `frontend/src/pages/DashboardPage.tsx`
    *   **Target Files:**
        *   `frontend/e2e/auth.spec.ts`
        *   `frontend/e2e/fixtures/mockOAuthResponse.ts`
    *   **Deliverables:**
        *   Playwright test: successful OAuth login flow end-to-end
        *   Mock OAuth provider responses (intercept network requests)
        *   Assertions: tokens in localStorage, user redirected to /dashboard, profile displayed
        *   Test: logout clears tokens and redirects to /login
        *   Test: accessing /dashboard without auth redirects to /login
        *   Test: expired token triggers refresh (mock 401 response)
    *   **Acceptance Criteria:**
        *   `npm run test:e2e` executes Playwright tests successfully
        *   OAuth login test completes without real OAuth provider (mocked)
        *   Dashboard displays after successful authentication
        *   Logout test clears tokens and redirects correctly
        *   Unauthorized access test verifies PrivateRoute behavior
        *   Tests run in CI pipeline (headless mode)
    *   **Dependencies:** [I3.T5, I3.T7]
    *   **Parallelizable:** No (depends on frontend auth implementation)

---

**Iteration 3 Summary:**

*   **Deliverables:**
    *   OAuth2 integration with Google and Microsoft
    *   JWT token service (generation, validation, refresh)
    *   Authentication REST API (/oauth/callback, /refresh, /logout)
    *   JWT authentication filter for protected endpoints
    *   Frontend authentication UI (LoginPage, OAuthCallback, Dashboard)
    *   API client with automatic token refresh
    *   End-to-end authentication tests (Playwright)

*   **Acceptance Criteria (Iteration-Level):**
    *   Users can log in with Google or Microsoft OAuth
    *   JWT tokens generated and validated correctly
    *   Protected API endpoints require authentication
    *   Frontend redirects unauthenticated users to login
    *   Token refresh works automatically on expiration
    *   Dashboard displays user profile and rooms
    *   E2E tests verify complete authentication flow

*   **Estimated Duration:** 2.5 weeks
