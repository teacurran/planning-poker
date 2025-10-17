/**
 * Custom React hook for accessing authentication state.
 * Provides a convenient interface to the auth store.
 */

import { useAuthStore } from '@/stores/authStore';

/**
 * Hook to access authentication state and actions.
 *
 * @example
 * ```tsx
 * function MyComponent() {
 *   const { isAuthenticated, user, clearAuth } = useAuth();
 *
 *   if (!isAuthenticated) {
 *     return <div>Please log in</div>;
 *   }
 *
 *   return (
 *     <div>
 *       <p>Welcome, {user.displayName}!</p>
 *       <button onClick={clearAuth}>Log out</button>
 *     </div>
 *   );
 * }
 * ```
 */
export function useAuth() {
  const user = useAuthStore((state) => state.user);
  const accessToken = useAuthStore((state) => state.accessToken);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const setAuth = useAuthStore((state) => state.setAuth);
  const clearAuth = useAuthStore((state) => state.clearAuth);
  const loadAuthFromStorage = useAuthStore((state) => state.loadAuthFromStorage);

  return {
    user,
    accessToken,
    refreshToken,
    isAuthenticated,
    setAuth,
    clearAuth,
    loadAuthFromStorage,
  };
}
