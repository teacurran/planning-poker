/**
 * Session Detail Page component.
 * Displays detailed session report with tier-based content.
 *
 * - Free tier: Summary card only
 * - Pro/Enterprise tier: Full details with round breakdown, consistency chart, and export
 */

import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeftIcon } from '@heroicons/react/24/outline';
import { useSessionDetail } from '@/services/reportingApi';
import { useAuthStore } from '@/stores/authStore';
import { UpgradeModal } from '@/components/subscription/UpgradeModal';
import SessionSummaryCard from '@/components/reporting/SessionSummaryCard';
import RoundBreakdownTable from '@/components/reporting/RoundBreakdownTable';
import UserConsistencyChart from '@/components/reporting/UserConsistencyChart';
import ExportControls from '@/components/reporting/ExportControls';

export default function SessionDetailPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const { user } = useAuthStore();
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);

  // Fetch session detail
  const {
    data: session,
    isLoading,
    error,
    refetch,
  } = useSessionDetail(sessionId || '');

  // Handle 403 errors (Free tier accessing Pro features)
  useEffect(() => {
    if (error && (error as any)?.response?.status === 403) {
      setShowUpgradeModal(true);
    }
  }, [error]);

  // Check if user has Pro tier access
  const isPro =
    user?.subscriptionTier === 'PRO' ||
    user?.subscriptionTier === 'PRO_PLUS' ||
    user?.subscriptionTier === 'ENTERPRISE';

  // Format date for display
  const formatDate = (isoDate: string) => {
    return new Date(isoDate).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  // Loading state with skeleton
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8 max-w-7xl">
          {/* Header skeleton */}
          <div className="mb-8">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-8 w-32 rounded mb-4"></div>
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-9 w-96 rounded mb-2"></div>
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-5 w-64 rounded"></div>
          </div>

          {/* Summary card skeleton */}
          <div className="mb-6 bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-6 w-48 rounded mb-4"></div>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="animate-pulse bg-gray-300 dark:bg-gray-700 h-16 rounded"></div>
              ))}
            </div>
          </div>

          {/* Chart skeleton */}
          <div className="mb-6 bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-6 w-48 rounded mb-4"></div>
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-64 rounded"></div>
          </div>

          {/* Table skeleton */}
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-6 w-48 rounded mb-4"></div>
            {[1, 2, 3].map((i) => (
              <div key={i} className="mb-4">
                <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-12 rounded"></div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // Error state (non-403 errors)
  if (error && !showUpgradeModal) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8 max-w-7xl">
          {/* Back button */}
          <Link
            to="/reports/sessions"
            className="inline-flex items-center text-sm font-medium text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 mb-6"
          >
            <ArrowLeftIcon className="h-4 w-4 mr-2" />
            Back to Session History
          </Link>

          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-6 max-w-2xl mx-auto">
            <div className="flex items-start">
              {/* Error icon */}
              <svg
                className="w-6 h-6 text-red-600 dark:text-red-400 mr-3 flex-shrink-0 mt-0.5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
              <div className="flex-1">
                <h3 className="text-lg font-semibold text-red-800 dark:text-red-200 mb-1">
                  Failed to load session details
                </h3>
                <p className="text-sm text-red-700 dark:text-red-300 mb-4">
                  {error.message || 'An unexpected error occurred while loading this session'}
                </p>
                <button
                  onClick={() => refetch()}
                  className="bg-red-600 hover:bg-red-700 text-white font-medium py-2 px-4 rounded transition-colors duration-200"
                >
                  Retry
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // No data state
  if (!session) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8 max-w-7xl">
          <Link
            to="/reports/sessions"
            className="inline-flex items-center text-sm font-medium text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 mb-6"
          >
            <ArrowLeftIcon className="h-4 w-4 mr-2" />
            Back to Session History
          </Link>

          <div className="text-center py-12">
            <p className="text-gray-500 dark:text-gray-400">Session not found</p>
          </div>
        </div>
      </div>
    );
  }

  // Success state - display data
  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="container mx-auto px-4 py-8 max-w-7xl">
        {/* Back button */}
        <Link
          to="/reports/sessions"
          className="inline-flex items-center text-sm font-medium text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 mb-6"
        >
          <ArrowLeftIcon className="h-4 w-4 mr-2" />
          Back to Session History
        </Link>

        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
            {session.room_title}
          </h1>
          <p className="text-sm text-gray-600 dark:text-gray-400">
            {formatDate(session.started_at)} - {formatDate(session.ended_at)}
          </p>
        </div>

        {/* Summary Card - Always shown (Free + Pro) */}
        <div className="mb-6">
          <SessionSummaryCard session={session} />
        </div>

        {/* Pro Tier Content */}
        {isPro && session.rounds && session.user_consistency_map && (
          <>
            {/* Round Breakdown Table */}
            <div className="mb-6">
              <RoundBreakdownTable rounds={session.rounds} />
            </div>

            {/* User Consistency Chart */}
            {Object.keys(session.user_consistency_map).length > 0 && (
              <div className="mb-6">
                <UserConsistencyChart userConsistencyMap={session.user_consistency_map} />
              </div>
            )}

            {/* Export Controls */}
            <div className="mb-6">
              <ExportControls sessionId={session.session_id} />
            </div>
          </>
        )}

        {/* Free Tier - Show upgrade CTA */}
        {!isPro && (
          <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-6">
            <div className="flex items-start">
              <svg
                className="w-6 h-6 text-blue-600 dark:text-blue-400 mr-3 flex-shrink-0 mt-0.5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
              <div className="flex-1">
                <h3 className="text-lg font-semibold text-blue-800 dark:text-blue-200 mb-1">
                  Upgrade to Pro for Detailed Analytics
                </h3>
                <p className="text-sm text-blue-700 dark:text-blue-300 mb-4">
                  Get access to round-by-round breakdowns, user consistency metrics, and export
                  functionality with a Pro subscription.
                </p>
                <button
                  onClick={() => setShowUpgradeModal(true)}
                  className="bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded transition-colors duration-200"
                >
                  Upgrade to Pro
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Upgrade Modal */}
        <UpgradeModal
          isOpen={showUpgradeModal}
          onClose={() => setShowUpgradeModal(false)}
          requiredTier="PRO"
          currentTier={user?.subscriptionTier || 'FREE'}
          feature="detailed session reports"
        />
      </div>
    </div>
  );
}
