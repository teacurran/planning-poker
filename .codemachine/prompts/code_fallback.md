# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

**Task ID:** I7.T1
**Description:** Create `SsoAdapter` service supporting OIDC and SAML2 protocols using Quarkus Security extensions. OIDC: configure IdP discovery endpoint, handle authorization code flow, validate ID token, extract user attributes (email, name, groups). SAML2: configure IdP metadata URL, handle SAML response, validate assertions, extract attributes from assertion. Map IdP attributes to User entity fields. Support per-organization SSO configuration (stored in Organization.sso_config JSONB). Implement backchannel logout (OIDC logout endpoint, SAML SLO).

**Acceptance Criteria:**
- OIDC authentication flow completes with test IdP (Okta sandbox)
- SAML2 authentication flow completes (Azure AD or SAML test IdP)
- User attributes correctly mapped from ID token/assertion
- Organization-specific SSO config loaded from database
- Logout endpoint invalidates SSO session
- Certificate validation works for SAML2

---

## Issues Detected

### 1. Test Failures - Mockito Unnecessary Stubbing Errors

**Files Affected:**
- `backend/src/test/java/com/scrumpoker/integration/sso/OidcProviderTest.java`
- `backend/src/test/java/com/scrumpoker/integration/sso/SsoAdapterTest.java`

**Specific Failures:**
- `OidcProviderTest.validateAndExtractClaims_expiredToken_throwsException` - UnnecessaryStubbingException at lines 296-300
- `OidcProviderTest.validateAndExtractClaims_invalidIssuer_throwsException` - UnnecessaryStubbingException at lines 309-313
- `OidcProviderTest.validateAndExtractClaims_invalidAudience_throwsException` - UnnecessaryStubbingException at lines 321-323
- `SsoAdapterTest.authenticate_nullAuthenticationData_throwsException` - UnnecessaryStubbingException at line 191
- `SsoAdapterTest.authenticate_nullOrganizationId_throwsException` - UnnecessaryStubbingException at line 210

**Root Cause:**
The test helper methods (`createExpiredMockJwt()`, `createMockJwtWithInvalidIssuer()`, `createMockJwtWithInvalidAudience()`) are setting up mock stubs for JWT fields that are never accessed because the validation throws an exception early (e.g., when checking expiration, the issuer and audience stubs are never used).

### 2. OpenSAML Version Mismatch

**File:** `backend/pom.xml`

**Issue:**
The `Saml2Provider.java` implementation uses OpenSAML 5 API, but `pom.xml` specifies OpenSAML version `4.3.2`:
```xml
<opensaml.version>4.3.2</opensaml.version>
```

OpenSAML 5 has breaking API changes from version 4, and the current code will fail at runtime with NoClassDefFoundError or similar errors.

### 3. Potential Runtime Issues

Although compilation succeeded (because OpenSAML 4 has similar class names), the actual SAML2 authentication flow will likely fail at runtime due to:
- Different initialization API between OpenSAML 4 and 5
- Different XML parsing APIs
- Different signature validation APIs

---

## Best Approach to Fix

### Fix #1: Resolve Test Mockito Stubbing Issues

You MUST update the test helper methods in `OidcProviderTest.java` and `SsoAdapterTest.java` to use **lenient stubbing** for mocks that might not be fully accessed when exceptions are thrown early.

**In OidcProviderTest.java:**

1. Locate the helper methods at lines 294-329:
   - `createExpiredMockJwt()`
   - `createMockJwtWithInvalidIssuer()`
   - `createMockJwtWithInvalidAudience()`

2. Change the mock creation from:
   ```java
   JsonWebToken jwt = mock(JsonWebToken.class);
   ```

   To:
   ```java
   JsonWebToken jwt = mock(JsonWebToken.class, withSettings().lenient());
   ```

3. This tells Mockito that not all stubs may be used, which is expected for these negative test cases.

**In SsoAdapterTest.java:**

1. Find the two test methods that have unnecessary stubbing errors:
   - `authenticate_nullAuthenticationData_throwsException` (around line 191)
   - `authenticate_nullOrganizationId_throwsException` (around line 210)

2. Add `lenient()` wrapper to any mock stubs that might not be used when the IllegalArgumentException is thrown early. For example:
   ```java
   lenient().when(objectMapper.readValue(anyString(), eq(SsoConfig.class)))
           .thenReturn(mockSsoConfig);
   ```

### Fix #2: Upgrade OpenSAML to Version 5.x

You MUST update `backend/pom.xml` to use OpenSAML 5.x to match the implementation in `Saml2Provider.java`.

**In backend/pom.xml:**

1. Locate line 21:
   ```xml
   <opensaml.version>4.3.2</opensaml.version>
   ```

2. Change it to:
   ```xml
   <opensaml.version>5.1.2</opensaml.version>
   ```

   (OpenSAML 5.1.2 is the latest stable version compatible with Java 17 and the code implementation)

3. Verify the Shibboleth repository is still present (it should be around lines 24-31) - this repository is required for OpenSAML 5 artifacts.

4. After updating, run `mvn clean compile` to verify the new version compiles correctly.

### Fix #3: Verify Saml2Provider Implementation

After upgrading OpenSAML to version 5.x, you should:

1. Run the backend tests again to ensure SAML2 tests pass:
   ```bash
   mvn test -Dtest="com.scrumpoker.integration.sso.*Test"
   ```

2. If there are still test failures in `Saml2ProviderTest`, read the test file and fix any test-specific issues (similar to the Mockito stubbing issues).

### Summary of Required Changes

1. **OidcProviderTest.java**: Add `withSettings().lenient()` to mock creation in 3 helper methods (lines ~294, ~307, ~319)
2. **SsoAdapterTest.java**: Add `lenient()` wrapper to mock stubs in 2 test methods that validate null arguments
3. **pom.xml**: Change `<opensaml.version>4.3.2</opensaml.version>` to `<opensaml.version>5.1.2</opensaml.version>`

### Testing Steps After Fixes

1. Run `mvn clean compile -DskipTests` to verify compilation
2. Run `mvn test -Dtest="com.scrumpoker.integration.sso.*Test"` to verify all SSO tests pass
3. Verify no linting errors or warnings (except deprecation warnings which are acceptable)

---

## Additional Context

- The OIDC implementation in `OidcProvider.java` is complete and correct
- The SAML2 implementation in `Saml2Provider.java` is complete and uses OpenSAML 5 API correctly
- The `SsoAdapter.java` orchestration layer is complete and correct
- All configuration POJOs (`SsoConfig`, `OidcConfig`, `Saml2Config`, `SsoUserInfo`) are complete
- The only issues are test mock configuration and dependency version mismatch

Do NOT rewrite the implementation files. Only fix the test files and pom.xml as specified above.
