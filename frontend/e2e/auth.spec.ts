/**
 * End-to-End tests for OAuth2 authentication flow.
 *
 * These tests verify the complete authentication flow including:
 * - OAuth login with Google/Microsoft
 * - Token storage in localStorage
 * - Dashboard access after authentication
 * - Unauthorized access redirects
 * - Token refresh on expiration
 *
 * All tests use mocked OAuth providers and API responses for deterministic testing.
 */

import { test, expect } from '@playwright/test';
import {
  mockTokenResponse,
  mockUser,
  mockAuthCode,
  mockRefreshedTokenResponse,
  setupAuthenticatedState,
  setupPKCESession,
  clearAllStorage,
  setupCommonRouteMocks,
} from './fixtures/mockOAuthResponse';

test.describe('OAuth Authentication Flow', () => {
  test.beforeEach(async ({ page }) => {
    // Set up route mocks FIRST, before any navigation
    await setupCommonRouteMocks(page);

    // Then clear all storage to ensure clean state
    await clearAllStorage(page);
  });

  test('should complete successful OAuth login flow with Google', async ({ page }) => {
    // Enable console logging to debug
    page.on('console', msg => console.log('[BROWSER CONSOLE]', msg.type(), msg.text()));

    // Navigate to login page
    await page.goto('/login');

    // Verify we're on the login page
    await expect(page).toHaveURL('/login');
    await expect(page.locator('h1')).toHaveText('Planning Poker');

    // Set up PKCE session data (simulating what LoginPage does before redirect)
    await setupPKCESession(page);

    // Simulate OAuth redirect by directly navigating to callback with auth code
    // In a real flow, user would be redirected to Google, grant consent, then back to callback
    // We skip the external redirect since we can't test against real OAuth providers
    await page.goto(`/auth/callback?code=${mockAuthCode}`);

    // Wait for redirect to dashboard after successful authentication
    await expect(page).toHaveURL('/dashboard', { timeout: 10000 });

    // Log page content for debugging
    const bodyText = await page.evaluate(() => document.body.innerText);
    console.log('[DEBUG] Dashboard page content:', bodyText.substring(0, 200));

    // Wait for network to be idle to ensure all API calls complete
    await page.waitForLoadState('networkidle');

    // Verify tokens are stored in localStorage
    const authState = await page.evaluate(() => {
      const stored = localStorage.getItem('auth_state');
      return stored ? JSON.parse(stored) : null;
    });

    expect(authState).toBeTruthy();
    expect(authState.isAuthenticated).toBe(true);
    expect(authState.accessToken).toBe(mockTokenResponse.accessToken);
    expect(authState.refreshToken).toBe(mockTokenResponse.refreshToken);
    expect(authState.user.userId).toBe(mockUser.userId);
    expect(authState.user.email).toBe(mockUser.email);

    // Verify dashboard content is displayed
    await expect(page.locator('h1')).toHaveText('Dashboard', { timeout: 10000 });

    // Verify user profile information is displayed
    await expect(page.getByText(mockUser.displayName)).toBeVisible();
    await expect(page.getByText(mockUser.email)).toBeVisible();

    // Verify rooms section is displayed
    await expect(page.getByRole('heading', { name: 'Your Rooms' })).toBeVisible();
    await expect(page.getByText('Sprint Planning Meeting')).toBeVisible();
    await expect(page.getByText('Backlog Refinement')).toBeVisible();
  });

  test('should handle OAuth error callback gracefully', async ({ page }) => {
    // Set up PKCE session
    await setupPKCESession(page);

    // Navigate to callback with error parameters
    await page.goto('/auth/callback?error=access_denied&error_description=User+denied+access');

    // Should show error message and allow return to login
    await expect(page.getByRole('heading', { name: /authentication failed/i })).toBeVisible();
    await expect(page.getByText(/user denied access/i)).toBeVisible();
  });

  test('should logout by clearing localStorage and redirecting', async ({ page }) => {
    // DON'T use setupAuthenticatedState() because it uses addInitScript which persists across reloads
    // Instead, manually set localStorage after navigating to the page

    // First navigate to home page
    await page.goto('/');

    // Then manually set auth state in localStorage
    await page.evaluate((authState) => {
      localStorage.setItem('auth_state', JSON.stringify(authState));
    }, {
      user: mockUser,
      accessToken: mockTokenResponse.accessToken,
      refreshToken: mockTokenResponse.refreshToken,
      isAuthenticated: true,
    });

    // Now navigate to dashboard
    await page.goto('/dashboard');

    // Wait for network idle and dashboard to fully load
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveURL('/dashboard');

    // Wait for dashboard to load
    await expect(page.locator('h1')).toHaveText('Dashboard', { timeout: 10000 });

    // Manually clear auth (simulating logout since no button exists yet)
    await page.evaluate(() => {
      localStorage.removeItem('auth_state');
    });

    // Reload the page to trigger auth check
    // This simulates what would happen after logout - the app needs to re-check auth state
    await page.reload();

    // Should be redirected to login page
    await expect(page).toHaveURL('/login', { timeout: 10000 });

    // Verify we're back on the login page with OAuth buttons visible
    await expect(page.getByRole('button', { name: /sign in with google/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /sign in with microsoft/i })).toBeVisible();
  });

  test('should redirect to login when accessing dashboard without authentication', async ({ page }) => {
    // Ensure no authentication state (already cleared in beforeEach)

    // Try to access dashboard directly
    await page.goto('/dashboard');

    // Should be redirected to login page
    await expect(page).toHaveURL('/login', { timeout: 5000 });

    // Verify login page is displayed
    await expect(page.getByRole('button', { name: /sign in with google/i })).toBeVisible();
  });

  test('should refresh expired token automatically and continue', async ({ page }) => {
    // Set up authenticated state
    await setupAuthenticatedState(page);

    let userRequestCount = 0;
    let roomRequestCount = 0;
    let refreshCalled = false;

    // Override the common route mocks with specific behavior for this test
    // Mock the user endpoints to return 401 on first call
    await page.route('**/api/v1/users/**', async (route) => {
      const url = route.request().url();

      if (url.includes('/rooms')) {
        // Rooms endpoint
        roomRequestCount++;
        if (roomRequestCount === 1) {
          // First call: Return 401 to simulate expired token
          await route.fulfill({
            status: 401,
            contentType: 'application/json',
            body: JSON.stringify({
              error: 'Unauthorized',
              message: 'Access token expired',
              timestamp: new Date().toISOString(),
            }),
          });
        } else {
          // Subsequent calls: Return success (after token refresh)
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
        }
      } else {
        // User profile endpoint
        userRequestCount++;
        if (userRequestCount === 1) {
          // First call: Return 401 to simulate expired token
          await route.fulfill({
            status: 401,
            contentType: 'application/json',
            body: JSON.stringify({
              error: 'Unauthorized',
              message: 'Access token expired',
              timestamp: new Date().toISOString(),
            }),
          });
        } else {
          // Subsequent calls: Return success (after token refresh)
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(mockUser),
          });
        }
      }
    });

    // Mock the token refresh endpoint
    await page.route('**/api/v1/auth/refresh', async (route) => {
      refreshCalled = true;
      console.log('[MOCK] Refresh token endpoint called');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockRefreshedTokenResponse),
      });
    });

    // Navigate to dashboard
    await page.goto('/dashboard');

    // Wait for the refresh to happen and dashboard to load
    await expect(page).toHaveURL('/dashboard');

    // Wait for dashboard heading to appear (indicates successful load)
    await expect(page.locator('h1')).toHaveText('Dashboard', { timeout: 10000 });

    // Verify that refresh was called
    expect(refreshCalled).toBe(true);

    // Verify that the user endpoint was called at least twice (initial + retry)
    expect(userRequestCount).toBeGreaterThanOrEqual(2);

    // Verify localStorage was updated with new access token
    const authState = await page.evaluate(() => {
      const stored = localStorage.getItem('auth_state');
      return stored ? JSON.parse(stored) : null;
    });

    expect(authState).toBeTruthy();
    expect(authState.accessToken).toBe(mockRefreshedTokenResponse.accessToken);

    // Verify user is still on dashboard (not redirected to login)
    await expect(page).toHaveURL('/dashboard');
  });

  test('should redirect to login when refresh token is invalid', async ({ page }) => {
    // Set up authenticated state
    await setupAuthenticatedState(page);

    // Override the common route mocks with specific behavior for this test
    // Mock the user endpoints to always return 401
    await page.route('**/api/v1/users/**', async (route) => {
      console.log('[MOCK] User endpoint returning 401');
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Unauthorized',
          message: 'Access token expired',
          timestamp: new Date().toISOString(),
        }),
      });
    });

    // Mock the token refresh endpoint to fail (invalid refresh token)
    await page.route('**/api/v1/auth/refresh', async (route) => {
      console.log('[MOCK] Refresh endpoint returning 401');
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Unauthorized',
          message: 'Invalid refresh token',
          timestamp: new Date().toISOString(),
        }),
      });
    });

    // Navigate to dashboard
    await page.goto('/dashboard');

    // Should be redirected to login page when refresh fails
    await expect(page).toHaveURL('/login', { timeout: 15000 });

    // Verify tokens are cleared from localStorage
    const authState = await page.evaluate(() => {
      return localStorage.getItem('auth_state');
    });

    expect(authState).toBeNull();
  });

  test('should handle missing PKCE session on callback', async ({ page }) => {
    // Navigate to callback WITHOUT setting up PKCE session
    await page.goto(`/auth/callback?code=${mockAuthCode}`);

    // Should show error about invalid state
    await expect(page.getByRole('heading', { name: /authentication failed/i })).toBeVisible();
    await expect(page.getByText(/pkce session data not found/i)).toBeVisible();
  });
});
