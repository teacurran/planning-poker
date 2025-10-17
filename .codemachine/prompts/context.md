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
  "inputs": "OAuth2 flow from architecture blueprint, OpenAPI spec for auth endpoints, React + TypeScript + Zustand patterns",
  "input_files": [
    "api/openapi.yaml",
    ".codemachine/artifacts/architecture/04_Behavior_and_Communication.md"
  ],
  "target_files": [
    "frontend/src/pages/LoginPage.tsx",
    "frontend/src/pages/OAuthCallbackPage.tsx",
    "frontend/src/stores/authStore.ts",
    "frontend/src/hooks/useAuth.ts",
    "frontend/src/components/auth/PrivateRoute.tsx",
    "frontend/src/utils/pkce.ts"
  ],
  "deliverables": "LoginPage with OAuth provider buttons styled with Tailwind, PKCE code_verifier generation (crypto.randomBytes equivalent in browser), OAuth redirect URL construction with code_challenge, OAuthCallbackPage: code extraction → API call → token storage, authStore with state: user, accessToken, refreshToken, isAuthenticated, useAuth hook for components to check authentication status, PrivateRoute redirects unauthenticated users to /login",
  "acceptance_criteria": "Clicking \"Sign in with Google\" redirects to Google OAuth consent screen, After consent, callback page receives code parameter, Callback page successfully exchanges code for tokens (visible in Network tab), Tokens stored in localStorage, authStore updates with user data, Navigating to /dashboard (PrivateRoute) works when authenticated, Unauthenticated users redirected to /login",
  "dependencies": [
    "I3.T3"
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

### Context: OAuth2 Authentication Endpoint (from openapi.yaml)

```yaml
/api/v1/auth/oauth/callback:
  post:
    tags:
      - Authentication
    summary: Exchange OAuth2 authorization code for JWT tokens
    description: |
      Exchanges OAuth2 authorization code from provider (Google/Microsoft) for access and refresh tokens.
      Returns JWT access token (15min expiry) and refresh token (30 days).
    operationId: oauthCallback
    security: []  # Public endpoint
    requestBody:
      required: true
      content:
        application/json:
          schema:
            type: object
            required:
              - code
              - provider
              - redirectUri
            properties:
              code:
                type: string
                description: Authorization code from OAuth2 provider
                example: "4/0AX4XfWh..."
              provider:
                type: string
                enum: [google, microsoft]
                description: OAuth2 provider
                example: google
              redirectUri:
                type: string
                format: uri
                description: Redirect URI used in authorization request (must match)
                example: "https://planningpoker.example.com/auth/callback"
          example:
            code: "4/0AX4XfWh..."
            provider: google
            redirectUri: "https://planningpoker.example.com/auth/callback"
    responses:
      '200':
        description: Successfully authenticated
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/TokenResponse'
            example:
              accessToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
              refreshToken: "v1.MR5tqKz..."
              expiresIn: 900
              tokenType: "Bearer"
              user:
                userId: "123e4567-e89b-12d3-a456-426614174000"
                email: "alice@example.com"
                displayName: "Alice Smith"
                avatarUrl: "https://example.com/avatar.jpg"
                subscriptionTier: "PRO"
                createdAt: "2025-01-01T10:00:00Z"
                updatedAt: "2025-01-10T15:30:00Z"
```

### Context: TokenResponse Schema (from openapi.yaml)

```yaml
TokenResponse:
  type: object
  required:
    - accessToken
    - refreshToken
    - expiresIn
    - tokenType
    - user
  properties:
    accessToken:
      type: string
      description: JWT access token (15min expiry)
      example: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    refreshToken:
      type: string
      description: Refresh token (30 days expiry, single-use)
      example: "v1.MR5tqKz..."
    expiresIn:
      type: integer
      description: Access token TTL in seconds
      example: 900
    tokenType:
      type: string
      description: Token type (always "Bearer")
      example: "Bearer"
    user:
      $ref: '#/components/schemas/UserDTO'
```

### Context: UserDTO Schema (from openapi.yaml)

```yaml
UserDTO:
  type: object
  required:
    - userId
    - email
    - displayName
    - subscriptionTier
    - createdAt
  properties:
    userId:
      type: string
      format: uuid
      description: User unique identifier
      example: "123e4567-e89b-12d3-a456-426614174000"
    email:
      type: string
      format: email
      maxLength: 255
      description: User email address (unique)
      example: "alice@example.com"
    oauthProvider:
      type: string
      enum: [google, microsoft]
      description: OAuth2 provider
      example: "google"
    displayName:
      type: string
      maxLength: 100
      description: User display name
      example: "Alice Smith"
    avatarUrl:
      type: string
      format: uri
      maxLength: 500
      nullable: true
      description: Profile avatar URL
      example: "https://example.com/avatar.jpg"
    subscriptionTier:
      $ref: '#/components/schemas/SubscriptionTier'
    createdAt:
      type: string
      format: date-time
      description: Account creation timestamp
      example: "2025-01-01T10:00:00Z"
    updatedAt:
      type: string
      format: date-time
      description: Last profile update timestamp
      example: "2025-01-10T15:30:00Z"
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** This file contains the fully implemented backend OAuth2 authentication endpoints. The `/api/v1/auth/oauth/callback` endpoint is ready to receive requests from the frontend.
    *   **Recommendation:** You MUST call the `POST /api/v1/auth/oauth/callback` endpoint with the exact request body structure shown in the OpenAPI spec: `{code, provider, redirectUri, codeVerifier}`. The provider must be either "google" or "microsoft". The endpoint returns a `TokenResponse` with `accessToken`, `refreshToken`, `expiresIn`, `tokenType`, and `user` object.
    *   **CRITICAL NOTE:** Looking at the AuthController implementation (lines 113-116), the `codeVerifier` field is validated and required. The OpenAPI spec example doesn't show it, but the backend code explicitly checks for it and returns a 400 error if missing. You MUST include this field in your API request.

*   **File:** `frontend/src/App.tsx`
    *   **Summary:** This is the main React application component using React Router v6. It already has routes defined for `/`, `/room/:roomId`, and `/dashboard`.
    *   **Recommendation:** You MUST add new routes for `/login` (LoginPage) and `/auth/callback` (OAuthCallbackPage). You SHOULD wrap the `/dashboard` route with the `PrivateRoute` component you'll create.

*   **File:** `frontend/package.json`
    *   **Summary:** The project already has all required dependencies installed: React 18, React Router v6.20, Zustand 4.4, React Query 5.12, and Tailwind CSS 3.3.
    *   **Recommendation:** You do NOT need to install any additional packages. All dependencies required for this task (Zustand for state management, React Router for navigation, Tailwind for styling) are already present.

*   **File:** `frontend/src/pages/HomePage.tsx`
    *   **Summary:** This is an example page component using Tailwind CSS classes and the `@/` path alias for imports. It demonstrates the project's styling conventions.
    *   **Recommendation:** You SHOULD follow the same Tailwind styling patterns used here: `dark:` variants for dark mode, responsive breakpoints like `md:`, and utility classes for spacing/colors. The `@/` path alias is already configured and working (see import on line 2).

*   **File:** `frontend/src/components/common/Button.tsx`
    *   **Summary:** A reusable Button component exists with variant support (primary, secondary).
    *   **Recommendation:** You SHOULD reuse this Button component for the "Sign in with Google" and "Sign in with Microsoft" buttons on the LoginPage. Import it with `import Button from '@/components/common/Button';`.

### Implementation Tips & Notes

*   **Tip:** The backend API is running at `http://localhost:8080` (as shown in openapi.yaml servers configuration). When making API calls from the frontend during development, you'll need to either:
    1. Configure a proxy in `vite.config.ts` to forward `/api` requests to `http://localhost:8080`, OR
    2. Use the full URL `http://localhost:8080/api/v1/auth/oauth/callback` in your API client

    I recommend option 1 (proxy) for cleaner code.

*   **Note:** PKCE (Proof Key for Code Exchange) is required for the OAuth2 flow. You need to generate a `code_verifier` (random string, 43-128 characters) and compute a `code_challenge` (base64url-encoded SHA-256 hash of the verifier). The browser's `crypto.subtle.digest()` API can be used for SHA-256 hashing.

*   **Note:** The OAuth2 redirect flow works as follows:
    1. User clicks "Sign in with Google" on LoginPage
    2. Frontend generates PKCE verifier/challenge, stores verifier in sessionStorage
    3. Frontend redirects to Google's authorization URL with `client_id`, `redirect_uri`, `code_challenge`, `response_type=code`, `scope=openid email profile`
    4. User grants permission on Google's consent screen
    5. Google redirects back to `http://localhost:5173/auth/callback?code=AUTH_CODE`
    6. OAuthCallbackPage extracts code from URL params, retrieves verifier from sessionStorage
    7. OAuthCallbackPage calls `POST /api/v1/auth/oauth/callback` with code, provider, redirectUri, codeVerifier
    8. Backend returns tokens and user data
    9. Frontend stores tokens in localStorage, updates authStore
    10. Frontend redirects to /dashboard

*   **Warning:** You will need to configure OAuth2 client IDs for Google and Microsoft in the backend's `application.properties`. For development, you can use test/mock values, but the OAuth redirect URLs must match exactly what's configured in the OAuth provider's console. The redirect URI should be `http://localhost:5173/auth/callback` for local development.

*   **Tip:** For Zustand store, create a simple state structure:
    ```typescript
    interface AuthState {
      user: UserDTO | null;
      accessToken: string | null;
      refreshToken: string | null;
      isAuthenticated: boolean;
      setAuth: (tokens: TokenResponse) => void;
      clearAuth: () => void;
    }
    ```
    Store this in `frontend/src/stores/authStore.ts` and use `create()` from Zustand. The store should persist tokens to localStorage in the `setAuth` action and load from localStorage on initialization.

*   **Tip:** For the PrivateRoute component, check `isAuthenticated` from the authStore. If false, use `<Navigate to="/login" />` from React Router v6 to redirect. Example pattern:
    ```typescript
    const PrivateRoute: React.FC<{children: React.ReactNode}> = ({children}) => {
      const isAuthenticated = useAuth(state => state.isAuthenticated);
      return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
    };
    ```

*   **Security Note:** Store tokens in `localStorage` as specified in the task, but be aware this has XSS vulnerability implications. In production, consider using httpOnly cookies or more secure storage mechanisms. For this task, localStorage is acceptable as it matches the architectural design.

*   **Tip:** When extracting the authorization code from the callback URL, use React Router's `useSearchParams()` hook:
    ```typescript
    const [searchParams] = useSearchParams();
    const code = searchParams.get('code');
    ```

*   **Tip:** For PKCE implementation, you can use the Web Crypto API which is available in all modern browsers:
    ```typescript
    // Generate verifier: random 128-character base64url string
    const array = new Uint8Array(96);
    crypto.getRandomValues(array);
    const verifier = base64UrlEncode(array);

    // Generate challenge: SHA-256 hash of verifier
    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const hash = await crypto.subtle.digest('SHA-256', data);
    const challenge = base64UrlEncode(new Uint8Array(hash));
    ```

*   **Note:** You'll need to configure OAuth client IDs. For Google OAuth2, the authorization URL is:
    ```
    https://accounts.google.com/o/oauth2/v2/auth?
      client_id=YOUR_CLIENT_ID
      &redirect_uri=http://localhost:5173/auth/callback
      &response_type=code
      &scope=openid%20email%20profile
      &code_challenge=CODE_CHALLENGE
      &code_challenge_method=S256
    ```
    The `client_id` must match what's configured in the backend `application.properties` file.

*   **Warning:** The backend AuthController expects the `redirectUri` in the callback request to EXACTLY match the one used in the initial authorization request. Store this in sessionStorage along with the code_verifier so you can include it in the token exchange call.

*   **Tip:** Consider error handling for the OAuth callback:
    - Check if `code` parameter is present in the URL
    - Handle cases where `error` parameter is present (user denied consent)
    - Validate that code_verifier exists in sessionStorage
    - Handle API call failures with appropriate error messages to the user

### Project Structure Observations

*   The frontend uses path aliases configured with `@/` prefix (see HomePage.tsx line 2)
*   All pages go in `frontend/src/pages/`
*   Reusable components go in `frontend/src/components/` (organized by domain like `auth/`, `common/`)
*   Stores go in `frontend/src/stores/`
*   Hooks go in `frontend/src/hooks/`
*   Utilities go in `frontend/src/utils/`
*   The project already has TypeScript configured with strict mode enabled

### OAuth Provider Configuration Reference

**Google OAuth2:**
- Authorization endpoint: `https://accounts.google.com/o/oauth2/v2/auth`
- Required scopes: `openid email profile`
- Response type: `code`
- PKCE method: `S256`

**Microsoft OAuth2:**
- Authorization endpoint: `https://login.microsoftonline.com/common/oauth2/v2.0/authorize`
- Required scopes: `openid email profile`
- Response type: `code`
- PKCE method: `S256`

Both providers return the authorization code as a `?code=` query parameter in the redirect URL.
