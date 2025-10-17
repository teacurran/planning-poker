/**
 * React Query hooks for API data fetching and mutations.
 *
 * This module provides:
 * - useUser(userId): Fetch user profile by ID
 * - useRooms(): Fetch current user's owned rooms (paginated)
 * - useRoomById(roomId): Fetch room details by ID
 * - useCreateRoom(): Mutation hook for creating new rooms
 *
 * All hooks automatically handle:
 * - Loading states
 * - Error states
 * - Data caching and invalidation
 * - Authentication (via apiClient interceptors)
 */

import { useQuery, useMutation, useQueryClient, UseQueryOptions, UseMutationOptions } from '@tanstack/react-query';
import { apiClient, getErrorMessage } from './api';
import { useAuthStore } from '@/stores/authStore';
import type { UserDTO } from '@/types/auth';
import type { RoomDTO, RoomListResponse, CreateRoomRequest } from '@/types/room';

// ============================================
// QUERY KEY FACTORIES
// ============================================

/**
 * Centralized query key factory to ensure consistency.
 * Query keys are used by React Query for caching and invalidation.
 */
export const queryKeys = {
  users: {
    all: ['users'] as const,
    detail: (userId: string) => ['users', userId] as const,
  },
  rooms: {
    all: ['rooms'] as const,
    byUser: (userId: string, page = 0, size = 20) => ['rooms', 'user', userId, page, size] as const,
    detail: (roomId: string) => ['rooms', roomId] as const,
  },
};

// ============================================
// USER HOOKS
// ============================================

/**
 * Fetch user profile by user ID.
 *
 * @param userId - The user ID to fetch
 * @param options - Additional React Query options
 * @returns React Query result with user data, loading state, and error
 *
 * @example
 * ```tsx
 * function UserProfile({ userId }: { userId: string }) {
 *   const { data: user, isLoading, error } = useUser(userId);
 *
 *   if (isLoading) return <div>Loading...</div>;
 *   if (error) return <div>Error: {error.message}</div>;
 *
 *   return <div>{user.displayName}</div>;
 * }
 * ```
 */
export function useUser(
  userId: string,
  options?: Omit<UseQueryOptions<UserDTO, Error>, 'queryKey' | 'queryFn'>
) {
  return useQuery<UserDTO, Error>({
    queryKey: queryKeys.users.detail(userId),
    queryFn: async () => {
      const response = await apiClient.get<UserDTO>(`/users/${userId}`);
      return response.data;
    },
    enabled: !!userId,
    staleTime: 5 * 60 * 1000, // 5 minutes
    ...options,
  });
}

// ============================================
// ROOM HOOKS
// ============================================

/**
 * Fetch the current user's owned rooms (paginated).
 *
 * This hook automatically uses the current authenticated user's ID.
 * If the user is not authenticated, the query is disabled and returns no data.
 *
 * @param page - Page number (0-indexed, default: 0)
 * @param size - Page size (default: 20)
 * @param options - Additional React Query options
 * @returns React Query result with rooms list, pagination info, loading state, and error
 *
 * @example
 * ```tsx
 * function MyRooms() {
 *   const { data, isLoading, error } = useRooms();
 *
 *   if (isLoading) return <div>Loading...</div>;
 *   if (error) return <div>Error: {error.message}</div>;
 *   if (!data) return <div>No rooms found</div>;
 *
 *   return (
 *     <div>
 *       {data.rooms.map(room => (
 *         <div key={room.roomId}>{room.title}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useRooms(
  page = 0,
  size = 20,
  options?: Omit<UseQueryOptions<RoomListResponse, Error>, 'queryKey' | 'queryFn'>
) {
  const { user } = useAuthStore();

  return useQuery<RoomListResponse, Error>({
    queryKey: queryKeys.rooms.byUser(user?.userId || '', page, size),
    queryFn: async () => {
      if (!user?.userId) {
        throw new Error('User not authenticated');
      }

      const response = await apiClient.get<RoomListResponse>(`/users/${user.userId}/rooms`, {
        params: { page, size },
      });
      return response.data;
    },
    enabled: !!user?.userId,
    staleTime: 2 * 60 * 1000, // 2 minutes (rooms change frequently)
    ...options,
  });
}

/**
 * Fetch room details by room ID.
 *
 * @param roomId - The room ID to fetch
 * @param options - Additional React Query options
 * @returns React Query result with room data, loading state, and error
 *
 * @example
 * ```tsx
 * function RoomDetails({ roomId }: { roomId: string }) {
 *   const { data: room, isLoading, error } = useRoomById(roomId);
 *
 *   if (isLoading) return <div>Loading...</div>;
 *   if (error) return <div>Error: {error.message}</div>;
 *
 *   return <div>{room.title}</div>;
 * }
 * ```
 */
export function useRoomById(
  roomId: string,
  options?: Omit<UseQueryOptions<RoomDTO, Error>, 'queryKey' | 'queryFn'>
) {
  return useQuery<RoomDTO, Error>({
    queryKey: queryKeys.rooms.detail(roomId),
    queryFn: async () => {
      const response = await apiClient.get<RoomDTO>(`/rooms/${roomId}`);
      return response.data;
    },
    enabled: !!roomId,
    staleTime: 1 * 60 * 1000, // 1 minute (room config may change during active sessions)
    ...options,
  });
}

// ============================================
// MUTATION HOOKS
// ============================================

/**
 * Create a new room.
 *
 * This mutation automatically invalidates the rooms list cache on success,
 * triggering a refetch of the user's rooms.
 *
 * @param options - Additional React Query mutation options
 * @returns React Query mutation result with mutate function, loading state, and error
 *
 * @example
 * ```tsx
 * function CreateRoomButton() {
 *   const createRoom = useCreateRoom();
 *
 *   const handleCreate = () => {
 *     createRoom.mutate(
 *       { title: 'Sprint Planning', privacyMode: 'PRIVATE' },
 *       {
 *         onSuccess: (room) => {
 *           console.log('Room created:', room.roomId);
 *           navigate(`/rooms/${room.roomId}`);
 *         },
 *         onError: (error) => {
 *           alert(`Failed to create room: ${error.message}`);
 *         },
 *       }
 *     );
 *   };
 *
 *   return (
 *     <button onClick={handleCreate} disabled={createRoom.isPending}>
 *       {createRoom.isPending ? 'Creating...' : 'Create Room'}
 *     </button>
 *   );
 * }
 * ```
 */
export function useCreateRoom(
  options?: Omit<UseMutationOptions<RoomDTO, Error, CreateRoomRequest, unknown>, 'mutationFn'>
) {
  const queryClient = useQueryClient();
  const { user } = useAuthStore();

  return useMutation<RoomDTO, Error, CreateRoomRequest>({
    mutationFn: async (roomData: CreateRoomRequest) => {
      const response = await apiClient.post<RoomDTO>('/rooms', roomData);
      return response.data;
    },
    onSuccess: async (data) => {
      // Invalidate rooms list to trigger refetch
      if (user?.userId) {
        await queryClient.invalidateQueries({ queryKey: queryKeys.rooms.byUser(user.userId) });
      }

      // Also invalidate the general rooms query key
      await queryClient.invalidateQueries({ queryKey: queryKeys.rooms.all });

      // Optionally set the new room data in cache
      queryClient.setQueryData(queryKeys.rooms.detail(data.roomId), data);
    },
    onError: (error) => {
      // Log error for debugging
      console.error('Failed to create room:', getErrorMessage(error));
    },
    ...options,
  });
}

/**
 * Update room configuration (host only).
 *
 * This mutation automatically invalidates the room detail cache on success.
 *
 * @param roomId - The room ID to update
 * @param options - Additional React Query mutation options
 * @returns React Query mutation result
 */
export function useUpdateRoom(
  roomId: string,
  options?: Omit<UseMutationOptions<RoomDTO, Error, Partial<CreateRoomRequest>, unknown>, 'mutationFn'>
) {
  const queryClient = useQueryClient();

  return useMutation<RoomDTO, Error, Partial<CreateRoomRequest>>({
    mutationFn: async (updates: Partial<CreateRoomRequest>) => {
      const response = await apiClient.put<RoomDTO>(`/rooms/${roomId}/config`, updates);
      return response.data;
    },
    onSuccess: async () => {
      // Invalidate room detail to trigger refetch
      await queryClient.invalidateQueries({ queryKey: queryKeys.rooms.detail(roomId) });
    },
    ...options,
  });
}

/**
 * Delete a room (soft delete, owner only).
 *
 * This mutation automatically invalidates the rooms list cache on success.
 *
 * @param options - Additional React Query mutation options
 * @returns React Query mutation result
 */
export function useDeleteRoom(
  options?: Omit<UseMutationOptions<void, Error, string, unknown>, 'mutationFn'>
) {
  const queryClient = useQueryClient();
  const { user } = useAuthStore();

  return useMutation<void, Error, string>({
    mutationFn: async (roomId: string) => {
      await apiClient.delete(`/rooms/${roomId}`);
    },
    onSuccess: async (_data, roomId) => {
      // Invalidate rooms list to trigger refetch
      if (user?.userId) {
        await queryClient.invalidateQueries({ queryKey: queryKeys.rooms.byUser(user.userId) });
      }

      // Remove deleted room from cache
      queryClient.removeQueries({ queryKey: queryKeys.rooms.detail(roomId) });
    },
    ...options,
  });
}
