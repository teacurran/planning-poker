# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I2.T2",
  "iteration_id": "I2",
  "iteration_goal": "Implement foundational domain services (Room Service, basic User Service), define REST API contracts (OpenAPI specification), and establish WebSocket protocol specification to enable frontend integration and parallel feature development.",
  "description": "Create comprehensive Markdown document specifying WebSocket communication protocol. Define message envelope structure (`{\"type\": \"message_type.v1\", \"requestId\": \"uuid\", \"payload\": {...}}`). Document all message types: client-to-server (`room.join.v1`, `vote.cast.v1`, `chat.message.v1`, `round.reveal.v1`), server-to-client (`vote.recorded.v1`, `round.revealed.v1`, `room.participant_joined.v1`, `error.v1`). Provide JSON schema for each payload type. Define error codes (4000-4999 for application errors). Specify connection lifecycle (handshake with JWT token, heartbeat protocol, graceful/ungraceful disconnection). Document versioning strategy for message types.",
  "agent_type_hint": "DocumentationAgent",
  "inputs": "*   WebSocket communication patterns from architecture blueprint (Section 4)\n        *   Vote casting sequence diagram\n        *   WebSocket message types overview",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   Markdown specification document (10+ pages)\n        *   Message envelope definition with required/optional fields\n        *   20+ message type definitions with JSON schema payloads\n        *   Error code catalog (4000: Unauthorized, 4001: Room not found, 4002: Invalid vote, etc.)\n        *   Connection lifecycle diagram (PlantUML or Mermaid)\n        *   Versioning policy explanation (backward compatibility guarantees)",
  "acceptance_criteria": "*   All message types from architecture blueprint documented\n        *   JSON schemas validate sample messages (test with AJV or similar validator)\n        *   Error codes cover common failure scenarios (auth, validation, server error)\n        *   Connection lifecycle clearly explains handshake, heartbeat, reconnection\n        *   Versioning strategy enables protocol evolution without breaking clients\n        *   Document reviewed by backend and frontend leads for completeness",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Communication Patterns (from .codemachine/artifacts/plan/01_Plan_Overview_and_Setup.md)

```markdown
*   **Communication Patterns:**
    *   **Synchronous REST (Request/Response):** User authentication, room CRUD, subscription management, report triggers
    *   **Asynchronous WebSocket (Event-Driven):** Real-time vote casting, room state sync, presence updates, chat
    *   **Asynchronous Job Processing (Fire-and-Forget):** Report exports, email notifications, analytics aggregation

    **Event Flow (WebSocket):**
    1. Client sends message to WebSocket handler
    2. Handler validates, persists to PostgreSQL
    3. Handler publishes event to Redis Pub/Sub channel `room:{roomId}`
    4. All application nodes subscribed to channel receive event
    5. Each node broadcasts to locally connected clients in that room

    **Relevant Sequence Diagrams:**
    *   Vote Casting & Round Reveal (Created in Architecture Blueprint reference)
    *   OAuth2 Authentication Flow (Created in Architecture Blueprint reference)
```

### Context: Asynchronous WebSocket (Event-Driven) (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

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

### Context: WebSocket Connection Lifecycle (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `api/websocket-protocol.md`
    *   **Summary:** Contains a 10+ section specification that already outlines the WebSocket endpoint, envelope structure, detailed client/server message catalogs, payload descriptions, connection lifecycle narrations, error taxonomy, versioning plan, troubleshooting guidance, and appendices linking to related docs. It reads like the deliverable for I2.T2.
    *   **Recommendation:** Treat this file as the canonical spec—expand or refine it rather than starting from scratch. Ensure new requirements (additional message types, envelope clarifications, diagrams) fit the existing structure: keep headings, tables, and example payloads consistent, and update the “Last Updated” date/version as needed.
*   **File:** `api/websocket-message-schemas.json`
    *   **Summary:** Provides JSON Schema definitions for every message payload (join/leave, vote, round lifecycle, chat, presence, errors, room snapshots, heartbeat, reconnection). These schemas document validation rules (required fields, enums, string formats, numeric ranges) referenced by the Markdown spec.
    *   **Recommendation:** When documenting or introducing message types, cross-reference these schemas. If you add or modify payload attributes in the Markdown file, update the corresponding schema definition to keep tooling (AJV validation, contract tests) in sync and mention schema IDs/linkbacks in the spec.

### Implementation Tips & Notes
*   **Tip:** The Markdown spec already sections “Message Envelope Format”, “Message Types”, “Connection Lifecycle”, “Error Handling”, and “Versioning Strategy”; align your additions with those sections to preserve navigability and automate TOC generation.
*   **Tip:** The existing message catalog covers the core interactions listed in the architecture blueprint. Verify completeness against the blueprint list (join/leave, vote cast/recorded, round start/reveal/reset, presence, chat, participant join/left/disconnected, error) and document bidirectional flow expectations in each entry.
*   **Tip:** Error codes currently live in the spec—extend them to cover all 4000-4999 cases with consistent naming, and reference which REST error they map to for troubleshooting.
*   **Tip:** When describing lifecycle/heartbeat behavior, mirror the enumerated steps from the blueprint (JWT handshake, Redis subscriptions, ping/pong, reconnection with `lastEventId`) and consider embedding a Mermaid or PlantUML diagram, as requested in the deliverables.
*   **Tip:** Before finalizing, validate the JSON schemas (`api/websocket-message-schemas.json`) with AJV or Spectral to ensure syntax correctness; include a brief note in the spec referencing the validation status so reviewers know the contract is machine-verifiable.
