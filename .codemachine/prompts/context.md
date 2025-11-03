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
    "backend/src/main/java/com/scrumpoker/integration/sso/SsoConfig.java",
    "backend/src/main/java/com/scrumpoker/domain/organization/BrandingConfig.java"
  ],
  "deliverables": "OrganizationService with methods for org and member management, Organization creation with domain validation, SSO configuration storage (OIDC/SAML2 settings in JSONB), Member add/remove with role assignment (ADMIN, MEMBER), Branding config storage (logo URL, colors), Enterprise tier enforcement for org features",
  "acceptance_criteria": "Creating organization validates domain (user email matches domain), SSO config persists to Organization.sso_config correctly, Adding member creates OrgMember record with role, Removing member soft-deletes membership (or hard delete based on design), Branding config JSON serializes correctly, Non-Enterprise tier cannot create organization (403)",
  "dependencies": ["I5.T4"],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Enterprise SSO Authentication (from 05_Operational_Architecture.md)

```markdown
**Enterprise SSO (Enterprise Tier):**
- **Protocols:** OIDC (OpenID Connect) and SAML2 support via Quarkus Security extensions
- **Configuration:** Per-organization SSO settings stored in `Organization.sso_config` JSONB field (IdP endpoint, certificate, attribute mapping)
- **Domain Enforcement:** Email domain verification ensures users with `@company.com` email automatically join organization workspace
- **Just-In-Time (JIT) Provisioning:** User accounts created on first SSO login with organization membership pre-assigned
- **Session Management:** SSO sessions synchronized with IdP via backchannel logout or session validation
```

### Context: Organization Role-Based Access Control (from 05_Operational_Architecture.md)

```markdown
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
```

### Context: Organization Entity Data Model (from 03_System_Structure_and_Data.md)

```markdown
| **Organization** | Enterprise SSO workspace | `org_id` (PK), `name`, `domain`, `sso_config` (JSONB: OIDC/SAML2 settings), `branding` (JSONB), `subscription_id` (FK) |
| **OrgMember** | User-organization membership | `org_id` (FK), `user_id` (FK), `role` (ADMIN/MEMBER), `joined_at` |
```

### Context: Audit Logging for Organizations (from 05_Operational_Architecture.md)

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

### Context: Organization Management Component (from 03_System_Structure_and_Data.md)

```markdown
Component(org_service, "Organization Service", "Domain Logic", "Org creation, SSO config, admin controls, member management")
Component(org_repository, "Organization Repository", "Panache Repository", "Organization, OrgMember, SSOConfig persistence")
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### ⚠️ CRITICAL DISCOVERY: OrganizationService Already Fully Implemented!

**File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
  - **Summary:** The OrganizationService is **ALREADY COMPLETE** with all required methods fully implemented (344 lines).
  - **Methods Present:**
    - ✅ `createOrganization(name, domain, ownerId)` - with domain validation and Enterprise tier enforcement
    - ✅ `updateSsoConfig(orgId, ssoConfig)` - with JSON serialization using Jackson ObjectMapper
    - ✅ `addMember(orgId, userId, role)` - with duplicate member prevention
    - ✅ `removeMember(orgId, userId)` - with "last admin" protection
    - ✅ `updateBranding(orgId, logoUrl, primaryColor, secondaryColor)` - with JSON serialization
    - ✅ `getOrganization(orgId)` - simple retrieval
    - ✅ `getUserOrganizations(userId)` - returns Multi stream
  - **Key Features Implemented:**
    - Domain validation (email domain extraction and matching)
    - Enterprise tier enforcement via FeatureGate injection
    - JSONB serialization/deserialization using Jackson ObjectMapper
    - Transactional integrity with `@WithTransaction`
    - Reactive Mutiny patterns (Uni/Multi)
    - Proper exception handling
  - **Recommendation:** ⚠️ **THIS TASK IS ALREADY COMPLETE!** You should verify the implementation meets all acceptance criteria, then mark the task as done. DO NOT reimplement this service.

### Relevant Existing Code

**File:** `backend/src/main/java/com/scrumpoker/domain/organization/Organization.java`
  - **Summary:** Entity class for Organization with JSONB fields for SSO config and branding.
  - **Key Fields:**
    - `orgId` (UUID primary key)
    - `name` (VARCHAR 255, not null)
    - `domain` (VARCHAR 255, not null, unique constraint)
    - `ssoConfig` (JSONB column - stored as String, serialized by application code)
    - `branding` (JSONB column - stored as String)
    - `subscription` (ManyToOne relationship to Subscription entity)
    - `createdAt` / `updatedAt` (auto-timestamped)
  - **Recommendation:** This entity is correctly structured. Use it as-is.

**File:** `backend/src/main/java/com/scrumpoker/domain/organization/OrgMember.java`
  - **Summary:** Many-to-many relationship entity between User and Organization.
  - **Key Fields:**
    - Composite primary key using `@EmbeddedId` (OrgMemberId containing orgId + userId)
    - `organization` and `user` relationships using `@MapsId`
    - `role` (enum: ADMIN or MEMBER)
    - `joinedAt` timestamp
  - **Recommendation:** This entity uses composite key pattern correctly. When creating/deleting members, always use the composite key.

**File:** `backend/src/main/java/com/scrumpoker/domain/organization/BrandingConfig.java`
  - **Summary:** POJO for branding configuration with Jackson annotations.
  - **Key Fields:**
    - `logoUrl` (String, @JsonProperty("logo_url"))
    - `primaryColor` (String, @JsonProperty("primary_color"))
    - `secondaryColor` (String, @JsonProperty("secondary_color"))
  - **Recommendation:** This class is already created and used by OrganizationService. It follows Jackson serialization patterns.

**File:** `backend/src/main/java/com/scrumpoker/integration/sso/SsoConfig.java`
  - **Summary:** POJO for SSO configuration supporting both OIDC and SAML2 protocols.
  - **Key Fields:**
    - `protocol` (String: "oidc" or "saml2")
    - `oidc` (OidcConfig object, optional)
    - `saml2` (Saml2Config object, optional)
    - `domainVerificationRequired` (boolean, default true)
    - `jitProvisioningEnabled` (boolean, default true)
  - **Recommendation:** This class is already created. OrganizationService.updateSsoConfig() accepts it as a parameter and serializes it to JSON using Jackson ObjectMapper.

**File:** `backend/src/main/java/com/scrumpoker/repository/OrganizationRepository.java`
  - **Summary:** Panache repository for Organization entity with custom finder methods.
  - **Methods Available:**
    - `findByDomain(String domain)` - for SSO domain verification
    - `findBySubscriptionId(UUID subscriptionId)` - for subscription queries
    - `searchByName(String namePattern)` - case-insensitive search
    - `countAll()` - total count
  - **Recommendation:** Use this repository via dependency injection. All reactive methods return Uni/Multi.

**File:** `backend/src/main/java/com/scrumpoker/repository/OrgMemberRepository.java`
  - **Summary:** Panache repository for OrgMember entity with composite key operations.
  - **Methods Available:**
    - `findByOrgId(UUID orgId)` - all members of an org
    - `findByUserId(UUID userId)` - all orgs for a user
    - `findByOrgIdAndRole(UUID orgId, OrgRole role)` - filter by role
    - `findByOrgIdAndUserId(UUID orgId, UUID userId)` - get specific membership
    - `isAdmin(UUID orgId, UUID userId)` - boolean check
    - `countByOrgId(UUID orgId)` - member count
  - **Recommendation:** OrganizationService already uses these methods correctly, especially the composite key pattern.

**File:** `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
  - **Summary:** Service for enforcing subscription tier-based feature access.
  - **Key Methods:**
    - `hasSufficientTier(User user, SubscriptionTier requiredTier)` - base tier check using ordinal comparison
    - `canManageOrganization(User user)` - checks for ENTERPRISE tier
    - `requireCanManageOrganization(User user)` - throws FeatureNotAvailableException if not ENTERPRISE
  - **Recommendation:** OrganizationService already injects and uses this correctly in createOrganization() method.

### Implementation Tips & Notes

- **Tip:** The OrganizationService is already fully implemented and appears to meet all acceptance criteria. Your PRIMARY action should be to **VERIFY** the implementation rather than code.

- **Note:** The implementation uses Jackson ObjectMapper for JSONB serialization. This is injected via `@Inject ObjectMapper objectMapper` and used in both `updateSsoConfig()` and `updateBranding()` methods with proper exception handling for JsonProcessingException.

- **Note:** Domain validation is implemented via the private helper method `extractEmailDomain(String email)` which splits on '@' and returns the domain part. This is used in `createOrganization()` to validate the owner's email domain matches the organization domain.

- **Note:** The "last admin protection" in `removeMember()` uses a count query to ensure at least one admin remains before allowing deletion. This is a solid defensive implementation preventing organization lockout.

- **Note:** The service follows reactive patterns consistently - all public methods return `Uni<>` for single results or `Multi<>` for streams. Methods use `@WithTransaction` for transactional integrity.

- **Warning:** The task description mentions "soft-deletes membership" but the implementation uses **hard delete** via `orgMemberRepository.delete(member)`. This is likely correct since the OrgMember entity does not have a `deleted_at` field. The task may have outdated information.

- **Tip:** Since task I7.T2 is already complete, you should update the task JSON to mark `"done": true`. The next actionable task will likely be I7.T3 (AuditLogService) or I7.T4 (SSO Authentication Flow).

### Verification Checklist

Before marking this task as complete, verify the following acceptance criteria are met:

1. ✅ Creating organization validates domain (user email matches domain) - **VERIFIED** in line 77-85 of OrganizationService.java
2. ✅ SSO config persists to Organization.sso_config correctly - **VERIFIED** in updateSsoConfig() method with Jackson serialization
3. ✅ Adding member creates OrgMember record with role - **VERIFIED** in addMember() method lines 164-209
4. ✅ Removing member deletes membership - **VERIFIED** in removeMember() method (uses hard delete, not soft delete)
5. ✅ Branding config JSON serializes correctly - **VERIFIED** in updateBranding() method lines 274-300
6. ✅ Non-Enterprise tier cannot create organization - **VERIFIED** via FeatureGate enforcement on line 74

### Final Recommendation

**DO NOT WRITE NEW CODE.** Instead:

1. Review the existing OrganizationService.java implementation (344 lines)
2. Verify it meets all 6 acceptance criteria (checklist above)
3. Check that all dependencies are properly injected (OrganizationRepository, OrgMemberRepository, UserRepository, FeatureGate, ObjectMapper)
4. Confirm the reactive patterns are correct (Uni/Multi return types)
5. Mark task I7.T2 as `"done": true` in the task tracking system
6. Inform the user that this task was already completed in a previous session

The implementation is production-ready and follows all architectural patterns correctly.
