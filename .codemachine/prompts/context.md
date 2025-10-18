# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I4.T6",
  "iteration_id": "I4",
  "iteration_goal": "Implement WebSocket-based real-time voting functionality including connection management, vote casting, round lifecycle (start, reveal, reset), Redis Pub/Sub for event broadcasting across stateless nodes, and frontend voting UI.",
  "description": "Implement React components for real-time voting UI. `RoomPage` component: join room, display participants list, show current round state (waiting for votes, votes cast count, revealed results). `VotingCard` component: deck of estimation cards (1, 2, 3, 5, 8, 13, ?, ∞, ☕), click to select, send vote.cast message. `ParticipantList` component: list of participants with vote status (voted/not voted, role badge). `RevealView` component: animated card flip, display all votes, show statistics (average, median, consensus indicator). `HostControls` component: buttons for start round, reveal, reset (visible only to host). Use WebSocketManager to send/receive messages. Update UI optimistically (instant feedback) and reconcile with server events.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "Voting UI requirements from product spec, WebSocketManager from I4.T5, Design system (Tailwind, Headless UI)",
  "input_files": [
    "frontend/src/services/websocket.ts",
    "frontend/src/hooks/useWebSocket.ts",
    "frontend/src/stores/roomStore.ts"
  ],
  "target_files": [
    "frontend/src/pages/RoomPage.tsx",
    "frontend/src/components/room/VotingCard.tsx",
    "frontend/src/components/room/ParticipantList.tsx",
    "frontend/src/components/room/RevealView.tsx",
    "frontend/src/components/room/HostControls.tsx",
    "frontend/src/components/room/DeckSelector.tsx"
  ],
  "deliverables": "RoomPage orchestrating voting flow, VotingCard displaying Fibonacci deck (customizable deck future iteration), Card selection sends vote.cast WebSocket message, ParticipantList shows real-time vote status (names, voted checkmarks), RevealView animates card flip on reveal event, shows all votes and stats, HostControls with Start Round, Reveal, Reset buttons (conditional rendering), Optimistic UI updates (instant card selection feedback)",
  "acceptance_criteria": "RoomPage loads and joins room via WebSocket, Clicking card sends vote.cast message (visible in Network tab), ParticipantList updates when other users vote (via vote.recorded event), Host clicking Reveal triggers round.reveal message, RevealView displays after reveal event with all votes visible, Statistics display (average, median, consensus badge), UI responsive on mobile, tablet, desktop, Animations smooth (card flip using CSS transitions)",
  "dependencies": [
    "I4.T1",
    "I4.T2",
    "I4.T3",
    "I4.T4",
    "I4.T5"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: WebSocket Protocol Specification (from api/websocket-protocol.md)

**Key Message Types for Voting Flow:**

**Client → Server Messages:**
- `vote.cast.v1` - Participant casts vote for the current round
  - Payload: `{ "cardValue": "5" }` (string, 1-10 characters, must match current deck)
  - Valid Fibonacci deck values: `"0"`, `"1"`, `"2"`, `"3"`, `"5"`, `"8"`, `"13"`, `"21"`, `"?"`
  - Error conditions: 4002 (invalid vote), 4003 (forbidden - observer role)

- `round.start.v1` (Host Only)
  - Payload: `{ "storyTitle": "...", "timerDurationSeconds": 120 }`
  - Error: 4003 (forbidden), 4005 (invalid state - round already in progress)

- `round.reveal.v1` (Host Only)
  - Payload: `{}` (empty)
  - Error: 4003 (forbidden), 4005 (no active round, no votes cast, already revealed)

- `round.reset.v1` (Host Only)
  - Payload: `{ "clearVotes": true }`
  - Error: 4003 (forbidden), 4005 (no active round)

**Server → Client Messages (Broadcasts):**
- `room.state.v1` (Unicast) - Initial state snapshot after joining
- `room.participant_joined.v1` - New participant joined
- `room.participant_left.v1` - Participant left gracefully
- `vote.recorded.v1` - Vote confirmed (does NOT reveal value)
  - Payload: `{ "participantId": "...", "votedAt": "...", "hasVoted": true }`
- `round.started.v1` - New round started
- `round.revealed.v1` - Votes revealed with statistics
  - Payload includes: `votes[]` array with all vote values, `statistics` object
- `round.reset.v1` - Round reset
- `error.v1` - Error response with code and message

**Message Envelope Format:**
```json
{
  "type": "message_type.v1",
  "requestId": "uuid-v4",
  "payload": { /* message-specific data */ }
}
```

**Connection Lifecycle:**
1. Client connects: `wss://api.../ws/room/{roomId}?token={jwt}`
2. Server validates JWT, sends `room.state.v1` with initial snapshot
3. Heartbeat: ping/pong every 30 seconds
4. Graceful disconnect: send `room.leave.v1` before closing

### Context: Voting Sequence Diagram (from 04_Behavior_and_Communication.md)

**Vote Casting Flow:**
1. Client sends `vote.cast.v1` with cardValue
2. Client optimistically updates UI (hide card, show "Voted" state)
3. Server validates, persists vote to PostgreSQL
4. Server publishes `vote.recorded.v1` to Redis Pub/Sub channel
5. All clients receive broadcast (including sender for confirmation)
6. Clients update participant list to show vote status (checkmark)

**Reveal Flow:**
1. Host sends `round.reveal.v1` (empty payload)
2. Server queries all votes from database
3. Server calculates statistics (average, median, consensus)
4. Server updates Round entity with stats
5. Server publishes `round.revealed.v1` with votes array and statistics
6. All clients receive broadcast
7. Clients animate card flip and display all votes with statistics

**Key Considerations:**
- **Vote Secrecy:** `vote.recorded.v1` does NOT include card value
- **Optimistic Updates:** Client-side UI should update immediately on vote cast
- **Server Reconciliation:** Server broadcast confirms action and corrects any mismatches
- **Role-Based Actions:** Only HOST can start, reveal, reset rounds
- **Error Handling:** Display error messages from `error.v1` payloads

### Context: Communication Patterns (from 04_Behavior_and_Communication.md)

**WebSocket Pattern Characteristics:**
- Persistent connection for session duration
- Events broadcast via Redis Pub/Sub to all application nodes
- Client-side event handlers update local state optimistically, reconcile on server confirmation
- Heartbeat/ping-pong protocol for connection liveness detection
- Automatic reconnection with exponential backoff on connection loss

**Optimistic UI Updates:**
- Vote casting: Immediately hide card, show "Voted" state (before server confirmation)
- Reconciliation: Update vote status when `vote.recorded.v1` broadcast received
- Error handling: Revert optimistic update if `error.v1` received

**Statistics Calculation:**
- Average: Mean of numeric votes (null if no numeric votes)
- Median: Median of numeric votes
- Mode: Most frequent vote value
- Consensus: Boolean indicating if variance is below threshold
- Distribution: Map of card value to count

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

#### File: `frontend/src/services/websocket.ts`
**Summary:** Complete WebSocket connection manager implementing the protocol specification. Handles connection lifecycle, message serialization, event handler registration, and reconnection logic.

**Recommendation:** You MUST import and use the singleton `wsManager` instance from this file. DO NOT create new WebSocket connections directly. The manager provides:
- `connect(roomId, token, displayName, role)` - establishes connection
- `send<T>(type, payload): string` - sends messages, returns requestId
- `on<T>(messageType, handler): () => void` - registers event handlers, returns unsubscribe function
- `onStatusChange(listener): () => void` - tracks connection status
- `isConnected(): boolean` - checks connection state

**Critical Details:**
- Connection automatically sends `room.join.v1` after establishing WebSocket
- Implements exponential backoff reconnection (1s, 2s, 4s, 8s, max 16s)
- Stores `lastEventId` for event replay on reconnection
- Heartbeat mechanism runs automatically

#### File: `frontend/src/hooks/useWebSocket.ts`
**Summary:** React hook that wraps `wsManager` and integrates with auth and room stores. Automatically manages connection based on roomId parameter and handles all WebSocket event types.

**Recommendation:** You MUST use this hook in `RoomPage.tsx` instead of calling `wsManager` directly. It provides:
- `connectionStatus: ConnectionStatus` - current connection state
- `isConnected: boolean` - helper for conditional rendering
- `send<T>(type, payload): string | null` - send messages
- `error: ErrorPayload | null` - last error received

**Critical Details:**
- Hook automatically connects when `roomId` is provided and user is authenticated
- Hook automatically disconnects on unmount or when roomId changes
- Event handlers are already registered and update `roomStore` state
- You DO NOT need to manually register handlers for standard message types

**Integration Pattern:**
```tsx
const { connectionStatus, isConnected, send, error } = useWebSocket(roomId);

const handleVote = (cardValue: string) => {
  send('vote.cast.v1', { cardValue });
};
```

#### File: `frontend/src/stores/roomStore.ts`
**Summary:** Zustand store managing real-time room state synchronized via WebSocket events. Contains all room data: participants, current round, revealed votes, statistics.

**Recommendation:** You MUST use this store to access room state. The `useWebSocket` hook automatically updates this store when receiving server events.

**Available State:**
- `roomId: string | null`
- `title: string | null`
- `config: RoomConfig | null` (includes deckType, timerEnabled, etc.)
- `participants: Map<string, Participant>` (keyed by participantId)
- `currentRound: Round | null`
- `revealedVotes: Vote[] | null` (populated only after reveal)
- `statistics: VoteStatistics | null`

**Available Actions (already called by useWebSocket):**
- `getParticipantsArray(): Participant[]` - get participants as array for rendering
- `getCurrentParticipant(userId): Participant | undefined` - get current user's participant data
- `getParticipant(participantId): Participant | undefined` - get specific participant

**Integration Pattern:**
```tsx
const participants = useRoomStore((state) => state.getParticipantsArray());
const currentRound = useRoomStore((state) => state.currentRound);
const revealedVotes = useRoomStore((state) => state.revealedVotes);
const statistics = useRoomStore((state) => state.statistics);
const config = useRoomStore((state) => state.config);
```

#### File: `frontend/src/stores/authStore.ts`
**Summary:** Zustand store managing authentication state, including user data and tokens.

**Recommendation:** Use this to access current user information for determining role and permissions.

**Integration Pattern:**
```tsx
const user = useAuthStore((state) => state.user);
const currentUserId = user?.userId;
```

#### File: `frontend/src/types/websocket.ts`
**Summary:** Complete TypeScript types matching the WebSocket protocol specification.

**Recommendation:** Import types from this file for type safety. All message payload types are defined here.

**Key Types:**
- `Participant` - participant data structure
- `Round` - round information
- `Vote` - revealed vote data
- `VoteStatistics` - statistics object
- `RoomConfig` - room configuration
- `MessageType` - constants for message type strings

#### File: `frontend/src/pages/RoomPage.tsx`
**Summary:** Currently contains basic placeholder UI with static voting card buttons. You will replace this entire implementation.

**Recommendation:** You MUST rewrite this file to implement the complete voting UI using the components you create.

### Implementation Tips & Notes

#### Tip 1: Card Deck Values
The task specifies Fibonacci deck values: `1, 2, 3, 5, 8, 13, ?, ∞, ☕`

However, the WebSocket protocol lists valid Fibonacci values as: `"0"`, `"1"`, `"2"`, `"3"`, `"5"`, `"8"`, `"13"`, `"21"`, `"?"`

**Resolution:** Use the protocol-specified values to ensure compatibility with backend validation. The `config.deckType` determines available cards. For now, implement Fibonacci deck with values from protocol spec. Custom deck support (`∞`, `☕`) can be added in future iterations based on `config.customDeck`.

#### Tip 2: Optimistic UI Updates
You SHOULD implement optimistic updates for vote casting:
1. When user clicks card, immediately update local UI (disable cards, show "Voted" badge)
2. Send `vote.cast.v1` message
3. Wait for `vote.recorded.v1` broadcast for confirmation
4. If `error.v1` received, revert optimistic update and show error

**Pattern:**
```tsx
const [hasVotedOptimistic, setHasVotedOptimistic] = useState(false);

const handleCardClick = (cardValue: string) => {
  // Optimistic update
  setHasVotedOptimistic(true);

  // Send to server
  send('vote.cast.v1', { cardValue });
};
```

#### Tip 3: Role-Based Rendering
You MUST conditionally render host controls based on user's role. The participant role is available from the room store:

```tsx
const participants = useRoomStore((state) => state.getParticipantsArray());
const user = useAuthStore((state) => state.user);

const currentParticipant = participants.find(
  (p) => p.participantId === user?.userId
);

const isHost = currentParticipant?.role === 'HOST';

// Render host controls only if isHost
{isHost && <HostControls />}
```

#### Tip 4: Reveal Animation
You SHOULD use CSS transitions for the card flip animation. Tailwind provides utilities for this:

```tsx
// Reveal animation example
<div className={`transform transition-transform duration-500 ${
  revealed ? 'rotate-y-180' : ''
}`}>
  {/* Card content */}
</div>
```

Add custom CSS for 3D flip effect in a component-specific CSS file or inline styles.

#### Tip 5: Responsive Design
The acceptance criteria requires mobile, tablet, desktop responsiveness. Use Tailwind's responsive utilities:

```tsx
<div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
  {/* Cards */}
</div>
```

#### Tip 6: Connection Status Display
You SHOULD display connection status to users for transparency:

```tsx
const { connectionStatus, error } = useWebSocket(roomId);

{connectionStatus === 'connecting' && <div>Connecting...</div>}
{connectionStatus === 'disconnected' && <div>Disconnected. Reconnecting...</div>}
{error && <div>Error: {error.message}</div>}
```

#### Tip 7: Statistics Display
The `statistics` object includes:
- `average: number | null` - display formatted to 1 decimal place
- `median: string | null` - display as-is
- `consensusReached: boolean` - show green badge if true, yellow/red if false
- `distribution: Record<string, number>` - can be visualized as bar chart (optional for this task)

#### Note: Component Organization
You should create the following component files in `frontend/src/components/room/`:
1. `VotingCard.tsx` - Individual card component (reusable)
2. `ParticipantList.tsx` - List of participants with vote status
3. `RevealView.tsx` - Revealed votes display with statistics
4. `HostControls.tsx` - Start/Reveal/Reset buttons
5. `DeckSelector.tsx` - Grid of voting cards (uses VotingCard components)

The `RoomPage.tsx` should orchestrate these components and manage the overall layout.

#### Warning: WebSocket Message Sending
DO NOT send WebSocket messages before the connection is established. Always check `isConnected` before calling `send()`:

```tsx
const handleAction = () => {
  if (!isConnected) {
    console.warn('Cannot send message: not connected');
    return;
  }
  send('round.reveal.v1', {});
};
```

#### Note: Participant Vote Status
The `Participant` type has a `hasVoted: boolean` field that is updated via `vote.recorded.v1` broadcasts. Use this to display checkmarks or status badges:

```tsx
{participant.hasVoted ? (
  <CheckCircleIcon className="h-5 w-5 text-green-500" />
) : (
  <ClockIcon className="h-5 w-5 text-gray-400" />
)}
```

#### Important: Current Round State
The `currentRound` can be `null` if no round has been started. You MUST handle this case:

```tsx
const currentRound = useRoomStore((state) => state.currentRound);

if (!currentRound) {
  return <div>Waiting for host to start a round...</div>;
}
```

#### Testing Tip: Network Tab Verification
The acceptance criteria states "Clicking card sends vote.cast message (visible in Network tab)". To verify:
1. Open browser DevTools → Network tab
2. Filter by "WS" (WebSocket)
3. Click on the WebSocket connection
4. View "Messages" subtab
5. Click a voting card
6. Verify outgoing message with type `vote.cast.v1`

---

**End of Task Briefing Package**
