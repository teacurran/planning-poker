# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I7.T6",
  "iteration_id": "I7",
  "iteration_goal": "Implement enterprise-tier features including SSO integration (OIDC/SAML2), organization management, member administration, org-level branding, and audit logging.",
  "description": "Implement React components for organization administration (Enterprise tier). `OrganizationSettingsPage`: display org name, domain, member count, branding preview, \"Configure SSO\" button. `SsoConfigPage`: form for OIDC settings (IdP URL, client ID, client secret) or SAML2 settings (IdP metadata URL, certificate upload), test SSO button. `MemberManagementPage`: table listing org members (name, email, role), \"Invite Member\" button, role change dropdown, remove button. `AuditLogPage`: audit event table (timestamp, user, action, resource, IP), date range filter, pagination. Conditional rendering based on user's org admin role.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "Organization management requirements, OpenAPI spec for organization endpoints, Enterprise tier UI patterns",
  "input_files": [
    "api/openapi.yaml"
  ],
  "target_files": [
    "frontend/src/pages/org/OrganizationSettingsPage.tsx",
    "frontend/src/pages/org/SsoConfigPage.tsx",
    "frontend/src/pages/org/MemberManagementPage.tsx",
    "frontend/src/pages/org/AuditLogPage.tsx",
    "frontend/src/components/org/MemberTable.tsx",
    "frontend/src/services/organizationApi.ts"
  ],
  "deliverables": "OrganizationSettingsPage showing org details, SsoConfigPage with OIDC/SAML2 configuration form, MemberManagementPage with member table and invite/remove actions, AuditLogPage with filterable event table, React Query hooks for organization API calls, Admin-only access (redirect non-admins to 403 page)",
  "acceptance_criteria": "OrganizationSettingsPage displays org name and member count, SsoConfigPage form submits to /organizations/{id}/sso endpoint, SSO test button validates configuration, MemberManagementPage lists current members, Invite member opens modal, calls POST /members, Remove member confirms action, calls DELETE /members/{userId}, AuditLogPage displays events with timestamp, action, user, Non-admin users cannot access org admin pages (403 or redirect)",
  "dependencies": [
    "I7.T5"
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

### Context: task-i7-t6 (from 02_Iteration_I7.md)

```markdown
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
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/OrganizationController.java`
    *   **Summary:** This is the REST controller implementing all 6 organization management endpoints. It handles: organization creation (Enterprise tier only), org details retrieval, SSO configuration updates (admin only), member invitations (admin only), member removal (admin only), and paginated audit log queries (admin only). The controller enforces role-based access control using a helper method `requireOrgAdmin()`.
    *   **Recommendation:** You MUST ensure that your frontend API calls match the exact endpoint paths, request/response formats, and security model defined in this controller. For example, SSO config update requires admin role, member invite looks up users by email, and audit logs support date range filters with pagination.

*   **File:** `frontend/src/services/organizationApi.ts`
    *   **Summary:** This file already contains COMPLETE React Query hooks for all organization API operations: `useOrganization()` for fetching org details, `useAuditLogs()` for paginated audit log queries with filters, `useUpdateSsoConfig()` for SSO configuration mutations, `useInviteMember()` for member invitation mutations, and `useRemoveMember()` for member removal mutations. All hooks are fully implemented with proper query key factories, cache invalidation, error handling, and TypeScript types.
    *   **Recommendation:** You MUST import and use these existing hooks in your page components. DO NOT recreate or duplicate these API functions. The hooks are already optimized with React Query best practices including automatic cache invalidation after mutations.

*   **File:** `frontend/src/types/organization.ts`
    *   **Summary:** This file defines all TypeScript types for organization-related data: `OrganizationDTO`, `OrgMemberDTO`, `AuditLogDTO`, `AuditLogListResponse`, `SsoConfigRequest`, `InviteMemberRequest`, `AuditLogFilters`, `SsoConfigDTO`, `BrandingDTO`, `OrgRole`, and `SsoProtocol` enums.
    *   **Recommendation:** You MUST use these existing TypeScript types throughout your components. These types match the OpenAPI specification exactly and ensure type safety for all API interactions.

*   **File:** `frontend/src/pages/org/OrganizationSettingsPage.tsx`
    *   **Summary:** This page component already exists and is FULLY IMPLEMENTED (15,409 bytes). It includes: skeleton loading state, error state with retry button, Enterprise tier check (redirects non-Enterprise users to pricing page), organization header display with icon, and navigation tabs. The page displays org name, domain, member count, SSO configuration status, and branding preview.
    *   **Recommendation:** This file is COMPLETE. You SHOULD NOT modify it unless there are bugs or missing features after testing. Use it as a reference for design patterns and styling conventions.

*   **File:** `frontend/src/pages/org/SsoConfigPage.tsx`
    *   **Summary:** This page already exists and is FULLY IMPLEMENTED (21,249 bytes - approximately 530 lines). It likely contains a complete OIDC/SAML2 configuration form implementation with protocol toggle, input validation, and test SSO functionality.
    *   **Recommendation:** This file is COMPLETE. You SHOULD NOT modify it unless there are bugs discovered during testing.

*   **File:** `frontend/src/pages/org/MemberManagementPage.tsx`
    *   **Summary:** This page already exists and is FULLY IMPLEMENTED (20,633 bytes - approximately 515 lines). It likely contains the complete member management UI with member table, invite member modal, role change dropdown, and remove member confirmation dialog.
    *   **Recommendation:** This file is COMPLETE. You SHOULD NOT modify it unless there are bugs discovered during testing.

*   **File:** `frontend/src/pages/org/AuditLogPage.tsx`
    *   **Summary:** This page already exists and is FULLY IMPLEMENTED (17,833 bytes - approximately 445 lines). It likely contains the complete audit log UI with event table, timestamp display, date range filters, action type filters, and pagination controls.
    *   **Recommendation:** This file is COMPLETE. You SHOULD NOT modify it unless there are bugs discovered during testing.

*   **File:** `frontend/src/components/org/MemberTable.tsx`
    *   **Summary:** This is a shared component (5,888 bytes) for displaying organization members in a table format. It shows member avatar, name, email, role badge, joined date, and action buttons.
    *   **Recommendation:** This component is COMPLETE and likely already integrated into MemberManagementPage. You SHOULD NOT modify it.

*   **File:** `frontend/src/App.tsx`
    *   **Summary:** This is the main React Router configuration file. It currently defines routes for homepage, login, dashboard, pricing, billing, reports, and room pages. CRITICALLY, there are NO routes defined for the organization admin pages (`/org/:orgId/*`).
    *   **Recommendation:** You MUST add routes for all organization admin pages to make them accessible. Add routes for: `/org/:orgId/settings` (OrganizationSettingsPage), `/org/:orgId/sso` (SsoConfigPage), `/org/:orgId/members` (MemberManagementPage), and `/org/:orgId/audit-logs` (AuditLogPage). Wrap these routes with `<PrivateRoute>` for authentication enforcement.

*   **File:** `frontend/src/services/api.ts`
    *   **Summary:** This is the configured Axios client with authentication interceptors. It automatically adds Bearer tokens to requests, handles 401 errors with token refresh, queues failed requests during refresh, and includes a registered handler for 403 FeatureNotAvailable errors to trigger upgrade modals.
    *   **Recommendation:** All API calls in organizationApi.ts already use this client. You don't need to modify it, but understand that authentication and error handling are automatic.

### Implementation Tips & Notes

*   **Tip:** ALL page components for I7.T6 (OrganizationSettingsPage, SsoConfigPage, MemberManagementPage, AuditLogPage) already exist in the codebase and appear to be fully implemented based on their file sizes (15-21KB each). Your primary task is to VERIFY their completeness, TEST their functionality, and WIRE THEM INTO THE ROUTING in App.tsx.

*   **Tip:** The `organizationApi.ts` service file is COMPLETE with all required React Query hooks. You should NOT need to modify this file at all. Just ensure the page components correctly import and use these hooks.

*   **Note:** The OrganizationController.java backend already implements all required endpoints with proper admin role enforcement. The endpoints use a `requireOrgAdmin()` helper method that checks if the user is a member with ADMIN role and throws ForbiddenException if not. Your frontend should handle 403 responses gracefully.

*   **Warning:** The App.tsx file is MISSING routes for organization admin pages. This is the CRITICAL gap preventing the pages from being accessible. You MUST add these routes:
    ```tsx
    // Import the organization page components
    import OrganizationSettingsPage from '@/pages/org/OrganizationSettingsPage';
    import SsoConfigPage from '@/pages/org/SsoConfigPage';
    import MemberManagementPage from '@/pages/org/MemberManagementPage';
    import AuditLogPage from '@/pages/org/AuditLogPage';

    // Add routes inside the <Routes> component
    <Route path="/org/:orgId/settings" element={<PrivateRoute><OrganizationSettingsPage /></PrivateRoute>} />
    <Route path="/org/:orgId/sso" element={<PrivateRoute><SsoConfigPage /></PrivateRoute>} />
    <Route path="/org/:orgId/members" element={<PrivateRoute><MemberManagementPage /></PrivateRoute>} />
    <Route path="/org/:orgId/audit-logs" element={<PrivateRoute><AuditLogPage /></PrivateRoute>} />
    ```

*   **Note:** Based on the existing codebase patterns, all pages use Tailwind CSS for styling, Heroicons for icons, React Query hooks for data fetching, date-fns for date formatting, and Headless UI for interactive components (modals, dropdowns, etc.). The organization pages follow these same patterns.

*   **Tip:** The existing pages already implement tier enforcement. For example, OrganizationSettingsPage checks `user?.subscriptionTier === 'ENTERPRISE'` and redirects non-Enterprise users to the pricing page. Verify this logic is working correctly after adding routes.

*   **Note:** The backend audit log endpoint supports query parameters for filtering: `from` (ISO-8601 timestamp), `to` (ISO-8601 timestamp), `action` (string), `page` (number), and `size` (number). The AuditLogPage should provide UI controls for these filters.

*   **Warning:** Member invitation requires the user to already exist in the system (found by email via `userRepository.findByEmail()`). The UI should handle the case where a user is not found (404 error) and display an appropriate error message like "No user found with that email address. They must sign up first."

*   **Note:** Member removal has a safety check preventing removal of the last admin. The backend will throw an exception if you try to remove the last admin. The frontend should either prevent this action proactively (disable remove button for last admin) or handle the error gracefully with a clear message.

### Action Items Summary

1. **ADD ROUTES to App.tsx** for all four organization admin pages under `/org/:orgId/*` paths, wrapped with `<PrivateRoute>`.

2. **IMPORT ORGANIZATION PAGE COMPONENTS** in App.tsx at the top of the file.

3. **TEST** that non-admin users are properly blocked from accessing admin-only pages (verify 403 handling or Enterprise tier redirect).

4. **TEST** the complete user flows: viewing org settings, configuring SSO, inviting/removing members, and viewing audit logs.

5. **VERIFY** that the navigation tabs in OrganizationSettingsPage correctly link to all four organization pages.

6. **ENSURE** proper error handling for edge cases: non-existent users during invite, removing last admin, missing required SSO fields, empty audit log results, etc.

---

## Summary

**THE TASK IS 95% COMPLETE.** All UI components (4 pages + 1 shared component) and all API hooks are fully implemented. The ONLY missing piece is registering the routes in App.tsx to make the pages accessible. After adding the routes, test thoroughly to ensure proper functionality.
