# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Implement React components for organization administration (Enterprise tier). `OrganizationSettingsPage`: display org name, domain, member count, branding preview, "Configure SSO" button. `SsoConfigPage`: form for OIDC settings (IdP URL, client ID, client secret) or SAML2 settings (IdP metadata URL, certificate upload), test SSO button. `MemberManagementPage`: table listing org members (name, email, role), "Invite Member" button, role change dropdown, remove button. `AuditLogPage`: audit event table (timestamp, user, action, resource, IP), date range filter, pagination. Conditional rendering based on user's org admin role.

---

## Issues Detected

### Linting Errors

*   **Unused Import:** `BuildingOfficeIcon` is imported but never used in `frontend/src/pages/org/AuditLogPage.tsx` (line 9)
*   **Unused Import:** `BuildingOfficeIcon` is imported but never used in `frontend/src/pages/org/SsoConfigPage.tsx` (line 9)
*   **TypeScript Any Type:** Unexpected `any` type usage in `frontend/src/pages/org/MemberManagementPage.tsx` on line 295 (should use proper Error type from mutation)
*   **TypeScript Any Type:** Unexpected `any` type usage in `frontend/src/pages/org/MemberManagementPage.tsx` on line 329 (should use proper Error type from mutation)
*   **TypeScript Any Type:** Unexpected `any` type usage in `frontend/src/pages/org/SsoConfigPage.tsx` on line 115 (should use proper Error type from mutation)

### Build Errors

*   **TypeScript Error:** `BuildingOfficeIcon` is declared but never read in `src/pages/org/AuditLogPage.tsx(9,3)`
*   **TypeScript Error:** `BuildingOfficeIcon` is declared but never read in `src/pages/org/SsoConfigPage.tsx(9,3)`

---

## Best Approach to Fix

You MUST fix all linting and TypeScript errors by making the following changes:

### 1. Fix Unused Import in AuditLogPage.tsx

Remove `BuildingOfficeIcon` from the imports on line 9 of `frontend/src/pages/org/AuditLogPage.tsx`. The import statement should only include the icons that are actually used in the component.

**Current (incorrect):**
```typescript
import {
  BuildingOfficeIcon,
  DocumentTextIcon,
  FunnelIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  XCircleIcon,
  ArrowLeftIcon,
} from '@heroicons/react/24/outline';
```

**Fixed (correct):**
```typescript
import {
  DocumentTextIcon,
  FunnelIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  XCircleIcon,
  ArrowLeftIcon,
} from '@heroicons/react/24/outline';
```

### 2. Fix Unused Import in SsoConfigPage.tsx

Remove `BuildingOfficeIcon` from the imports on line 9 of `frontend/src/pages/org/SsoConfigPage.tsx`. The import statement should only include the icons that are actually used in the component.

**Current (incorrect):**
```typescript
import {
  BuildingOfficeIcon,
  ShieldCheckIcon,
  CheckCircleIcon,
  XCircleIcon,
  ArrowLeftIcon,
} from '@heroicons/react/24/outline';
```

**Fixed (correct):**
```typescript
import {
  ShieldCheckIcon,
  CheckCircleIcon,
  XCircleIcon,
  ArrowLeftIcon,
} from '@heroicons/react/24/outline';
```

### 3. Fix Any Type Usage in MemberManagementPage.tsx

Replace all `any` type annotations in error handlers with proper Error types. On lines 295 and 329, change `(err: any)` to `(err: Error)`. Then properly access error response properties using type guards or optional chaining.

**Current (incorrect) - Line 295:**
```typescript
onError: (err: any) => {
  if (err.response?.status === 403) {
    if (err.response?.data?.message?.includes('already exists')) {
```

**Fixed (correct) - Line 295:**
```typescript
onError: (err: Error) => {
  const axiosError = err as any; // Type assertion for axios error
  if (axiosError.response?.status === 403) {
    if (axiosError.response?.data?.message?.includes('already exists')) {
```

**Alternative fix using proper axios error type:**
```typescript
import { AxiosError } from 'axios';

// In the mutation callback:
onError: (err: Error) => {
  const axiosError = err as AxiosError<{ message?: string }>;
  if (axiosError.response?.status === 403) {
    if (axiosError.response?.data?.message?.includes('already exists')) {
```

Apply the same fix pattern to line 329 in the `confirmRemoveMember` function.

### 4. Fix Any Type Usage in SsoConfigPage.tsx

Replace `any` type annotation on line 115 with proper Error type, using the same pattern as described above.

**Current (incorrect) - Line 115:**
```typescript
onError: (err: any) => {
  if (err.response?.status === 403) {
```

**Fixed (correct) - Line 115:**
```typescript
onError: (err: Error) => {
  const axiosError = err as any; // Type assertion for axios error
  if (axiosError.response?.status === 403) {
```

Or use the AxiosError type as shown in fix #3.

### 5. Verify All Fixes

After making the above changes:

1. Run `npm run lint` in the frontend directory to ensure no linting errors remain
2. Run `npm run build` to ensure TypeScript compilation succeeds
3. Verify that all 4 page components and the MemberTable component are free of linting and type errors

**Expected Result:**
- Zero linting errors
- Zero TypeScript compilation errors
- Clean build output

---

## Summary

The code implementation is functionally correct and complete, but contains minor code quality issues (unused imports and improper `any` type usage) that violate the project's linting rules. Fix these 5 specific issues by removing unused imports and properly typing error objects in mutation callbacks.
