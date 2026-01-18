/**
 * Smoke test helper utilities for production-like testing.
 *
 * These helpers support environment-agnostic smoke testing against
 * staging or production environments with configurable URLs.
 */

import type { Page } from '@playwright/test';
import type { UserDTO } from '../../src/types/auth';

/**
 * Environment configuration for smoke tests
 */
export interface SmokeTestEnvironment {
  baseUrl: string;
  apiUrl: string;
  useMockOAuth: boolean;
  stripeTestMode: boolean;
}

/**
 * Get environment configuration from environment variables
 */
export function getEnvironmentConfig(): SmokeTestEnvironment {
  const baseUrl = process.env.BASE_URL || 'http://localhost:5173';
  const apiUrl = process.env.API_URL || 'http://localhost:8080';
  const useMockOAuth = process.env.SMOKE_TEST_MOCK_OAUTH === 'true';
  const stripeTestMode = process.env.STRIPE_TEST_MODE !== 'false'; // Default true

  return {
    baseUrl,
    apiUrl,
    useMockOAuth,
    stripeTestMode,
  };
}

/**
 * Smoke test user (persistent across test runs)
 */
export const smokeTestUser: UserDTO = {
  userId: 'smoke-test-user-001',
  email: 'smoke-test@example.com',
  oauthProvider: 'google',
  displayName: 'Smoke Test User',
  avatarUrl: 'https://example.com/smoke-test-avatar.jpg',
  subscriptionTier: 'FREE',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

/**
 * Second smoke test user for multi-user scenarios
 */
export const smokeTestUser2: UserDTO = {
  userId: 'smoke-test-user-002',
  email: 'smoke-test-2@example.com',
  oauthProvider: 'google',
  displayName: 'Smoke Test User 2',
  avatarUrl: 'https://example.com/smoke-test-avatar-2.jpg',
  subscriptionTier: 'FREE',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

/**
 * Pro tier smoke test user for subscription testing
 */
export const smokeTestProUser: UserDTO = {
  userId: 'smoke-test-pro-user-001',
  email: 'smoke-test-pro@example.com',
  oauthProvider: 'google',
  displayName: 'Smoke Test Pro User',
  avatarUrl: 'https://example.com/smoke-test-pro-avatar.jpg',
  subscriptionTier: 'PRO',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

/**
 * Mock tokens for smoke tests
 */
export const smokeTestToken = 'smoke-test-token-abc123';
export const smokeTestToken2 = 'smoke-test-token-xyz789';
export const smokeTestProToken = 'smoke-test-pro-token-def456';

/**
 * Setup authenticated state for smoke test user
 */
export async function setupSmokeTestAuth(
  page: Page,
  user: UserDTO = smokeTestUser,
  accessToken: string = smokeTestToken
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
 * Wait for WebSocket connection with retry logic for flaky networks
 */
export async function waitForWebSocketWithRetry(
  page: Page,
  maxRetries: number = 3
): Promise<void> {
  for (let i = 0; i < maxRetries; i++) {
    try {
      await page.waitForSelector('text=Connected', { timeout: 10000 });
      return; // Success
    } catch (error) {
      if (i === maxRetries - 1) {
        throw error; // Last retry failed
      }
      console.log(`WebSocket connection retry ${i + 1}/${maxRetries}`);
      await page.waitForTimeout(2000); // Wait before retry
    }
  }
}

/**
 * Select voting card with error handling
 */
export async function selectCard(page: Page, cardValue: string): Promise<void> {
  const cardButton = page.locator(`button:has-text("${cardValue}")`).first();
  await cardButton.waitFor({ state: 'visible', timeout: 5000 });
  await cardButton.click();
}

/**
 * Wait for async job completion with polling
 * Used for export jobs and other async operations
 */
export async function waitForJobCompletion(
  page: Page,
  jobId: string,
  maxWaitSeconds: number = 30
): Promise<string | null> {
  for (let i = 0; i < maxWaitSeconds; i++) {
    await page.waitForTimeout(1000);

    const statusElement = page.locator(`[data-testid="job-status-${jobId}"]`);
    const status = await statusElement.textContent().catch(() => null);

    if (status === 'COMPLETED') {
      const downloadUrlElement = page.locator(`[data-testid="download-url-${jobId}"]`);
      return await downloadUrlElement.getAttribute('href');
    }

    if (status === 'FAILED') {
      throw new Error(`Job ${jobId} failed`);
    }
  }

  throw new Error(`Job ${jobId} timed out after ${maxWaitSeconds} seconds`);
}

/**
 * Fill Stripe checkout form with test card
 * Works with Stripe hosted checkout page
 */
export async function fillStripeTestCard(page: Page): Promise<void> {
  // Wait for Stripe checkout iframe to load
  const stripeFrame = page.frameLocator('iframe[name*="stripe"]');

  // Fill card details
  await stripeFrame.locator('input[name="cardnumber"]').fill('4242424242424242');
  await stripeFrame.locator('input[name="exp-date"]').fill('12/34');
  await stripeFrame.locator('input[name="cvc"]').fill('123');
  await stripeFrame.locator('input[name="postal"]').fill('12345');
}

/**
 * Setup mock OAuth for staging environment
 * Reuses patterns from auth.spec.ts
 */
export async function setupMockOAuth(page: Page): Promise<void> {
  const mockAuthCode = 'mock-smoke-test-auth-code';
  const mockTokenResponse = {
    accessToken: smokeTestToken,
    refreshToken: `${smokeTestToken}-refresh`,
    expiresIn: 3600,
    tokenType: 'Bearer',
    user: smokeTestUser,
  };

  // Mock OAuth callback endpoint
  await page.route('**/api/v1/auth/oauth/callback', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(mockTokenResponse),
    });
  });

  // Setup PKCE session
  await page.addInitScript(
    (session) => {
      sessionStorage.setItem('oauth_pkce_session', JSON.stringify(session));
    },
    {
      codeVerifier: 'mock-code-verifier-smoke',
      codeChallenge: 'mock-code-challenge-smoke',
      redirectUri: `${process.env.BASE_URL || 'http://localhost:5173'}/auth/callback`,
      provider: 'google',
    }
  );

  return;
}

/**
 * Clean up test data (rooms created during smoke tests)
 * Use unique prefixes for easy identification
 */
export const SMOKE_TEST_PREFIX = 'smoke-test-';

/**
 * Generate unique room title for smoke tests
 */
export function generateSmokeTestRoomTitle(): string {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  return `${SMOKE_TEST_PREFIX}room-${timestamp}`;
}

/**
 * Retry wrapper for flaky operations
 */
export async function retryOperation<T>(
  operation: () => Promise<T>,
  maxRetries: number = 3,
  delayMs: number = 1000
): Promise<T> {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await operation();
    } catch (error) {
      if (i === maxRetries - 1) {
        throw error;
      }
      console.log(`Retry ${i + 1}/${maxRetries} after error:`, error);
      await new Promise(resolve => setTimeout(resolve, delayMs));
    }
  }
  throw new Error('Retry operation failed');
}
