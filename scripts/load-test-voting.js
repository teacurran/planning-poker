/**
 * k6 Load Test: WebSocket Voting Scenario
 *
 * Tests: 500 concurrent rooms with 10 participants each (5,000 WebSocket connections)
 * Validates: Real-time vote casting, WebSocket message latency, connection stability
 *
 * NFR Targets:
 * - p95 latency <200ms for WebSocket messages
 * - Support 500 concurrent sessions with 5,000 active WebSocket connections
 * - Error rate <1%
 *
 * Usage:
 *   # Full scale (500 rooms, 5,000 connections)
 *   k6 run scripts/load-test-voting.js
 *
 *   # Scaled down for local testing (50 rooms, 500 connections)
 *   k6 run -e VUS=500 -e ROOMS=50 scripts/load-test-voting.js
 *
 *   # Custom environment
 *   k6 run -e BASE_URL=https://staging.example.com -e WS_URL=wss://staging.example.com scripts/load-test-voting.js
 */

import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================================================
// Configuration
// ============================================================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080';
const TARGET_ROOMS = parseInt(__ENV.ROOMS || '500');
const PARTICIPANTS_PER_ROOM = parseInt(__ENV.PARTICIPANTS || '10');
const TARGET_VUS = TARGET_ROOMS * PARTICIPANTS_PER_ROOM; // 5,000 VUs
const RAMP_UP_DURATION = __ENV.RAMP_UP || '2m';
const SUSTAIN_DURATION = __ENV.SUSTAIN || '5m';
const RAMP_DOWN_DURATION = __ENV.RAMP_DOWN || '1m';

// Vote card values to randomly select from
const CARD_VALUES = ['0', '1', '2', '3', '5', '8', '13', '21', '?'];

// ============================================================================
// Custom Metrics
// ============================================================================

// WebSocket message round-trip latency
const wsMessageLatency = new Trend('ws_message_latency', true);

// Vote cast to vote recorded latency
const voteLatency = new Trend('vote_e2e_latency', true);

// WebSocket connection success rate
const wsConnectionSuccess = new Rate('ws_connection_success');

// Message processing error rate
const messageErrors = new Rate('message_errors');

// Counters for different message types
const messagesReceived = new Counter('messages_received_total');
const votesCast = new Counter('votes_cast_total');
const votesRecorded = new Counter('votes_recorded_total');

// ============================================================================
// Test Configuration
// ============================================================================

export const options = {
  scenarios: {
    voting_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_UP_DURATION, target: TARGET_VUS },     // Ramp up to target
        { duration: SUSTAIN_DURATION, target: TARGET_VUS },     // Sustain load
        { duration: RAMP_DOWN_DURATION, target: 0 }             // Ramp down
      ],
      gracefulRampDown: '30s'
    }
  },
  thresholds: {
    // WebSocket message latency must be <200ms for p95
    'ws_message_latency': ['p(95)<200', 'p(99)<500'],

    // Vote end-to-end latency
    'vote_e2e_latency': ['p(95)<200', 'p(99)<500'],

    // Connection success rate must be >99%
    'ws_connection_success': ['rate>0.99'],

    // Message error rate must be <1%
    'message_errors': ['rate<0.01'],

    // HTTP request failures (for auth) must be <1%
    'http_req_failed': ['rate<0.01']
  }
};

// ============================================================================
// Test Data Setup
// ============================================================================

// Generate room IDs for all VUs (each room has PARTICIPANTS_PER_ROOM VUs)
function getRoomId(vuId) {
  const roomNumber = Math.floor((vuId - 1) / PARTICIPANTS_PER_ROOM);
  return `room-${String(roomNumber).padStart(6, '0')}`;
}

function getParticipantName(vuId) {
  const participantNumber = ((vuId - 1) % PARTICIPANTS_PER_ROOM) + 1;
  return `participant-${participantNumber}`;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Generate a unique request ID for correlation
 */
function generateRequestId() {
  return `req-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Authenticate and get JWT token
 * In production, this would hit the auth endpoint.
 * For load testing, you may want to use pre-generated tokens.
 */
function authenticate(displayName) {
  // For this load test, we'll use a simplified approach
  // In a real scenario, you would:
  // 1. POST to /api/v1/auth/login or register endpoint
  // 2. Extract JWT from response
  // For now, we'll simulate with a test token or skip auth if backend allows

  // Option 1: Use test endpoint if available
  // const authResponse = http.post(`${BASE_URL}/api/v1/auth/test-login`, JSON.stringify({
  //   displayName: displayName
  // }), {
  //   headers: { 'Content-Type': 'application/json' }
  // });

  // Option 2: Use pre-configured test token
  // For load testing purposes, return a test token
  // NOTE: In production testing, replace this with actual authentication
  return 'test-jwt-token'; // Replace with real token generation
}

/**
 * Create a room for testing
 * Returns room ID
 */
function createRoom(roomId) {
  const payload = JSON.stringify({
    roomName: `Load Test Room ${roomId}`,
    privacyMode: 'PUBLIC'
  });

  const response = http.post(`${BASE_URL}/api/v1/rooms`, payload, {
    headers: {
      'Content-Type': 'application/json'
      // 'Authorization': `Bearer ${token}` // Add if auth required
    }
  });

  const success = check(response, {
    'room created': (r) => r.status === 201 || r.status === 200
  });

  if (success && response.json('roomId')) {
    return response.json('roomId');
  }

  // If creation fails, return the test room ID anyway for testing
  return roomId;
}

// ============================================================================
// Main Test Function
// ============================================================================

export default function() {
  const vuId = __VU;
  const iterationId = __ITER;
  const roomId = getRoomId(vuId);
  const participantName = getParticipantName(vuId);

  // Authenticate (simplified for load testing)
  const token = authenticate(participantName);

  // Track pending requests for latency measurement
  const pendingRequests = new Map();

  // WebSocket URL with room ID and token
  const wsUrl = `${WS_URL}/ws/room/${roomId}${token ? `?token=${token}` : ''}`;

  // Connect to WebSocket
  const connectionStart = Date.now();

  const response = ws.connect(wsUrl, null, function(socket) {
    const connectionEstablished = Date.now();
    const connectionTime = connectionEstablished - connectionStart;
    wsConnectionSuccess.add(1);

    console.log(`[VU ${vuId}] Connected to room ${roomId} in ${connectionTime}ms`);

    // ========================================================================
    // Socket Event Handlers
    // ========================================================================

    socket.on('open', () => {
      // Must send room.join.v1 within 10 seconds or connection will be closed
      const joinRequestId = generateRequestId();
      const joinStart = Date.now();
      pendingRequests.set(joinRequestId, { type: 'room.join.v1', start: joinStart });

      const joinMessage = JSON.stringify({
        type: 'room.join.v1',
        requestId: joinRequestId,
        payload: {
          displayName: participantName,
          role: 'VOTER'
        }
      });

      socket.send(joinMessage);
      console.log(`[VU ${vuId}] Sent room.join.v1`);
    });

    socket.on('message', (data) => {
      messagesReceived.add(1);

      try {
        const message = JSON.parse(data);
        const messageType = message.type;
        const requestId = message.requestId;

        // Handle different message types
        switch(messageType) {
          case 'room.joined.v1':
            // Join successful
            if (pendingRequests.has(requestId)) {
              const { start } = pendingRequests.get(requestId);
              const latency = Date.now() - start;
              wsMessageLatency.add(latency);
              pendingRequests.delete(requestId);
              console.log(`[VU ${vuId}] Joined room in ${latency}ms`);
            }
            break;

          case 'vote.recorded.v1':
            // Vote successfully recorded
            votesRecorded.add(1);
            if (pendingRequests.has(requestId)) {
              const { start } = pendingRequests.get(requestId);
              const latency = Date.now() - start;
              voteLatency.add(latency);
              wsMessageLatency.add(latency);
              pendingRequests.delete(requestId);
              console.log(`[VU ${vuId}] Vote recorded in ${latency}ms`);
            }
            break;

          case 'error.v1':
            // Error response
            messageErrors.add(1);
            console.error(`[VU ${vuId}] Error: ${JSON.stringify(message.payload)}`);
            if (pendingRequests.has(requestId)) {
              pendingRequests.delete(requestId);
            }
            break;

          case 'heartbeat.ping.v1':
            // Respond to heartbeat
            const pongMessage = JSON.stringify({
              type: 'heartbeat.pong.v1',
              requestId: generateRequestId(),
              payload: {}
            });
            socket.send(pongMessage);
            break;

          default:
            // Other message types (state updates, notifications)
            console.log(`[VU ${vuId}] Received ${messageType}`);
        }
      } catch (err) {
        messageErrors.add(1);
        console.error(`[VU ${vuId}] Failed to parse message: ${err.message}`);
      }
    });

    socket.on('close', () => {
      console.log(`[VU ${vuId}] Connection closed`);
    });

    socket.on('error', (err) => {
      messageErrors.add(1);
      console.error(`[VU ${vuId}] WebSocket error: ${err}`);
    });

    // ========================================================================
    // Test Behavior: Cast Votes
    // ========================================================================

    // Wait for join to complete (give it 2 seconds)
    socket.setTimeout(() => {
      // Cast first vote
      castVote(socket, vuId, pendingRequests);
    }, 2000);

    // Cast additional votes every 30-60 seconds (simulate realistic behavior)
    const voteIntervals = [32000, 47000, 65000, 80000];
    voteIntervals.forEach(interval => {
      socket.setTimeout(() => {
        castVote(socket, vuId, pendingRequests);
      }, interval);
    });

    // Keep connection alive for the sustain duration
    // Connection will be closed when VU ramps down
    socket.setTimeout(() => {
      socket.close();
    }, 120000); // 2 minutes per VU iteration
  });

  // Check WebSocket connection was established
  check(response, {
    'websocket connected': (r) => r && r.status === 101
  });

  if (!response || response.status !== 101) {
    wsConnectionSuccess.add(0);
    console.error(`[VU ${vuId}] Failed to connect: status ${response?.status}`);
  }
}

/**
 * Cast a vote via WebSocket
 */
function castVote(socket, vuId, pendingRequests) {
  const voteRequestId = generateRequestId();
  const voteStart = Date.now();
  const cardValue = CARD_VALUES[Math.floor(Math.random() * CARD_VALUES.length)];

  pendingRequests.set(voteRequestId, { type: 'vote.cast.v1', start: voteStart });

  const voteMessage = JSON.stringify({
    type: 'vote.cast.v1',
    requestId: voteRequestId,
    payload: {
      cardValue: cardValue
    }
  });

  socket.send(voteMessage);
  votesCast.add(1);
  console.log(`[VU ${vuId}] Cast vote: ${cardValue}`);
}

// ============================================================================
// Setup & Teardown
// ============================================================================

export function setup() {
  console.log(`========================================`);
  console.log(`Starting WebSocket Voting Load Test`);
  console.log(`========================================`);
  console.log(`Target Rooms: ${TARGET_ROOMS}`);
  console.log(`Participants per Room: ${PARTICIPANTS_PER_ROOM}`);
  console.log(`Total VUs: ${TARGET_VUS}`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`WebSocket URL: ${WS_URL}`);
  console.log(`Ramp Up: ${RAMP_UP_DURATION}`);
  console.log(`Sustain: ${SUSTAIN_DURATION}`);
  console.log(`Ramp Down: ${RAMP_DOWN_DURATION}`);
  console.log(`========================================\n`);

  // Optionally, pre-create rooms here if needed
  // For now, we'll let rooms be created on-demand or assume they exist

  return {
    startTime: Date.now()
  };
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n========================================`);
  console.log(`Load Test Completed`);
  console.log(`========================================`);
  console.log(`Total Duration: ${duration.toFixed(2)}s`);
  console.log(`========================================\n`);
}
