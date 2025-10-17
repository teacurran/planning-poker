# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create `OAuth2Adapter` service integrating with Google and Microsoft OAuth2 providers using Quarkus OIDC extension. Implement authorization code flow with PKCE: validate authorization code, exchange for access token, fetch user profile (ID token claims: sub, email, name, picture), return `OAuthUserInfo` DTO. Configure OAuth2 client IDs/secrets in `application.properties` (use environment variables for prod). Handle token validation (signature verification, expiration checks). Implement provider-specific logic (Google uses `accounts.google.com`, Microsoft uses `login.microsoftonline.com`).

---

## Issues Detected

### **Critical Issue: Checkstyle Violations**

The OAuth2 implementation has **100+ checkstyle violations** that will cause `mvn verify` to fail. These MUST be fixed to meet project quality standards.

#### **Missing File:**
*   **Missing package-info.java:** The `com.scrumpoker.integration.oauth` package requires a `package-info.java` file with package documentation.

#### **OAuth2AuthenticationException.java (1 violation):**
*   **Line 12:** Line is longer than 80 characters (found 89) - break into multiple lines
*   **Line 107:** Method `toString` lacks Javadoc explaining safe extension pattern

#### **GoogleOAuthProvider.java (44+ violations):**
*   **Unused imports (lines 3, 4, 13, 15):** Remove unused imports:
    - `io.quarkus.oidc.OidcRequestContext`
    - `io.quarkus.oidc.OidcTenantConfig`
    - `javax.net.ssl.HttpsURLConnection`
    - `java.io.OutputStream`
*   **Missing Javadoc (lines 35, 37-39, 41, 44, 47, 50):** All private/package fields need Javadoc comments
*   **Visibility modifiers (lines 42, 45, 48):** Fields `clientId`, `clientSecret`, `jwtParser` must be `private` (currently package-private)
*   **Line length violations:** Lines 29, 35, 38, 60, 62, 80-81, 100, 120, 126, 149, 158, 181, 183, 188 exceed 80 characters
*   **Operator wrap violations:** Lines 80, 149 - `+` and `&&` operators should be on new line
*   **Final parameters:** Parameters in methods at lines 64-66, 132, 199 should be marked `final`
*   **Magic numbers:** Lines 51, 90 (10), 96 (200), 141 (1000), 206 (11) - extract to constants

#### **MicrosoftOAuthProvider.java (44+ violations):**
*   **Missing Javadoc (lines 34, 36-37, 39, 42, 45, 48, 51):** All private/package fields need Javadoc comments
*   **Visibility modifiers (lines 43, 46, 49):** Fields `clientId`, `clientSecret`, `jwtParser` must be `private`
*   **Line length violations:** Lines 25, 34, 61, 63, 81-82, 101, 121, 127, 164, 184, 193, 195, 200
*   **Operator wrap violations:** Lines 81, 215 - `+` and `||` operators should be on new line
*   **Final parameters:** Parameters in methods at lines 65-67, 139, 214, 226 should be marked `final`
*   **Magic numbers:** Lines 52 (10), 91 (10), 97 (200), 148 (1000), 233 (11) - extract to constants

#### **OAuthUserInfo.java (40+ violations):**
*   **Line length violations:** Lines 8, 10 exceed 80 characters
*   **Magic numbers:** Lines 20 (255), 29 (255), 37 (100), 45 (500), 54 (50) - extract to constants
*   **Final parameters & HiddenField violations (lines 72-73):** Constructor parameters should be `final` and hide fields - use `this.` prefix consistently
*   **Missing Javadoc & DesignForExtension (lines 83-130):** All getters/setters need Javadoc OR make class `final` to avoid extension issues
*   **Operator wrap violations (lines 125-130):** In `toString()`, `+` operators should be on new line

---

## Best Approach to Fix

### **Step 1: Create Missing package-info.java**

Create `backend/src/main/java/com/scrumpoker/integration/oauth/package-info.java`:

```java
/**
 * OAuth2 integration adapters for third-party identity providers.
 * <p>
 * Provides OAuth2 authentication flows for Google and Microsoft
 * using PKCE (Proof Key for Code Exchange) for secure token exchange.
 * Implements ID token validation with JWT signature verification.
 * </p>
 */
package com.scrumpoker.integration.oauth;
```

### **Step 2: Fix All Checkstyle Violations Systematically**

For each OAuth file, fix violations in this order:

1. **Remove unused imports** (GoogleOAuthProvider only)
2. **Extract magic numbers to constants** - Create constants at the top of each class:
   ```java
   private static final int CONNECT_TIMEOUT_SECONDS = 10;
   private static final int HTTP_OK = 200;
   private static final long MILLIS_PER_SECOND = 1000L;
   private static final int ID_TOKEN_PREFIX_LENGTH = 11;
   private static final int MAX_SUBJECT_LENGTH = 255;
   private static final int MAX_EMAIL_LENGTH = 255;
   private static final int MAX_NAME_LENGTH = 100;
   private static final int MAX_AVATAR_URL_LENGTH = 500;
   private static final int MAX_PROVIDER_LENGTH = 50;
   ```

3. **Make all injected/config fields private** - Change visibility:
   ```java
   @Inject
   private JWTParser jwtParser;  // was package-private

   @ConfigProperty(name = "quarkus.oidc.google.client-id")
   private String clientId;  // was package-private
   ```

4. **Add Javadoc to all private fields** - Example:
   ```java
   /** OAuth2 token endpoint URL for Google. */
   private static final String TOKEN_ENDPOINT =
       "https://oauth2.googleapis.com/token";

   /** JWT parser for ID token validation. */
   @Inject
   private JWTParser jwtParser;
   ```

5. **Mark all method parameters as final**:
   ```java
   public OAuthUserInfo exchangeCodeForToken(
       final String authorizationCode,
       final String codeVerifier,
       final String redirectUri
   ) throws OAuth2AuthenticationException {
   ```

6. **Fix line length violations** - Break lines at 80 characters:
   ```java
   // Before (89 chars):
   throw new OAuth2AuthenticationException("Invalid token response", "google", e);

   // After:
   throw new OAuth2AuthenticationException(
       "Invalid token response", "google", e
   );
   ```

7. **Fix operator wrap violations** - Move operators to new line:
   ```java
   // Before:
   String requestBody = "code=" + authorizationCode +
       "&client_id=" + clientId;

   // After:
   String requestBody = "code=" + authorizationCode
       + "&client_id=" + clientId;
   ```

8. **Fix constructor HiddenField violations in OAuthUserInfo** - Use `this.`:
   ```java
   public OAuthUserInfo(
       final String subject,
       final String email,
       final String name,
       final String avatarUrl,
       final String provider
   ) {
       this.subject = subject;
       this.email = email;
       this.name = name;
       this.avatarUrl = avatarUrl;
       this.provider = provider;
   }
   ```

9. **Fix DesignForExtension issues** - Make DTOs and exceptions `final`:
   ```java
   public final class OAuthUserInfo {
       // class content
   }

   public final class OAuth2AuthenticationException
       extends RuntimeException {
       // class content
   }
   ```

### **Step 3: Verify All Fixes**

After making changes, run these commands in order:

1. **Check compilation:** `mvn clean compile` - must succeed
2. **Check tests:** `mvn test` - all tests must pass
3. **Check checkstyle:** `mvn checkstyle:check` - must show ZERO violations
4. **Full verification:** `mvn verify` - complete build must succeed

### **Step 4: Final Validation**

Ensure:
- [ ] `mvn checkstyle:check` shows 0 violations for oauth package
- [ ] `mvn test` passes all OAuth tests (OAuth2AdapterTest, GoogleOAuthProviderTest, MicrosoftOAuthProviderTest)
- [ ] `mvn verify` completes successfully
- [ ] No functional changes - only formatting/style fixes
- [ ] package-info.java exists in oauth package

---

## Important Notes

**DO NOT change any functional logic** - this is purely a code style/quality fix. The implementation is complete and all tests pass. You are ONLY fixing checkstyle violations.

**Reference existing code** - Look at other packages in the project (e.g., `com.scrumpoker.domain.user`) to see examples of proper checkstyle compliance:
- How constants are extracted
- How Javadoc is written
- How line breaks are formatted
- How classes are marked `final` when not designed for extension

**Test after each major change** - Run `mvn checkstyle:check` frequently to catch new issues early.
