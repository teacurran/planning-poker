/**
 * Axios API client with authentication and token refresh logic.
 */

import axios, { AxiosError, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/stores/authStore';
import { refreshAccessToken } from './authApi';
import type { ErrorResponse } from '@/types/auth';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

let isRefreshing = false;

type RefreshSubscriber = {
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
};

let refreshSubscribers: RefreshSubscriber[] = [];

function subscribeTokenRefresh(subscriber: RefreshSubscriber): void {
  refreshSubscribers.push(subscriber);
}

function onTokenRefreshed(token: string): void {
  refreshSubscribers.forEach(({ resolve }) => resolve(token));
  refreshSubscribers = [];
}

function onTokenRefreshFailed(error: unknown): void {
  refreshSubscribers.forEach(({ reject }) => reject(error));
  refreshSubscribers = [];
}

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const { accessToken } = useAuthStore.getState();

    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ErrorResponse>) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };

    // Only handle 401 errors for token refresh
    if (error.response?.status !== 401) {
      return Promise.reject(error);
    }

    if (originalRequest._retry) {
      useAuthStore.getState().clearAuth();
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        subscribeTokenRefresh({
          resolve: (token: string) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            resolve(apiClient(originalRequest));
          },
          reject: (refreshError: unknown) => {
            reject(refreshError);
          },
        });
      });
    }

    isRefreshing = true;

    try {
      const { refreshToken } = useAuthStore.getState();

      if (!refreshToken) {
        useAuthStore.getState().clearAuth();
        isRefreshing = false;
        onTokenRefreshFailed(new Error('Refresh token missing'));
        return Promise.reject(error);
      }

      const tokenResponse = await refreshAccessToken(refreshToken);
      const newAccessToken = tokenResponse.access_token;

      // Update auth store with refreshed tokens
      const currentUser = useAuthStore.getState().user;
      if (currentUser) {
        useAuthStore.getState().setAuth({
          accessToken: newAccessToken,
          refreshToken: tokenResponse.refresh_token,
          expiresIn: tokenResponse.expires_in,
          user: currentUser,
        });
      }

      onTokenRefreshed(newAccessToken);

      if (originalRequest.headers) {
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
      }

      isRefreshing = false;
      return apiClient(originalRequest);

    } catch (refreshError) {
      useAuthStore.getState().clearAuth();
      isRefreshing = false;
      onTokenRefreshFailed(refreshError);

      return Promise.reject(refreshError);
    }
  }
);

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
