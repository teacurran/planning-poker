/**
 * Authentication API service.
 *
 * This module provides functions for authentication-related API calls:
 * - Token refresh (exchange refresh token for new access token)
 * - Logout (revoke refresh token)
 *
 * NOTE: This file uses a separate Axios instance WITHOUT the response interceptor
 * to prevent infinite loops when the refresh endpoint itself returns 401.
 */

import axios from 'axios';
import type { TokenResponse } from '@/types/auth';

// Use the same base URL as the main API client
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

/**
 * Dedicated Axios instance for auth operations.
 * Does NOT use the response interceptor to avoid infinite refresh loops.
 */
const authApiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Refresh the access token using a valid refresh token.
 *
 * Calls POST /api/v1/auth/refresh with the refresh token in the request body.
 * Returns a new TokenResponse with rotated tokens.
 *
 * @param refreshToken - The current valid refresh token
 * @returns Promise resolving to TokenResponse with new access and refresh tokens
 * @throws AxiosError if the refresh token is invalid or expired
 */
export async function refreshAccessToken(refreshToken: string): Promise<TokenResponse> {
  const response = await authApiClient.post<TokenResponse>('/auth/refresh', {
    refreshToken,
  });

  return response.data;
}

/**
 * Logout the user by revoking the refresh token.
 *
 * Calls POST /api/v1/auth/logout with the Authorization header.
 * This invalidates the refresh token on the server side.
 *
 * @param accessToken - The current access token for authorization
 * @returns Promise resolving when logout is complete
 */
export async function logout(accessToken: string): Promise<void> {
  await authApiClient.post(
    '/auth/logout',
    {},
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  );
}
