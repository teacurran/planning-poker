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

### Context: Enterprise Requirements (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: enterprise-requirements -->
#### Enterprise Requirements
- **SSO Integration:** OIDC and SAML2 protocol support for identity federation
- **Organization Management:** Workspace creation, custom branding, org-wide defaults
- **Role-Based Access:** Admin/member roles with configurable permissions
- **Audit Logging:** Comprehensive event tracking for compliance and security monitoring
```

### Context: Organization Management API Endpoints (from api/openapi.yaml, lines 73-270)

```yaml
/api/v1/organizations:
  post:
    tags:
      - Organizations
    summary: Create organization workspace
    description: |
      Creates a new organization workspace. **Requires Enterprise tier subscription.**
    operationId: createOrganization
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/CreateOrganizationRequest'
    responses:
      '201':
        description: Organization created
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OrganizationDTO'

/api/v1/organizations/{orgId}:
  get:
    tags:
      - Organizations
    summary: Get organization settings
    description: |
      Returns organization configuration, branding, and member count. Requires organization membership.
    operationId: getOrganization
    parameters:
      - $ref: '#/components/parameters/OrgIdPath'
    responses:
      '200':
        description: Organization retrieved

/api/v1/organizations/{orgId}/sso:
  put:
    tags:
      - Organizations
    summary: Configure OIDC/SAML2 SSO settings
    description: |
      Updates SSO configuration for organization. **Requires ADMIN role.**
    operationId: updateSsoConfig
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/SsoConfigRequest'

/api/v1/organizations/{orgId}/members:
  post:
    tags:
      - Organizations
    summary: Invite member to organization
    description: |
      Sends invitation email to join organization. **Requires ADMIN role.**
    operationId: inviteMember
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/InviteMemberRequest'
    responses:
      '201':
        description: Member invited

/api/v1/organizations/{orgId}/members/{userId}:
  delete:
    tags:
      - Organizations
    summary: Remove member from organization
    description: |
      Removes user from organization. **Requires ADMIN role.** Cannot remove last admin.
    operationId: removeMember
    responses:
      '204':
        description: Member removed

/api/v1/organizations/{orgId}/audit-logs:
  get:
    tags:
      - Organizations
    summary: Query audit trail
    description: |
      Returns paginated audit log entries for compliance. **Requires ADMIN role.**
    operationId: getAuditLogs
    parameters:
      - $ref: '#/components/parameters/OrgIdPath'
      - name: from
        in: query
        schema:
          type: string
          format: date-time
        description: Start timestamp (ISO 8601 format)
      - name: to
        in: query
        schema:
          type: string
          format: date-time
        description: End timestamp (ISO 8601 format)
      - name: action
        in: query
        schema:
          type: string
        description: Filter by action type
      - name: page
        in: query
        schema:
          type: integer
          default: 0
      - name: size
        in: query
        schema:
          type: integer
          default: 20
    responses:
      '200':
        description: Audit logs retrieved
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AuditLogListResponse'
```

### Context: Organization DTOs (from api/openapi.yaml, lines 1963-2264)

```yaml
OrganizationDTO:
  type: object
  required:
    - orgId
    - name
    - domain
    - createdAt
  properties:
    orgId:
      type: string
      format: uuid
    name:
      type: string
      maxLength: 255
    domain:
      type: string
      maxLength: 255
    ssoConfig:
      $ref: '#/components/schemas/SsoConfigDTO'
    branding:
      $ref: '#/components/schemas/BrandingDTO'
    subscriptionId:
      type: string
      format: uuid
      nullable: true
    memberCount:
      type: integer
    createdAt:
      type: string
      format: date-time
    updatedAt:
      type: string
      format: date-time

SsoConfigDTO:
  type: object
  description: SSO configuration (OIDC or SAML2)
  properties:
    protocol:
      type: string
      enum: [OIDC, SAML2]
    issuer:
      type: string
      format: uri
    clientId:
      type: string
    authorizationEndpoint:
      type: string
      format: uri
    tokenEndpoint:
      type: string
      format: uri
    jwksUri:
      type: string
      format: uri
    samlEntityId:
      type: string
    samlSsoUrl:
      type: string
      format: uri

BrandingDTO:
  type: object
  description: Organization branding customization
  properties:
    logoUrl:
      type: string
      format: uri
      maxLength: 500
    primaryColor:
      type: string
      pattern: '^#[0-9A-Fa-f]{6}$'
    secondaryColor:
      type: string
      pattern: '^#[0-9A-Fa-f]{6}$'

SsoConfigRequest:
  type: object
  required:
    - protocol
  properties:
    protocol:
      type: string
      enum: [OIDC, SAML2]
    issuer:
      type: string
      format: uri
    clientId:
      type: string
    clientSecret:
      type: string
      format: password
    authorizationEndpoint:
      type: string
      format: uri
    tokenEndpoint:
      type: string
      format: uri
    jwksUri:
      type: string
      format: uri
    samlEntityId:
      type: string
    samlSsoUrl:
      type: string
      format: uri
    samlCertificate:
      type: string

OrgMemberDTO:
  type: object
  required:
    - userId
    - displayName
    - email
    - role
    - joinedAt
  properties:
    userId:
      type: string
      format: uuid
    displayName:
      type: string
    email:
      type: string
      format: email
    avatarUrl:
      type: string
      format: uri
      nullable: true
    role:
      $ref: '#/components/schemas/OrgRole'
    joinedAt:
      type: string
      format: date-time

InviteMemberRequest:
  type: object
  required:
    - email
    - role
  properties:
    email:
      type: string
      format: email
    role:
      $ref: '#/components/schemas/OrgRole'

AuditLogDTO:
  type: object
  required:
    - logId
    - action
    - resourceType
    - timestamp
  properties:
    logId:
      type: string
      format: uuid
    orgId:
      type: string
      format: uuid
      nullable: true
    userId:
      type: string
      format: uuid
      nullable: true
    action:
      type: string
      maxLength: 100
    resourceType:
      type: string
      maxLength: 50
    resourceId:
      type: string
      maxLength: 255
      nullable: true
    ipAddress:
      type: string
      maxLength: 45
      nullable: true
    userAgent:
      type: string
      maxLength: 500
      nullable: true
    timestamp:
      type: string
      format: date-time

AuditLogListResponse:
  type: object
  required:
    - logs
    - page
    - size
    - totalElements
    - totalPages
  properties:
    logs:
      type: array
      items:
        $ref: '#/components/schemas/AuditLogDTO'
    page:
      type: integer
    size:
      type: integer
    totalElements:
      type: integer
    totalPages:
      type: integer

OrgRole:
  type: string
  enum:
    - ADMIN
    - MEMBER
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `frontend/src/pages/DashboardPage.tsx`
    *   **Summary:** This is a complete example of a React page component following the project's patterns. It demonstrates: React Query hooks for data fetching (`useUser`, `useRooms`), auth store usage for current user, loading skeletons with Tailwind animations, error handling with retry, responsive grid layouts, and empty state UI.
    *   **Recommendation:** You MUST follow this exact pattern for all organization pages. Key patterns to replicate:
        - Use React Query hooks from `apiHooks.ts` (you'll create organization-specific hooks)
        - Show loading skeleton with `animate-pulse` while data fetches
        - Display error state with retry button and error icon
        - Use Tailwind responsive classes (`md:grid-cols-2`, `lg:grid-cols-3`)
        - Container layout: `min-h-screen bg-gray-50 dark:bg-gray-900` → `container mx-auto px-4 py-8`
        - Extract `userId` from `useAuthStore()` for API calls

*   **File:** `frontend/src/pages/SubscriptionSettingsPage.tsx`
    *   **Summary:** This page demonstrates advanced patterns: modal dialogs using Headless UI (`Dialog`, `Transition`), confirmation modals with destructive actions, table components with pagination, date formatting with `date-fns`, utility functions for badge styling, and mutation hooks with optimistic updates.
    *   **Recommendation:** You SHOULD use this as a reference for:
        - `CancelConfirmationModal` pattern for your "Remove Member" confirmation dialog
        - `PaymentHistoryTable` pattern for your `MemberTable` and `AuditLogPage` table
        - Headless UI imports: `import { Dialog, Transition } from '@headlessui/react'`
        - Hero Icons: `import { ... } from '@heroicons/react/24/outline'`
        - Date formatting: `format(new Date(timestamp), 'MMMM d, yyyy')`
        - Status badges and tier badges utility functions (create similar for org roles)

*   **File:** `frontend/src/services/apiHooks.ts`
    *   **Summary:** This file defines the standard pattern for React Query hooks. Key elements: query key factories for cache management, custom hooks wrapping `useQuery` and `useMutation`, error handling with `getErrorMessage`, stale time configuration (5 minutes), and enabled flags for conditional queries.
    *   **Recommendation:** You MUST create `organizationApi.ts` following this pattern. Create these hooks:
        ```typescript
        // Query hooks
        useOrganization(orgId: string) // GET /organizations/{orgId}
        useOrgMembers(orgId: string) // GET /organizations/{orgId}/members (you'll need to add this endpoint or derive from org data)
        useAuditLogs(orgId: string, filters: {...}, page: number, size: number) // GET /organizations/{orgId}/audit-logs

        // Mutation hooks
        useUpdateSsoConfig() // PUT /organizations/{orgId}/sso
        useInviteMember() // POST /organizations/{orgId}/members
        useRemoveMember() // DELETE /organizations/{orgId}/members/{userId}
        ```
        - Use query key factory pattern: `queryKeys.organizations = { detail: (orgId) => ['organizations', orgId], members: (orgId) => [...], ... }`
        - For mutations, return `{ mutate, mutateAsync, isPending, isSuccess, error }` from `useMutation`
        - On success, invalidate relevant query keys: `queryClient.invalidateQueries({ queryKey: queryKeys.organizations.detail(orgId) })`

*   **File:** `frontend/src/stores/authStore.ts`
    *   **Summary:** Zustand store managing authentication state. Contains `user` object with subscription tier, access tokens, and auth methods. The user object includes `subscriptionTier` field which you'll use to check for Enterprise access.
    *   **Recommendation:** You MUST check `user.subscriptionTier === 'ENTERPRISE'` before rendering org admin pages. If not Enterprise, show an upgrade prompt or redirect to pricing page. Also, you need to track the user's organization memberships and admin status. Consider extending authStore or creating a separate `orgStore` to track:
        ```typescript
        interface OrgStore {
          currentOrgId: string | null;
          isOrgAdmin: boolean;
          setCurrentOrg: (orgId: string, isAdmin: boolean) => void;
        }
        ```

*   **File:** `frontend/src/components/subscription/UpgradeModal.tsx`
    *   **Summary:** Modal component triggered when users hit feature gates (403 errors). Shows tier comparison and upgrade CTA.
    *   **Recommendation:** You SHOULD integrate this modal into your organization pages. When a non-Enterprise user tries to access org features, show this modal. Also handle non-admin users differently (they have Enterprise, but lack admin role in specific org) - show different error message: "Requires organization admin role".

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/OrganizationController.java`
    *   **Summary:** The complete backend REST controller with all 6 organization endpoints implemented. This defines the exact API contract your frontend must call.
    *   **Recommendation:** Your API calls MUST match these endpoint signatures:
        - `POST /api/v1/organizations` - Create org (Enterprise tier only)
        - `GET /api/v1/organizations/{orgId}` - Get org details
        - `PUT /api/v1/organizations/{orgId}/sso` - Update SSO config (admin only)
        - `POST /api/v1/organizations/{orgId}/members` - Invite member (admin only)
        - `DELETE /api/v1/organizations/{orgId}/members/{userId}` - Remove member (admin only)
        - `GET /api/v1/organizations/{orgId}/audit-logs?from=&to=&action=&page=&size=` - Query audit logs (admin only)
        All admin-only endpoints return 403 if user is not an admin of that org.

### Implementation Tips & Notes

*   **Tip:** For SSO configuration form, you need conditional fields based on protocol selection. When `protocol === 'OIDC'`, show: issuer, clientId, clientSecret, authorizationEndpoint, tokenEndpoint, jwksUri. When `protocol === 'SAML2'`, show: samlEntityId, samlSsoUrl, samlCertificate (file upload). Use React state to toggle field visibility: `const [protocol, setProtocol] = useState<'OIDC' | 'SAML2'>('OIDC')`.

*   **Tip:** For file uploads (SAML certificate), use a file input and read as base64 string:
    ```typescript
    const handleCertificateUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = () => {
          const base64 = reader.result as string;
          // Store in form state
        };
        reader.readAsDataText(file);
      }
    };
    ```

*   **Note:** The "Test SSO" button should call a separate endpoint to validate the SSO configuration without saving. However, looking at the OpenAPI spec and backend controller, I don't see a test endpoint. You should either:
    1. Make the button call the same `PUT /sso` endpoint with a `?test=true` query param (requires backend changes)
    2. Make the button disabled for now and add a TODO comment
    3. Make the button client-side validate required fields only (recommended for MVP)

*   **Note:** For audit logs date range filter, use HTML date inputs or a date picker library like `react-datepicker`. Convert selected dates to ISO-8601 strings: `new Date(dateValue).toISOString()`. If no dates selected, don't pass `from`/`to` query params.

*   **Warning:** The backend does NOT have a `GET /organizations/{orgId}/members` endpoint. Looking at the controller, member data is embedded in `OrganizationDTO` (just `memberCount`, not the list). You have two options:
    1. Add a new backend endpoint `GET /organizations/{orgId}/members` that returns `List<OrgMemberDTO>` (requires backend changes - outside scope of this task)
    2. Store members list in organization state when users are invited/removed, and display from local state (recommended for now)
    For MVP, I recommend option 2: After inviting a member (POST returns OrgMemberDTO), add it to local state array. After removing, filter it out. This avoids backend changes.

*   **Tip:** For role change dropdown, use Headless UI `Listbox` component:
    ```tsx
    import { Listbox } from '@headlessui/react';

    <Listbox value={member.role} onChange={(newRole) => handleRoleChange(member.userId, newRole)}>
      <Listbox.Button>...</Listbox.Button>
      <Listbox.Options>
        <Listbox.Option value="ADMIN">Admin</Listbox.Option>
        <Listbox.Option value="MEMBER">Member</Listbox.Option>
      </Listbox.Options>
    </Listbox>
    ```
    However, the backend doesn't have an "update member role" endpoint. For MVP, disable role changes or make it admin-only creation choice during invite.

*   **Tip:** For MemberManagementPage, create a modal for inviting members. Use the same `Dialog` pattern from SubscriptionSettingsPage. Modal fields: email input (with validation), role dropdown (ADMIN/MEMBER). On submit, call `useInviteMember` mutation.

*   **Tip:** For removing members, show confirmation dialog (like CancelConfirmationModal). Warn if removing an admin: "Are you sure you want to remove [name] (Admin) from the organization?". Backend prevents removing last admin, so handle that 403 error gracefully: "Cannot remove the last administrator."

*   **Tip:** For AuditLogPage table columns:
    - Timestamp (format: `MMM d, yyyy HH:mm:ss`)
    - User (display name if available, otherwise userId)
    - Action (e.g., "user.login", "sso.config.updated")
    - Resource (resourceType + resourceId, e.g., "Room: abc123")
    - IP Address
    - Sortable by timestamp (newest first by default)

*   **Note:** For pagination controls, reuse `PaginationControls` component from reporting pages (check `frontend/src/components/reporting/PaginationControls.tsx`). If it doesn't exist, create a reusable component with Previous/Next buttons and page numbers.

*   **Critical:** For admin-only access enforcement, you MUST check two conditions:
    1. User has Enterprise tier subscription (check `authStore.user.subscriptionTier === 'ENTERPRISE'`)
    2. User is an admin of the specific organization (check by calling org API and checking user's membership, or track in local state)
    If either fails, redirect to appropriate error:
    - No Enterprise tier → redirect to `/pricing` with message
    - Not org admin → show 403 error page with "Contact your organization administrator"

*   **Tip:** For OrganizationSettingsPage layout, use a card-based design:
    - Top card: Org details (name, domain, member count badge, branding preview if set)
    - Middle card: SSO status (protocol type, configured/not configured badge, "Configure SSO" button → navigate to SsoConfigPage)
    - Bottom card: Quick stats (members count, audit log recent activity count)
    - Sidebar navigation: Settings, Members, SSO, Audit Logs (tabs or links)

*   **Tip:** For branding preview, if `organization.branding` exists, show:
    - Logo: `<img src={organization.branding.logoUrl} />` (with fallback if null)
    - Color swatch: `<div style={{ backgroundColor: organization.branding.primaryColor }} />` (with label)

*   **Warning:** Type safety - you need to create TypeScript types for all organization DTOs. Create `frontend/src/types/organization.ts`:
    ```typescript
    export interface OrganizationDTO {
      orgId: string;
      name: string;
      domain: string;
      ssoConfig: SsoConfigDTO | null;
      branding: BrandingDTO | null;
      subscriptionId: string | null;
      memberCount: number;
      createdAt: string;
      updatedAt: string;
    }
    // ... other types matching OpenAPI schemas
    ```
    Import these in your components and API hooks.

*   **Tip:** For SsoConfigPage form validation, use React Hook Form or simple controlled inputs with state. Required fields depend on protocol:
    - OIDC: protocol, issuer, clientId (clientSecret optional for public clients)
    - SAML2: protocol, samlEntityId, samlSsoUrl
    Show validation errors below each input field.

*   **Critical:** Error handling for 403 responses - when user hits admin-only endpoint without admin role, the API returns 403. Your error handling should:
    ```typescript
    if (error.response?.status === 403) {
      // Check error message
      if (error.response.data.message?.includes('ADMIN role')) {
        // Show "You don't have admin access" message
        setErrorMessage('Only organization administrators can perform this action.');
      } else if (error.response.data.message?.includes('Enterprise')) {
        // Show upgrade modal
        openUpgradeModal();
      }
    }
    ```

*   **Tip:** For navigation between org admin pages, create a shared layout component:
    ```tsx
    // frontend/src/components/org/OrgAdminLayout.tsx
    const OrgAdminLayout: React.FC<{ children: React.ReactNode }> = ({ children }) => {
      const { orgId } = useParams();

      return (
        <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
          <div className="container mx-auto px-4 py-8">
            <div className="mb-6">
              <h1>Organization Administration</h1>
              <nav className="flex space-x-4 mt-4">
                <Link to={`/org/${orgId}/settings`}>Settings</Link>
                <Link to={`/org/${orgId}/members`}>Members</Link>
                <Link to={`/org/${orgId}/sso`}>SSO Config</Link>
                <Link to={`/org/${orgId}/audit-logs`}>Audit Logs</Link>
              </nav>
            </div>
            {children}
          </div>
        </div>
      );
    };
    ```

*   **Note:** Don't forget to add routes in your router configuration (likely `App.tsx` or a routes file):
    ```tsx
    <Route path="/org/:orgId/settings" element={<OrganizationSettingsPage />} />
    <Route path="/org/:orgId/sso" element={<SsoConfigPage />} />
    <Route path="/org/:orgId/members" element={<MemberManagementPage />} />
    <Route path="/org/:orgId/audit-logs" element={<AuditLogPage />} />
    ```
    Wrap these routes with a guard checking Enterprise tier and org membership.
