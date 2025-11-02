/**
 * End-to-End tests for voting flow with real-time WebSocket communication.
 *
 * These tests verify the complete voting experience including:
 * - User joins room and sees participant list
 * - Card selection and vote casting
 * - Real-time participant vote status updates
 * - Host reveal flow with animation and statistics
 * - Multi-user synchronization (two users voting simultaneously)
 * - WebSocket reconnection and state preservation
 * - Round reset functionality
 *
 * IMPORTANT: These tests require the backend server to be running on localhost:8080
 * Run the backend first: cd backend && mvn quarkus:dev
 * Then run tests: cd frontend && npm run test:e2e
 */

import { test, expect } from '@playwright/test';
import {
  mockAliceUser,
  mockBobUser,
  mockAliceToken,
  mockBobToken,
  testRoomId,
  setupAuthenticatedUser,
  waitForWebSocketConnection,
  setupVotingRouteMocks,
  selectVotingCard,
} from './fixtures/mockVotingData';

test.describe('Voting Flow', () => {
  test.beforeEach(async ({ page }) => {
    // Set up route mocks for API endpoints (but NOT WebSocket - using real backend)
    await setupVotingRouteMocks(page, mockAliceUser);
  });

  test('should join room, cast vote, and see participant update', async ({ page }) => {
    // Set up authenticated user (Alice as HOST)
    await setupAuthenticatedUser(page, mockAliceUser, mockAliceToken);

    // Navigate to room
    await page.goto(`/room/${testRoomId}`);

    // Verify room page loaded
    await expect(page.locator('h1')).toContainText('Planning Poker Room');
    await expect(page.locator(`text=Room ID: ${testRoomId}`)).toBeVisible();

    // Wait for WebSocket connection
    await waitForWebSocketConnection(page);

    // Verify connection status shows "Connected"
    await expect(page.locator('text=Connected')).toBeVisible();

    // Verify participant list is visible (should show Alice)
    await expect(page.locator('text=Alice (Host)')).toBeVisible();

    // Start a new round (Alice is host)
    const startButton = page.locator('button:has-text("Start New Round")');
    await expect(startButton).toBeVisible();
    await startButton.click();

    // Wait for round to start (voting cards should appear)
    await expect(page.locator('text=Cast Your Vote')).toBeVisible({ timeout: 5000 });

    // Select a voting card (value "5")
    await selectVotingCard(page, '5');

    // Verify optimistic UI update - "Vote Cast!" message appears
    await expect(page.locator('text=Vote Cast!')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('text=You voted: 5')).toBeVisible();

    // Verify waiting message
    await expect(page.locator('text=Waiting for host to reveal votes...')).toBeVisible();
  });

  test('should reveal votes and display statistics with animation', async ({ page }) => {
    // Set up authenticated user (Alice as HOST)
    await setupAuthenticatedUser(page, mockAliceUser, mockAliceToken);

    // Navigate to room
    await page.goto(`/room/${testRoomId}`);

    // Wait for WebSocket connection
    await waitForWebSocketConnection(page);

    // Start new round
    await page.locator('button:has-text("Start New Round")').click();
    await expect(page.locator('text=Cast Your Vote')).toBeVisible({ timeout: 5000 });

    // Cast vote
    await selectVotingCard(page, '8');
    await expect(page.locator('text=Vote Cast!')).toBeVisible({ timeout: 5000 });

    // Host reveals votes
    const revealButton = page.locator('button:has-text("Reveal")');
    await expect(revealButton).toBeVisible();
    await expect(revealButton).toBeEnabled();
    await revealButton.click();

    // Wait for reveal view to appear (with timeout for animation)
    await expect(page.locator('text=Voting Results')).toBeVisible({ timeout: 5000 });

    // Verify statistics are displayed
    // Note: Statistics will vary based on how many participants voted
    // For single user (Alice voting 8), average should be 8
    await expect(page.locator('text=Average:')).toBeVisible();
    await expect(page.locator('text=Median:')).toBeVisible();

    // Verify the revealed votes section is visible
    await expect(page.locator('text=All Votes')).toBeVisible();

    // Verify Alice's vote is visible in the reveal view
    await expect(page.locator('text=Alice (Host)')).toBeVisible();
    await expect(page.locator('text=8')).toBeVisible();
  });

  test('should handle multi-user voting synchronization', async ({ browser }) => {
    // Create two separate browser contexts for Alice (HOST) and Bob (VOTER)
    const aliceContext = await browser.newContext();
    const bobContext = await browser.newContext();

    const alicePage = await aliceContext.newPage();
    const bobPage = await bobContext.newPage();

    try {
      // Set up route mocks for both users
      await setupVotingRouteMocks(alicePage, mockAliceUser);
      await setupVotingRouteMocks(bobPage, mockBobUser);

      // Set up authenticated states
      await setupAuthenticatedUser(alicePage, mockAliceUser, mockAliceToken);
      await setupAuthenticatedUser(bobPage, mockBobUser, mockBobToken);

      // Navigate both users to the same room
      await alicePage.goto(`/room/${testRoomId}`);
      await bobPage.goto(`/room/${testRoomId}`);

      // Wait for both WebSocket connections
      await waitForWebSocketConnection(alicePage);
      await waitForWebSocketConnection(bobPage);

      // Verify both users see each other in participant list
      await expect(alicePage.locator('text=Bob (Voter)')).toBeVisible({ timeout: 10000 });
      await expect(bobPage.locator('text=Alice (Host)')).toBeVisible({ timeout: 10000 });

      // Alice starts the round (as host)
      await alicePage.locator('button:has-text("Start New Round")').click();

      // Both users should see voting interface
      await expect(alicePage.locator('text=Cast Your Vote')).toBeVisible({ timeout: 5000 });
      await expect(bobPage.locator('text=Cast Your Vote')).toBeVisible({ timeout: 5000 });

      // Alice votes "5"
      await selectVotingCard(alicePage, '5');
      await expect(alicePage.locator('text=Vote Cast!')).toBeVisible({ timeout: 5000 });

      // Bob votes "8"
      await selectVotingCard(bobPage, '8');
      await expect(bobPage.locator('text=Vote Cast!')).toBeVisible({ timeout: 5000 });

      // Alice should see that Bob voted (checkmark or voted status in participant list)
      // Note: This depends on the ParticipantList component implementation
      // Looking for visual indication that both users have voted

      // Alice reveals the round
      await alicePage.locator('button:has-text("Reveal")').click();

      // Both users should see the reveal view with statistics
      await expect(alicePage.locator('text=Voting Results')).toBeVisible({ timeout: 5000 });
      await expect(bobPage.locator('text=Voting Results')).toBeVisible({ timeout: 5000 });

      // Verify both see the same statistics
      // Average of 5 and 8 = 6.5, Median = 6.5
      await expect(alicePage.locator('text=Average:')).toBeVisible();
      await expect(bobPage.locator('text=Average:')).toBeVisible();

      // Verify both users' votes are visible in reveal view
      await expect(alicePage.locator('text=Alice (Host)')).toBeVisible();
      await expect(alicePage.locator('text=Bob (Voter)')).toBeVisible();
      await expect(bobPage.locator('text=Alice (Host)')).toBeVisible();
      await expect(bobPage.locator('text=Bob (Voter)')).toBeVisible();
    } finally {
      // Clean up contexts
      await aliceContext.close();
      await bobContext.close();
    }
  });

  test('should handle WebSocket reconnection and preserve state', async ({ page }) => {
    // Set up authenticated user
    await setupAuthenticatedUser(page, mockAliceUser, mockAliceToken);

    // Navigate to room
    await page.goto(`/room/${testRoomId}`);

    // Wait for WebSocket connection
    await waitForWebSocketConnection(page);

    // Start round and cast vote
    await page.locator('button:has-text("Start New Round")').click();
    await expect(page.locator('text=Cast Your Vote')).toBeVisible({ timeout: 5000 });
    await selectVotingCard(page, '5');
    await expect(page.locator('text=Vote Cast!')).toBeVisible({ timeout: 5000 });

    // Programmatically close WebSocket connection to simulate disconnection
    await page.evaluate(() => {
      // Access WebSocket through the window object if exposed for testing
      // This assumes the useWebSocket hook or WebSocketManager exposes the socket
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const wsInstances = (window as any).WebSocket?.instances;
      if (wsInstances && wsInstances.length > 0) {
        // Close the first WebSocket connection
        wsInstances[0].close();
      } else {
        // Alternative: Force close by accessing the prototype
        // This is a fallback if WebSocket isn't directly accessible
        console.warn('Could not find WebSocket instance to close');
      }
    });

    // Verify reconnection indicator appears
    await expect(page.locator('text=Disconnected - Reconnecting...')).toBeVisible({
      timeout: 5000,
    });

    // Wait for reconnection to complete
    await expect(page.locator('text=Connected')).toBeVisible({ timeout: 15000 });

    // Verify vote state is preserved after reconnection
    // User should still see "Vote Cast!" message
    await expect(page.locator('text=Vote Cast!')).toBeVisible();
    await expect(page.locator('text=You voted: 5')).toBeVisible();
  });

  test('should reset round and clear votes', async ({ page }) => {
    // Set up authenticated user (Alice as HOST)
    await setupAuthenticatedUser(page, mockAliceUser, mockAliceToken);

    // Navigate to room
    await page.goto(`/room/${testRoomId}`);

    // Wait for WebSocket connection
    await waitForWebSocketConnection(page);

    // Start round, cast vote, and reveal
    await page.locator('button:has-text("Start New Round")').click();
    await expect(page.locator('text=Cast Your Vote')).toBeVisible({ timeout: 5000 });
    await selectVotingCard(page, '8');
    await expect(page.locator('text=Vote Cast!')).toBeVisible({ timeout: 5000 });

    // Reveal votes
    await page.locator('button:has-text("Reveal")').click();
    await expect(page.locator('text=Voting Results')).toBeVisible({ timeout: 5000 });

    // Reset the round
    const resetButton = page.locator('button:has-text("Reset")');
    await expect(resetButton).toBeVisible();
    await resetButton.click();

    // Verify UI returns to waiting state
    await expect(page.locator('text=Waiting for Round to Start')).toBeVisible({ timeout: 5000 });

    // Verify no "Vote Cast!" message (state cleared)
    await expect(page.locator('text=Vote Cast!')).not.toBeVisible();

    // Host should see the start round button again
    await expect(page.locator('button:has-text("Start New Round")')).toBeVisible();
  });

  test('should show host controls only to host role', async ({ browser }) => {
    // Create two contexts: Alice (HOST) and Bob (VOTER)
    const aliceContext = await browser.newContext();
    const bobContext = await browser.newContext();

    const alicePage = await aliceContext.newPage();
    const bobPage = await bobContext.newPage();

    try {
      // Set up route mocks
      await setupVotingRouteMocks(alicePage, mockAliceUser);
      await setupVotingRouteMocks(bobPage, mockBobUser);

      // Set up authenticated states
      await setupAuthenticatedUser(alicePage, mockAliceUser, mockAliceToken);
      await setupAuthenticatedUser(bobPage, mockBobUser, mockBobToken);

      // Navigate both to room
      await alicePage.goto(`/room/${testRoomId}`);
      await bobPage.goto(`/room/${testRoomId}`);

      // Wait for connections
      await waitForWebSocketConnection(alicePage);
      await waitForWebSocketConnection(bobPage);

      // Alice (HOST) should see host controls
      await expect(alicePage.locator('button:has-text("Start New Round")')).toBeVisible();

      // Bob (VOTER) should NOT see host controls
      await expect(bobPage.locator('button:has-text("Start New Round")')).not.toBeVisible();

      // Start round as Alice
      await alicePage.locator('button:has-text("Start New Round")').click();

      // Alice should see Reveal and Reset buttons
      await expect(alicePage.locator('button:has-text("Reveal")')).toBeVisible({ timeout: 5000 });

      // Bob should NOT see Reveal or Reset buttons
      await expect(bobPage.locator('button:has-text("Reveal")')).not.toBeVisible();
      await expect(bobPage.locator('button:has-text("Reset")')).not.toBeVisible();
    } finally {
      await aliceContext.close();
      await bobContext.close();
    }
  });

  test('should display connection status correctly', async ({ page }) => {
    // Set up authenticated user
    await setupAuthenticatedUser(page, mockAliceUser, mockAliceToken);

    // Navigate to room
    await page.goto(`/room/${testRoomId}`);

    // Initially should show "Connecting to room..." with loading spinner
    // This may not be visible if connection is very fast, so we use a short timeout
    // and don't fail the test if it's not visible
    const connectingStatus = page.locator('text=Connecting to room...');
    await connectingStatus.isVisible({ timeout: 1000 }).catch(() => false);

    // Connection should succeed and show "Connected"
    await waitForWebSocketConnection(page);
    await expect(page.locator('text=Connected')).toBeVisible();

    // Verify green indicator dot is visible
    const connectedIndicator = page.locator('.bg-green-600, .bg-green-400').first();
    await expect(connectedIndicator).toBeVisible();
  });
});
