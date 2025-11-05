# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I7.T8",
  "iteration_id": "I7",
  "iteration_goal": "Implement enterprise-tier features including SSO integration (OIDC/SAML2), organization management, member administration, org-level branding, and audit logging.",
  "description": "Create unit tests for `OrganizationService` with mocked `SubscriptionRepository` and `StripeAdapter`. Test scenarios: create organization (verify domain validation), add member (verify OrgMember created), remove member (verify deletion), update SSO config (verify JSONB serialization), update branding (verify JSONB persistence). Test edge cases: duplicate member addition, removing last admin (prevent), invalid domain.",
  "agent_type_hint": "BackendAgent",
  "inputs": "OrganizationService from I7.T2, Mockito testing patterns",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java"
  ],
  "target_files": [
    "backend/src/test/java/com/scrumpoker/domain/organization/OrganizationServiceTest.java"
  ],
  "deliverables": "OrganizationServiceTest with 12+ test methods, Tests for org creation, member management, SSO config, Edge case tests (duplicate member, remove last admin), JSONB serialization tests (SSO config, branding)",
  "acceptance_criteria": "`mvn test` runs organization service tests, Org creation validates email domain matches org domain, Add member creates OrgMember with correct role, Remove last admin throws exception (prevent lockout), SSO config persists to JSONB correctly, Branding config round-trips through JSONB",
  "dependencies": [
    "I7.T2"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: authentication-and-authorization (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: authentication-and-authorization -->
#### Authentication & Authorization

<!-- anchor: authentication-mechanisms -->
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

<!-- anchor: authorization-strategy -->
##### Authorization Strategy

**Role-Based Access Control (RBAC):**
- **Roles:** `ANONYMOUS`, `USER`, `PRO_USER`, `ORG_ADMIN`, `ORG_MEMBER`
- **Implementation:** Quarkus Security annotations (`@RolesAllowed`) on REST endpoints and service methods
- **JWT Claims:** Access token includes `roles` array for authorization decisions
- **Dynamic Role Mapping:** Subscription tier (`FREE`, `PRO`, `PRO_PLUS`, `ENTERPRISE`) mapped to roles during token generation

**Resource-Level Permissions:**
- **Room Access:**
  - `PUBLIC` rooms: Accessible to anyone with room ID
  - `INVITE_ONLY` rooms: Requires room owner to whitelist participant (Pro+ tier)
  - `ORG_RESTRICTED` rooms: Requires organization membership (Enterprise tier)
- **Room Operations:**
  - Host controls (reveal, reset, kick): Room creator or user with `HOST` role in `RoomParticipant`
  - Configuration updates: Room owner only
  - Vote casting: Participants with `VOTER` role (excludes `OBSERVER`)
- **Report Access:**
  - Free tier: Session summary only (no round-level detail)
  - Pro tier: Full session history with round breakdown
  - Enterprise tier: Organization-wide analytics with member filtering

**Enforcement Points:**
1. **API Gateway/Ingress:** JWT validation and signature verification
2. **REST Controllers:** Role-based annotations reject unauthorized requests with `403 Forbidden`
3. **WebSocket Handshake:** Token validation before connection upgrade
4. **Service Layer:** Domain-level checks (e.g., room privacy mode enforcement, subscription feature gating)
```

### Context: security-considerations (from 05_Operational_Architecture.md)

```markdown
<!-- anchor: security-considerations -->
#### Security Considerations

<!-- anchor: transport-security -->
##### Transport Security

- **HTTPS/TLS 1.3:** All REST API and WebSocket traffic encrypted in transit
- **Certificate Management:** AWS Certificate Manager (ACM) or Let's Encrypt with automated renewal
- **HSTS (HTTP Strict Transport Security):** `Strict-Transport-Security: max-age=31536000; includeSubDomains` header enforced
- **WebSocket Secure (WSS):** TLS-encrypted WebSocket connections (`wss://` protocol)

<!-- anchor: application-security -->
##### Application Security

**Input Validation:**
- **REST APIs:** Bean Validation (JSR-380) annotations on DTOs, automatic validation in Quarkus REST layer
- **WebSocket Messages:** Zod schema validation on client, server-side JSON schema validation before deserialization
- **SQL Injection Prevention:** Parameterized queries via Hibernate Reactive, no dynamic SQL concatenation
- **XSS Prevention:** React automatic escaping for user-generated content, CSP (Content Security Policy) headers

**Authentication Security:**
- **JWT Signature:** RS256 (RSA with SHA-256) algorithm, private key stored in Kubernetes Secret
- **Token Expiration:** Short-lived access tokens (1 hour), refresh tokens rotated on use
- **OAuth2 State Parameter:** CSRF protection for OAuth flow, state validated on callback
- **PKCE:** Protects authorization code from interception in browser-based flows

**Authorization Security:**
- **Least Privilege:** Default deny policy, explicit role grants required for resource access
- **Resource Ownership Validation:** Service layer verifies user owns/has permission for requested resource (e.g., room, report)
- **Rate Limiting:** Redis-backed token bucket algorithm:
  - Anonymous users: 10 req/min per IP
  - Authenticated users: 100 req/min per user
  - WebSocket messages: 50 msg/min per connection

**Data Protection:**
- **Encryption at Rest:** PostgreSQL Transparent Data Encryption (TDE) for sensitive columns (email, payment metadata)
- **PII Handling:** User emails hashed in logs, full values only in database and audit logs
- **Secrets Management:** Kubernetes Secrets for database credentials, OAuth client secrets, JWT signing keys
- **Payment Security:** Stripe tokenization for card details, no PCI-sensitive data stored in application database
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/api/rest/SsoAuthenticationIntegrationTest.java`
    *   **Summary:** This is a comprehensive integration test for the SSO authentication flow. It demonstrates the project's testing patterns including:
        - Use of `@QuarkusTest` with custom test profiles
        - `@RunOnVertxContext` and `UniAsserter` for reactive Panache testing
        - Mock alternatives for external dependencies (MockSsoAdapter)
        - Test setup with `@BeforeEach` for data cleanup and test org creation
        - Helper method `runInVertxContext()` for executing reactive operations from blocking test methods
        - Proper assertion patterns for audit logs with async processing delays
        - Domain validation testing (email domain matching)
    *   **Recommendation:** Use this file as your PRIMARY reference for test structure and patterns. The `runInVertxContext()` helper method pattern is CRITICAL for testing reactive Panache operations from non-reactive test methods.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
    *   **Summary:** The service under test. It contains the following key methods:
        - `createOrganization(name, domain, ownerId)` - Creates org, validates domain ownership, adds owner as ADMIN
        - `updateSsoConfig(orgId, ssoConfig)` - Updates SSO config in JSONB
        - `updateBranding(orgId, brandingConfig)` - Updates branding in JSONB
        - `addMember(orgId, userId, role)` - Adds member with specified role
        - `removeMember(orgId, userId)` - Removes member (must prevent removing last admin)
        - `getOrganization(orgId)` - Retrieves organization
        - `getUserOrganizations(userId)` - Lists user's organizations
    *   **Recommendation:** You MUST mock the injected dependencies: `OrganizationRepository`, `OrgMemberRepository`, `UserRepository`, and `FeatureGate`. Use Mockito's `@Mock` and `@InjectMocks` annotations or manual mock creation.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserService.java`
    *   **Summary:** Provides reference for reactive service testing patterns, though not directly related to OrganizationService.
    *   **Recommendation:** This can provide secondary reference for reactive Uni/Multi testing patterns if needed.

*   **File:** `backend/src/test/java/com/scrumpoker/domain/billing/BillingServiceTest.java`
    *   **Summary:** Another domain service unit test that demonstrates pure Mockito-based testing (NOT @QuarkusTest).
    *   **Recommendation:** This shows the alternative pattern of testing services with Mockito mocks without Quarkus integration. Since the task specifies unit tests (not integration tests), you SHOULD follow this pattern rather than @QuarkusTest.

### Implementation Tips & Notes

*   **Tip:** The task description mentions "mocked `SubscriptionRepository` and `StripeAdapter`" but these are NOT injected into `OrganizationService`. This appears to be a copy-paste error in the task description. The ACTUAL dependencies you need to mock are:
    - `OrganizationRepository`
    - `OrgMemberRepository`
    - `UserRepository`
    - `FeatureGate`
    - `ObjectMapper` (for JSONB serialization)

*   **Tip:** For testing JSONB serialization/deserialization, you'll need to mock the `ObjectMapper` behavior. The service uses `objectMapper.writeValueAsString()` and `objectMapper.readValue()` for SSO config and branding.

*   **Note:** The `FeatureGate.requireCanManageOrganization(user)` method is called in `createOrganization()` and will throw `FeatureNotAvailableException` if the user doesn't have Enterprise tier. You MUST test this behavior.

*   **Tip:** For the "remove last admin" edge case test, you'll need to mock `orgMemberRepository.countAdmins(orgId)` to return 1, then verify that calling `removeMember()` throws an exception.

*   **Note:** The `OrganizationService` uses `@WithTransaction` annotation on its methods. In unit tests with mocked repositories, transactions won't actually execute, so you don't need to worry about transaction management in your tests.

*   **Tip:** Use AssertJ for fluent assertions (`assertThat(...).isEqualTo(...)`) as seen in the existing test files. This is the project's standard assertion library.

*   **Note:** All reactive methods return `Uni<>` or `Multi<>` types. In unit tests with mocked repositories, you'll need to:
    1. Mock repository methods to return `Uni.createFrom().item(mockObject)` for successful cases
    2. Mock to return `Uni.createFrom().failure(new Exception(...))` for failure cases
    3. Use `.await().indefinitely()` to block and get the result in your test assertions

*   **Warning:** Do NOT use `@QuarkusTest` for this task. The task explicitly asks for UNIT tests with mocked dependencies, not integration tests. Use pure JUnit 5 with Mockito.

*   **Tip:** For testing duplicate member addition, mock `orgMemberRepository.findByOrgIdAndUserId()` to return an existing member, then verify the service throws `IllegalStateException`.

*   **Tip:** The project uses `java.util.function.Supplier<Uni<T>>` pattern in some helper methods. You may need to adapt this pattern when mocking reactive return values.

### Test Coverage Requirements

Based on the acceptance criteria, you MUST implement these test methods (minimum 12):

1. **Organization Creation Tests (3 tests):**
   - Happy path: valid domain, Enterprise tier user
   - Domain mismatch: user email domain != org domain
   - Missing Enterprise tier: throws FeatureNotAvailableException

2. **Member Management Tests (4 tests):**
   - Add member: creates OrgMember with specified role
   - Duplicate member: throws exception when member already exists
   - Remove member: successfully removes member
   - Remove last admin: throws exception to prevent lockout

3. **SSO Configuration Tests (2 tests):**
   - Update SSO config: JSONB serialization successful
   - Get organization: SSO config deserialized correctly

4. **Branding Configuration Tests (2 tests):**
   - Update branding: JSONB serialization successful
   - Get organization: branding config deserialized correctly

5. **Query Tests (1 test):**
   - Get user organizations: returns user's org memberships

This gives you 12 test methods as a minimum baseline to meet the acceptance criteria.
