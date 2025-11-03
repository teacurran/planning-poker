# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I6.T6",
  "iteration_id": "I6",
  "iteration_goal": "Implement session history tracking, tier-based reporting (basic summaries for Free, detailed analytics for Pro/Enterprise), export functionality (CSV/PDF), and frontend reporting UI.",
  "description": "Implement `SessionDetailPage` component showing session report details. Free tier view: summary card (story count, consensus rate, average vote, participants list). Pro tier view: round-by-round table (story title, individual votes, average, median, consensus indicator), user consistency chart (bar chart showing each user's vote variance), export buttons (CSV, PDF). Use `useSessionDetail` hook fetching tier-appropriate data. Display UpgradeModal if Free tier user tries to view detailed data (403 response). Show download link when export job completes.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "Reporting tier features from product spec, ReportingService detail levels",
  "input_files": [
    "api/openapi.yaml",
    "frontend/src/components/subscription/UpgradeModal.tsx"
  ],
  "target_files": [
    "frontend/src/pages/SessionDetailPage.tsx",
    "frontend/src/components/reporting/SessionSummaryCard.tsx",
    "frontend/src/components/reporting/RoundBreakdownTable.tsx",
    "frontend/src/components/reporting/UserConsistencyChart.tsx",
    "frontend/src/components/reporting/ExportControls.tsx"
  ],
  "deliverables": "SessionDetailPage with tier-based content, Summary card for Free tier (basic stats), Round breakdown table for Pro tier (detailed data), User consistency chart (Recharts bar chart), Export buttons (CSV, PDF) triggering export API, Export job polling (check status every 2 seconds), Download link appears when job completes, UpgradeModal on 403 error (Free tier trying to access Pro data)",
  "acceptance_criteria": "Free tier user sees summary card only, Pro tier user sees round breakdown table and chart, Export CSV button creates job, polls status, shows download link, Download link opens exported CSV file, PDF export works similarly, 403 error triggers UpgradeModal, Chart displays user consistency correctly (variance bars)",
  "dependencies": [
    "I6.T5"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: reporting-requirements (from 01_Context_and_Drivers.md)

```markdown
#### Reporting Requirements
- **Free Tier:** Basic session summaries (story count, consensus rate, average vote)
- **Pro Tier:** Round-level detail, user consistency metrics, CSV/JSON/PDF export
- **Enterprise Tier:** Organizational dashboards, team trends, SSO-filtered reports, audit logs
```

### Context: usability-nfrs (from 01_Context_and_Drivers.md)

```markdown
#### Usability
- **Responsive Design:** Mobile-first Tailwind CSS with breakpoints for tablet/desktop
- **Accessibility:** WCAG 2.1 Level AA compliance for keyboard navigation and screen readers
- **Browser Support:** Last 2 versions of Chrome, Firefox, Safari, Edge
- **Internationalization:** English language in initial release, i18n framework for future localization
```

### Context: data-model-overview-erd (from 03_System_Structure_and_Data.md)

```markdown
#### Key Entities

| Entity | Purpose | Key Attributes |
|--------|---------|----------------|
| **SessionHistory** | Completed session record | `session_id` (PK), `room_id` (FK), `started_at`, `ended_at`, `total_rounds`, `total_stories`, `participants` (JSONB array), `summary_stats` (JSONB) |
| **Round** | Estimation round within session | `round_id` (PK), `room_id` (FK), `round_number`, `story_title`, `started_at`, `revealed_at`, `average`, `median`, `consensus_reached` |
| **Vote** | Individual estimation vote | `vote_id` (PK), `room_id` (FK), `round_number`, `participant_id`, `card_value`, `voted_at` |
```

### Context: end-to-end-testing (from 03_Verification_and_Glossary.md)

```markdown
#### End-to-End (E2E) Testing

**Scope:** Complete user journeys from browser through entire backend stack

**Framework:** Playwright (browser automation)

**Coverage Target:** Top 10 critical user flows

**Approach:**
- Simulate real user interactions (clicks, form submissions, navigation)
- Test against running application (frontend + backend + database)
- Mock external services where necessary (OAuth providers, Stripe)
- Visual regression testing for UI components (optional, future enhancement)
- Run in CI pipeline on staging environment before production deployment

**Examples:**
- `auth.spec.ts`: OAuth login flow → callback → token storage → dashboard redirect
- `voting.spec.ts`: Create room → join → cast vote → reveal → see results
- `subscription.spec.ts`: Upgrade to Pro → Stripe checkout → webhook → tier updated

**Acceptance Criteria:**
- All E2E tests pass (`npm run test:e2e`)
- Tests run headless in CI (no UI required)
- Screenshots captured on failure for debugging
- Test execution time <10 minutes for full suite
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `frontend/src/pages/SessionHistoryPage.tsx`
    *   **Summary:** This file implements the session list page with filtering, pagination, and loading/error states. It demonstrates the pattern for React Query integration, state management, and Tailwind CSS styling already established in the project.
    *   **Recommendation:** You MUST follow the exact same patterns used in this file for: (1) Loading skeleton UI structure, (2) Error handling with retry button, (3) Date filter implementation, (4) Pagination controls integration, (5) Tailwind dark mode classes (`dark:*`), (6) Responsive grid layouts (`grid-cols-1 md:grid-cols-4`). The SessionHistoryPage is located at `/Users/tea/dev/github/planning-poker/frontend/src/pages/SessionHistoryPage.tsx` and spans 300 lines with comprehensive examples.

*   **File:** `frontend/src/services/reportingApi.ts`
    *   **Summary:** This file provides the `useSessions` React Query hook for fetching session list data. It includes query key factories, authentication handling via `useAuthStore`, and proper TypeScript types.
    *   **Recommendation:** You MUST create a new hook `useSessionDetail(sessionId: string)` following the EXACT pattern used in `useSessions`. Key requirements: (1) Use `reportingQueryKeys.sessions.detail(sessionId)` for cache key, (2) Call `apiClient.get<DetailedSessionReportDTO>('/reports/sessions/{sessionId}')`, (3) Enable query only when `sessionId` is truthy, (4) Set `staleTime: 3 * 60 * 1000` (3 minutes), (5) Handle authentication via `useAuthStore` user check. The file is at `/Users/tea/dev/github/planning-poker/frontend/src/services/reportingApi.ts` with 115 lines showing the exact pattern to follow.

*   **File:** `frontend/src/components/subscription/UpgradeModal.tsx`
    *   **Summary:** This modal displays when users hit tier limits (403 errors). It shows required tier, features, pricing, and upgrade/contact buttons with Headless UI Dialog component. Located at `/Users/tea/dev/github/planning-poker/frontend/src/components/subscription/UpgradeModal.tsx` spanning 196 lines.
    *   **Recommendation:** You MUST reuse this exact component when handling 403 errors in SessionDetailPage. Import it and trigger with state like `const [showUpgrade, setShowUpgrade] = useState(false)`. Pass props: `isOpen={showUpgrade}`, `onClose={() => setShowUpgrade(false)}`, `requiredTier="PRO"`, `currentTier={user.subscriptionTier}`, `feature="detailed session reports"`. The component uses Headless UI's Dialog and Transition components with smooth animations.

*   **File:** `backend/src/main/java/com/scrumpoker/api/rest/ReportingController.java`
    *   **Summary:** This REST controller provides three key endpoints at `/Users/tea/dev/github/planning-poker/backend/src/main/java/com/scrumpoker/api/rest/ReportingController.java` (493 lines): (1) `GET /api/v1/reports/sessions/{sessionId}` (lines 242-313) returns `DetailedSessionReportDTO`, throws 403 for Free tier via FeatureGate, (2) `POST /api/v1/reports/export` (lines 315-409) creates export job and returns 202 Accepted with job ID, (3) `GET /api/v1/jobs/{jobId}` (lines 411-491) polls job status and returns download URL when complete.
    *   **Recommendation:** You MUST understand the API contract: (1) Session detail endpoint returns 403 for Free tier users accessing detailed data (caught by FeatureNotAvailableExceptionMapper), (2) Export endpoint returns 202 Accepted with `ExportJobResponse { jobId }`, (3) Job status endpoint returns `JobStatusResponse { jobId, status, downloadUrl, errorMessage, createdAt, completedAt }`. The frontend must poll `/jobs/{jobId}` every 2 seconds until status is COMPLETED or FAILED.

*   **File:** `frontend/src/types/reporting.ts`
    *   **Summary:** This file at `/Users/tea/dev/github/planning-poker/frontend/src/types/reporting.ts` (47 lines) currently defines TypeScript types for reporting API responses. It has `SessionSummaryDTO` (lines 11-22) and `SessionListResponse` (lines 29-35).
    *   **Recommendation:** You MUST extend this file to add missing types for detailed session report. Add: (1) `DetailedSessionReportDTO` with fields matching backend DTO (session metadata, rounds array with RoundDetailDTO, user_consistency_map: Record<string, number>), (2) `RoundDetailDTO` with round_number, story_title, votes array (VoteDetailDTO[]), average, median, consensus_reached, (3) `VoteDetailDTO` with participant_name, card_value, voted_at, (4) `ExportJobResponse { jobId: string }`, (5) `JobStatusResponse { jobId, status, downloadUrl?, errorMessage?, createdAt, completedAt? }`, (6) `ExportFormat` type as 'CSV' | 'PDF'.

### Implementation Tips & Notes

*   **Tip:** The SessionHistoryPage already implements the loading skeleton pattern you need. Copy its structure (lines 75-105) for your loading state, replacing the table skeleton with card/chart skeletons appropriate for the detail view. The skeleton uses `animate-pulse` with `bg-gray-300 dark:bg-gray-700` for shimmer effect.

*   **Tip:** For export job polling, use React Query's `refetchInterval` option in a separate `useExportJobStatus(jobId)` hook: `useQuery({ queryKey: ['exportJob', jobId], queryFn: () => apiClient.get('/jobs/' + jobId), enabled: !!jobId, refetchInterval: (data) => data?.status === 'PENDING' || data?.status === 'PROCESSING' ? 2000 : false })`. This automatically stops polling when job completes or fails.

*   **Tip:** The project uses Recharts library (installed, visible in package.json). For the user consistency chart, use `<BarChart>` with `<Bar dataKey="variance" fill="#3b82f6" />`. Data format: `[{ name: "Alice", variance: 2.3 }, { name: "Bob", variance: 1.5 }]`. Import from `recharts` and use ResponsiveContainer for responsive sizing: `<ResponsiveContainer width="100%" height={300}><BarChart data={chartData}>...</BarChart></ResponsiveContainer>`.

*   **Note:** The backend returns session detail as `DetailedSessionReportDTO` which includes BOTH summary data (for Free tier fallback) AND detailed round data (for Pro tier). The FeatureGate in ReportingService (lines 294-296 in ReportingController.java) throws FeatureNotAvailableException for Free tier users. The frontend should detect 403 errors in the React Query `onError` callback and display UpgradeModal.

*   **Note:** All components in this project follow mobile-first responsive design with Tailwind breakpoints: `sm:` (640px), `md:` (768px), `lg:` (1024px), `xl:` (1280px). Your tables and charts MUST be scrollable on mobile (`overflow-x-auto` wrapper) and properly sized on desktop. The SessionHistoryPage shows this pattern with `container mx-auto px-4 py-8 max-w-7xl`.

*   **Warning:** The backend's error responses use snake_case JSON (e.g., `error_code`, `error_message`). However, the current ErrorResponse DTO at lines 3-4 in various mapper files uses camelCase. Check the actual backend implementation in `ErrorResponse.java` to confirm field names. For FeatureNotAvailableException, the mapper returns JSON with `error` and `message` fields.

*   **Warning:** Export download URLs from S3 expire after 24 hours (per backend S3Adapter implementation). Display a warning message near the download link: "Download link expires in 24 hours". Use an info icon from Heroicons and subtle text color: `text-sm text-gray-600 dark:text-gray-400`.

*   **Best Practice:** Create separate sub-components for each section (SessionSummaryCard.tsx, RoundBreakdownTable.tsx, UserConsistencyChart.tsx, ExportControls.tsx) rather than building everything in SessionDetailPage.tsx. This improves testability and follows the existing component organization pattern seen in `frontend/src/components/reporting/` directory (currently has SessionListTable.tsx and PaginationControls.tsx).

*   **Best Practice:** Use semantic HTML for accessibility: `<table>` with `<thead>`, `<tbody>`, `<th>`, `<td>` for the round breakdown table (not div-based layout), `<section>` for content sections with `<h2>` headings, `<button>` for interactive elements (not `<div onClick>`). The SessionHistoryPage demonstrates proper semantic structure with header, filters section, table section, and pagination footer.

*   **Critical Implementation Detail:** The session detail route should be `/reports/sessions/:sessionId` to match RESTful conventions. Extract sessionId from URL params using `const { sessionId } = useParams<{ sessionId: string }>()` from react-router-dom. Add this route to App.tsx inside the PrivateRoute wrapper.

*   **Critical Implementation Detail:** The backend uses snake_case for JSON field names. Your TypeScript interfaces MUST use snake_case to match: `session_id`, `room_title`, `started_at`, `total_stories`, `consensus_rate`, `average_vote`, `user_consistency_map`, etc. Do NOT convert to camelCase - this is intentional to match the backend serialization exactly.

*   **Export Job Flow:** When user clicks "Export CSV" button: (1) Call `POST /api/v1/reports/export` with `{ sessionId, format: 'CSV' }`, (2) Backend returns 202 with `{ jobId }`, (3) Store jobId in component state, (4) Start polling `GET /api/v1/jobs/{jobId}` every 2 seconds, (5) When status becomes 'COMPLETED', display download link with `downloadUrl` from response, (6) If status becomes 'FAILED', display error message. Use React Query for all API calls.

*   **Tier Detection Logic:** Check user's subscription tier from `useAuthStore` before rendering Pro features. Pattern: `const { user } = useAuthStore(); const isPro = user?.subscriptionTier === 'PRO' || user?.subscriptionTier === 'PRO_PLUS' || user?.subscriptionTier === 'ENTERPRISE';`. If `!isPro`, render SessionSummaryCard only. If `isPro`, render full detail view with RoundBreakdownTable and UserConsistencyChart. ALSO handle 403 errors from API as backup.

---

**End of Task Briefing Package**
