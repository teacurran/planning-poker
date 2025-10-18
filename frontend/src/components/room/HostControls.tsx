/**
 * Host controls component - buttons for starting, revealing, and resetting rounds.
 * Only visible to users with HOST role.
 */

import React from 'react';

interface HostControlsProps {
  /** Callback to start a new round */
  onStartRound: () => void;

  /** Callback to reveal votes */
  onReveal: () => void;

  /** Callback to reset the current round */
  onReset: () => void;

  /** Whether a round is currently in progress */
  roundInProgress: boolean;

  /** Whether the current round has been revealed */
  roundRevealed: boolean;

  /** Whether any votes have been cast */
  hasVotes: boolean;

  /** Whether controls are disabled (e.g., not connected) */
  disabled?: boolean;
}

const PlayIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="currentColor" viewBox="0 0 20 20">
    <path
      fillRule="evenodd"
      d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z"
      clipRule="evenodd"
    />
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

const RefreshIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="currentColor" viewBox="0 0 20 20">
    <path
      fillRule="evenodd"
      d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z"
      clipRule="evenodd"
    />
  </svg>
);

/**
 * HostControls component - provides round management controls for the host.
 */
export const HostControls: React.FC<HostControlsProps> = ({
  onStartRound,
  onReveal,
  onReset,
  roundInProgress,
  roundRevealed,
  hasVotes,
  disabled = false,
}) => {
  // Determine which buttons to show based on round state
  const showStartButton = !roundInProgress;
  const showRevealButton = roundInProgress && !roundRevealed && hasVotes;
  const showResetButton = roundInProgress;

  return (
    <div className="bg-amber-50 dark:bg-amber-900/20 rounded-xl p-6 shadow-lg border-2 border-amber-200 dark:border-amber-800">
      <div className="flex items-center space-x-2 mb-4">
        <svg
          className="h-6 w-6 text-amber-600 dark:text-amber-400"
          fill="currentColor"
          viewBox="0 0 20 20"
        >
          <path d="M10 2a1 1 0 011 1v5.586l1.707-1.707a1 1 0 111.414 1.414L10.414 12 14.121 15.707a1 1 0 01-1.414 1.414L10 14.414l-2.707 2.707a1 1 0 01-1.414-1.414L9.586 12 5.879 8.293a1 1 0 011.414-1.414L9 8.586V3a1 1 0 011-1z" />
        </svg>
        <h3 className="text-xl font-semibold text-amber-900 dark:text-amber-100">
          Host Controls
        </h3>
      </div>

      <div className="space-y-3">
        {/* Start Round Button */}
        {showStartButton && (
          <button
            onClick={onStartRound}
            disabled={disabled}
            className="
              w-full flex items-center justify-center space-x-2
              bg-gradient-to-r from-green-500 to-green-600 hover:from-green-600 hover:to-green-700
              text-white font-semibold py-3 px-6 rounded-lg shadow-md
              transition-all duration-200
              disabled:opacity-50 disabled:cursor-not-allowed
              focus:outline-none focus:ring-4 focus:ring-green-300 dark:focus:ring-green-700
              active:scale-95
            "
          >
            <PlayIcon className="h-5 w-5" />
            <span>Start New Round</span>
          </button>
        )}

        {/* Reveal Button */}
        {showRevealButton && (
          <button
            onClick={onReveal}
            disabled={disabled}
            className="
              w-full flex items-center justify-center space-x-2
              bg-gradient-to-r from-primary-500 to-primary-600 hover:from-primary-600 hover:to-primary-700
              text-white font-semibold py-3 px-6 rounded-lg shadow-md
              transition-all duration-200
              disabled:opacity-50 disabled:cursor-not-allowed
              focus:outline-none focus:ring-4 focus:ring-primary-300 dark:focus:ring-primary-700
              active:scale-95
            "
          >
            <EyeIcon className="h-5 w-5" />
            <span>Reveal Votes</span>
          </button>
        )}

        {/* Reset Button */}
        {showResetButton && (
          <button
            onClick={onReset}
            disabled={disabled}
            className="
              w-full flex items-center justify-center space-x-2
              bg-gradient-to-r from-gray-500 to-gray-600 hover:from-gray-600 hover:to-gray-700
              text-white font-semibold py-3 px-6 rounded-lg shadow-md
              transition-all duration-200
              disabled:opacity-50 disabled:cursor-not-allowed
              focus:outline-none focus:ring-4 focus:ring-gray-300 dark:focus:ring-gray-700
              active:scale-95
            "
          >
            <RefreshIcon className="h-5 w-5" />
            <span>Reset Round</span>
          </button>
        )}

        {/* Help Text */}
        {roundInProgress && !roundRevealed && !hasVotes && (
          <div className="mt-4 p-3 bg-amber-100 dark:bg-amber-900/40 rounded-lg">
            <p className="text-sm text-amber-800 dark:text-amber-200 text-center">
              Waiting for participants to vote...
            </p>
          </div>
        )}

        {!roundInProgress && (
          <div className="mt-4 p-3 bg-blue-100 dark:bg-blue-900/40 rounded-lg">
            <p className="text-sm text-blue-800 dark:text-blue-200 text-center">
              Start a new round to begin voting
            </p>
          </div>
        )}
      </div>
    </div>
  );
};
