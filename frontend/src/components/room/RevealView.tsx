/**
 * Reveal view component - displays revealed votes with statistics and animations.
 */

import React, { useMemo } from 'react';
import { useRoomStore } from '@/stores/roomStore';
import { VotingCard } from './VotingCard';

interface StatisticCardProps {
  label: string;
  value: string | number | null;
  description?: string;
}

const StatisticCard: React.FC<StatisticCardProps> = ({ label, value, description }) => (
  <div className="bg-white dark:bg-gray-800 rounded-lg p-4 shadow-md">
    <p className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">{label}</p>
    <p className="text-3xl font-bold text-gray-900 dark:text-white">
      {value !== null && value !== undefined ? value : '—'}
    </p>
    {description && (
      <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">{description}</p>
    )}
  </div>
);

interface ConsensusIndicatorProps {
  consensusReached: boolean;
}

const ConsensusIndicator: React.FC<ConsensusIndicatorProps> = ({ consensusReached }) => {
  if (consensusReached) {
    return (
      <div className="flex items-center justify-center space-x-2 bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300 px-4 py-3 rounded-lg">
        <svg className="h-6 w-6" fill="currentColor" viewBox="0 0 20 20">
          <path
            fillRule="evenodd"
            d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
            clipRule="evenodd"
          />
        </svg>
        <span className="font-semibold text-lg">Consensus Reached!</span>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center space-x-2 bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300 px-4 py-3 rounded-lg">
      <svg className="h-6 w-6" fill="currentColor" viewBox="0 0 20 20">
        <path
          fillRule="evenodd"
          d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
          clipRule="evenodd"
        />
      </svg>
      <span className="font-semibold text-lg">Votes Vary - Discuss!</span>
    </div>
  );
};

interface DistributionBarProps {
  value: string;
  count: number;
  maxCount: number;
}

const DistributionBar: React.FC<DistributionBarProps> = ({ value, count, maxCount }) => {
  const percentage = (count / maxCount) * 100;

  return (
    <div className="flex items-center space-x-3">
      <div className="w-12 text-center font-bold text-gray-700 dark:text-gray-300">{value}</div>
      <div className="flex-1 bg-gray-200 dark:bg-gray-700 rounded-full h-6 overflow-hidden">
        <div
          className="bg-gradient-to-r from-primary-500 to-primary-600 h-full flex items-center justify-end px-2 transition-all duration-500 ease-out"
          style={{ width: `${percentage}%` }}
        >
          {count > 0 && (
            <span className="text-white text-xs font-semibold">{count}</span>
          )}
        </div>
      </div>
    </div>
  );
};

/**
 * RevealView component - displays revealed votes with animated cards and statistics.
 */
export const RevealView: React.FC = () => {
  const revealedVotes = useRoomStore((state) => state.revealedVotes);
  const statistics = useRoomStore((state) => state.statistics);

  // Sort distribution by count (descending) for better visualization
  const sortedDistribution = useMemo(() => {
    if (!statistics?.distribution) return [];

    return Object.entries(statistics.distribution)
      .sort(([, a], [, b]) => b - a)
      .map(([value, count]) => ({ value, count }));
  }, [statistics?.distribution]);

  const maxCount = Math.max(...sortedDistribution.map((d) => d.count), 1);

  if (!revealedVotes || !statistics) {
    return null;
  }

  // Format average to 1 decimal place
  const formattedAverage = statistics.average !== null
    ? statistics.average.toFixed(1)
    : null;

  return (
    <div className="bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-900 dark:to-gray-800 rounded-xl p-6 shadow-xl">
      {/* Header */}
      <div className="mb-6">
        <h3 className="text-2xl font-bold text-gray-900 dark:text-white mb-4">
          Round Results
        </h3>
        <ConsensusIndicator consensusReached={statistics.consensusReached} />
      </div>

      {/* Statistics Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <StatisticCard
          label="Average"
          value={formattedAverage}
          description="Mean of numeric votes"
        />
        <StatisticCard
          label="Median"
          value={statistics.median}
          description="Middle value"
        />
        <StatisticCard
          label="Mode"
          value={statistics.mode}
          description="Most common"
        />
        <StatisticCard
          label="Total Votes"
          value={statistics.totalVotes}
          description={`${statistics.totalVotes} participant${statistics.totalVotes !== 1 ? 's' : ''}`}
        />
      </div>

      {/* Vote Distribution */}
      {sortedDistribution.length > 0 && (
        <div className="mb-6 bg-white dark:bg-gray-800 rounded-lg p-4 shadow-md">
          <h4 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Vote Distribution
          </h4>
          <div className="space-y-2">
            {sortedDistribution.map(({ value, count }) => (
              <DistributionBar
                key={value}
                value={value}
                count={count}
                maxCount={maxCount}
              />
            ))}
          </div>
        </div>
      )}

      {/* Revealed Cards */}
      <div>
        <h4 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Individual Votes
        </h4>
        <div className="flex flex-wrap gap-4 justify-center">
          {revealedVotes.map((vote, index) => (
            <div
              key={vote.participantId}
              style={{
                animationDelay: `${index * 0.1}s`,
              }}
            >
              <VotingCard
                value={vote.cardValue}
                revealed={true}
                displayName={vote.displayName}
              />
            </div>
          ))}
        </div>
      </div>

      {/* Empty State */}
      {revealedVotes.length === 0 && (
        <div className="text-center py-8 text-gray-500 dark:text-gray-400">
          <p>No votes were cast this round</p>
        </div>
      )}
    </div>
  );
};
