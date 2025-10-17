# System Architecture Blueprint: Scrum Poker Platform

---

<!-- anchor: api-design-and-communication -->
### 3.7. API Design & Communication

<!-- anchor: api-style -->
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

---

<!-- anchor: communication-patterns -->
#### Communication Patterns

<!-- anchor: synchronous-rest-pattern -->
##### Synchronous REST (Request/Response)

**Use Cases:**
- User authentication and registration
- Room creation and configuration updates
- Subscription management (upgrade, cancellation, payment method updates)
- Report generation triggers and export downloads
- Organization settings management

**Pattern Characteristics:**
- Client blocks waiting for server response (typically <500ms)
- Transactional consistency guaranteed within single database transaction
- Idempotency keys for payment operations to prevent duplicate charges
- Error responses use standard HTTP status codes (4xx client errors, 5xx server errors)

**Example Endpoints:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth2 code for JWT token
- `POST /api/v1/rooms` - Create new estimation room
- `GET /api/v1/rooms/{roomId}` - Retrieve room configuration
- `PUT /api/v1/users/{userId}/preferences` - Update user preferences
- `POST /api/v1/subscriptions/{subscriptionId}/upgrade` - Upgrade subscription tier
- `GET /api/v1/reports/sessions?from=2025-01-01&to=2025-01-31` - Query session history

---

<!-- anchor: asynchronous-websocket-pattern -->
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

---

<!-- anchor: asynchronous-job-processing-pattern -->
##### Asynchronous Job Processing (Fire-and-Forget)

**Use Cases:**
- Report export generation (CSV, PDF) for large datasets
- Email notifications (subscription confirmations, payment receipts)
- Analytics aggregation for organizational dashboards
- Audit log archival to object storage

**Pattern Characteristics:**
- REST endpoint returns `202 Accepted` immediately with job ID
- Job message enqueued to Redis Stream
- Background worker consumes stream, processes job
- Client polls status endpoint or receives WebSocket notification on completion
- Job results stored in object storage (S3) with time-limited signed URLs

**Flow Example (Report Export):**
1. Client: `POST /api/v1/reports/export` → Server: `202 Accepted` + `{"jobId": "uuid", "status": "pending"}`
2. Server enqueues job to Redis Stream: `jobs:reports`
3. Background worker consumes job, queries PostgreSQL, generates CSV
4. Worker uploads file to S3, updates job status in database
5. Client polls: `GET /api/v1/jobs/{jobId}` → `{"status": "completed", "downloadUrl": "https://..."}`

---

<!-- anchor: key-interaction-flow-vote-round -->
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

~~~plantuml
@startuml
!pragma teoz true

title Vote Casting and Round Reveal - Sequence Diagram

actor "Alice\n(Voter)" as Alice
actor "Bob\n(Voter)" as Bob
actor "Charlie\n(Host)" as Charlie

participant "SPA\n(Alice's Browser)" as SPA_A
participant "SPA\n(Bob's Browser)" as SPA_B
participant "SPA\n(Charlie's Browser)" as SPA_C

participant "Load Balancer\n(Sticky Sessions)" as LB

box "Application Node 1" #LightBlue
  participant "WebSocket Handler A" as WS_A
  participant "Voting Service A" as VS_A
end box

box "Application Node 2" #LightGreen
  participant "WebSocket Handler B" as WS_B
  participant "Voting Service B" as VS_B
end box

box "Application Node 3" #LightYellow
  participant "WebSocket Handler C" as WS_C
  participant "Voting Service C" as VS_C
end box

participant "Redis Pub/Sub\n(Channel: room:abc123)" as Redis
database "PostgreSQL" as DB

== Vote Casting Phase ==

Alice -> SPA_A : Selects card "5", clicks Submit
activate SPA_A
SPA_A -> SPA_A : Optimistically hide card, show "Voted" state
SPA_A -> WS_A : WebSocket: {"type":"vote.cast.v1", "requestId":"r1", "payload":{"cardValue":"5"}}
deactivate SPA_A

activate WS_A
WS_A -> VS_A : castVote(roomId="abc123", participantId="alice", cardValue="5")
activate VS_A
VS_A -> DB : INSERT INTO vote (round_id, participant_id, card_value, voted_at) VALUES (...)
DB --> VS_A : Success
VS_A -> Redis : PUBLISH room:abc123 {"type":"vote.recorded.v1", "payload":{"participantId":"alice", "votedAt":"..."}}
VS_A --> WS_A : VoteRecorded
deactivate VS_A
WS_A --> SPA_A : WebSocket: {"type":"vote.recorded.v1", "requestId":"r1", "payload":{...}}
deactivate WS_A

& Bob -> SPA_B : Selects card "8", clicks Submit
activate SPA_B
SPA_B -> SPA_B : Optimistically hide card, show "Voted" state
SPA_B -> WS_B : WebSocket: {"type":"vote.cast.v1", "requestId":"r2", "payload":{"cardValue":"8"}}
deactivate SPA_B

activate WS_B
WS_B -> VS_B : castVote(roomId="abc123", participantId="bob", cardValue="8")
activate VS_B
VS_B -> DB : INSERT INTO vote (...)
DB --> VS_B : Success
VS_B -> Redis : PUBLISH room:abc123 {"type":"vote.recorded.v1", "payload":{"participantId":"bob", ...}}
VS_B --> WS_B : VoteRecorded
deactivate VS_B
WS_B --> SPA_B : WebSocket: {"type":"vote.recorded.v1", "requestId":"r2", ...}
deactivate WS_B

Redis -> WS_A : Subscriber receives: vote.recorded.v1 (alice)
activate WS_A
WS_A -> SPA_C : Broadcast to Charlie: {"type":"vote.recorded.v1", "payload":{"participantId":"alice", "hasVoted":true}}
deactivate WS_A

Redis -> WS_B : Subscriber receives: vote.recorded.v1 (alice)
activate WS_B
WS_B -> SPA_A : Broadcast to Alice (confirmation)
WS_B -> SPA_B : Broadcast to Bob: {"type":"vote.recorded.v1", "payload":{"participantId":"alice", ...}}
deactivate WS_B

Redis -> WS_C : Subscriber receives: vote.recorded.v1 (alice)
activate WS_C
WS_C -> SPA_C : Broadcast to Charlie (if connected to Node 3)
deactivate WS_C

note over Redis : Same broadcast pattern for Bob's vote (omitted for brevity)

== Reveal Phase ==

Charlie -> SPA_C : Clicks "Reveal Cards"
activate SPA_C
SPA_C -> WS_C : WebSocket: {"type":"round.reveal.v1", "requestId":"r3"}
deactivate SPA_C

activate WS_C
WS_C -> VS_C : revealRound(roomId="abc123", roundId="...")
activate VS_C

VS_C -> DB : SELECT card_value FROM vote WHERE round_id = ... AND participant_id IN (...)
DB --> VS_C : [{"participantId":"alice","cardValue":"5"},{"participantId":"bob","cardValue":"8"}]

VS_C -> VS_C : Calculate:\n- Average: (5+8)/2 = 6.5\n- Median: 6.5\n- Consensus: false (variance > threshold)

VS_C -> DB : UPDATE round SET revealed_at = NOW(), average = 6.5, median = 6.5, consensus_reached = false WHERE round_id = ...
DB --> VS_C : Success

VS_C -> Redis : PUBLISH room:abc123 {"type":"round.revealed.v1", "payload":{"votes":[...], "stats":{"avg":6.5,"median":6.5,"consensus":false}}}
VS_C --> WS_C : RevealCompleted
deactivate VS_C
WS_C --> SPA_C : WebSocket: {"type":"round.revealed.v1", ...}
deactivate WS_C

Redis -> WS_A : Subscriber receives: round.revealed.v1
activate WS_A
WS_A -> SPA_A : Broadcast: {"type":"round.revealed.v1", "payload":{"votes":[{"participantId":"alice","cardValue":"5"},{"participantId":"bob","cardValue":"8"}], "stats":{...}}}
activate SPA_A
SPA_A -> SPA_A : Animate card flip, display all votes and statistics
deactivate SPA_A
deactivate WS_A

Redis -> WS_B : Subscriber receives: round.revealed.v1
activate WS_B
WS_B -> SPA_B : Broadcast: round.revealed.v1
activate SPA_B
SPA_B -> SPA_B : Animate card flip, display results
deactivate SPA_B
deactivate WS_B

Redis -> WS_C : Subscriber receives: round.revealed.v1 (if different node subscription)
activate WS_C
WS_C -> SPA_C : Broadcast confirmation
activate SPA_C
SPA_C -> SPA_C : Display results, show consensus indicator
deactivate SPA_C
deactivate WS_C

@enduml
~~~

---

<!-- anchor: key-interaction-flow-oauth-login -->
#### Key Interaction Flow: OAuth2 Authentication (Google/Microsoft)

##### Description

This sequence demonstrates the OAuth2 authorization code flow for user authentication via Google or Microsoft identity providers, JWT token generation, and session establishment.

##### Diagram (PlantUML)

~~~plantuml
@startuml

title OAuth2 Authentication Flow - Google/Microsoft Login

actor "User" as User
participant "SPA\n(React App)" as SPA
participant "Quarkus API\n(/api/v1/auth)" as API
participant "OAuth2 Adapter" as OAuth
participant "User Service" as UserService
participant "PostgreSQL" as DB
participant "Google/Microsoft\nOAuth2 Provider" as Provider

User -> SPA : Clicks "Sign in with Google"
activate SPA

SPA -> SPA : Generate PKCE code_verifier & code_challenge,\nstore in sessionStorage
SPA -> Provider : Redirect to authorization URL:\nhttps://accounts.google.com/o/oauth2/v2/auth\n?client_id=...&redirect_uri=...&code_challenge=...
deactivate SPA

User -> Provider : Grants permission
Provider -> SPA : Redirect to callback:\nhttps://app.scrumpoker.com/auth/callback?code=AUTH_CODE
activate SPA

SPA -> API : POST /api/v1/auth/oauth/callback\n{"provider":"google", "code":"AUTH_CODE", "codeVerifier":"..."}
deactivate SPA

activate API
API -> OAuth : exchangeCodeForToken(provider, code, codeVerifier)
activate OAuth

OAuth -> Provider : POST /token\n{code, client_id, client_secret, code_verifier}
Provider --> OAuth : {"access_token":"...", "id_token":"..."}

OAuth -> OAuth : Validate id_token signature (JWT),\nextract claims: {sub, email, name, picture}
OAuth --> API : OAuthUserInfo{subject, email, name, avatarUrl}
deactivate OAuth

API -> UserService : findOrCreateUser(provider="google", subject="...", email="...", name="...")
activate UserService

UserService -> DB : SELECT * FROM user WHERE oauth_provider='google' AND oauth_subject='...'
alt User exists
  DB --> UserService : User{user_id, email, subscription_tier, ...}
else New user
  DB --> UserService : NULL
  UserService -> DB : INSERT INTO user (oauth_provider, oauth_subject, email, display_name, avatar_url, subscription_tier)\nVALUES ('google', '...', '...', '...', '...', 'FREE')
  DB --> UserService : User{user_id, ...}
  UserService -> UserService : Create default UserPreference record
  UserService -> DB : INSERT INTO user_preference (user_id, default_deck_type, theme) VALUES (...)
end

UserService --> API : User{user_id, email, displayName, subscriptionTier}
deactivate UserService

API -> API : Generate JWT access token:\n{sub: user_id, email, tier, exp: now+1h}
API -> API : Generate refresh token (UUID),\nstore in Redis with 30-day TTL

API --> SPA : 200 OK\n{"accessToken":"...", "refreshToken":"...", "user":{...}}
deactivate API

activate SPA
SPA -> SPA : Store tokens in localStorage,\nstore user in Zustand state
SPA -> User : Redirect to Dashboard
deactivate SPA

@enduml
~~~

---

<!-- anchor: rest-api-endpoints -->
#### REST API Endpoints Overview

**Authentication & User Management:**
- `POST /api/v1/auth/oauth/callback` - Exchange OAuth2 code for JWT tokens
- `POST /api/v1/auth/refresh` - Refresh expired access token
- `POST /api/v1/auth/logout` - Revoke refresh token
- `GET /api/v1/users/{userId}` - Retrieve user profile
- `PUT /api/v1/users/{userId}` - Update profile (display name, avatar)
- `GET /api/v1/users/{userId}/preferences` - Get user preferences
- `PUT /api/v1/users/{userId}/preferences` - Update default room settings, theme

**Room Management:**
- `POST /api/v1/rooms` - Create new room (authenticated or anonymous)
- `GET /api/v1/rooms/{roomId}` - Get room configuration and current state
- `PUT /api/v1/rooms/{roomId}/config` - Update room settings (host only)
- `DELETE /api/v1/rooms/{roomId}` - Delete room (owner only)
- `GET /api/v1/users/{userId}/rooms` - List user's owned rooms

**Subscription & Billing:**
- `GET /api/v1/subscriptions/{userId}` - Get current subscription status
- `POST /api/v1/subscriptions/checkout` - Create Stripe checkout session for upgrade
- `POST /api/v1/subscriptions/{subscriptionId}/cancel` - Cancel subscription (end of period)
- `POST /api/v1/subscriptions/webhook` - Stripe webhook endpoint (signature verification)
- `GET /api/v1/billing/invoices` - List payment history

**Reporting & Analytics:**
- `GET /api/v1/reports/sessions` - List session history (tier-gated pagination, filters)
- `GET /api/v1/reports/sessions/{sessionId}` - Detailed session report (tier-gated round detail)
- `POST /api/v1/reports/export` - Generate export job (CSV/PDF), returns job ID
- `GET /api/v1/jobs/{jobId}` - Poll export job status, retrieve download URL

**Organization Management (Enterprise):**
- `POST /api/v1/organizations` - Create organization workspace
- `GET /api/v1/organizations/{orgId}` - Get org settings
- `PUT /api/v1/organizations/{orgId}/sso` - Configure OIDC/SAML2 settings
- `POST /api/v1/organizations/{orgId}/members` - Invite member
- `DELETE /api/v1/organizations/{orgId}/members/{userId}` - Remove member
- `GET /api/v1/organizations/{orgId}/audit-logs` - Query audit trail

---

<!-- anchor: websocket-connection-lifecycle -->
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
