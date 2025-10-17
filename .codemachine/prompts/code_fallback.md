# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create `OAuth2Adapter` service integrating with Google and Microsoft OAuth2 providers using Quarkus OIDC extension. Implement authorization code flow with PKCE: validate authorization code, exchange for access token, fetch user profile (ID token claims: sub, email, name, picture), return `OAuthUserInfo` DTO. Configure OAuth2 client IDs/secrets in `application.properties` (use environment variables for prod). Handle token validation (signature verification, expiration checks). Implement provider-specific logic (Google uses `accounts.google.com`, Microsoft uses `login.microsoftonline.com`).

---

## Issues Detected

### **Critical Issues**

*   **Missing Tests:** There are NO unit tests for the OAuth2Adapter, GoogleOAuthProvider, or MicrosoftOAuthProvider classes. The acceptance criteria requires "OAuth2 flow completes successfully for Google (test with real OAuth code)" and "OAuth2 flow completes for Microsoft (test with real OAuth code)" and "ID token validation rejects expired/invalid tokens". You MUST create comprehensive unit tests.

*   **Incorrect Implementation Approach:** The code uses raw HTTP calls with Java's HttpClient instead of leveraging the Quarkus OIDC extension that is ALREADY in the dependencies. The task description specifically says "using Quarkus OIDC extension". You should use `io.quarkus.oidc.client.OidcClient` for token exchange, NOT manual HTTP requests.

*   **Missing JWTParser Configuration:** The GoogleOAuthProvider and MicrosoftOAuthProvider inject `JWTParser` but there is NO configuration for JWKS endpoints in `application.properties`. The JWTParser needs to know where to fetch public keys for signature validation. You must configure JWKS endpoints.

*   **Code Coverage Violation:** The `com.scrumpoker.integration.oauth` package has 0% code coverage, but the project requires 80% minimum. This fails the `mvn verify` build. You MUST add unit tests to achieve >80% coverage.

### **Checkstyle Violations (Must Fix)**

The OAuth code has 100+ checkstyle violations including:

*   **Missing package-info.java:** The `com.scrumpoker.integration.oauth` package needs a package-info.java file with package documentation
*   **Line length >80 characters:** Many lines exceed 80 characters (e.g., OAuth2Adapter.java lines 9, 10, 12, 16, etc.)
*   **Missing Javadoc comments:** Private fields need Javadoc (`provider`, `errorCode` in OAuth2AuthenticationException; `googleProvider`, `microsoftProvider` in OAuth2Adapter)
*   **Visibility modifier violations:** Injected fields `googleProvider` and `microsoftProvider` in OAuth2Adapter must be private (currently package-private)
*   **Final parameters:** All method parameters should be marked `final` (checkstyle violations on all constructors and methods)
*   **HiddenField violations:** Constructor parameters in OAuth2AuthenticationException hide fields - use different parameter names or use `this.` prefix

### **Design Issues**

*   **No Dependency on Quarkus OIDC:** The implementation reinvents OAuth2 token exchange logic instead of using Quarkus OIDC's built-in `OidcClient`. This is error-prone and harder to maintain.

*   **Fragile JSON Parsing:** The `extractIdToken()` method uses naive string manipulation to parse JSON. This will break if the JSON response format changes (e.g., whitespace, property order). Use Jackson ObjectMapper which is already available in Quarkus.

*   **No Integration with OIDC Configuration:** The code reads `quarkus.oidc.google.client-id` properties but doesn't leverage Quarkus OIDC's built-in multi-tenancy features for handling multiple providers.

---

## Best Approach to Fix

### **Step 1: Refactor to Use Quarkus OIDC Extension Properly**

**IMPORTANT:** Do NOT implement raw HTTP token exchange. Instead, use Quarkus OIDC's `OidcClient` API:

```java
@Inject
@Named("google")
OidcClient googleOidcClient;

@Inject
@Named("microsoft")
OidcClient microsoftOidcClient;
```

Configure named OIDC clients in `application.properties`:

```properties
# Google OIDC Client Configuration
quarkus.oidc-client.google.auth-server-url=https://accounts.google.com
quarkus.oidc-client.google.client-id=${GOOGLE_CLIENT_ID:your-google-client-id}
quarkus.oidc-client.google.credentials.secret=${GOOGLE_CLIENT_SECRET:your-google-client-secret}
quarkus.oidc-client.google.grant.type=authorization_code
quarkus.oidc-client.google.token-path=https://oauth2.googleapis.com/token

# Microsoft OIDC Client Configuration
quarkus.oidc-client.microsoft.auth-server-url=https://login.microsoftonline.com/common/v2.0
quarkus.oidc-client.microsoft.client-id=${MICROSOFT_CLIENT_ID:your-microsoft-client-id}
quarkus.oidc-client.microsoft.credentials.secret=${MICROSOFT_CLIENT_SECRET:your-microsoft-client-secret}
quarkus.oidc-client.microsoft.grant.type=authorization_code
quarkus.oidc-client.microsoft.token-path=https://login.microsoftonline.com/common/oauth2/v2.0/token
```

**Note:** Replace the existing `quarkus.oidc.google.*` and `quarkus.oidc.microsoft.*` properties with `quarkus.oidc-client.google.*` and `quarkus.oidc-client.microsoft.*` respectively.

Use the OidcClient to exchange authorization code for tokens:

```java
Tokens tokens = oidcClient.getTokens(
    Map.of(
        "code", authorizationCode,
        "redirect_uri", redirectUri,
        "code_verifier", codeVerifier,
        "grant_type", "authorization_code"
    )
).await().indefinitely();

String idToken = tokens.getIdToken();
```

Then parse and validate the ID token using Quarkus's JWT support.

### **Step 2: Fix All Checkstyle Violations**

1. Create `backend/src/main/java/com/scrumpoker/integration/oauth/package-info.java`:
```java
/**
 * OAuth2 integration adapters for third-party identity providers.
 * Provides OAuth2 authentication flows for Google and Microsoft.
 */
package com.scrumpoker.integration.oauth;
```

2. Fix line length violations by breaking long lines at 80 characters
3. Add Javadoc comments to all private fields
4. Make all injected fields `private` and add `@Inject` annotation
5. Mark all method parameters as `final`
6. Fix HiddenField violations by renaming constructor parameters or using `this.` prefix

Run `mvn checkstyle:check` repeatedly until ALL violations are fixed.

### **Step 3: Create Comprehensive Unit Tests**

Create `backend/src/test/java/com/scrumpoker/integration/oauth/OAuth2AdapterTest.java`:

**Requirements:**
- Test `exchangeCodeForToken()` for both Google and Microsoft providers
- Mock the `OidcClient` to return mock tokens
- Test `validateIdToken()` with valid, expired, and invalid tokens
- Test error cases (invalid provider, null parameters, etc.)
- Achieve >80% code coverage for OAuth2Adapter

Create `backend/src/test/java/com/scrumpoker/integration/oauth/GoogleOAuthProviderTest.java`:

**Requirements:**
- Test token exchange with mocked OidcClient
- Test ID token validation with various claim scenarios
- Test missing claims (sub, email, name)
- Test invalid issuer, audience, expiration
- Achieve >80% code coverage

Create `backend/src/test/java/com/scrumpoker/integration/oauth/MicrosoftOAuthProviderTest.java`:

**Requirements:**
- Test token exchange with mocked OidcClient
- Test ID token validation including preferred_username fallback
- Test Microsoft-specific issuer formats
- Achieve >80% code coverage

**Note on "Real OAuth Code" Testing:**
The acceptance criteria mentions "test with real OAuth code" but this is NOT practical for automated unit tests (you cannot commit real OAuth credentials or obtain real auth codes in CI). Instead:
- Unit tests should mock the OAuth provider responses
- Document in test comments that manual integration testing was performed with real Google/Microsoft OAuth flows
- The "real OAuth code" testing will be done in task I3.T8 (integration tests)

### **Step 4: Verify Build Success**

After making ALL changes:

1. Run `mvn clean compile` - must succeed with no errors
2. Run `mvn checkstyle:check` - must show ZERO checkstyle violations for OAuth files
3. Run `mvn test` - all tests must pass
4. Run `mvn jacoco:check` - OAuth package must have >80% coverage
5. Run `mvn verify` - complete build must succeed

### **Step 5: Final Validation Checklist**

Before submitting, verify:
- [ ] All OAuth classes use Quarkus OIDC extension (OidcClient) instead of raw HTTP
- [ ] All checkstyle violations fixed (run `mvn checkstyle:check | grep oauth`)
- [ ] package-info.java exists for `com.scrumpoker.integration.oauth`
- [ ] Unit tests exist with >80% coverage
- [ ] All tests pass (`mvn test`)
- [ ] Full build succeeds (`mvn verify`)
- [ ] Configuration uses `quarkus.oidc-client.*` properties
- [ ] Environment variables used for secrets in production
- [ ] OAuthUserInfo DTO fields match User entity fields exactly
- [ ] Exception handling follows existing project patterns

---

## Additional Context

**Reference Files to Study:**
- `backend/src/test/java/com/scrumpoker/domain/user/UserServiceTest.java` - Example of comprehensive unit testing patterns with Mockito
- `backend/src/main/java/com/scrumpoker/domain/user/UserService.java` - The `findOrCreateUser()` method expects OAuthUserInfo with specific field names
- `backend/pom.xml` - Note that `quarkus-oidc` is already in dependencies (line 75-78)

**Quarkus OIDC Resources:**
- Quarkus OIDC Client Guide: https://quarkus.io/guides/security-oidc-client
- Use `@Named` qualifier to inject specific OIDC clients for different providers
