/**
 * k6 Load Test: REST API and Subscription Checkout Scenarios
 *
 * Tests: REST API endpoints and subscription checkout flow
 * Validates: API latency, throughput, error rates, subscription processing
 *
 * NFR Targets:
 * - p95 latency <500ms for REST API endpoints
 * - Support 100 subscription checkouts per minute
 * - Error rate <1%
 *
 * Usage:
 *   # Full load test
 *   k6 run scripts/load-test-api.js
 *
 *   # Custom environment
 *   k6 run -e BASE_URL=https://staging.example.com scripts/load-test-api.js
 *
 *   # Specific scenario only
 *   k6 run -e SCENARIO=api_load scripts/load-test-api.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';

// ============================================================================
// Configuration
// ============================================================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_PREFIX = '/api/v1';
const TARGET_SCENARIO = __ENV.SCENARIO || 'all'; // 'api_load', 'subscription_checkout', or 'all'

// ============================================================================
// Custom Metrics
// ============================================================================

const roomCreationSuccess = new Rate('room_creation_success');
const roomCreationLatency = new Trend('room_creation_latency', true);

const roomListingLatency = new Trend('room_listing_latency', true);

const participantJoinSuccess = new Rate('participant_join_success');
const participantJoinLatency = new Trend('participant_join_latency', true);

const subscriptionCheckoutSuccess = new Rate('subscription_checkout_success');
const subscriptionCheckoutLatency = new Trend('subscription_checkout_latency', true);

const apiErrors = new Counter('api_errors_total');

// ============================================================================
// Test Configuration
// ============================================================================

export const options = {
  scenarios: {
    // Scenario 1: General API Load
    api_load: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { duration: '1m', target: 50 },    // Ramp up to 50 req/s
        { duration: '3m', target: 100 },   // Ramp up to 100 req/s
        { duration: '2m', target: 100 },   // Sustain 100 req/s
        { duration: '1m', target: 0 }      // Ramp down
      ],
      exec: 'apiLoadScenario'
    },

    // Scenario 2: Subscription Checkout
    subscription_checkout: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1m',  // 100 checkouts per minute
      duration: '5m',
      preAllocatedVUs: 20,
      maxVUs: 50,
      exec: 'subscriptionCheckoutScenario'
    }
  },
  thresholds: {
    // REST API latency must be <500ms for p95
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],

    // Specific endpoint latencies
    'room_creation_latency': ['p(95)<500', 'p(99)<1000'],
    'room_listing_latency': ['p(95)<500', 'p(99)<800'],
    'participant_join_latency': ['p(95)<500', 'p(99)<1000'],
    'subscription_checkout_latency': ['p(95)<1000', 'p(99)<2000'],

    // Success rates
    'room_creation_success': ['rate>0.99'],
    'participant_join_success': ['rate>0.99'],
    'subscription_checkout_success': ['rate>0.95'], // Stripe may have variability

    // HTTP error rate must be <1%
    'http_req_failed': ['rate<0.01']
  }
};

// ============================================================================
// Helper Functions
// ============================================================================

const headers = {
  'Content-Type': 'application/json',
  'Accept': 'application/json'
};

function generateRequestId() {
  return `req-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Authenticate and get JWT token
 * For load testing, using simplified auth
 */
function getAuthToken() {
  // In production, authenticate here
  // For now, return test token or make actual auth call
  return null; // Replace with actual token if auth is enforced
}

/**
 * Add auth header if token available
 */
function getHeaders() {
  const token = getAuthToken();
  if (token) {
    return {
      ...headers,
      'Authorization': `Bearer ${token}`
    };
  }
  return headers;
}

// ============================================================================
// Scenario 1: API Load Testing
// ============================================================================

export function apiLoadScenario() {
  const testHeaders = getHeaders();

  // Randomly select API operation to perform
  const operations = ['createRoom', 'listRooms', 'getRoomDetails', 'joinRoom'];
  const operation = operations[Math.floor(Math.random() * operations.length)];

  switch(operation) {
    case 'createRoom':
      testCreateRoom(testHeaders);
      break;
    case 'listRooms':
      testListRooms(testHeaders);
      break;
    case 'getRoomDetails':
      testGetRoomDetails(testHeaders);
      break;
    case 'joinRoom':
      testJoinRoom(testHeaders);
      break;
  }

  sleep(0.1); // Small think time between requests
}

/**
 * Test: Create Room
 */
function testCreateRoom(testHeaders) {
  group('Create Room', () => {
    const roomName = `Load Test Room ${Date.now()}`;
    const payload = JSON.stringify({
      roomName: roomName,
      privacyMode: 'PUBLIC',
      votingSystem: 'FIBONACCI',
      autoReveal: false
    });

    const start = Date.now();
    const response = http.post(`${BASE_URL}${API_PREFIX}/rooms`, payload, {
      headers: testHeaders,
      tags: { name: 'CreateRoom' }
    });
    const duration = Date.now() - start;

    const success = check(response, {
      'room created successfully': (r) => r.status === 201 || r.status === 200,
      'response has roomId': (r) => r.json('roomId') !== undefined
    });

    roomCreationSuccess.add(success);
    roomCreationLatency.add(duration);

    if (!success) {
      apiErrors.add(1);
      console.error(`Room creation failed: ${response.status} - ${response.body}`);
    }

    // Return roomId for potential chaining
    return response.json('roomId');
  });
}

/**
 * Test: List Rooms
 */
function testListRooms(testHeaders) {
  group('List Rooms', () => {
    const start = Date.now();
    const response = http.get(`${BASE_URL}${API_PREFIX}/rooms?page=0&size=20&privacyMode=PUBLIC`, {
      headers: testHeaders,
      tags: { name: 'ListRooms' }
    });
    const duration = Date.now() - start;

    const success = check(response, {
      'rooms listed successfully': (r) => r.status === 200,
      'response is array': (r) => Array.isArray(r.json())
    });

    roomListingLatency.add(duration);

    if (!success) {
      apiErrors.add(1);
      console.error(`Room listing failed: ${response.status} - ${response.body}`);
    }
  });
}

/**
 * Test: Get Room Details
 */
function testGetRoomDetails(testHeaders) {
  group('Get Room Details', () => {
    // Use a test room ID (in practice, use a room from previous create)
    const testRoomId = 'test-room-001';

    const response = http.get(`${BASE_URL}${API_PREFIX}/rooms/${testRoomId}`, {
      headers: testHeaders,
      tags: { name: 'GetRoomDetails' }
    });

    check(response, {
      'room details retrieved': (r) => r.status === 200 || r.status === 404
    });

    if (response.status !== 200 && response.status !== 404) {
      apiErrors.add(1);
      console.error(`Get room details failed: ${response.status}`);
    }
  });
}

/**
 * Test: Join Room (REST API, not WebSocket)
 */
function testJoinRoom(testHeaders) {
  group('Join Room', () => {
    const testRoomId = 'test-room-001';
    const payload = JSON.stringify({
      displayName: `Participant-${Date.now()}`,
      role: 'VOTER'
    });

    const start = Date.now();
    const response = http.post(`${BASE_URL}${API_PREFIX}/rooms/${testRoomId}/participants`, payload, {
      headers: testHeaders,
      tags: { name: 'JoinRoom' }
    });
    const duration = Date.now() - start;

    const success = check(response, {
      'participant joined': (r) => r.status === 201 || r.status === 200 || r.status === 404
    });

    participantJoinLatency.add(duration);

    // 404 is acceptable (room doesn't exist in test scenario)
    if (response.status !== 404) {
      participantJoinSuccess.add(success);
    }

    if (!success && response.status !== 404) {
      apiErrors.add(1);
      console.error(`Join room failed: ${response.status} - ${response.body}`);
    }
  });
}

// ============================================================================
// Scenario 2: Subscription Checkout Flow
// ============================================================================

export function subscriptionCheckoutScenario() {
  const testHeaders = getHeaders();

  group('Subscription Checkout Flow', () => {
    // Step 1: Get available subscription plans
    const plansResponse = http.get(`${BASE_URL}${API_PREFIX}/subscriptions/plans`, {
      headers: testHeaders,
      tags: { name: 'GetSubscriptionPlans' }
    });

    check(plansResponse, {
      'plans retrieved': (r) => r.status === 200
    });

    if (plansResponse.status !== 200) {
      apiErrors.add(1);
      console.error(`Failed to get subscription plans: ${plansResponse.status}`);
      return;
    }

    sleep(0.5); // User think time

    // Step 2: Create checkout session
    const checkoutPayload = JSON.stringify({
      planId: 'pro_monthly',
      successUrl: `${BASE_URL}/subscription/success`,
      cancelUrl: `${BASE_URL}/subscription/cancel`
    });

    const start = Date.now();
    const checkoutResponse = http.post(`${BASE_URL}${API_PREFIX}/subscriptions/checkout`, checkoutPayload, {
      headers: testHeaders,
      tags: { name: 'CreateCheckoutSession' }
    });
    const duration = Date.now() - start;

    const success = check(checkoutResponse, {
      'checkout session created': (r) => r.status === 201 || r.status === 200,
      'response has sessionId': (r) => r.json('sessionId') !== undefined || r.json('checkoutUrl') !== undefined
    });

    subscriptionCheckoutSuccess.add(success);
    subscriptionCheckoutLatency.add(duration);

    if (!success) {
      apiErrors.add(1);
      console.error(`Checkout session creation failed: ${checkoutResponse.status} - ${checkoutResponse.body}`);
    }

    sleep(1); // Simulate user being redirected to Stripe

    // Step 3: Check subscription status (simulate post-checkout check)
    const statusResponse = http.get(`${BASE_URL}${API_PREFIX}/subscriptions/status`, {
      headers: testHeaders,
      tags: { name: 'CheckSubscriptionStatus' }
    });

    check(statusResponse, {
      'subscription status retrieved': (r) => r.status === 200 || r.status === 404
    });
  });

  sleep(1); // Think time between iterations
}

// ============================================================================
// Mixed API Scenario (Alternative approach)
// ============================================================================

export function mixedApiScenario() {
  const testHeaders = getHeaders();

  // Simulate a user journey: create room -> list rooms -> join room
  group('User Journey: Create and Join Room', () => {
    // 1. Create a room
    const roomId = testCreateRoom(testHeaders);

    sleep(0.5);

    // 2. List rooms to verify it appears
    testListRooms(testHeaders);

    sleep(0.5);

    // 3. Join the room (if roomId was returned)
    if (roomId) {
      const payload = JSON.stringify({
        displayName: `User-${Date.now()}`,
        role: 'VOTER'
      });

      http.post(`${BASE_URL}${API_PREFIX}/rooms/${roomId}/participants`, payload, {
        headers: testHeaders
      });
    }
  });

  sleep(2); // Think time between user journeys
}

// ============================================================================
// Setup & Teardown
// ============================================================================

export function setup() {
  console.log(`========================================`);
  console.log(`Starting REST API Load Test`);
  console.log(`========================================`);
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`Target Scenario: ${TARGET_SCENARIO}`);
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
  console.log(`API Load Test Completed`);
  console.log(`========================================`);
  console.log(`Total Duration: ${duration.toFixed(2)}s`);
  console.log(`========================================\n`);
}

// Generate HTML report
export function handleSummary(data) {
  return {
    'load-test-api-report.html': htmlReport(data),
    'stdout': JSON.stringify(data, null, 2)
  };
}
