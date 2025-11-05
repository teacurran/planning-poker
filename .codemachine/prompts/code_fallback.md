# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create unit tests for `OrganizationService` with mocked `SubscriptionRepository` and `StripeAdapter`. Test scenarios: create organization (verify domain validation), add member (verify OrgMember created), remove member (verify deletion), update SSO config (verify JSONB serialization), update branding (verify JSONB persistence). Test edge cases: duplicate member addition, removing last admin (prevent), invalid domain.

---

## Issues Detected

*   **Missing Implementation:** The test file `backend/src/test/java/com/scrumpoker/domain/organization/OrganizationServiceTest.java` has NOT been created yet. The task requires a complete unit test suite with 12+ test methods.

*   **Directory Missing:** The directory `backend/src/test/java/com/scrumpoker/domain/organization/` does not exist and needs to be created.

*   **No Code Generated:** There is no generated code to verify. The task requires implementing a comprehensive unit test suite from scratch.

---

## Best Approach to Fix

You MUST create the file `backend/src/test/java/com/scrumpoker/domain/organization/OrganizationServiceTest.java` with a complete unit test suite following these requirements:

### 1. Test Structure Requirements

**Use JUnit 5 + Mockito (NOT @QuarkusTest):**
- This is a UNIT test, not an integration test
- Use `@ExtendWith(MockitoExtension.class)` for Mockito support
- Use `@Mock` for dependencies: `OrganizationRepository`, `OrgMemberRepository`, `UserRepository`, `FeatureGate`, `ObjectMapper`
- Use `@InjectMocks` for the `OrganizationService` under test
- Use `@BeforeEach` to reset mocks and set up common test data

**Reference Pattern:**
Follow the pattern from `backend/src/test/java/com/scrumpoker/domain/billing/BillingServiceTest.java` which demonstrates pure Mockito-based unit testing without Quarkus integration.

### 2. Required Test Methods (Minimum 12)

**Organization Creation Tests (3 tests):**
1. `testCreateOrganization_Success_ValidDomainAndEnterpriseTier()` - Mock FeatureGate to allow, verify org created with owner as ADMIN
2. `testCreateOrganization_Failure_EmailDomainMismatch()` - User email `user@different.com` creating org with domain `company.com` should fail
3. `testCreateOrganization_Failure_MissingEnterpriseTier()` - Mock FeatureGate to throw `FeatureNotAvailableException`

**Member Management Tests (4 tests):**
4. `testAddMember_Success_CreatesOrgMemberWithRole()` - Verify OrgMember created with specified role (e.g., MEMBER)
5. `testAddMember_Failure_DuplicateMember()` - Mock `orgMemberRepository.findByOrgIdAndUserId()` to return existing member, verify throws exception
6. `testRemoveMember_Success_RemovesMember()` - Mock member exists, verify deletion called
7. `testRemoveMember_Failure_RemoveLastAdmin()` - Mock `orgMemberRepository.countAdmins(orgId)` to return 1, verify throws exception preventing lockout

**SSO Configuration Tests (2 tests):**
8. `testUpdateSsoConfig_Success_SerializesToJsonb()` - Mock ObjectMapper to verify `writeValueAsString()` called with SSO config
9. `testGetOrganization_Success_DeserializesSsoConfig()` - Mock org with SSO config JSON, verify ObjectMapper deserializes correctly

**Branding Configuration Tests (2 tests):**
10. `testUpdateBranding_Success_SerializesToJsonb()` - Mock ObjectMapper to verify branding persisted as JSON
11. `testGetOrganization_Success_DeserializesBranding()` - Mock org with branding JSON, verify deserialization

**Query Tests (1 test):**
12. `testGetUserOrganizations_Success_ReturnsUserOrgs()` - Mock `orgMemberRepository.findByUserId()` to return list of memberships

### 3. Reactive Mocking Patterns

Since `OrganizationService` uses Quarkus Hibernate Reactive and returns `Uni<>` types, you MUST mock repository methods to return Uni instances:

**Success Case:**
```java
when(organizationRepository.persist(any(Organization.class)))
    .thenReturn(Uni.createFrom().item(mockOrg));
```

**Failure Case:**
```java
when(featureGate.requireCanManageOrganization(any(User.class)))
    .thenReturn(Uni.createFrom().failure(new FeatureNotAvailableException("Enterprise required")));
```

**Blocking for Assertions:**
Use `.await().indefinitely()` to block and get the result:
```java
Organization result = organizationService.createOrganization(name, domain, ownerId)
    .await().indefinitely();
assertThat(result).isNotNull();
```

**Failure Assertions:**
```java
assertThatThrownBy(() ->
    organizationService.removeMember(orgId, lastAdminUserId).await().indefinitely()
).isInstanceOf(IllegalStateException.class)
 .hasMessageContaining("Cannot remove last admin");
```

### 4. JSONB Serialization Testing

Mock the `ObjectMapper` behavior for SSO config and branding:

```java
@Mock
private ObjectMapper objectMapper;

// In test method:
SsoConfig ssoConfig = new SsoConfig(/* ... */);
when(objectMapper.writeValueAsString(ssoConfig))
    .thenReturn("{\"protocol\":\"oidc\",\"issuer\":\"https://idp.example.com\"}");
```

### 5. Critical Implementation Notes

*   **IMPORTANT:** The task description mentions "mocked `SubscriptionRepository` and `StripeAdapter`" but these are NOT injected into `OrganizationService`. Ignore this - it's a copy-paste error. Mock the ACTUAL dependencies listed in section 1.

*   **Domain Validation:** The `createOrganization()` method validates that the owner's email domain matches the organization domain. Test both success (matching domains) and failure (mismatched domains).

*   **Transaction Annotations:** The service uses `@WithTransaction` but in unit tests with mocked repositories, transactions won't execute. You don't need to mock transaction behavior.

*   **AssertJ Assertions:** Use AssertJ fluent assertions (`assertThat(...).isEqualTo(...)`) as this is the project standard.

*   **Feature Gate:** The `FeatureGate.requireCanManageOrganization()` checks if the user has Enterprise tier. Mock this to return `Uni.createFrom().voidItem()` for success, or `Uni.createFrom().failure(exception)` for failure.

### 6. Example Test Method Structure

```java
@Test
void testCreateOrganization_Success_ValidDomainAndEnterpriseTier() {
    // Arrange
    String name = "Acme Corp";
    String domain = "acme.com";
    Long ownerId = 1L;

    User owner = new User();
    owner.setId(ownerId);
    owner.setEmail("john@acme.com");

    Organization mockOrg = new Organization();
    mockOrg.setId(100L);
    mockOrg.setName(name);
    mockOrg.setDomain(domain);

    when(userRepository.findById(ownerId))
        .thenReturn(Uni.createFrom().item(owner));
    when(featureGate.requireCanManageOrganization(owner))
        .thenReturn(Uni.createFrom().voidItem());
    when(organizationRepository.persist(any(Organization.class)))
        .thenReturn(Uni.createFrom().item(mockOrg));
    when(orgMemberRepository.persist(any(OrgMember.class)))
        .thenReturn(Uni.createFrom().item(new OrgMember()));

    // Act
    Organization result = organizationService.createOrganization(name, domain, ownerId)
        .await().indefinitely();

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(name);
    assertThat(result.getDomain()).isEqualTo(domain);
    verify(organizationRepository).persist(any(Organization.class));
    verify(orgMemberRepository).persist(argThat(member ->
        member.getRole() == OrgRole.ADMIN &&
        member.getUserId().equals(ownerId)
    ));
}
```

### 7. Acceptance Criteria Checklist

Ensure your test suite verifies ALL of these:
- [ ] Org creation validates email domain matches org domain
- [ ] Add member creates OrgMember with correct role
- [ ] Remove last admin throws exception (prevent lockout)
- [ ] SSO config persists to JSONB correctly (mock ObjectMapper)
- [ ] Branding config round-trips through JSONB (mock ObjectMapper)
- [ ] Duplicate member addition throws exception
- [ ] Missing Enterprise tier throws FeatureNotAvailableException
- [ ] All tests pass with `mvn test -Dtest=OrganizationServiceTest`

---

## Additional Requirements

*   Create the directory structure if it doesn't exist: `backend/src/test/java/com/scrumpoker/domain/organization/`
*   Use proper package declaration: `package com.scrumpoker.domain.organization;`
*   Import all necessary classes (JUnit 5, Mockito, AssertJ, Uni, domain entities)
*   Add Javadoc comments for the test class explaining its purpose
*   Use descriptive test method names following the pattern: `test<MethodName>_<Scenario>_<ExpectedOutcome>()`
