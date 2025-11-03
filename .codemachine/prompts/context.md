# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I7.T2",
  "iteration_id": "I7",
  "iteration_goal": "Implement enterprise-tier features including SSO integration (OIDC/SAML2), organization management, member administration, org-level branding, and audit logging.",
  "description": "Create `OrganizationService` domain service managing enterprise organizations. Methods: `createOrganization(name, domain, ownerId)`, `updateSsoConfig(orgId, oidcConfig)` (store IdP settings in JSONB), `addMember(orgId, userId, role)`, `removeMember(orgId, userId)`, `updateBranding(orgId, logoUrl, primaryColor)`, `getOrganization(orgId)`, `getUserOrganizations(userId)`. Use `OrganizationRepository`, `OrgMemberRepository`. Validate domain ownership (user's email domain matches org domain). Enforce Enterprise tier requirement for org creation. Store branding config in Organization.branding JSONB.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Organization entity from I1, Organization management requirements, SSO config structure (IdP endpoint, client ID, certificate)",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/organization/Organization.java",
    "backend/src/main/java/com/scrumpoker/domain/organization/OrgMember.java",
    "backend/src/main/java/com/scrumpoker/repository/OrganizationRepository.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java",
    "backend/src/main/java/com/scrumpoker/domain/organization/SsoConfig.java",
    "backend/src/main/java/com/scrumpoker/domain/organization/BrandingConfig.java"
  ],
  "deliverables": "OrganizationService with methods for org and member management, Organization creation with domain validation, SSO configuration storage (OIDC/SAML2 settings in JSONB), Member add/remove with role assignment (ADMIN, MEMBER), Branding config storage (logo URL, colors), Enterprise tier enforcement for org features",
  "acceptance_criteria": "Creating organization validates domain (user email matches domain), SSO config persists to Organization.ssoConfig correctly, Adding member creates OrgMember record with role, Removing member soft-deletes membership (or hard delete based on design), Branding config JSON serializes correctly, Non-Enterprise tier cannot create organization (403)",
  "dependencies": [
    "I5.T4"
  ],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: enterprise-requirements (from 01_Context_and_Drivers.md)

```markdown
#### Enterprise Requirements
- **SSO Integration:** OIDC and SAML2 protocol support for identity federation
- **Organization Management:** Workspace creation, custom branding, org-wide defaults
- **Role-Based Access:** Admin/member roles with configurable permissions
- **Audit Logging:** Comprehensive event tracking for compliance and security monitoring
```

### Context: authentication-mechanisms (from 05_Operational_Architecture.md)

```markdown
##### Authentication Mechanisms

**Enterprise SSO (Enterprise Tier):**
- **Protocols:** OIDC (OpenID Connect) and SAML2 support via Quarkus Security extensions
- **Configuration:** Per-organization SSO settings stored in `Organization.sso_config` JSONB field (IdP endpoint, certificate, attribute mapping)
- **Domain Enforcement:** Email domain verification ensures users with `@company.com` email automatically join organization workspace
- **Just-In-Time (JIT) Provisioning:** User accounts created on first SSO login with organization membership pre-assigned
- **Session Management:** SSO sessions synchronized with IdP via backchannel logout or session validation
```

### Context: authorization-strategy (from 05_Operational_Architecture.md)

```markdown
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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/Organization.java`
    *   **Summary:** This entity defines the Organization table with fields for name, domain, ssoConfig (JSONB), branding (JSONB), and subscription relationship. The SSO and branding configs are stored as JSON strings that need to be serialized/deserialized by application code.
    *   **Recommendation:** You MUST use this entity in OrganizationService. The `ssoConfig` and `branding` fields are String types containing JSON - you'll need to serialize SsoConfig and BrandingConfig POJOs to JSON strings before persisting, and deserialize when reading.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
    *   **Summary:** This is the existing SSO adapter that handles both OIDC and SAML2 authentication. It already demonstrates how to parse SsoConfig from JSON (lines 223-243). It uses Jackson ObjectMapper for JSON serialization.
    *   **Recommendation:** You SHOULD follow the same pattern for JSON serialization/deserialization that SsoAdapter uses. The SsoConfig, OidcConfig, and Saml2Config classes already exist and are being used - you need to create matching POJO classes for your service and reuse these existing configuration structures.

*   **File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoConfig.java` (and related config classes)
    *   **Summary:** These POJOs define the structure for SSO configuration including protocol selection, OIDC settings, and SAML2 settings.
    *   **Recommendation:** Your `updateSsoConfig` method should accept these existing POJO structures, serialize them to JSON, and store in `Organization.ssoConfig`. DO NOT create duplicate configuration structures in `com.scrumpoker.domain.organization` package. The target file `SsoConfig.java` mentioned in the task description refers to REUSING the existing `com.scrumpoker.integration.sso.SsoConfig` class, not creating a new one.

*   **File:** `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
    *   **Summary:** This service provides tier-based feature access control. It has methods like `canManageOrganization(User)` (line 237) which checks for ENTERPRISE tier, and `requireCanManageOrganization(User)` (line 250) which throws FeatureNotAvailableException if the user lacks sufficient tier.
    *   **Recommendation:** You MUST inject FeatureGate into OrganizationService and use `requireCanManageOrganization(user)` in your `createOrganization` method to enforce Enterprise tier requirement. This will throw a FeatureNotAvailableException (403) if the user is not Enterprise tier.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrgMember.java`
    *   **Summary:** This entity represents the many-to-many relationship between users and organizations, including the role field.
    *   **Recommendation:** You MUST use this entity for the `addMember` and `removeMember` operations. Set the appropriate OrgRole when adding members.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/OrganizationRepository.java`
    *   **Summary:** Panache repository for Organization entities providing reactive database access.
    *   **Recommendation:** You MUST inject this repository for all Organization CRUD operations. Use reactive return types (Uni<>, Multi<>).

*   **File:** `backend/src/main/java/com/scrumpoker/repository/OrgMemberRepository.java`
    *   **Summary:** Panache repository for OrgMember entities.
    *   **Recommendation:** You MUST inject this repository for member management operations.

*   **File:** `backend/src/test/java/com/scrumpoker/integration/sso/SsoAdapterTest.java`
    *   **Summary:** This test file shows examples of how SsoConfig JSON is structured (lines 367-391) and how ObjectMapper is used for deserialization.
    *   **Recommendation:** You SHOULD reference these JSON examples when implementing your SSO config storage and retrieval logic.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** The User entity with email field and subscriptionTier field.
    *   **Recommendation:** You MUST retrieve the User entity in `createOrganization` to validate the email domain matches the organization domain, and to check the subscription tier for Enterprise enforcement.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
    *   **Summary:** Panache repository for User entities with reactive access.
    *   **Recommendation:** You MUST inject this repository to look up users by ID when validating domain ownership and adding members.

### Implementation Tips & Notes

*   **Tip:** I have confirmed that the project uses **Jackson ObjectMapper** for JSON serialization. You SHOULD inject ObjectMapper in your service (via `@Inject`) and use it for converting SsoConfig and BrandingConfig POJOs to/from JSON strings.

*   **Tip:** The domain validation requirement states "user's email domain matches org domain". You SHOULD extract the domain from the user's email (everything after the '@' symbol) and compare it with the organization's domain field. For example, if creating org with domain "company.com", only users with emails like "user@company.com" should be allowed to create it.

*   **Note:** The acceptance criteria mentions "soft-deletes membership (or hard delete based on design)". Looking at the OrgMember entity, there is NO `deleted_at` column, suggesting this should be a **hard delete** operation. You SHOULD use `orgMemberRepository.delete(orgMember)` rather than setting a soft delete flag.

*   **Note:** The Organization entity already has a `subscription` relationship (ManyToOne to Subscription). However, tier enforcement should be checked against the **owner User's subscriptionTier**, not the organization's subscription field. The organization subscription field appears to be for organizational-level subscriptions.

*   **Warning:** The SsoConfig POJO classes already exist in the `integration.sso` package. DO NOT create duplicate classes in the `domain.organization` package. You SHOULD import and reuse `com.scrumpoker.integration.sso.SsoConfig` and related classes. The task description's mention of creating `SsoConfig.java` in target files is misleading - you should NOT create a new file, but reuse the existing one.

*   **Warning:** Make sure to use **reactive return types** (`Uni<>`, `Multi<>`) for all service methods since the repositories use Hibernate Reactive. The acceptance criteria and existing patterns show all operations should be non-blocking.

*   **Tip:** For the BrandingConfig POJO, create a simple class with fields like `logoUrl`, `primaryColor`, `secondaryColor`. Keep it in the `domain.organization` package since it's domain-specific (unlike SsoConfig which is integration-focused). This is the ONE new file you need to create.

*   **Tip:** When implementing `getUserOrganizations(userId)`, you'll need to query OrgMember by userId first, then fetch the related Organization entities. Consider using a join query or Multi operations to avoid N+1 queries.

*   **Convention:** Based on the existing codebase pattern (e.g., RoomService, UserService, BillingService), services are marked as `@ApplicationScoped` CDI beans with dependencies injected via `@Inject`. Follow this pattern consistently.

*   **Convention:** Existing services use method-level `@Transactional` annotations. You SHOULD add `@Transactional` to methods that modify data (createOrganization, updateSsoConfig, addMember, removeMember, updateBranding).

*   **Convention:** The project uses Mutiny reactive types. For single results use `Uni<T>`, for multiple results use `Multi<T>`. The `await().indefinitely()` pattern is used in tests but NOT in service implementations.

*   **Tip:** For error handling, create domain-specific exceptions if needed, or reuse existing exceptions. For domain validation failures (email domain mismatch), you could throw `IllegalArgumentException` with a descriptive message. For tier enforcement, FeatureGate already throws `FeatureNotAvailableException`.

*   **Tip:** When implementing `updateSsoConfig`, the method should accept the raw SsoConfig POJO (from `com.scrumpoker.integration.sso` package), serialize it to JSON using ObjectMapper, and update the Organization entity. The same pattern applies to `updateBranding` with BrandingConfig.

---

## 4. Additional Context

### Key Design Decisions

1. **JSONB Storage**: SSO configuration and branding are stored as JSONB in PostgreSQL, serialized as JSON strings in Java. This provides flexibility for complex nested structures without requiring multiple tables.

2. **Domain Validation**: The domain field in Organization is used for email-based auto-assignment during SSO JIT provisioning (see authentication-mechanisms context). Your service should enforce that only users with matching email domains can create organizations with that domain.

3. **Tier Enforcement**: Enterprise features are gated at the service layer using FeatureGate, not at the REST controller layer. This ensures business logic is protected regardless of the entry point.

4. **Role Hierarchy**: OrgRole enum defines ADMIN and MEMBER roles. Admins have full control over organization settings and member management.

5. **Reuse SSO Config Classes**: The SsoConfig, OidcConfig, and Saml2Config classes in the `integration.sso` package are shared between SsoAdapter and OrganizationService. DO NOT create duplicates.

### Missing Dependencies

Note that task I7.T2 depends on task I5.T4 (FeatureGate implementation), which is already marked as done in the project. You can safely use FeatureGate as documented.

### Testing Strategy

When you implement OrganizationService, you'll need corresponding unit tests (task I7.T8) that will:
- Mock OrganizationRepository and OrgMemberRepository
- Mock FeatureGate for tier enforcement tests
- Test JSON serialization/deserialization of SsoConfig and BrandingConfig
- Verify domain validation logic
- Test member add/remove operations
- Test edge cases (duplicate member addition, removing last admin)

However, for THIS task (I7.T2), you are only implementing the service itself, not the tests. Tests will be created in task I7.T8.

### Expected Method Signatures

Based on the task description and reactive patterns in the codebase, your OrganizationService should have methods with these signatures:

```java
@ApplicationScoped
public class OrganizationService {

    Uni<Organization> createOrganization(String name, String domain, UUID ownerId);

    Uni<Organization> updateSsoConfig(UUID orgId, SsoConfig ssoConfig);

    Uni<OrgMember> addMember(UUID orgId, UUID userId, OrgRole role);

    Uni<Void> removeMember(UUID orgId, UUID userId);

    Uni<Organization> updateBranding(UUID orgId, String logoUrl, String primaryColor, String secondaryColor);

    Uni<Organization> getOrganization(UUID orgId);

    Multi<Organization> getUserOrganizations(UUID userId);
}
```

Note: These signatures use reactive types (Uni/Multi) and should return the entity objects or Void for delete operations.
