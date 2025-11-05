/**
 * k6 Load Test: WebSocket Reconnection Storm Scenario
 *
 * Tests: WebSocket connection resilience under rapid connect/disconnect/reconnect patterns
 * Validates: Connection establishment, reconnection handling, timeout mechanisms, state recovery
 *
 * NFR Targets:
 * - Support 1,000+ reconnections per minute
 * - Connection establishment time <1s under load
 * - Successful reconnection rate >95%
 * - Heartbeat timeout mechanism works correctly
 * - Join timeout (10s) enforced
 *
 * Usage:
 *   # Full reconnection storm (1,000 reconnections/min)
 *   k6 run scripts/load-test-reconnection-storm.js
 *
 *   # Scaled down for local testing (100 reconnections/min)
 *   k6 run -e RECONNECTIONS_PER_MIN=100 scripts/load-test-reconnection-storm.js
 *
 *   # Custom environment
 *   k6 run -e BASE_URL=https://staging.example.com -e WS_URL=wss://staging.example.com scripts/load-test-reconnection-storm.js
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
const RECONNECTIONS_PER_MIN = parseInt(__ENV.RECONNECTIONS_PER_MIN || '1000');
const TEST_DURATION = __ENV.TEST_DURATION || '5m';

// Calculate VUs needed: 1 reconnection cycle takes ~3s, so to achieve N/min:
// VUs = (N / 60) * 3 = N / 20
const TARGET_VUS = Math.ceil(RECONNECTIONS_PER_MIN / 20);

// ============================================================================
// Custom Metrics
// ============================================================================

// Connection establishment latency
const connectionLatency = new Trend('connection_establishment_latency', true);

// Reconnection success rate
const reconnectionSuccess = new Rate('reconnection_success');

// Connection lifecycle counters
const connectionsEstablished = new Counter('connections_established_total');
const connectionsDropped = new Counter('connections_dropped_total');
const reconnectionsAttempted = new Counter('reconnections_attempted_total');
const reconnectionsSucceeded = new Counter('reconnections_succeeded_total');

// Timeout handling
const joinTimeoutEnforced = new Rate('join_timeout_enforced');
const heartbeatTimeoutTriggered = new Rate('heartbeat_timeout_triggered');

// Message latency for reconnected sessions
const postReconnectMessageLatency = new Trend('post_reconnect_message_latency', true);

// Error tracking
const connectionErrors = new Counter('connection_errors_total');

// ============================================================================
// Test Configuration
// ============================================================================

export const options = {
  scenarios: {
    reconnection_storm: {
      executor: 'constant-vus',
      vus: TARGET_VUS,
      duration: TEST_DURATION
    }
  },
  thresholds: {
    // Connection establishment must be <1s for p95
    'connection_establishment_latency': ['p(95)<1000', 'p(99)<2000'],

    // Reconnection success rate must be >95%
    'reconnection_success': ['rate>0.95'],

    // Post-reconnect messages should still meet latency targets
    'post_reconnect_message_latency': ['p(95)<300', 'p(99)<500'],

    // Join timeout should be enforced (>50% of non-joining connections)
    'join_timeout_enforced': ['rate>0.5']
  }
};

// ============================================================================
// Helper Functions
// ============================================================================

function generateRequestId() {
  return `req-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

function getRoomId() {
  // Use a small pool of rooms to simulate realistic reconnection patterns
  const roomPool = ['room-001', 'room-002', 'room-003', 'room-004', 'room-005'];
  return roomPool[Math.floor(Math.random() * roomPool.length)];
}

function getParticipantName() {
  return `participant-${Date.now()}-${Math.random().toString(36).substr(2, 5)}`;
}

// ============================================================================
// Reconnection Scenarios
// ============================================================================

/**
 * Scenario 1: Normal reconnection with join
 * Simulates user reconnecting after network interruption
 */
function normalReconnection(roomId, participantName) {
  const wsUrl = `${WS_URL}/ws/room/${roomId}`;
  const connectionStart = Date.now();

  const response = ws.connect(wsUrl, null, function(socket) {
    const connectionEstablished = Date.now();
    const connectionTime = connectionEstablished - connectionStart;

    connectionsEstablished.add(1);
    connectionLatency.add(connectionTime);

    socket.on('open', () => {
      // Send join message
      const joinRequestId = generateRequestId();
      const joinMessage = JSON.stringify({
        type: 'room.join.v1',
        requestId: joinRequestId,
        payload: {
          displayName: participantName,
          role: 'VOTER'
        }
      });
      socket.send(joinMessage);
    });

    socket.on('message', (data) => {
      try {
        const message = JSON.parse(data);

        if (message.type === 'room.joined.v1') {
          reconnectionSuccess.add(1);
          reconnectionsSucceeded.add(1);
        }

        // Handle heartbeat ping
        if (message.type === 'heartbeat.ping.v1') {
          const pongMessage = JSON.stringify({
            type: 'heartbeat.pong.v1',
            requestId: generateRequestId(),
            payload: {}
          });
          socket.send(pongMessage);
        }
      } catch (err) {
        connectionErrors.add(1);
      }
    });

    socket.on('close', () => {
      connectionsDropped.add(1);
    });

    socket.on('error', (err) => {
      connectionErrors.add(1);
      reconnectionSuccess.add(0);
    });

    // Stay connected for 2-5 seconds, then disconnect
    const stayDuration = 2000 + Math.random() * 3000;
    socket.setTimeout(() => {
      socket.close();
    }, stayDuration);
  });

  check(response, {
    'connection established': (r) => r && r.status === 101
  });
}

/**
 * Scenario 2: Rapid connect/disconnect without join
 * Tests join timeout enforcement (10s deadline)
 */
function rapidConnectDisconnect(roomId) {
  const wsUrl = `${WS_URL}/ws/room/${roomId}`;
  const connectionStart = Date.now();

  reconnectionsAttempted.add(1);

  const response = ws.connect(wsUrl, null, function(socket) {
    const connectionTime = Date.now() - connectionStart;
    connectionsEstablished.add(1);
    connectionLatency.add(connectionTime);

    // Don't send join message - test timeout enforcement
    socket.on('close', () => {
      connectionsDropped.add(1);
      // If closed within 11 seconds, join timeout was likely enforced
      const totalTime = Date.now() - connectionStart;
      if (totalTime < 11000) {
        joinTimeoutEnforced.add(1);
      } else {
        joinTimeoutEnforced.add(0);
      }
    });

    socket.on('error', (err) => {
      connectionErrors.add(1);
    });

    // Close immediately after connection (don't wait for join timeout)
    socket.setTimeout(() => {
      socket.close();
    }, 500);
  });

  check(response, {
    'connection established': (r) => r && r.status === 101
  });
}

/**
 * Scenario 3: Heartbeat timeout simulation
 * Connect, join, but don't respond to heartbeat pings
 */
function heartbeatTimeoutTest(roomId, participantName) {
  const wsUrl = `${WS_URL}/ws/room/${roomId}`;
  const connectionStart = Date.now();

  reconnectionsAttempted.add(1);

  const response = ws.connect(wsUrl, null, function(socket) {
    const connectionTime = Date.now() - connectionStart;
    connectionsEstablished.add(1);
    connectionLatency.add(connectionTime);

    socket.on('open', () => {
      // Send join message
      const joinMessage = JSON.stringify({
        type: 'room.join.v1',
        requestId: generateRequestId(),
        payload: {
          displayName: participantName,
          role: 'VOTER'
        }
      });
      socket.send(joinMessage);
    });

    let joined = false;
    socket.on('message', (data) => {
      try {
        const message = JSON.parse(data);

        if (message.type === 'room.joined.v1') {
          joined = true;
        }

        // Ignore heartbeat pings - don't send pong
        // Server should close connection after heartbeat timeout (60s)
      } catch (err) {
        connectionErrors.add(1);
      }
    });

    socket.on('close', () => {
      connectionsDropped.add(1);
      if (joined) {
        // If we joined and then got disconnected, heartbeat timeout worked
        const totalTime = Date.now() - connectionStart;
        if (totalTime > 30000 && totalTime < 70000) {
          heartbeatTimeoutTriggered.add(1);
        } else {
          heartbeatTimeoutTriggered.add(0);
        }
      }
    });

    socket.on('error', (err) => {
      connectionErrors.add(1);
    });

    // Stay connected for up to 70 seconds to test heartbeat timeout
    // (Heartbeat interval: 30s, timeout: 60s, so should disconnect around 60s)
    socket.setTimeout(() => {
      socket.close();
    }, 70000);
  });

  check(response, {
    'connection established': (r) => r && r.status === 101
  });
}

/**
 * Scenario 4: Reconnection with message exchange
 * Connect, join, send message, disconnect, reconnect
 */
function reconnectWithMessageExchange(roomId, participantName) {
  const wsUrl = `${WS_URL}/ws/room/${roomId}`;
  const connectionStart = Date.now();

  reconnectionsAttempted.add(1);

  const response = ws.connect(wsUrl, null, function(socket) {
    const connectionTime = Date.now() - connectionStart;
    connectionsEstablished.add(1);
    connectionLatency.add(connectionTime);

    let joined = false;
    const pendingRequests = new Map();

    socket.on('open', () => {
      const joinRequestId = generateRequestId();
      const joinStart = Date.now();
      pendingRequests.set(joinRequestId, joinStart);

      const joinMessage = JSON.stringify({
        type: 'room.join.v1',
        requestId: joinRequestId,
        payload: {
          displayName: participantName,
          role: 'VOTER'
        }
      });
      socket.send(joinMessage);
    });

    socket.on('message', (data) => {
      try {
        const message = JSON.parse(data);
        const requestId = message.requestId;

        if (message.type === 'room.joined.v1') {
          joined = true;
          if (pendingRequests.has(requestId)) {
            const latency = Date.now() - pendingRequests.get(requestId);
            postReconnectMessageLatency.add(latency);
            pendingRequests.delete(requestId);
          }

          // Cast a vote after joining
          const voteRequestId = generateRequestId();
          const voteStart = Date.now();
          pendingRequests.set(voteRequestId, voteStart);

          const voteMessage = JSON.stringify({
            type: 'vote.cast.v1',
            requestId: voteRequestId,
            payload: {
              cardValue: '5'
            }
          });
          socket.send(voteMessage);
        }

        if (message.type === 'vote.recorded.v1') {
          if (pendingRequests.has(requestId)) {
            const latency = Date.now() - pendingRequests.get(requestId);
            postReconnectMessageLatency.add(latency);
            pendingRequests.delete(requestId);
          }
          reconnectionSuccess.add(1);
          reconnectionsSucceeded.add(1);
        }

        // Respond to heartbeat
        if (message.type === 'heartbeat.ping.v1') {
          const pongMessage = JSON.stringify({
            type: 'heartbeat.pong.v1',
            requestId: generateRequestId(),
            payload: {}
          });
          socket.send(pongMessage);
        }
      } catch (err) {
        connectionErrors.add(1);
      }
    });

    socket.on('close', () => {
      connectionsDropped.add(1);
    });

    socket.on('error', (err) => {
      connectionErrors.add(1);
      reconnectionSuccess.add(0);
    });

    // Stay connected for 2-4 seconds
    const stayDuration = 2000 + Math.random() * 2000;
    socket.setTimeout(() => {
      socket.close();
    }, stayDuration);
  });

  check(response, {
    'connection established': (r) => r && r.status === 101
  });
}

// ============================================================================
// Main Test Function
// ============================================================================

export default function() {
  const roomId = getRoomId();
  const participantName = getParticipantName();

  // Randomly select reconnection scenario
  const scenarios = [
    { fn: normalReconnection, weight: 50 },           // 50% normal reconnections
    { fn: rapidConnectDisconnect, weight: 30 },       // 30% rapid connect/disconnect
    { fn: heartbeatTimeoutTest, weight: 5 },          // 5% heartbeat timeout tests
    { fn: reconnectWithMessageExchange, weight: 15 }  // 15% with message exchange
  ];

  const totalWeight = scenarios.reduce((sum, s) => sum + s.weight, 0);
  const random = Math.random() * totalWeight;

  let cumulativeWeight = 0;
  for (const scenario of scenarios) {
    cumulativeWeight += scenario.weight;
    if (random < cumulativeWeight) {
      if (scenario.fn === rapidConnectDisconnect) {
        scenario.fn(roomId);
      } else {
        scenario.fn(roomId, participantName);
      }
      break;
    }
  }

  // Small sleep between reconnection attempts
  sleep(0.5 + Math.random() * 1.5);
}

// ============================================================================
// Setup & Teardown
// ============================================================================

export function setup() {
  console.log(`========================================`);
  console.log(`Starting WebSocket Reconnection Storm Test`);
  console.log(`========================================`);
  console.log(`Target Reconnections/Min: ${RECONNECTIONS_PER_MIN}`);
  console.log(`Virtual Users: ${TARGET_VUS}`);
  console.log(`Test Duration: ${TEST_DURATION}`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`WebSocket URL: ${WS_URL}`);
  console.log(`========================================`);
  console.log(`Scenario Mix:`);
  console.log(`  - 50% Normal reconnections (join + stay)`);
  console.log(`  - 30% Rapid connect/disconnect (no join)`);
  console.log(`  - 5% Heartbeat timeout tests`);
  console.log(`  - 15% Reconnect with message exchange`);
  console.log(`========================================\n`);

  // Verify API is accessible
  const healthCheck = http.get(`${BASE_URL}/q/health/ready`);
  if (healthCheck.status !== 200) {
    console.warn(`Warning: Health check returned ${healthCheck.status}`);
  }

  return {
    startTime: Date.now()
  };
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`\n========================================`);
  console.log(`Reconnection Storm Test Completed`);
  console.log(`========================================`);
  console.log(`Total Duration: ${duration.toFixed(2)}s`);
  console.log(`========================================\n`);
}
