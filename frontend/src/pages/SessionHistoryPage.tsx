/**
 * Session History Page component.
 * Displays user's past planning poker sessions with filtering and pagination.
 */

import React, { useState } from 'react';
import { useSessions } from '@/services/reportingApi';
import { SessionListTable } from '@/components/reporting/SessionListTable';
import { PaginationControls } from '@/components/reporting/PaginationControls';
import type { SessionsQueryParams } from '@/types/reporting';

const SessionHistoryPage: React.FC = () => {
  // State for filters and pagination
  const [page, setPage] = useState(0);
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [sortOrder, setSortOrder] = useState<'newest' | 'oldest'>('newest');

  // Build query params
  const queryParams: SessionsQueryParams = {
    page,
    size: 20,
    from: dateFrom || undefined,
    to: dateTo || undefined,
  };

  // Fetch sessions data
  const {
    data: sessionsData,
    isLoading,
    error,
    refetch,
  } = useSessions(queryParams);

  // Handle page change
  const handlePageChange = (newPage: number) => {
    setPage(newPage);
    // Scroll to top on page change
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  // Handle filter change
  const handleFilterChange = () => {
    // Reset to first page when filters change
    setPage(0);
  };

  // Handle sort order change
  const handleSortOrderChange = (order: 'newest' | 'oldest') => {
    setSortOrder(order);
    setPage(0);
  };

  // Handle clear filters
  const handleClearFilters = () => {
    setDateFrom('');
    setDateTo('');
    setPage(0);
  };

  // Sort sessions client-side (if backend doesn't support sorting)
  const sortedSessions = React.useMemo(() => {
    if (!sessionsData?.sessions) return [];

    const sessions = [...sessionsData.sessions];

    if (sortOrder === 'oldest') {
      return sessions.reverse();
    }

    return sessions;
  }, [sessionsData?.sessions, sortOrder]);

  // Loading state with skeleton
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8 max-w-7xl">
          {/* Header skeleton */}
          <div className="mb-8">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-9 w-56 rounded mb-2"></div>
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-5 w-96 rounded"></div>
          </div>

          {/* Filters skeleton */}
          <div className="mb-6 bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-10 rounded"></div>
              <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-10 rounded"></div>
              <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-10 rounded"></div>
              <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-10 rounded"></div>
            </div>
          </div>

          {/* Table skeleton */}
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="mb-4">
                <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-16 rounded"></div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // Error state
  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8 max-w-7xl">
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
                  Failed to load session history
                </h3>
                <p className="text-sm text-red-700 dark:text-red-300 mb-4">
                  {error.message || 'An unexpected error occurred while loading your sessions'}
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

  // Success state - display data
  const hasFilters = dateFrom || dateTo;

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="container mx-auto px-4 py-8 max-w-7xl">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
            Session History
          </h1>
          <p className="text-gray-600 dark:text-gray-300">
            View and analyze your past planning poker sessions
          </p>
        </div>

        {/* Filters */}
        <div className="mb-6 bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {/* Date from filter */}
            <div>
              <label
                htmlFor="date-from"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2"
              >
                From Date
              </label>
              <input
                type="date"
                id="date-from"
                value={dateFrom}
                onChange={(e) => {
                  setDateFrom(e.target.value);
                  handleFilterChange();
                }}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-primary-500 focus:border-primary-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
              />
            </div>

            {/* Date to filter */}
            <div>
              <label
                htmlFor="date-to"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2"
              >
                To Date
              </label>
              <input
                type="date"
                id="date-to"
                value={dateTo}
                onChange={(e) => {
                  setDateTo(e.target.value);
                  handleFilterChange();
                }}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-primary-500 focus:border-primary-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
              />
            </div>

            {/* Sort order */}
            <div>
              <label
                htmlFor="sort-order"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2"
              >
                Sort By
              </label>
              <select
                id="sort-order"
                value={sortOrder}
                onChange={(e) => handleSortOrderChange(e.target.value as 'newest' | 'oldest')}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-primary-500 focus:border-primary-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
              >
                <option value="newest">Newest First</option>
                <option value="oldest">Oldest First</option>
              </select>
            </div>

            {/* Clear filters button */}
            <div className="flex items-end">
              <button
                onClick={handleClearFilters}
                disabled={!hasFilters}
                className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm text-sm font-medium text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200"
              >
                Clear Filters
              </button>
            </div>
          </div>

          {/* Active filters display */}
          {hasFilters && (
            <div className="mt-4 flex flex-wrap gap-2">
              <span className="text-sm text-gray-600 dark:text-gray-400">Active filters:</span>
              {dateFrom && (
                <span className="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-primary-100 text-primary-800 dark:bg-primary-900 dark:text-primary-300">
                  From: {dateFrom}
                  <button
                    onClick={() => {
                      setDateFrom('');
                      handleFilterChange();
                    }}
                    className="ml-2 text-primary-600 dark:text-primary-400 hover:text-primary-800 dark:hover:text-primary-200"
                    aria-label="Remove from date filter"
                  >
                    ×
                  </button>
                </span>
              )}
              {dateTo && (
                <span className="inline-flex items-center px-3 py-1 rounded-full text-sm font-medium bg-primary-100 text-primary-800 dark:bg-primary-900 dark:text-primary-300">
                  To: {dateTo}
                  <button
                    onClick={() => {
                      setDateTo('');
                      handleFilterChange();
                    }}
                    className="ml-2 text-primary-600 dark:text-primary-400 hover:text-primary-800 dark:hover:text-primary-200"
                    aria-label="Remove to date filter"
                  >
                    ×
                  </button>
                </span>
              )}
            </div>
          )}
        </div>

        {/* Session list table */}
        <SessionListTable sessions={sortedSessions} isLoading={isLoading} />

        {/* Pagination controls */}
        {sessionsData && sessionsData.total > 0 && (
          <div className="mt-6">
            <PaginationControls
              currentPage={page}
              pageSize={queryParams.size || 20}
              totalCount={sessionsData.total}
              hasNext={sessionsData.has_next}
              onPageChange={handlePageChange}
              disabled={isLoading}
            />
          </div>
        )}
      </div>
    </div>
  );
};

export default SessionHistoryPage;
