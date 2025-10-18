/**
 * Participant list component - displays all room participants with their vote status.
 */

import React, { useMemo } from 'react';
import { useRoomStore } from '@/stores/roomStore';
import { useAuthStore } from '@/stores/authStore';
import type { Participant } from '@/types/websocket';

// Icons using simple SVG (no external dependencies needed)
const CheckCircleIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="currentColor" viewBox="0 0 20 20">
    <path
      fillRule="evenodd"
      d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
      clipRule="evenodd"
    />
  </svg>
);

const ClockIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="currentColor" viewBox="0 0 20 20">
    <path
      fillRule="evenodd"
      d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z"
      clipRule="evenodd"
    />
  </svg>
);

const CrownIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="currentColor" viewBox="0 0 20 20">
    <path d="M10 2a1 1 0 011 1v5.586l1.707-1.707a1 1 0 111.414 1.414L10.414 12 14.121 15.707a1 1 0 01-1.414 1.414L10 14.414l-2.707 2.707a1 1 0 01-1.414-1.414L9.586 12 5.879 8.293a1 1 0 011.414-1.414L9 8.586V3a1 1 0 011-1z" />
  </svg>
);

const EyeIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="currentColor" viewBox="0 0 20 20">
    <path d="M10 12a2 2 0 100-4 2 2 0 000 4z" />
    <path
      fillRule="evenodd"
      d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z"
      clipRule="evenodd"
    />
  </svg>
);

interface ParticipantItemProps {
  participant: Participant;
  isCurrentUser: boolean;
}

const ParticipantItem: React.FC<ParticipantItemProps> = ({ participant, isCurrentUser }) => {
  const getRoleBadge = () => {
    switch (participant.role) {
      case 'HOST':
        return (
          <div className="flex items-center space-x-1 text-xs font-semibold text-amber-600 dark:text-amber-400">
            <CrownIcon className="h-4 w-4" />
            <span>Host</span>
          </div>
        );
      case 'OBSERVER':
        return (
          <div className="flex items-center space-x-1 text-xs font-medium text-gray-500 dark:text-gray-400">
            <EyeIcon className="h-4 w-4" />
            <span>Observer</span>
          </div>
        );
      default:
        return null;
    }
  };

  const getVoteStatus = () => {
    // Observers don't vote
    if (participant.role === 'OBSERVER') {
      return null;
    }

    if (participant.hasVoted) {
      return (
        <div className="flex items-center space-x-1 text-green-600 dark:text-green-400">
          <CheckCircleIcon className="h-5 w-5" />
          <span className="text-xs font-medium">Voted</span>
        </div>
      );
    }

    return (
      <div className="flex items-center space-x-1 text-gray-400 dark:text-gray-500">
        <ClockIcon className="h-5 w-5" />
        <span className="text-xs font-medium">Waiting</span>
      </div>
    );
  };

  return (
    <div
      className={`
        flex items-center justify-between p-3 rounded-lg
        ${
          isCurrentUser
            ? 'bg-primary-50 dark:bg-primary-900/20 ring-2 ring-primary-300 dark:ring-primary-700'
            : 'bg-white dark:bg-gray-800'
        }
        shadow-sm hover:shadow-md transition-shadow duration-200
      `}
    >
      <div className="flex items-center space-x-3 flex-1 min-w-0">
        {/* Avatar */}
        <div className="flex-shrink-0">
          {participant.avatarUrl ? (
            <img
              src={participant.avatarUrl}
              alt={participant.displayName}
              className="h-10 w-10 rounded-full"
            />
          ) : (
            <div className="h-10 w-10 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center text-white font-bold text-sm">
              {participant.displayName.charAt(0).toUpperCase()}
            </div>
          )}
        </div>

        {/* Name and Role */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center space-x-2">
            <p className="text-sm font-medium text-gray-900 dark:text-white truncate">
              {participant.displayName}
              {isCurrentUser && (
                <span className="ml-2 text-xs text-primary-600 dark:text-primary-400 font-normal">
                  (You)
                </span>
              )}
            </p>
          </div>
          {getRoleBadge()}
        </div>

        {/* Vote Status */}
        <div className="flex-shrink-0">{getVoteStatus()}</div>
      </div>
    </div>
  );
};

/**
 * ParticipantList component - displays all participants with their vote status.
 * Automatically updates based on room store state.
 */
export const ParticipantList: React.FC = () => {
  const participants = useRoomStore((state) => state.getParticipantsArray());
  const user = useAuthStore((state) => state.user);

  // Sort participants: Host first, then current user, then others
  const sortedParticipants = useMemo(() => {
    return [...participants].sort((a, b) => {
      // Host always first
      if (a.role === 'HOST' && b.role !== 'HOST') return -1;
      if (b.role === 'HOST' && a.role !== 'HOST') return 1;

      // Current user next
      const aIsCurrentUser = a.participantId === user?.userId;
      const bIsCurrentUser = b.participantId === user?.userId;
      if (aIsCurrentUser && !bIsCurrentUser) return -1;
      if (bIsCurrentUser && !aIsCurrentUser) return 1;

      // Then by connection time (earlier first)
      return new Date(a.connectedAt).getTime() - new Date(b.connectedAt).getTime();
    });
  }, [participants, user?.userId]);

  const votedCount = participants.filter((p) => p.hasVoted && p.role !== 'OBSERVER').length;
  const voterCount = participants.filter((p) => p.role !== 'OBSERVER').length;

  return (
    <div className="bg-gray-50 dark:bg-gray-900 rounded-xl p-6 shadow-lg">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-xl font-semibold text-gray-900 dark:text-white">
          Participants
          <span className="ml-2 text-sm font-normal text-gray-500 dark:text-gray-400">
            ({participants.length})
          </span>
        </h3>
        {voterCount > 0 && (
          <div className="text-sm font-medium text-gray-600 dark:text-gray-300">
            <span className="text-primary-600 dark:text-primary-400">{votedCount}</span>
            <span className="text-gray-400 dark:text-gray-500"> / {voterCount}</span>
            <span className="ml-1 text-gray-500 dark:text-gray-400">voted</span>
          </div>
        )}
      </div>

      <div className="space-y-2 max-h-[600px] overflow-y-auto">
        {sortedParticipants.length === 0 ? (
          <div className="text-center py-8 text-gray-500 dark:text-gray-400">
            <p>No participants yet</p>
          </div>
        ) : (
          sortedParticipants.map((participant) => (
            <ParticipantItem
              key={participant.participantId}
              participant={participant}
              isCurrentUser={participant.participantId === user?.userId}
            />
          ))
        )}
      </div>
    </div>
  );
};
