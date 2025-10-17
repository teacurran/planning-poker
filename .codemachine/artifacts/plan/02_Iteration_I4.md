# Project Plan: Scrum Poker Platform - Iteration 4

**Version:** 1.0
**Date:** 2025-10-17

---

<!-- anchor: iteration-4 -->
### Iteration 4: Real-Time Voting Engine (WebSocket)

*   **Iteration ID:** `I4`

*   **Goal:** Implement WebSocket-based real-time voting functionality including connection management, vote casting, round lifecycle (start, reveal, reset), Redis Pub/Sub for event broadcasting across stateless nodes, and frontend voting UI.

*   **Prerequisites:** I2 (RoomService, Room entity), I3 (Authentication, JWT validation)

*   **Tasks:**

<!-- anchor: task-i4-t1 -->
*   **Task 4.1: Implement WebSocket Connection Handler**
    *   **Task ID:** `I4.T1`
    *   **Description:** Create `RoomWebSocketHandler` using Quarkus WebSocket extension. Implement endpoint `/ws/room/{roomId}` with JWT authentication on handshake (validate token from query parameter `?token={jwt}`). Manage connection lifecycle: onOpen (validate room exists, validate user authorized, subscribe to Redis channel `room:{roomId}`, broadcast `participant_joined` event), onClose (unsubscribe, broadcast `participant_left`), onMessage (route to message handlers), onError (log, close gracefully). Store active connections in ConcurrentHashMap keyed by room ID. Implement heartbeat protocol (ping/pong every 30 seconds).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   WebSocket protocol specification from I2.T2
        *   WebSocket connection lifecycle from architecture blueprint
        *   JWT authentication from I3
    *   **Input Files:**
        *   `api/websocket-protocol.md`
        *   `.codemachine/artifacts/architecture/04_Behavior_and_Communication.md` (WebSocket section)
        *   `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
        *   `backend/src/main/java/com/scrumpoker/api/websocket/ConnectionRegistry.java`
        *   `backend/src/main/java/com/scrumpoker/api/websocket/WebSocketMessage.java` (envelope DTO)
    *   **Deliverables:**
        *   WebSocket endpoint with JWT-based authentication
        *   Connection registry managing active sessions per room
        *   Heartbeat mechanism (server sends ping, expects pong within 60 seconds)
        *   Participant joined/left event broadcasting
        *   Error handling and graceful disconnection
    *   **Acceptance Criteria:**
        *   WebSocket connection succeeds with valid JWT token
        *   Connection rejected with 401 if token invalid/missing
        *   Participant joined event broadcasted to existing room connections
        *   Heartbeat mechanism prevents stale connections (auto-close after timeout)
        *   Connection gracefully closed on client disconnect
        *   Multiple clients can connect to same room simultaneously
    *   **Dependencies:** [I2.T3, I3.T2]
    *   **Parallelizable:** No (depends on RoomService and JWT)

<!-- anchor: task-i4-t2 -->
*   **Task 4.2: Implement Redis Pub/Sub Event Publisher & Subscriber**
    *   **Task ID:** `I4.T2`
    *   **Description:** Create `RoomEventPublisher` service publishing WebSocket events to Redis Pub/Sub channel `room:{roomId}`. Implement `RoomEventSubscriber` subscribing to channels and forwarding messages to locally connected WebSocket clients. Use Quarkus reactive Redis client. Publisher method: `publishEvent(roomId, messageType, payload)` serializes message to JSON and publishes. Subscriber: on message received, look up connections in ConnectionRegistry for that room, send message to each WebSocket session. Handle subscription lifecycle (subscribe when first client joins room, unsubscribe when last client leaves).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Redis Pub/Sub pattern from architecture blueprint
        *   WebSocket message broadcasting requirements
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/04_Behavior_and_Communication.md` (communication patterns)
        *   `backend/src/main/java/com/scrumpoker/api/websocket/ConnectionRegistry.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/event/RoomEventPublisher.java`
        *   `backend/src/main/java/com/scrumpoker/event/RoomEventSubscriber.java`
        *   `backend/src/main/java/com/scrumpoker/event/RoomEvent.java` (event DTO)
    *   **Deliverables:**
        *   RoomEventPublisher with `publishEvent(roomId, type, payload)` method
        *   Redis Pub/Sub channel naming: `room:{roomId}`
        *   RoomEventSubscriber listening to subscribed channels
        *   Event routing to WebSocket clients via ConnectionRegistry
        *   JSON serialization/deserialization for event payloads
        *   Subscription management (subscribe/unsubscribe based on room activity)
    *   **Acceptance Criteria:**
        *   Publishing event to Redis channel succeeds
        *   Subscriber receives event from Redis
        *   Event forwarded to all connected WebSocket clients in target room
        *   Events not sent to clients in other rooms
        *   Multiple application nodes can publish/subscribe (test with 2 backend instances)
        *   Subscription cleaned up when no clients in room
    *   **Dependencies:** [I4.T1]
    *   **Parallelizable:** No (depends on ConnectionRegistry)

<!-- anchor: task-i4-t3 -->
*   **Task 4.3: Implement Voting Service (Vote Casting & Round Management)**
    *   **Task ID:** `I4.T3`
    *   **Description:** Create `VotingService` domain service implementing voting logic. Methods: `castVote(roomId, roundId, participantId, cardValue)` (persist vote to database, publish `vote.recorded` event), `startRound(roomId, storyTitle)` (create Round entity, publish `round.started` event), `revealRound(roomId, roundId)` (query all votes, calculate average/median/consensus, update Round entity with stats, publish `round.revealed` event with all votes), `resetRound(roomId, roundId)` (delete votes, reset Round entity). Use `RoundRepository`, `VoteRepository`, `RoomEventPublisher`. Implement consensus algorithm (variance threshold < 2 points for Fibonacci deck). Handle duplicate vote prevention (upsert vote if participant votes twice).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Voting requirements from product spec
        *   Vote sequence diagram from architecture blueprint
        *   Round and Vote entities from I1
    *   **Input Files:**
        *   `.codemachine/artifacts/architecture/04_Behavior_and_Communication.md` (vote flow sequence)
        *   `backend/src/main/java/com/scrumpoker/domain/room/Round.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/Vote.java`
        *   `backend/src/main/java/com/scrumpoker/repository/RoundRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/ConsensusCalculator.java` (utility class)
    *   **Deliverables:**
        *   VotingService with methods: castVote, startRound, revealRound, resetRound
        *   Vote persistence with duplicate handling (upsert by participant + round)
        *   Round creation with story title, started timestamp
        *   Reveal logic: query votes, calculate stats (avg, median, consensus), persist
        *   ConsensusCalculator determining consensus based on variance threshold
        *   Event publishing after each operation (vote recorded, round started, revealed, reset)
    *   **Acceptance Criteria:**
        *   Cast vote persists to database and publishes event
        *   Starting round creates Round entity with correct timestamp
        *   Reveal round calculates correct average and median (test with known vote values)
        *   Consensus detection works (e.g., all votes 5 → consensus true, votes 3,5,8 → false)
        *   Duplicate vote from same participant updates existing vote (not create new)
        *   Reset round deletes votes and resets Round entity
    *   **Dependencies:** [I2.T3, I4.T2]
    *   **Parallelizable:** No (depends on RoomEventPublisher)

<!-- anchor: task-i4-t4 -->
*   **Task 4.4: Implement WebSocket Message Handlers (Vote, Reveal, Chat)**
    *   **Task ID:** `I4.T4`
    *   **Description:** Create message handler classes for WebSocket messages per protocol spec. Implement `VoteCastHandler` (validate message, extract payload, call VotingService.castVote), `RoundRevealHandler` (validate host role, call VotingService.revealRound), `RoundStartHandler` (validate host, call VotingService.startRound), `RoundResetHandler` (validate host, call VotingService.resetRound), `ChatMessageHandler` (persist chat message, broadcast to room). Integrate handlers with RoomWebSocketHandler onMessage router (switch on message type). Validate authorization (only host can reveal/reset, all voters can cast votes). Return error messages for validation failures.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   WebSocket protocol spec from I2.T2
        *   VotingService from I4.T3
        *   Message type definitions
    *   **Input Files:**
        *   `api/websocket-protocol.md`
        *   `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
        *   `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/websocket/handler/VoteCastHandler.java`
        *   `backend/src/main/java/com/scrumpoker/api/websocket/handler/RoundRevealHandler.java`
        *   `backend/src/main/java/com/scrumpoker/api/websocket/handler/RoundStartHandler.java`
        *   `backend/src/main/java/com/scrumpoker/api/websocket/handler/RoundResetHandler.java`
        *   `backend/src/main/java/com/scrumpoker/api/websocket/handler/ChatMessageHandler.java`
        *   `backend/src/main/java/com/scrumpoker/api/websocket/MessageRouter.java`
    *   **Deliverables:**
        *   5 message handler classes processing specific message types
        *   MessageRouter dispatching messages to handlers based on type
        *   Authorization validation (host-only operations)
        *   Payload validation (Zod/Bean Validation for JSON payloads)
        *   Error responses sent back to client for validation failures
        *   Integration with VotingService methods
    *   **Acceptance Criteria:**
        *   vote.cast.v1 message triggers VotingService.castVote correctly
        *   round.reveal.v1 from host reveals round successfully
        *   round.reveal.v1 from non-host returns error message (403 Forbidden)
        *   Invalid payload structure returns error message (400 Bad Request)
        *   Chat message broadcasts to all room participants
        *   Message router correctly dispatches to appropriate handler
    *   **Dependencies:** [I4.T3]
    *   **Parallelizable:** No (depends on VotingService)

<!-- anchor: task-i4-t5 -->
*   **Task 4.5: Create Frontend WebSocket Manager**
    *   **Task ID:** `I4.T5`
    *   **Description:** Implement `WebSocketManager` class managing WebSocket connection lifecycle for React frontend. Features: connect to `/ws/room/{roomId}` with JWT token, handle connection states (connecting, connected, disconnected), implement reconnection logic with exponential backoff (1s, 2s, 4s, 8s, max 16s), send messages (vote.cast, chat.message), receive messages and dispatch to event handlers, maintain heartbeat (respond to ping with pong). Integrate with Zustand store (`roomStore`) to update room state on incoming events. Create React hook `useWebSocket(roomId)` for components.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   WebSocket protocol spec from I2.T2
        *   WebSocket connection lifecycle requirements
        *   React + Zustand patterns
    *   **Input Files:**
        *   `api/websocket-protocol.md`
        *   `frontend/src/stores/authStore.ts` (for JWT token)
    *   **Target Files:**
        *   `frontend/src/services/websocket.ts` (WebSocketManager class)
        *   `frontend/src/hooks/useWebSocket.ts`
        *   `frontend/src/stores/roomStore.ts` (room state management)
    *   **Deliverables:**
        *   WebSocketManager with methods: connect, disconnect, send, on(messageType, handler)
        *   Reconnection logic with exponential backoff
        *   Heartbeat pong response
        *   Message serialization (JS object → JSON string)
        *   Message deserialization and event dispatching
        *   useWebSocket hook providing connection status and send function
        *   roomStore integration updating state on vote.recorded, round.revealed events
    *   **Acceptance Criteria:**
        *   WebSocket connects successfully to backend
        *   Connection state tracked (connecting, connected, disconnected)
        *   Reconnection triggers automatically on disconnect
        *   Sent messages appear in backend logs (vote.cast received)
        *   Received events update roomStore state
        *   Heartbeat keeps connection alive (no timeout disconnect)
        *   Hook provides connection status to components
    *   **Dependencies:** [I4.T1]
    *   **Parallelizable:** No (depends on WebSocket backend)

<!-- anchor: task-i4-t6 -->
*   **Task 4.6: Create Voting Room UI Components (Frontend)**
    *   **Task ID:** `I4.T6`
    *   **Description:** Implement React components for real-time voting UI. `RoomPage` component: join room, display participants list, show current round state (waiting for votes, votes cast count, revealed results). `VotingCard` component: deck of estimation cards (1, 2, 3, 5, 8, 13, ?, ∞, ☕), click to select, send vote.cast message. `ParticipantList` component: list of participants with vote status (voted/not voted, role badge). `RevealView` component: animated card flip, display all votes, show statistics (average, median, consensus indicator). `HostControls` component: buttons for start round, reveal, reset (visible only to host). Use WebSocketManager to send/receive messages. Update UI optimistically (instant feedback) and reconcile with server events.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   Voting UI requirements from product spec
        *   WebSocketManager from I4.T5
        *   Design system (Tailwind, Headless UI)
    *   **Input Files:**
        *   `frontend/src/services/websocket.ts`
        *   `frontend/src/hooks/useWebSocket.ts`
        *   `frontend/src/stores/roomStore.ts`
    *   **Target Files:**
        *   `frontend/src/pages/RoomPage.tsx`
        *   `frontend/src/components/room/VotingCard.tsx`
        *   `frontend/src/components/room/ParticipantList.tsx`
        *   `frontend/src/components/room/RevealView.tsx`
        *   `frontend/src/components/room/HostControls.tsx`
        *   `frontend/src/components/room/DeckSelector.tsx`
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
    *   **Dependencies:** [I4.T5]
    *   **Parallelizable:** No (depends on WebSocketManager)

<!-- anchor: task-i4-t7 -->
*   **Task 4.7: Write Integration Tests for WebSocket Voting Flow**
    *   **Task ID:** `I4.T7`
    *   **Description:** Create integration tests for complete voting flow using Quarkus test WebSocket client. Test scenarios: connect to room, cast vote, receive vote.recorded event, host reveals round, receive round.revealed event with statistics, reset round, votes cleared. Test multi-client scenario (2+ clients in same room, votes synchronize). Test authorization (non-host cannot reveal). Test disconnect/reconnect (client disconnects, reconnects, state restored). Use Testcontainers for Redis and PostgreSQL.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   WebSocket handlers from I4.T4
        *   VotingService from I4.T3
        *   Test WebSocket client patterns
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
        *   `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/api/websocket/VotingFlowIntegrationTest.java`
    *   **Deliverables:**
        *   Integration test: complete vote → reveal → reset flow
        *   Test: multiple clients receive synchronized events
        *   Test: authorization failures (non-host reveal attempt)
        *   Test: reconnection preserves room state
        *   Testcontainers setup for Redis and PostgreSQL
    *   **Acceptance Criteria:**
        *   `mvn verify` runs WebSocket integration tests successfully
        *   Vote cast by client A received by client B via Redis Pub/Sub
        *   Reveal calculates correct statistics (known vote inputs)
        *   Non-host reveal attempt returns error message
        *   Reconnection test joins room and receives current state
        *   All tests pass with Testcontainers
    *   **Dependencies:** [I4.T4]
    *   **Parallelizable:** No (depends on handlers)

<!-- anchor: task-i4-t8 -->
*   **Task 4.8: Write End-to-End Tests for Voting UI (Playwright)**
    *   **Task ID:** `I4.T8`
    *   **Description:** Create Playwright E2E tests for frontend voting flow. Test: user joins room, sees participant list, selects card, card selection reflected in UI, host reveals round, reveal animation plays, statistics displayed. Test multi-user scenario (open 2 browser contexts as different users, vote in parallel, verify both see reveal). Test reconnection (disconnect WebSocket, verify reconnection message, state preserved). Mock backend WebSocket or use real backend in test environment.
    *   **Agent Type Hint:** `FrontendAgent`
    *   **Inputs:**
        *   Voting UI components from I4.T6
        *   Playwright testing patterns for WebSocket
    *   **Input Files:**
        *   `frontend/src/pages/RoomPage.tsx`
        *   `frontend/src/components/room/*.tsx`
    *   **Target Files:**
        *   `frontend/e2e/voting.spec.ts`
    *   **Deliverables:**
        *   E2E test: join room, cast vote, see participant update
        *   E2E test: host reveal, see reveal animation and results
        *   E2E test: multi-user voting (2 browser contexts)
        *   E2E test: WebSocket reconnection
    *   **Acceptance Criteria:**
        *   `npm run test:e2e` executes voting tests
        *   Card selection updates UI instantly
        *   Reveal animation plays after host reveal
        *   Multi-user test verifies synchronization
        *   Reconnection test verifies WebSocket resilience
        *   Tests run in CI pipeline
    *   **Dependencies:** [I4.T6]
    *   **Parallelizable:** No (depends on UI components)

---

**Iteration 4 Summary:**

*   **Deliverables:**
    *   WebSocket connection handler with JWT authentication
    *   Redis Pub/Sub event publisher and subscriber
    *   VotingService with vote casting and round lifecycle
    *   WebSocket message handlers (vote, reveal, reset, chat)
    *   Frontend WebSocket manager with reconnection logic
    *   Voting room UI (cards, participant list, reveal view, host controls)
    *   Integration and E2E tests for voting flow

*   **Acceptance Criteria (Iteration-Level):**
    *   Real-time voting works end-to-end (frontend → backend → database → broadcast)
    *   Multiple clients in room see synchronized vote events
    *   Reveal displays correct statistics (average, median, consensus)
    *   Host controls work correctly (start, reveal, reset)
    *   WebSocket reconnection maintains room state
    *   Tests verify voting flow across multiple users

*   **Estimated Duration:** 3 weeks
