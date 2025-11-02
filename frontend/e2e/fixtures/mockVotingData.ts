/**
 * Mock data fixtures for voting E2E tests.
 * These fixtures provide realistic test data for WebSocket voting flow testing.
 */

import type { Page } from '@playwright/test';
import type { UserDTO } from '../../src/types/auth';

/**
 * Mock users for multi-user voting tests
 */
export const mockAliceUser: UserDTO = {
  userId: 'alice-123',
  email: 'alice@example.com',
  oauthProvider: 'google',
  displayName: 'Alice (Host)',
  avatarUrl: 'https://example.com/alice.jpg',
  subscriptionTier: 'FREE',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

export const mockBobUser: UserDTO = {
  userId: 'bob-456',
  email: 'bob@example.com',
  oauthProvider: 'google',
  displayName: 'Bob (Voter)',
  avatarUrl: 'https://example.com/bob.jpg',
  subscriptionTier: 'FREE',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

/**
 * Mock access tokens for testing
 */
export const mockAliceToken = 'mock-alice-token-abc123';
export const mockBobToken = 'mock-bob-token-xyz789';

/**
 * Test room ID - should match a room created in the backend for E2E tests
 */
export const testRoomId = 'e2e-test-room';

/**
 * Helper function to set up authenticated state for a specific user
 */
export async function setupAuthenticatedUser(
  page: Page,
  user: UserDTO,
  accessToken: string
): Promise<void> {
  await page.addInitScript(
    (authState) => {
      localStorage.setItem('auth_state', JSON.stringify(authState));
    },
    {
      user,
      accessToken,
      refreshToken: `${accessToken}-refresh`,
      isAuthenticated: true,
    }
  );
}

/**
 * Helper function to wait for WebSocket connection to be established
 * Checks for the "Connected" status indicator in the UI
 */
export async function waitForWebSocketConnection(page: Page): Promise<void> {
  // Wait for the connection status to show "Connected"
  await page.waitForSelector('text=Connected', { timeout: 10000 });
}

/**
 * Helper function to setup common route mocks for voting tests
 * This prevents real API calls and provides consistent test data
 */
export async function setupVotingRouteMocks(page: Page, user: UserDTO): Promise<void> {
  // Mock user profile endpoint
  await page.route('**/api/v1/users/**', async (route) => {
    const url = route.request().url();

    if (url.includes('/rooms')) {
      // User rooms endpoint
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          rooms: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        }),
      });
    } else {
      // User profile endpoint
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(user),
      });
    }
  });

  // Mock room endpoint (for initial room load)
  await page.route(`**/api/v1/rooms/${testRoomId}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        roomId: testRoomId,
        ownerId: mockAliceUser.userId,
        organizationId: null,
        title: 'E2E Test Room',
        privacyMode: 'PUBLIC',
        config: {
          votingSystem: 'FIBONACCI',
          allowRevote: true,
          autoRevealVotes: false,
          participantLimit: 10,
          deckType: 'fibonacci',
        },
        createdAt: '2024-01-01T10:00:00Z',
        lastActiveAt: '2024-01-02T14:30:00Z',
        deletedAt: null,
      }),
    });
  });
}

/**
 * Helper function to click a voting card by value
 */
export async function selectVotingCard(page: Page, cardValue: string): Promise<void> {
  // Find the button with the specific card value
  // The DeckSelector renders buttons with the card value as text
  const cardButton = page.locator(`button:has-text("${cardValue}")`).first();
  await cardButton.click();
}

/**
 * Helper function to verify participant has voted in the participant list
 */
export async function verifyParticipantVoted(
  page: Page,
  participantName: string
): Promise<boolean> {
  // Look for checkmark icon next to participant name (indicates voted)
  const participantRow = page.locator(`text=${participantName}`).locator('..');
  const checkmark = participantRow.locator('svg[data-icon="check"]');
  return await checkmark.isVisible();
}
