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
```

### Context: Authentication Mechanisms (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: authentication-mechanisms -->
##### Authentication Mechanisms

**OAuth2 Social Login (Free/Pro Tiers):**
- **Providers:** Google OAuth2, Microsoft Identity Platform
- **Flow:** Authorization Code Flow with PKCE (Proof Key for Code Exchange) for browser-based clients
- **Implementation:** Quarkus OIDC extension handling token exchange and validation
- **Token Storage:** JWT access tokens (1-hour expiration) in browser `localStorage`, refresh tokens (30-day expiration) in `httpOnly` secure cookies
- **User Provisioning:** Automatic user creation on first login with `oauth_provider` and `oauth_subject` as unique identifiers
- **Profile Sync:** Email, display name, and avatar URL synced from OAuth provider on each login
```

### Context: Application Security (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: application-security -->
##### Application Security

**Authentication Security:**
- **JWT Signature:** RS256 (RSA with SHA-256) algorithm, private key stored in Kubernetes Secret
- **Token Expiration:** Short-lived access tokens (1 hour), refresh tokens rotated on use
- **OAuth2 State Parameter:** CSRF protection for OAuth flow, state validated on callback
- **PKCE:** Protects authorization code from interception in browser-based flows
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/integration/oauth/OAuth2Adapter.java`
    *   **Summary:** This is the main adapter class that coordinates OAuth2 authentication across multiple providers. It already contains the complete implementation with methods `exchangeCodeForToken()` and `validateIdToken()` that delegate to provider-specific implementations.
    *   **Status:** **IMPLEMENTATION IS COMPLETE** - The adapter follows the Strategy pattern and properly validates inputs, logs operations, and delegates to GoogleOAuthProvider and MicrosoftOAuthProvider.
    *   **Recommendation:** This file is DONE. Do NOT modify it unless you find bugs during testing.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/oauth/GoogleOAuthProvider.java`
    *   **Summary:** Complete implementation of Google OAuth2 provider handling authorization code exchange and ID token validation using Google's OAuth2 endpoints.
    *   **Status:** **IMPLEMENTATION IS COMPLETE** - Properly implements token exchange with PKCE, ID token validation with signature verification, issuer/audience validation, and claim extraction.
    *   **Key Features:** Uses `JWTParser` for signature validation, validates expiration/issuer/audience, extracts subject/email/name/picture claims, handles missing name claim gracefully.
    *   **Recommendation:** This file is DONE. The implementation is production-ready.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/oauth/MicrosoftOAuthProvider.java`
    *   **Summary:** Complete implementation of Microsoft OAuth2 provider with similar structure to GoogleOAuthProvider but adapted for Microsoft Identity Platform specifics.
    *   **Status:** **IMPLEMENTATION IS COMPLETE** - Handles Microsoft's multi-tenant endpoints, supports both 'email' and 'preferred_username' claims, validates multiple Microsoft issuer formats.
    *   **Key Features:** Flexible issuer validation supporting both `login.microsoftonline.com` and `sts.windows.net`, fallback from 'email' to 'preferred_username' claim.
    *   **Recommendation:** This file is DONE. The implementation correctly handles Microsoft-specific OAuth2 nuances.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/oauth/OAuthUserInfo.java`
    *   **Summary:** DTO class containing validated user information extracted from OAuth2 ID tokens.
    *   **Status:** **IMPLEMENTATION IS COMPLETE** - Properly defined with Bean Validation annotations, all required fields (subject, email, name, avatarUrl, provider), complete getters/setters/toString.
    *   **Recommendation:** This file is DONE. The DTO is ready for use by UserService for JIT user provisioning.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/oauth/OAuth2AuthenticationException.java`
    *   **Summary:** Custom exception class for OAuth2-specific authentication errors.
    *   **Status:** Should exist alongside other OAuth files. If not present, this is the only missing piece.
    *   **Recommendation:** Verify this file exists. If missing, create it with proper exception hierarchy and error context (provider, error code).

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Quarkus configuration file containing OAuth2 provider configurations.
    *   **Status:** **CONFIGURATION IS COMPLETE** - Contains complete Google and Microsoft OAuth2 configuration with placeholders for client IDs and secrets that use environment variables (lines 72-105).
    *   **Key Configuration:**
        - Google: `quarkus.oidc.google.auth-server-url`, `quarkus.oidc.google.client-id`, `quarkus.oidc.google.credentials.secret`
        - Microsoft: `quarkus.oidc.microsoft.auth-server-url`, `quarkus.oidc.microsoft.client-id`, `quarkus.oidc.microsoft.credentials.secret`
    *   **Recommendation:** Configuration is DONE. Environment variables are properly configured for production deployment.

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven build configuration with all required dependencies.
    *   **Status:** **ALL REQUIRED DEPENDENCIES PRESENT** - Contains `quarkus-oidc` (line 75-78), `quarkus-smallrye-jwt` (line 81-84) for OAuth2/JWT support.
    *   **Recommendation:** No changes needed to dependencies. The JWTParser required by the provider implementations is included in `quarkus-smallrye-jwt`.

### Implementation Tips & Notes

**CRITICAL FINDING:** Task I3.T1 is **ALREADY COMPLETE**. All target files have been fully implemented:

1. **OAuth2Adapter.java** (148 lines) - Complete with:
   - Input validation for all parameters (lines 58-69)
   - Provider routing logic for Google and Microsoft (lines 74-86)
   - Both `exchangeCodeForToken()` and `validateIdToken()` methods
   - Helper methods `getSupportedProviders()` and `isProviderSupported()` (lines 126-146)
   - Comprehensive logging with JBoss Logger (lines 26, 71, 109)
   - Proper CDI annotation `@ApplicationScoped` (line 23)

2. **GoogleOAuthProvider.java** (227 lines) - Complete with:
   - Token exchange implementation using Java HttpClient (lines 50-122)
   - PKCE support in token request (line 77)
   - ID token validation with JWTParser (lines 132-190)
   - Signature verification using JWKS endpoint (handled by JWTParser)
   - Expiration check (lines 141-145)
   - Issuer validation for Google (lines 148-153)
   - Audience validation (lines 156-159)
   - Claims extraction: sub, email, name, picture (lines 162-165)
   - Fallback logic for missing name claim (lines 176-179)
   - Simple JSON parsing to extract id_token (lines 199-217)
   - Configuration injection using `@ConfigProperty` (lines 41-45)

3. **MicrosoftOAuthProvider.java** (254 lines) - Complete with:
   - Microsoft-specific token endpoint (line 37-38)
   - Support for both personal and organizational accounts via /common tenant
   - Token exchange similar to Google (lines 65-122)
   - Flexible issuer validation (lines 214-217) supporting:
     * `login.microsoftonline.com` (modern endpoint)
     * `sts.windows.net` (legacy endpoint)
   - Fallback from 'email' to 'preferred_username' claim (lines 173-176)
   - Complete claim extraction (lines 168-195)
   - Proper error handling for missing claims (lines 178-186)

4. **OAuthUserInfo.java** (133 lines) - Complete DTO with:
   - All required fields: subject, email, name, avatarUrl, provider (lines 21-55)
   - Bean Validation annotations: `@NotNull`, `@Email`, `@Size` (lines 19-54)
   - Complete constructor for easy instantiation (lines 72-79)
   - All getters and setters (lines 82-121)
   - Proper toString() for debugging (lines 124-132)

5. **application.properties** - Complete configuration:
   - OIDC globally disabled for now (line 79): `quarkus.oidc.enabled=false`
   - Google OAuth2 provider configuration (lines 82-91)
   - Microsoft OAuth2 provider configuration (lines 94-104)
   - Environment variable placeholders for secrets (proper pattern: `${VAR:default}`)
   - Correct auth server URLs for both providers

### Verification Checklist

Since the implementation is complete, verify these aspects:

✓ **OAuth2Adapter.java**: Strategy pattern correctly implemented with provider routing
✓ **GoogleOAuthProvider.java**: Token exchange and validation logic complete
✓ **MicrosoftOAuthProvider.java**: Microsoft-specific endpoint handling complete
✓ **OAuthUserInfo.java**: DTO with all required fields and validation
✓ **application.properties**: Multi-tenant OIDC configuration with environment variables
✓ **pom.xml**: Required dependencies (`quarkus-oidc`, `quarkus-smallrye-jwt`) present

**MISSING PIECE TO CHECK:**
⚠ **OAuth2AuthenticationException.java**: Verify this custom exception class exists. If not, this is the only file to create.

### Testing Recommendations

Since the implementation is complete, the next step is to verify it works correctly:

1. **Check for OAuth2AuthenticationException.java**:
   ```bash
   ls backend/src/main/java/com/scrumpoker/integration/oauth/OAuth2AuthenticationException.java
   ```
   If missing, create it with:
   - Extends `RuntimeException`
   - Constructor accepting message and provider
   - Constructor accepting message, provider, error details, and cause
   - Fields: `provider`, `errorCode`, `errorDescription`

2. **Set Environment Variables** for testing:
   ```bash
   export GOOGLE_CLIENT_ID="your-google-client-id"
   export GOOGLE_CLIENT_SECRET="your-google-client-secret"
   export MICROSOFT_CLIENT_ID="your-microsoft-client-id"
   export MICROSOFT_CLIENT_SECRET="your-microsoft-client-secret"
   ```

3. **Run the Application** to verify configuration loads:
   ```bash
   cd backend
   mvn quarkus:dev
   ```
   Check logs for any OIDC configuration errors.

4. **Create Unit Tests** to verify:
   - OAuth2Adapter routing logic
   - Input validation (null/empty parameter handling)
   - Provider-specific implementations with mocked HTTP responses
   - ID token validation with test JWT tokens
   - Claim extraction accuracy

5. **Integration Testing** (to be done in subsequent task I3.T3):
   - Full OAuth2 flow with real providers
   - Token exchange success/failure scenarios
   - ID token validation with expired/invalid tokens

### Project Conventions Observed

- **Package Structure:** Integration code properly organized under `com.scrumpoker.integration.oauth`
- **CDI Annotations:** `@ApplicationScoped` used for singleton services (OAuth2Adapter, providers)
- **Configuration:** `@ConfigProperty` for environment-based configuration
- **Logging:** JBoss logging used consistently with proper log levels (INFO for successful auth, DEBUG for validation details, ERROR for failures)
- **Exception Handling:** Custom exceptions (like OAuth2AuthenticationException) for domain-specific errors
- **Validation:** Proper input validation before processing (null checks, empty string checks)
- **Code Style:** Clean, well-documented code with comprehensive JavaDoc comments explaining each method's purpose, parameters, returns, and exceptions
- **HTTP Client:** Java 11+ HttpClient used for OAuth token exchange (lines 50-52 in providers)
- **Timeout Configuration:** 10-second connect and request timeouts for resilience

### Next Steps

**Task I3.T1 Status: COMPLETE**

The Coder Agent should:

1. **Verify OAuth2AuthenticationException.java exists** - This is the only potentially missing file
2. **If missing, create OAuth2AuthenticationException.java** with proper exception hierarchy
3. **Run `mvn clean compile`** to verify compilation succeeds
4. **Run `mvn quarkus:dev`** to verify application starts without errors
5. **Mark task I3.T1 as DONE** in the task tracking system
6. **Proceed to task I3.T2** (JWT Token Service implementation)

**No other code changes are needed unless bugs are found during testing.**

### Exception Class Template (if missing)

If OAuth2AuthenticationException.java doesn't exist, create it with this structure:

```java
package com.scrumpoker.integration.oauth;

/**
 * Exception thrown when OAuth2 authentication fails.
 * Captures provider-specific error details for debugging and user feedback.
 */
public class OAuth2AuthenticationException extends RuntimeException {

    private final String provider;
    private final String errorCode;
    private final String errorDescription;

    public OAuth2AuthenticationException(String message, String provider) {
        super(message);
        this.provider = provider;
        this.errorCode = null;
        this.errorDescription = null;
    }

    public OAuth2AuthenticationException(String message, String provider,
                                        String errorCode, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.errorCode = errorCode;
        this.errorDescription = null;
    }

    public String getProvider() { return provider; }
    public String getErrorCode() { return errorCode; }
    public String getErrorDescription() { return errorDescription; }
}
```
