# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T5",
  "iteration_id": "I3",
  "iteration_goal": "Implement OAuth2 authentication (Google, Microsoft), JWT token generation/validation, user registration/login flows, and frontend authentication UI to enable secured access to the application.",
  "description": "Implement React components for authentication flow: `LoginPage` with \"Sign in with Google\" and \"Sign in with Microsoft\" buttons (redirect to OAuth providers with PKCE), `OAuthCallbackPage` to handle OAuth redirect (extract code, call `/api/v1/auth/oauth/callback`, store tokens in localStorage, redirect to dashboard). Create `authStore` (Zustand) to manage authentication state (user, tokens, isAuthenticated). Implement `useAuth` hook for accessing auth state. Create `PrivateRoute` component requiring authentication. Generate and store PKCE code_verifier/code_challenge in sessionStorage.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "*   OAuth2 flow from architecture blueprint\n        *   OpenAPI spec for auth endpoints\n        *   React + TypeScript + Zustand patterns",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   LoginPage with OAuth provider buttons styled with Tailwind\n        *   PKCE code_verifier generation (crypto.randomBytes equivalent in browser)\n        *   OAuth redirect URL construction with code_challenge\n        *   OAuthCallbackPage: code extraction → API call → token storage\n        *   authStore with state: user, accessToken, refreshToken, isAuthenticated\n        *   useAuth hook for components to check authentication status\n        *   PrivateRoute redirects unauthenticated users to /login",
  "acceptance_criteria": "*   Clicking \"Sign in with Google\" redirects to Google OAuth consent screen\n        *   After consent, callback page receives code parameter\n        *   Callback page successfully exchanges code for tokens (visible in Network tab)\n        *   Tokens stored in localStorage\n        *   authStore updates with user data\n        *   Navigating to /dashboard (PrivateRoute) works when authenticated\n        *   Unauthenticated users redirected to /login",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Task 3.5 – Create Frontend Authentication Components (from .codemachine/artifacts/plan/02_Iteration_I3.md)

```markdown
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
```

### Context: Authentication Mechanisms (from .codemachine/artifacts/architecture/05_Operational_Architecture.md)

```markdown
##### Authentication Mechanisms

**OAuth2 Social Login (Free/Pro Tiers):**
- **Providers:** Google OAuth2, Microsoft Identity Platform
- **Flow:** Authorization Code Flow with PKCE (Proof Key for Code Exchange) for browser-based clients
- **Implementation:** Quarkus OIDC extension handling token exchange and validation
- **Token Storage:** JWT access tokens (1-hour expiration) in browser `localStorage`, refresh tokens (30-day expiration) in `httpOnly` secure cookies
- **User Provisioning:** Automatic user creation on first login with `oauth_provider` and `oauth_subject` as unique identifiers
- **Profile Sync:** Email, display name, and avatar URL synced from OAuth provider on each login

**Enterprise SSO (Enterprise Tier):**
- **Protocols:** OIDC (OpenID Connect) and SAML2 support via Quarkus Security extensions
- **Configuration:** Per-organization SSO settings stored in `Organization.sso_config` JSONB field (IdP endpoint, certificate, attribute mapping)
- **Domain Enforcement:** Email domain verification ensures users with `@company.com` email automatically join organization workspace
- **Just-In-Time (JIT) Provisioning:** User accounts created on first SSO login with organization membership pre-assigned
- **Session Management:** SSO sessions synchronized with IdP via backchannel logout or session validation

**Anonymous Play:**
- **Identifier:** Client-generated UUID stored in browser `sessionStorage` for session continuity
- **Room Association:** Anonymous participants linked to room via `RoomParticipant.anonymous_id`
- **Feature Restrictions:** No session history access, no saved preferences, no administrative capabilities
- **Data Lifecycle:** Anonymous session data purged 24 hours after room inactivity
```

### Context: Key Interaction Flow – OAuth2 Authentication (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

```markdown
<!-- anchor: key-interaction-flow-oauth-login -->
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

---
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `frontend/src/pages/LoginPage.tsx`
    *   **Summary:** Presents the Tailwind-styled login screen with Google and Microsoft buttons. The click handler generates PKCE verifier/challenge via the PKCE util, stores `{codeVerifier, redirectUri, provider}` in sessionStorage, assembles the provider-specific authorization URL (client IDs pulled from `VITE_*` env vars), and redirects the browser to the IdP.
    *   **Recommendation:** Reuse `handleOAuthLogin` when extending UI (e.g., extra copy or telemetry). Keep PKCE session storage untouched so the callback page can recover the verifier, and ensure the redirect URI (`${window.location.origin}/auth/callback`) matches the route defined in React Router and OAuth client settings.
*   **File:** `frontend/src/pages/OAuthCallbackPage.tsx`
    *   **Summary:** Handles the `/auth/callback` route. It parses `code`/`error` params, loads + clears the PKCE session, posts the payload to `/api/v1/auth/oauth/callback`, stores the returned `TokenResponse` via `useAuth().setAuth`, and shows loading/error states before redirecting to `/dashboard`.
    *   **Recommendation:** When integrating with API helpers, maintain the existing fetch structure (JSON body with `provider`, `code`, `redirectUri`, `codeVerifier`). Any error handling changes should continue to surface `ErrorResponse.message` text and redirect back to `/login` after a small delay.
*   **File:** `frontend/src/stores/authStore.ts`
    *   **Summary:** Zustand store that initializes from `localStorage`, persists `{user, accessToken, refreshToken}` under `auth_state`, exposes `setAuth`, `clearAuth`, and `loadAuthFromStorage`, and keeps an `isAuthenticated` boolean in sync.
    *   **Recommendation:** Use `setAuth` for every successful token response so persistence stays consistent. If you add new token fields, update both the stored payload and the derived boolean logic, and prefer using the provided `clearAuth` when logging users out (e.g., on refresh failure) instead of duplicating localStorage access elsewhere.
*   **File:** `frontend/src/utils/pkce.ts`
    *   **Summary:** Implements RFC 7636 helpers: creates a cryptographically strong verifier, computes the SHA-256 challenge, and manages PKCE session data via `storePKCESession` / `retrieveAndClearPKCESession` keyed under `oauth_pkce_session` in sessionStorage.
    *   **Recommendation:** Always call `retrieveAndClearPKCESession` exactly once during callback handling so the verifier isn't reused accidentally. If you support additional providers or flows, extend the `PKCESession.provider` union here and the consuming components simultaneously.

### Implementation Tips & Notes
*   **Tip:** Types centralize under `frontend/src/types/auth.ts` (e.g., `OAuthProvider`, `OAuthCallbackRequest`, `TokenResponse`). Import from there instead of redefining DTOs so the frontend stays aligned with the OpenAPI schema.
*   **Tip:** `useAuth` (frontend/src/hooks/useAuth.ts) is the single source for `isAuthenticated` and token setters; `frontend/src/components/auth/PrivateRoute.tsx` already redirects unauthenticated visitors to `/login`, so plug it into your route definitions rather than guarding each page manually.
*   **Tip:** Keep Tailwind classes consistent with the existing design system components (e.g., shared `Button` in `@/components/common/Button`). This avoids drift and automatically picks up future theming tweaks.
*   **Note:** Backend expects the callback payload to include the original redirect URI. If you change routing (e.g., custom subpath), update both the stored `redirectUri` in `LoginPage` and the `OAuthCallbackPage` payload to stay in sync with the server’s whitelist.
*   **Note:** Error surfaces currently rely on simple alert/inline text plus a delayed redirect. Preserve at least a minimal feedback loop so users understand why login failed before you navigate away.
