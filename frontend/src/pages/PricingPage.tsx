/**
 * Pricing page component.
 * Displays subscription tiers, features, and handles upgrade flow.
 */

import React from 'react';
import { useNavigate } from 'react-router-dom';
import { TierComparisonTable } from '@/components/subscription/TierComparisonTable';
import { useAuthStore } from '@/stores/authStore';
import { useCreateCheckout } from '@/services/subscriptionApi';
import type { SubscriptionTier } from '@/types/auth';
import type { CheckoutRequest } from '@/types/subscription';

export const PricingPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const createCheckout = useCreateCheckout();

  const handleUpgradeClick = async (tier: SubscriptionTier) => {
    // Free tier - no action needed
    if (tier === 'FREE') {
      return;
    }

    // Enterprise tier - contact sales
    if (tier === 'ENTERPRISE') {
      // TODO: Implement contact sales flow (could open a modal or navigate to contact page)
      window.location.href = 'mailto:sales@planningpoker.example.com?subject=Enterprise Plan Inquiry';
      return;
    }

    // User must be logged in to upgrade
    if (!user) {
      navigate('/login', { state: { from: '/pricing' } });
      return;
    }

    // Already on this tier
    if (user.subscriptionTier === tier) {
      navigate('/billing/settings');
      return;
    }

    // Create checkout session for PRO or PRO_PLUS
    const checkoutData: CheckoutRequest = {
      tier: tier as 'PRO' | 'PRO_PLUS',
      successUrl: `${window.location.origin}/billing/success?tier=${tier}`,
      cancelUrl: `${window.location.origin}/pricing`,
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

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      {/* Hero section */}
      <div className="bg-gradient-to-b from-blue-600 to-blue-700 dark:from-blue-800 dark:to-blue-900">
        <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
          <div className="text-center">
            <h1 className="text-4xl font-extrabold text-white sm:text-5xl md:text-6xl">
              Choose Your Plan
            </h1>
            <p className="mx-auto mt-4 max-w-2xl text-xl text-blue-100">
              Unlock powerful features and take your planning poker sessions to the next level.
              All plans include our core planning poker functionality.
            </p>
          </div>
        </div>
      </div>

      {/* Tier comparison section */}
      <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
        <TierComparisonTable
          currentTier={user?.subscriptionTier}
          onUpgradeClick={handleUpgradeClick}
          isLoading={createCheckout.isPending}
        />
      </div>

      {/* FAQ section */}
      <div className="mx-auto max-w-4xl px-4 py-16 sm:px-6 lg:px-8">
        <h2 className="mb-8 text-center text-3xl font-bold text-gray-900 dark:text-white">
          Frequently Asked Questions
        </h2>

        <div className="space-y-6">
          <div className="rounded-lg bg-white p-6 shadow dark:bg-gray-800">
            <h3 className="mb-2 text-lg font-semibold text-gray-900 dark:text-white">
              Can I change plans later?
            </h3>
            <p className="text-gray-600 dark:text-gray-400">
              Yes! You can upgrade your plan at any time. If you downgrade, your subscription will
              continue until the end of your current billing period, and then switch to the new plan.
            </p>
          </div>

          <div className="rounded-lg bg-white p-6 shadow dark:bg-gray-800">
            <h3 className="mb-2 text-lg font-semibold text-gray-900 dark:text-white">
              What payment methods do you accept?
            </h3>
            <p className="text-gray-600 dark:text-gray-400">
              We accept all major credit cards through Stripe, our secure payment processor.
            </p>
          </div>

          <div className="rounded-lg bg-white p-6 shadow dark:bg-gray-800">
            <h3 className="mb-2 text-lg font-semibold text-gray-900 dark:text-white">
              Is there a free trial?
            </h3>
            <p className="text-gray-600 dark:text-gray-400">
              Our Free plan is available indefinitely with no credit card required. You can upgrade
              to a paid plan at any time to unlock additional features.
            </p>
          </div>

          <div className="rounded-lg bg-white p-6 shadow dark:bg-gray-800">
            <h3 className="mb-2 text-lg font-semibold text-gray-900 dark:text-white">
              What happens if I cancel my subscription?
            </h3>
            <p className="text-gray-600 dark:text-gray-400">
              You'll retain access to your paid plan features until the end of your current billing
              period. After that, your account will revert to the Free plan.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
