/**
 * User profile card component for the dashboard.
 * Displays user avatar, display name, email, and subscription tier badge.
 */

import React from 'react';
import type { UserDTO } from '@/types/auth';
import { getTierBadgeClasses, formatTierName } from '@/utils/subscriptionUtils';

interface UserProfileCardProps {
  user: UserDTO;
}

export const UserProfileCard: React.FC<UserProfileCardProps> = ({ user }) => {
  const [imageError, setImageError] = React.useState(false);

  // Get initials from display name for fallback avatar
  const initials = user.displayName
    ?.split(' ')
    .map((n) => n.charAt(0))
    .join('')
    .toUpperCase()
    .substring(0, 2) || '??';

  return (
    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
      <div className="flex items-center space-x-4">
        {/* Avatar */}
        <div className="flex-shrink-0">
          {user.avatarUrl && !imageError ? (
            <img
              src={user.avatarUrl}
              alt={user.displayName}
              className="w-16 h-16 rounded-full object-cover border-2 border-primary-500"
              onError={() => setImageError(true)}
            />
          ) : (
            <div className="w-16 h-16 rounded-full bg-primary-600 dark:bg-primary-700 flex items-center justify-center border-2 border-primary-500">
              <span className="text-xl font-bold text-white">{initials}</span>
            </div>
          )}
        </div>

        {/* User info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h2 className="text-xl font-bold text-gray-900 dark:text-white truncate">
              {user.displayName}
            </h2>
            {/* Subscription tier badge */}
            <span
              className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${getTierBadgeClasses(
                user.subscriptionTier
              )}`}
            >
              {formatTierName(user.subscriptionTier)}
            </span>
          </div>
          <p className="text-sm text-gray-600 dark:text-gray-400 truncate">
            {user.email}
          </p>
        </div>
      </div>
    </div>
  );
};
