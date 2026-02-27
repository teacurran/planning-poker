/**
 * Authentication state management using Zustand.
 * Manages user authentication state, tokens, and localStorage persistence.
 */

import { create } from 'zustand';
import type { UserDTO, TokenResponse } from '@/types/auth';

interface AuthState {
  user: UserDTO | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;

  setAuth: (tokenResponse: TokenResponse) => void;
  clearAuth: () => void;
  loadAuthFromStorage: () => void;
}

const AUTH_STORAGE_KEY = 'auth_state';

function loadFromLocalStorage(): Pick<AuthState, 'user' | 'accessToken' | 'refreshToken' | 'isAuthenticated'> {
  try {
    const stored = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!stored) {
      return { user: null, accessToken: null, refreshToken: null, isAuthenticated: false };
    }

    const parsed = JSON.parse(stored);
    if (!parsed.accessToken || !parsed.user) {
      return { user: null, accessToken: null, refreshToken: null, isAuthenticated: false };
    }

    return {
      user: parsed.user,
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken,
      isAuthenticated: true,
    };
  } catch {
    return { user: null, accessToken: null, refreshToken: null, isAuthenticated: false };
  }
}

function saveToLocalStorage(state: Pick<AuthState, 'user' | 'accessToken' | 'refreshToken'>): void {
  try {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Ignore storage errors
  }
}

function clearLocalStorage(): void {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  } catch {
    // Ignore storage errors
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  ...loadFromLocalStorage(),

  setAuth: (tokenResponse: TokenResponse) => {
    const newState = {
      user: tokenResponse.user,
      accessToken: tokenResponse.accessToken,
      refreshToken: tokenResponse.refreshToken,
      isAuthenticated: true,
    };
    saveToLocalStorage(newState);
    set(newState);
  },

  clearAuth: () => {
    clearLocalStorage();
    set({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
    });
  },

  loadAuthFromStorage: () => {
    set(loadFromLocalStorage());
  },
}));
