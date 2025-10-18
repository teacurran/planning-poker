# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I4.T5",
  "iteration_id": "I4",
  "iteration_goal": "Implement WebSocket-based real-time voting functionality including connection management, vote casting, round lifecycle (start, reveal, reset), Redis Pub/Sub for event broadcasting across stateless nodes, and frontend voting UI.",
  "description": "Implement `WebSocketManager` class managing WebSocket connection lifecycle for React frontend. Features: connect to `/ws/room/{roomId}` with JWT token, handle connection states (connecting, connected, disconnected), implement reconnection logic with exponential backoff (1s, 2s, 4s, 8s, max 16s), send messages (vote.cast, chat.message), receive messages and dispatch to event handlers, maintain heartbeat (respond to ping with pong). Integrate with Zustand store (`roomStore`) to update room state on incoming events. Create React hook `useWebSocket(roomId)` for components.",
  "agent_type_hint": "FrontendAgent",
  "inputs": "WebSocket protocol spec from I2.T2, WebSocket connection lifecycle requirements, React + Zustand patterns",
  "input_files": [
    "api/websocket-protocol.md",
    "frontend/src/stores/authStore.ts"
  ],
  "target_files": [
    "frontend/src/services/websocket.ts",
    "frontend/src/hooks/useWebSocket.ts",
    "frontend/src/stores/roomStore.ts"
  ],
  "deliverables": "WebSocketManager with methods: connect, disconnect, send, on(messageType, handler), Reconnection logic with exponential backoff, Heartbeat pong response, Message serialization (JS object → JSON string), Message deserialization and event dispatching, useWebSocket hook providing connection status and send function, roomStore integration updating state on vote.recorded, round.revealed events",
  "acceptance_criteria": "WebSocket connects successfully to backend, Connection state tracked (connecting, connected, disconnected), Reconnection triggers automatically on disconnect, Sent messages appear in backend logs (vote.cast received), Received events update roomStore state, Heartbeat keeps connection alive (no timeout disconnect), Hook provides connection status to components",
  "dependencies": [
    "I4.T1"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: WebSocket Protocol Specification (from api/websocket-protocol.md)

The complete WebSocket protocol specification has been defined and includes:

**Endpoint URL Pattern:**
```
wss://api.planningpoker.example.com/ws/room/{roomId}?token={jwt}
```

**Message Envelope Format:**
```json
{
  "type": "message_type.v1",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    // Message-specific payload
  }
}
```

**Key Message Types (Client → Server):**
- `room.join.v1` - Participant joins room (sent immediately after connection)
- `room.leave.v1` - Participant leaves room gracefully
- `vote.cast.v1` - Participant submits vote for current round
- `round.start.v1` - Start new estimation round (Host only)
- `round.reveal.v1` - Reveal votes for current round (Host only)
- `round.reset.v1` - Reset current round for re-voting (Host only)
- `chat.message.v1` - Send chat message to room

**Key Message Types (Server → Client):**
- `room.state.v1` - Initial room state snapshot (sent upon connection)
- `room.participant_joined.v1` - Participant joined room (broadcast)
- `room.participant_left.v1` - Participant left gracefully (broadcast)
- `vote.recorded.v1` - Vote confirmed (does NOT reveal value) (broadcast)
- `round.started.v1` - New round started (broadcast)
- `round.revealed.v1` - Votes revealed with statistics (broadcast)
- `round.reset.v1` - Round reset (broadcast)
- `chat.message.v1` - Chat message broadcast
- `error.v1` - Error response (unicast)

**Connection Lifecycle:**
1. **WebSocket Handshake**: Client initiates with JWT token as query parameter
2. **Server Setup**: Server validates JWT and subscribes to Redis Pub/Sub channel
3. **Room Join**: Client MUST send `room.join.v1` within 10 seconds
4. **Heartbeat Protocol**: Client sends ping every 30 seconds, server responds with pong
5. **Graceful Disconnection**: Client sends `room.leave.v1` before closing

**Reconnection Strategy (Client-Side):**
- Detect connection loss via WebSocket `onclose` event
- Attempt reconnection with exponential backoff: 1s, 2s, 4s, 8s, 16s (max)
- Include `lastEventId` in reconnection handshake to retrieve missed events
- Server replays events from Redis or database within 5-minute window

**Error Codes (4000-4999 range):**
- `4000` - UNAUTHORIZED (invalid or expired JWT token)
- `4001` - ROOM_NOT_FOUND (room does not exist or deleted)
- `4002` - INVALID_VOTE (vote validation failed)
- `4003` - FORBIDDEN (insufficient permissions)
- `4004` - VALIDATION_ERROR (request payload validation failed)
- `4005` - INVALID_STATE (action not valid in current room/round state)
- `4008` - POLICY_VIOLATION (protocol violation, e.g., didn't send room.join.v1)
- `4999` - INTERNAL_SERVER_ERROR (unexpected server error)

### Context: WebSocket Communication Pattern (from 04_Behavior_and_Communication.md)

**Asynchronous WebSocket (Event-Driven) Pattern:**

Use Cases:
- Real-time vote casting and vote state updates
- Room state synchronization (participant joins/leaves, host controls)
- Card reveal events with animated timing coordination
- Presence updates (typing indicators, ready states)
- Chat messages and emoji reactions

Pattern Characteristics:
- Persistent connection maintained for session duration
- Events broadcast via Redis Pub/Sub to all application nodes
- Client-side event handlers update local state optimistically, reconcile on server confirmation
- Heartbeat/ping-pong protocol for connection liveness detection
- Automatic reconnection with exponential backoff on connection loss

Message Flow:
1. Client sends WebSocket message: `{"type": "vote.cast.v1", "requestId": "uuid", "payload": {"cardValue": "5"}}`
2. Server validates, persists vote to PostgreSQL
3. Server publishes event to Redis channel: `room:{roomId}`
4. All application nodes subscribed to channel receive event
5. Each node broadcasts to locally connected clients in that room
6. Clients receive: `{"type": "vote.recorded.v1", "requestId": "uuid", "payload": {"participantId": "...", "votedAt": "..."}}`

### Context: WebSocket Connection Lifecycle (from websocket-protocol.md)

**Connection Establishment Steps:**
1. Client initiates: `wss://api.planningpoker.example.com/ws/room/{roomId}?token={jwt}`
2. Server validates JWT token, extracts user/participant identity
3. Server checks room existence and user authorization
4. Server subscribes connection to Redis Pub/Sub channel: `room:{roomId}`
5. WebSocket connection established (HTTP 101 Switching Protocols)
6. Client MUST send `room.join.v1` message immediately after connection
7. Server validates join request, creates/updates `RoomParticipant` record
8. Server broadcasts `room.participant_joined.v1` event to existing participants
9. Server sends `room.state.v1` (initial state snapshot) to newly connected client

**Heartbeat Protocol:**
- Client sends WebSocket `ping` frame every 30 seconds
- Server responds with `pong` frame
- Connection terminated if no `ping` received within 60 seconds (2x interval)

**Reconnection Strategy (Client-Side):**
```javascript
class RoomWebSocket {
  constructor(roomId, token) {
    this.roomId = roomId;
    this.token = token;
    this.lastEventId = null;
    this.reconnectAttempts = 0;
    this.maxReconnectDelay = 16000; // 16 seconds
    this.connect();
  }

  connect() {
    const url = `wss://api.planningpoker.example.com/ws/room/${this.roomId}?token=${this.token}`;
    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      console.log('Connected');
      this.reconnectAttempts = 0;

      // Send join message with lastEventId for replay
      this.send({
        type: 'room.join.v1',
        requestId: uuidv4(),
        payload: {
          displayName: 'Alice',
          role: 'VOTER',
          lastEventId: this.lastEventId // For event replay
        }
      });
    };

    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data);

      // Store lastEventId for reconnection
      if (message.payload && message.payload.lastEventId) {
        this.lastEventId = message.payload.lastEventId;
      }

      this.handleMessage(message);
    };

    this.ws.onclose = (event) => {
      if (event.code !== 1000) {
        // Unexpected closure, attempt reconnection
        this.reconnect();
      }
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };
  }

  reconnect() {
    const delay = Math.min(
      1000 * Math.pow(2, this.reconnectAttempts),
      this.maxReconnectDelay
    );
    this.reconnectAttempts++;

    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

    setTimeout(() => {
      this.connect();
    }, delay);
  }

  send(message) {
    if (this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    }
  }

  handleMessage(message) {
    // Handle different message types
    console.log('Received:', message);
  }
}
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
    *   **Summary:** This is the backend WebSocket endpoint implementation. It handles connection establishment, JWT authentication, heartbeat protocol, message routing, and connection cleanup. The endpoint is at `/ws/room/{roomId}` with JWT token passed as query parameter.
    *   **Recommendation:** Your frontend WebSocket client MUST connect to this endpoint with the correct URL pattern: `wss://{host}/ws/room/{roomId}?token={jwt}`. You MUST send a `room.join.v1` message within 10 seconds of connection or the server will close the connection with code 4008.
    *   **Critical Details:**
        - Server expects JWT token in query parameter: `?token={jwt}`
        - Client must send `room.join.v1` within 10 seconds (enforced by `enforceJoinTimeout()` scheduled task)
        - Server sends ping frames every 30 seconds (via `sendHeartbeatPings()` task)
        - Server closes connections that don't respond to ping within 60 seconds
        - Server uses `ConnectionRegistry` to manage active connections per room
        - Server uses `MessageRouter` to route messages to appropriate handlers

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Summary:** This service implements the voting logic including `castVote()`, `startRound()`, `revealRound()`, and `resetRound()` methods. It publishes events to Redis Pub/Sub via `RoomEventPublisher`.
    *   **Recommendation:** The server is already set up to handle voting operations and publish events. Your frontend client should expect to receive `vote.recorded.v1`, `round.started.v1`, `round.revealed.v1`, and `round.reset.v1` events from the server.
    *   **Event Payload Examples:**
        - `vote.recorded.v1`: `{"participantId": "uuid", "votedAt": "timestamp"}`
        - `round.revealed.v1`: `{"votes": [...], "stats": {"avg": 6.5, "median": "6.5", "consensus": false}, "revealedAt": "timestamp"}`

*   **File:** `backend/src/main/java/com/scrumpoker/event/RoomEventPublisher.java`
    *   **Summary:** This service publishes events to Redis Pub/Sub channels using the pattern `room:{roomId}`. Events are serialized as JSON with the standard message envelope format (type, requestId, payload).
    *   **Recommendation:** The backend is already configured to publish events in the correct format. Your frontend client should parse incoming WebSocket messages as JSON and extract the `type`, `requestId`, and `payload` fields.

*   **File:** `api/websocket-protocol.md` (complete protocol specification)
    *   **Summary:** This is the comprehensive WebSocket protocol specification defining all message types, connection lifecycle, error codes, security requirements, and best practices.
    *   **Recommendation:** You MUST follow this protocol specification exactly. Pay special attention to:
        - Message envelope format (type, requestId, payload)
        - Connection lifecycle (handshake → join → heartbeat → disconnect)
        - Error handling (codes 4000-4999)
        - Reconnection strategy (exponential backoff: 1s, 2s, 4s, 8s, 16s max)
        - Heartbeat protocol (client sends ping every 30s, server responds with pong)

*   **File:** `frontend/src/stores/authStore.ts`
    *   **Summary:** This Zustand store manages authentication state including access tokens. The WebSocket client will need to get the JWT token from this store.
    *   **Recommendation:** You SHOULD import and use the `authStore` to get the current user's JWT access token when establishing WebSocket connections. The token is required for authentication.

### Implementation Tips & Notes

*   **Tip:** The backend WebSocket server is fully implemented and ready to accept connections. Task I4.T1 (WebSocket Connection Handler) is already complete. Your focus is purely on the frontend client implementation.

*   **Note:** The backend enforces a strict 10-second timeout for sending `room.join.v1` after connection. Ensure your `WebSocketManager` sends this message immediately in the `onopen` handler.

*   **Tip:** For heartbeat implementation, the browser's native WebSocket API handles ping/pong frames automatically in most cases. However, you should still implement ping sending on the client side every 30 seconds to keep the connection alive. Use `setInterval()` to send ping frames.

*   **Note:** The backend uses Redis Pub/Sub for event broadcasting across multiple application nodes. This means events published from one backend instance will be received by WebSocket clients connected to different backend instances. Your frontend client doesn't need to worry about this - just handle incoming events from the WebSocket connection.

*   **Warning:** When implementing reconnection logic, you MUST use exponential backoff (1s, 2s, 4s, 8s, max 16s) to avoid overwhelming the server with rapid reconnection attempts. Reset the backoff counter to 0 on successful connection.

*   **Tip:** Store the `lastEventId` from `room.state.v1` and broadcast messages. Include this in the `room.join.v1` payload when reconnecting to request replay of missed events within the 5-minute window.

*   **Note:** For Zustand store integration, you should create a `roomStore` that maintains room state including:
    - Current room ID
    - Room configuration (deck type, privacy mode, etc.)
    - Participants list with vote status (hasVoted: true/false)
    - Current round information
    - Revealed votes and statistics (after reveal)

*   **Tip:** Create event handlers using a simple mapping pattern:
    ```typescript
    const eventHandlers = new Map<string, (payload: any) => void>();

    eventHandlers.set('vote.recorded.v1', (payload) => {
      roomStore.getState().updateParticipantVoteStatus(payload.participantId, true);
    });

    eventHandlers.set('round.revealed.v1', (payload) => {
      roomStore.getState().setRevealedVotes(payload.votes, payload.stats);
    });
    ```

*   **Warning:** The WebSocket URL differs between development and production environments. Use environment variables or configuration to set the correct WebSocket base URL. In development, it might be `ws://localhost:8080`, while in production it will be `wss://api.scrumpoker.com`.

*   **Tip:** Implement optimistic UI updates for vote casting. When the user casts a vote, immediately update the local state to show "Voted" status, then reconcile with the server's `vote.recorded.v1` event.

*   **Note:** The `useWebSocket` hook should provide:
    - `connectionStatus`: 'connecting' | 'connected' | 'disconnected'
    - `send`: function to send messages
    - `isConnected`: boolean derived from connectionStatus
    - Error state and last error message

*   **Tip:** For TypeScript types, define interfaces for all message payloads:
    ```typescript
    interface VoteCastPayload {
      cardValue: string;
    }

    interface VoteRecordedPayload {
      participantId: string;
      votedAt: string;
    }

    interface RoundRevealedPayload {
      votes: Array<{participantId: string; cardValue: string}>;
      stats: {avg: number | null; median: string | null; consensus: boolean};
      revealedAt: string;
    }
    ```

*   **Warning:** Remember to clean up WebSocket connections and timers when components unmount. Use React's `useEffect` cleanup function to call `disconnect()` and clear intervals.

*   **Tip:** Consider using a state machine pattern for connection state management:
    ```
    DISCONNECTED → CONNECTING → CONNECTED
                ↑                  ↓
                └──────────────────┘
                  (on error/close)
    ```

*   **Note:** The frontend project already has the necessary dependencies installed (React, TypeScript, Zustand). You don't need to install additional packages for WebSocket support - use the browser's native `WebSocket` API.

*   **Architecture Pattern:** Your WebSocketManager class should be a singleton instance that can be imported and used across the application. Consider creating it as a module with a single exported instance rather than a class that needs to be instantiated multiple times.

*   **Environment Configuration:** You MUST determine the WebSocket URL based on the current environment. The backend server runs on port 8080 in development. Create a configuration file or use Vite's environment variables:
    ```typescript
    const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL ||
      (window.location.protocol === 'https:' ? 'wss:' : 'ws:') +
      '//' + window.location.hostname + ':8080';
    ```

*   **User Information:** When sending the `room.join.v1` message, you need to include the user's display name and role. You SHOULD get this from the authStore or user profile data. For now, you can use a default or fetch from the user's profile.

*   **UUID Generation:** You will need to generate UUIDs for requestId fields. Use the `crypto.randomUUID()` function available in modern browsers, or install a lightweight UUID library if needed.

### Recommended Implementation Structure

```typescript
// frontend/src/services/websocket.ts
interface WebSocketMessage {
  type: string;
  requestId: string;
  payload: any;
}

type ConnectionStatus = 'connecting' | 'connected' | 'disconnected';
type MessageHandler = (payload: any) => void;

class WebSocketManager {
  private ws: WebSocket | null = null;
  private roomId: string | null = null;
  private token: string | null = null;
  private connectionStatus: ConnectionStatus = 'disconnected';
  private reconnectAttempts = 0;
  private maxReconnectDelay = 16000;
  private eventHandlers = new Map<string, Set<MessageHandler>>();
  private heartbeatInterval: NodeJS.Timeout | null = null;
  private lastEventId: string | null = null;

  connect(roomId: string, token: string): void {
    // Implementation
  }

  disconnect(): void {
    // Implementation
  }

  send(type: string, payload: any): void {
    // Implementation
  }

  on(messageType: string, handler: MessageHandler): () => void {
    // Implementation - returns unsubscribe function
  }

  private reconnect(): void {
    // Exponential backoff logic
  }

  private startHeartbeat(): void {
    // Send ping every 30 seconds
  }

  private handleMessage(message: WebSocketMessage): void {
    // Dispatch to event handlers
  }
}

export const wsManager = new WebSocketManager();

// frontend/src/hooks/useWebSocket.ts
export function useWebSocket(roomId: string | null) {
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('disconnected');
  const token = useAuthStore(state => state.accessToken);

  useEffect(() => {
    if (!roomId || !token) return;

    wsManager.connect(roomId, token);

    // Subscribe to connection status changes
    const unsubscribe = wsManager.on('connection.status', (status) => {
      setConnectionStatus(status);
    });

    return () => {
      unsubscribe();
      wsManager.disconnect();
    };
  }, [roomId, token]);

  return {
    connectionStatus,
    send: wsManager.send.bind(wsManager),
    isConnected: connectionStatus === 'connected',
  };
}

// frontend/src/stores/roomStore.ts
interface RoomState {
  roomId: string | null;
  participants: Map<string, Participant>;
  currentRound: Round | null;
  revealedVotes: Vote[] | null;
  statistics: Statistics | null;

  // Actions
  updateParticipantVoteStatus: (participantId: string, hasVoted: boolean) => void;
  setRevealedVotes: (votes: Vote[], stats: Statistics) => void;
  addParticipant: (participant: Participant) => void;
  removeParticipant: (participantId: string) => void;
}

export const useRoomStore = create<RoomState>((set) => ({
  // Implementation
}));
```
