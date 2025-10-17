# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create `RoomWebSocketHandler` using Quarkus WebSocket extension. Implement endpoint `/ws/room/{roomId}` with JWT authentication on handshake (validate token from query parameter `?token={jwt}`). Manage connection lifecycle: onOpen (validate room exists, validate user authorized, subscribe to Redis channel `room:{roomId}`, broadcast `participant_joined` event), onClose (unsubscribe, broadcast `participant_left`), onMessage (route to message handlers), onError (log, close gracefully). Store active connections in ConcurrentHashMap keyed by room ID. Implement heartbeat protocol (ping/pong every 30 seconds).

**Task ID:** I4.T1
**Deliverables:** WebSocket endpoint with JWT-based authentication, Connection registry managing active sessions per room, Heartbeat mechanism (server sends ping, expects pong within 60 seconds), Participant joined/left event broadcasting, Error handling and graceful disconnection
**Acceptance Criteria:** WebSocket connection succeeds with valid JWT token, Connection rejected with 401 if token invalid/missing, Participant joined event broadcasted to existing room connections, Heartbeat mechanism prevents stale connections (auto-close after timeout), Connection gracefully closed on client disconnect, Multiple clients can connect to same room simultaneously

---

## Issues Detected

### **CRITICAL Issue 1: Heartbeat Ping Sending Not Implemented**
*   **Location:** `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java` lines 309-330
*   **Problem:** The `sendHeartbeatPings()` scheduled method is a stub that does NOT actually send ping frames to active connections. The method has a TODO comment on line 328 saying "TODO: Add method to ConnectionRegistry to get all sessions across all rooms"
*   **Impact:** Server does NOT send ping frames every 30 seconds as required by the WebSocket protocol specification. This violates the acceptance criteria "Heartbeat mechanism (server sends ping, expects pong within 60 seconds)".
*   **Expected Behavior:** Server must send WebSocket ping frames to all active connections every 30 seconds using `session.getAsyncRemote().sendPing()`.

### **CRITICAL Issue 2: Join Timeout Enforcement Not Functional**
*   **Location:** `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java` lines 377-395
*   **Problem:** The `enforceJoinTimeout()` scheduled method cannot actually close sessions because it only has the session ID string, not the actual Session object. Comment on line 388 says "In production, you'd want to maintain a session ID -> Session mapping"
*   **Impact:** Connections that fail to send `room.join.v1` within 10 seconds are NOT closed with code 4008 (POLICY_VIOLATION) as required by the protocol specification.
*   **Expected Behavior:** Sessions that don't send `room.join.v1` within 10 seconds must be forcibly closed with WebSocket close code 4008.

### **CRITICAL Issue 3: Participant Joined Event Not Broadcasted**
*   **Location:** `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java` lines 478-495
*   **Problem:** The `handleRoomJoin()` method is a placeholder that does NOT broadcast `room.participant_joined.v1` to existing room connections. Lines 489-491 have TODO comments saying this will be implemented in Task I4.T4.
*   **Impact:** When a new participant joins a room, existing participants are NOT notified via `room.participant_joined.v1` event. This violates the acceptance criteria "Participant joined event broadcasted to existing room connections".
*   **Expected Behavior:** When `room.join.v1` is received, the server must broadcast a `room.participant_joined.v1` event to ALL existing connections in the room (excluding the joining user).

---

## Best Approach to Fix

You MUST modify the following files to fix these issues:

### Fix 1: Implement Heartbeat Ping Sending

1. **Add method to `ConnectionRegistry.java`:**
   - Create a new method `public Set<Session> getAllSessions()` that returns all active sessions across all rooms
   - Iterate through `roomConnections.values()` and collect all sessions into a single Set

2. **Complete `sendHeartbeatPings()` in `RoomWebSocketHandler.java`:**
   - Call `connectionRegistry.getAllSessions()` to get all active sessions
   - Iterate through each session and call `session.getAsyncRemote().sendPing(ByteBuffer.allocate(0))` to send ping frame
   - Wrap in try-catch to handle errors for individual sessions without stopping the loop
   - Log success/failure counts

### Fix 2: Implement Join Timeout Enforcement

1. **Add session tracking to `RoomWebSocketHandler.java`:**
   - Create a new `ConcurrentHashMap<String, Session> sessionIdToSession` field at class level
   - In `onOpen()`, add the session to this map: `sessionIdToSession.put(session.getId(), session)`
   - In `onClose()`, remove the session from this map: `sessionIdToSession.remove(session.getId())`

2. **Complete `enforceJoinTimeout()` in `RoomWebSocketHandler.java`:**
   - When a session exceeds the join timeout deadline, look up the Session object using `sessionIdToSession.get(sessionId)`
   - If found and still open, close it with code 4008: `session.close(new CloseReason(CloseCodes.getCloseCode(4008), "POLICY_VIOLATION"))`
   - Note: You may need to define custom close code 4008 since standard WebSocket close codes only go up to 4999

### Fix 3: Broadcast Participant Joined Event

1. **Complete `handleRoomJoin()` in `RoomWebSocketHandler.java`:**
   - Extract displayName from the join message payload (or use a default like "Anonymous")
   - Determine the participant role (default to "VOTER" for now, full logic in I4.T4)
   - Create a `room.participant_joined.v1` message using `WebSocketMessage.createParticipantJoined()`
   - Call `connectionRegistry.broadcastToRoom(roomId, joinedMessage)` to notify existing participants
   - The broadcast should reach all connections in the room including the newly joined user (protocol spec doesn't say to exclude them)

### Additional Notes

- Do NOT implement database operations (creating RoomParticipant records) - that is for Task I4.T4
- Do NOT implement Redis Pub/Sub - that is for Task I4.T2
- Do NOT implement the `room.state.v1` snapshot - that is for Task I4.T4
- Focus ONLY on the three critical issues above to meet the acceptance criteria for Task I4.T1

### Testing Checklist

After making changes, verify:
- [ ] Code compiles without errors: `mvn compile -DskipTests`
- [ ] Heartbeat ping frames are sent every 30 seconds to all connections
- [ ] Sessions that don't send `room.join.v1` within 10 seconds are closed with code 4008
- [ ] When a client sends `room.join.v1`, all existing room participants receive `room.participant_joined.v1`
