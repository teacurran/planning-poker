/**
 * Billing success page.
 * Displays after successful Stripe checkout redirect.
 */

import React, { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircleIcon } from '@heroicons/react/24/outline';
import { useAuthStore } from '@/stores/authStore';
import { useSubscription } from '@/services/subscriptionApi';
import { formatTierName } from '@/utils/subscriptionUtils';
import type { SubscriptionTier } from '@/types/auth';

/**
 * Type guard to validate if a string is a valid SubscriptionTier.
 */
function isValidTier(tier: string | null): tier is SubscriptionTier {
  if (!tier) return false;
  return ['FREE', 'PRO', 'PRO_PLUS', 'ENTERPRISE'].includes(tier);
}

export const BillingSuccessPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { user } = useAuthStore();
  const tierParam = searchParams.get('tier');
  const tier: SubscriptionTier | null = isValidTier(tierParam) ? tierParam : null;

  // Fetch updated subscription to verify
  const { data: subscription, refetch } = useSubscription(user?.userId || '', {
    enabled: !!user?.userId,
  });

  useEffect(() => {
    // Refetch subscription data when component mounts
    if (user?.userId) {
      refetch();
    }
  }, [user?.userId, refetch]);

  const handleContinue = () => {
    navigate('/dashboard');
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="w-full max-w-md">
        <div className="rounded-lg bg-white p-8 shadow-lg dark:bg-gray-800">
          {/* Success icon */}
          <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-green-100 dark:bg-green-900">
            <CheckCircleIcon className="h-10 w-10 text-green-600 dark:text-green-400" />
          </div>

          {/* Title */}
          <h1 className="mt-6 text-center text-2xl font-bold text-gray-900 dark:text-white">
            Subscription Activated!
          </h1>

          {/* Description */}
          <p className="mt-4 text-center text-gray-600 dark:text-gray-400">
            {tier
              ? `Welcome to ${formatTierName(tier)}! Your subscription has been successfully activated.`
              : 'Your subscription has been successfully activated.'}
          </p>

          {/* Subscription details */}
          {subscription && (
            <div className="mt-6 rounded-lg bg-gray-50 p-4 dark:bg-gray-900">
              <dl className="space-y-2">
                <div className="flex justify-between text-sm">
                  <dt className="text-gray-600 dark:text-gray-400">Current Plan:</dt>
                  <dd className="font-semibold text-gray-900 dark:text-white">
                    {formatTierName(subscription.tier)}
                  </dd>
                </div>
                <div className="flex justify-between text-sm">
                  <dt className="text-gray-600 dark:text-gray-400">Status:</dt>
                  <dd className="font-semibold text-green-600 dark:text-green-400">
                    {subscription.status}
                  </dd>
                </div>
              </dl>
            </div>
          )}

          {/* Action buttons */}
          <div className="mt-8 space-y-3">
            <button
              onClick={handleContinue}
              className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 dark:bg-blue-500 dark:hover:bg-blue-600"
            >
              Continue to Dashboard
            </button>
            <button
              onClick={() => navigate('/billing/settings')}
              className="w-full rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
            >
              View Billing Settings
            </button>
          </div>

          {/* Info message */}
          <p className="mt-6 text-center text-xs text-gray-500 dark:text-gray-400">
            You now have access to all {tier ? formatTierName(tier) : 'premium'} features.
          </p>
        </div>
      </div>
    </div>
  );
};
