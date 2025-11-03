# Code Refinement Task

The previous code submission did not pass verification. Task I7.T2 was NOT implemented - none of the required deliverables were created.

---

## Original Task Description

**Task ID:** I7.T2

**Description:** Create `OrganizationService` domain service managing enterprise organizations. Methods: `createOrganization(name, domain, ownerId)`, `updateSsoConfig(orgId, oidcConfig)` (store IdP settings in JSONB), `addMember(orgId, userId, role)`, `removeMember(orgId, userId)`, `updateBranding(orgId, logoUrl, primaryColor)`, `getOrganization(orgId)`, `getUserOrganizations(userId)`. Use `OrganizationRepository`, `OrgMemberRepository`. Validate domain ownership (user's email domain matches org domain). Enforce Enterprise tier requirement for org creation. Store branding config in Organization.branding JSONB.

**Acceptance Criteria:**
- Creating organization validates domain (user email matches domain)
- SSO config persists to Organization.ssoConfig correctly
- Adding member creates OrgMember record with role
- Removing member soft-deletes membership (or hard delete based on design)
- Branding config JSON serializes correctly
- Non-Enterprise tier cannot create organization (403)

---

## Issues Detected

### 1. Missing Implementation - Task I7.T2 Not Started

**Critical Issue:** The task I7.T2 has NOT been implemented at all. None of the target files exist:

**Missing Files:**
- `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java` - DOES NOT EXIST
- `backend/src/main/java/com/scrumpoker/domain/organization/BrandingConfig.java` - DOES NOT EXIST

**Note:** The target file `SsoConfig.java` mentioned in the task description should NOT be created because it already exists in `backend/src/main/java/com/scrumpoker/integration/sso/SsoConfig.java` and should be reused, not duplicated.

### 2. Compilation Errors from Previous Task

There are compilation errors in the SSO integration code (task I7.T1) that are preventing the project from compiling. These errors are in `Saml2Provider.java` and are related to missing OpenSAML classes. However, these are NOT part of task I7.T2 and should be fixed separately.

For task I7.T2, you MUST ensure your code compiles even if the SSO integration has issues.

---

## Best Approach to Fix

You MUST implement task I7.T2 from scratch. Create the following files and follow the implementation guidance below:

### Step 1: Create BrandingConfig POJO

**File:** `backend/src/main/java/com/scrumpoker/domain/organization/BrandingConfig.java`

Create a simple POJO class to represent branding configuration that will be serialized to JSON and stored in `Organization.branding` field.

**Requirements:**
- Package: `com.scrumpoker.domain.organization`
- Fields: `String logoUrl`, `String primaryColor`, `String secondaryColor`
- Include Jackson annotations for JSON serialization: `@JsonProperty`
- Include standard getters, setters, no-args constructor, and all-args constructor
- Add `@JsonIgnoreProperties(ignoreUnknown = true)` to handle schema evolution
- Make it a simple data class - no business logic

**Example structure:**
```java
package com.scrumpoker.domain.organization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BrandingConfig {
    @JsonProperty("logo_url")
    private String logoUrl;

    @JsonProperty("primary_color")
    private String primaryColor;

    @JsonProperty("secondary_color")
    private String secondaryColor;

    // constructors, getters, setters
}
```

### Step 2: Create OrganizationService

**File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`

Create the domain service with the following specifications:

**Service Setup:**
- Annotate with `@ApplicationScoped` (CDI bean)
- Inject dependencies via `@Inject`:
  - `OrganizationRepository organizationRepository`
  - `OrgMemberRepository orgMemberRepository`
  - `UserRepository userRepository`
  - `FeatureGate featureGate`
  - `ObjectMapper objectMapper` (for JSON serialization)

**Method 1: createOrganization(String name, String domain, UUID ownerId)**

**Signature:** `public Uni<Organization> createOrganization(String name, String domain, UUID ownerId)`

**Implementation Steps:**
1. Use reactive chain: `userRepository.findById(ownerId)`
2. Validate user exists (throw `IllegalArgumentException` if null)
3. Call `featureGate.requireCanManageOrganization(user)` to enforce Enterprise tier (throws `FeatureNotAvailableException` if not Enterprise)
4. Extract email domain from user's email (everything after '@')
5. Validate that extracted domain matches the `domain` parameter (throw `IllegalArgumentException("User email domain does not match organization domain")` if mismatch)
6. Create new `Organization` entity:
   - Set `name`, `domain`
   - Set `ssoConfig` to null initially
   - Set `branding` to null initially
   - Set `createdAt` to `Instant.now()`
7. Persist organization: `organizationRepository.persist(organization)`
8. Chain to create OrgMember for owner:
   - Create `OrgMember` with composite ID (`OrgMemberId`)
   - Set role to `OrgRole.ADMIN`
   - Set `joinedAt` to `Instant.now()`
9. Persist member: `orgMemberRepository.persist(orgMember)`
10. Return the organization
11. Annotate method with `@Transactional`

**Method 2: updateSsoConfig(UUID orgId, SsoConfig ssoConfig)**

**Signature:** `public Uni<Organization> updateSsoConfig(UUID orgId, SsoConfig ssoConfig)`

**Implementation Steps:**
1. Import `com.scrumpoker.integration.sso.SsoConfig` (DO NOT create a new SsoConfig class)
2. Use `organizationRepository.findById(orgId)`
3. Validate organization exists
4. Serialize `ssoConfig` to JSON string using `objectMapper.writeValueAsString(ssoConfig)`
5. Set `organization.setSsoConfig(jsonString)`
6. Persist updated organization
7. Return organization
8. Annotate with `@Transactional`
9. Wrap ObjectMapper exceptions in `RuntimeException` or custom exception

**Method 3: addMember(UUID orgId, UUID userId, OrgRole role)**

**Signature:** `public Uni<OrgMember> addMember(UUID orgId, UUID userId, OrgRole role)`

**Implementation Steps:**
1. Validate organization exists
2. Validate user exists
3. Check if member already exists (query `orgMemberRepository` by composite ID)
4. If member exists, throw `IllegalStateException("User is already a member of this organization")`
5. Create new `OrgMember`:
   - Use `OrgMemberId` composite key with `orgId` and `userId`
   - Set `role`
   - Set `joinedAt` to `Instant.now()`
6. Persist member
7. Return member
8. Annotate with `@Transactional`

**Method 4: removeMember(UUID orgId, UUID userId)**

**Signature:** `public Uni<Void> removeMember(UUID orgId, UUID userId)`

**Implementation Steps:**
1. Create composite ID: `new OrgMemberId(orgId, userId)`
2. Use `orgMemberRepository.findById(compositeId)`
3. Validate member exists
4. Check if this is the last admin:
   - Query count of ADMIN members in organization: `orgMemberRepository.count("id.orgId = ?1 and role = ?2", orgId, OrgRole.ADMIN)`
   - If count is 1 and the member being removed is ADMIN, throw `IllegalStateException("Cannot remove the last admin from organization")`
5. Hard delete the member: `orgMemberRepository.delete(member)`
6. Return `Uni.createFrom().voidItem()`
7. Annotate with `@Transactional`

**Note on soft vs hard delete:** The `OrgMember` entity does NOT have a `deletedAt` field, so use hard delete.

**Method 5: updateBranding(UUID orgId, String logoUrl, String primaryColor, String secondaryColor)**

**Signature:** `public Uni<Organization> updateBranding(UUID orgId, String logoUrl, String primaryColor, String secondaryColor)`

**Implementation Steps:**
1. Use `organizationRepository.findById(orgId)`
2. Validate organization exists
3. Create `BrandingConfig` object with the provided values
4. Serialize to JSON using `objectMapper.writeValueAsString(brandingConfig)`
5. Set `organization.setBranding(jsonString)`
6. Persist updated organization
7. Return organization
8. Annotate with `@Transactional`

**Method 6: getOrganization(UUID orgId)**

**Signature:** `public Uni<Organization> getOrganization(UUID orgId)`

**Implementation Steps:**
1. Simple lookup: `organizationRepository.findById(orgId)`
2. Return the result (Uni<Organization>, can be null if not found)

**Method 7: getUserOrganizations(UUID userId)**

**Signature:** `public Multi<Organization> getUserOrganizations(UUID userId)`

**Implementation Steps:**
1. Query org members: `orgMemberRepository.list("id.userId", userId)`
2. Use `Multi.createFrom().iterable(members)`
3. Chain with `flatMap` to fetch each organization:
   - For each member, fetch organization: `organizationRepository.findById(member.getId().getOrgId())`
4. Return `Multi<Organization>`

**Alternative approach using join query:**
```java
return orgMemberRepository.find(
    "SELECT m.organization FROM OrgMember m WHERE m.id.userId = ?1", userId
).stream();
```

### Step 3: Testing and Validation

After implementing the files:

1. **Compilation check:** Run `mvn clean compile -DskipTests` to verify the code compiles
2. **Verify dependencies:** Ensure all imports are correct:
   - SsoConfig imported from `com.scrumpoker.integration.sso` package
   - ObjectMapper imported from `com.fasterxml.jackson.databind`
   - Reactive types (Uni, Multi) imported from `io.smallrye.mutiny`
   - Transactional annotation from `jakarta.transaction`
3. **Do NOT run tests yet** - unit tests will be created in task I7.T8

### Critical Requirements Summary

**DO:**
- Use reactive types (`Uni<>`, `Multi<>`) for all method return types
- Use `@Transactional` on methods that modify data
- Use `@ApplicationScoped` on the service class
- Import `SsoConfig` from `com.scrumpoker.integration.sso` package (existing class)
- Use `ObjectMapper` for JSON serialization/deserialization
- Use `FeatureGate.requireCanManageOrganization(user)` for Enterprise tier enforcement
- Validate domain ownership (email domain matches org domain)
- Prevent removal of last admin
- Check for duplicate members before adding

**DO NOT:**
- Create a new `SsoConfig` class in `domain.organization` package (reuse the existing one from `integration.sso`)
- Use blocking code (avoid `.await().indefinitely()` in service methods)
- Use soft deletes for OrgMember (use hard delete since there's no `deletedAt` field)
- Allow creation of organization if user is not Enterprise tier
- Allow adding duplicate members

### Error Handling

- Domain validation failures: throw `IllegalArgumentException` with descriptive message
- Entity not found: return null in Uni/Multi (let controller handle 404)
- Feature gate violations: let `FeatureNotAvailableException` propagate (handled by exception mapper)
- JSON serialization errors: wrap in `RuntimeException` or create custom `JsonProcessingException` wrapper

---

## Additional Context

The task context document provides detailed implementation guidance including:
- Existing file locations and their usage patterns
- JSON serialization examples from SsoAdapter
- FeatureGate usage for tier enforcement
- Repository patterns and reactive operations
- Domain validation logic

Refer to the implementation tips in the context document for specific code patterns and conventions used in this codebase.

---

## Expected Deliverables

After implementing this task, the following files MUST exist and compile:

1. `backend/src/main/java/com/scrumpoker/domain/organization/BrandingConfig.java` (NEW)
2. `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java` (NEW)

The code MUST compile successfully with `mvn clean compile -DskipTests`.
