# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I6.T5",
  "iteration_id": "I6",
  "iteration_goal": "Implement session history tracking, tier-based reporting (basic summaries for Free, detailed analytics for Pro/Enterprise), export functionality (CSV/PDF), and frontend reporting UI.",
  "description": "Implement `SessionHistoryPage` component displaying user's past sessions. List sessions with: date, room title, round count, participants count, consensus rate. Pagination controls (previous/next, page numbers). Click session to navigate to detail page. Filter by date range (date picker). Sort by date (newest/oldest). Use `useSessions` React Query hook for data fetching. Display loading skeleton, empty state (no sessions), error state.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "Reporting API endpoints from I6.T4, Session list requirements",
  "input_files": [
    "api/openapi.yaml"
  ],
  "target_files": [
    "frontend/src/pages/SessionHistoryPage.tsx",
    "frontend/src/components/reporting/SessionListTable.tsx",
    "frontend/src/components/reporting/PaginationControls.tsx",
    "frontend/src/services/reportingApi.ts"
  ],
  "deliverables": "SessionHistoryPage with session list table, Pagination controls (previous, next, page numbers), Date range filter (date picker inputs), Sort controls (newest/oldest), Session row click navigates to detail page, Loading, empty, and error states",
  "acceptance_criteria": "Page loads user's sessions from API, Table displays session metadata (date, room, rounds, participants), Pagination works (clicking next loads next page), Date filter applies (API called with from/to params), Sort changes order (newest first vs. oldest first), Clicking session navigates to /reports/sessions/{sessionId}, Empty state shows \"No sessions found\" message",
  "dependencies": [
    "I6.T4"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: REST API Endpoints Overview (from 04_Behavior_and_Communication.md)

```markdown
**Reporting & Analytics:**
- `GET /api/v1/reports/sessions` - List session history (tier-gated pagination, filters)
- `GET /api/v1/reports/sessions/{sessionId}` - Detailed session report (tier-gated round detail)
- `POST /api/v1/reports/export` - Generate export job (CSV/PDF), returns job ID
- `GET /api/v1/jobs/{jobId}` - Poll export job status, retrieve download URL
```

### Context: API Style (from 04_Behavior_and_Communication.md)

```markdown
**Primary API Style:** **RESTful JSON API (OpenAPI 3.1 Specification)**

**Rationale:**
- **Simplicity & Familiarity:** REST over HTTPS provides a well-understood contract for CRUD operations on resources (users, rooms, subscriptions)
- **Tooling Ecosystem:** OpenAPI specification enables automatic client SDK generation (TypeScript for React frontend), API documentation (Swagger UI), and contract testing
- **Caching Support:** HTTP semantics (ETags, Cache-Control headers) enable browser and CDN caching for read-heavy endpoints (room configurations, user profiles)
- **Versioning Strategy:** URL-based versioning (`/api/v1/`) for backward compatibility during iterative releases
```

### Context: Usability NFRs (from 01_Context_and_Drivers.md)

```markdown
#### Usability
- **Responsive Design:** Mobile-first Tailwind CSS with breakpoints for tablet/desktop
- **Accessibility:** WCAG 2.1 Level AA compliance for keyboard navigation and screen readers
- **Browser Support:** Last 2 versions of Chrome, Firefox, Safari, Edge
- **Internationalization:** English language in initial release, i18n framework for future localization
```

### Context: Frontend Technology Stack (from 02_Architecture_Overview.md)

```markdown
| **Frontend Framework** | **React 18+ with TypeScript** | Strong ecosystem, concurrent rendering for real-time updates, TypeScript for type safety in WebSocket message contracts |
| **UI Component Library** | **Tailwind CSS + Headless UI** | Utility-first CSS for rapid development, Headless UI for accessible components (modals, dropdowns), minimal bundle size |
| **State Management** | **Zustand + React Query** | Lightweight state management (Zustand), server state caching and synchronization (React Query), WebSocket integration support |

**Frontend (React):**
- `@tanstack/react-query` - Server state management and caching
- `zustand` - Client-side state management (UI, WebSocket connection state)
- `react-hook-form` - Form validation and submission
- `zod` - Schema validation for API responses and WebSocket messages
- `date-fns` - Date/time formatting for session history
- `recharts` - Charting library for analytics dashboards
- `@headlessui/react` - Accessible UI components
- `heroicons` - Icon library
```

### Context: GET /api/v1/reports/sessions Endpoint (from api/openapi.yaml)

```yaml
/api/v1/reports/sessions:
  get:
    tags:
      - Reports
    summary: List session history
    description: |
      Returns paginated session history with filters.
      **Tier Requirements:**
      - Free tier: Last 30 days, max 10 results
      - Pro tier: Last 90 days, max 100 results
      - Pro Plus/Enterprise: Unlimited history
    operationId: listSessions
    parameters:
      - name: from
        in: query
        schema:
          type: string
          format: date
        description: Start date (ISO 8601 format)
        example: "2025-01-01"
      - name: to
        in: query
        schema:
          type: string
          format: date
        description: End date (ISO 8601 format)
        example: "2025-01-31"
      - name: roomId
        in: query
        schema:
          type: string
          pattern: '^[a-z0-9]{6}$'
        description: Filter by room ID
        example: "abc123"
      - $ref: '#/components/parameters/PageParam'
      - $ref: '#/components/parameters/SizeParam'
    responses:
      '200':
        description: Session list retrieved
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/SessionListResponse'
```

### Context: SessionListResponse Schema (from api/openapi.yaml)

```yaml
SessionListResponse:
  type: object
  required:
    - sessions
    - page
    - size
    - totalElements
    - totalPages
  properties:
    sessions:
      type: array
      items:
        $ref: '#/components/schemas/SessionSummaryDTO'
    page:
      type: integer
      example: 0
    size:
      type: integer
      example: 20
    totalElements:
      type: integer
      example: 42
    totalPages:
      type: integer
      example: 3
```

### Context: SessionSummaryDTO Schema (from api/openapi.yaml)

```yaml
SessionSummaryDTO:
  type: object
  required:
    - sessionId
    - roomId
    - startedAt
    - endedAt
    - totalRounds
    - totalStories
  properties:
    sessionId:
      type: string
      format: uuid
      description: Session unique identifier
      example: "123e4567-e89b-12d3-a456-426614174000"
    roomId:
      type: string
      pattern: '^[a-z0-9]{6}$'
      description: Room identifier
      example: "abc123"
    roomTitle:
      type: string
      description: Room title at time of session
      example: "Sprint 42 Planning"
    startedAt:
      type: string
      format: date-time
      description: Session start timestamp
      example: "2025-01-15T10:00:00Z"
    endedAt:
      type: string
      format: date-time
      description: Session end timestamp
      example: "2025-01-15T12:00:00Z"
    totalRounds:
      type: integer
      description: Number of estimation rounds
      example: 8
    totalStories:
      type: integer
      description: Number of stories estimated
      example: 8
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `frontend/src/services/apiHooks.ts`
    *   **Summary:** This file provides a well-established pattern for creating React Query hooks for API data fetching. It includes a centralized `queryKeys` factory for cache management, `useQuery` hooks for data fetching (e.g., `useUser`, `useRooms`), and `useMutation` hooks for data modifications (e.g., `useCreateRoom`, `useDeleteRoom`).
    *   **Recommendation:** You MUST follow this exact pattern when creating the `useSessions` hook in the new `reportingApi.ts` file. Use the same structure: define query keys in the `queryKeys` factory, implement reactive `useQuery` hooks, configure stale time appropriately (2-5 minutes for reporting data), and add comprehensive JSDoc comments.
    *   **Key Pattern:** All hooks use `apiClient` from `./api.ts` for API calls, extract data with `response.data`, include `enabled` flags where appropriate (e.g., `enabled: !!userId`), and provide custom `UseQueryOptions` for flexibility.

*   **File:** `frontend/src/pages/DashboardPage.tsx`
    *   **Summary:** This page demonstrates the complete pattern for implementing a page with data fetching, loading states, error states, and responsive UI. It uses `useUser` and `useRooms` hooks, displays a loading skeleton during data fetch, shows an error state with retry button, and renders a responsive grid for data cards.
    *   **Recommendation:** You SHOULD use this as a template for your `SessionHistoryPage.tsx`. Copy the loading skeleton pattern (lines 45-88), the error state pattern (lines 92-134), and the responsive grid layout (lines 173-181). The page demonstrates how to handle combined loading states, refetch logic, and empty states.
    *   **Key Patterns:**
        - Loading skeleton uses `animate-pulse` with gray backgrounds matching light/dark mode
        - Error state includes an SVG icon, error message, and retry button
        - Empty state has centered layout with icon, message, and CTA button
        - Responsive grid: `grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6`

*   **File:** `frontend/src/components/dashboard/RoomListCard.tsx`
    *   **Summary:** This component shows the pattern for creating clickable cards with data display, badges, timestamps, and actions. It uses `date-fns` for timestamp formatting (`formatDistanceToNow`), implements keyboard accessibility (onKeyDown for Enter/Space), and includes hover states.
    *   **Recommendation:** You SHOULD create a similar `SessionListTable.tsx` component (or individual session row components). Use the same accessibility patterns (role="button", tabIndex, aria-label), timestamp formatting with `date-fns`, and badge styling with Tailwind utility classes for different states.
    *   **Key Pattern:** The card is clickable via both onClick and keyboard events, uses `truncate` for long text, implements responsive padding and shadows, and includes `flex-shrink-0` for badges to prevent wrapping.

*   **File:** `frontend/src/services/api.ts`
    *   **Summary:** This file configures the Axios API client with authentication, token refresh, and 403 error handling (FeatureNotAvailable). The apiClient has base URL configuration, request interceptors for Authorization headers, and response interceptors for error handling.
    *   **Recommendation:** You MUST use this `apiClient` instance for all API calls in your `reportingApi.ts` file. Do NOT create a new Axios instance. The existing client already handles authentication, token refresh, and tier-based access errors (403 FeatureNotAvailable will trigger the UpgradeModal).

*   **File:** `frontend/src/App.tsx`
    *   **Summary:** This is the main application router. It shows where to add new routes using React Router v6 patterns. All authenticated routes are wrapped in `<PrivateRoute>`.
    *   **Recommendation:** You MUST add a new route for the SessionHistoryPage at `/reports/sessions` (or `/sessions` or `/dashboard/sessions` - verify with team preference). Wrap it in `<PrivateRoute>` since only authenticated users can view their session history. Add the route between lines 46-53 in the existing `<Routes>` block.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/dto/SessionListResponse.java`
    *   **Summary:** This DTO defines the actual backend response structure. IMPORTANT: The backend uses `has_next` (boolean) instead of `totalPages` from the OpenAPI spec. The response also has `total` (int) for total count instead of `totalElements`.
    *   **Recommendation:** You MUST match the TypeScript interface to the actual backend implementation, NOT the OpenAPI spec. Use `has_next: boolean` and `total: number` in your TypeScript types. This discrepancy exists between spec and implementation.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/SessionSummaryDTO.java`
    *   **Summary:** This shows the actual field names used in the backend response. All fields use snake_case JSON property names (e.g., `session_id`, `room_title`, `started_at`). The backend returns numeric types as strings for precision (BigDecimal → string in JSON).
    *   **Recommendation:** You MUST use snake_case field names in your TypeScript interfaces to match the backend serialization. For numeric fields like `consensus_rate` and `average_vote`, use `number` type in TypeScript (they'll be parsed from JSON strings automatically). Use ISO 8601 string format for timestamps (startedAt, endedAt).

### Implementation Tips & Notes

*   **Tip:** The project uses `date-fns` for date formatting. You SHOULD use `format(date, 'MMM dd, yyyy')` for displaying session dates in the table, and `formatDistanceToNow(date, { addSuffix: true })` for relative timestamps like "2 days ago".

*   **Tip:** For the date range filter, you can use native HTML5 `<input type="date">` elements styled with Tailwind, or integrate a library like `react-datepicker` if more advanced features are needed. The HTML5 approach is simpler and requires no additional dependencies.

*   **Tip:** Pagination controls should use the `has_next` boolean from the response to determine if the "Next" button should be enabled. Since the backend doesn't return `totalPages`, calculate it client-side if needed: `Math.ceil(total / size)` or just use `has_next` for simpler next/previous controls.

*   **Note:** The project follows a consistent directory structure: pages go in `frontend/src/pages/`, reusable components go in `frontend/src/components/[feature]/`, services go in `frontend/src/services/`, and types go in `frontend/src/types/`. You MUST create `frontend/src/components/reporting/` directory for reporting-specific components.

*   **Note:** The project uses Tailwind CSS with dark mode support (`dark:` variant). All components MUST include dark mode styles. The pattern is: `bg-white dark:bg-gray-800`, `text-gray-900 dark:text-white`, etc. Check existing components for the correct dark mode color palette.

*   **Note:** The backend returns JSON with snake_case field names, but frontend TypeScript typically uses camelCase. The existing code handles this inconsistency by defining types with snake_case to match the API exactly. You SHOULD follow this pattern for reporting types.

*   **Warning:** The task specifies creating a `useSessions` hook, but this should be added to a NEW file `frontend/src/services/reportingApi.ts` (not `apiHooks.ts`). The existing `apiHooks.ts` is for core user/room functionality. Create a new file following the same pattern for reporting-specific hooks and import `apiClient` from `./api.ts`.

*   **Warning:** When implementing pagination, be aware that the backend uses 0-indexed page numbers (page 0, 1, 2...). The UI can display 1-indexed page numbers to users (page 1, 2, 3...) but must send 0-indexed values to the API.

*   **Tip:** For sort controls (newest/oldest), the backend endpoint likely accepts a `sort` query parameter (verify in ReportingController.java). If not implemented yet, you can implement client-side sorting by reversing the sessions array, or add the sort parameter to the API call for server-side sorting.

*   **Note:** The existing routing pattern in `App.tsx` uses React Router v6. To add the SessionHistoryPage route, use: `<Route path="/reports/sessions" element={<PrivateRoute><SessionHistoryPage /></PrivateRoute>} />`. Make sure to import the new page component at the top of the file.

*   **Accessibility Note:** All interactive elements must be keyboard accessible. Use semantic HTML where possible (`<button>`, `<a>`, `<table>`), add `aria-label` attributes for screen readers, and ensure tab order is logical. The existing components demonstrate this pattern - follow their lead.

---

**End of Task Briefing Package**
