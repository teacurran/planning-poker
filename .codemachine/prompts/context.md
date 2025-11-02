# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I4.T8",
  "iteration_id": "I4",
  "iteration_goal": "Implement WebSocket-based real-time voting functionality including connection management, vote casting, round lifecycle (start, reveal, reset), Redis Pub/Sub for event broadcasting across stateless nodes, and frontend voting UI.",
  "description": "Create Playwright E2E tests for frontend voting flow. Test: user joins room, sees participant list, selects card, card selection reflected in UI, host reveals round, reveal animation plays, statistics displayed. Test multi-user scenario (open 2 browser contexts as different users, vote in parallel, verify both see reveal). Test reconnection (disconnect WebSocket, verify reconnection message, state preserved). Mock backend WebSocket or use real backend in test environment.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "Voting UI components from I4.T6, Playwright testing patterns for WebSocket",
  "input_files": [
    "frontend/src/pages/RoomPage.tsx",
    "frontend/src/components/room/*.tsx"
  ],
  "target_files": [
    "frontend/e2e/voting.spec.ts"
  ],
  "deliverables": "E2E test: join room, cast vote, see participant update, E2E test: host reveal, see reveal animation and results, E2E test: multi-user voting (2 browser contexts), E2E test: WebSocket reconnection",
  "acceptance_criteria": "`npm run test:e2e` executes voting tests, Card selection updates UI instantly, Reveal animation plays after host reveal, Multi-user test verifies synchronization, Reconnection test verifies WebSocket resilience, Tests run in CI pipeline",
  "dependencies": ["I4.T6"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: WebSocket Connection Lifecycle (from 04_Behavior_and_Communication.md)

```markdown
#### WebSocket Connection Lifecycle

**Connection Establishment:**
1. Client initiates WebSocket handshake: `wss://api.scrumpoker.com/ws/room/{roomId}?token={jwt}`
2. Server validates JWT token, extracts user/participant identity
3. Server checks room existence and user authorization (privacy mode enforcement)
4. Server subscribes connection to Redis Pub/Sub channel: `room:{roomId}`
5. Server broadcasts `room.participant_joined.v1` event to existing participants
6. Server sends initial room state snapshot to newly connected client

**Heartbeat Protocol:**
- Client sends `ping` frame every 30 seconds
- Server responds with `pong` frame
- Connection terminated if no `ping` received within 60 seconds (2x interval)

**Graceful Disconnection:**
1. Client sends `room.leave.v1` message before closing connection
2. Server persists disconnection timestamp in `RoomParticipant` table
3. Server broadcasts `room.participant_left.v1` to remaining participants
4. Server unsubscribes from Redis channel if no more local connections to room

**Ungraceful Disconnection (Network Failure):**
1. Server detects missing heartbeat, marks connection as stale
2. Server broadcasts `room.participant_disconnected.v1` with grace period
3. If client reconnects within 5 minutes, restores session without re-join
4. If timeout expires, participant marked as left, votes remain valid

**Reconnection Strategy (Client-Side):**
- Detect connection loss via WebSocket `onclose` event
- Attempt reconnection with exponential backoff: 1s, 2s, 4s, 8s, 16s (max)
- Include `lastEventId` in reconnection handshake to retrieve missed events
- Server replays events from Redis or database within 5-minute window
```

### Context: Key Interaction Flow - Vote Casting and Round Reveal (from 04_Behavior_and_Communication.md)

```markdown
##### Description

This sequence diagram illustrates the critical real-time workflow for a Scrum Poker estimation round, from initial vote casting through final reveal and consensus calculation. The flow demonstrates WebSocket message handling, Redis Pub/Sub event distribution across stateless application nodes, and optimistic UI updates with server reconciliation.

**Scenario:**
1. Two participants (Alice and Bob) connected to different application nodes due to load balancer sticky session routing
2. Alice casts vote "5", Bob casts vote "8"
3. Host triggers reveal after all votes submitted
4. System calculates statistics (average: 6.5, median: 6.5, no consensus due to variance)
5. All participants receive synchronized reveal event with results

**WebSocket Message Types:**
- `room.join.v1` - Participant joins room
- `room.leave.v1` - Participant exits room
- `vote.cast.v1` - Participant submits vote
- `vote.recorded.v1` - Server confirms vote persisted (broadcast to room)
- `round.reveal.v1` - Host triggers card reveal
- `round.revealed.v1` - Server broadcasts reveal with statistics
- `round.reset.v1` - Host resets round for re-voting
- `chat.message.v1` - Participant sends chat message
- `presence.update.v1` - Participant status change (ready, away)
- `error.v1` - Server-side validation or authorization error
```

### Context: Asynchronous WebSocket Pattern (from 04_Behavior_and_Communication.md)

```markdown
##### Asynchronous WebSocket (Event-Driven)

**Use Cases:**
- Real-time vote casting and vote state updates
- Room state synchronization (participant joins/leaves, host controls)
- Card reveal events with animated timing coordination
- Presence updates (typing indicators, ready states)
- Chat messages and emoji reactions

**Pattern Characteristics:**
- Persistent connection maintained for session duration
- Events broadcast via Redis Pub/Sub to all application nodes
- Client-side event handlers update local state optimistically, reconcile on server confirmation
- Heartbeat/ping-pong protocol for connection liveness detection
- Automatic reconnection with exponential backoff on connection loss

**Message Flow:**
1. Client sends WebSocket message: `{"type": "vote.cast.v1", "requestId": "uuid", "payload": {"cardValue": "5"}}`
2. Server validates, persists vote to PostgreSQL
3. Server publishes event to Redis channel: `room:{roomId}`
4. All application nodes subscribed to channel receive event
5. Each node broadcasts to locally connected clients in that room
6. Clients receive: `{"type": "vote.recorded.v1", "requestId": "uuid", "payload": {"participantId": "...", "votedAt": "..."}}`
```

### Context: Task I4.T6 - Create Voting Room UI Components (from 02_Iteration_I4.md)

```markdown
*   **Task 4.6: Create Voting Room UI Components (Frontend)**
    *   **Description:** Implement React components for real-time voting UI. `RoomPage` component: join room, display participants list, show current round state (waiting for votes, votes cast count, revealed results). `VotingCard` component: deck of estimation cards (1, 2, 3, 5, 8, 13, ?, ∞, ☕), click to select, send vote.cast message. `ParticipantList` component: list of participants with vote status (voted/not voted, role badge). `RevealView` component: animated card flip, display all votes, show statistics (average, median, consensus indicator). `HostControls` component: buttons for start round, reveal, reset (visible only to host). Use WebSocketManager to send/receive messages. Update UI optimistically (instant feedback) and reconcile with server events.
    *   **Deliverables:**
        *   RoomPage orchestrating voting flow
        *   VotingCard displaying Fibonacci deck (customizable deck future iteration)
        *   Card selection sends vote.cast WebSocket message
        *   ParticipantList shows real-time vote status (names, voted checkmarks)
        *   RevealView animates card flip on reveal event, shows all votes and stats
        *   HostControls with Start Round, Reveal, Reset buttons (conditional rendering)
        *   Optimistic UI updates (instant card selection feedback)
    *   **Acceptance Criteria:**
        *   RoomPage loads and joins room via WebSocket
        *   Clicking card sends vote.cast message (visible in Network tab)
        *   ParticipantList updates when other users vote (via vote.recorded event)
        *   Host clicking Reveal triggers round.reveal message
        *   RevealView displays after reveal event with all votes visible
        *   Statistics display (average, median, consensus badge)
        *   UI responsive on mobile, tablet, desktop
        *   Animations smooth (card flip using CSS transitions)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `frontend/e2e/auth.spec.ts`
    *   **Summary:** This file contains comprehensive E2E tests for OAuth authentication flow using Playwright. It demonstrates the project's testing patterns including: mock setup, route interception, localStorage assertions, multi-step authentication flows, and error handling.
    *   **Recommendation:** You MUST follow the exact same Playwright testing patterns established in this file. Specifically:
        - Use `test.describe()` and `test.beforeEach()` for test organization
        - Call `setupCommonRouteMocks(page)` at the start of `beforeEach()` to set up API route mocking
        - Use `await page.route()` to intercept and mock API/WebSocket requests
        - Use `await page.evaluate()` for reading/asserting localStorage/sessionStorage
        - Use `await expect(page).toHaveURL()` for navigation assertions
        - Use descriptive test names starting with "should"

*   **File:** `frontend/e2e/fixtures/mockOAuthResponse.ts`
    *   **Summary:** This file provides mock data fixtures and helper functions for E2E tests. It exports mock users, tokens, rooms, and setup utilities like `setupAuthenticatedState()`, `setupCommonRouteMocks()`, and `clearAllStorage()`.
    *   **Recommendation:** You SHOULD create a similar fixtures file for voting tests: `frontend/e2e/fixtures/mockVotingData.ts`. This should export:
        - Mock room data with voting configuration
        - Mock participant lists (Alice as HOST, Bob as VOTER)
        - Mock WebSocket message payloads (vote.cast, vote.recorded, round.revealed)
        - Mock statistics data (average, median, consensus)
        - Helper functions for setting up WebSocket route mocks

*   **File:** `frontend/playwright.config.ts`
    *   **Summary:** This file configures Playwright test runner with settings for baseURL, retries, reporters, and webServer (auto-starts Vite dev server on `npm run test:e2e`).
    *   **Recommendation:** No changes needed to this file. The existing configuration is correct. Tests will automatically start the dev server on localhost:5173.

*   **File:** `frontend/src/pages/RoomPage.tsx`
    *   **Summary:** This is the main room page component orchestrating the voting flow. It uses `useWebSocket` hook for WebSocket connection, maintains optimistic local state (`selectedCard`, `hasVotedOptimistic`), handles card selection, and renders ParticipantList, DeckSelector, RevealView, and HostControls components. Key event handlers: `handleCardSelect`, `handleStartRound`, `handleReveal`, `handleReset`.
    *   **Recommendation:** Your E2E tests MUST verify the complete user journey through this component:
        - Initial load and connection status display (connecting → connected)
        - Participant list rendering with real-time updates
        - Card selection triggering optimistic UI update (shows "Vote Cast!" message)
        - Host controls visibility (only visible to host role)
        - Reveal animation and statistics display (RevealView component)
        - Round reset clearing votes and returning to voting state

*   **File:** `frontend/src/components/room/VotingCard.tsx`, `ParticipantList.tsx`, `RevealView.tsx`, `HostControls.tsx`, `DeckSelector.tsx`
    *   **Summary:** These are the individual UI components that compose the voting experience. They receive props from RoomPage and render specific parts of the UI (cards, participant badges, reveal animation, host buttons).
    *   **Recommendation:** You should test these components indirectly through user interactions in your E2E tests. For example:
        - `DeckSelector`: Test by clicking card buttons and verifying vote message sent
        - `ParticipantList`: Test by verifying participant names and vote checkmarks appear
        - `RevealView`: Test by verifying statistics (average, median) and animated card display after reveal
        - `HostControls`: Test by clicking "Start Round", "Reveal", "Reset" buttons (when role is HOST)

*   **File:** `backend/src/test/java/com/scrumpoker/api/websocket/VotingFlowIntegrationTest.java`
    *   **Summary:** This file contains comprehensive backend integration tests for the WebSocket voting flow. It demonstrates how to test WebSocket connections with JWT authentication, multi-client scenarios, Redis Pub/Sub message broadcasting, and reconnection handling. Uses `WebSocketTestClient` helper class.
    *   **Recommendation:** You SHOULD study this file to understand the expected WebSocket message flow and timing:
        - Connect → send `room.join.v1` → receive `room.participant_joined.v1`
        - Send `vote.cast.v1` → receive `vote.recorded.v1` (both sender and other clients)
        - Send `round.reveal.v1` (host only) → receive `round.revealed.v1` with votes array and stats
        - Send `round.reset.v1` (host only) → receive `round.reset.v1`
        - Reconnection flow: disconnect → reconnect → state preserved

*   **File:** `frontend/package.json`
    *   **Summary:** This file defines npm scripts. The `test:e2e` script runs `playwright test`, which executes all tests in `frontend/e2e/` directory.
    *   **Recommendation:** Your new test file `frontend/e2e/voting.spec.ts` will be automatically discovered and executed by the existing `npm run test:e2e` command. No package.json changes needed.

### Implementation Tips & Notes

*   **Tip 1: WebSocket Mocking Strategy**
    - Playwright does NOT natively support WebSocket mocking via `page.route()`. The `page.route()` API only intercepts HTTP/HTTPS requests.
    - You have TWO options for WebSocket testing:
        1. **Option A (Recommended):** Use the REAL backend WebSocket server during E2E tests. Start both backend (Quarkus) and frontend (Vite) servers, then run Playwright tests. This tests the full integration.
        2. **Option B (Advanced):** Mock WebSocket at the browser level using `page.evaluateOnNewDocument()` to override `WebSocket` constructor and simulate server messages. This is more complex but gives full control.
    - For this task, I RECOMMEND **Option A** (real backend) because:
        - The backend integration tests already verify WebSocket message handling
        - E2E tests should verify real frontend-backend integration
        - WebSocket mocking in Playwright is non-trivial and error-prone

*   **Tip 2: Multi-Browser Context Pattern**
    - To test multi-user voting (Alice and Bob voting simultaneously), you MUST use Playwright's `browser.newContext()` to create 2 isolated browser contexts with separate localStorage/cookies.
    - Pattern:
        ```typescript
        test('multi-user voting synchronization', async ({ browser }) => {
          // Create two contexts (simulate two different users)
          const aliceContext = await browser.newContext();
          const bobContext = await browser.newContext();

          const alicePage = await aliceContext.newPage();
          const bobPage = await bobContext.newPage();

          // Set up Alice as HOST
          await setupAuthenticatedState(alicePage, mockAliceUser);
          // Set up Bob as VOTER
          await setupAuthenticatedState(bobPage, mockBobUser);

          // Navigate both to the same room
          await alicePage.goto('/room/test-room-123');
          await bobPage.goto('/room/test-room-123');

          // Alice starts round
          await alicePage.click('button:has-text("Start New Round")');

          // Both vote
          await alicePage.click('[data-card-value="5"]');
          await bobPage.click('[data-card-value="8"]');

          // Alice reveals
          await alicePage.click('button:has-text("Reveal")');

          // Verify both see reveal
          await expect(alicePage.locator('text=Average: 6.5')).toBeVisible();
          await expect(bobPage.locator('text=Average: 6.5')).toBeVisible();
        });
        ```

*   **Tip 3: WebSocket Reconnection Testing**
    - To test reconnection, you need to programmatically close the WebSocket connection and verify the UI shows "Reconnecting..." then "Connected" after reconnect.
    - You can simulate disconnection using:
        ```typescript
        // Force close WebSocket connection
        await page.evaluate(() => {
          // Access the WebSocket instance from the window/app
          // This assumes your WebSocket manager exposes the socket
          const ws = (window as any).__testWebSocket; // You may need to expose this for testing
          if (ws) ws.close();
        });

        // Verify reconnection indicator appears
        await expect(page.locator('text=Disconnected - Reconnecting...')).toBeVisible();

        // Wait for reconnection
        await expect(page.locator('text=Connected')).toBeVisible({ timeout: 10000 });
        ```

*   **Tip 4: Test Data Setup**
    - You MUST create a room and participants in the backend database BEFORE running E2E tests.
    - Options:
        1. Use API calls in `test.beforeAll()` to create room via REST API
        2. Use a test database seeding script
        3. Have the backend integration tests create the room, then reference the same room ID in E2E tests
    - I RECOMMEND option 1 (API calls) because it's most maintainable.

*   **Tip 5: Waiting for WebSocket Messages**
    - WebSocket events are asynchronous. After clicking a button (e.g., vote card), you need to wait for the UI to update.
    - Use Playwright's auto-waiting assertions:
        ```typescript
        // Click vote card
        await page.click('[data-card-value="5"]');

        // Wait for optimistic UI update (immediate)
        await expect(page.locator('text=Vote Cast!')).toBeVisible();

        // Wait for server confirmation (participant list checkmark)
        await expect(page.locator('[data-participant="alice"] [data-voted="true"]')).toBeVisible();
        ```

*   **Note 1: CSS Animation Testing**
    - The RevealView component has a card flip animation. Playwright can verify animations by:
        1. Checking CSS classes are applied (e.g., `animate-card-flip`)
        2. Taking screenshots before/after animation
        3. Waiting for animation to complete (use `{ timeout: 5000 }` for slow animations)
    - Example:
        ```typescript
        // Host clicks reveal
        await alicePage.click('button:has-text("Reveal")');

        // Verify reveal animation starts
        await expect(alicePage.locator('.animate-card-flip')).toBeVisible();

        // Wait for animation to complete and statistics to display
        await expect(alicePage.locator('text=Average:')).toBeVisible({ timeout: 5000 });
        ```

*   **Note 2: Test Isolation and Cleanup**
    - Each test MUST start with a clean room state (no votes, no round).
    - Use `test.beforeEach()` to reset room state via API or database.
    - Use `test.afterEach()` to close all browser contexts and pages.

*   **Warning:** The backend server MUST be running on `http://localhost:8080` for E2E tests to work (unless you mock WebSocket).
    - The frontend Vite server auto-starts via `playwright.config.ts` webServer config.
    - You need to manually start the backend Quarkus server before running E2E tests:
        ```bash
        cd backend
        mvn quarkus:dev
        ```
    - Alternatively, update the GitHub Actions CI workflow to start the backend server before running E2E tests.

*   **Note 3: Test Naming Convention**
    - Follow the existing pattern from `auth.spec.ts`:
        - Test file: `voting.spec.ts`
        - Test suite: `test.describe('Voting Flow', () => { ... })`
        - Individual tests: `test('should cast vote and update participant list', async ({ page }) => { ... })`
    - Use descriptive test names that clearly state the expected behavior.

*   **Note 4: Accessibility Testing**
    - The acceptance criteria mention "UI responsive on mobile, tablet, desktop".
    - You can test responsive behavior using Playwright's device emulation:
        ```typescript
        test('voting UI should be responsive on mobile', async ({ browser }) => {
          const context = await browser.newContext({
            ...devices['iPhone 12'],
          });
          const page = await context.newPage();

          await page.goto('/room/test-room-123');

          // Verify card deck is visible and scrollable on mobile
          await expect(page.locator('[data-testid="deck-selector"]')).toBeVisible();
        });
        ```

*   **Critical Note: Backend Must Be Running**
    - Since we're using Option A (real backend), you MUST have the backend server running before executing E2E tests.
    - The playwright.config.ts only starts the frontend Vite dev server, NOT the backend.
    - For local development, run in two terminals:
        ```bash
        # Terminal 1: Backend
        cd backend && mvn quarkus:dev

        # Terminal 2: Frontend E2E tests (Vite auto-starts via playwright config)
        cd frontend && npm run test:e2e
        ```
    - For CI/CD, the GitHub Actions workflow MUST be updated to start the backend before running E2E tests.

*   **Important: Room and User Setup**
    - The RoomPage component expects to receive a valid `roomId` in the URL path (`/room/{roomId}`).
    - You need to create a test room and participants BEFORE navigating to the room page.
    - Suggested approach:
        ```typescript
        test.beforeAll(async () => {
          // Create test room via REST API
          const response = await fetch('http://localhost:8080/api/v1/rooms', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${testAccessToken}`
            },
            body: JSON.stringify({
              title: 'E2E Test Room',
              privacyMode: 'PUBLIC',
              config: { deckType: 'fibonacci' }
            })
          });
          const room = await response.json();
          testRoomId = room.roomId;
        });
        ```

*   **Data Attribute Selectors**
    - The existing components may not have `data-*` attributes for testing.
    - You have two options:
        1. Add `data-testid` attributes to key UI elements (preferred for test stability)
        2. Use text content selectors (e.g., `button:has-text("Reveal")`)
    - For this task, I recommend using text selectors to avoid modifying the component files (since the task only asks for test creation, not component changes).

---

**End of Task Briefing Package**

Good luck with the implementation! Start with a simple single-user voting flow test, then build up to multi-user and reconnection scenarios. Remember to ensure the backend is running before executing tests.
