# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T3",
  "iteration_id": "I3",
  "iteration_goal": "Implement OAuth2 authentication (Google, Microsoft), JWT token generation/validation, user registration/login flows, and frontend authentication UI to enable secured access to the application.",
  "description": "Create `AuthController` with endpoints per OpenAPI spec: `POST /api/v1/auth/oauth/callback` (exchange OAuth code for JWT tokens), `POST /api/v1/auth/refresh` (refresh access token), `POST /api/v1/auth/logout` (revoke refresh token). Inject `OAuth2Adapter`, `UserService`, `JwtTokenService`. OAuth callback flow: validate code, exchange for user info, find or create user in database, generate JWT tokens, return TokenPair. Refresh flow: validate refresh token, generate new tokens, rotate refresh token. Logout flow: delete refresh token from Redis.",
  "agent_type_hint": "BackendAgent",
  "inputs": "*   OAuth2 sequence diagram from architecture blueprint\n        *   OpenAPI specification for auth endpoints\n        *   OAuth2Adapter and JwtTokenService from I3.T1, I3.T2",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   AuthController with 3 endpoints: /oauth/callback, /refresh, /logout\n        *   OAuth callback handler: code exchange → user provisioning → token generation\n        *   User provisioning logic (find by oauth_provider + oauth_subject, create if new user)\n        *   Refresh token rotation implementation\n        *   Logout implementation (Redis DELETE refresh token key)",
  "acceptance_criteria": "*   POST /oauth/callback with valid code returns 200 with access + refresh tokens\n        *   New user created in database on first OAuth login\n        *   Existing user found and tokens issued on subsequent login\n        *   POST /refresh with valid refresh token returns new token pair\n        *   POST /logout deletes refresh token from Redis (subsequent refresh fails)\n        *   Invalid codes/tokens return 401 Unauthorized",
  "dependencies": [
    "I3.T1",
    "I3.T2",
    "I2.T4"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Task 3.3 – Implement Authentication REST Controller (from .codemachine/artifacts/plan/02_Iteration_I3.md)

```markdown
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
```

### Context: Key Interaction Flow – OAuth2 Authentication (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

```markdown
#### Key Interaction Flow: OAuth2 Authentication (Google/Microsoft)

This sequence demonstrates the OAuth2 authorization code flow for user authentication via Google or Microsoft identity providers, JWT token generation, and session establishment.

~~~plantuml
@startuml

actor "User" as User
participant "SPA\n(React App)" as SPA
participant "Quarkus API\n(/api/v1/auth)" as API
participant "OAuth2 Adapter" as OAuth
participant "User Service" as UserService
participant "PostgreSQL" as DB
participant "Google/Microsoft\nOAuth2 Provider" as Provider

User -> SPA : Clicks "Sign in with Google"
SPA -> SPA : Generate PKCE code_verifier & code_challenge,\nstore in sessionStorage
SPA -> Provider : Redirect to authorization URL (Google/Microsoft)
Provider -> SPA : Redirect to callback with ?code=AUTH_CODE
SPA -> API : POST /api/v1/auth/oauth/callback {provider, code, codeVerifier}
API -> OAuth : exchangeCodeForToken(provider, code, codeVerifier)
OAuth -> Provider : POST /token {code, client_id, client_secret, code_verifier}
Provider --> OAuth : {"access_token":"...", "id_token":"..."}
OAuth -> OAuth : Validate id_token signature, extract claims {sub, email, name, picture}
OAuth --> API : OAuthUserInfo{subject, email, name, avatarUrl}
API -> UserService : findOrCreateUser(provider, subject, email, name)
UserService -> DB : Lookup user; insert new record + default preferences if missing
UserService --> API : Persisted User entity (id, email, subscriptionTier)
API -> API : Generate JWT access token (sub, email, tier, exp)
API -> API : Generate refresh token (UUID) and store in Redis (30-day TTL)
API --> SPA : 200 OK {accessToken, refreshToken, user}
SPA -> SPA : Store tokens + user state, redirect to dashboard

@enduml
~~~
```

### Context: Authentication Mechanisms (from .codemachine/artifacts/architecture/05_Operational_Architecture.md)

```markdown
##### Authentication Mechanisms

**OAuth2 Social Login (Free/Pro Tiers):**
- Providers: Google OAuth2, Microsoft Identity Platform
- Flow: Authorization Code Flow with PKCE for browser clients
- Implementation: Quarkus OIDC extension handles token exchange/validation
- Token Storage: JWT access tokens (1-hour expiration) in browser localStorage, refresh tokens (30-day expiration) in httpOnly cookies
- User Provisioning: Automatic user creation on first login with `oauth_provider` + `oauth_subject`
- Profile Sync: Email, display name, avatar URL synced from provider on each login

**Enterprise SSO (Enterprise Tier):**
- Protocols: OIDC and SAML2 with organization-specific config in `Organization.sso_config`
- Domain Enforcement: Email domain matching ties logins to org workspaces
- JIT Provisioning: Accounts created on first SSO login with membership assigned

**Anonymous Play:**
- Anonymous participants tracked via session UUID, limited capabilities, and 24-hour data retention
```

### Context: Authorization Strategy (from .codemachine/artifacts/architecture/05_Operational_Architecture.md)

```markdown
##### Authorization Strategy

**Role-Based Access Control (RBAC):**
- Roles: `ANONYMOUS`, `USER`, `PRO_USER`, `ORG_ADMIN`, `ORG_MEMBER`
- Enforcement: Quarkus `@RolesAllowed` annotations rely on `roles` array inside JWT claims
- Dynamic Role Mapping: Subscription tiers map to RBAC roles at token generation time

**Resource-Level Permissions:**
- Room access governed by privacy mode (public/invite-only/org-restricted)
- Report access depth tied to subscription tier (summary vs. detailed analytics)

**Enforcement Points:**
1. Ingress + REST controllers validate JWT signatures and roles
2. WebSocket handshake validates tokens before upgrading
3. Service layer applies feature gating (subscription + org rules)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** Quarkus JAX-RS resource exposing `/api/v1/auth` endpoints for OAuth callback, token refresh, logout, and the SSO callback. Each handler validates input, orchestrates adapters/services, and builds `TokenResponse` DTOs (wrapping access/refresh tokens plus mapped `UserDTO`). Helper methods like `createBadRequestResponse`, `createUnauthorizedResponse`, and `createErrorResponse` centralize error payloads, while `buildTokenResponse` injects `expiresIn` via `JwtTokenService`.
    *   **Recommendation:** Implement any new behaviour inside these existing methods—e.g., extend validation, logging, or mapping—but keep the reactive `Uni<Response>` contract, reuse helper response builders, and ensure sensitive errors (OAuth failures) surface standardized codes so Playwright tests and consumers remain stable.
*   **File:** `backend/src/main/java/com/scrumpoker/integration/oauth/OAuth2Adapter.java`
    *   **Summary:** Application-scoped strategy adapter delegating to provider-specific classes (`GoogleOAuthProvider`, `MicrosoftOAuthProvider`). `exchangeCodeForToken` enforces non-null parameters, logs provider operations, invokes the provider implementation, and returns an `OAuthUserInfo` (subject, email, name, avatar). `validateIdToken` exposes shared validation logic for cached tokens.
    *   **Recommendation:** During OAuth callback handling always call `exchangeCodeForToken` exactly once per request and rely on the returned DTO—do not reimplement provider logic. Handle provider names case-insensitively and propagate `OAuth2AuthenticationException` to the provided exception mapper to keep audit visibility consistent.
*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Summary:** Centralized JWT lifecycle manager. Generates RS256 access tokens with issuer from `mp.jwt.verify.issuer`, includes `email`, `roles`, `tier` claims, and writes refresh tokens to Redis with prefix `refresh_token:` plus TTL (`mp.jwt.refresh.token.expiration`). Exposes helpers for validation (`validateAccessToken`), refresh rotation (`refreshTokens`), lookup (`getUserIdFromRefreshToken`), invalidation, and retrieving expiration seconds for responses.
    *   **Recommendation:** Always call `generateTokens`, `refreshTokens`, and `invalidateRefreshToken` rather than manipulating Redis directly. When returning HTTP responses, use `getAccessTokenExpirationSeconds()` so clients know TTL. Avoid logging raw tokens—follow existing metadata-style logs for compliance.
*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Summary:** Reactive domain service for user CRUD plus preference management. Method `findOrCreateUser` looks up by `(oauthProvider, oauthSubject)` and either updates stale profile fields or calls `createUser`, which persists the entity (defaulting tier FREE) and creates default user preferences within transaction boundaries (`@WithTransaction`).
    *   **Recommendation:** During OAuth callback keep business logic here by invoking `findOrCreateUser`. If you need user details later (e.g., refresh flow), call `getUserById` which respects soft deletes and throws `UserNotFoundException` for invalid IDs.

### Implementation Tips & Notes
*   **Tip:** DTOs used by the controller (`OAuthCallbackRequest`, `RefreshTokenRequest`, `TokenResponse`) already enforce bean validation; pair these with explicit null/blank checks (as seen in existing methods) to return descriptive `ErrorResponse` payloads.
*   **Tip:** All controller methods are annotated with `@PermitAll` because they rely on refresh tokens or OAuth codes; authorization happens through token issuance rather than JAX-RS security—keep that model to avoid circular dependencies with `JwtAuthenticationFilter`.
*   **Tip:** For refresh flow, chain `jwtTokenService.getUserIdFromRefreshToken()` → `userService.getUserById()` → `jwtTokenService.refreshTokens()`. Handle null futures with `.onItem().ifNull().failWith(...)` to ensure invalid tokens return 401 as specified.
*   **Tip:** Logging already uses `LOG.infof/LOG.warnf` across the controller—maintain similar detail (provider, userId) but never log access/refresh tokens themselves.
*   **Tip:** The `OrganizationService`/`AuditLogService` wiring in this controller is leveraged by the SSO endpoint—if you add new dependencies, annotate them with `@Inject` at the top to maintain CDI conventions and to keep constructor ordering consistent for tests like `SsoAuthenticationIntegrationTest`.
