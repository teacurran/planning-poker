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
  "dependencies": ["I7.T5"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: enterprise-requirements (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: enterprise-requirements -->
#### Enterprise Requirements
- **SSO Integration:** OIDC and SAML2 protocol support for identity federation
- **Organization Management:** Workspace creation, custom branding, org-wide defaults
- **Role-Based Access:** Admin/member roles with configurable permissions
- **Audit Logging:** Comprehensive event tracking for compliance and security monitoring
```

### Context: authentication-mechanisms (from 05_Operational_Architecture.md)

```markdown
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
```

### Context: authorization-strategy (from 05_Operational_Architecture.md)

```markdown
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

### Context: data-model-overview-erd (from 03_System_Structure_and_Data.md)

```markdown
<!-- anchor: data-model-overview-erd -->
### 3.6. Data Model Overview & ERD

#### Description

The data model follows a relational schema leveraging PostgreSQL's ACID properties for transactional consistency and JSONB columns for flexible configuration storage (room settings, deck definitions). The model is optimized for both transactional writes (vote casting, room creation) and analytical reads (session history, organizational reporting).

**Design Principles:**
1. **Normalized Core Entities:** Users, Rooms, Organizations follow 3NF to prevent update anomalies
2. **Denormalized Read Models:** SessionSummary and VoteStatistics tables precompute aggregations for reporting performance
3. **JSONB for Flexibility:** RoomConfig, DeckDefinition, UserPreferences stored as JSONB to support customization without schema migrations
4. **Soft Deletes:** Critical entities (Users, Rooms) use `deleted_at` timestamp for audit trail and GDPR compliance
5. **Partitioning Strategy:** SessionHistory and AuditLog partitioned by month for query performance and data lifecycle management

#### Key Entities

| Entity | Purpose | Key Attributes |
|--------|---------|----------------|
| **User** | Registered user account | `user_id` (PK), `email`, `oauth_provider`, `oauth_subject`, `display_name`, `avatar_url`, `subscription_tier`, `created_at` |
| **UserPreference** | Saved user defaults | `user_id` (FK), `default_deck_type`, `default_room_config` (JSONB), `theme`, `notification_settings` (JSONB) |
| **Organization** | Enterprise SSO workspace | `org_id` (PK), `name`, `domain`, `sso_config` (JSONB: OIDC/SAML2 settings), `branding` (JSONB), `subscription_id` (FK) |
| **OrgMember** | User-organization membership | `org_id` (FK), `user_id` (FK), `role` (ADMIN/MEMBER), `joined_at` |
| **Room** | Estimation session | `room_id` (PK, nanoid 6-char), `owner_id` (FK nullable for anonymous), `org_id` (FK nullable), `title`, `privacy_mode` (PUBLIC/INVITE_ONLY/ORG_RESTRICTED), `config` (JSONB: deck, rules, timer), `created_at`, `last_active_at` |
| **RoomParticipant** | Active session participants | `room_id` (FK), `user_id` (FK nullable), `anonymous_id`, `display_name`, `role` (HOST/VOTER/OBSERVER), `connected_at` |
| **Vote** | Individual estimation vote | `vote_id` (PK), `room_id` (FK), `round_number`, `participant_id`, `card_value`, `voted_at` |
| **Round** | Estimation round within session | `round_id` (PK), `room_id` (FK), `round_number`, `story_title`, `started_at`, `revealed_at`, `average`, `median`, `consensus_reached` |
| **SessionHistory** | Completed session record | `session_id` (PK), `room_id` (FK), `started_at`, `ended_at`, `total_rounds`, `total_stories`, `participants` (JSONB array), `summary_stats` (JSONB) |
| **Subscription** | Stripe subscription record | `subscription_id` (PK), `stripe_subscription_id`, `entity_id` (user_id or org_id), `entity_type` (USER/ORG), `tier` (FREE/PRO/PRO_PLUS/ENTERPRISE), `status`, `current_period_end`, `canceled_at` |
| **PaymentHistory** | Payment transaction log | `payment_id` (PK), `subscription_id` (FK), `stripe_invoice_id`, `amount`, `currency`, `status`, `paid_at` |
| **AuditLog** | Compliance and security audit trail | `log_id` (PK), `org_id` (FK nullable), `user_id` (FK nullable), `action`, `resource_type`, `resource_id`, `ip_address`, `user_agent`, `timestamp` |
```

### Context: iteration-7 (from 02_Iteration_I7.md)

```markdown
<!-- anchor: iteration-7 -->
### Iteration 7: Enterprise Features (SSO & Organizations)

*   **Iteration ID:** `I7`

*   **Goal:** Implement enterprise-tier features including SSO integration (OIDC/SAML2), organization management, member administration, org-level branding, and audit logging.

*   **Prerequisites:** I3 (authentication), I5 (subscription tier enforcement)
```

### Context: task-i7-t6 (from 02_Iteration_I7.md)

```markdown
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
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/OrganizationController.java`
    *   **Summary:** This file contains the complete REST API implementation for organization management. It provides 6 endpoints: create organization (POST /organizations), get organization (GET /organizations/{orgId}), update SSO config (PUT /organizations/{orgId}/sso), invite member (POST /organizations/{orgId}/members), remove member (DELETE /organizations/{orgId}/members/{userId}), and query audit logs (GET /organizations/{orgId}/audit-logs). All endpoints enforce proper authorization (requires ADMIN role for modifications, organization membership for reads).
    *   **Recommendation:** You MUST NOT modify this file. Your frontend components should consume these existing endpoints exactly as specified. The controller uses `@RolesAllowed("USER")` and validates org admin access via the `requireOrgAdmin()` helper method. Your frontend MUST handle 403 Forbidden responses when non-admin users try to access admin-only pages.
    *   **Critical Details:**
        - Audit logs endpoint supports pagination with query params: `from`, `to`, `action`, `page`, `size`
        - SSO config endpoint expects `SsoConfigRequest` body
        - Member invite expects `InviteMemberRequest` with `email` and `role` fields
        - Member removal is idempotent (returns 204 No Content on success)
        - All endpoints return appropriate DTOs that match the OpenAPI spec

*   **File:** `frontend/src/services/organizationApi.ts`
    *   **Summary:** This file provides complete React Query hooks for all organization API operations. It includes: `useOrganization(orgId)` for fetching org details, `useAuditLogs(orgId, filters)` for paginated audit logs, `useUpdateSsoConfig(orgId)` mutation for SSO updates, `useInviteMember(orgId)` mutation for inviting members, and `useRemoveMember(orgId)` mutation for removing members. All hooks automatically handle cache invalidation and error logging.
    *   **Recommendation:** You MUST import and use these existing hooks in your page components. DO NOT create new hooks or make direct API calls. The hooks are already optimized with proper query keys, cache invalidation, stale time configuration, and error handling. All mutations automatically invalidate the organization cache to trigger refetches.
    *   **Usage Pattern:**
        ```tsx
        // In your component:
        const { data: org, isLoading, error } = useOrganization(orgId);
        const updateSso = useUpdateSsoConfig(orgId);
        const inviteMember = useInviteMember(orgId);
        const removeMember = useRemoveMember(orgId);
        const { data: auditLogs } = useAuditLogs(orgId, { page: 0, size: 20 });
        ```

*   **File:** `frontend/src/types/organization.ts`
    *   **Summary:** This file defines all TypeScript interfaces for organization-related data, matching the OpenAPI specification exactly. It includes: `OrganizationDTO`, `OrgMemberDTO`, `AuditLogDTO`, `AuditLogListResponse`, `SsoConfigDTO`, `SsoConfigRequest`, `InviteMemberRequest`, `BrandingDTO`, `OrgRole` enum, `SsoProtocol` enum, and `AuditLogFilters`.
    *   **Recommendation:** You MUST import types from this file for all props, state, and function signatures. DO NOT create duplicate type definitions. These types ensure type safety and match the backend DTOs exactly.

*   **File:** `frontend/src/pages/org/OrganizationSettingsPage.tsx`
    *   **Summary:** THIS FILE ALREADY EXISTS AND IS COMPLETE. It displays organization overview with name, domain, member count, SSO status, branding preview, and navigation tabs. It includes loading skeleton, error states, and Enterprise tier check. It provides quick action buttons to navigate to members, SSO, and audit logs pages.
    *   **Recommendation:** You MUST NOT modify this file. It is already fully implemented and tested. Your other three page components (SsoConfigPage, MemberManagementPage, AuditLogPage) should follow the same design patterns, styling conventions, and navigation structure established in this file.
    *   **Design Patterns to Follow:**
        - Loading state: Full-page skeleton with animated pulse for all cards
        - Error state: Red banner with error icon, error message, and retry button
        - Enterprise tier check: Yellow banner redirecting to pricing if not Enterprise
        - Navigation tabs: Border-bottom style with active blue underline
        - Card layout: Grid with white cards on gray-50 background, dark mode support
        - Tailwind classes: Consistent spacing, colors, typography

*   **File:** `frontend/src/pages/org/AuditLogPage.tsx`
    *   **Summary:** THIS FILE ALREADY EXISTS AND IS COMPLETE. It displays a paginated table of audit log entries with timestamp, user, action, resource type, and IP address. It includes date range filtering, action type filtering, and pagination controls. It uses the `useAuditLogs` hook and handles loading/error states.
    *   **Recommendation:** This file is complete. You do not need to modify it. Use it as a reference for table layouts and pagination patterns.

*   **File:** `frontend/src/pages/org/MemberManagementPage.tsx`
    *   **Summary:** THIS FILE ALREADY EXISTS AND IS COMPLETE. It displays the member table using the `MemberTable` component, with invite member modal and remove member confirmation dialog. It uses the `useInviteMember` and `useRemoveMember` hooks.
    *   **Recommendation:** This file is complete. You do not need to modify it.

*   **File:** `frontend/src/pages/org/SsoConfigPage.tsx`
    *   **Summary:** THIS FILE ALREADY EXISTS AND IS COMPLETE. It provides a form for configuring OIDC or SAML2 SSO settings with protocol toggle, input fields for IdP URL, client ID, client secret (OIDC) or metadata URL and certificate (SAML2), and a test SSO button. It uses the `useUpdateSsoConfig` hook.
    *   **Recommendation:** This file is complete. You do not need to modify it.

*   **File:** `frontend/src/components/org/MemberTable.tsx`
    *   **Summary:** This file provides a reusable table component for displaying organization members. It shows member avatar, name, email, role badge (with conditional colors: purple for ADMIN, gray for MEMBER), joined date, and remove button. It includes loading spinner and empty state. It uses Heroicons for icons and date-fns for date formatting.
    *   **Recommendation:** This component is complete and used by `MemberManagementPage`. You do not need to modify it.

*   **File:** `frontend/src/stores/authStore.ts`
    *   **Summary:** This Zustand store manages authentication state including `user`, `accessToken`, `refreshToken`, and `isAuthenticated`. It persists to localStorage and provides `setAuth()`, `clearAuth()`, and `loadAuthFromStorage()` actions. The `user` object includes `subscriptionTier` which you MUST use to check for Enterprise tier access.
    *   **Recommendation:** You MUST import `useAuthStore` to access the current user and check subscription tier. Use `const { user } = useAuthStore()` to get user data, then check `user?.subscriptionTier === 'ENTERPRISE'` to enforce Enterprise-only access to org admin pages.

*   **File:** `frontend/src/App.tsx`
    *   **Summary:** This is the main app routing configuration using React Router. It currently defines routes for home, login, dashboard, pricing, billing, reports, and room pages. It wraps routes in `PrivateRoute` for authentication checks.
    *   **Recommendation:** You MUST add routes for the organization admin pages to this file. Add routes with paths like `/org/:orgId/settings`, `/org/:orgId/members`, `/org/:orgId/sso`, and `/org/:orgId/audit-logs`. Wrap them in `PrivateRoute` to require authentication. The routes are MISSING from this file and you MUST add them.
    *   **Pattern to follow:**
        ```tsx
        <Route
          path="/org/:orgId/settings"
          element={
            <PrivateRoute>
              <OrganizationSettingsPage />
            </PrivateRoute>
          }
        />
        ```

### Implementation Tips & Notes

*   **Tip:** All four organization admin page components ALREADY EXIST and are fully implemented. I found complete implementations in `frontend/src/pages/org/` for `OrganizationSettingsPage.tsx`, `SsoConfigPage.tsx`, `MemberManagementPage.tsx`, and `AuditLogPage.tsx`. You DO NOT need to write these components from scratch.

*   **Tip:** The React Query hooks in `organizationApi.ts` are production-ready and handle all edge cases (loading states, error handling, cache invalidation, parallel query execution). Trust these hooks and use them directly. Do not bypass them with manual API calls.

*   **Critical Missing Piece:** The organization admin routes are NOT registered in `App.tsx`. You MUST add four route definitions for the org admin pages. Without these routes, the pages cannot be accessed even though they exist.

*   **Note:** The existing pages follow a consistent navigation pattern with tabs at the top linking between Settings, Members, SSO Configuration, and Audit Logs. The active tab is highlighted with a blue underline. This navigation is implemented in each page component, not as a shared layout. Ensure consistency across all pages.

*   **Authorization Pattern:** All page components check `user?.subscriptionTier === 'ENTERPRISE'` and display a yellow warning banner with "Upgrade to Enterprise" message if the user lacks the required tier. This check happens BEFORE making any API calls to avoid unnecessary 403 errors.

*   **Styling Convention:** The project uses Tailwind CSS with a specific design system:
    - Background: `bg-gray-50 dark:bg-gray-900` for page background
    - Cards: `bg-white dark:bg-gray-800` with `rounded-lg shadow-md`
    - Text: `text-gray-900 dark:text-white` for headings, `text-gray-600 dark:text-gray-300` for body
    - Primary action buttons: `bg-blue-600 hover:bg-blue-700 text-white`
    - Danger action buttons: `bg-red-600 hover:bg-red-700 text-white`
    - Status badges: Rounded full with inline-flex, different colors per state
    - Icons: Heroicons v2 (24/outline for most UI elements)

*   **Error Handling:** All page components use a consistent error state UI: a red banner (bg-red-50 border-red-200) with an XCircleIcon, error message text, and a retry button. This pattern should be maintained in any new components.

*   **Date Formatting:** The project uses `date-fns` for all date formatting. Import `format` from `date-fns` and use patterns like `format(new Date(timestamp), 'MMMM d, yyyy')` for readable dates or `format(new Date(timestamp), 'PPpp')` for full datetime.

*   **Warning:** The pages assume the user has organization membership and admin role. The backend enforces this via the `requireOrgAdmin()` helper, which returns 403 Forbidden if the user is not an admin. Your frontend MUST handle this gracefully by checking user role before rendering admin-only controls (like delete buttons, invite forms, SSO config forms).

*   **Navigation Flow:** Users typically access organization pages from their dashboard or a settings menu. Consider adding a link in the dashboard or navigation bar to `/org/{orgId}/settings` for users who are org admins. The existing `OrganizationSettingsPage` serves as the entry point to the org admin area.

---

## Summary of Required Work

Based on my investigation, here's what you need to do:

1. **Add Routes to App.tsx** - The four organization admin pages already exist but are not accessible because routes are missing. Add four `<Route>` definitions to `App.tsx` for paths: `/org/:orgId/settings`, `/org/:orgId/members`, `/org/:orgId/sso`, and `/org/:orgId/audit-logs`. Wrap each route in `<PrivateRoute>`.

2. **Import Statements** - Import the four page components at the top of `App.tsx`:
   ```tsx
   import OrganizationSettingsPage from '@/pages/org/OrganizationSettingsPage';
   import SsoConfigPage from '@/pages/org/SsoConfigPage';
   import MemberManagementPage from '@/pages/org/MemberManagementPage';
   import AuditLogPage from '@/pages/org/AuditLogPage';
   ```

3. **Verify Existing Files** - Confirm that all target files already exist and are complete by reviewing them. The components are production-ready and fully tested.

4. **Test Navigation** - After adding routes, test that clicking navigation tabs in OrganizationSettingsPage correctly navigates between the four pages.

5. **Test Authorization** - Verify that non-Enterprise users see the upgrade prompt and cannot access org admin pages. Test with different subscription tiers.

6. **Test API Integration** - Verify that the SSO config form submits correctly, member invite modal works, member removal confirmation works, and audit log filters function properly.

**THE TASK IS 95% COMPLETE.** All UI components and API hooks already exist. You only need to register the routes in App.tsx to make the pages accessible.
