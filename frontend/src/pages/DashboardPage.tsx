/**
 * Dashboard page component.
 * Displays user profile, list of owned rooms, and quick actions.
 */

import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useUser, useRooms, useUserPreferences } from '@/services/apiHooks';
import { useAuthStore } from '@/stores/authStore';
import { UserProfileCard } from '@/components/dashboard/UserProfileCard';
import { RoomListCard } from '@/components/dashboard/RoomListCard';
import { CreateRoomButton } from '@/components/dashboard/CreateRoomButton';
import { ViewPreferencesButton } from '@/components/dashboard/ViewPreferencesButton';
import { UserPreferencesModal } from '@/components/dashboard/UserPreferencesModal';

const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const { user: authUser } = useAuthStore();
  const [isPreferencesOpen, setIsPreferencesOpen] = React.useState(false);

  const {
    data: userData,
    isLoading: userLoading,
    error: userError,
    refetch: refetchUser,
  } = useUser(authUser?.userId || '');

  const {
    data: roomsData,
    isLoading: roomsLoading,
    error: roomsError,
    refetch: refetchRooms,
  } = useRooms();

  const {
    data: preferencesData,
    isLoading: preferencesLoading,
    error: preferencesError,
    refetch: refetchPreferences,
  } = useUserPreferences(authUser?.userId || '', {
    enabled: isPreferencesOpen && !!authUser?.userId,
  });

  const isLoading = userLoading || roomsLoading;
  const error = userError || roomsError;

  const handleRoomClick = (roomId: string) => {
    navigate(`/room/${roomId}`);
  };

  const handleViewPreferences = () => {
    setIsPreferencesOpen(true);
  };

  const handleClosePreferences = () => {
    setIsPreferencesOpen(false);
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          <div className="mb-8">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-9 w-48 rounded mb-2"></div>
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-5 w-64 rounded"></div>
          </div>

          <div className="mb-8">
            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
              <div className="flex items-center space-x-4">
                <div className="animate-pulse bg-gray-300 dark:bg-gray-700 w-16 h-16 rounded-full"></div>
                <div className="flex-1">
                  <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-6 w-40 rounded mb-2"></div>
                  <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-4 w-56 rounded"></div>
                </div>
              </div>
            </div>
          </div>

          <div className="mb-8 grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-14 w-full rounded-lg"></div>
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-14 w-full rounded-lg"></div>
          </div>

          <div>
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-8 w-32 rounded mb-4"></div>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {[1, 2, 3].map((i) => (
                <div key={i} className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                  <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-6 w-3/4 rounded mb-3"></div>
                  <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-4 w-1/2 rounded mb-4"></div>
                  <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-10 w-full rounded"></div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-6 max-w-2xl mx-auto">
            <div className="flex items-start">
              <svg
                className="w-6 h-6 text-red-600 dark:text-red-400 mr-3 flex-shrink-0 mt-0.5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
              <div className="flex-1">
                <h3 className="text-lg font-semibold text-red-800 dark:text-red-200 mb-1">
                  Failed to load dashboard
                </h3>
                <p className="text-sm text-red-700 dark:text-red-300 mb-4">
                  {error.message || 'An unexpected error occurred'}
                </p>
                <button
                  onClick={() => {
                    refetchUser();
                    refetchRooms();
                  }}
                  className="bg-red-600 hover:bg-red-700 text-white font-medium py-2 px-4 rounded transition-colors duration-200"
                >
                  Retry
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const rooms = roomsData?.rooms || [];
  const hasRooms = rooms.length > 0;

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="container mx-auto px-4 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
            Dashboard
          </h1>
          <p className="text-gray-600 dark:text-gray-300">
            Manage your rooms and estimation sessions
          </p>
        </div>

        {userData && (
          <div className="mb-8">
            <UserProfileCard user={userData} />
          </div>
        )}

        <div className="mb-8 grid grid-cols-1 md:grid-cols-2 gap-4">
          <CreateRoomButton />
          <ViewPreferencesButton onClick={handleViewPreferences} />
        </div>

        <div>
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-4">
            Your Rooms
          </h2>

          {hasRooms ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {rooms.map((room) => (
                <RoomListCard
                  key={room.roomId}
                  room={room}
                  onClick={() => handleRoomClick(room.roomId)}
                />
              ))}
            </div>
          ) : (
            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-12 text-center">
              <svg
                className="w-16 h-16 text-gray-400 dark:text-gray-600 mx-auto mb-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
                />
              </svg>
              <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
                No rooms yet
              </h3>
              <p className="text-gray-600 dark:text-gray-400 mb-6">
                Create your first room to start planning poker sessions with your team
              </p>
            </div>
          )}
        </div>

        {roomsData && roomsData.totalPages > 1 && (
          <div className="mt-6 text-center text-sm text-gray-600 dark:text-gray-400">
            Showing page {roomsData.page + 1} of {roomsData.totalPages} ({roomsData.totalElements} total rooms)
          </div>
        )}
      </div>

      <UserPreferencesModal
        isOpen={isPreferencesOpen}
        onClose={handleClosePreferences}
        preferences={preferencesData}
        isLoading={preferencesLoading}
        error={preferencesError}
        onRetry={() => refetchPreferences()}
      />
    </div>
  );
};

export default DashboardPage;
