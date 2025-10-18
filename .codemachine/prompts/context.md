# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I4.T4",
  "iteration_id": "I4",
  "iteration_goal": "Implement WebSocket-based real-time voting functionality including connection management, vote casting, round lifecycle (start, reveal, reset), Redis Pub/Sub for event broadcasting across stateless nodes, and frontend voting UI.",
  "description": "Create message handler classes for WebSocket messages per protocol spec. Implement `VoteCastHandler` (validate message, extract payload, call VotingService.castVote), `RoundRevealHandler` (validate host role, call VotingService.revealRound), `RoundStartHandler` (validate host, call VotingService.startRound), `RoundResetHandler` (validate host, call VotingService.resetRound), `ChatMessageHandler` (persist chat message, broadcast to room). Integrate handlers with RoomWebSocketHandler onMessage router (switch on message type). Validate authorization (only host can reveal/reset, all voters can cast votes). Return error messages for validation failures.",
  "agent_type_hint": "BackendAgent",
  "inputs": "WebSocket protocol spec from I2.T2, VotingService from I4.T3, Message type definitions",
  "input_files": [
    "api/websocket-protocol.md",
    "backend/src/main/java/com/scrumpoker/domain/room/VotingService.java",
    "backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/api/websocket/handler/VoteCastHandler.java",
    "backend/src/main/java/com/scrumpoker/api/websocket/handler/RoundRevealHandler.java",
    "backend/src/main/java/com/scrumpoker/api/websocket/handler/RoundStartHandler.java",
    "backend/src/main/java/com/scrumpoker/api/websocket/handler/RoundResetHandler.java",
    "backend/src/main/java/com/scrumpoker/api/websocket/handler/ChatMessageHandler.java",
    "backend/src/main/java/com/scrumpoker/api/websocket/MessageRouter.java"
  ],
  "deliverables": "5 message handler classes processing specific message types, MessageRouter dispatching messages to handlers based on type, Authorization validation (host-only operations), Payload validation (Zod/Bean Validation for JSON payloads), Error responses sent back to client for validation failures, Integration with VotingService methods",
  "acceptance_criteria": "vote.cast.v1 message triggers VotingService.castVote correctly, round.reveal.v1 from host reveals round successfully, round.reveal.v1 from non-host returns error message (403 Forbidden), Invalid payload structure returns error message (400 Bad Request), Chat message broadcasts to all room participants, Message router correctly dispatches to appropriate handler",
  "dependencies": [
    "I4.T3"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: WebSocket Message Types - Client to Server (from websocket-protocol.md)

```markdown
### 3.1 Client → Server Messages

These messages are sent by clients to the server to initiate actions.

| Message Type | Direction | Description | Host Only |
|--------------|-----------|-------------|-----------|
| `room.join.v1` | Client → Server | Participant joins room (sent immediately after connection) | No |
| `room.leave.v1` | Client → Server | Participant leaves room gracefully | No |
| `vote.cast.v1` | Client → Server | Participant submits vote for current round | No |
| `round.start.v1` | Client → Server | Start new estimation round | **Yes** |
| `round.reveal.v1` | Client → Server | Reveal votes for current round | **Yes** |
| `round.reset.v1` | Client → Server | Reset current round for re-voting | **Yes** |
| `chat.message.v1` | Client → Server | Send chat message to room | No |
| `presence.update.v1` | Client → Server | Update participant presence status | No |
```

### Context: Message Schemas for Vote Casting (from websocket-protocol.md)

```markdown
#### 4.1.3 `vote.cast.v1`

**Purpose:** Participant casts vote for the current round.

**Payload Schema:**
```json
{
  "cardValue": "5"                    // Required, 1-10 characters, must match current deck
}
```

**Valid Card Values (depends on deck type):**
- Fibonacci: `"0"`, `"1"`, `"2"`, `"3"`, `"5"`, `"8"`, `"13"`, `"21"`, `"?"`
- T-Shirt: `"XS"`, `"S"`, `"M"`, `"L"`, `"XL"`, `"XXL"`, `"?"`
- Powers of 2: `"1"`, `"2"`, `"4"`, `"8"`, `"16"`, `"32"`, `"?"`
- Custom: As defined in room configuration

**Example:**
```json
{
  "type": "vote.cast.v1",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "cardValue": "5"
  }
}
```

**Server Broadcast:**
- `vote.recorded.v1` to all participants (does NOT include vote value)

**Error Conditions:**
- `4002`: Invalid vote (card value not in deck, no active round, already voted)
- `4003`: Forbidden (observer role cannot vote)
```

### Context: Message Schemas for Round Start (from websocket-protocol.md)

```markdown
#### 4.1.4 `round.start.v1` (Host Only)

**Purpose:** Host starts a new estimation round.

**Payload Schema:**
```json
{
  "storyTitle": "As a user, I want to...",  // Optional, max 500 characters
  "timerDurationSeconds": 120               // Optional, 10-600 seconds
}
```

**Example:**
```json
{
  "type": "round.start.v1",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "storyTitle": "As a user, I want to login with Google OAuth2",
    "timerDurationSeconds": 120
  }
}
```

**Server Broadcast:**
- `round.started.v1` to all participants

**Error Conditions:**
- `4003`: Forbidden (only HOST role can start rounds)
- `4005`: Invalid state (round already in progress)
```

### Context: Message Schemas for Round Reveal (from websocket-protocol.md)

```markdown
#### 4.1.5 `round.reveal.v1` (Host Only)

**Purpose:** Host triggers reveal of votes for the current round.

**Payload Schema:**
```json
{}  // Empty payload
```

**Example:**
```json
{
  "type": "round.reveal.v1",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {}
}
```

**Server Broadcast:**
- `round.revealed.v1` to all participants with vote values and statistics

**Error Conditions:**
- `4003`: Forbidden (only HOST role can reveal)
- `4005`: Invalid state (no active round, no votes cast, already revealed)
```

### Context: Message Schemas for Round Reset (from websocket-protocol.md)

```markdown
#### 4.1.6 `round.reset.v1` (Host Only)

**Purpose:** Host resets current round for re-voting.

**Payload Schema:**
```json
{
  "clearVotes": true                  // Optional, default true
}
```

**Example:**
```json
{
  "type": "round.reset.v1",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "clearVotes": true
  }
}
```

**Server Broadcast:**
- `round.reset.v1` to all participants

**Error Conditions:**
- `4003`: Forbidden (only HOST role can reset)
- `4005`: Invalid state (no active round)
```

### Context: Message Schemas for Chat Messages (from websocket-protocol.md)

```markdown
#### 4.1.7 `chat.message.v1`

**Purpose:** Participant sends chat message to room.

**Payload Schema:**
```json
{
  "message": "Hello team!",                           // Required, 1-2000 characters
  "replyToMessageId": "550e8400-e29b-41d4-a716-..."   // Optional, UUID of message being replied to
}
```

**Example:**
```json
{
  "type": "chat.message.v1",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "message": "I think 8 points is too high for this story."
  }
}
```

**Server Broadcast:**
- `chat.message.v1` to all participants with sender details and message ID
```

### Context: Error Code Catalog (from websocket-protocol.md)

```markdown
### 6.2 Error Code Catalog

WebSocket application errors use the **4000-4999 range** (distinct from standard WebSocket close codes 1000-1999).

| Code | Error | Description | Recovery Strategy |
|------|-------|-------------|-------------------|
| **4000** | `UNAUTHORIZED` | Invalid or expired JWT token | Refresh token and reconnect with new JWT |
| **4001** | `ROOM_NOT_FOUND` | Room does not exist or has been deleted | Notify user, redirect to room list |
| **4002** | `INVALID_VOTE` | Vote validation failed (invalid card value, no active round, already voted) | Show error to user, allow retry |
| **4003** | `FORBIDDEN` | Insufficient permissions (e.g., observer trying to vote, non-host starting round) | Show permission error, update UI to reflect role |
| **4004** | `VALIDATION_ERROR` | Request payload validation failed | Show field-specific errors, allow correction |
| **4005** | `INVALID_STATE` | Action not valid in current room/round state | Update local state from server, retry if appropriate |
| **4006** | `RATE_LIMIT_EXCEEDED` | Too many messages sent in short time | Throttle client-side message sending |
| **4007** | `ROOM_FULL` | Room has reached participant limit | Notify user, cannot join |
| **4008** | `POLICY_VIOLATION` | Protocol violation (e.g., didn't send room.join.v1 within 10s) | Reconnect with proper handshake |
| **4999** | `INTERNAL_SERVER_ERROR` | Unexpected server error | Retry with exponential backoff |
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
    *   **Summary:** This is the main WebSocket endpoint handler that manages connection lifecycle, authentication, and message routing. It already handles `room.join.v1` and `room.leave.v1` messages. The `onMessage` method (lines 212-267) currently has a placeholder comment indicating where message handlers should be integrated (line 254-260).
    *   **Recommendation:** You MUST integrate your MessageRouter into the `onMessage` method after the existing `room.join.v1` and `room.leave.v1` handlers. The handler already extracts `userId` and `roomId` from session properties (lines 215-216), which you SHOULD reuse in your handlers.
    *   **Key Pattern:** The handler uses a private method pattern for error handling: `sendError(session, requestId, code, error, message)` (line 619). You SHOULD use this method in your handlers when validation fails.
    *   **Important:** The handler is annotated with `@ServerEndpoint` and `@ApplicationScoped`. Your message handlers should also be `@ApplicationScoped` CDI beans for injection into the MessageRouter.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Summary:** This service implements all voting logic including `castVote`, `startRound`, `revealRound`, and `resetRound`. All methods return `Uni<>` reactive types and are already annotated with `@WithTransaction`.
    *   **Recommendation:** Your handlers MUST call these VotingService methods. Note that `castVote` requires UUID parameters for `roundId` and `participantId` (line 58), so you will need to parse these from strings. The service already handles event publishing via RoomEventPublisher, so handlers do NOT need to manually publish events.
    *   **Important:** The VotingService methods validate input (e.g., card value length on line 64-67) and throw `IllegalArgumentException` for invalid input. Your handlers MUST catch these exceptions and convert them to WebSocket error messages with code 4002 or 4004.
    *   **Critical:** The VotingService already publishes events after successful operations, so your handlers only need to call the service methods and handle errors.

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/WebSocketMessage.java`
    *   **Summary:** This class represents the message envelope structure with `type`, `requestId`, and `payload` fields. It includes static factory methods like `createError` (used in RoomWebSocketHandler line 620-622).
    *   **Recommendation:** You SHOULD use the `getPayload()` method to access the message payload as a `Map<String, Object>`. For extracting typed values, you will need to cast appropriately and handle ClassCastException for invalid payloads.

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/ConnectionRegistry.java`
    *   **Summary:** This registry manages active WebSocket connections and provides methods like `broadcastToRoom(roomId, message)` and `sendToSession(session, message)`.
    *   **Recommendation:** Your ChatMessageHandler SHOULD use `ConnectionRegistry.broadcastToRoom()` to send chat messages to all participants. Vote/round events are already broadcasted via VotingService → RoomEventPublisher.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomParticipant.java`
    *   **Summary:** This entity includes a `role` field of type `RoomRole` enum which determines permissions.
    *   **Recommendation:** You MUST check the participant's role before executing host-only operations. The role values are defined in the `RoomRole` enum (HOST, VOTER, OBSERVER). You will need to query RoomParticipantRepository to fetch the participant's role.

### Implementation Tips & Notes

*   **Tip:** The WebSocket protocol specification (api/websocket-protocol.md) is comprehensive and includes exact payload schemas for all message types. You MUST validate payloads against these schemas.

*   **Note:** The RoomWebSocketHandler already has a TODO comment at line 254-260 listing the exact message types that need handlers. Your MessageRouter MUST handle all these types: `vote.cast.v1`, `round.start.v1`, `round.reveal.v1`, `round.reset.v1`, `chat.message.v1`, `presence.update.v1`.

*   **Tip:** For chat message handling, you will likely need to create a simple broadcast-only implementation for now. The protocol spec shows chat messages should be broadcast with sender details and a message ID. Since chat persistence is not required for this task, you can broadcast directly via ConnectionRegistry.

*   **Architecture Pattern:** The task requires creating separate handler classes (VoteCastHandler, RoundRevealHandler, etc.) which should all implement a common interface. A recommended pattern would be:
    ```java
    public interface MessageHandler {
        Uni<Void> handle(Session session, WebSocketMessage message, String userId, String roomId);
    }
    ```
    Each handler class should be `@ApplicationScoped` and implement this interface.

*   **Authorization Strategy:** For host-only operations, you MUST:
    1. Extract `userId` from session properties (already done in RoomWebSocketHandler line 215)
    2. Query RoomParticipantRepository to get the participant's role
    3. Check if role == RoomRole.HOST
    4. If not, call `sendError(session, requestId, 4003, "FORBIDDEN", "Only host can perform this action")`
    5. Return early without calling VotingService

*   **Reactive Error Handling:** Since VotingService methods return `Uni<>`, you SHOULD use `.onFailure().recoverWithUni()` to catch exceptions and convert them to error messages. Example pattern:
    ```java
    votingService.castVote(roomId, roundId, participantId, cardValue)
        .onFailure(IllegalArgumentException.class).recoverWithUni(e -> {
            sendError(session, requestId, 4002, "INVALID_VOTE", e.getMessage());
            return Uni.createFrom().voidItem();
        })
        .onFailure().recoverWithUni(e -> {
            sendError(session, requestId, 4999, "INTERNAL_SERVER_ERROR", "Unexpected error");
            return Uni.createFrom().voidItem();
        })
        .subscribe().with(
            success -> Log.infof("Vote cast successfully"),
            failure -> Log.errorf("Failed to handle vote.cast.v1: %s", failure)
        );
    ```

*   **Payload Extraction Pattern:** The payload is a `Map<String, Object>`. You SHOULD create helper methods to safely extract typed values:
    ```java
    private String extractString(Map<String, Object> payload, String key, boolean required) {
        Object value = payload.get(key);
        if (value == null && required) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value != null ? value.toString() : null;
    }
    ```

*   **Warning:** The RoomWebSocketHandler is annotated with `@ApplicationScoped`, but WebSocket endpoints in Quarkus are typically instantiated once per connection. However, since the handler is marked `@ApplicationScoped`, it's shared across connections. Your handler classes should also be `@ApplicationScoped` and stateless to avoid concurrency issues. Do NOT store state in handler fields.

*   **Testing Consideration:** The acceptance criteria states "round.reveal.v1 from non-host returns error message (403 Forbidden)". This means you MUST verify authorization BEFORE calling VotingService methods, not rely on the service to do authorization checks.

*   **Current Round Tracking:** For vote casting, the payload only includes `cardValue`, not `roundId`. This means you will need to query the current active round for the room. You SHOULD add a helper method or query to RoundRepository like `findActiveRoundByRoomId(String roomId)` which returns the latest round where `revealedAt` is null. Alternatively, use `findLatestByRoomId()` and check if it's active.

*   **Chat Message Broadcast:** Unlike vote/round events which are published via RoomEventPublisher (already integrated with Redis Pub/Sub), chat messages should broadcast directly via ConnectionRegistry since they don't need Redis Pub/Sub for this task (chat persistence is out of scope). Use:
    ```java
    WebSocketMessage chatBroadcast = WebSocketMessage.create("chat.message.v1", UUID.randomUUID().toString(), chatPayload);
    connectionRegistry.broadcastToRoom(roomId, chatBroadcast);
    ```

*   **MessageRouter Integration:** In RoomWebSocketHandler.onMessage(), after handling `room.join.v1` and `room.leave.v1`, add:
    ```java
    // Route to message handlers
    messageRouter.route(session, message, userId, roomId)
        .subscribe().with(
            success -> Log.debugf("Message handled: %s", message.getType()),
            failure -> Log.errorf(failure, "Failed to handle message: %s", message.getType())
        );
    ```

*   **Participant ID Resolution:** For vote casting, you need to resolve the WebSocket session's userId to a RoomParticipant ID. You SHOULD query RoomParticipantRepository with:
    ```java
    roomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
        .onItem().transformToUni(participant -> {
            if (participant == null) {
                return Uni.createFrom().failure(
                    new IllegalArgumentException("Participant not found in room")
                );
            }
            return votingService.castVote(roomId, roundId, participant.participantId, cardValue);
        })
    ```

*   **Error Response Pattern:** Always send errors back to the requesting client before returning. Use the existing sendError method from RoomWebSocketHandler. If creating a centralized error handler, inject ConnectionRegistry and create a reusable error sender.

### Recommended Class Structure

```java
// Common interface for all message handlers
public interface MessageHandler {
    String getMessageType();
    Uni<Void> handle(Session session, WebSocketMessage message, String userId, String roomId);
}

@ApplicationScoped
public class VoteCastHandler implements MessageHandler {
    @Inject VotingService votingService;
    @Inject RoomParticipantRepository participantRepository;
    @Inject RoundRepository roundRepository;
    @Inject ConnectionRegistry connectionRegistry;

    @Override
    public String getMessageType() { return "vote.cast.v1"; }

    @Override
    public Uni<Void> handle(Session session, WebSocketMessage message, String userId, String roomId) {
        // 1. Extract and validate payload
        // 2. Find current active round
        // 3. Resolve participant ID
        // 4. Call votingService.castVote()
        // 5. Handle errors and send error responses
    }
}

@ApplicationScoped
public class MessageRouter {
    @Inject Instance<MessageHandler> handlers;  // CDI will inject all MessageHandler beans

    private Map<String, MessageHandler> handlerMap;

    @PostConstruct
    void init() {
        handlerMap = new HashMap<>();
        for (MessageHandler handler : handlers) {
            handlerMap.put(handler.getMessageType(), handler);
        }
    }

    public Uni<Void> route(Session session, WebSocketMessage message, String userId, String roomId) {
        MessageHandler handler = handlerMap.get(message.getType());
        if (handler == null) {
            return Uni.createFrom().voidItem(); // Unknown message type, ignore
        }
        return handler.handle(session, message, userId, roomId);
    }
}
```

---

## Summary Checklist for Coder Agent

Before you start coding, ensure you understand:

- [ ] Create 5 handler classes implementing MessageHandler interface: VoteCastHandler, RoundStartHandler, RoundRevealHandler, RoundResetHandler, ChatMessageHandler
- [ ] All handlers must be `@ApplicationScoped` and stateless
- [ ] Create MessageRouter that uses CDI Instance<MessageHandler> to discover and route messages
- [ ] Integrate MessageRouter into RoomWebSocketHandler.onMessage() method
- [ ] Implement authorization checks for host-only operations (start, reveal, reset)
- [ ] Extract and validate payload fields with proper error messages
- [ ] Call VotingService methods with reactive error handling
- [ ] Use ConnectionRegistry.sendToSession() for sending error messages
- [ ] Use ConnectionRegistry.broadcastToRoom() for chat messages
- [ ] Resolve current active round for vote casting (query for roundId)
- [ ] Resolve participant ID from userId + roomId for vote casting
- [ ] Handle all exceptions and convert to WebSocket error codes (4002, 4003, 4004, 4005, 4999)
- [ ] Return Uni<Void> from all handler methods
- [ ] Follow reactive programming patterns with onFailure().recoverWithUni()

**Next Steps:**
1. Create MessageHandler interface in `backend/src/main/java/com/scrumpoker/api/websocket/handler/`
2. Implement 5 handler classes (VoteCastHandler, RoundStartHandler, RoundRevealHandler, RoundResetHandler, ChatMessageHandler)
3. Create MessageRouter with CDI-based handler discovery
4. Integrate MessageRouter into RoomWebSocketHandler.onMessage()
5. Test each message type with authorization and validation checks

Good luck! Remember to handle authorization BEFORE calling service methods, and use reactive error handling throughout.
