/**
 * SessionSummaryCard - Summary statistics card for session reports.
 *
 * Displays high-level session metrics suitable for all tiers (including Free).
 * Shows story count, consensus rate, average vote, and participants list.
 */

import type { DetailedSessionReportDTO } from '@/types/reporting';

interface SessionSummaryCardProps {
  session: DetailedSessionReportDTO;
}

/**
 * Format consensus rate as percentage.
 */
function formatConsensusRate(rate: number): string {
  return `${(rate * 100).toFixed(0)}%`;
}

/**
 * Format average vote to 1 decimal place.
 */
function formatAverageVote(avg: number): string {
  return avg.toFixed(1);
}

export default function SessionSummaryCard({ session }: SessionSummaryCardProps) {
  return (
    <section className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
      <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-6">
        Session Summary
      </h2>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6 mb-6">
        {/* Total Stories */}
        <div className="flex flex-col">
          <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">
            Total Stories
          </dt>
          <dd className="text-2xl font-bold text-gray-900 dark:text-white">
            {session.total_stories}
          </dd>
        </div>

        {/* Consensus Rate */}
        <div className="flex flex-col">
          <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">
            Consensus Rate
          </dt>
          <dd className="text-2xl font-bold text-green-600 dark:text-green-400">
            {formatConsensusRate(session.consensus_rate)}
          </dd>
        </div>

        {/* Average Vote */}
        <div className="flex flex-col">
          <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">
            Average Vote
          </dt>
          <dd className="text-2xl font-bold text-blue-600 dark:text-blue-400">
            {formatAverageVote(session.average_vote)}
          </dd>
        </div>

        {/* Participant Count */}
        <div className="flex flex-col">
          <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">
            Participants
          </dt>
          <dd className="text-2xl font-bold text-gray-900 dark:text-white">
            {session.participant_count}
          </dd>
        </div>
      </div>

      {/* Participants List */}
      <div>
        <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
          Participant List
        </h3>
        <div className="flex flex-wrap gap-2">
          {session.participants.map((participant, index) => (
            <span
              key={index}
              className="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200"
            >
              {participant}
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}
