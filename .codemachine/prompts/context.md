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
  "description": "Create unit tests for `OrganizationService` with mocked repositories. Test scenarios: create organization (verify domain validation), add member (verify OrgMember created), remove member (verify deletion), update SSO config (verify JSONB serialization), update branding (verify JSONB persistence). Test edge cases: duplicate member addition, removing last admin (prevent), invalid domain.",
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
#### Authentication & Authorization

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
- **Configuration:** Per-organization SSO settings stored in `Organization.ssoConfig` JSONB field (IdP endpoint, certificate, attribute mapping)
- **Domain Enforcement:** Email domain verification ensures users with `@company.com` email automatically join organization workspace
- **Just-In-Time (JIT) Provisioning:** User accounts created on first SSO login with organization membership pre-assigned
- **Session Management:** SSO sessions synchronized with IdP via backchannel logout or session validation
```

### Context: unit-testing (from 03_Verification_and_Glossary.md)

```markdown
#### Unit Testing

**Scope:** Individual classes and methods in isolation (services, utilities, validators)

**Framework:** JUnit 5 (backend), Jest/Vitest (frontend)

**Coverage Target:** >90% code coverage for service layer, >80% for overall codebase

**Approach:**
- Mock external dependencies (repositories, adapters, external services) using Mockito
- Test business logic thoroughly (happy paths, edge cases, error scenarios)
- Fast execution (<5 minutes for entire unit test suite)
- Run on every developer commit and in CI pipeline

**Examples:**
- `RoomServiceTest`: Tests room creation with unique ID generation, config validation, soft delete
- `VotingServiceTest`: Tests vote casting, consensus calculation with known inputs
- `BillingServiceTest`: Tests subscription tier transitions, Stripe integration mocking

**Acceptance Criteria:**
- All unit tests pass (`mvn test`, `npm run test:unit`)
- Coverage reports meet targets (verify with JaCoCo, Istanbul)
- No flaky tests (consistent results across runs)
```

### Context: task-i7-t8 (from 02_Iteration_I7.md)

```markdown
*   **Task 7.8: Write Unit Tests for Organization Service**
    *   **Task ID:** `I7.T8`
    *   **Description:** Create unit tests for `OrganizationService` with mocked repositories. Test scenarios: create organization (verify domain validation), add member (verify OrgMember created), remove member (verify deletion), update SSO config (verify JSONB serialization), update branding (verify JSONB persistence). Test edge cases: duplicate member addition, removing last admin (prevent), invalid domain.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   OrganizationService from I7.T2
        *   Mockito testing patterns
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/domain/organization/OrganizationServiceTest.java`
    *   **Deliverables:**
        *   OrganizationServiceTest with 12+ test methods
        *   Tests for org creation, member management, SSO config
        *   Edge case tests (duplicate member, remove last admin)
        *   JSONB serialization tests (SSO config, branding)
    *   **Acceptance Criteria:**
        *   `mvn test` runs organization service tests
        *   Org creation validates email domain matches org domain
        *   Add member creates OrgMember with correct role
        *   Remove last admin throws exception (prevent lockout)
        *   SSO config persists to JSONB correctly
        *   Branding config round-trips through JSONB
    *   **Dependencies:** [I7.T2]
    *   **Parallelizable:** Yes (can work parallel with integration tests)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/domain/organization/OrganizationServiceTest.java`
    *   **Summary:** This file ALREADY EXISTS and contains a comprehensive test suite for OrganizationService with 24 test methods covering all major functionality.
    *   **Current State:** The test file is complete and fully implemented with:
        - Organization creation tests (4 tests: success, domain mismatch, missing enterprise tier, user not found)
        - Member management tests (9 tests: add member success, duplicate member, org not found, user not found, remove member success, remove last admin prevention, remove admin when multiple exist, member not found)
        - SSO configuration tests (3 tests: success with JSON serialization, org not found, JSON serialization failure)
        - Branding configuration tests (3 tests: success with JSON serialization, org not found, JSON serialization failure)
        - Query tests (3 tests: get organization success, not found returns null, get user organizations)
    *   **Recommendation:** The task appears to be ALREADY COMPLETED. You should verify that all 24 test methods are present and that they meet the acceptance criteria.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
    *   **Summary:** This is the service being tested. It contains 8 public methods for organization management:
        - `createOrganization(name, domain, ownerId)` - Creates org with domain validation and Enterprise tier enforcement
        - `updateSsoConfig(orgId, ssoConfig)` - Updates SSO config with JSON serialization
        - `addMember(orgId, userId, role)` - Adds member with duplicate prevention
        - `removeMember(orgId, userId)` - Removes member with last admin protection
        - `updateBranding(orgId, logoUrl, primaryColor, secondaryColor)` - Updates branding with JSON serialization
        - `getOrganization(orgId)` - Retrieves organization by ID
        - `getUserOrganizations(userId)` - Gets all orgs for a user
        - `extractEmailDomain(email)` - Private helper for domain extraction
    *   **Dependencies:** OrganizationRepository, OrgMemberRepository, UserRepository, FeatureGate, ObjectMapper
    *   **Recommendation:** All methods in the service are already covered by the existing test suite.

*   **File:** `backend/src/test/java/com/scrumpoker/domain/billing/BillingServiceTest.java`
    *   **Summary:** Example test file showing the project's testing patterns and conventions.
    *   **Testing Pattern:** Uses `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`, `@BeforeEach` for setup
    *   **Recommendation:** The OrganizationServiceTest already follows these same patterns consistently.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/BrandingConfig.java`
    *   **Summary:** POJO for branding configuration with Jackson JSON annotations (@JsonProperty).
    *   **Fields:** logoUrl, primaryColor, secondaryColor
    *   **Recommendation:** The test suite already includes tests for JSON serialization of BrandingConfig objects.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoConfig.java`
    *   **Summary:** POJO for SSO configuration with support for OIDC protocol and Jackson JSON annotations.
    *   **Fields:** protocol, oidc (OidcConfig), domainVerificationRequired, jitProvisioningEnabled
    *   **Recommendation:** The test suite already includes tests for JSON serialization of SsoConfig objects using mocked ObjectMapper.

*   **File:** `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
    *   **Summary:** Service for enforcing tier-based feature access control with hierarchical tier system (FREE < PRO < PRO_PLUS < ENTERPRISE).
    *   **Key Method:** `requireCanManageOrganization(User)` - Throws FeatureNotAvailableException if user doesn't have Enterprise tier
    *   **Recommendation:** The test suite already mocks FeatureGate appropriately, using `doNothing()` for success cases and `doThrow()` for tier enforcement failures.

### Implementation Tips & Notes

*   **Task Status:** The OrganizationServiceTest.java file is ALREADY COMPLETE and contains 710 lines with 24 comprehensive test methods. The task appears to have been completed in a previous session.

*   **Test Coverage:** The existing test suite exceeds the requirement of "12+ test methods" by covering:
    - ✅ Organization creation with domain validation (4 tests including edge cases)
    - ✅ Member management (9 tests including duplicate prevention and last admin protection)
    - ✅ SSO config updates with JSONB serialization (3 tests)
    - ✅ Branding updates with JSONB serialization (3 tests)
    - ✅ Query methods (3 tests)
    - ✅ All edge cases mentioned in requirements (duplicate member, last admin, invalid domain)

*   **Testing Patterns Used:**
    - Pure unit testing with Mockito (no Quarkus integration)
    - `@ExtendWith(MockitoExtension.class)` for JUnit 5 integration
    - `@Mock` for dependencies, `@InjectMocks` for service under test
    - `@BeforeEach` setup method creating test fixtures
    - AssertJ for fluent assertions (`assertThat()`, `assertThatThrownBy()`)
    - ArgumentCaptor for verifying method arguments passed to mocks
    - Reactive testing pattern: `.await().indefinitely()` for Uni/Multi unwrapping

*   **Key Test Patterns to Note:**
    - **Mocking repositories:** Uses `when().thenReturn(Uni.createFrom().item())` for successful cases
    - **Mocking failures:** Uses `Uni.createFrom().nullItem()` for not found cases
    - **Testing exceptions:** Uses `assertThatThrownBy(() -> ...).isInstanceOf().hasMessageContaining()`
    - **Verifying persistence:** Uses ArgumentCaptor to capture entities passed to `.persist()` methods
    - **Testing JSONB:** Mocks ObjectMapper with `when(objectMapper.writeValueAsString()).thenReturn(expectedJson)`
    - **Testing reactive code:** All Uni results are unwrapped with `.await().indefinitely()`

*   **Important Edge Cases Covered:**
    - Domain mismatch between user email and organization domain
    - Missing Enterprise tier when creating organization
    - Duplicate member addition prevention
    - Last admin removal prevention (prevents organizational lockout)
    - JSON serialization failures for SSO config and branding
    - Organization/user not found scenarios

*   **What You Should Do:**
    1. **Verify Completeness:** Run `mvn test -Dtest=OrganizationServiceTest` to ensure all tests pass
    2. **Check Coverage:** Use `mvn test jacoco:report` to verify >90% coverage for OrganizationService
    3. **Review Test Quality:** Ensure all acceptance criteria are met:
        - ✅ Org creation validates email domain matches org domain (line 152-175)
        - ✅ Add member creates OrgMember with correct role (line 231-265)
        - ✅ Remove last admin throws exception (line 363-385)
        - ✅ SSO config persists to JSONB correctly (line 435-470)
        - ✅ Branding config round-trips through JSONB (line 517-553)
    4. **Mark Task as Done:** If all tests pass and coverage is sufficient, the task is complete

*   **Potential Issues to Check:**
    - Ensure all imports are present (especially Jackson JsonProcessingException)
    - Verify test method naming follows convention: `test<Method>_<Scenario>_<ExpectedResult>`
    - Check that all `verify()` calls have corresponding assertions
    - Ensure mock setup in @BeforeEach doesn't interfere with individual tests

*   **Git History Context:** Recent commits show the OrganizationService was implemented and polished recently:
    - `3fd4a68`: "refactor(org): polish OrganizationService docs and formatting"
    - `5d2c988`: "feat(org): add OrganizationService and branding JSON model"
    - The test file was likely created in the same iteration

---

## Final Assessment

**The task I7.T8 appears to be ALREADY COMPLETED.** The test file exists with 24 comprehensive test methods covering all required scenarios and edge cases. The test suite follows project conventions, uses proper Mockito patterns, and includes thorough assertions.

**Recommended Action:**
1. Run the tests to verify they all pass: `mvn test -Dtest=OrganizationServiceTest`
2. Check code coverage: `mvn test jacoco:report`
3. If tests pass with >90% coverage, mark the task as done: `"done": true`
4. If any tests fail or coverage is insufficient, review and fix the failing tests

**No new code should be written unless:**
- Tests are failing and need fixes
- Coverage is below the 90% threshold and additional tests are needed
- A specific test case from the acceptance criteria is missing (though review shows all are present)
