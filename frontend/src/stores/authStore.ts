/**
 * Authentication state management using Zustand.
 * Manages user authentication state, tokens, and localStorage persistence.
 */

import { create } from 'zustand';
import type { UserDTO, TokenResponse } from '@/types/auth';

interface AuthState {
  // State
  user: UserDTO | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;

  // Actions
  setAuth: (tokenResponse: TokenResponse) => void;
  clearAuth: () => void;
  loadAuthFromStorage: () => void;
}

const AUTH_STORAGE_KEY = 'auth_state';

/**
 * Load authentication state from localStorage.
 * Called during store initialization to restore session on page reload.
 */
function loadFromLocalStorage(): Pick<AuthState, 'user' | 'accessToken' | 'refreshToken' | 'isAuthenticated'> {
  try {
    const stored = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!stored) {
      return {
        user: null,
        accessToken: null,
        refreshToken: null,
        isAuthenticated: false,
      };
    }

    const parsed = JSON.parse(stored);

    // Validate that required fields exist
    if (!parsed.accessToken || !parsed.user) {
      return {
        user: null,
        accessToken: null,
        refreshToken: null,
        isAuthenticated: false,
      };
    }

    return {
      user: parsed.user,
      accessToken: parsed.accessToken,
      refreshToken: parsed.refreshToken,
      isAuthenticated: true,
    };
  } catch (error) {
    console.error('Failed to load auth state from localStorage:', error);
    return {
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
    };
  }
}

/**
 * Save authentication state to localStorage.
 */
function saveToLocalStorage(state: Pick<AuthState, 'user' | 'accessToken' | 'refreshToken'>): void {
  try {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(state));
  } catch (error) {
    console.error('Failed to save auth state to localStorage:', error);
  }
}

/**
 * Clear authentication state from localStorage.
 */
function clearLocalStorage(): void {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  } catch (error) {
    console.error('Failed to clear auth state from localStorage:', error);
  }
}

/**
 * Zustand store for authentication state.
 * Automatically persists to localStorage and loads on initialization.
 */
export const useAuthStore = create<AuthState>((set) => ({
  // Initialize state from localStorage
  ...loadFromLocalStorage(),

  /**
   * Set authentication state from token response.
   * Stores tokens and user data in state and localStorage.
   */
  setAuth: (tokenResponse: TokenResponse) => {
    const newState = {
      user: tokenResponse.user,
      accessToken: tokenResponse.accessToken,
      refreshToken: tokenResponse.refreshToken,
      isAuthenticated: true,
    };

    // Save to localStorage
    saveToLocalStorage(newState);

    // Update Zustand state
    set(newState);
  },

  /**
   * Clear authentication state.
   * Removes tokens and user data from state and localStorage.
   */
  clearAuth: () => {
    // Clear localStorage
    clearLocalStorage();

    // Update Zustand state
    set({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
    });
  },

  /**
   * Manually reload authentication state from localStorage.
   * Useful for syncing state across tabs or after external changes.
   */
  loadAuthFromStorage: () => {
    set(loadFromLocalStorage());
  },
}));
