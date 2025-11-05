/**
 * Audit log page component.
 * Displays filterable and paginated audit log entries for compliance tracking.
 */

import React, { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  BuildingOfficeIcon,
  DocumentTextIcon,
  FunnelIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  XCircleIcon,
  ArrowLeftIcon,
} from '@heroicons/react/24/outline';
import { useOrganization, useAuditLogs } from '@/services/organizationApi';
import { useAuthStore } from '@/stores/authStore';
import { format } from 'date-fns';
import type { AuditLogFilters } from '@/types/organization';

const AuditLogPage: React.FC = () => {
  const { orgId } = useParams<{ orgId: string }>();
  const navigate = useNavigate();
  const { user } = useAuthStore();

  // Fetch organization data
  const { data: organization, isLoading: orgLoading, error: orgError } = useOrganization(orgId || '');

  // Filter and pagination state
  const [filters, setFilters] = useState<AuditLogFilters>({
    page: 0,
    size: 20,
  });

  // Temporary filter state (for form inputs before applying)
  const [tempFromDate, setTempFromDate] = useState('');
  const [tempToDate, setTempToDate] = useState('');
  const [tempAction, setTempAction] = useState('');

  // Fetch audit logs
  const { data: auditData, isLoading: logsLoading, error: logsError } = useAuditLogs(
    orgId || '',
    filters
  );

  // Check if user has Enterprise tier
  const hasEnterpriseTier = user?.subscriptionTier === 'ENTERPRISE';

  // Apply filters
  const handleApplyFilters = () => {
    setFilters({
      ...filters,
      from: tempFromDate ? new Date(tempFromDate).toISOString() : undefined,
      to: tempToDate ? new Date(tempToDate).toISOString() : undefined,
      action: tempAction || undefined,
      page: 0, // Reset to first page when filters change
    });
  };

  // Clear filters
  const handleClearFilters = () => {
    setTempFromDate('');
    setTempToDate('');
    setTempAction('');
    setFilters({
      page: 0,
      size: 20,
    });
  };

  // Pagination handlers
  const handlePreviousPage = () => {
    if (filters.page && filters.page > 0) {
      setFilters({ ...filters, page: filters.page - 1 });
    }
  };

  const handleNextPage = () => {
    if (auditData && filters.page !== undefined && filters.page < auditData.totalPages - 1) {
      setFilters({ ...filters, page: filters.page + 1 });
    }
  };

  const handlePageChange = (page: number) => {
    setFilters({ ...filters, page });
  };

  // Loading state
  if (orgLoading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-9 w-64 rounded mb-8"></div>
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-6 w-3/4 rounded mb-4"></div>
          </div>
        </div>
      </div>
    );
  }

  // Error state
  if (orgError) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-6 max-w-2xl mx-auto">
            <XCircleIcon className="w-6 h-6 text-red-600 dark:text-red-400 mb-2" />
            <h3 className="text-lg font-semibold text-red-800 dark:text-red-200">
              Failed to load organization
            </h3>
            <p className="text-sm text-red-700 dark:text-red-300">{orgError.message}</p>
          </div>
        </div>
      </div>
    );
  }

  // Check if user lacks Enterprise tier
  if (!hasEnterpriseTier) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-6 max-w-2xl mx-auto">
            <h3 className="text-lg font-semibold text-yellow-800 dark:text-yellow-200">
              Enterprise Tier Required
            </h3>
            <p className="text-sm text-yellow-700 dark:text-yellow-300">
              Audit logs require an Enterprise subscription.
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (!organization) {
    return null;
  }

  const logs = auditData?.logs || [];
  const currentPage = filters.page || 0;
  const totalPages = auditData?.totalPages || 0;
  const totalElements = auditData?.totalElements || 0;

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="container mx-auto px-4 py-8">
        {/* Header */}
        <div className="mb-8">
          <button
            onClick={() => navigate(`/org/${orgId}/settings`)}
            className="flex items-center text-sm text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300 mb-4"
          >
            <ArrowLeftIcon className="h-4 w-4 mr-1" />
            Back to Settings
          </button>

          <div className="flex items-center mb-4">
            <DocumentTextIcon className="h-8 w-8 text-blue-600 dark:text-blue-400 mr-3" />
            <div>
              <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
                Audit Logs
              </h1>
              <p className="text-gray-600 dark:text-gray-300">
                Track activity and changes for {organization.name}
              </p>
            </div>
          </div>

          {/* Navigation tabs */}
          <nav className="flex space-x-4 border-b border-gray-200 dark:border-gray-700">
            <Link
              to={`/org/${orgId}/settings`}
              className="border-b-2 border-transparent pb-2 text-sm font-medium text-gray-600 hover:border-gray-300 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300"
            >
              Settings
            </Link>
            <Link
              to={`/org/${orgId}/members`}
              className="border-b-2 border-transparent pb-2 text-sm font-medium text-gray-600 hover:border-gray-300 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300"
            >
              Members
            </Link>
            <Link
              to={`/org/${orgId}/sso`}
              className="border-b-2 border-transparent pb-2 text-sm font-medium text-gray-600 hover:border-gray-300 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300"
            >
              SSO Configuration
            </Link>
            <Link
              to={`/org/${orgId}/audit-logs`}
              className="border-b-2 border-blue-600 pb-2 text-sm font-medium text-blue-600 dark:text-blue-400"
            >
              Audit Logs
            </Link>
          </nav>
        </div>

        {/* Filters */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-4 mb-6">
          <div className="flex items-center mb-4">
            <FunnelIcon className="h-5 w-5 text-gray-500 dark:text-gray-400 mr-2" />
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Filters</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {/* From Date */}
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                From Date
              </label>
              <input
                type="date"
                value={tempFromDate}
                onChange={(e) => setTempFromDate(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
              />
            </div>

            {/* To Date */}
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                To Date
              </label>
              <input
                type="date"
                value={tempToDate}
                onChange={(e) => setTempToDate(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
              />
            </div>

            {/* Action Filter */}
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Action Type
              </label>
              <input
                type="text"
                value={tempAction}
                onChange={(e) => setTempAction(e.target.value)}
                placeholder="e.g., user.login"
                className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
              />
            </div>

            {/* Filter Actions */}
            <div className="flex items-end space-x-2">
              <button
                onClick={handleApplyFilters}
                className="flex-1 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                Apply
              </button>
              <button
                onClick={handleClearFilters}
                className="px-4 py-2 bg-gray-200 text-gray-700 text-sm font-medium rounded-md hover:bg-gray-300 focus:outline-none focus:ring-2 focus:ring-gray-500 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
              >
                Clear
              </button>
            </div>
          </div>
        </div>

        {/* Error loading logs */}
        {logsError && (
          <div className="mb-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
            <div className="flex items-center">
              <XCircleIcon className="h-5 w-5 text-red-600 dark:text-red-400 mr-2" />
              <p className="text-sm text-red-800 dark:text-red-200">
                {logsError.message || 'Failed to load audit logs'}
              </p>
            </div>
          </div>
        )}

        {/* Audit Log Table */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md overflow-hidden">
          {logsLoading ? (
            <div className="flex items-center justify-center py-12">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-gray-300 border-t-blue-600 dark:border-gray-600 dark:border-t-blue-400" />
            </div>
          ) : logs.length === 0 ? (
            <div className="py-12 text-center">
              <DocumentTextIcon className="mx-auto h-12 w-12 text-gray-400 dark:text-gray-600" />
              <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
                No audit logs found
              </p>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-500">
                Try adjusting your filters or check back later
              </p>
            </div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                  <thead className="bg-gray-50 dark:bg-gray-900">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                        Timestamp
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                        User
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                        Action
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                        Resource
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                        IP Address
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200 bg-white dark:divide-gray-700 dark:bg-gray-800">
                    {logs.map((log) => (
                      <tr key={log.logId} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                        {/* Timestamp */}
                        <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900 dark:text-gray-300">
                          {format(new Date(log.timestamp), 'MMM d, yyyy HH:mm:ss')}
                        </td>

                        {/* User */}
                        <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900 dark:text-gray-300">
                          {log.userId || <span className="text-gray-400">System</span>}
                        </td>

                        {/* Action */}
                        <td className="whitespace-nowrap px-6 py-4">
                          <span className="inline-flex rounded-full bg-blue-100 px-2 py-1 text-xs font-semibold text-blue-800 dark:bg-blue-900 dark:text-blue-200">
                            {log.action}
                          </span>
                        </td>

                        {/* Resource */}
                        <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-900 dark:text-gray-300">
                          {log.resourceType}
                          {log.resourceId && (
                            <span className="ml-1 text-gray-500 dark:text-gray-400">
                              ({log.resourceId.substring(0, 8)}...)
                            </span>
                          )}
                        </td>

                        {/* IP Address */}
                        <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500 dark:text-gray-400">
                          {log.ipAddress || '-'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="border-t border-gray-200 bg-white px-4 py-3 dark:border-gray-700 dark:bg-gray-800">
                  <div className="flex items-center justify-between">
                    {/* Results info */}
                    <div className="text-sm text-gray-700 dark:text-gray-300">
                      Showing{' '}
                      <span className="font-medium">{currentPage * (filters.size || 20) + 1}</span>{' '}
                      to{' '}
                      <span className="font-medium">
                        {Math.min((currentPage + 1) * (filters.size || 20), totalElements)}
                      </span>{' '}
                      of <span className="font-medium">{totalElements}</span> results
                    </div>

                    {/* Pagination controls */}
                    <div className="flex items-center space-x-2">
                      <button
                        onClick={handlePreviousPage}
                        disabled={currentPage === 0}
                        className="inline-flex items-center rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                      >
                        <ChevronLeftIcon className="h-4 w-4" />
                      </button>

                      {/* Page numbers */}
                      <div className="hidden md:flex space-x-1">
                        {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
                          const pageNum = i;
                          return (
                            <button
                              key={pageNum}
                              onClick={() => handlePageChange(pageNum)}
                              className={`px-3 py-2 text-sm font-medium rounded-md ${
                                currentPage === pageNum
                                  ? 'bg-blue-600 text-white'
                                  : 'bg-white text-gray-700 hover:bg-gray-50 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600'
                              }`}
                            >
                              {pageNum + 1}
                            </button>
                          );
                        })}
                      </div>

                      <button
                        onClick={handleNextPage}
                        disabled={currentPage >= totalPages - 1}
                        className="inline-flex items-center rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                      >
                        <ChevronRightIcon className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default AuditLogPage;
