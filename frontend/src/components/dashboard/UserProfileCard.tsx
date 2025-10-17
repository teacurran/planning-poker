/**
 * User profile card component for the dashboard.
 * Displays user avatar, display name, email, and subscription tier badge.
 */

import React from 'react';
import type { UserDTO, SubscriptionTier } from '@/types/auth';

interface UserProfileCardProps {
  user: UserDTO;
}

/**
 * Get color classes for subscription tier badge.
 */
function getTierBadgeClasses(tier: SubscriptionTier): string {
  switch (tier) {
    case 'FREE':
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300';
    case 'PRO':
      return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300';
    case 'PRO_PLUS':
      return 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-300';
    case 'ENTERPRISE':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300';
    default:
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300';
  }
}

/**
 * Format subscription tier for display.
 */
function formatTierName(tier: SubscriptionTier): string {
  switch (tier) {
    case 'FREE':
      return 'Free';
    case 'PRO':
      return 'Pro';
    case 'PRO_PLUS':
      return 'Pro Plus';
    case 'ENTERPRISE':
      return 'Enterprise';
    default:
      return tier;
  }
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
