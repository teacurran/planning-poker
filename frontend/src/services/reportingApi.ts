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

import { useQuery, useMutation, UseQueryOptions, UseMutationOptions } from '@tanstack/react-query';
import { apiClient } from './api';
import { useAuthStore } from '@/stores/authStore';
import type {
  SessionListResponse,
  SessionsQueryParams,
  DetailedSessionReportDTO,
  ExportJobRequest,
  ExportJobResponse,
  JobStatusResponse,
} from '@/types/reporting';

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
  exportJobs: {
    all: ['exportJobs'] as const,
    status: (jobId: string) => [...reportingQueryKeys.exportJobs.all, jobId] as const,
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

/**
 * Fetch detailed session report with round-by-round breakdown.
 *
 * **Tier Requirements:**
 * - Free tier: Returns 403 Forbidden error (shows UpgradeModal)
 * - Pro tier: Full round details, votes, and user consistency metrics
 * - Pro Plus/Enterprise: Same as Pro tier
 *
 * @param sessionId - Session ID to fetch details for
 * @param options - Additional React Query options
 * @returns React Query result with detailed session data
 *
 * @example
 * ```tsx
 * function SessionDetailPage() {
 *   const { sessionId } = useParams();
 *   const { data, isLoading, error } = useSessionDetail(sessionId!);
 *
 *   if (isLoading) return <div>Loading...</div>;
 *   if (error) {
 *     // Handle 403 error by showing UpgradeModal
 *     if (error.response?.status === 403) {
 *       return <UpgradeModal />;
 *     }
 *     return <div>Error: {error.message}</div>;
 *   }
 *
 *   return <div>{data.room_title}</div>;
 * }
 * ```
 */
export function useSessionDetail(
  sessionId: string,
  options?: Omit<UseQueryOptions<DetailedSessionReportDTO, Error>, 'queryKey' | 'queryFn'>
) {
  const { user } = useAuthStore();

  return useQuery<DetailedSessionReportDTO, Error>({
    queryKey: reportingQueryKeys.sessions.detail(sessionId),
    queryFn: async () => {
      if (!user?.userId) {
        throw new Error('User not authenticated');
      }

      const response = await apiClient.get<DetailedSessionReportDTO>(
        `/reports/sessions/${sessionId}`
      );

      return response.data;
    },
    enabled: !!user?.userId && !!sessionId,
    staleTime: 3 * 60 * 1000, // 3 minutes
    ...options,
  });
}

// ============================================
// EXPORT JOB HOOKS
// ============================================

/**
 * Create an export job for a session report.
 *
 * Returns a job ID which can be used to poll job status with useExportJobStatus.
 *
 * **Tier Requirements:**
 * - Free tier: Returns 403 Forbidden error
 * - Pro tier: Can export to CSV or PDF
 * - Pro Plus/Enterprise: Same as Pro tier
 *
 * @param options - Mutation options (onSuccess, onError, etc.)
 * @returns Mutation object with mutate function
 *
 * @example
 * ```tsx
 * function ExportControls({ sessionId }) {
 *   const exportMutation = useExportJob({
 *     onSuccess: (data) => {
 *       setJobId(data.job_id);
 *     },
 *     onError: (error) => {
 *       console.error('Export failed:', error);
 *     }
 *   });
 *
 *   const handleExport = (format: 'CSV' | 'PDF') => {
 *     exportMutation.mutate({ session_id: sessionId, format });
 *   };
 *
 *   return (
 *     <button onClick={() => handleExport('CSV')}>
 *       Export CSV
 *     </button>
 *   );
 * }
 * ```
 */
export function useExportJob(
  options?: UseMutationOptions<ExportJobResponse, Error, ExportJobRequest>
) {
  return useMutation<ExportJobResponse, Error, ExportJobRequest>({
    mutationFn: async (request: ExportJobRequest) => {
      const response = await apiClient.post<ExportJobResponse>('/reports/export', request);
      return response.data;
    },
    ...options,
  });
}

/**
 * Poll export job status.
 *
 * Automatically polls every 2 seconds while job is PENDING or PROCESSING.
 * Stops polling when job is COMPLETED or FAILED.
 *
 * @param jobId - Job ID to poll (null/undefined to disable polling)
 * @param options - Additional React Query options
 * @returns React Query result with job status and download URL
 *
 * @example
 * ```tsx
 * function ExportStatus({ jobId }) {
 *   const { data, isLoading } = useExportJobStatus(jobId);
 *
 *   if (isLoading) return <div>Checking status...</div>;
 *   if (!data) return null;
 *
 *   if (data.status === 'COMPLETED') {
 *     return <a href={data.download_url}>Download</a>;
 *   }
 *
 *   if (data.status === 'FAILED') {
 *     return <div>Export failed: {data.error_message}</div>;
 *   }
 *
 *   return <div>Processing...</div>;
 * }
 * ```
 */
export function useExportJobStatus(
  jobId: string | null,
  options?: Omit<UseQueryOptions<JobStatusResponse, Error>, 'queryKey' | 'queryFn'>
) {
  return useQuery<JobStatusResponse, Error>({
    queryKey: reportingQueryKeys.exportJobs.status(jobId || ''),
    queryFn: async () => {
      if (!jobId) {
        throw new Error('Job ID is required');
      }

      const response = await apiClient.get<JobStatusResponse>(`/jobs/${jobId}`);
      return response.data;
    },
    enabled: !!jobId,
    refetchInterval: (query) => {
      // Poll every 2 seconds while job is pending or processing
      const data = query?.state?.data;
      if (data?.status === 'PENDING' || data?.status === 'PROCESSING') {
        return 2000;
      }
      // Stop polling when completed or failed
      return false;
    },
    ...options,
  });
}
