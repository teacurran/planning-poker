/**
 * Session list table component.
 * Displays session metadata in a responsive table format with clickable rows.
 */

import React from 'react';
import { useNavigate } from 'react-router-dom';
import { format } from 'date-fns';
import type { SessionSummaryDTO } from '@/types/reporting';

interface SessionListTableProps {
  sessions: SessionSummaryDTO[];
  isLoading?: boolean;
}

/**
 * Format consensus rate as percentage.
 */
function formatConsensusRate(rate: number): string {
  return `${Math.round(rate * 100)}%`;
}

/**
 * Format duration between two timestamps.
 */
function formatDuration(startedAt: string, endedAt: string): string {
  try {
    const start = new Date(startedAt);
    const end = new Date(endedAt);
    const durationMs = end.getTime() - start.getTime();
    const minutes = Math.floor(durationMs / 60000);
    const hours = Math.floor(minutes / 60);

    if (hours > 0) {
      const remainingMinutes = minutes % 60;
      return `${hours}h ${remainingMinutes}m`;
    }

    return `${minutes}m`;
  } catch (error) {
    return 'N/A';
  }
}

export const SessionListTable: React.FC<SessionListTableProps> = ({ sessions, isLoading = false }) => {
  const navigate = useNavigate();

  // Handle session row click
  const handleSessionClick = (sessionId: string) => {
    navigate(`/reports/sessions/${sessionId}`);
  };

  // Empty state
  if (!isLoading && sessions.length === 0) {
    return (
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-12 text-center">
        <svg
          className="w-16 h-16 text-gray-400 dark:text-gray-600 mx-auto mb-4"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
          />
        </svg>
        <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
          No sessions found
        </h3>
        <p className="text-gray-600 dark:text-gray-400">
          No estimation sessions match your current filters. Try adjusting the date range or clearing filters.
        </p>
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md overflow-hidden">
      {/* Desktop table view */}
      <div className="hidden md:block overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          <thead className="bg-gray-50 dark:bg-gray-900">
            <tr>
              <th
                scope="col"
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider"
              >
                Date
              </th>
              <th
                scope="col"
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider"
              >
                Room
              </th>
              <th
                scope="col"
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider"
              >
                Duration
              </th>
              <th
                scope="col"
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider"
              >
                Rounds
              </th>
              <th
                scope="col"
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider"
              >
                Participants
              </th>
              <th
                scope="col"
                className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider"
              >
                Consensus
              </th>
            </tr>
          </thead>
          <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
            {sessions.map((session) => (
              <tr
                key={session.session_id}
                onClick={() => handleSessionClick(session.session_id)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    handleSessionClick(session.session_id);
                  }
                }}
                tabIndex={0}
                role="button"
                className="hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer transition-colors duration-150"
                aria-label={`View session ${session.room_title}`}
              >
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-white">
                  {format(new Date(session.started_at), 'MMM dd, yyyy')}
                  <div className="text-xs text-gray-500 dark:text-gray-400">
                    {format(new Date(session.started_at), 'h:mm a')}
                  </div>
                </td>
                <td className="px-6 py-4 text-sm text-gray-900 dark:text-white">
                  <div className="font-medium truncate max-w-xs" title={session.room_title}>
                    {session.room_title}
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                  {formatDuration(session.started_at, session.ended_at)}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                  {session.total_rounds}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                  {session.participant_count}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <span
                    className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                      session.consensus_rate >= 0.8
                        ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300'
                        : session.consensus_rate >= 0.5
                        ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300'
                        : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300'
                    }`}
                  >
                    {formatConsensusRate(session.consensus_rate)}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile card view */}
      <div className="md:hidden divide-y divide-gray-200 dark:divide-gray-700">
        {sessions.map((session) => (
          <div
            key={session.session_id}
            onClick={() => handleSessionClick(session.session_id)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                handleSessionClick(session.session_id);
              }
            }}
            tabIndex={0}
            role="button"
            className="p-4 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer transition-colors duration-150"
            aria-label={`View session ${session.room_title}`}
          >
            {/* Date and room title */}
            <div className="flex items-start justify-between mb-3">
              <div className="flex-1">
                <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-1 truncate">
                  {session.room_title}
                </h3>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {format(new Date(session.started_at), 'MMM dd, yyyy • h:mm a')}
                </p>
              </div>
              <span
                className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium flex-shrink-0 ml-2 ${
                  session.consensus_rate >= 0.8
                    ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300'
                    : session.consensus_rate >= 0.5
                    ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300'
                    : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300'
                }`}
              >
                {formatConsensusRate(session.consensus_rate)}
              </span>
            </div>

            {/* Metrics */}
            <div className="grid grid-cols-3 gap-4 text-sm">
              <div>
                <p className="text-gray-500 dark:text-gray-400 text-xs mb-1">Duration</p>
                <p className="text-gray-900 dark:text-white font-medium">
                  {formatDuration(session.started_at, session.ended_at)}
                </p>
              </div>
              <div>
                <p className="text-gray-500 dark:text-gray-400 text-xs mb-1">Rounds</p>
                <p className="text-gray-900 dark:text-white font-medium">{session.total_rounds}</p>
              </div>
              <div>
                <p className="text-gray-500 dark:text-gray-400 text-xs mb-1">Participants</p>
                <p className="text-gray-900 dark:text-white font-medium">{session.participant_count}</p>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
