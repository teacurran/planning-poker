# Project Plan: Scrum Poker Platform - Iteration 7

**Version:** 1.0
**Date:** 2025-10-17

---

<!-- anchor: iteration-7 -->
### Iteration 7: Enterprise Features (SSO & Organizations)

*   **Iteration ID:** `I7`

*   **Goal:** Implement enterprise-tier features including SSO integration (OIDC/SAML2), organization management, member administration, org-level branding, and audit logging.

*   **Prerequisites:** I3 (authentication), I5 (subscription tier enforcement)

*   **Tasks:**

<!-- anchor: task-i7-t1 -->
*   **Task 7.1: Implement SSO Adapter (OIDC & SAML2)**
    *   **Task ID:** `I7.T1`
    *   **Description:** Create `SsoAdapter` service supporting OIDC and SAML2 protocols using Quarkus Security extensions. OIDC: configure IdP discovery endpoint, handle authorization code flow, validate ID token, extract user attributes (email, name, groups). SAML2: configure IdP metadata URL, handle SAML response, validate assertions, extract attributes from assertion. Map IdP attributes to User entity fields. Support per-organization SSO configuration (stored in Organization.sso_config JSONB). Implement backchannel logout (OIDC logout endpoint, SAML SLO).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   SSO requirements from architecture blueprint
        *   Quarkus OIDC and SAML2 extension documentation
        *   Enterprise SSO patterns (Okta, Azure AD)
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/05_Operational_Architecture.md` (SSO section)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/integration/sso/SsoAdapter.java`
        *   `backend/src/main/java/com/scrumpoker/integration/sso/OidcProvider.java`
        *   `backend/src/main/java/com/scrumpoker/integration/sso/Saml2Provider.java`
        *   `backend/src/main/java/com/scrumpoker/integration/sso/SsoUserInfo.java` (DTO)
    *   **Deliverables:**
        *   SsoAdapter with OIDC and SAML2 support
        *   Organization-specific SSO configuration (IdP endpoint, certificate, attribute mapping from JSONB)
        *   User attribute extraction (email, name, groups/roles)
        *   Backchannel logout implementation
        *   SSO provider-specific implementations (Okta, Azure AD tested)
    *   **Acceptance Criteria:**
        *   OIDC authentication flow completes with test IdP (Okta sandbox)
        *   SAML2 authentication flow completes (Azure AD or SAML test IdP)
        *   User attributes correctly mapped from ID token/assertion
        *   Organization-specific SSO config loaded from database
        *   Logout endpoint invalidates SSO session
        *   Certificate validation works for SAML2
    *   **Dependencies:** []
    *   **Parallelizable:** Yes

<!-- anchor: task-i7-t2 -->
*   **Task 7.2: Implement Organization Service**
    *   **Task ID:** `I7.T2`
    *   **Description:** Create `OrganizationService` domain service managing enterprise organizations. Methods: `createOrganization(name, domain, ownerId)`, `updateSsoConfig(orgId, oidcConfig)` (store IdP settings in JSONB), `addMember(orgId, userId, role)`, `removeMember(orgId, userId)`, `updateBranding(orgId, logoUrl, primaryColor)`, `getOrganization(orgId)`, `getUserOrganizations(userId)`. Use `OrganizationRepository`, `OrgMemberRepository`. Validate domain ownership (user's email domain matches org domain). Enforce Enterprise tier requirement for org creation. Store branding config in Organization.branding JSONB.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Organization entity from I1
        *   Organization management requirements
        *   SSO config structure (IdP endpoint, client ID, certificate)
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/organization/Organization.java`
        *   `backend/src/main/java/com/scrumpoker/domain/organization/OrgMember.java`
        *   `backend/src/main/java/com/scrumpoker/repository/OrganizationRepository.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/organization/SsoConfig.java` (POJO for JSONB)
        *   `backend/src/main/java/com/scrumpoker/domain/organization/BrandingConfig.java` (POJO)
    *   **Deliverables:**
        *   OrganizationService with methods for org and member management
        *   Organization creation with domain validation
        *   SSO configuration storage (OIDC/SAML2 settings in JSONB)
        *   Member add/remove with role assignment (ADMIN, MEMBER)
        *   Branding config storage (logo URL, colors)
        *   Enterprise tier enforcement for org features
    *   **Acceptance Criteria:**
        *   Creating organization validates domain (user email matches domain)
        *   SSO config persists to Organization.sso_config correctly
        *   Adding member creates OrgMember record with role
        *   Removing member soft-deletes membership (or hard delete based on design)
        *   Branding config JSON serializes correctly
        *   Non-Enterprise tier cannot create organization (403)
    *   **Dependencies:** [I5.T4]
    *   **Parallelizable:** Yes (can work parallel with I7.T1)

<!-- anchor: task-i7-t3 -->
*   **Task 7.3: Implement Audit Logging Service**
    *   **Task ID:** `I7.T3`
    *   **Description:** Create `AuditLogService` recording security and administrative events for Enterprise compliance. Methods: `logEvent(orgId, userId, action, resourceType, resourceId, ipAddress, userAgent)`. Events to log: user authentication (SSO login), organization config changes (SSO settings updated), member management (user added/removed/role changed), room deletion, sensitive data access. Use `AuditLogRepository`. Async event publishing (fire-and-forget via CDI event or Redis Stream for performance). Store event in partitioned AuditLog table (partition by month). Include contextual data (IP address, user agent, timestamp, change details JSONB).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Audit logging requirements from architecture blueprint
        *   AuditLog entity from I1
        *   Events requiring audit trail
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/organization/AuditLog.java`
        *   `backend/src/main/java/com/scrumpoker/repository/AuditLogRepository.java`
        *   `.codemachine/artifacts/architecture/05_Operational_Architecture.md` (audit logging section)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/organization/AuditLogService.java`
        *   `backend/src/main/java/com/scrumpoker/event/AuditEvent.java` (CDI event)
    *   **Deliverables:**
        *   AuditLogService with logEvent method
        *   Async audit event processing (CDI @ObservesAsync or Redis)
        *   Audit events for: SSO login, org config change, member add/remove, room delete
        *   IP address and user agent extraction from HTTP request context
        *   Change details stored in JSONB (before/after values for config changes)
        *   Integration in OrganizationService (log after member add/remove)
    *   **Acceptance Criteria:**
        *   SSO login creates audit log entry
        *   Organization config change logged with before/after values
        *   Member add event includes user ID and assigned role
        *   Audit log query by orgId returns events sorted by timestamp
        *   Async processing doesn't block main request thread
        *   IP address and user agent correctly captured
    *   **Dependencies:** []
    *   **Parallelizable:** Yes (can work parallel with other tasks)

<!-- anchor: task-i7-t4 -->
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

<!-- anchor: task-i7-t5 -->
*   **Task 7.5: Create Organization REST Controllers**
    *   **Task ID:** `I7.T5`
    *   **Description:** Implement REST endpoints for organization management per OpenAPI spec. Endpoints: `POST /api/v1/organizations` (create org, Enterprise tier only), `GET /api/v1/organizations/{orgId}` (get org details), `PUT /api/v1/organizations/{orgId}/sso` (configure SSO settings, admin only), `POST /api/v1/organizations/{orgId}/members` (invite member, admin only), `DELETE /api/v1/organizations/{orgId}/members/{userId}` (remove member, admin only), `GET /api/v1/organizations/{orgId}/audit-logs` (query audit trail, admin only). Use `OrganizationService`, `AuditLogService`. Enforce admin role checks (only org admins can modify org).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   OpenAPI spec for organization endpoints from I2.T1
        *   OrganizationService from I7.T2
    *   **Input Files:**
        *   `api/openapi.yaml` (organization endpoints)
        *   `backend/src/main/java/com/scrumpoker/domain/organization/OrganizationService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/rest/OrganizationController.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/OrganizationDTO.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/SsoConfigRequest.java`
        *   `backend/src/main/java/com/scrumpoker/api/rest/dto/InviteMemberRequest.java`
    *   **Deliverables:**
        *   OrganizationController with 6 endpoints
        *   DTOs for organization and SSO config
        *   Admin role enforcement (only admins can update SSO, manage members)
        *   Audit log query endpoint with pagination
        *   Enterprise tier enforcement for org creation
    *   **Acceptance Criteria:**
        *   POST /organizations creates org (Enterprise tier only)
        *   PUT /sso updates SSO configuration (admin only, 403 for members)
        *   POST /members invites user to org
        *   DELETE /members removes user from org
        *   GET /audit-logs returns paginated audit events
        *   Non-admin requests to admin endpoints return 403
    *   **Dependencies:** [I7.T2, I7.T3]
    *   **Parallelizable:** No (depends on services)

<!-- anchor: task-i7-t6 -->
*   **Task 7.6: Create Frontend Organization Management Pages**
    *   **Task ID:** `I7.T6`
    *   **Description:** Implement React components for organization administration (Enterprise tier). `OrganizationSettingsPage`: display org name, domain, member count, branding preview, "Configure SSO" button. `SsoConfigPage`: form for OIDC settings (IdP URL, client ID, client secret) or SAML2 settings (IdP metadata URL, certificate upload), test SSO button. `MemberManagementPage`: table listing org members (name, email, role), "Invite Member" button, role change dropdown, remove button. `AuditLogPage`: audit event table (timestamp, user, action, resource, IP), date range filter, pagination. Conditional rendering based on user's org admin role.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   Organization management requirements
        *   OpenAPI spec for organization endpoints
        *   Enterprise tier UI patterns
    *   **Input Files:**
        *   `api/openapi.yaml` (organization endpoints)
    *   **Target Files:**
        *   `frontend/src/pages/org/OrganizationSettingsPage.tsx`
        *   `frontend/src/pages/org/SsoConfigPage.tsx`
        *   `frontend/src/pages/org/MemberManagementPage.tsx`
        *   `frontend/src/pages/org/AuditLogPage.tsx`
        *   `frontend/src/components/org/MemberTable.tsx`
        *   `frontend/src/services/organizationApi.ts` (React Query hooks)
    *   **Deliverables:**
        *   OrganizationSettingsPage showing org details
        *   SsoConfigPage with OIDC/SAML2 configuration form
        *   MemberManagementPage with member table and invite/remove actions
        *   AuditLogPage with filterable event table
        *   React Query hooks for organization API calls
        *   Admin-only access (redirect non-admins to 403 page)
    *   **Acceptance Criteria:**
        *   OrganizationSettingsPage displays org name and member count
        *   SsoConfigPage form submits to /organizations/{id}/sso endpoint
        *   SSO test button validates configuration
        *   MemberManagementPage lists current members
        *   Invite member opens modal, calls POST /members
        *   Remove member confirms action, calls DELETE /members/{userId}
        *   AuditLogPage displays events with timestamp, action, user
        *   Non-admin users cannot access org admin pages (403 or redirect)
    *   **Dependencies:** [I7.T5]
    *   **Parallelizable:** No (depends on API)

<!-- anchor: task-i7-t7 -->
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

<!-- anchor: task-i7-t8 -->
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

---

**Iteration 7 Summary:**

*   **Deliverables:**
    *   SSO integration (OIDC and SAML2 support)
    *   OrganizationService with member and config management
    *   Audit logging for security and compliance events
    *   SSO authentication flow with JIT provisioning
    *   Organization management REST API endpoints
    *   Frontend org admin pages (settings, SSO, members, audit logs)
    *   Integration and unit tests for SSO and organizations

*   **Acceptance Criteria (Iteration-Level):**
    *   Enterprise customers can configure SSO (OIDC or SAML2)
    *   Users authenticate via SSO IdP successfully
    *   JIT provisioning creates users and assigns to organizations
    *   Org admins can manage members (invite, remove, change roles)
    *   Audit logs track security and administrative events
    *   Frontend admin UI allows org configuration
    *   Tests verify SSO flows and org management logic

*   **Estimated Duration:** 3 weeks
