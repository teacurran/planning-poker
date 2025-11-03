/**
 * Subscription API service layer with React Query hooks.
 *
 * This module provides:
 * - useSubscription(userId): Fetch user subscription status
 * - useInvoices(userId, page, size): Fetch payment history with pagination
 * - useCreateCheckout(): Mutation hook for creating Stripe checkout sessions
 * - useCancelSubscription(): Mutation hook for canceling subscriptions
 *
 * All hooks automatically handle:
 * - Loading states
 * - Error states
 * - Data caching and invalidation
 * - Authentication (via apiClient interceptors)
 */

import {
  useQuery,
  useMutation,
  useQueryClient,
  UseQueryOptions,
  UseMutationOptions,
} from '@tanstack/react-query';
import { apiClient, getErrorMessage } from './api';
import { queryKeys as baseQueryKeys } from './apiHooks';
import type {
  SubscriptionDTO,
  CheckoutRequest,
  CheckoutResponse,
  InvoiceListResponse,
} from '@/types/subscription';

// ============================================
// QUERY KEY FACTORIES
// ============================================

/**
 * Extend centralized query keys for subscription operations.
 */
export const subscriptionQueryKeys = {
  ...baseQueryKeys,
  subscriptions: {
    all: ['subscriptions'] as const,
    byUser: (userId: string) => ['subscriptions', 'user', userId] as const,
  },
  billing: {
    all: ['billing'] as const,
    invoices: (userId: string, page: number, size: number) =>
      ['billing', 'invoices', userId, page, size] as const,
  },
};

// ============================================
// SUBSCRIPTION QUERY HOOKS
// ============================================

/**
 * Fetch user subscription status.
 *
 * @param userId - The user ID to fetch subscription for
 * @param options - Additional React Query options
 * @returns React Query result with subscription data, loading state, and error
 *
 * @example
 * ```tsx
 * function SubscriptionStatus({ userId }: { userId: string }) {
 *   const { data: subscription, isLoading, error } = useSubscription(userId);
 *
 *   if (isLoading) return <div>Loading...</div>;
 *   if (error) return <div>Error: {error.message}</div>;
 *   if (!subscription) return <div>No subscription found</div>;
 *
 *   return <div>{subscription.tier}</div>;
 * }
 * ```
 */
export function useSubscription(
  userId: string,
  options?: Omit<UseQueryOptions<SubscriptionDTO, Error>, 'queryKey' | 'queryFn'>
) {
  return useQuery<SubscriptionDTO, Error>({
    queryKey: subscriptionQueryKeys.subscriptions.byUser(userId),
    queryFn: async () => {
      const response = await apiClient.get<SubscriptionDTO>(`/subscriptions/${userId}`);
      return response.data;
    },
    enabled: !!userId,
    staleTime: 5 * 60 * 1000, // 5 minutes
    ...options,
  });
}

/**
 * Fetch payment history (invoices) with pagination.
 *
 * @param page - Page number (0-indexed, default: 0)
 * @param size - Page size (default: 10)
 * @param options - Additional React Query options
 * @returns React Query result with invoice list, loading state, and error
 *
 * @example
 * ```tsx
 * function PaymentHistory() {
 *   const [page, setPage] = useState(0);
 *   const { data, isLoading, error } = useInvoices(page, 10);
 *
 *   if (isLoading) return <div>Loading...</div>;
 *   if (error) return <div>Error: {error.message}</div>;
 *
 *   return (
 *     <div>
 *       {data?.invoices.map(invoice => (
 *         <div key={invoice.paymentId}>{invoice.amount}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useInvoices(
  page = 0,
  size = 10,
  options?: Omit<UseQueryOptions<InvoiceListResponse, Error>, 'queryKey' | 'queryFn'>
) {
  return useQuery<InvoiceListResponse, Error>({
    queryKey: subscriptionQueryKeys.billing.invoices('current', page, size),
    queryFn: async () => {
      const response = await apiClient.get<InvoiceListResponse>('/billing/invoices', {
        params: { page, size },
      });
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes (payment history changes infrequently)
    placeholderData: (previousData) => previousData, // Smooth pagination
    ...options,
  });
}

// ============================================
// SUBSCRIPTION MUTATION HOOKS
// ============================================

/**
 * Create a Stripe checkout session and redirect to Stripe.
 *
 * This mutation automatically:
 * 1. Creates a checkout session via the backend
 * 2. Redirects the user to Stripe Checkout
 * 3. User completes payment on Stripe
 * 4. Stripe redirects back to successUrl or cancelUrl
 *
 * @param options - Additional React Query mutation options
 * @returns React Query mutation result
 *
 * @example
 * ```tsx
 * function UpgradeButton({ tier }: { tier: 'PRO' | 'PRO_PLUS' }) {
 *   const createCheckout = useCreateCheckout();
 *
 *   const handleUpgrade = () => {
 *     createCheckout.mutate(
 *       {
 *         tier,
 *         successUrl: `${window.location.origin}/billing/success`,
 *         cancelUrl: `${window.location.origin}/billing/cancel`,
 *       },
 *       {
 *         onSuccess: (response) => {
 *           // Redirect to Stripe Checkout
 *           window.location.href = response.checkoutUrl;
 *         },
 *         onError: (error) => {
 *           alert(`Failed to create checkout: ${error.message}`);
 *         },
 *       }
 *     );
 *   };
 *
 *   return (
 *     <button onClick={handleUpgrade} disabled={createCheckout.isPending}>
 *       {createCheckout.isPending ? 'Creating...' : `Upgrade to ${tier}`}
 *     </button>
 *   );
 * }
 * ```
 */
export function useCreateCheckout(
  options?: Omit<UseMutationOptions<CheckoutResponse, Error, CheckoutRequest, unknown>, 'mutationFn'>
) {
  return useMutation<CheckoutResponse, Error, CheckoutRequest>({
    mutationFn: async (checkoutData: CheckoutRequest) => {
      const response = await apiClient.post<CheckoutResponse>('/subscriptions/checkout', checkoutData);
      return response.data;
    },
    onError: (error) => {
      console.error('Failed to create checkout session:', getErrorMessage(error));
    },
    ...options,
  });
}

/**
 * Cancel a subscription (at end of billing period).
 *
 * This mutation automatically invalidates subscription cache on success.
 * The subscription remains active until the end of the current billing period.
 *
 * @param options - Additional React Query mutation options
 * @returns React Query mutation result
 *
 * @example
 * ```tsx
 * function CancelSubscriptionButton({ subscriptionId }: { subscriptionId: string }) {
 *   const cancelSubscription = useCancelSubscription();
 *
 *   const handleCancel = () => {
 *     if (confirm('Are you sure you want to cancel your subscription?')) {
 *       cancelSubscription.mutate(subscriptionId, {
 *         onSuccess: (subscription) => {
 *           alert(`Subscription will end on ${subscription.currentPeriodEnd}`);
 *         },
 *         onError: (error) => {
 *           alert(`Failed to cancel: ${error.message}`);
 *         },
 *       });
 *     }
 *   };
 *
 *   return (
 *     <button onClick={handleCancel} disabled={cancelSubscription.isPending}>
 *       {cancelSubscription.isPending ? 'Canceling...' : 'Cancel Subscription'}
 *     </button>
 *   );
 * }
 * ```
 */
export function useCancelSubscription(
  options?: Omit<UseMutationOptions<SubscriptionDTO, Error, string, unknown>, 'mutationFn'>
) {
  const queryClient = useQueryClient();

  return useMutation<SubscriptionDTO, Error, string>({
    mutationFn: async (subscriptionId: string) => {
      const response = await apiClient.post<SubscriptionDTO>(
        `/subscriptions/${subscriptionId}/cancel`
      );
      return response.data;
    },
    onSuccess: async (data) => {
      // Invalidate subscription queries to trigger refetch
      await queryClient.invalidateQueries({
        queryKey: subscriptionQueryKeys.subscriptions.byUser(data.entityId),
      });

      // Also invalidate the general subscriptions query key
      await queryClient.invalidateQueries({
        queryKey: subscriptionQueryKeys.subscriptions.all,
      });

      // Optionally update the cache directly
      queryClient.setQueryData(
        subscriptionQueryKeys.subscriptions.byUser(data.entityId),
        data
      );
    },
    onError: (error) => {
      console.error('Failed to cancel subscription:', getErrorMessage(error));
    },
    ...options,
  });
}

/**
 * Refresh user subscription data after successful checkout.
 * Call this after Stripe redirects back to successUrl.
 *
 * @param userId - The user ID to refresh subscription for
 */
export async function refreshSubscription(userId: string): Promise<void> {
  const queryClient = useQueryClient();
  await queryClient.invalidateQueries({
    queryKey: subscriptionQueryKeys.subscriptions.byUser(userId),
  });
}
