# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create `SsoAdapter` service supporting OIDC and SAML2 protocols using Quarkus Security extensions. OIDC: configure IdP discovery endpoint, handle authorization code flow, validate ID token, extract user attributes (email, name, groups). SAML2: configure IdP metadata URL, handle SAML response, validate assertions, extract attributes from assertion. Map IdP attributes to User entity fields. Support per-organization SSO configuration (stored in Organization.sso_config JSONB). Implement backchannel logout (OIDC logout endpoint, SAML SLO).

---

## Issues Detected

### Critical Issues

1. **SAML2 Implementation Incomplete**: The `Saml2Provider.java` is a skeleton implementation that throws `SsoAuthenticationException` with message "SAML2 response parsing not yet implemented. OpenSAML 5 integration required." in the `parseSamlResponsePlaceholder()` method at line 266. This means SAML2 authentication CANNOT work at all.

2. **Missing OpenSAML Dependencies**: The `pom.xml` file has OpenSAML dependencies COMMENTED OUT (lines 174-205). Without these dependencies, SAML2 support cannot be completed. The comments indicate:
   - OpenSAML dependencies require Shibboleth repository configuration
   - SAML2 support is only "partially implemented"
   - Dependencies need to be uncommented when ready to complete SAML2

3. **SAML2 Logout Not Implemented**: The `Saml2Provider.logout()` method at line 386 only logs a warning "SAML2 SLO not yet implemented" and returns `false`, meaning SAML Single Logout does not work.

4. **No Integration Tests**: There are NO test files for the SSO implementation (`Glob` search for `**/test/**/sso/**/*.java` returned no files). The acceptance criteria requires:
   - "OIDC authentication flow completes with test IdP (Okta sandbox)"
   - "SAML2 authentication flow completes (Azure AD or SAML test IdP)"
   - "Certificate validation works for SAML2"
   These cannot be verified without tests.

### Moderate Issues

5. **JWTParser Dependency Not Configured**: The `OidcProvider.java` uses `@Inject JWTParser` at line 62, but this requires proper Quarkus OIDC configuration. The current `application.properties` has OIDC config for Google and Microsoft social login (lines 78-109), but NO enterprise OIDC configuration for multi-tenant IdPs. The JWTParser needs:
   - JWKS URI configuration for signature verification
   - Proper tenant/issuer validation setup
   - This may fail at runtime if JWTParser cannot validate enterprise IdP tokens

6. **Blocking HTTP Client in Reactive Code**: The `OidcProvider` uses `java.net.http.HttpClient` (synchronous/blocking) inside `Uni.createFrom().item()` at lines 104-177. This is an ANTI-PATTERN in reactive Quarkus applications. The comment at line 78-80 acknowledges this: "This method is REACTIVE (returns Uni) but internally uses blocking HTTP client. Future enhancement: replace with Mutiny WebClient for fully reactive flow." This can cause thread starvation under load.

7. **Missing OIDC Discovery Implementation**: The `OidcProvider` does NOT implement OIDC discovery (fetching `/.well-known/openid-configuration`). It uses hardcoded fallback at line 127: `tokenEndpoint = oidcConfig.getIssuer() + "/token"`. This is fragile and may not work with all IdPs.

### Minor Issues

8. **No Certificate Expiration Handling for Renewal**: While `Saml2Provider.loadCertificate()` validates certificate expiration at line 183 (`x509Cert.checkValidity()`), there's NO mechanism to notify administrators when certificates are about to expire or to auto-reload renewed certificates from the database.

9. **Hardcoded Timeout Values**: Connection timeout is hardcoded to 10 seconds (`CONNECT_TIMEOUT_SECONDS = 10` at line 52 in `OidcProvider`). This should be configurable via `application.properties` for different IdP latency requirements.

10. **Missing Attribute Mapping Validation**: The `Saml2Config.attributeMapping` is optional (can be empty Map), but `Saml2Provider.extractUserInfo()` uses `.getOrDefault()` with hardcoded defaults ("email", "name", etc.). If an IdP uses completely different attribute names, this will silently fail to extract user info.

---

## Best Approach to Fix

### CRITICAL: Complete SAML2 Implementation

You MUST complete the SAML2 implementation to meet acceptance criteria. Here's the required approach:

1. **Add OpenSAML Dependencies**:
   - Uncomment the OpenSAML dependencies in `pom.xml` (lines 174-205)
   - Verify the Shibboleth repository is accessible
   - Ensure OpenSAML version 5.1.2 is compatible with Quarkus 3.15.1

2. **Implement SAML Response Parsing**:
   - Replace `parseSamlResponsePlaceholder()` with actual OpenSAML implementation
   - Follow the detailed pseudo-code in the comments (lines 202-253) which shows:
     - Initialize OpenSAML (`InitializationService.initialize()`)
     - Unmarshall SAML response XML
     - Validate signature using IdP certificate
     - Extract assertion and validate conditions (NotBefore, NotOnOrAfter)
     - Extract attributes from AttributeStatement
   - Use the existing `loadCertificate()` method for certificate loading (already implemented correctly)

3. **Implement SAML Logout**:
   - In `Saml2Provider.logout()`, build a SAML LogoutRequest XML using OpenSAML
   - Sign the request (may require SP private key - check architecture docs)
   - Send to IdP's SLO URL
   - Handle LogoutResponse parsing

4. **Create Integration Tests**:
   - Create test file: `backend/src/test/java/com/scrumpoker/integration/sso/OidcProviderTest.java`
     - Test OIDC token exchange with MOCKED IdP responses
     - Test ID token validation (signature, expiration, issuer, audience)
     - Test user attribute extraction (email, name, groups)
     - Test backchannel logout
   - Create test file: `backend/src/test/java/com/scrumpoker/integration/sso/Saml2ProviderTest.java`
     - Test SAML assertion parsing with MOCKED SAML response XML
     - Test certificate validation (use test certificate)
     - Test attribute extraction with custom mapping
     - Test assertion expiration validation
   - Create test file: `backend/src/test/java/com/scrumpoker/integration/sso/SsoAdapterTest.java`
     - Test protocol routing (OIDC vs SAML2)
     - Test JSON config parsing from Organization.ssoConfig format
     - Test error handling for invalid configs

### MODERATE: Fix OIDC Issues

5. **Replace Blocking HTTP Client**:
   - Replace `java.net.http.HttpClient` with Quarkus Mutiny `WebClient` (reactive)
   - Example: `@Inject @RestClient WebClient webClient;` then use `.sendAsync()` returning `Uni<Response>`
   - This ensures non-blocking token exchange

6. **Implement OIDC Discovery**:
   - Add method `discoverOidcConfiguration(String issuer)` that fetches `issuer + "/.well-known/openid-configuration"`
   - Cache discovery results in memory (per organization) to avoid repeated calls
   - Use discovered endpoints instead of hardcoded fallbacks

7. **Configure JWTParser for Multi-Tenant**:
   - Research how to configure Quarkus SmallRye JWT to validate tokens from MULTIPLE issuers (one per organization)
   - May need to use `JWTParser` with custom configuration per request, not CDI injection
   - Consider using `jose4j` library directly for more control over JWKS fetching and caching

### MINOR: Polish and Production-Ready

8. **Add Configuration Properties**:
   - Make timeouts configurable in `application.properties`:
     ```properties
     sso.oidc.connect-timeout=${SSO_OIDC_TIMEOUT:10000}
     sso.saml2.clock-skew=${SSO_SAML2_CLOCK_SKEW:300}
     ```
   - Use `@ConfigProperty` injection in providers

9. **Add Certificate Expiration Monitoring**:
   - Create scheduled job (Quarkus `@Scheduled`) that checks all organizations' SAML certificates monthly
   - Log warnings for certificates expiring within 30 days
   - Consider emitting audit log events for certificate issues

10. **Validate Attribute Mappings**:
    - In `SsoAdapter.authenticate()`, validate that `Organization.ssoConfig` has required attribute mappings for SAML2
    - Throw clear error message if email mapping is missing (cannot provision users without email)

---

## Implementation Priority

**Phase 1 (BLOCKER - must be done):**
- Uncomment OpenSAML dependencies in pom.xml
- Implement SAML response parsing in `Saml2Provider.parseSamlResponsePlaceholder()`
- Create at least basic unit tests for OIDC and SAML2 providers

**Phase 2 (HIGH - strongly recommended):**
- Replace blocking HttpClient with reactive WebClient in OidcProvider
- Implement OIDC discovery
- Add SAML logout implementation

**Phase 3 (MEDIUM - improve production readiness):**
- Add integration tests with real/test IdPs
- Fix JWTParser multi-tenant configuration
- Add configuration properties for timeouts

**Phase 4 (LOW - polish):**
- Certificate expiration monitoring
- Attribute mapping validation
- Enhanced error messages

---

## Testing Strategy

After implementing fixes:

1. **Compile**: Run `mvn clean compile` - MUST succeed without errors
2. **Unit Tests**: Run `mvn test` - all SSO tests MUST pass
3. **Manual OIDC Test**:
   - Set up Okta developer account
   - Configure organization with OIDC config in database
   - Test authorization code flow manually via curl/Postman
4. **Manual SAML2 Test**:
   - Use SAMLtest.id or similar test IdP
   - Configure organization with SAML2 config
   - Test assertion validation with real SAML response

---

## Reference Implementation

For SAML2 parsing, refer to the detailed pseudo-code already in `Saml2Provider.java` lines 202-253. This code shows EXACTLY how to use OpenSAML 5 API. You must translate this pseudo-code to actual working Java code.

For OIDC token validation, study the existing `OAuth2Adapter` and its providers (`GoogleOAuthProvider`, `MicrosoftOAuthProvider`) in `backend/src/main/java/com/scrumpoker/integration/oauth/` - these show the correct pattern for token exchange and validation, though they use blocking clients which you should improve.

---

## Acceptance Criteria Verification

After all fixes, verify EACH criterion:

- [ ] OIDC authentication flow completes with test IdP (Okta sandbox) - **Currently CANNOT verify, no tests**
- [ ] SAML2 authentication flow completes (Azure AD or SAML test IdP) - **Currently FAILS, not implemented**
- [ ] User attributes correctly mapped from ID token/assertion - **OIDC: YES, SAML2: NO**
- [ ] Organization-specific SSO config loaded from database - **YES, implemented in SsoAdapter**
- [ ] Logout endpoint invalidates SSO session - **OIDC: basic impl, SAML2: NO**
- [ ] Certificate validation works for SAML2 - **Cert loading: YES, validation in parsing: NO (not implemented)**
