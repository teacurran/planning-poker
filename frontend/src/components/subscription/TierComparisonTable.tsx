/**
 * Tier comparison table component.
 * Displays a responsive grid of subscription tiers with features and pricing.
 */

import React from 'react';
import { CheckIcon } from '@heroicons/react/24/outline';
import type { SubscriptionTier } from '@/types/auth';
import { TIER_FEATURES, getAllTiers, type TierMetadata } from '@/utils/subscriptionUtils';

interface TierComparisonTableProps {
  currentTier?: SubscriptionTier;
  onUpgradeClick: (tier: SubscriptionTier) => void;
  isLoading?: boolean;
}

/**
 * Tier card component for individual tier display.
 */
const TierCard: React.FC<{
  tier: SubscriptionTier;
  metadata: TierMetadata;
  isCurrent: boolean;
  onUpgradeClick: () => void;
  isLoading: boolean;
}> = ({ tier, metadata, isCurrent, onUpgradeClick, isLoading }) => {
  const isEnterprise = tier === 'ENTERPRISE';
  const isFree = tier === 'FREE';
  const isRecommended = metadata.recommended;

  return (
    <div
      className={`relative rounded-lg border-2 p-6 shadow-sm transition-all hover:shadow-lg ${
        isRecommended
          ? 'border-blue-500 dark:border-blue-400'
          : 'border-gray-200 dark:border-gray-700'
      } ${isCurrent ? 'bg-blue-50 dark:bg-blue-950' : 'bg-white dark:bg-gray-800'}`}
    >
      {/* Recommended badge */}
      {isRecommended && (
        <div className="absolute -top-4 left-1/2 -translate-x-1/2">
          <span className="inline-flex items-center rounded-full bg-blue-500 px-3 py-1 text-xs font-medium text-white">
            Recommended
          </span>
        </div>
      )}

      {/* Current tier badge */}
      {isCurrent && (
        <div className="absolute -top-4 right-4">
          <span className="inline-flex items-center rounded-full bg-green-500 px-3 py-1 text-xs font-medium text-white">
            Current Plan
          </span>
        </div>
      )}

      {/* Tier name */}
      <h3 className="text-2xl font-bold text-gray-900 dark:text-white">{metadata.name}</h3>

      {/* Price */}
      <div className="mt-4 flex items-baseline">
        {metadata.price !== null ? (
          <>
            <span className="text-4xl font-extrabold text-gray-900 dark:text-white">
              ${metadata.price}
            </span>
            <span className="ml-1 text-xl text-gray-500 dark:text-gray-400">/month</span>
          </>
        ) : (
          <span className="text-2xl font-bold text-gray-900 dark:text-white">
            {metadata.priceLabel}
          </span>
        )}
      </div>

      {/* Description */}
      <p className="mt-4 text-sm text-gray-600 dark:text-gray-400">{metadata.description}</p>

      {/* CTA Button */}
      <button
        onClick={onUpgradeClick}
        disabled={isCurrent || isLoading}
        className={`mt-6 w-full rounded-md px-4 py-2 text-sm font-medium transition-colors ${
          isCurrent
            ? 'cursor-not-allowed bg-gray-300 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
            : isRecommended
              ? 'bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600'
              : 'bg-gray-900 text-white hover:bg-gray-800 dark:bg-gray-700 dark:hover:bg-gray-600'
        } disabled:cursor-not-allowed disabled:opacity-50`}
      >
        {isLoading
          ? 'Processing...'
          : isCurrent
            ? 'Current Plan'
            : isFree
              ? 'Get Started'
              : isEnterprise
                ? 'Contact Sales'
                : 'Upgrade'}
      </button>

      {/* Features list */}
      <ul className="mt-6 space-y-3">
        {metadata.features.map((feature, index) => (
          <li key={index} className="flex items-start">
            <CheckIcon className="h-5 w-5 flex-shrink-0 text-green-500 dark:text-green-400" />
            <span className="ml-3 text-sm text-gray-700 dark:text-gray-300">{feature}</span>
          </li>
        ))}
      </ul>
    </div>
  );
};

/**
 * Tier comparison table component.
 */
export const TierComparisonTable: React.FC<TierComparisonTableProps> = ({
  currentTier,
  onUpgradeClick,
  isLoading = false,
}) => {
  const tiers = getAllTiers();

  return (
    <div className="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-4">
      {tiers.map((tier) => {
        const metadata = TIER_FEATURES[tier];
        const isCurrent = tier === currentTier;

        return (
          <TierCard
            key={tier}
            tier={tier}
            metadata={metadata}
            isCurrent={isCurrent}
            onUpgradeClick={() => onUpgradeClick(tier)}
            isLoading={isLoading}
          />
        );
      })}
    </div>
  );
};
