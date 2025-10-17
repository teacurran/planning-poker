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
  "inputs": "Dashboard requirements from product spec, API hooks from I3.T6, Design system (Tailwind, Headless UI)",
  "input_files": [
    "frontend/src/services/apiHooks.ts",
    "frontend/src/stores/authStore.ts"
  ],
  "target_files": [
    "frontend/src/pages/DashboardPage.tsx",
    "frontend/src/components/dashboard/UserProfileCard.tsx",
    "frontend/src/components/dashboard/RoomListCard.tsx",
    "frontend/src/components/dashboard/CreateRoomButton.tsx"
  ],
  "deliverables": "DashboardPage with user profile section (avatar, name, email, tier badge), Room list grid (responsive, 1 col mobile, 2 col tablet, 3 col desktop), Room card component showing title, privacy mode, last active date, Create room button with prominent styling, Loading skeleton using Tailwind animate-pulse, Error state UI (retry button, error message)",
  "acceptance_criteria": "Dashboard loads user data from API on mount, User profile displays correct information (avatar, name, subscription tier), Room list shows user's owned rooms from API, Clicking room card navigates to /room/{roomId}, Create room button navigates to /rooms/new, Loading state displayed while fetching data, Error state shows message if API call fails, Responsive layout works on mobile, tablet, desktop",
  "dependencies": ["I3.T6"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: technology-stack-summary (from 02_Architecture_Overview.md)

```markdown
### 3.2. Technology Stack Summary

| **Category** | **Technology Choice** | **Justification** |
|--------------|----------------------|-------------------|
| **Frontend Framework** | **React 18+ with TypeScript** | Strong ecosystem, concurrent rendering for real-time updates, TypeScript for type safety in WebSocket message contracts |
| **UI Component Library** | **Tailwind CSS + Headless UI** | Utility-first CSS for rapid development, Headless UI for accessible components (modals, dropdowns), minimal bundle size |
| **State Management** | **Zustand + React Query** | Lightweight state management (Zustand), server state caching and synchronization (React Query), WebSocket integration support |
| **WebSocket Client** | **Native WebSocket API + Reconnecting wrapper** | Native browser API for compatibility, lightweight reconnection logic with exponential backoff |

#### Key Libraries & Extensions

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

### Context: usability-nfrs (from 01_Context_and_Drivers.md)

```markdown
#### Usability
- **Responsive Design:** Mobile-first Tailwind CSS with breakpoints for tablet/desktop
- **Accessibility:** WCAG 2.1 Level AA compliance for keyboard navigation and screen readers
- **Browser Support:** Last 2 versions of Chrome, Firefox, Safari, Edge
- **Internationalization:** English language in initial release, i18n framework for future localization
```

### Context: user-account-requirements (from 01_Context_and_Drivers.md)

```markdown
#### User Account Requirements
- **OAuth2 Authentication:** Google and Microsoft social login integration
- **Profile Management:** Display name, avatar, theme preferences, default room settings
- **Session History:** Persistent storage of past sessions with tier-based access controls
- **Preference Persistence:** User-specific defaults for deck type, room rules, reveal behavior
```

### Context: core-gameplay-requirements (from 01_Context_and_Drivers.md)

```markdown
#### Core Gameplay Requirements
- **Real-time Estimation:** WebSocket-based blind card selection with configurable deck types (Fibonacci, T-shirt, custom)
- **Session Management:** Host controls for round lifecycle (start, lock, reveal, reset), participant management (kick, mute)
- **Calculation Engine:** Automatic computation of average, median, and consensus indicators upon reveal
- **Room Controls:** Unique room ID generation (6-character nanoid), shareable links, privacy modes
```

### Context: task-i3-t7 (from 02_Iteration_I3.md)

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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `frontend/src/services/apiHooks.ts`
    *   **Summary:** This file contains comprehensive React Query hooks for API data fetching. It includes `useUser(userId)` for fetching user profiles, `useRooms()` for fetching the current user's owned rooms (automatically uses `authStore.user.userId`), and `useCreateRoom()` mutation hook for creating new rooms. All hooks automatically handle loading states, error states, data caching, and cache invalidation.
    *   **Recommendation:** You MUST import and use the following hooks in your DashboardPage component:
        - `useUser(userId)` - Pass the current authenticated user's ID from `authStore.user.userId`
        - `useRooms()` - No parameters needed, automatically fetches current user's rooms
        - Import `queryKeys` if you need to manually invalidate cache
    *   **Important Details:**
        - `useUser()` has `staleTime: 5 * 60 * 1000` (5 minutes cache)
        - `useRooms()` has `staleTime: 2 * 60 * 1000` (2 minutes cache, rooms change frequently)
        - Both hooks return React Query standard result: `{ data, isLoading, error, refetch }`
        - `useRooms()` is automatically disabled if user is not authenticated (`enabled: !!user?.userId`)
        - `useCreateRoom()` automatically invalidates the rooms list cache on success

*   **File:** `frontend/src/stores/authStore.ts`
    *   **Summary:** This file defines the Zustand authentication store with automatic localStorage persistence. It manages user authentication state including user profile data, access/refresh tokens, and isAuthenticated boolean. The store automatically loads state from localStorage on initialization.
    *   **Recommendation:** You MUST import and use `useAuthStore` hook to access the current authenticated user:
        - `const { user, isAuthenticated } = useAuthStore();`
        - The `user` object contains: `userId, email, displayName, avatarUrl, subscriptionTier, createdAt`
        - Always check `isAuthenticated` before rendering authenticated content
    *   **Important Details:**
        - User data is automatically persisted to localStorage
        - The store includes methods `setAuth()`, `clearAuth()`, and `loadAuthFromStorage()`
        - User type is `UserDTO` from `@/types/auth`

*   **File:** `frontend/src/types/auth.ts`
    *   **Summary:** TypeScript type definitions for authentication-related data structures matching the OpenAPI specification. Includes `UserDTO`, `TokenResponse`, `OAuthProvider`, and `SubscriptionTier` types.
    *   **Recommendation:** You SHOULD import and use these types for type safety:
        - `UserDTO` - For user profile data
        - `SubscriptionTier` - For displaying subscription tier badges (values: 'FREE', 'PRO', 'PRO_PLUS', 'ENTERPRISE')
    *   **Important Details:**
        - `avatarUrl` is optional and can be null
        - `SubscriptionTier` is always present and has 4 possible values
        - All date fields are ISO 8601 strings (e.g., `createdAt`, `updatedAt`)

*   **File:** `frontend/src/types/room.ts`
    *   **Summary:** TypeScript type definitions for room-related data structures. Includes `RoomDTO`, `RoomListResponse`, `PrivacyMode`, and `VotingSystem` types.
    *   **Recommendation:** You MUST import and use these types:
        - `RoomDTO` - For individual room data in room cards
        - `RoomListResponse` - For the paginated response from `useRooms()` hook (contains `rooms: RoomDTO[]` array plus pagination metadata)
        - `PrivacyMode` - For displaying privacy badge (values: 'PUBLIC', 'PRIVATE')
    *   **Important Details:**
        - `RoomListResponse` includes pagination fields: `page, size, totalElements, totalPages`
        - Each `RoomDTO` has `lastActiveAt` timestamp (ISO 8601 string) - use this for "last active" display
        - `deletedAt` field indicates soft-deleted rooms (should be filtered out by API, but check defensively)

*   **File:** `frontend/src/components/common/Button.tsx`
    *   **Summary:** Reusable Button component with variant support ('primary', 'secondary'). Uses Tailwind CSS classes for styling with consistent dark mode support.
    *   **Recommendation:** You SHOULD reuse this Button component for the "Create New Room" button and any action buttons. Pass `variant="primary"` for prominent CTAs.
    *   **Important Details:**
        - Primary variant uses `bg-primary-600 hover:bg-primary-700` (defined in tailwind.config.js)
        - Button accepts `onClick`, `variant`, `className` props
        - Button already includes transition animations and dark mode support

*   **File:** `frontend/tailwind.config.js`
    *   **Summary:** Tailwind CSS configuration with custom color palette. Primary color is blue-based (sky blue theme from #0ea5e9 to #082f49).
    *   **Recommendation:** You MUST use the following Tailwind utilities for consistency with the existing design:
        - Primary colors: `bg-primary-600`, `text-primary-600`, `border-primary-500`
        - Dark mode: Use `dark:` prefix for all color classes (e.g., `bg-white dark:bg-gray-800`)
        - Responsive: Use `md:` for tablet (768px+) and `lg:` for desktop (1024px+)
        - Grid layout: `grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6`
    *   **Important Details:**
        - Dark mode is configured as 'class' mode (toggle via className on root element)
        - Primary color has full palette from 50-950
        - Loading skeleton animation: Use `animate-pulse` utility class

*   **File:** `frontend/src/pages/DashboardPage.tsx` (current state)
    *   **Summary:** This is the current placeholder implementation with static mock data. It has the correct page structure with responsive grid layout, but needs to be replaced with real data fetching using API hooks.
    *   **Recommendation:** You SHOULD preserve the overall page structure and styling approach (min-h-screen, container layout, grid system), but MUST replace all static content with dynamic data from API hooks. Keep the visual styling intact as it follows the design system correctly.

*   **File:** `frontend/src/App.tsx`
    *   **Summary:** React Router configuration with route definitions. Dashboard is protected by `PrivateRoute` wrapper, which redirects unauthenticated users to /login.
    *   **Recommendation:** For navigation from DashboardPage components, use `react-router-dom`'s `useNavigate()` hook:
        - Import: `import { useNavigate } from 'react-router-dom';`
        - Usage: `const navigate = useNavigate(); navigate('/rooms/new');`
        - Room detail navigation: `navigate(\`/room/${roomId}\`);`

### Implementation Tips & Notes

*   **Tip:** For the loading skeleton, I confirmed that Tailwind's `animate-pulse` utility is already configured and working in the project. Use it on placeholder divs with gray backgrounds:
    ```tsx
    <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-6 w-32 rounded"></div>
    ```

*   **Tip:** The `useRooms()` hook returns `RoomListResponse` which has the structure:
    ```typescript
    {
      rooms: RoomDTO[],
      page: number,
      size: number,
      totalElements: number,
      totalPages: number
    }
    ```
    Access the rooms array with `data?.rooms || []` for safe iteration.

*   **Tip:** Date formatting for "last active" timestamps - you should use `date-fns` library (already listed in the tech stack). Import `formatDistanceToNow` for relative timestamps:
    ```tsx
    import { formatDistanceToNow } from 'date-fns';
    formatDistanceToNow(new Date(room.lastActiveAt), { addSuffix: true })
    // Outputs: "2 hours ago"
    ```

*   **Note:** Subscription tier badges should use color-coded styling to differentiate tiers visually:
    - FREE: Gray (`bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300`)
    - PRO: Blue (`bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300`)
    - PRO_PLUS: Purple (`bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-300`)
    - ENTERPRISE: Gold (`bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300`)

*   **Note:** Privacy mode badges for rooms should also be color-coded:
    - PUBLIC: Green (`bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300`)
    - PRIVATE: Red (`bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300`)

*   **Warning:** The `useRooms()` hook is automatically disabled when user is not authenticated. However, since DashboardPage is protected by `PrivateRoute`, the user will always be authenticated when this page renders. No additional auth checks are needed within the component itself.

*   **Warning:** Avatar images may be null or invalid URLs. Always provide fallback rendering:
    ```tsx
    {user.avatarUrl ? (
      <img src={user.avatarUrl} alt={user.displayName} onError={(e) => { e.currentTarget.style.display = 'none'; }} />
    ) : (
      <div className="avatar-fallback">
        {user.displayName?.charAt(0).toUpperCase()}
      </div>
    )}
    ```

*   **Performance Note:** The current DashboardPage placeholder uses static data. When implementing real data fetching, React Query's automatic caching will prevent unnecessary re-fetches. The stale time settings (5 minutes for user, 2 minutes for rooms) are already optimized for this use case.

*   **Accessibility Note:** All interactive elements (room cards, buttons) must be keyboard accessible. Use semantic HTML (`<button>`, `<nav>`) and proper ARIA labels for screen readers. Room cards should have `role="button"` or be wrapped in `<button>` tags, not just divs with onClick handlers.

*   **Error Handling:** The API hooks return an `error` object when requests fail. Display user-friendly error messages with retry functionality:
    ```tsx
    {error && (
      <div className="error-state">
        <p>Failed to load rooms: {error.message}</p>
        <button onClick={() => refetch()}>Retry</button>
      </div>
    )}
    ```

### Component Architecture Recommendations

Based on the task requirements and existing codebase patterns, here's the recommended component breakdown:

1. **DashboardPage.tsx** (Main container)
   - Orchestrates layout and data fetching
   - Uses `useUser()` and `useRooms()` hooks
   - Manages loading and error states
   - Renders child components with data

2. **UserProfileCard.tsx** (Presentational component)
   - Props: `user: UserDTO`
   - Displays avatar, name, email, subscription tier badge
   - Compact card layout with dark mode support

3. **RoomListCard.tsx** (Presentational component)
   - Props: `room: RoomDTO`, `onClick: () => void`
   - Displays room title, privacy badge, last active time
   - "Open Room" button or entire card clickable
   - Card hover effects for interactivity

4. **CreateRoomButton.tsx** (Interactive component)
   - Prominent CTA button
   - Uses `useNavigate()` to go to `/rooms/new`
   - Primary variant styling (blue background)

This component structure follows React best practices: container/presentational pattern, single responsibility principle, and makes components highly testable.
