/**
 * Subscription settings page component.
 * Displays current subscription tier, billing status, cancel button, and payment history.
 */

import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Dialog, Transition } from '@headlessui/react';
import { Fragment } from 'react';
import {
  CreditCardIcon,
  ExclamationTriangleIcon,
  CalendarIcon,
  DocumentTextIcon,
} from '@heroicons/react/24/outline';
import { format } from 'date-fns';
import { useAuthStore } from '@/stores/authStore';
import { useSubscription, useInvoices, useCancelSubscription } from '@/services/subscriptionApi';
import {
  getTierBadgeClasses,
  formatTierName,
  formatSubscriptionStatus,
  getStatusBadgeClasses,
  formatPrice,
} from '@/utils/subscriptionUtils';
import type { PaymentHistoryDTO } from '@/types/subscription';

/**
 * Cancellation confirmation modal.
 */
const CancelConfirmationModal: React.FC<{
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  isLoading: boolean;
  currentPeriodEnd: string;
}> = ({ isOpen, onClose, onConfirm, isLoading, currentPeriodEnd }) => {
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
                <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-900">
                  <ExclamationTriangleIcon className="h-6 w-6 text-red-600 dark:text-red-400" />
                </div>

                <Dialog.Title
                  as="h3"
                  className="mt-4 text-center text-lg font-semibold leading-6 text-gray-900 dark:text-white"
                >
                  Cancel Subscription
                </Dialog.Title>

                <div className="mt-4">
                  <p className="text-center text-sm text-gray-600 dark:text-gray-400">
                    Are you sure you want to cancel your subscription? Your access will remain active
                    until{' '}
                    <span className="font-semibold">
                      {format(new Date(currentPeriodEnd), 'MMMM d, yyyy')}
                    </span>
                    , and then your account will revert to the Free plan.
                  </p>
                </div>

                <div className="mt-6 flex gap-3">
                  <button
                    type="button"
                    onClick={onClose}
                    disabled={isLoading}
                    className="inline-flex flex-1 justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                  >
                    Keep Subscription
                  </button>
                  <button
                    type="button"
                    onClick={onConfirm}
                    disabled={isLoading}
                    className="inline-flex flex-1 justify-center rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-red-500 dark:hover:bg-red-600"
                  >
                    {isLoading ? 'Canceling...' : 'Yes, Cancel'}
                  </button>
                </div>
              </Dialog.Panel>
            </Transition.Child>
          </div>
        </div>
      </Dialog>
    </Transition>
  );
};

/**
 * Payment history table component.
 */
const PaymentHistoryTable: React.FC<{
  invoices: PaymentHistoryDTO[];
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  isLoading: boolean;
}> = ({ invoices, page, totalPages, onPageChange, isLoading }) => {
  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-8">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-gray-300 border-t-blue-600 dark:border-gray-600 dark:border-t-blue-400" />
      </div>
    );
  }

  if (invoices.length === 0) {
    return (
      <div className="py-8 text-center">
        <DocumentTextIcon className="mx-auto h-12 w-12 text-gray-400 dark:text-gray-600" />
        <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">No payment history yet</p>
      </div>
    );
  }

  return (
    <div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          <thead className="bg-gray-50 dark:bg-gray-900">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                Date
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                Amount
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                Status
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                Invoice
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200 bg-white dark:divide-gray-700 dark:bg-gray-800">
            {invoices.map((invoice) => (
              <tr key={invoice.paymentId}>
                <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900 dark:text-white">
                  {format(new Date(invoice.paidAt), 'MMM d, yyyy')}
                </td>
                <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900 dark:text-white">
                  {formatPrice(invoice.amount)}
                </td>
                <td className="whitespace-nowrap px-6 py-4 text-sm">
                  <span
                    className={`inline-flex rounded-full px-2 text-xs font-semibold leading-5 ${
                      invoice.status === 'PAID'
                        ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300'
                        : invoice.status === 'PENDING'
                          ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300'
                          : invoice.status === 'FAILED'
                            ? 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300'
                            : 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-300'
                    }`}
                  >
                    {invoice.status}
                  </span>
                </td>
                <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500 dark:text-gray-400">
                  {invoice.stripeInvoiceId ? (
                    <a
                      href={`https://dashboard.stripe.com/invoices/${invoice.stripeInvoiceId}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
                    >
                      View
                    </a>
                  ) : (
                    <span className="text-gray-400">N/A</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between">
          <button
            onClick={() => onPageChange(page - 1)}
            disabled={page === 0}
            className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
          >
            Previous
          </button>
          <span className="text-sm text-gray-600 dark:text-gray-400">
            Page {page + 1} of {totalPages}
          </span>
          <button
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages - 1}
            className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
};

/**
 * Subscription settings page.
 */
export const SubscriptionSettingsPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const [page, setPage] = useState(0);
  const [showCancelModal, setShowCancelModal] = useState(false);

  const { data: subscription, isLoading: subscriptionLoading } = useSubscription(
    user?.userId || '',
    {
      enabled: !!user?.userId,
    }
  );

  const { data: invoicesData, isLoading: invoicesLoading } = useInvoices(page, 10, {
    enabled: !!user?.userId,
  });

  const cancelSubscription = useCancelSubscription();

  const handleCancelSubscription = () => {
    if (!subscription?.subscriptionId) return;

    cancelSubscription.mutate(subscription.subscriptionId, {
      onSuccess: (updatedSubscription) => {
        setShowCancelModal(false);
        alert(
          `Subscription canceled. Access continues until ${format(new Date(updatedSubscription.currentPeriodEnd), 'MMMM d, yyyy')}`
        );
      },
      onError: (error) => {
        alert(`Failed to cancel subscription: ${error.message}`);
      },
    });
  };

  if (!user) {
    navigate('/login');
    return null;
  }

  if (subscriptionLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="h-12 w-12 animate-spin rounded-full border-4 border-gray-300 border-t-blue-600 dark:border-gray-600 dark:border-t-blue-400" />
      </div>
    );
  }

  const isCanceled = subscription?.status === 'CANCELED' || !!subscription?.canceledAt;
  const isFree = user.subscriptionTier === 'FREE';

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900 py-8">
      <div className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8">
        <h1 className="mb-8 text-3xl font-bold text-gray-900 dark:text-white">
          Subscription & Billing
        </h1>

        {/* Current subscription card */}
        <div className="mb-8 rounded-lg bg-white p-6 shadow dark:bg-gray-800">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-4">
              <CreditCardIcon className="h-8 w-8 text-blue-600 dark:text-blue-400" />
              <div>
                <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
                  Current Plan
                </h2>
                <div className="mt-2 flex items-center gap-2">
                  <span
                    className={`inline-flex items-center rounded-full px-3 py-1 text-sm font-medium ${getTierBadgeClasses(user.subscriptionTier)}`}
                  >
                    {formatTierName(user.subscriptionTier)}
                  </span>
                  {subscription && (
                    <span
                      className={`inline-flex items-center rounded-full px-3 py-1 text-sm font-medium ${getStatusBadgeClasses(subscription.status)}`}
                    >
                      {formatSubscriptionStatus(subscription.status)}
                    </span>
                  )}
                </div>
              </div>
            </div>

            {!isFree && (
              <button
                onClick={() => navigate('/pricing')}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600"
              >
                Upgrade Plan
              </button>
            )}
          </div>

          {/* Billing period info */}
          {subscription && !isFree && (
            <div className="mt-6 flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
              <CalendarIcon className="h-5 w-5" />
              {isCanceled ? (
                <span>
                  Access ends on{' '}
                  <span className="font-semibold text-gray-900 dark:text-white">
                    {format(new Date(subscription.currentPeriodEnd), 'MMMM d, yyyy')}
                  </span>
                </span>
              ) : (
                <span>
                  Renews on{' '}
                  <span className="font-semibold text-gray-900 dark:text-white">
                    {format(new Date(subscription.currentPeriodEnd), 'MMMM d, yyyy')}
                  </span>
                </span>
              )}
            </div>
          )}

          {/* Cancel button */}
          {!isFree && !isCanceled && subscription && (
            <div className="mt-6 border-t border-gray-200 pt-6 dark:border-gray-700">
              <button
                onClick={() => setShowCancelModal(true)}
                className="text-sm font-medium text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
              >
                Cancel Subscription
              </button>
            </div>
          )}
        </div>

        {/* Payment history */}
        <div className="rounded-lg bg-white p-6 shadow dark:bg-gray-800">
          <h2 className="mb-6 text-xl font-semibold text-gray-900 dark:text-white">
            Payment History
          </h2>

          <PaymentHistoryTable
            invoices={invoicesData?.invoices || []}
            page={page}
            totalPages={invoicesData?.totalPages || 0}
            onPageChange={setPage}
            isLoading={invoicesLoading}
          />
        </div>
      </div>

      {/* Cancel confirmation modal */}
      {subscription && (
        <CancelConfirmationModal
          isOpen={showCancelModal}
          onClose={() => setShowCancelModal(false)}
          onConfirm={handleCancelSubscription}
          isLoading={cancelSubscription.isPending}
          currentPeriodEnd={subscription.currentPeriodEnd}
        />
      )}
    </div>
  );
};
