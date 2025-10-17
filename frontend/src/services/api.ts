/**
 * Axios API client with authentication and token refresh logic.
 *
 * This module configures an Axios instance with:
 * - Base URL configuration (environment-aware)
 * - Request interceptor to attach Authorization header
 * - Response interceptor to handle 401 errors and trigger token refresh
 * - Automatic retry of failed requests after token refresh
 */

import axios, { AxiosError, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/stores/authStore';
import { refreshAccessToken } from './authApi';
import type { ErrorResponse } from '@/types/auth';

// Configure base URL from environment variable with fallback
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

/**
 * Main Axios instance for API calls.
 * Configured with base URL, timeout, and default headers.
 */
export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Flag to prevent infinite refresh loops.
 * When a refresh is in progress, subsequent requests wait for it to complete.
 */
let isRefreshing = false;

/**
 * Queue of pending requests waiting for token refresh to complete.
 * Each item is a promise resolver that will retry the original request.
 */
let refreshSubscribers: ((token: string) => void)[] = [];

/**
 * Subscribe a request to the refresh queue.
 * The callback will be invoked when the refresh completes with the new token.
 */
function subscribeTokenRefresh(callback: (token: string) => void): void {
  refreshSubscribers.push(callback);
}

/**
 * Notify all queued requests that the refresh is complete.
 * Each queued request will be retried with the new token.
 */
function onTokenRefreshed(token: string): void {
  refreshSubscribers.forEach((callback) => callback(token));
  refreshSubscribers = [];
}

/**
 * Request interceptor: Attach Authorization header if user is authenticated.
 *
 * This interceptor runs before every request and adds the Bearer token
 * from the auth store if the user is authenticated.
 */
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const { accessToken } = useAuthStore.getState();

    // Only add Authorization header if token exists and this isn't a refresh request
    if (accessToken && !config.url?.includes('/auth/refresh')) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * Response interceptor: Handle 401 errors and trigger token refresh.
 *
 * When a request fails with 401 Unauthorized:
 * 1. If this is a retry attempt, reject immediately (prevent infinite loop)
 * 2. If a refresh is already in progress, queue this request
 * 3. Otherwise, initiate token refresh and queue this request
 * 4. After refresh completes, retry all queued requests with new token
 * 5. If refresh fails, clear auth state and redirect to login
 */
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ErrorResponse>) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };

    // Only handle 401 errors
    if (error.response?.status !== 401) {
      return Promise.reject(error);
    }

    // Prevent infinite retry loops - if this request already failed once, don't retry again
    if (originalRequest._retry) {
      // Clear auth and reject - the refresh token is invalid
      useAuthStore.getState().clearAuth();
      return Promise.reject(error);
    }

    // Mark this request as a retry to prevent loops
    originalRequest._retry = true;

    // If a refresh is already in progress, queue this request
    if (isRefreshing) {
      return new Promise((resolve) => {
        subscribeTokenRefresh((token: string) => {
          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${token}`;
          }
          resolve(apiClient(originalRequest));
        });
      });
    }

    // Start the refresh process
    isRefreshing = true;

    try {
      const { refreshToken } = useAuthStore.getState();

      if (!refreshToken) {
        // No refresh token available - clear auth and reject
        useAuthStore.getState().clearAuth();
        isRefreshing = false;
        return Promise.reject(error);
      }

      // Call refresh endpoint
      const tokenResponse = await refreshAccessToken(refreshToken);

      // Update auth store with new tokens
      useAuthStore.getState().setAuth(tokenResponse);

      // Notify all queued requests
      onTokenRefreshed(tokenResponse.accessToken);

      // Retry the original request with new token
      if (originalRequest.headers) {
        originalRequest.headers.Authorization = `Bearer ${tokenResponse.accessToken}`;
      }

      isRefreshing = false;
      return apiClient(originalRequest);

    } catch (refreshError) {
      // Refresh failed - clear auth and reject all queued requests
      useAuthStore.getState().clearAuth();
      isRefreshing = false;
      refreshSubscribers = []; // Clear the queue

      return Promise.reject(refreshError);
    }
  }
);

/**
 * Parse API error response into a user-friendly message.
 * Extracts the message field from ErrorResponse or provides a fallback.
 */
export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const errorResponse = error.response?.data as ErrorResponse | undefined;
    return errorResponse?.message || error.message || 'An unexpected error occurred';
  }

  if (error instanceof Error) {
    return error.message;
  }

  return 'An unexpected error occurred';
}
