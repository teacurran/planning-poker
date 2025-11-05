/**
 * Member table component for displaying organization members.
 * Shows member details with role badges and removal actions.
 */

import React from 'react';
import { UserCircleIcon, TrashIcon } from '@heroicons/react/24/outline';
import type { OrgMemberDTO, OrgRole } from '@/types/organization';
import { format } from 'date-fns';

interface MemberTableProps {
  members: OrgMemberDTO[];
  onRemoveMember: (userId: string, displayName: string, role: OrgRole) => void;
  isLoading?: boolean;
  currentUserId?: string;
}

/**
 * Get CSS classes for role badge styling.
 */
const getRoleBadgeClasses = (role: OrgRole): string => {
  switch (role) {
    case 'ADMIN':
      return 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200';
    case 'MEMBER':
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300';
    default:
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300';
  }
};

/**
 * Member table component.
 */
export const MemberTable: React.FC<MemberTableProps> = ({
  members,
  onRemoveMember,
  isLoading = false,
  currentUserId,
}) => {
  // Loading state
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-gray-300 border-t-blue-600 dark:border-gray-600 dark:border-t-blue-400" />
      </div>
    );
  }

  // Empty state
  if (members.length === 0) {
    return (
      <div className="py-8 text-center">
        <UserCircleIcon className="mx-auto h-12 w-12 text-gray-400 dark:text-gray-600" />
        <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">No members yet</p>
        <p className="mt-1 text-xs text-gray-500 dark:text-gray-500">
          Invite members to collaborate in your organization
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
        <thead className="bg-gray-50 dark:bg-gray-900">
          <tr>
            <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
              Member
            </th>
            <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
              Email
            </th>
            <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
              Role
            </th>
            <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
              Joined
            </th>
            <th className="px-6 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
              Actions
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200 bg-white dark:divide-gray-700 dark:bg-gray-800">
          {members.map((member) => {
            const isCurrentUser = currentUserId === member.userId;

            return (
              <tr key={member.userId} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                {/* Member name and avatar */}
                <td className="whitespace-nowrap px-6 py-4">
                  <div className="flex items-center">
                    {member.avatarUrl ? (
                      <img
                        src={member.avatarUrl}
                        alt={member.displayName}
                        className="h-10 w-10 rounded-full"
                      />
                    ) : (
                      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-gray-200 dark:bg-gray-700">
                        <UserCircleIcon className="h-6 w-6 text-gray-500 dark:text-gray-400" />
                      </div>
                    )}
                    <div className="ml-4">
                      <div className="text-sm font-medium text-gray-900 dark:text-white">
                        {member.displayName}
                        {isCurrentUser && (
                          <span className="ml-2 text-xs text-gray-500 dark:text-gray-400">(You)</span>
                        )}
                      </div>
                    </div>
                  </div>
                </td>

                {/* Email */}
                <td className="whitespace-nowrap px-6 py-4">
                  <div className="text-sm text-gray-900 dark:text-gray-300">{member.email}</div>
                </td>

                {/* Role */}
                <td className="whitespace-nowrap px-6 py-4">
                  <span
                    className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ${getRoleBadgeClasses(
                      member.role
                    )}`}
                  >
                    {member.role}
                  </span>
                </td>

                {/* Joined date */}
                <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500 dark:text-gray-400">
                  {format(new Date(member.joinedAt), 'MMM d, yyyy')}
                </td>

                {/* Actions */}
                <td className="whitespace-nowrap px-6 py-4 text-right text-sm">
                  <button
                    onClick={() => onRemoveMember(member.userId, member.displayName, member.role)}
                    className="text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300 disabled:cursor-not-allowed disabled:opacity-50"
                    title="Remove member"
                  >
                    <TrashIcon className="h-5 w-5" />
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
};
