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

### Context: logging-strategy (from 05_Operational_Architecture.md)

```markdown
##### Logging Strategy

**Structured Logging (JSON Format):**
- **Library:** SLF4J with Quarkus Logging JSON formatter
- **Schema:** Each log entry includes:
  - `timestamp` - ISO8601 format
  - `level` - DEBUG, INFO, WARN, ERROR
  - `logger` - Java class name
  - `message` - Human-readable description
  - `correlationId` - Unique request/WebSocket session identifier for distributed tracing
  - `userId` - Authenticated user ID (omitted for anonymous)
  - `roomId` - Estimation room context
  - `action` - Semantic action (e.g., `vote.cast`, `room.created`, `subscription.upgraded`)
  - `duration` - Operation latency in milliseconds (for timed operations)
  - `error` - Exception stack trace (for ERROR level)

**Log Levels by Environment:**
- **Development:** DEBUG (verbose SQL queries, WebSocket message payloads)
- **Staging:** INFO (API requests, service method calls, integration events)
- **Production:** WARN (error conditions, performance degradation, security events)

**Log Aggregation:**
- **Stack:** Loki (log aggregation) + Promtail (log shipper) + Grafana (visualization)
- **Alternative:** AWS CloudWatch Logs or GCP Cloud Logging for managed service
- **Retention:** 30 days for application logs, 90 days for audit logs (compliance requirement)
- **Query Optimization:** Indexed fields: `correlationId`, `userId`, `roomId`, `action`, `level`

**Audit Logging:**
- **Scope:** Enterprise tier security and compliance events
- **Storage:** Dedicated `AuditLog` table (partitioned by month) + immutable S3 bucket for archival
- **Events:**
  - User authentication (SSO login, OAuth login, failed attempts)
  - Organization configuration changes (SSO settings, branding updates)
  - Member management (user invited/removed, role changes)
  - Room deletion (preserving session history for compliance)
  - Sensitive data access (report export, audit log queries)
```

### Context: task-i7-t4 (from 02_Iteration_I7.md)

```markdown
*   **Task 7.4: Implement SSO Authentication Flow**
    *   **Task ID:** `I7.T4`
    *   **Description:** Extend `AuthController` to handle SSO authentication. New endpoint: `POST /api/v1/auth/sso/callback` (handle OIDC/SAML2 callback, validate assertion, extract user info, find or create user with JIT provisioning, assign to organization based on email domain, generate JWT tokens, return TokenPair). Integrate `SsoAdapter`, `OrganizationService`. JIT provisioning: if user doesn't exist, create User entity with SSO provider info, auto-add to organization if email domain matches. Domain-based org assignment (user with `@company.com` joins org with domain `company.com`). Log SSO login to AuditLog.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   SSO callback flow requirements
        *   SsoAdapter from I7.T1
        *   OrganizationService from I7.T2
        *   AuditLogService from I7.T3
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
        *   `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java` (extend)
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/SsoCallbackRequest.java`
    *   **Deliverables:**
        *   SSO callback endpoint handling OIDC and SAML2
        *   User JIT provisioning (create user on first SSO login)
        *   Email domain matching for org assignment
        *   OrgMember creation with MEMBER role on JIT provisioning
        *   JWT token generation for SSO-authenticated user
        *   Audit log entry for SSO login event
    *   **Acceptance Criteria:**
        *   OIDC callback creates user on first login
        *   User auto-assigned to organization matching email domain
        *   SAML2 callback works similarly
        *   JWT tokens returned with org membership in claims
        *   Existing user login skips provisioning, returns tokens
        *   Audit log records SSO login with user ID and org ID
    *   **Dependencies:** [I7.T1, I7.T2, I7.T3]
    *   **Parallelizable:** No (depends on all three services)
```

### Context: task-i7-t7 (from 02_Iteration_I7.md)

```markdown
*   **Task 7.7: Write Integration Tests for SSO Authentication**
    *   **Task ID:** `I7.T7`
    *   **Description:** Create integration test for SSO authentication flow using mock IdP. Test OIDC: mock authorization server, valid ID token, callback processes successfully, user created (JIT provisioning), org assignment, JWT tokens returned. Test SAML2: mock SAML response, assertion validated, user provisioned, tokens returned. Test audit log entry creation. Use Testcontainers for PostgreSQL.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   SSO callback handler from I7.T4
        *   Mock IdP patterns (WireMock for OIDC, SAML test assertions)
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
        *   `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/api/rest/SsoAuthenticationIntegrationTest.java`
        *   `backend/src/test/resources/sso/mock_id_token.jwt`
        *   `backend/src/test/resources/sso/mock_saml_response.xml`
    *   **Deliverables:**
        *   Integration test for OIDC SSO flow
        *   Integration test for SAML2 SSO flow
        *   Mock IdP responses (ID token, SAML assertion)
        *   Assertions: user created, org assigned, tokens returned
        *   Audit log entry verified
    *   **Acceptance Criteria:**
        *   `mvn verify` runs SSO integration tests
        *   OIDC test creates user on first login
        *   User assigned to organization based on email domain
        *   SAML2 test works similarly
        *   JWT tokens returned contain org membership claim
        *   Audit log entry created for SSO login
    *   **Dependencies:** [I7.T4]
    *   **Parallelizable:** No (depends on SSO implementation)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/SsoAuthenticationIntegrationTest.java`
    *   **Summary:** ALREADY EXISTS! This file has been partially implemented with comprehensive OIDC integration tests. It includes 6 test methods covering first-time login, returning users, validation errors, domain mismatches, and edge cases. The file uses `@QuarkusTest` with Testcontainers, mocks the `SsoAdapter`, and verifies JIT provisioning, organization assignment, JWT tokens, and audit logging.
    *   **Current State:** The file contains a TODO section for SAML2 tests (lines 377-390) with explicit comments that "SAML2 support is planned but NOT YET IMPLEMENTED." The OIDC tests are fully functional and passing.
    *   **Recommendation:** You MUST extend this existing file rather than creating a new one. Add SAML2 test methods in the TODO section. Follow the exact same pattern used for OIDC tests: setup test organization with SAML2 config, mock `SsoAdapter.authenticate()`, call `/api/v1/auth/sso/callback` with SAML2 request, verify user creation, org assignment, JWT tokens, and audit log.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java`
    *   **Summary:** Contains the SSO callback endpoint at line 192+ (`ssoCallback` method). Handles both OIDC and SAML2 protocols via the `SsoAdapter`. Performs JIT provisioning, domain validation, organization assignment, JWT token generation, and audit logging.
    *   **Recommendation:** You DO NOT need to modify this file. The SSO callback implementation is complete. Your tests must verify its behavior end-to-end.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
    *   **Summary:** Adapter that routes SSO authentication to protocol-specific providers (OidcProvider, Saml2Provider). The `authenticate()` method parses `ssoConfigJson`, routes based on protocol, and returns `SsoUserInfo`.
    *   **Current Limitation:** Lines 121-126 show that ONLY OIDC is currently implemented. SAML2 routing exists but will throw "Unsupported SSO protocol: saml2" until Saml2Provider is implemented.
    *   **Recommendation:** Your SAML2 tests MUST mock the `SsoAdapter.authenticate()` method to return a successful `SsoUserInfo` response, bypassing the unimplemented Saml2Provider. This is the same pattern used in existing OIDC tests (see line 108-119 of SsoAuthenticationIntegrationTest.java).

*   **File:** `backend/src/test/resources/sso/mock_id_token.jwt`
    *   **Summary:** Contains a base64-encoded JWT token for reference. However, the README.md explicitly states this file is NOT used directly in tests - tests mock the SsoAdapter instead.
    *   **Recommendation:** You SHOULD create `mock_saml_response.xml` in the same directory for documentation purposes, but like the JWT token, you do NOT need to parse it in tests. Mock the `SsoAdapter.authenticate()` response instead.

*   **File:** `backend/src/test/resources/sso/README.md`
    *   **Summary:** Documents that SAML2 support is planned, and notes that `mock_saml_response.xml` will be added in a future iteration.
    *   **Recommendation:** Update this README when you add the SAML2 mock file.

*   **File:** `backend/src/test/java/com/scrumpoker/integration/sso/SsoAdapterTest.java`
    *   **Summary:** Unit tests for SsoAdapter using Mockito. Tests routing logic, error handling, and protocol support checks.
    *   **Recommendation:** This is a unit test file. Your task is integration testing (end-to-end API flow). Do NOT modify this file.

*   **File:** `backend/src/test/java/com/scrumpoker/integration/sso/OidcProviderTest.java`
    *   **Summary:** Unit tests for OidcProvider. Tests JWT validation, claim extraction, expiration checks, issuer/audience validation.
    *   **Recommendation:** Reference this file to understand how to create mock JWT claims if needed, but your integration tests should mock at the SsoAdapter level, not at the JWT parsing level.

*   **File:** `backend/src/test/java/com/scrumpoker/worker/ExportJobIntegrationTest.java`
    *   **Summary:** Example integration test using `@QuarkusTest`, `@TestProfile`, Testcontainers, and `@RunOnVertxContext` with `UniAsserter`.
    *   **Recommendation:** The SsoAuthenticationIntegrationTest.java already follows this exact pattern. Reuse the same test structure: `@BeforeEach` with cleanup, `@RunOnVertxContext` with `UniAsserter`, REST Assured for API calls, Panache transactions for database assertions.

### Implementation Tips & Notes

*   **Tip:** The existing OIDC tests in SsoAuthenticationIntegrationTest.java are excellent templates. Method `testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg()` (lines 128-205) shows the complete flow: setup organization, mock SsoAdapter, call API, verify user created, verify org membership, verify audit log with 500ms delay for async processing.

*   **Note:** SAML2 is NOT implemented in the codebase yet. The task asks you to test SAML2, but since Saml2Provider doesn't exist, you MUST mock the `SsoAdapter.authenticate()` method to return a successful response for SAML2 protocol. This is acceptable because the integration test is testing the AuthController's handling of SSO callbacks, not the actual SAML2 protocol parsing.

*   **Critical:** Use `@InjectMock` on the `SsoAdapter` bean (see line 54-55 of existing test). In `@BeforeEach`, use Mockito to configure the mock's behavior: `when(ssoAdapter.authenticate(...)).thenReturn(Uni.createFrom().item(ssoUserInfo))`. This bypasses the unimplemented Saml2Provider.

*   **Pattern:** For SAML2 tests, create a helper method like `createSaml2ConfigJson()` that returns a JSON string with `"protocol": "saml2"` and appropriate SAML2 configuration fields. Reference the OIDC version at line 400-411.

*   **Audit Logging:** The existing test at lines 182-188 demonstrates that audit log creation is async. You MUST add a 500ms `Thread.sleep()` delay before querying the audit log, otherwise the async event may not have processed yet.

*   **Test Profile:** The existing test uses `SsoTestProfile` which extends `NoSecurityTestProfile` (lines 78-80). Continue using this profile for SAML2 tests to disable JWT authentication filters during testing.

*   **Assertion Pattern:** Use `UniAsserter.assertThat()` with Panache transactions for database queries (see lines 158-168, 171-179, 190-204). This ensures reactive Uni operations complete properly in the test context.

*   **SAML2 Mock File:** Create `backend/src/test/resources/sso/mock_saml_response.xml` with a sample SAML2 assertion. Reference the SAML2 specification or use an Okta/Azure AD example. However, as noted, this file is for documentation only - tests don't parse it.

*   **Warning:** Do NOT attempt to implement actual SAML2 parsing logic in the tests. The SsoAdapter does not support SAML2 yet, and implementing it is outside the scope of this task. The task is to write integration tests that VERIFY the AuthController's SSO callback endpoint works correctly when given SAML2 input, with the SsoAdapter mocked to return success.

*   **Coverage:** Add at least 3 SAML2 test methods mirroring the OIDC tests:
    1. `testSaml2SsoCallback_FirstLogin_CreatesUserAndAssignsToOrg` - Happy path for new user
    2. `testSaml2SsoCallback_ReturningUser_DoesNotDuplicateOrgMembership` - Existing user login
    3. `testSaml2SsoCallback_DomainMismatch_Returns401` - Security validation

*   **Deliverable Note:** The task specifies creating `mock_saml_response.xml`. Create this file with a realistic SAML2 assertion XML structure, but document in the README.md that it's not used directly in tests (consistent with how `mock_id_token.jwt` is documented).

*   **Validation:** After implementation, run `mvn clean verify` to ensure all tests pass. The existing 6 OIDC tests should continue to pass, and your new SAML2 tests should pass as well.

*   **Test Isolation:** Each test method in the file calls `Panache.withTransaction()` in `@BeforeEach` to delete all audit logs, org members, users, and organizations (lines 89-94). This ensures tests don't interfere with each other. Your new SAML2 tests benefit from this same cleanup.

---

## 4. Additional Context from Codebase Structure

### Project Test Structure
- Integration tests use `@QuarkusTest` annotation
- Testcontainers automatically started via Quarkus Dev Services (PostgreSQL, Redis)
- Test profiles extend `NoSecurityTestProfile` to bypass authentication filters
- REST Assured library used for HTTP endpoint testing
- Mockito `@InjectMock` used to replace CDI beans with mocks
- Reactive tests use `@RunOnVertxContext` with `UniAsserter` for async assertions

### Key Testing Patterns Observed
1. **Setup Phase:** `@BeforeEach` with `@RunOnVertxContext` cleans database and configures mocks
2. **Execution Phase:** REST Assured `given().when().then()` pattern for API calls
3. **Verification Phase:** `UniAsserter.assertThat()` with Panache queries for database verification
4. **Async Handling:** Manual `Thread.sleep()` for async operations (audit logging)

### Maven Build Configuration
- Tests run with `mvn verify` (integration test phase)
- Quarkus automatically provisions test containers
- Test resources directory: `backend/src/test/resources/`
- Test source directory: `backend/src/test/java/`

---

## 5. Critical Success Factors

1. **DO NOT create a new test file** - Extend the existing `SsoAuthenticationIntegrationTest.java`
2. **DO NOT implement SAML2Provider** - Mock the SsoAdapter to return success for SAML2
3. **DO follow the exact pattern** - Use the existing OIDC tests as templates for SAML2 tests
4. **DO add Thread.sleep(500)** - Before audit log assertions to allow async processing
5. **DO create mock_saml_response.xml** - But document it's for reference only (not parsed)
6. **DO update README.md** - Document the new SAML2 mock file and its usage
7. **DO verify all tests pass** - Run `mvn verify` to ensure no regressions

---

## 6. File Summary

**Files to Modify:**
- `backend/src/test/java/com/scrumpoker/api/rest/SsoAuthenticationIntegrationTest.java` (add SAML2 tests in TODO section, lines 377-390)
- `backend/src/test/resources/sso/README.md` (update SAML2 documentation)

**Files to Create:**
- `backend/src/test/resources/sso/mock_saml_response.xml` (SAML2 assertion XML for reference)

**Files to Read (for context):**
- `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java` (SSO callback endpoint - line 192+)
- `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java` (SSO adapter routing - lines 121-126 show SAML2 not implemented)
- `backend/src/test/java/com/scrumpoker/api/rest/SsoAuthenticationIntegrationTest.java` (existing OIDC tests lines 126-375)

---

## 7. SAML2 Configuration Structure

Based on the OIDC configuration pattern, your SAML2 configuration JSON should follow this structure:

```json
{
  "protocol": "saml2",
  "saml2": {
    "idpMetadataUrl": "https://acmecorp.okta.com/app/metadata/saml2",
    "entityId": "https://app.scrumpoker.com",
    "assertionConsumerServiceUrl": "https://app.scrumpoker.com/auth/saml/callback"
  }
}
```

This mirrors the OIDC structure and provides realistic SAML2 configuration fields for the test organization.
