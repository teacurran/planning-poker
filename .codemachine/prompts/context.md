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
  "inputs": "WebSocket communication patterns from architecture blueprint (Section 4), Vote casting sequence diagram, WebSocket message types overview",
  "input_files": [
    ".codemachine/artifacts/architecture/04_Behavior_and_Communication.md"
  ],
  "target_files": [
    "api/websocket-protocol.md",
    "api/websocket-message-schemas.json"
  ],
  "deliverables": "Markdown specification document (10+ pages), Message envelope definition with required/optional fields, 20+ message type definitions with JSON schema payloads, Error code catalog (4000: Unauthorized, 4001: Room not found, 4002: Invalid vote, etc.), Connection lifecycle diagram (PlantUML or Mermaid), Versioning policy explanation (backward compatibility guarantees)",
  "acceptance_criteria": "All message types from architecture blueprint documented, JSON schemas validate sample messages (test with AJV or similar validator), Error codes cover common failure scenarios (auth, validation, server error), Connection lifecycle clearly explains handshake, heartbeat, reconnection, Versioning strategy enables protocol evolution without breaking clients, Document reviewed by backend and frontend leads for completeness",
  "dependencies": [],
  "parallelizable": true,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: api-style (from 04_Behavior_and_Communication.md)

```markdown
#### API Style

**Primary API Style:** **RESTful JSON API (OpenAPI 3.1 Specification)**

**Rationale:**
- **Simplicity & Familiarity:** REST over HTTPS provides a well-understood contract for CRUD operations on resources (users, rooms, subscriptions)
- **Tooling Ecosystem:** OpenAPI specification enables automatic client SDK generation (TypeScript for React frontend), API documentation (Swagger UI), and contract testing
- **Caching Support:** HTTP semantics (ETags, Cache-Control headers) enable browser and CDN caching for read-heavy endpoints (room configurations, user profiles)
- **Versioning Strategy:** URL-based versioning (`/api/v1/`) for backward compatibility during iterative releases

**WebSocket Protocol:** **Custom JSON-RPC Style Over WebSocket**

**Rationale:**
- **Real-Time Bidirectional Communication:** WebSocket connections maintained for duration of estimation session, enabling sub-100ms latency for vote events and reveals
- **Message Format:** JSON envelopes with `type`, `requestId`, and `payload` fields for request/response correlation
- **Versioned Message Types:** Each message type (e.g., `vote.cast.v1`, `room.reveal.v1`) versioned independently for protocol evolution
- **Fallback Strategy:** Graceful degradation to HTTP long-polling for environments with WebSocket restrictions (corporate proxies)

**Alternative Considered:**
- **GraphQL:** Rejected due to complexity overhead for small team and straightforward data model. GraphQL subscription complexity for WebSocket integration not justified by query flexibility benefits.
- **gRPC:** Rejected due to browser support limitations (requires gRPC-Web proxy) and team unfamiliarity. Better suited for backend-to-backend microservice communication.
```

### Context: asynchronous-websocket-pattern (from 04_Behavior_and_Communication.md)

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

### Context: key-interaction-flow-vote-round (from 04_Behavior_and_Communication.md)

```markdown
#### Key Interaction Flow: Vote Casting and Round Reveal

##### Description

This sequence diagram illustrates the critical real-time workflow for a Scrum Poker estimation round, from initial vote casting through final reveal and consensus calculation. The flow demonstrates WebSocket message handling, Redis Pub/Sub event distribution across stateless application nodes, and optimistic UI updates with server reconciliation.

**Scenario:**
1. Two participants (Alice and Bob) connected to different application nodes due to load balancer sticky session routing
2. Alice casts vote "5", Bob casts vote "8"
3. Host triggers reveal after all votes submitted
4. System calculates statistics (average: 6.5, median: 6.5, no consensus due to variance)
5. All participants receive synchronized reveal event with results

##### Diagram (PlantUML)

[The full sequence diagram is included in the architecture document showing the complete flow of vote casting and revealing across multiple application nodes with Redis Pub/Sub coordination]
```

### Context: websocket-connection-lifecycle (from 04_Behavior_and_Communication.md)

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

*   **File:** `api/openapi.yaml`
    *   **Summary:** This file contains the complete OpenAPI 3.1 specification for the REST API with 30+ endpoints documented. It was created as part of task I2.T1 and is now complete.
    *   **Recommendation:** You SHOULD use this file as a reference model for structuring your WebSocket protocol documentation. Notice how it defines schemas, error responses, and examples. Your WebSocket protocol doc should follow a similar level of detail and organization.
    *   **Note:** Pay special attention to the error response structure defined here:
        ```json
        {
          "error": "ERROR_CODE",
          "message": "Human-readable message",
          "timestamp": "ISO 8601 timestamp"
        }
        ```
        Your WebSocket error messages (error.v1) should follow a similar standardized structure.

*   **File:** `docs/api-design.md`
    *   **Summary:** This supplementary documentation explains REST API design principles, authentication flows, and provides examples. It was also created as part of I2.T1.
    *   **Recommendation:** This document demonstrates the expected documentation quality and completeness. Your WebSocket protocol document should have a similar structure with clear sections for: Overview, Message Format, Message Types, Error Handling, Connection Lifecycle, Security, Examples, and Best Practices.
    *   **Tip:** Notice how this document includes concrete code examples (JavaScript, bash) - you SHOULD include similar examples showing how to connect to WebSocket, send messages, and handle responses.

*   **File:** `.codemachine/artifacts/architecture/04_Behavior_and_Communication.md`
    *   **Summary:** This is the authoritative source for WebSocket requirements. It defines the message types, communication patterns, and the complete voting sequence diagram.
    *   **Recommendation:** You MUST use this as your primary reference. Every message type listed in the "WebSocket Message Types" section must be documented in your protocol specification. The sequence diagram provides critical context for understanding message flows - reference it when explaining message ordering and timing.

### Implementation Tips & Notes

*   **Tip:** The task requires **JSON schema definitions** for each payload type. You should create the `api/websocket-message-schemas.json` file containing JSON Schema (draft-07 or later) definitions for all message payloads. This enables automated validation on both client and server.

*   **Note:** The message envelope structure is clearly defined: `{"type": "message_type.v1", "requestId": "uuid", "payload": {...}}`. The `type` field uses semantic versioning (`.v1`, `.v2`, etc.) to enable protocol evolution. Make sure to explain this versioning strategy clearly.

*   **Warning:** The architecture specifies error codes in the 4000-4999 range for WebSocket application errors (as opposed to standard WebSocket close codes 1000-1999). You MUST define a comprehensive error code catalog covering:
    - 4000: Unauthorized (invalid/expired JWT)
    - 4001: Room not found
    - 4002: Invalid vote/action
    - 4003: Forbidden (insufficient permissions)
    - 4004: Validation error
    - 4xxx: Additional error scenarios

*   **Tip:** For the connection lifecycle diagram, you can use either PlantUML or Mermaid format. The architecture document already uses PlantUML (see the voting sequence diagram), so using PlantUML would maintain consistency. However, Mermaid is more widely supported in Markdown renderers.

*   **Note:** The WebSocket endpoint format is: `wss://api.scrumpoker.com/ws/room/{roomId}?token={jwt}`. The JWT token is passed as a query parameter (not in headers, since WebSocket handshake doesn't support custom headers reliably in all browsers).

*   **Tip:** Your documentation should clearly explain the Redis Pub/Sub broadcasting mechanism. When a message is sent to one WebSocket connection, it gets published to a Redis channel, and all application nodes broadcast it to their local connections. This is critical for horizontal scaling.

*   **Warning:** The heartbeat protocol uses WebSocket ping/pong frames (not custom JSON messages). The client sends ping every 30 seconds, server responds with pong, and connection is terminated after 60 seconds of no heartbeat. This is different from application-level messages.

*   **Tip:** Include a troubleshooting section covering common issues:
    - Connection refused (invalid JWT, room doesn't exist)
    - Unexpected disconnections (heartbeat timeout)
    - Missed events (reconnection and event replay)
    - Message order guarantees (within single connection vs. across connections)

### Message Types to Document

Based on the architecture blueprint, you MUST document at least these message types (expand as needed):

**Client → Server:**
- `room.join.v1` - Join room (include participant details)
- `room.leave.v1` - Leave room
- `vote.cast.v1` - Submit vote
- `round.start.v1` - Start new round (host only)
- `round.reveal.v1` - Reveal votes (host only)
- `round.reset.v1` - Reset round (host only)
- `chat.message.v1` - Send chat message
- `presence.update.v1` - Update presence status

**Server → Client:**
- `room.state.v1` - Initial room state snapshot
- `room.participant_joined.v1` - Participant joined
- `room.participant_left.v1` - Participant left
- `room.participant_disconnected.v1` - Participant disconnected (ungraceful)
- `vote.recorded.v1` - Vote confirmed
- `round.started.v1` - Round started
- `round.revealed.v1` - Votes revealed with statistics
- `round.reset.v1` - Round reset
- `chat.message.v1` - Chat message broadcast
- `presence.update.v1` - Presence change broadcast
- `error.v1` - Error response

### Validation & Testing

*   **Recommendation:** After creating your JSON schemas, test them using AJV (Another JSON Validator) or a similar tool. Create sample messages for each type and validate them against your schemas.

*   **Example validation command:**
    ```bash
    npm install -g ajv-cli
    ajv validate -s api/websocket-message-schemas.json -d sample-messages/*.json
    ```

*   **Tip:** Create a `/docs/websocket-examples` directory with concrete examples of each message type. This helps developers understand the protocol quickly.

### Documentation Structure Recommendation

Your `api/websocket-protocol.md` document should follow this structure:

1. **Overview** - Purpose, WebSocket endpoint URL, authentication
2. **Message Envelope Format** - Structure, fields, versioning
3. **Message Types** - Complete catalog (client→server, server→client)
4. **Message Schemas** - Reference to JSON schema file
5. **Connection Lifecycle** - Handshake, heartbeat, disconnection, reconnection
6. **Error Handling** - Error codes, error message format
7. **Security** - JWT authentication, authorization checks
8. **Versioning Strategy** - How protocol evolves, backward compatibility
9. **Best Practices** - Client implementation guidance
10. **Examples** - Code samples for JavaScript/TypeScript clients
11. **Troubleshooting** - Common issues and solutions

This comprehensive briefing provides everything needed to complete task I2.T2 successfully. Good luck!
