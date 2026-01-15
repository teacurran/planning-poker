/**
 * Card showing a list of the most recent estimation sessions.
 */

import React from 'react';
import { format } from 'date-fns';
import type { SessionSummaryDTO } from '@/types/reporting';

interface RecentSessionsCardProps {
  sessions: SessionSummaryDTO[];
  onSessionClick: (sessionId: string) => void;
  onViewAll: () => void;
}

function formatConsensusRate(rate: number): string {
  if (typeof rate !== 'number' || Number.isNaN(rate)) {
    return 'N/A';
  }
  return `${Math.round(rate * 100)}% consensus`;
}

export const RecentSessionsCard: React.FC<RecentSessionsCardProps> = ({
  sessions,
  onSessionClick,
  onViewAll,
}) => {
  return (
    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Recent Sessions</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Quick view of your latest estimation activity
          </p>
        </div>
        <button
          onClick={onViewAll}
          className="inline-flex items-center justify-center rounded-md border border-primary-600 px-4 py-2 text-sm font-semibold text-primary-700 transition-colors hover:bg-primary-50 dark:border-primary-400 dark:text-primary-200 dark:hover:bg-primary-900/20"
        >
          View All Sessions
        </button>
      </div>

      {sessions.length === 0 ? (
        <div className="text-center py-6">
          <svg
            className="w-12 h-12 text-gray-400 dark:text-gray-600 mx-auto mb-3"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.8}
              d="M8 7V3m8 4V3m-9 8h10m-9 4h4m6-9h1a2 2 0 012 2v9a2 2 0 01-2 2H6a2 2 0 01-2-2V8a2 2 0 012-2h1"
            />
          </svg>
          <p className="text-gray-700 dark:text-gray-300 font-medium">No sessions yet</p>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Run your first estimation session to see it appear here.
          </p>
        </div>
      ) : (
        <ul className="divide-y divide-gray-200 dark:divide-gray-700">
          {sessions.map((session) => (
            <li key={session.session_id} className="py-4">
              <button
                onClick={() => onSessionClick(session.session_id)}
                className="w-full text-left"
                aria-label={`Open session ${session.room_title}`}
              >
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <p className="text-base font-semibold text-gray-900 dark:text-white">
                      {session.room_title}
                    </p>
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                      {format(new Date(session.started_at), 'MMM d, yyyy • h:mm a')}
                    </p>
                  </div>
                  <div className="text-sm text-gray-500 dark:text-gray-400 text-left sm:text-right">
                    <p>{formatConsensusRate(session.consensus_rate)}</p>
                    <p>
                      {session.total_rounds} rounds • {session.participant_count} participants
                    </p>
                  </div>
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};
