# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Implement `SessionDetailPage` component showing session report details. Free tier view: summary card (story count, consensus rate, average vote, participants list). Pro tier view: round-by-round table (story title, individual votes, average, median, consensus indicator), user consistency chart (bar chart showing each user's vote variance), export buttons (CSV, PDF). Use `useSessionDetail` hook fetching tier-appropriate data. Display UpgradeModal if Free tier user tries to view detailed data (403 response). Show download link when export job completes.

**Target Files:**
- frontend/src/pages/SessionDetailPage.tsx
- frontend/src/components/reporting/SessionSummaryCard.tsx
- frontend/src/components/reporting/RoundBreakdownTable.tsx
- frontend/src/components/reporting/UserConsistencyChart.tsx
- frontend/src/components/reporting/ExportControls.tsx

**Deliverables:** SessionDetailPage with tier-based content, Summary card for Free tier (basic stats), Round breakdown table for Pro tier (detailed data), User consistency chart (Recharts bar chart), Export buttons (CSV, PDF) triggering export API, Export job polling (check status every 2 seconds), Download link appears when job completes, UpgradeModal on 403 error (Free tier trying to access Pro data)

---

## Issues Detected

### Linting Errors

*   **Linting Error:** There is a linting error in `src/components/reporting/UserConsistencyChart.tsx` on line 45, column 45 due to use of `any` type. The error message is: "Unexpected any. Specify a different type @typescript-eslint/no-explicit-any"

*   **Linting Error:** There is a linting error in `src/pages/SessionDetailPage.tsx` on line 35, column 28 due to use of `any` type. The error message is: "Unexpected any. Specify a different type @typescript-eslint/no-explicit-any"

---

## Best Approach to Fix

You MUST fix the TypeScript `any` type violations by providing proper type definitions:

1. **In `src/components/reporting/UserConsistencyChart.tsx` at line 45:**
   - Replace the `any` type in the `CustomTooltip` function parameter with proper Recharts types
   - Import the correct types from `recharts` library: `TooltipProps` from `recharts`
   - Update the function signature to: `function CustomTooltip({ active, payload }: TooltipProps<number, string>)`

2. **In `src/pages/SessionDetailPage.tsx` at line 35:**
   - Replace the `(error as any)?.response?.status` type assertion with proper error handling
   - Use the axios error type: import `{ AxiosError }` from `axios`
   - Update the error check to properly type the error: `if (error && error instanceof Error && (error as AxiosError)?.response?.status === 403)`
   - Alternatively, use a more robust type guard approach by checking if the error has a response property first

These are the ONLY changes needed - do not modify any other code logic or structure.
