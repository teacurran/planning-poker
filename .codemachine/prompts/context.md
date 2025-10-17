# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T1",
  "iteration_id": "I3",
  "iteration_goal": "Implement OAuth2 authentication (Google, Microsoft), JWT token generation/validation, user registration/login flows, and frontend authentication UI to enable secured access to the application.",
  "description": "Create `OAuth2Adapter` service integrating with Google and Microsoft OAuth2 providers using Quarkus OIDC extension. Implement authorization code flow with PKCE: validate authorization code, exchange for access token, fetch user profile (ID token claims: sub, email, name, picture), return `OAuthUserInfo` DTO. Configure OAuth2 client IDs/secrets in `application.properties` (use environment variables for prod). Handle token validation (signature verification, expiration checks). Implement provider-specific logic (Google uses `accounts.google.com`, Microsoft uses `login.microsoftonline.com`).",
  "agent_type_hint": "BackendAgent",
  "inputs": "OAuth2 authentication flow from architecture blueprint, Quarkus OIDC extension documentation, Google and Microsoft OAuth2 endpoint URLs",
  "input_files": [
    ".codemachine/artifacts/architecture/04_Behavior_and_Communication.md"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/integration/oauth/OAuth2Adapter.java",
    "backend/src/main/java/com/scrumpoker/integration/oauth/OAuthUserInfo.java",
    "backend/src/main/java/com/scrumpoker/integration/oauth/GoogleOAuthProvider.java",
    "backend/src/main/java/com/scrumpoker/integration/oauth/MicrosoftOAuthProvider.java",
    "backend/src/main/resources/application.properties"
  ],
  "deliverables": "OAuth2Adapter with methods: `exchangeCodeForToken(provider, code, codeVerifier)`, `validateIdToken(idToken)`, Provider-specific implementations for Google and Microsoft, OAuthUserInfo DTO with fields: subject, email, name, avatarUrl, provider, Configuration properties for client IDs, secrets, redirect URIs, ID token signature validation using JWKS endpoints",
  "acceptance_criteria": "OAuth2 flow completes successfully for Google (test with real OAuth code), OAuth2 flow completes for Microsoft (test with real OAuth code), ID token validation rejects expired/invalid tokens, OAuthUserInfo correctly populated from ID token claims, Environment variables used for sensitive config (client secrets)",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: OAuth2 Authentication Flow (from 04_Behavior_and_Communication.md)

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

### Context: OAuth2 Social Login Requirements (from 05_Operational_Architecture.md)

```markdown
##### Authentication Mechanisms

**OAuth2 Social Login (Free/Pro Tiers):**
- **Providers:** Google OAuth2, Microsoft Identity Platform
- **Flow:** Authorization Code Flow with PKCE (Proof Key for Code Exchange) for browser-based clients
- **Implementation:** Quarkus OIDC extension handling token exchange and validation
- **Token Storage:** JWT access tokens (1-hour expiration) in browser `localStorage`, refresh tokens (30-day expiration) in `httpOnly` secure cookies
- **User Provisioning:** Automatic user creation on first login with `oauth_provider` and `oauth_subject` as unique identifiers
- **Profile Sync:** Email, display name, and avatar URL synced from OAuth provider on each login
```

### Context: Transport Security (from 05_Operational_Architecture.md)

```markdown
##### Transport Security

- **HTTPS/TLS 1.3:** All REST API and WebSocket traffic encrypted in transit
- **Certificate Management:** AWS Certificate Manager (ACM) or Let's Encrypt with automated renewal
- **HSTS (HTTP Strict Transport Security):** `Strict-Transport-Security: max-age=31536000; includeSubDomains` header enforced
- **WebSocket Secure (WSS):** TLS-encrypted WebSocket connections (`wss://` protocol)
```

### Context: Authentication Security (from 05_Operational_Architecture.md)

```markdown
**Authentication Security:**
- **JWT Signature:** RS256 (RSA with SHA-256) algorithm, private key stored in Kubernetes Secret
- **Token Expiration:** Short-lived access tokens (1 hour), refresh tokens rotated on use
- **OAuth2 State Parameter:** CSRF protection for OAuth flow, state validated on callback
- **PKCE:** Protects authorization code from interception in browser-based flows
```

### Context: OpenAPI /auth/oauth/callback Endpoint (from api/openapi.yaml)

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
    responses:
      '200':
        description: Successfully authenticated
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/TokenResponse'
      '400':
        $ref: '#/components/responses/BadRequest'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '500':
        $ref: '#/components/responses/InternalServerError'
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven build configuration with Quarkus 3.15.1 dependencies. Already includes `quarkus-oidc` (line 75-78) and `quarkus-smallrye-jwt` (line 81-84) extensions required for OAuth2 and JWT.
    *   **Recommendation:** You MUST NOT add new dependencies for OAuth2 - the OIDC extension is already present. Use the existing Quarkus OIDC extension for OAuth2 integration.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Application configuration file with OIDC settings already stubbed out (lines 72-85). Currently disabled with `quarkus.oidc.enabled=false`.
    *   **Recommendation:** You SHOULD extend the existing OIDC configuration section. Note the current placeholders use environment variables (e.g., `${OIDC_CLIENT_ID}`). For this task, configure MULTIPLE OAuth2 providers (Google + Microsoft) using Quarkus OIDC's multi-tenancy feature with named tenants (e.g., `quarkus.oidc.google.*`, `quarkus.oidc.microsoft.*`).
    *   **Warning:** DO NOT enable OIDC globally yet (`quarkus.oidc.enabled=false` should remain). This task focuses on building the adapter layer. The REST controller (I3.T3) will handle enabling it.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** JPA entity for users with OAuth authentication fields: `oauthProvider` (line 39-40), `oauthSubject` (line 43-45), `email` (line 34-35), `displayName` (line 49-50), `avatarUrl` (line 53-54).
    *   **Recommendation:** Your `OAuthUserInfo` DTO MUST map directly to these User entity fields. The sequence diagram shows these exact field names are expected by `UserService.findOrCreateUser()`.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Summary:** Domain service with `findOrCreateUser()` method (lines 243-271) that is the primary JIT provisioning entry point. This method expects parameters: `oauthProvider`, `oauthSubject`, `email`, `displayName`, `avatarUrl`.
    *   **Recommendation:** Your `OAuth2Adapter` MUST return data compatible with this signature. The service handles finding existing users by `oauth_provider + oauth_subject` and creates new users with default preferences automatically.
    *   **Tip:** The UserService already implements all the business logic for user provisioning. Your adapter only needs to extract claims from ID tokens and return an `OAuthUserInfo` DTO.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
    *   **Summary:** Panache repository with method `findByOAuthProviderAndSubject(String provider, String subject)` (lines 35-37).
    *   **Recommendation:** This is the exact method UserService uses to find existing OAuth users. Ensure your provider names ("google", "microsoft") are consistent with what will be stored in the database.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/` (directory)
    *   **Summary:** Empty directory created in I1 for integration adapters. This is where your OAuth2 adapter belongs.
    *   **Recommendation:** Create the `oauth` subdirectory under `integration` for all OAuth-related classes.

### Implementation Tips & Notes

*   **Tip:** Quarkus OIDC extension provides built-in OAuth2 token exchange and validation. You SHOULD use `io.quarkus.oidc.client.OidcClient` for the token exchange flow instead of implementing raw HTTP calls. This handles PKCE, token validation, and JWKS retrieval automatically.

*   **Note:** The architecture requires PKCE (Proof Key for Code Exchange) support. Quarkus OIDC handles this automatically when you configure the authorization code flow. The `code_verifier` parameter must be passed from the frontend through your adapter to the OIDC client.

*   **Warning:** ID token validation is CRITICAL for security. The architecture specifies RS256 signature validation using JWKS endpoints. Quarkus OIDC will handle this automatically if you configure the `auth-server-url` correctly (Google: `https://accounts.google.com`, Microsoft: `https://login.microsoftonline.com/common`).

*   **Tip:** For provider-specific logic, the task description mentions creating separate `GoogleOAuthProvider` and `MicrosoftOAuthProvider` classes. Consider using a Strategy pattern where each provider implements a common interface, and `OAuth2Adapter` delegates to the appropriate provider based on the `provider` parameter.

*   **Note:** The OpenAPI spec shows the endpoint expects `provider` as an enum: `[google, microsoft]`. Use lowercase string identifiers consistently throughout your implementation.

*   **Tip:** The `OAuthUserInfo` DTO should extract these ID token claims:
    *   `sub` → `subject` field
    *   `email` → `email` field
    *   `name` → `name` field (Google and Microsoft both provide this)
    *   `picture` → `avatarUrl` field (Google uses `picture`, Microsoft uses `picture` in v2.0 endpoint)
    *   Store the `provider` value ("google" or "microsoft") for tracking

*   **Warning:** The task acceptance criteria requires testing with "real OAuth code". You MUST configure actual OAuth2 client credentials for Google and Microsoft in your local development environment. Use environment variables to avoid committing secrets to git.

*   **Note:** The architecture specifies 1-hour access token expiration and 30-day refresh tokens. However, this task (I3.T1) focuses ONLY on the OAuth2 adapter for initial authentication. JWT token generation is handled in task I3.T2, and refresh logic in I3.T3.

*   **Tip:** For error handling, follow the existing pattern in the codebase. Create custom exceptions like `OAuth2AuthenticationException` that can be mapped to appropriate HTTP status codes in the controller layer.

*   **Note:** Configuration in `application.properties` should use environment variable placeholders for production secrets:
    ```properties
    # Google OAuth2
    quarkus.oidc.google.auth-server-url=https://accounts.google.com
    quarkus.oidc.google.client-id=${GOOGLE_CLIENT_ID:your-dev-client-id}
    quarkus.oidc.google.credentials.secret=${GOOGLE_CLIENT_SECRET:your-dev-secret}

    # Microsoft OAuth2
    quarkus.oidc.microsoft.auth-server-url=https://login.microsoftonline.com/common
    quarkus.oidc.microsoft.client-id=${MICROSOFT_CLIENT_ID:your-dev-client-id}
    quarkus.oidc.microsoft.credentials.secret=${MICROSOFT_CLIENT_SECRET:your-dev-secret}
    ```

*   **Tip:** For local testing, create a `.env` file in the `backend/` directory (add to `.gitignore`) with your OAuth2 client credentials. Quarkus will load these automatically in dev mode.

### Project Structure Notes

*   The project follows hexagonal architecture with clear separation: `api` (controllers), `domain` (services), `integration` (adapters), `repository` (data access).
*   Integration adapters like OAuth2Adapter should be in `com.scrumpoker.integration.oauth` package.
*   The codebase uses reactive programming with `Uni<>` and `Multi<>` from SmallRye Mutiny. However, for this OAuth2 adapter task, you can use synchronous methods since token exchange is a blocking operation and will be called from a REST controller in I3.T3.
*   Error handling follows a pattern of custom exceptions (e.g., `UserNotFoundException`, `RoomNotFoundException`). Create `OAuth2AuthenticationException` for OAuth-specific errors.

### Testing Guidance

*   Unit tests should use Mockito to mock the OIDC client and test the adapter logic independently.
*   Integration tests will be added in I3.T8 for the complete authentication flow.
*   For this task, focus on ensuring the adapter correctly extracts and maps ID token claims to the `OAuthUserInfo` DTO.
