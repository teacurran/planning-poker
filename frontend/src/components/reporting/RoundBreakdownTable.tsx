/**
 * RoundBreakdownTable - Detailed round-by-round breakdown for Pro tier.
 *
 * Displays each round's story title, individual votes, average, median,
 * and consensus indicator. Only visible to Pro/Enterprise users.
 */

import { CheckCircleIcon, XCircleIcon } from '@heroicons/react/24/solid';
import type { RoundDetailDTO } from '@/types/reporting';

interface RoundBreakdownTableProps {
  rounds: RoundDetailDTO[];
}

/**
 * Format votes array into comma-separated string.
 */
function formatVotes(votes: { participant_name: string; card_value: string }[]): string {
  return votes.map((v) => `${v.participant_name}: ${v.card_value}`).join(', ');
}

/**
 * Format numeric value to 1 decimal place.
 */
function formatNumber(value: number): string {
  return value.toFixed(1);
}

export default function RoundBreakdownTable({ rounds }: RoundBreakdownTableProps) {
  if (rounds.length === 0) {
    return (
      <section className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">
          Round Breakdown
        </h2>
        <p className="text-gray-500 dark:text-gray-400 text-center py-8">
          No rounds found for this session.
        </p>
      </section>
    );
  }

  return (
    <section className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
      <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">
        Round Breakdown
      </h2>

      {/* Mobile: Horizontal scroll wrapper */}
      <div className="overflow-x-auto -mx-6 px-6">
        <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          <thead className="bg-gray-50 dark:bg-gray-700">
            <tr>
              <th
                scope="col"
                className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider"
              >
                Round
              </th>
              <th
                scope="col"
                className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider"
              >
                Story Title
              </th>
              <th
                scope="col"
                className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider min-w-[300px]"
              >
                Individual Votes
              </th>
              <th
                scope="col"
                className="px-4 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider"
              >
                Average
              </th>
              <th
                scope="col"
                className="px-4 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider"
              >
                Median
              </th>
              <th
                scope="col"
                className="px-4 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider"
              >
                Consensus
              </th>
            </tr>
          </thead>
          <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
            {rounds.map((round) => (
              <tr
                key={round.round_number}
                className="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
              >
                <td className="px-4 py-4 whitespace-nowrap text-sm font-medium text-gray-900 dark:text-white">
                  {round.round_number}
                </td>
                <td className="px-4 py-4 text-sm text-gray-900 dark:text-white max-w-xs">
                  <div className="line-clamp-2" title={round.story_title}>
                    {round.story_title}
                  </div>
                </td>
                <td className="px-4 py-4 text-sm text-gray-600 dark:text-gray-300">
                  <div className="max-w-md overflow-hidden text-ellipsis" title={formatVotes(round.votes)}>
                    {formatVotes(round.votes)}
                  </div>
                </td>
                <td className="px-4 py-4 whitespace-nowrap text-center text-sm font-semibold text-blue-600 dark:text-blue-400">
                  {formatNumber(round.average)}
                </td>
                <td className="px-4 py-4 whitespace-nowrap text-center text-sm font-semibold text-blue-600 dark:text-blue-400">
                  {formatNumber(round.median)}
                </td>
                <td className="px-4 py-4 whitespace-nowrap text-center">
                  {round.consensus_reached ? (
                    <CheckCircleIcon
                      className="h-5 w-5 text-green-500 dark:text-green-400 inline-block"
                      aria-label="Consensus reached"
                    />
                  ) : (
                    <XCircleIcon
                      className="h-5 w-5 text-red-500 dark:text-red-400 inline-block"
                      aria-label="No consensus"
                    />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
