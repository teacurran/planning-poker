# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

**Task ID:** I7.T6
**Description:** Implement React components for organization administration (Enterprise tier). `OrganizationSettingsPage`: display org name, domain, member count, branding preview, "Configure SSO" button. `SsoConfigPage`: form for OIDC settings (IdP URL, client ID, client secret) or SAML2 settings (IdP metadata URL, certificate upload), test SSO button. `MemberManagementPage`: table listing org members (name, email, role), "Invite Member" button, role change dropdown, remove button. `AuditLogPage`: audit event table (timestamp, user, action, resource, IP), date range filter, pagination. Conditional rendering based on user's org admin role.

**Acceptance Criteria:**
- OrganizationSettingsPage displays org name and member count
- SsoConfigPage form submits to /organizations/{id}/sso endpoint
- SSO test button validates configuration
- MemberManagementPage lists current members
- Invite member opens modal, calls POST /members
- Remove member confirms action, calls DELETE /members/{userId}
- AuditLogPage displays events with timestamp, action, user
- Non-admin users cannot access org admin pages (403 or redirect)

---

## Issues Detected

**CRITICAL MISSING IMPLEMENTATION:**

* **Missing Routes in App.tsx:** The four organization admin page components exist and are fully implemented in `frontend/src/pages/org/`, BUT the routes are NOT registered in `frontend/src/App.tsx`. This means the pages are completely inaccessible to users. The application cannot navigate to `/org/:orgId/settings`, `/org/:orgId/members`, `/org/:orgId/sso`, or `/org/:orgId/audit-logs` because these routes do not exist in the React Router configuration.

---

## Best Approach to Fix

You MUST add the missing route definitions to `frontend/src/App.tsx`. Follow these exact steps:

### Step 1: Add Import Statements

At the top of `frontend/src/App.tsx`, add these four import statements after the existing page imports (around line 13):

```typescript
import OrganizationSettingsPage from '@/pages/org/OrganizationSettingsPage';
import SsoConfigPage from '@/pages/org/SsoConfigPage';
import MemberManagementPage from '@/pages/org/MemberManagementPage';
import AuditLogPage from '@/pages/org/AuditLogPage';
```

### Step 2: Add Route Definitions

Inside the `<Routes>` component in `App.tsx` (after the existing routes, around line 70), add these four route definitions:

```tsx
<Route
  path="/org/:orgId/settings"
  element={
    <PrivateRoute>
      <OrganizationSettingsPage />
    </PrivateRoute>
  }
/>
<Route
  path="/org/:orgId/members"
  element={
    <PrivateRoute>
      <MemberManagementPage />
    </PrivateRoute>
  }
/>
<Route
  path="/org/:orgId/sso"
  element={
    <PrivateRoute>
      <SsoConfigPage />
    </PrivateRoute>
  }
/>
<Route
  path="/org/:orgId/audit-logs"
  element={
    <PrivateRoute>
      <AuditLogPage />
    </PrivateRoute>
  }
/>
```

### Step 3: Verify the Fix

After adding the routes:

1. Run `npm run build` to ensure there are no TypeScript compilation errors
2. Run `npm run lint` to ensure there are no linting errors
3. Verify that the import statements use the correct path alias (`@/pages/org/...`)
4. Verify that all four routes are wrapped in `<PrivateRoute>` to require authentication

---

## IMPORTANT NOTES

- **DO NOT modify the page components** - they are already complete and correct
- **DO NOT modify the API hooks** - they are already correct
- **DO NOT modify the types** - they are already correct
- **ONLY modify `frontend/src/App.tsx`** to add the missing routes

The task is 95% complete. You only need to add these 4 route definitions to make the pages accessible.
