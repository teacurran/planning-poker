/**
 * React Query hooks for organization API data fetching and mutations.
 *
 * This module provides:
 * - useOrganization(orgId): Fetch organization details by ID
 * - useAuditLogs(orgId, filters): Fetch paginated audit logs with filters
 * - useUpdateSsoConfig(): Mutation hook for updating SSO configuration
 * - useInviteMember(): Mutation hook for inviting organization members
 * - useRemoveMember(): Mutation hook for removing organization members
 *
 * All hooks automatically handle:
 * - Loading states
 * - Error states
 * - Data caching and invalidation
 * - Authentication (via apiClient interceptors)
 */

import { useQuery, useMutation, useQueryClient, UseQueryOptions, UseMutationOptions } from '@tanstack/react-query';
import { apiClient, getErrorMessage } from './api';
import type {
  OrganizationDTO,
  AuditLogListResponse,
  AuditLogFilters,
  SsoConfigRequest,
  InviteMemberRequest,
  OrgMemberDTO,
} from '@/types/organization';

// ============================================
// QUERY KEY FACTORIES
// ============================================

/**
 * Centralized query key factory for organization-related queries.
 */
export const organizationQueryKeys = {
  all: ['organizations'] as const,
  detail: (orgId: string) => ['organizations', orgId] as const,
  members: (orgId: string) => ['organizations', orgId, 'members'] as const,
  auditLogs: (orgId: string, filters?: AuditLogFilters) =>
    ['organizations', orgId, 'audit-logs', filters] as const,
};

// ============================================
// ORGANIZATION HOOKS
// ============================================

/**
 * Fetch organization details by organization ID.
 *
 * @param orgId - The organization ID to fetch
 * @param options - Additional React Query options
 * @returns React Query result with organization data, loading state, and error
 *
 * @example
 * ```tsx
 * function OrgSettings({ orgId }: { orgId: string }) {
 *   const { data: org, isLoading, error } = useOrganization(orgId);
 *
 *   if (isLoading) return <div>Loading...</div>;
 *   if (error) return <div>Error: {error.message}</div>;
 *
 *   return <div>{org.name}</div>;
 * }
 * ```
 */
export function useOrganization(
  orgId: string,
  options?: Omit<UseQueryOptions<OrganizationDTO, Error>, 'queryKey' | 'queryFn'>
) {
  return useQuery<OrganizationDTO, Error>({
    queryKey: organizationQueryKeys.detail(orgId),
    queryFn: async () => {
      const response = await apiClient.get<OrganizationDTO>(`/organizations/${orgId}`);
      return response.data;
    },
    enabled: !!orgId,
    staleTime: 5 * 60 * 1000, // 5 minutes
    ...options,
  });
}

/**
 * Fetch paginated audit logs for an organization with optional filters.
 *
 * @param orgId - The organization ID
 * @param filters - Optional filters (date range, action type, pagination)
 * @param options - Additional React Query options
 * @returns React Query result with audit logs, pagination info, loading state, and error
 *
 * @example
 * ```tsx
 * function AuditLogs({ orgId }: { orgId: string }) {
 *   const [page, setPage] = useState(0);
 *   const { data, isLoading } = useAuditLogs(orgId, { page, size: 20 });
 *
 *   return (
 *     <div>
 *       {data?.logs.map(log => (
 *         <div key={log.logId}>{log.action}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useAuditLogs(
  orgId: string,
  filters?: AuditLogFilters,
  options?: Omit<UseQueryOptions<AuditLogListResponse, Error>, 'queryKey' | 'queryFn'>
) {
  return useQuery<AuditLogListResponse, Error>({
    queryKey: organizationQueryKeys.auditLogs(orgId, filters),
    queryFn: async () => {
      const params: Record<string, string | number> = {};

      if (filters?.from) params.from = filters.from;
      if (filters?.to) params.to = filters.to;
      if (filters?.action) params.action = filters.action;
      if (filters?.page !== undefined) params.page = filters.page;
      if (filters?.size !== undefined) params.size = filters.size;

      const response = await apiClient.get<AuditLogListResponse>(
        `/organizations/${orgId}/audit-logs`,
        { params }
      );
      return response.data;
    },
    enabled: !!orgId,
    staleTime: 1 * 60 * 1000, // 1 minute (audit logs should be relatively fresh)
    ...options,
  });
}

// ============================================
// MUTATION HOOKS
// ============================================

/**
 * Update SSO configuration for an organization.
 *
 * This mutation automatically invalidates the organization cache on success,
 * triggering a refetch of the organization data.
 *
 * @param orgId - The organization ID
 * @param options - Additional React Query mutation options
 * @returns React Query mutation result with mutate function, loading state, and error
 *
 * @example
 * ```tsx
 * function SsoConfigForm({ orgId }: { orgId: string }) {
 *   const updateSso = useUpdateSsoConfig(orgId);
 *
 *   const handleSubmit = (config: SsoConfigRequest) => {
 *     updateSso.mutate(config, {
 *       onSuccess: () => {
 *         alert('SSO configuration updated successfully');
 *       },
 *       onError: (error) => {
 *         alert(`Failed to update SSO: ${error.message}`);
 *       },
 *     });
 *   };
 *
 *   return <button onClick={() => handleSubmit(config)}>Save SSO Config</button>;
 * }
 * ```
 */
export function useUpdateSsoConfig(
  orgId: string,
  options?: Omit<UseMutationOptions<OrganizationDTO, Error, SsoConfigRequest, unknown>, 'mutationFn'>
) {
  const queryClient = useQueryClient();

  return useMutation<OrganizationDTO, Error, SsoConfigRequest>({
    mutationFn: async (ssoConfig: SsoConfigRequest) => {
      const response = await apiClient.put<OrganizationDTO>(
        `/organizations/${orgId}/sso`,
        ssoConfig
      );
      return response.data;
    },
    onSuccess: async () => {
      // Invalidate organization detail to trigger refetch
      await queryClient.invalidateQueries({
        queryKey: organizationQueryKeys.detail(orgId)
      });
    },
    onError: (error) => {
      console.error('Failed to update SSO configuration:', getErrorMessage(error));
    },
    ...options,
  });
}

/**
 * Invite a member to an organization.
 *
 * This mutation automatically invalidates the organization cache on success
 * to update the member count.
 *
 * @param orgId - The organization ID
 * @param options - Additional React Query mutation options
 * @returns React Query mutation result with mutate function, loading state, and error
 *
 * @example
 * ```tsx
 * function InviteMemberButton({ orgId }: { orgId: string }) {
 *   const inviteMember = useInviteMember(orgId);
 *
 *   const handleInvite = () => {
 *     inviteMember.mutate(
 *       { email: 'user@example.com', role: 'MEMBER' },
 *       {
 *         onSuccess: (member) => {
 *           alert(`Invited ${member.email} successfully`);
 *         },
 *       }
 *     );
 *   };
 *
 *   return <button onClick={handleInvite}>Invite Member</button>;
 * }
 * ```
 */
export function useInviteMember(
  orgId: string,
  options?: Omit<UseMutationOptions<OrgMemberDTO, Error, InviteMemberRequest, unknown>, 'mutationFn'>
) {
  const queryClient = useQueryClient();

  return useMutation<OrgMemberDTO, Error, InviteMemberRequest>({
    mutationFn: async (invitation: InviteMemberRequest) => {
      const response = await apiClient.post<OrgMemberDTO>(
        `/organizations/${orgId}/members`,
        invitation
      );
      return response.data;
    },
    onSuccess: async () => {
      // Invalidate organization detail to update member count
      await queryClient.invalidateQueries({
        queryKey: organizationQueryKeys.detail(orgId)
      });

      // Also invalidate members list if it exists
      await queryClient.invalidateQueries({
        queryKey: organizationQueryKeys.members(orgId)
      });
    },
    onError: (error) => {
      console.error('Failed to invite member:', getErrorMessage(error));
    },
    ...options,
  });
}

/**
 * Remove a member from an organization.
 *
 * This mutation automatically invalidates the organization cache on success
 * to update the member count.
 *
 * @param orgId - The organization ID
 * @param options - Additional React Query mutation options
 * @returns React Query mutation result with mutate function, loading state, and error
 *
 * @example
 * ```tsx
 * function RemoveMemberButton({ orgId, userId }: { orgId: string, userId: string }) {
 *   const removeMember = useRemoveMember(orgId);
 *
 *   const handleRemove = () => {
 *     removeMember.mutate(userId, {
 *       onSuccess: () => {
 *         alert('Member removed successfully');
 *       },
 *       onError: (error) => {
 *         if (error.message.includes('last admin')) {
 *           alert('Cannot remove the last administrator');
 *         }
 *       },
 *     });
 *   };
 *
 *   return <button onClick={handleRemove}>Remove</button>;
 * }
 * ```
 */
export function useRemoveMember(
  orgId: string,
  options?: Omit<UseMutationOptions<void, Error, string, unknown>, 'mutationFn'>
) {
  const queryClient = useQueryClient();

  return useMutation<void, Error, string>({
    mutationFn: async (userId: string) => {
      await apiClient.delete(`/organizations/${orgId}/members/${userId}`);
    },
    onSuccess: async () => {
      // Invalidate organization detail to update member count
      await queryClient.invalidateQueries({
        queryKey: organizationQueryKeys.detail(orgId)
      });

      // Also invalidate members list if it exists
      await queryClient.invalidateQueries({
        queryKey: organizationQueryKeys.members(orgId)
      });
    },
    onError: (error) => {
      console.error('Failed to remove member:', getErrorMessage(error));
    },
    ...options,
  });
}
