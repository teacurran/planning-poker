/**
 * Upgrade modal component.
 * Displays when user hits a tier limit (403 FeatureNotAvailable error).
 * Prompts user to upgrade with feature benefits and upgrade button.
 */

import React from 'react';
import { Dialog, Transition } from '@headlessui/react';
import { Fragment } from 'react';
import { XMarkIcon, SparklesIcon } from '@heroicons/react/24/outline';
import { useNavigate } from 'react-router-dom';
import type { SubscriptionTier } from '@/types/auth';
import { TIER_FEATURES, formatTierName } from '@/utils/subscriptionUtils';
import { useCreateCheckout } from '@/services/subscriptionApi';
import type { CheckoutRequest } from '@/types/subscription';

interface UpgradeModalProps {
  isOpen: boolean;
  onClose: () => void;
  requiredTier: SubscriptionTier;
  currentTier: SubscriptionTier;
  feature: string;
}

export const UpgradeModal: React.FC<UpgradeModalProps> = ({
  isOpen,
  onClose,
  requiredTier,
  currentTier,
  feature,
}) => {
  const navigate = useNavigate();
  const createCheckout = useCreateCheckout();

  const requiredTierMetadata = TIER_FEATURES[requiredTier];
  const isEnterprise = requiredTier === 'ENTERPRISE';

  const handleUpgrade = () => {
    // Enterprise tier - redirect to contact sales
    if (isEnterprise) {
      window.location.href = 'mailto:sales@planningpoker.example.com?subject=Enterprise Plan Inquiry';
      onClose();
      return;
    }

    // For PRO and PRO_PLUS, create checkout session
    const checkoutData: CheckoutRequest = {
      tier: requiredTier as 'PRO' | 'PRO_PLUS',
      successUrl: `${window.location.origin}/billing/success?tier=${requiredTier}`,
      cancelUrl: window.location.href, // Return to current page on cancel
    };

    createCheckout.mutate(checkoutData, {
      onSuccess: (response) => {
        // Redirect to Stripe Checkout
        window.location.href = response.checkoutUrl;
      },
      onError: (error) => {
        alert(`Failed to create checkout session: ${error.message}`);
      },
    });
  };

  const handleViewPricing = () => {
    navigate('/pricing');
    onClose();
  };

  return (
    <Transition appear show={isOpen} as={Fragment}>
      <Dialog as="div" className="relative z-50" onClose={onClose}>
        <Transition.Child
          as={Fragment}
          enter="ease-out duration-300"
          enterFrom="opacity-0"
          enterTo="opacity-100"
          leave="ease-in duration-200"
          leaveFrom="opacity-100"
          leaveTo="opacity-0"
        >
          <div className="fixed inset-0 bg-black bg-opacity-25 dark:bg-opacity-50" />
        </Transition.Child>

        <div className="fixed inset-0 overflow-y-auto">
          <div className="flex min-h-full items-center justify-center p-4 text-center">
            <Transition.Child
              as={Fragment}
              enter="ease-out duration-300"
              enterFrom="opacity-0 scale-95"
              enterTo="opacity-100 scale-100"
              leave="ease-in duration-200"
              leaveFrom="opacity-100 scale-100"
              leaveTo="opacity-0 scale-95"
            >
              <Dialog.Panel className="w-full max-w-md transform overflow-hidden rounded-2xl bg-white p-6 text-left align-middle shadow-xl transition-all dark:bg-gray-800">
                {/* Close button */}
                <button
                  onClick={onClose}
                  className="absolute right-4 top-4 rounded-md p-1 text-gray-400 hover:text-gray-500 dark:hover:text-gray-300"
                >
                  <XMarkIcon className="h-6 w-6" />
                </button>

                {/* Icon */}
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-900">
                  <SparklesIcon className="h-6 w-6 text-blue-600 dark:text-blue-400" />
                </div>

                {/* Title */}
                <Dialog.Title
                  as="h3"
                  className="mt-4 text-center text-lg font-semibold leading-6 text-gray-900 dark:text-white"
                >
                  Upgrade to {formatTierName(requiredTier)}
                </Dialog.Title>

                {/* Description */}
                <div className="mt-4">
                  <p className="text-center text-sm text-gray-600 dark:text-gray-400">
                    You need <span className="font-semibold">{formatTierName(requiredTier)}</span> to
                    access <span className="font-semibold">{feature}</span>.
                  </p>
                </div>

                {/* Tier benefits */}
                <div className="mt-6 rounded-lg bg-gray-50 p-4 dark:bg-gray-900">
                  <h4 className="mb-3 text-sm font-semibold text-gray-900 dark:text-white">
                    {formatTierName(requiredTier)} includes:
                  </h4>
                  <ul className="space-y-2">
                    {requiredTierMetadata.features.slice(0, 5).map((benefit, index) => (
                      <li key={index} className="flex items-start text-sm text-gray-700 dark:text-gray-300">
                        <svg
                          className="mr-2 mt-0.5 h-4 w-4 flex-shrink-0 text-green-500"
                          fill="currentColor"
                          viewBox="0 0 20 20"
                        >
                          <path
                            fillRule="evenodd"
                            d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                            clipRule="evenodd"
                          />
                        </svg>
                        {benefit}
                      </li>
                    ))}
                  </ul>
                </div>

                {/* Pricing */}
                {requiredTierMetadata.price !== null && (
                  <div className="mt-4 text-center">
                    <span className="text-3xl font-bold text-gray-900 dark:text-white">
                      ${requiredTierMetadata.price}
                    </span>
                    <span className="text-gray-600 dark:text-gray-400">/month</span>
                  </div>
                )}

                {/* Action buttons */}
                <div className="mt-6 flex flex-col gap-3">
                  <button
                    type="button"
                    onClick={handleUpgrade}
                    disabled={createCheckout.isPending}
                    className="inline-flex w-full justify-center rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600"
                  >
                    {createCheckout.isPending
                      ? 'Processing...'
                      : isEnterprise
                        ? 'Contact Sales'
                        : `Upgrade to ${formatTierName(requiredTier)}`}
                  </button>

                  <button
                    type="button"
                    onClick={handleViewPricing}
                    className="inline-flex w-full justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                  >
                    View All Plans
                  </button>
                </div>

                {/* Current tier info */}
                <p className="mt-4 text-center text-xs text-gray-500 dark:text-gray-400">
                  Current plan: {formatTierName(currentTier)}
                </p>
              </Dialog.Panel>
            </Transition.Child>
          </div>
        </div>
      </Dialog>
    </Transition>
  );
};
