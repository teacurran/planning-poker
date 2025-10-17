/**
 * Mock data fixtures for OAuth authentication E2E tests.
 * These fixtures provide realistic test data that matches the application's type definitions.
 */

import type { Page } from '@playwright/test';
import type { TokenResponse, UserDTO } from '../../src/types/auth';
import type { RoomDTO, RoomListResponse } from '../../src/types/room';
import type { PKCESession } from '../../src/utils/pkce';

/**
 * Mock user data for testing
 */
export const mockUser: UserDTO = {
  userId: 'test-user-123',
  email: 'testuser@example.com',
  oauthProvider: 'google',
  displayName: 'Test User',
  avatarUrl: 'https://example.com/avatar.jpg',
  subscriptionTier: 'FREE',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

/**
 * Mock token response from OAuth callback
 */
export const mockTokenResponse: TokenResponse = {
  accessToken: 'mock-access-token-abc123',
  refreshToken: 'mock-refresh-token-xyz789',
  expiresIn: 3600,
  tokenType: 'Bearer',
  user: mockUser,
};

/**
 * Mock refreshed token response (new access token)
 */
export const mockRefreshedTokenResponse: TokenResponse = {
  accessToken: 'mock-refreshed-access-token-def456',
  refreshToken: 'mock-refresh-token-xyz789', // Same refresh token
  expiresIn: 3600,
  tokenType: 'Bearer',
  user: mockUser,
};

/**
 * Mock PKCE session data
 */
export const mockPKCESession: PKCESession = {
  codeVerifier: 'mock-code-verifier-abc123',
  redirectUri: 'http://localhost:5173/auth/callback',
  provider: 'google',
};

/**
 * Mock authorization code returned from OAuth provider
 */
export const mockAuthCode = 'mock-authorization-code-abc123';

/**
 * Mock room data
 */
export const mockRooms: RoomDTO[] = [
  {
    roomId: 'room-1',
    ownerId: 'test-user-123',
    organizationId: null,
    title: 'Sprint Planning Meeting',
    privacyMode: 'PRIVATE',
    config: {
      votingSystem: 'FIBONACCI',
      allowRevote: true,
      autoRevealVotes: false,
      participantLimit: 10,
    },
    createdAt: '2024-01-01T10:00:00Z',
    lastActiveAt: '2024-01-02T14:30:00Z',
    deletedAt: null,
  },
  {
    roomId: 'room-2',
    ownerId: 'test-user-123',
    organizationId: null,
    title: 'Backlog Refinement',
    privacyMode: 'PUBLIC',
    config: {
      votingSystem: 'T_SHIRT',
      allowRevote: false,
      autoRevealVotes: true,
      participantLimit: 20,
    },
    createdAt: '2024-01-03T09:00:00Z',
    lastActiveAt: '2024-01-03T09:00:00Z',
    deletedAt: null,
  },
];

/**
 * Mock paginated room list response
 */
export const mockRoomListResponse: RoomListResponse = {
  rooms: mockRooms,
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
};

/**
 * Helper function to set up authenticated state in localStorage
 * This simulates a user who is already logged in
 */
export async function setupAuthenticatedState(page: Page): Promise<void> {
  await page.addInitScript((authState) => {
    localStorage.setItem('auth_state', JSON.stringify(authState));
  }, {
    user: mockUser,
    accessToken: mockTokenResponse.accessToken,
    refreshToken: mockTokenResponse.refreshToken,
    isAuthenticated: true,
  });
}

/**
 * Helper function to set up PKCE session in sessionStorage
 * This simulates the state after user clicks "Sign in with Google"
 */
export async function setupPKCESession(page: Page): Promise<void> {
  await page.addInitScript((session) => {
    sessionStorage.setItem('oauth_pkce_session', JSON.stringify(session));
  }, mockPKCESession);
}

/**
 * Helper function to clear all browser storage
 * Use this in beforeEach hooks to ensure clean test state
 * Must be called after page navigation
 */
export async function clearAllStorage(page: Page): Promise<void> {
  // First navigate to the app if not already there
  const url = page.url();
  if (!url || url === 'about:blank') {
    await page.goto('/');
  }

  await page.evaluate(() => {
    localStorage.clear();
    sessionStorage.clear();
  });
}

/**
 * Helper function to set up common API route mocks
 * This mocks the backend API endpoints needed for most tests
 */
export async function setupCommonRouteMocks(page: Page): Promise<void> {
  // Mock the OAuth callback endpoint
  await page.route('**/api/v1/auth/oauth/callback', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(mockTokenResponse),
    });
  });

  // Mock the user profile endpoint
  await page.route(`**/api/v1/users/${mockUser.userId}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(mockUser),
    });
  });

  // Mock the rooms list endpoint
  await page.route('**/api/v1/rooms**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(mockRoomListResponse),
    });
  });

  // Mock the logout endpoint
  await page.route('**/api/v1/auth/logout', async (route) => {
    await route.fulfill({
      status: 204,
    });
  });
}
