/**
 * Utility functions for subscription management.
 */

import type { SubscriptionTier } from '@/types/auth';
import type { SubscriptionStatus } from '@/types/subscription';

/**
 * Pricing configuration for subscription tiers (monthly, in USD).
 */
export const TIER_PRICING: Record<Exclude<SubscriptionTier, 'FREE' | 'ENTERPRISE'>, number> = {
  PRO: 10,
  PRO_PLUS: 30,
};

/**
 * Tier display metadata.
 */
export interface TierMetadata {
  name: string;
  price: number | null; // null for contact-based pricing
  priceLabel: string;
  description: string;
  features: string[];
  recommended?: boolean;
}

/**
 * Complete tier feature configuration.
 */
export const TIER_FEATURES: Record<SubscriptionTier, TierMetadata> = {
  FREE: {
    name: 'Free',
    price: 0,
    priceLabel: 'Free',
    description: 'Perfect for trying out Planning Poker',
    features: [
      'Basic planning poker functionality',
      'Public rooms only',
      'Basic session summaries',
      '30 days session history',
      'Banner ads',
    ],
  },
  PRO: {
    name: 'Pro',
    price: 10,
    priceLabel: '$10/month',
    description: 'For professional teams and power users',
    features: [
      'All Free features',
      'Ad-free experience',
      'Advanced reports with round-level detail',
      'User consistency metrics',
      'CSV/JSON/PDF export',
      '90 days session history',
    ],
    recommended: true,
  },
  PRO_PLUS: {
    name: 'Pro Plus',
    price: 30,
    priceLabel: '$30/month',
    description: 'Enhanced privacy and control',
    features: [
      'All Pro features',
      'Invite-only rooms',
      'Enhanced privacy controls',
      'Priority support',
    ],
  },
  ENTERPRISE: {
    name: 'Enterprise',
    price: null,
    priceLabel: 'Contact Sales',
    description: 'For organizations with advanced needs',
    features: [
      'All Pro Plus features',
      'Organization management',
      'SSO integration (OIDC/SAML2)',
      'Audit logging',
      'Organization-wide analytics',
      'Organization-restricted rooms',
      'Unlimited session history',
      'Dedicated support',
    ],
  },
};

/**
 * Get color classes for subscription tier badge.
 */
export function getTierBadgeClasses(tier: SubscriptionTier): string {
  switch (tier) {
    case 'FREE':
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300';
    case 'PRO':
      return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300';
    case 'PRO_PLUS':
      return 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-300';
    case 'ENTERPRISE':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300';
    default:
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300';
  }
}

/**
 * Format subscription tier for display.
 */
export function formatTierName(tier: SubscriptionTier): string {
  return TIER_FEATURES[tier]?.name || tier;
}

/**
 * Format price in cents to USD string.
 * @param cents - Amount in cents (e.g., 2999)
 * @returns Formatted price string (e.g., "$29.99")
 */
export function formatPrice(cents: number): string {
  const dollars = cents / 100;
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(dollars);
}

/**
 * Format subscription status for display.
 */
export function formatSubscriptionStatus(status: SubscriptionStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'Active';
    case 'TRIALING':
      return 'Trial';
    case 'PAST_DUE':
      return 'Past Due';
    case 'CANCELED':
      return 'Canceled';
    case 'PAUSED':
      return 'Paused';
    default:
      return status;
  }
}

/**
 * Get status badge color classes.
 */
export function getStatusBadgeClasses(status: SubscriptionStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300';
    case 'TRIALING':
      return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300';
    case 'PAST_DUE':
      return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300';
    case 'CANCELED':
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300';
    case 'PAUSED':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300';
    default:
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300';
  }
}

/**
 * Check if a tier is higher than another.
 */
export function isTierHigherThan(tier: SubscriptionTier, compareTo: SubscriptionTier): boolean {
  const tierOrder: SubscriptionTier[] = ['FREE', 'PRO', 'PRO_PLUS', 'ENTERPRISE'];
  return tierOrder.indexOf(tier) > tierOrder.indexOf(compareTo);
}

/**
 * Get the next tier in the hierarchy.
 */
export function getNextTier(currentTier: SubscriptionTier): SubscriptionTier | null {
  const tierOrder: SubscriptionTier[] = ['FREE', 'PRO', 'PRO_PLUS', 'ENTERPRISE'];
  const currentIndex = tierOrder.indexOf(currentTier);

  if (currentIndex === -1 || currentIndex === tierOrder.length - 1) {
    return null;
  }

  return tierOrder[currentIndex + 1];
}

/**
 * Get all tiers in order.
 */
export function getAllTiers(): SubscriptionTier[] {
  return ['FREE', 'PRO', 'PRO_PLUS', 'ENTERPRISE'];
}
