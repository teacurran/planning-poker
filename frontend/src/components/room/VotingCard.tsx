/**
 * Individual voting card component.
 * Displays a card with a value that can be selected or revealed.
 */

import React from 'react';

interface VotingCardProps {
  /** Card value (e.g., "1", "3", "5", "?") */
  value: string;

  /** Whether this card is currently selected */
  selected?: boolean;

  /** Whether the card is disabled (not clickable) */
  disabled?: boolean;

  /** Whether to show the card in revealed state (for RevealView) */
  revealed?: boolean;

  /** Click handler */
  onClick?: () => void;

  /** Display name (for revealed cards) */
  displayName?: string;
}

/**
 * VotingCard component - displays an individual estimation card.
 * Can be used in both selection mode (DeckSelector) and revealed mode (RevealView).
 */
export const VotingCard: React.FC<VotingCardProps> = ({
  value,
  selected = false,
  disabled = false,
  revealed = false,
  onClick,
  displayName,
}) => {
  const baseClasses = 'relative rounded-lg font-bold transition-all duration-200 select-none';

  // Different styling for selection mode vs reveal mode
  if (revealed) {
    // Revealed card styling - compact display for results
    return (
      <div className="flex flex-col items-center space-y-2">
        <div
          className={`
            ${baseClasses}
            w-16 h-20 flex items-center justify-center text-2xl
            bg-gradient-to-br from-primary-500 to-primary-600 text-white shadow-lg
            transform transition-transform duration-500
          `}
          style={{
            animation: 'flipIn 0.5s ease-out',
          }}
        >
          {value}
        </div>
        {displayName && (
          <span className="text-xs text-gray-600 dark:text-gray-400 text-center max-w-[80px] truncate">
            {displayName}
          </span>
        )}
      </div>
    );
  }

  // Selection mode styling
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`
        ${baseClasses}
        w-full aspect-[3/4] flex items-center justify-center text-3xl sm:text-4xl
        ${
          selected
            ? 'bg-gradient-to-br from-primary-600 to-primary-700 text-white shadow-xl scale-105 ring-4 ring-primary-300 dark:ring-primary-500'
            : 'bg-white dark:bg-gray-800 text-gray-900 dark:text-white shadow-md hover:shadow-lg'
        }
        ${
          disabled && !selected
            ? 'opacity-50 cursor-not-allowed'
            : 'hover:scale-105 active:scale-95 cursor-pointer'
        }
        focus:outline-none focus:ring-4 focus:ring-primary-300 dark:focus:ring-primary-500
      `}
      aria-label={`Vote ${value}`}
      aria-pressed={selected}
    >
      {value}
    </button>
  );
};

// Add CSS animation for card flip
const style = document.createElement('style');
style.textContent = `
  @keyframes flipIn {
    0% {
      transform: rotateY(90deg);
      opacity: 0;
    }
    100% {
      transform: rotateY(0deg);
      opacity: 1;
    }
  }
`;
if (typeof document !== 'undefined' && !document.querySelector('#voting-card-styles')) {
  style.id = 'voting-card-styles';
  document.head.appendChild(style);
}
