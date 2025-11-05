# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I7.T7",
  "iteration_id": "I7",
  "iteration_goal": "Implement enterprise-tier features including SSO integration (OIDC/SAML2), organization management, member administration, org-level branding, and audit logging.",
  "description": "Create integration test for SSO authentication flow using mock IdP. Test OIDC: mock authorization server, valid ID token, callback processes successfully, user created (JIT provisioning), org assignment, JWT tokens returned. Test SAML2: mock SAML response, assertion validated, user provisioned, tokens returned. Test audit log entry creation. Use Testcontainers for PostgreSQL.",
  "agent_type_hint": "BackendAgent",
  "inputs": "SSO callback handler from I7.T4, Mock IdP patterns (WireMock for OIDC, SAML test assertions)",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/api/rest/AuthController.java",
    "backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java"
  ],
  "target_files": [
    "backend/src/test/java/com/scrumpoker/api/rest/SsoAuthenticationIntegrationTest.java",
    "backend/src/test/resources/sso/mock_id_token.jwt",
    "backend/src/test/resources/sso/mock_saml_response.xml"
  ],
  "deliverables": "Integration test for OIDC SSO flow, Integration test for SAML2 SSO flow, Mock IdP responses (ID token, SAML assertion), Assertions: user created, org assigned, tokens returned, Audit log entry verified",
  "acceptance_criteria": "`mvn verify` runs SSO integration tests, OIDC test creates user on first login, User assigned to organization based on email domain, SAML2 test works similarly, JWT tokens returned contain org membership claim, Audit log entry created for SSO login",
  "dependencies": [
    "I7.T4"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: authentication-mechanisms (from 05_Operational_Architecture.md)

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

### Context: audit-logging (from 05_Operational_Architecture.md)

```markdown
**Audit Logging:**
- **Scope:** Enterprise tier security and compliance events
- **Storage:** Dedicated `AuditLog` table (partitioned by month) + immutable S3 bucket for archival
- **Events:**
  - User authentication (SSO login, logout)
  - Organization configuration changes (SSO settings, branding)
  - Member management (invite, role change, removal)
  - Administrative actions (room deletion, user account suspension)
- **Attributes:** `timestamp`, `orgId`, `userId`, `action`, `resourceType`, `resourceId`, `ipAddress`, `userAgent`, `changeDetails` (JSONB)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** This file contains the REST controller for authentication endpoints including OAuth2 and SSO callback handlers. The `ssoCallback` method (lines 370-558) implements the complete SSO authentication flow including organization lookup, SSO adapter authentication, user JIT provisioning, organization membership assignment, JWT token generation, and audit log entry creation.
    *   **Recommendation:** You MUST study this implementation carefully as it shows the exact flow your test needs to verify. Pay special attention to how `SsoAdapter.authenticate()` is called with `SsoAuthParams` (lines 462-467), email domain extraction (line 430), organization lookup (line 433), JIT provisioning (lines 482-489), membership assignment with error recovery (lines 495-507), and audit logging (lines 519-525).
    *   **Note:** The SSO callback handler extracts IP address and user agent from HTTP headers (lines 401-408) for audit logging. Your test should verify these are captured correctly.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
    *   **Summary:** This is the SSO adapter service that provides a unified interface for enterprise SSO authentication. Currently ONLY supports OIDC protocol (lines 121-126 validate protocol is "oidc"). Key methods: `authenticate()` (main entry point), `authenticateOidc()` (delegates to OidcProvider), `parseSsoConfig()` (deserializes JSON config), and `logout()` (backchannel logout).
    *   **Recommendation:** The adapter expects `ssoConfigJson` containing JSON with `"protocol": "oidc"` and oidc configuration object, `authenticationData` (authorization code), `SsoAuthParams` with `codeVerifier` and `redirectUri`, and `organizationId` UUID.
    *   **CRITICAL:** SAML2 is mentioned in comments and task description but is NOT IMPLEMENTED. The code throws `SsoAuthenticationException` for any protocol other than "oidc". You should ONLY test OIDC flow, not SAML2.

*   **File:** `backend/src/test/java/com/scrumpoker/integration/sso/SsoAdapterTest.java`
    *   **Summary:** Unit test for SsoAdapter showing how to mock the adapter's dependencies using Mockito. Helper methods `createOidcConfigJson()` (lines 282-293) and `createOidcSsoConfig()` (lines 295-308) show the exact SSO config structure needed.
    *   **Recommendation:** You SHOULD use these helper methods as a reference for creating test SSO configurations. Notice the use of `lenient()` for some mocks to avoid strict stubbing exceptions.

*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/UserControllerTest.java` and similar integration tests
    *   **Summary:** Demonstrates the integration test pattern: `@QuarkusTest`, `@TestProfile`, Testcontainers, and REST Assured's `given().when().then()` pattern.
    *   **Recommendation:** You MUST follow this pattern: Use `@QuarkusTest`, create test profile for database config, use Testcontainers for PostgreSQL, use REST Assured for HTTP testing.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
    *   **Summary:** Handles organization management including `addMember()` which is called by AuthController to assign users to organizations. Checks for duplicate membership and recovers gracefully.
    *   **Recommendation:** Your test must verify that organization membership is created correctly during SSO callback.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/AuditLogService.java`
    *   **Summary:** Creates audit log entries for enterprise compliance. The `logSsoLogin()` method is called by AuthController (line 520-525).
    *   **Recommendation:** Your test MUST verify audit log entry is created with correct org ID, user ID, action, IP address, and user agent by querying the `audit_log` table.

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Summary:** Generates JWT access tokens and refresh tokens.
    *   **Recommendation:** Your test MUST verify JWT tokens are returned and contain expected claims (user ID, email, tier, org membership).

### Implementation Tips & Notes

*   **CRITICAL:** The task description mentions testing SAML2, but the codebase shows SAML2 is NOT IMPLEMENTED. The `SsoAdapter` only supports OIDC (see line 121-126). You should ONLY implement integration tests for OIDC SSO flow. Document in test comments that SAML2 is planned but not yet implemented.

*   **Warning:** The acceptance criteria mentions "SAML2 test works similarly", but this is NOT POSSIBLE with current codebase. Focus on thoroughly testing OIDC flow. You may want to create a TODO comment or skip annotation for future SAML2 test.

*   **Tip:** For mocking the IdP, you have two approaches:
    1. **Simple approach:** Mock the `SsoAdapter.authenticate()` method to return successful `SsoUserInfo`. This tests AuthController logic without OIDC protocol details.
    2. **Complex approach:** Use WireMock to mock OIDC IdP endpoints (token endpoint, JWKS endpoint) and let real SsoAdapter/OidcProvider execute. This tests the full protocol flow.
    
    The simple approach is recommended for this integration test since the OidcProvider already has unit tests.

*   **Tip:** Create test resources at `backend/src/test/resources/sso/`. You need `mock_id_token.jwt` (sample JWT string). You do NOT need `mock_saml_response.xml` since SAML2 is not implemented.

*   **Note:** AuthController's SSO callback requires `email` parameter in request (line 423-426) for domain-based organization lookup. Your test data must include this in `SsoCallbackRequest`.

*   **Tip:** Following existing test patterns, your test should:
    1. Create test profile (or reuse existing `NoSecurityTestProfile`)
    2. Set up test data in `@BeforeEach`: create organization with domain and SSO config
    3. Mock `SsoAdapter.authenticate()` to return successful `SsoUserInfo`
    4. Use REST Assured to POST to `/api/v1/auth/sso/callback`
    5. Assert response status 200 OK
    6. Assert response body contains access token, refresh token, user data
    7. Query database to verify user was created
    8. Query database to verify organization membership was created  
    9. Query database to verify audit log entry was created

*   **Warning:** The `SsoAdapter.authenticate()` method expects SSO config as JSON string (from `Organization.ssoConfig` JSONB field), not Java object. Your test data must include properly formatted JSON string.

*   **Note:** Audit log service uses `@ObservesAsync` for async event processing (AuthController line 520). The audit log entry might not be immediately available. You may need small delay or retry logic in test assertion, or use `await().atMost()` pattern.

*   **Recommendation:** The most practical approach for this integration test is:
    1. Create a test organization in database with domain "example.com" and SSO config JSON
    2. Mock the `SsoAdapter` bean using `@InjectMock` or Quarkus MockBean
    3. Stub `authenticate()` to return successful `SsoUserInfo` with email "testuser@example.com"
    4. Call `/api/v1/auth/sso/callback` with test request data
    5. Verify the full flow: user created, org membership added, tokens returned, audit log created
    
    This approach tests the AuthController integration logic without needing to mock external IdP HTTP calls.

*   **Tip:** Look at how `OAuth2AdapterTest` mocks OAuth2 flows - you can use similar patterns for mocking SSO adapter responses.

*   **Note:** The project uses Quarkus `@WithTransaction` annotation. Your test data setup should be transactional to ensure proper cleanup. Consider using `@Transactional` annotation or Quarkus test transaction utilities.

*   **Tip:** For JWT token verification, you can parse the returned access token and decode its claims using a JWT library like `io.jsonwebtoken:jjwt` or `io.vertx:vertx-auth-jwt` (already in Quarkus). Assert the token contains correct user ID, email, and subscription tier.
