/**
 * Subscription-related TypeScript types matching the OpenAPI specification.
 * These types ensure type safety for subscription API requests and responses.
 */

import type { SubscriptionTier } from './auth';

/**
 * Subscription status enum.
 */
export type SubscriptionStatus = 'ACTIVE' | 'TRIALING' | 'PAST_DUE' | 'CANCELED' | 'PAUSED';

/**
 * Entity type for subscription (USER or ORGANIZATION).
 */
export type EntityType = 'USER' | 'ORGANIZATION';

/**
 * Payment status for invoices.
 */
export type PaymentStatus = 'PAID' | 'PENDING' | 'FAILED' | 'REFUNDED';

/**
 * Subscription data transfer object matching the API response schema.
 */
export interface SubscriptionDTO {
  subscriptionId: string;
  stripeSubscriptionId?: string;
  entityId: string;
  entityType: EntityType;
  tier: SubscriptionTier;
  status: SubscriptionStatus;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  canceledAt?: string | null;
  createdAt: string;
}

/**
 * Request payload for creating a Stripe checkout session.
 */
export interface CheckoutRequest {
  tier: Exclude<SubscriptionTier, 'FREE' | 'ENTERPRISE'>;
  successUrl: string;
  cancelUrl: string;
}

/**
 * Response from creating a Stripe checkout session.
 */
export interface CheckoutResponse {
  sessionId: string;
  checkoutUrl: string;
}

/**
 * Payment history record for invoices.
 */
export interface PaymentHistoryDTO {
  paymentId: string;
  subscriptionId: string;
  stripeInvoiceId?: string;
  amount: number; // in cents
  currency: string; // e.g., "USD"
  status: PaymentStatus;
  paidAt: string;
}

/**
 * Paginated response for invoice list.
 */
export interface InvoiceListResponse {
  invoices: PaymentHistoryDTO[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Feature not available error details.
 */
export interface FeatureNotAvailableDetails {
  requiredTier: SubscriptionTier;
  currentTier: SubscriptionTier;
  feature: string;
}
