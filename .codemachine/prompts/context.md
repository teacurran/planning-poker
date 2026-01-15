# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I3.T7",
  "iteration_id": "I3",
  "iteration_goal": "Implement OAuth2 authentication (Google, Microsoft), JWT token generation/validation, user registration/login flows, and frontend authentication UI to enable secured access to the application.",
  "description": "Implement `DashboardPage` component displaying user profile, list of owned rooms, recent session history, and quick actions (create new room, view preferences). Use `useUser` and `useRooms` hooks to fetch data. Display loading skeleton while fetching, error message on failure. Show user avatar, display name, email. List rooms in card grid with room title, privacy mode badge, last active timestamp, \"Open Room\" button. Add \"Create New Room\" button navigating to room creation form. Style with Tailwind CSS, responsive for mobile/tablet/desktop.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "*   Dashboard requirements from product spec\n        *   API hooks from I3.T6\n        *   Design system (Tailwind, Headless UI)",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   DashboardPage with user profile section (avatar, name, email, tier badge)\n        *   Room list grid (responsive, 1 col mobile, 2 col tablet, 3 col desktop)\n        *   Room card component showing title, privacy mode, last active date\n        *   Create room button with prominent styling\n        *   Loading skeleton using Tailwind animate-pulse\n        *   Error state UI (retry button, error message)",
  "acceptance_criteria": "*   Dashboard loads user data from API on mount\n        *   User profile displays correct information (avatar, name, subscription tier)\n        *   Room list shows user's owned rooms from API\n        *   Clicking room card navigates to /room/{roomId}\n        *   Create room button navigates to /rooms/new\n        *   Loading state displayed while fetching data\n        *   Error state shows message if API call fails\n        *   Responsive layout works on mobile, tablet, desktop",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Task 3.7 – Create User Dashboard Page (Frontend) (from .codemachine/artifacts/plan/02_Iteration_I3.md)

```markdown
*   **Task 3.7: Create User Dashboard Page (Frontend)**
    *   **Task ID:** `I3.T7`
    *   **Description:** Implement `DashboardPage` component displaying user profile, list of owned rooms, recent session history, and quick actions (create new room, view preferences). Use `useUser` and `useRooms` hooks to fetch data. Display loading skeleton while fetching, error message on failure. Show user avatar, display name, email. List rooms in card grid with room title, privacy mode badge, last active timestamp, "Open Room" button. Add "Create New Room" button navigating to room creation form. Style with Tailwind CSS, responsive for mobile/tablet/desktop.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   Dashboard requirements from product spec
        *   API hooks from I3.T6
        *   Design system (Tailwind, Headless UI)
    *   **Input Files:**
        *   `frontend/src/services/apiHooks.ts`
        *   `frontend/src/stores/authStore.ts`
    *   **Target Files:**
        *   `frontend/src/pages/DashboardPage.tsx`
        *   `frontend/src/components/dashboard/UserProfileCard.tsx`
        *   `frontend/src/components/dashboard/RoomListCard.tsx`
        *   `frontend/src/components/dashboard/CreateRoomButton.tsx`
    *   **Deliverables:**
        *   DashboardPage with user profile section (avatar, name, email, tier badge)
        *   Room list grid (responsive, 1 col mobile, 2 col tablet, 3 col desktop)
        *   Room card component showing title, privacy mode, last active date
        *   Create room button with prominent styling
        *   Loading skeleton using Tailwind animate-pulse
        *   Error state UI (retry button, error message)
    *   **Acceptance Criteria:**
        *   Dashboard loads user data from API on mount
        *   User profile displays correct information (avatar, name, subscription tier)
        *   Room list shows user's owned rooms from API
        *   Clicking room card navigates to /room/{roomId}
        *   Create room button navigates to /rooms/new
        *   Loading state displayed while fetching data
        *   Error state shows message if API call fails
        *   Responsive layout works on mobile, tablet, desktop
    *   **Dependencies:** [I3.T6]
    *   **Parallelizable:** No (depends on API client hooks)
```

### Context: Task 3.6 – Implement Frontend API Client with Authentication (from .codemachine/artifacts/plan/02_Iteration_I3.md)

```markdown
*   **Task 3.6: Implement Frontend API Client with Authentication**
    *   **Task ID:** `I3.T6`
    *   **Description:** Create API client wrapper using React Query integrating authentication. Configure Axios instance with base URL, request interceptor to add `Authorization: Bearer <token>` header from authStore, response interceptor to handle 401 errors (refresh token or logout). Implement token refresh logic: on 401, call `/api/v1/auth/refresh`, update tokens in store, retry original request. Create React Query hooks for common API calls: `useUser(userId)`, `useRooms()`, `useRoomById(roomId)`. Handle loading and error states.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   OpenAPI spec for endpoint definitions
        *   React Query patterns
        *   Token refresh flow requirements
    *   **Input Files:**
        *   `api/openapi.yaml`
        *   `frontend/src/stores/authStore.ts`
    *   **Target Files:**
        *   `frontend/src/services/api.ts` (Axios instance with interceptors)
        *   `frontend/src/services/apiHooks.ts` (React Query hooks)
        *   `frontend/src/services/authApi.ts` (auth-specific API calls)
    *   **Deliverables:**
        *   Axios instance configured with baseURL, timeout
        *   Request interceptor adding Authorization header from authStore
        *   Response interceptor detecting 401, triggering token refresh
        *   Token refresh logic: call /refresh API, update authStore, retry request
        *   React Query hooks: useUser, useRooms, useRoomById
        *   Error handling: network errors, 500 server errors
    *   **Acceptance Criteria:**
        *   API requests include Authorization header when user authenticated
        *   Expired access token triggers refresh automatically
        *   After refresh, original request retries successfully
        *   If refresh fails (invalid refresh token), user logged out and redirected to login
        *   React Query hooks return loading/error/data states correctly
        *   Cache invalidation works (e.g., after room creation, useRooms refetches)
    *   **Dependencies:** [I3.T5]
    *   **Parallelizable:** No (depends on authStore)
```

### Context: REST API Endpoints Overview (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

```markdown
#### REST API Endpoints Overview

**Authentication & User Management:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth2 code for JWT tokens
- `POST /api/v1/auth/refresh` - Refresh expired access token
- `POST /api/v1/auth/logout` - Revoke refresh token
- `GET /api/v1/users/{userId}` - Retrieve user profile
- `PUT /api/v1/users/{userId}` - Update profile (display name, avatar)
- `GET /api/v1/users/{userId}/preferences` - Get user preferences
- `PUT /api/v1/users/{userId}/preferences` - Update default room settings, theme

**Room Management:**
- `POST /api/v1/rooms` - Create new room (authenticated or anonymous)
- `GET /api/v1/rooms/{roomId}` - Get room configuration and current state
- `PUT /api/v1/rooms/{roomId}/config` - Update room settings (host only)
- `DELETE /api/v1/rooms/{roomId}` - Delete room (owner only)
- `GET /api/v1/users/{userId}/rooms` - List user's owned rooms

**Subscription & Billing:**
- `GET /api/v1/subscriptions/{userId}` - Get current subscription status
- `POST /api/v1/subscriptions/checkout` - Create Stripe checkout session for upgrade
- `POST /api/v1/subscriptions/{subscriptionId}/cancel` - Cancel subscription (end of period)
- `POST /api/v1/subscriptions/webhook` - Stripe webhook endpoint (signature verification)
- `GET /api/v1/billing/invoices` - List payment history

**Reporting & Analytics:**
- `GET /api/v1/reports/sessions` - List session history (tier-gated pagination, filters)
- `GET /api/v1/reports/sessions/{sessionId}` - Detailed session report (tier-gated round detail)
- `POST /api/v1/reports/export` - Generate export job (CSV/PDF), returns job ID
- `GET /api/v1/jobs/{jobId}` - Poll export job status, retrieve download URL

**Organization Management (Enterprise):**
- `POST /api/v1/organizations` - Create organization workspace
- `GET /api/v1/organizations/{orgId}` - Get org settings
- `PUT /api/v1/organizations/{orgId}/sso` - Configure OIDC/SAML2 settings
- `POST /api/v1/organizations/{orgId}/members` - Invite member
- `DELETE /api/v1/organizations/{orgId}/members/{userId}` - Remove member
- `GET /api/v1/organizations/{orgId}/audit-logs` - Query audit trail
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `frontend/src/pages/DashboardPage.tsx:1`
    *   **Summary:** Fully implements the Dashboard container: pulls the authenticated ID from `useAuthStore`, fetches profile/rooms through `useUser` and `useRooms`, and renders three UI states (skeleton, retryable error, data) with Tailwind styling, responsive grid breakpoints, and navigation handlers for room cards plus `/rooms/new` CTA.
    *   **Recommendation:** Keep all data-fetching logic centralized here and continue delegating presentation to dedicated components so you can reuse them later. When extending functionality (e.g., recent sessions), follow the existing pattern of deriving combined loading/error state before rendering.
*   **File:** `frontend/src/components/dashboard/UserProfileCard.tsx:1`
    *   **Summary:** Shows the avatar, display name, email, and subscription badge using helpers like `getTierBadgeClasses`; gracefully falls back to initials if the avatar fails to load.
    *   **Recommendation:** Pass the exact `UserDTO` from `useUser` and avoid duplicating tier-formatting logic elsewhere—if you need more profile actions, add them to this component rather than bloating the page container.
*   **File:** `frontend/src/components/dashboard/RoomListCard.tsx:1`
    *   **Summary:** Presents each room’s title, privacy badge, relative `lastActiveAt`, and an `Open Room` CTA while providing keyboard accessibility; `CreateRoomButton.tsx:1` complements it with a reusable CTA that already wires up router navigation.
    *   **Recommendation:** Reuse these building blocks when adjusting layout; if you need new metadata (e.g., participant counts), add props here so every dashboard section stays consistent and testable.
*   **File:** `frontend/src/services/apiHooks.ts:1`
    *   **Summary:** Defines the canonical React Query hooks (`useUser`, `useRooms`, `useRoomById`) and query-key factories, automatically scoping room queries to the logged-in user (via `useAuthStore`) and setting sensible `staleTime` values.
    *   **Recommendation:** Any new dashboard data should use these hooks (or extend this module) instead of hitting `apiClient` directly. Stick to the provided query keys so cache invalidation and pagination (page/size params) work uniformly.

### Implementation Tips & Notes
*   **Tip:** `useRooms` throws if no authenticated user is present; always guard the hook with the ID from `useAuthStore` (as seen in `DashboardPage`) before invoking downstream logic.
*   **Tip:** For loading skeletons, reuse the Tailwind `animate-pulse` patterns already in `DashboardPage.tsx:36` so visual behavior stays consistent between profile and list sections.
*   **Note:** `roomsData` exposes pagination info (`page`, `totalPages`, `totalElements`); if you introduce paging controls, feed those values directly rather than recomputing counts.
*   **Note:** When displaying times, `RoomListCard.tsx:38` already uses `date-fns`’ `formatDistanceToNow`; match that utility for any new “recent activity” badges to keep locale/relative phrasing uniform.
*   **Warning:** `useRooms`’ query key includes `(userId, page, size)`—if you add filters (e.g., sort order), they must also be part of the key to prevent cache collisions and stale data.
