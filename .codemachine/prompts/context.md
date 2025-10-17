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

### Context: Authentication Mechanisms (from 05_Operational_Architecture.md)

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
```

### Context: Application Security - Authentication (from 05_Operational_Architecture.md)

```markdown
**Authentication Security:**
- **JWT Signature:** RS256 (RSA with SHA-256) algorithm, private key stored in Kubernetes Secret
- **Token Expiration:** Short-lived access tokens (1 hour), refresh tokens rotated on use
- **OAuth2 State Parameter:** CSRF protection for OAuth flow, state validated on callback
- **PKCE:** Protects authorization code from interception in browser-based flows
```

### Context: OpenAPI OAuth Callback Endpoint (from openapi.yaml)

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
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/integration/oauth/OAuth2Adapter.java`
    *   **Summary:** This is a **SKELETON** implementation that already exists. It provides the adapter pattern structure, method signatures for `exchangeCodeForToken()` and `validateIdToken()`, input validation, and provider routing logic. **However, the actual OAuth2 provider implementations (GoogleOAuthProvider, MicrosoftOAuthProvider) are NOT YET FULLY IMPLEMENTED for the authorization code flow with PKCE.**
    *   **Recommendation:** You MUST implement the actual token exchange logic in `GoogleOAuthProvider.java` and `MicrosoftOAuthProvider.java`. The OAuth2Adapter already handles routing, so you need to focus on implementing the `exchangeCodeForToken()` method in each provider class. The skeleton shows mocked test implementations exist, but real HTTP calls to Google and Microsoft token endpoints are needed.
    *   **Critical Detail:** The adapter already validates inputs (null checks, empty checks) and routes to the correct provider. Your implementation should focus on the HTTP token exchange, ID token validation, and claim extraction.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/oauth/OAuthUserInfo.java`
    *   **Summary:** This DTO is **ALREADY COMPLETE**. It contains all required fields (subject, email, name, avatarUrl, provider) with Bean Validation annotations.
    *   **Recommendation:** You MUST use this exact DTO as the return type from your provider implementations. Do NOT modify its structure.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Configuration file already has **PLACEHOLDER** sections for Google and Microsoft OAuth2. The OIDC extension is currently DISABLED (`quarkus.oidc.enabled=false`) to allow dev mode startup without keys.
    *   **Recommendation:** You SHOULD configure the OIDC properties for both providers. Note that the current setup has client ID/secret placeholders that use environment variables. You MUST document these environment variables and ensure they're configurable.
    *   **Critical Configuration:**
        - Google: `auth-server-url=https://accounts.google.com`, client ID/secret from env vars
        - Microsoft: `auth-server-url=https://login.microsoftonline.com/common/v2.0`, client ID/secret from env vars
        - The global `quarkus.oidc.enabled` is set to false - you may need to enable it or use programmatic configuration

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven configuration already includes the Quarkus OIDC extension (`quarkus-oidc`), JWT support (`quarkus-smallrye-jwt`), and reactive dependencies.
    *   **Recommendation:** You do NOT need to add additional dependencies. The required extensions are already present.

*   **File:** `backend/src/test/java/com/scrumpoker/integration/oauth/OAuth2AdapterTest.java`
    *   **Summary:** Comprehensive unit tests already exist using Mockito. Tests cover all routing logic, validation, and edge cases.
    *   **Recommendation:** You SHOULD run these tests to ensure your implementation doesn't break the existing contract. The tests mock the provider implementations, so they will pass even with skeleton providers, but you should verify your real implementation works correctly.

### Implementation Tips & Notes

*   **Tip:** The task requires implementing the **Authorization Code Flow with PKCE**. This means:
    1. The frontend generates a `code_verifier` (random string) and `code_challenge` (SHA256 hash of verifier)
    2. Frontend redirects to OAuth provider with `code_challenge`
    3. After user consent, provider redirects back with `authorization_code`
    4. Your backend receives `code` + `code_verifier` and exchanges them for tokens
    5. The `code_verifier` is sent to the provider to prove the same client that started the flow is completing it

*   **Tip:** For **Google OAuth2**:
    - Token endpoint: `https://oauth2.googleapis.com/token`
    - ID token validation: Use Google's JWKS endpoint `https://www.googleapis.com/oauth2/v3/certs`
    - Claims: `sub` (subject), `email`, `name`, `picture`

*   **Tip:** For **Microsoft OAuth2**:
    - Token endpoint: `https://login.microsoftonline.com/common/oauth2/v2.0/token`
    - ID token validation: Use Microsoft's JWKS endpoint `https://login.microsoftonline.com/common/discovery/v2.0/keys`
    - Claims: `sub` (subject), `email`, `name`, `picture`

*   **Warning:** The Quarkus OIDC extension can handle token exchange and validation automatically, BUT the current configuration has it DISABLED. You have two implementation approaches:
    1. **Approach A (Recommended):** Use Quarkus OIDC extension programmatically to handle token exchange and validation (this leverages the extension's built-in JWKS validation)
    2. **Approach B:** Implement manual HTTP calls to token endpoints using Quarkus REST Client and manually validate JWT signatures using a JWT library

    **I STRONGLY RECOMMEND Approach A** because it's more secure, handles edge cases, and is production-ready.

*   **Note:** The architecture blueprint specifies that access tokens expire in **1 hour**, but the OpenAPI spec says **15 minutes**. The OpenAPI spec (`expiresIn: 900` = 15 min) is likely correct for production security. Use **15-minute expiry** for access tokens.

*   **Note:** The task description says "ID token claims: sub, email, name, picture" - the `picture` claim from OAuth providers should map to the `avatarUrl` field in `OAuthUserInfo`.

*   **Critical:** The acceptance criteria state "OAuth2 flow completes successfully for Google (test with real OAuth code)". This means you MUST provide integration test instructions or a way to test with real Google/Microsoft credentials. Consider creating test accounts or documenting how to set up OAuth apps in Google Cloud Console and Azure Portal.

*   **Security Note:** The `client_secret` MUST be stored in environment variables, never hardcoded. The current `application.properties` already uses `${GOOGLE_CLIENT_SECRET:your-google-client-secret}` pattern - this is correct. Document these required environment variables.

### Project Conventions

*   **Logging:** Use JBoss Logging (`org.jboss.logging.Logger`) as shown in the existing `OAuth2Adapter.java`. Log at INFO level for successful operations, ERROR for failures.
*   **Exception Handling:** Use `OAuth2AuthenticationException` for authentication failures (it's already defined in the codebase).
*   **Validation:** Input validation is already done in `OAuth2Adapter.java` - your provider implementations should focus on OAuth-specific validation (token signature, expiration, issuer).
*   **Testing:** Write both unit tests (with mocked HTTP calls) and integration tests. The existing test structure uses Mockito for unit tests.

### Implementation Checklist

Based on my analysis, here's what you MUST implement:

1. ✅ **GoogleOAuthProvider.java** - Implement `exchangeCodeForToken()` method:
   - Make HTTP POST to Google token endpoint with authorization code + code_verifier
   - Extract ID token from response
   - Validate ID token (signature, expiration, issuer, audience)
   - Extract claims (sub, email, name, picture)
   - Return `OAuthUserInfo` object

2. ✅ **MicrosoftOAuthProvider.java** - Implement `exchangeCodeForToken()` method:
   - Same as Google but with Microsoft endpoints
   - Handle Microsoft-specific claim names if different

3. ✅ **Implement `validateAndExtractClaims()` method in both providers**:
   - Validate JWT signature using JWKS
   - Check expiration (`exp` claim)
   - Check issuer (`iss` claim)
   - Extract user profile claims

4. ✅ **Update application.properties**:
   - Document required environment variables (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, MICROSOFT_CLIENT_ID, MICROSOFT_CLIENT_SECRET)
   - Ensure OIDC configuration is correct for both providers

5. ✅ **Write integration tests**:
   - Test with mocked HTTP responses
   - Provide documentation for testing with real OAuth credentials

6. ✅ **Error handling**:
   - Handle network errors (provider unreachable)
   - Handle invalid tokens (expired, wrong signature, wrong issuer)
   - Return meaningful error messages

---

## Summary

**Current State:** The OAuth2 integration framework is in place with a working adapter pattern, but the actual OAuth2 provider implementations are skeleton/stubs. Configuration placeholders exist but need real values.

**Your Task:** Implement the core OAuth2 authorization code flow with PKCE for Google and Microsoft providers. Use the Quarkus OIDC extension where possible for security and robustness. Focus on token exchange, ID token validation, and user claim extraction.

**Key Success Criteria:**
- Real OAuth codes from Google/Microsoft can be exchanged for user profile data
- ID tokens are cryptographically validated
- User profile claims are correctly extracted and mapped to `OAuthUserInfo`
- Environment variables are used for sensitive configuration
- Integration tests demonstrate the flow works end-to-end
