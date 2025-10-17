/**
 * Room list card component for the dashboard.
 * Displays room title, privacy mode badge, last active timestamp, and open button.
 */

import React from 'react';
import { formatDistanceToNow } from 'date-fns';
import type { RoomDTO, PrivacyMode } from '@/types/room';

interface RoomListCardProps {
  room: RoomDTO;
  onClick: () => void;
}

/**
 * Get color classes for privacy mode badge.
 */
function getPrivacyBadgeClasses(privacyMode: PrivacyMode): string {
  switch (privacyMode) {
    case 'PUBLIC':
      return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300';
    case 'PRIVATE':
      return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300';
    default:
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300';
  }
}

/**
 * Format privacy mode for display.
 */
function formatPrivacyMode(privacyMode: PrivacyMode): string {
  return privacyMode.charAt(0) + privacyMode.slice(1).toLowerCase();
}

export const RoomListCard: React.FC<RoomListCardProps> = ({ room, onClick }) => {
  // Format last active timestamp
  const lastActiveText = React.useMemo(() => {
    try {
      return formatDistanceToNow(new Date(room.lastActiveAt), { addSuffix: true });
    } catch (error) {
      return 'Recently';
    }
  }, [room.lastActiveAt]);

  return (
    <div
      className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6 hover:shadow-lg transition-shadow duration-200 cursor-pointer border border-transparent hover:border-primary-500"
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onClick();
        }
      }}
      aria-label={`Open room: ${room.title}`}
    >
      {/* Room title and privacy badge */}
      <div className="flex items-start justify-between gap-2 mb-3">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white truncate flex-1">
          {room.title}
        </h3>
        <span
          className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium flex-shrink-0 ${getPrivacyBadgeClasses(
            room.privacyMode
          )}`}
        >
          {formatPrivacyMode(room.privacyMode)}
        </span>
      </div>

      {/* Last active timestamp */}
      <div className="flex items-center text-sm text-gray-600 dark:text-gray-400 mb-4">
        <svg
          className="w-4 h-4 mr-1.5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
        <span>Last active {lastActiveText}</span>
      </div>

      {/* Open room button */}
      <button
        className="w-full bg-primary-600 hover:bg-primary-700 text-white font-medium py-2 px-4 rounded transition-colors duration-200"
        onClick={(e) => {
          e.stopPropagation();
          onClick();
        }}
        aria-label={`Open room: ${room.title}`}
      >
        Open Room
      </button>
    </div>
  );
};
