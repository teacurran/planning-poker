/**
 * Deck selector component - displays a grid of voting cards.
 * Handles card selection and sends vote.cast message.
 */

import React, { useCallback } from 'react';
import { VotingCard } from './VotingCard';

interface DeckSelectorProps {
  /** Callback when a card is selected */
  onCardSelect: (value: string) => void;

  /** Whether voting is disabled (e.g., not connected, observer role, round not started) */
  disabled?: boolean;

  /** Current selected value (for controlled state) */
  selectedValue?: string | null;

  /** Deck type from room config */
  deckType?: string;

  /** Custom deck values (if deckType is 'custom') */
  customDeck?: string[] | null;
}

// Deck configurations based on protocol specification
const FIBONACCI_DECK = ['0', '1', '2', '3', '5', '8', '13', '21', '?'];
const T_SHIRT_DECK = ['XS', 'S', 'M', 'L', 'XL', 'XXL', '?'];
const POWER_OF_TWO_DECK = ['1', '2', '4', '8', '16', '32', '64', '?'];

/**
 * DeckSelector component - displays a responsive grid of voting cards.
 */
export const DeckSelector: React.FC<DeckSelectorProps> = ({
  onCardSelect,
  disabled = false,
  selectedValue = null,
  deckType = 'fibonacci',
  customDeck = null,
}) => {
  // Determine which deck to display
  const getDeckValues = useCallback((): string[] => {
    if (deckType === 'custom' && customDeck && customDeck.length > 0) {
      return customDeck;
    }

    switch (deckType.toLowerCase()) {
      case 't_shirt':
        return T_SHIRT_DECK;
      case 'power_of_two':
        return POWER_OF_TWO_DECK;
      case 'fibonacci':
      default:
        return FIBONACCI_DECK;
    }
  }, [deckType, customDeck]);

  const deckValues = getDeckValues();

  const handleCardClick = useCallback(
    (value: string) => {
      if (!disabled) {
        onCardSelect(value);
      }
    },
    [disabled, onCardSelect]
  );

  return (
    <div className="w-full">
      <div
        className="
          grid gap-3 sm:gap-4
          grid-cols-3 xs:grid-cols-4 sm:grid-cols-5 md:grid-cols-6 lg:grid-cols-5 xl:grid-cols-6
          max-w-3xl mx-auto
        "
      >
        {deckValues.map((value) => (
          <VotingCard
            key={value}
            value={value}
            selected={selectedValue === value}
            disabled={disabled}
            onClick={() => handleCardClick(value)}
          />
        ))}
      </div>

      {disabled && (
        <div className="mt-4 text-center">
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Voting is currently disabled
          </p>
        </div>
      )}
    </div>
  );
};
