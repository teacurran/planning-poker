/**
 * React Query hooks for reporting and analytics API.
 *
 * This module provides:
 * - useSessions(params): Fetch paginated session history with filters
 *
 * All hooks automatically handle:
 * - Loading states
 * - Error states
 * - Data caching and invalidation
 * - Authentication (via apiClient interceptors)
 * - Tier-based access control (403 errors trigger upgrade modal)
 */

import { useQuery, UseQueryOptions } from '@tanstack/react-query';
import { apiClient } from './api';
import { useAuthStore } from '@/stores/authStore';
import type { SessionListResponse, SessionsQueryParams } from '@/types/reporting';

// ============================================
// QUERY KEY FACTORIES
// ============================================

/**
 * Query key factory for reporting endpoints.
 * Ensures consistent cache keys across the application.
 */
export const reportingQueryKeys = {
  sessions: {
    all: ['sessions'] as const,
    lists: () => [...reportingQueryKeys.sessions.all, 'list'] as const,
    list: (userId: string, params: SessionsQueryParams) =>
      [...reportingQueryKeys.sessions.lists(), userId, params] as const,
    detail: (sessionId: string) => [...reportingQueryKeys.sessions.all, sessionId] as const,
  },
};

// ============================================
// SESSION HISTORY HOOKS
// ============================================

/**
 * Fetch paginated session history with optional filters.
 *
 * This hook automatically uses the current authenticated user's ID.
 * If the user is not authenticated, the query is disabled and returns no data.
 *
 * **Tier Requirements:**
 * - Free tier: Last 30 days, max 10 results
 * - Pro tier: Last 90 days, max 100 results
 * - Pro Plus/Enterprise: Unlimited history
 *
 * @param params - Query parameters (date range, pagination)
 * @param options - Additional React Query options
 * @returns React Query result with session list, pagination info, loading state, and error
 *
 * @example
 * ```tsx
 * function SessionHistoryPage() {
 *   const { data, isLoading, error } = useSessions({
 *     from: '2025-01-01',
 *     to: '2025-01-31',
 *     page: 0,
 *     size: 20
 *   });
 *
 *   if (isLoading) return <div>Loading...</div>;
 *   if (error) return <div>Error: {error.message}</div>;
 *   if (!data) return <div>No sessions found</div>;
 *
 *   return (
 *     <div>
 *       {data.sessions.map(session => (
 *         <div key={session.session_id}>{session.room_title}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useSessions(
  params: SessionsQueryParams = {},
  options?: Omit<UseQueryOptions<SessionListResponse, Error>, 'queryKey' | 'queryFn'>
) {
  const { user } = useAuthStore();

  // Default pagination values
  const page = params.page ?? 0;
  const size = params.size ?? 20;

  return useQuery<SessionListResponse, Error>({
    queryKey: reportingQueryKeys.sessions.list(user?.userId || '', { ...params, page, size }),
    queryFn: async () => {
      if (!user?.userId) {
        throw new Error('User not authenticated');
      }

      const response = await apiClient.get<SessionListResponse>('/reports/sessions', {
        params: {
          from: params.from,
          to: params.to,
          roomId: params.roomId,
          page,
          size,
        },
      });

      return response.data;
    },
    enabled: !!user?.userId,
    staleTime: 3 * 60 * 1000, // 3 minutes (reporting data doesn't change frequently)
    ...options,
  });
}
