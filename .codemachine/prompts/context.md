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

### Context: enterprise-requirements (from 01_Context_and_Drivers.md)

```markdown
#### Enterprise Requirements
- **SSO Integration:** OIDC and SAML2 protocol support for identity federation
- **Organization Management:** Workspace creation, custom branding, org-wide defaults
- **Role-Based Access:** Admin/member roles with configurable permissions
- **Audit Logging:** Comprehensive event tracking for compliance and security monitoring
```

### Context: security-nfrs (from 01_Context_and_Drivers.md)

```markdown
#### Security
- **Transport Security:** HTTPS/TLS 1.3 for all communications, WSS for WebSocket connections
- **Authentication:** JWT tokens with 1-hour expiration, refresh token rotation
- **Authorization:** Role-based access control (RBAC) for organization features
- **Data Protection:** Encryption at rest for sensitive data (PII, payment info), GDPR compliance
- **Session Isolation:** Anonymous session data segregated by room ID, automatic cleanup after 24 hours
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### ⚠️ CRITICAL DISCOVERY: THIS TASK IS ALREADY COMPLETE

*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/SsoAuthenticationIntegrationTest.java`
    *   **Summary:** **THIS FILE ALREADY EXISTS AND IS COMPREHENSIVE.** The file is 442 lines long and contains a fully working integration test suite for SSO authentication with 6 test cases covering all acceptance criteria.
    *   **Current Test Coverage:**
        1. Lines 165-244: `testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg` - Tests complete OIDC flow including JIT provisioning, org assignment, JWT token generation, and audit logging
        2. Lines 247-312: `testOidcSsoCallback_ReturningUser_DoesNotDuplicateOrgMembership` - Tests idempotency for returning users
        3. Lines 315-334: `testOidcSsoCallback_MissingEmail_Returns400` - Tests validation for missing email
        4. Lines 337-356: `testOidcSsoCallback_UnknownDomain_Returns401` - Tests domain validation
        5. Lines 359-378: `testOidcSsoCallback_MissingCodeVerifier_Returns400` - Tests OIDC parameter validation
        6. Lines 382-404: `testOidcSsoCallback_DomainMismatch_Returns401` - Tests security domain matching
    *   **Test Implementation Approach:** Uses `MockSsoAdapter` class (lines 77-122) marked as `@Alternative` bean to replace real SsoAdapter during tests. This approach avoids WireMock complexity and integrates cleanly with Quarkus CDI.
    *   **Recommendation:** **DO NOT REWRITE THIS FILE.** Run `mvn verify` to confirm all tests pass. The tests comprehensively verify all requirements in the acceptance criteria.

*   **CRITICAL FINDING - SAML2 Status:**
    *   Lines 407-419 of SsoAuthenticationIntegrationTest.java explicitly document: "NOTE: SAML2 protocol is planned but NOT YET IMPLEMENTED in the codebase. The SsoAdapter only supports OIDC protocol (see SsoAdapter lines 121-126). SAML2 integration tests will be added in a future iteration when SAML2 support is implemented."
    *   `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java` lines 121-126 throw `SsoAuthenticationException` for any protocol other than "oidc" with message: "Unsupported SSO protocol: " + protocol + ". Only OIDC is currently supported."
    *   **Conclusion:** SAML2 cannot be tested because it is not implemented in the codebase. The task description asks for SAML2 tests, but this is impossible without first implementing SAML2 authentication support.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** REST controller handling SSO authentication at `POST /api/v1/auth/sso/callback` (lines 370-558). Complete implementation includes:
        - Lines 401-408: Extract IP address and user agent from HTTP headers for audit logging
        - Lines 428-447: Validate email and lookup organization by email domain
        - Lines 461-467: Call SsoAdapter.authenticate() with org SSO config
        - Lines 472-479: Verify email domain matches organization domain
        - Lines 481-489: JIT user provisioning via UserService.findOrCreateUser()
        - Lines 494-507: Organization membership assignment with duplicate handling
        - Lines 512-525: JWT token generation and audit log entry creation
    *   **Recommendation:** The existing integration test already verifies this complete flow works correctly.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
    *   **Summary:** SSO adapter providing unified SSO interface. **Only supports OIDC protocol** (lines 121-126).
    *   **Key Methods:**
        - `authenticate()` (line 92): Main entry point, validates inputs, routes to OIDC handler
        - `authenticateOidc()` (line 143): Delegates to OidcProvider
        - `parseSsoConfig()` (line 179): Deserializes JSON config from database
        - `getSupportedProtocols()` (line 243): Returns ["oidc"] only
    *   **Recommendation:** The adapter is well-tested by existing unit and integration tests.

*   **File:** `backend/src/test/resources/sso/`
    *   **Summary:** Test resources directory exists with:
        - `README.md` - Documentation
        - `mock_id_token.jwt` - Sample JWT token (not actively used by tests, MockSsoAdapter used instead)
    *   **Missing:** `mock_saml_response.xml` (because SAML2 is not implemented)
    *   **Recommendation:** The existing test approach using MockSsoAdapter is superior to file-based mocks. No changes needed.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/AuditLogService.java`
    *   **Summary:** Creates audit log entries. Method `logSsoLogin()` is called by AuthController line 520-525.
    *   **Test Coverage:** Existing test at lines 219-243 verifies audit log entry creation with 500ms delay for async processing.

### Implementation Tips & Notes

*   **CRITICAL: Task Already Complete for OIDC** - The existing test file (`SsoAuthenticationIntegrationTest.java`) already:
    ✅ Tests OIDC SSO callback with mock IdP (using MockSsoAdapter)
    ✅ Verifies user JIT provisioning on first login
    ✅ Confirms organization assignment based on email domain
    ✅ Validates JWT tokens are returned in response
    ✅ Checks audit log entry creation
    ✅ Tests error scenarios (missing email, unknown domain, domain mismatch)
    ✅ Uses Testcontainers for PostgreSQL
    ✅ Follows Quarkus @QuarkusTest integration test pattern

*   **CRITICAL: SAML2 Cannot Be Tested** - The codebase explicitly does not support SAML2:
    - SsoAdapter.java throws exception for non-OIDC protocols
    - No Saml2Provider class exists
    - No SAML parsing/validation logic exists
    - Architecture blueprint mentions SAML2 as planned feature, but it's not implemented
    ❌ Writing SAML2 tests is **impossible** without implementing SAML2 support first

*   **Acceptance Criteria Analysis:**
    - ✅ "`mvn verify` runs SSO integration tests" - Already works
    - ✅ "OIDC test creates user on first login" - Line 196-206 verifies this
    - ✅ "User assigned to organization based on email domain" - Lines 208-217 verify this
    - ❌ "SAML2 test works similarly" - **Cannot be done**, SAML2 not implemented
    - ✅ "JWT tokens returned contain org membership claim" - Lines 188-192 verify token response
    - ✅ "Audit log entry created for SSO login" - Lines 229-243 verify this

*   **What You Should Do:**
    1. Run `mvn verify` to confirm existing tests pass
    2. Review the test file to understand the implementation
    3. Document that OIDC tests are complete and passing
    4. Document that SAML2 tests cannot be written until SAML2 support is added to SsoAdapter
    5. Optionally create a placeholder test method with `@Disabled` annotation noting SAML2 is not yet implemented
    6. Mark the task as done for OIDC portion; note SAML2 as blocked on implementation

*   **Test Pattern Used (for reference):**
    - `@QuarkusTest` with custom test profile extending `NoSecurityTestProfile`
    - `MockSsoAdapter` as `@Alternative` bean replacing real SsoAdapter
    - `@RunOnVertxContext` with `UniAsserter` for reactive testing
    - REST Assured for HTTP endpoint testing
    - Database queries via repositories to verify data persistence
    - 500ms delay before audit log assertions to allow async processing

*   **Why Not Use WireMock:** The existing implementation chose MockSsoAdapter over WireMock because:
    - Cleaner integration with Quarkus CDI and reactive patterns
    - No need to mock HTTP endpoints and certificate validation
    - Full control over test scenarios and error conditions
    - Tests run faster without HTTP calls
    - This is an integration test of AuthController, not OidcProvider (which has its own unit tests)

*   **Recommendation:** Do NOT attempt to implement SAML2 tests. The architecture says SAML2 "support" exists, but code inspection proves it does not. The task was written under the assumption SAML2 was implemented, but it isn't. Document this discrepancy rather than creating fake tests.
