/**
 * Organization settings page component.
 * Displays organization details, branding, SSO status, and navigation to other admin pages.
 */

import React from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  BuildingOfficeIcon,
  ShieldCheckIcon,
  UserGroupIcon,
  DocumentTextIcon,
  CogIcon,
  CheckCircleIcon,
  XCircleIcon,
} from '@heroicons/react/24/outline';
import { useOrganization } from '@/services/organizationApi';
import { useAuthStore } from '@/stores/authStore';
import { format } from 'date-fns';

const OrganizationSettingsPage: React.FC = () => {
  const { orgId } = useParams<{ orgId: string }>();
  const navigate = useNavigate();
  const { user } = useAuthStore();

  // Fetch organization data
  const {
    data: organization,
    isLoading,
    error,
    refetch,
  } = useOrganization(orgId || '');

  // Check if user has Enterprise tier
  const hasEnterpriseTier = user?.subscriptionTier === 'ENTERPRISE';

  // Loading state
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          {/* Header skeleton */}
          <div className="mb-8">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-9 w-64 rounded mb-2"></div>
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-5 w-96 rounded"></div>
          </div>

          {/* Cards skeleton */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {[1, 2, 3].map((i) => (
              <div key={i} className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-6 w-3/4 rounded mb-4"></div>
                <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-4 w-full rounded mb-2"></div>
                <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-4 w-2/3 rounded"></div>
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
        <div className="container mx-auto px-4 py-8">
          <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-6 max-w-2xl mx-auto">
            <div className="flex items-start">
              <XCircleIcon className="w-6 h-6 text-red-600 dark:text-red-400 mr-3 flex-shrink-0 mt-0.5" />
              <div className="flex-1">
                <h3 className="text-lg font-semibold text-red-800 dark:text-red-200 mb-1">
                  Failed to load organization
                </h3>
                <p className="text-sm text-red-700 dark:text-red-300 mb-4">
                  {error.message || 'An unexpected error occurred'}
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

  // Check if user lacks Enterprise tier
  if (!hasEnterpriseTier) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-6 max-w-2xl mx-auto">
            <div className="flex items-start">
              <ShieldCheckIcon className="w-6 h-6 text-yellow-600 dark:text-yellow-400 mr-3 flex-shrink-0 mt-0.5" />
              <div className="flex-1">
                <h3 className="text-lg font-semibold text-yellow-800 dark:text-yellow-200 mb-1">
                  Enterprise Tier Required
                </h3>
                <p className="text-sm text-yellow-700 dark:text-yellow-300 mb-4">
                  Organization administration requires an Enterprise subscription.
                </p>
                <button
                  onClick={() => navigate('/pricing')}
                  className="bg-yellow-600 hover:bg-yellow-700 text-white font-medium py-2 px-4 rounded transition-colors duration-200"
                >
                  View Pricing
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (!organization) {
    return null;
  }

  const ssoConfigured = !!organization.ssoConfig;
  const hasBranding = !!organization.branding;

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <div className="container mx-auto px-4 py-8">
        {/* Header */}
        <div className="mb-8">
          <div className="flex items-center mb-4">
            <BuildingOfficeIcon className="h-8 w-8 text-blue-600 dark:text-blue-400 mr-3" />
            <div>
              <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
                {organization.name}
              </h1>
              <p className="text-gray-600 dark:text-gray-300">
                Organization Administration
              </p>
            </div>
          </div>

          {/* Navigation tabs */}
          <nav className="flex space-x-4 border-b border-gray-200 dark:border-gray-700">
            <Link
              to={`/org/${orgId}/settings`}
              className="border-b-2 border-blue-600 pb-2 text-sm font-medium text-blue-600 dark:text-blue-400"
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
              className="border-b-2 border-transparent pb-2 text-sm font-medium text-gray-600 hover:border-gray-300 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-300"
            >
              Audit Logs
            </Link>
          </nav>
        </div>

        {/* Organization Details Card */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
          {/* Basic Info */}
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="flex items-center mb-4">
              <BuildingOfficeIcon className="h-6 w-6 text-gray-500 dark:text-gray-400 mr-2" />
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                Organization Details
              </h2>
            </div>
            <div className="space-y-3">
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">Name</p>
                <p className="text-sm font-medium text-gray-900 dark:text-white">
                  {organization.name}
                </p>
              </div>
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">Domain</p>
                <p className="text-sm font-medium text-gray-900 dark:text-white">
                  {organization.domain}
                </p>
              </div>
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">Created</p>
                <p className="text-sm font-medium text-gray-900 dark:text-white">
                  {format(new Date(organization.createdAt), 'MMMM d, yyyy')}
                </p>
              </div>
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">Members</p>
                <p className="text-sm font-medium text-gray-900 dark:text-white">
                  <span className="inline-flex items-center rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-medium text-blue-800 dark:bg-blue-900 dark:text-blue-200">
                    {organization.memberCount} {organization.memberCount === 1 ? 'member' : 'members'}
                  </span>
                </p>
              </div>
            </div>
          </div>

          {/* SSO Status Card */}
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="flex items-center mb-4">
              <ShieldCheckIcon className="h-6 w-6 text-gray-500 dark:text-gray-400 mr-2" />
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                SSO Status
              </h2>
            </div>
            <div className="space-y-3">
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400 mb-2">Configuration Status</p>
                {ssoConfigured ? (
                  <div className="flex items-center">
                    <CheckCircleIcon className="h-5 w-5 text-green-500 mr-2" />
                    <span className="text-sm font-medium text-green-600 dark:text-green-400">
                      Configured
                    </span>
                  </div>
                ) : (
                  <div className="flex items-center">
                    <XCircleIcon className="h-5 w-5 text-gray-400 mr-2" />
                    <span className="text-sm font-medium text-gray-600 dark:text-gray-400">
                      Not Configured
                    </span>
                  </div>
                )}
              </div>
              {ssoConfigured && organization.ssoConfig && (
                <div>
                  <p className="text-sm text-gray-500 dark:text-gray-400">Protocol</p>
                  <p className="text-sm font-medium text-gray-900 dark:text-white">
                    {organization.ssoConfig.protocol}
                  </p>
                </div>
              )}
              <button
                onClick={() => navigate(`/org/${orgId}/sso`)}
                className="mt-4 w-full inline-flex justify-center items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
              >
                <CogIcon className="h-4 w-4 mr-2" />
                Configure SSO
              </button>
            </div>
          </div>

          {/* Branding Card */}
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="flex items-center mb-4">
              <UserGroupIcon className="h-6 w-6 text-gray-500 dark:text-gray-400 mr-2" />
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                Branding
              </h2>
            </div>
            {hasBranding && organization.branding ? (
              <div className="space-y-3">
                {organization.branding.logoUrl && (
                  <div>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mb-2">Logo</p>
                    <img
                      src={organization.branding.logoUrl}
                      alt="Organization logo"
                      className="h-12 w-auto object-contain"
                    />
                  </div>
                )}
                {organization.branding.primaryColor && (
                  <div>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mb-2">Primary Color</p>
                    <div className="flex items-center">
                      <div
                        className="h-6 w-6 rounded border border-gray-300 dark:border-gray-600 mr-2"
                        style={{ backgroundColor: organization.branding.primaryColor }}
                      />
                      <span className="text-sm font-mono text-gray-900 dark:text-white">
                        {organization.branding.primaryColor}
                      </span>
                    </div>
                  </div>
                )}
                {organization.branding.secondaryColor && (
                  <div>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mb-2">Secondary Color</p>
                    <div className="flex items-center">
                      <div
                        className="h-6 w-6 rounded border border-gray-300 dark:border-gray-600 mr-2"
                        style={{ backgroundColor: organization.branding.secondaryColor }}
                      />
                      <span className="text-sm font-mono text-gray-900 dark:text-white">
                        {organization.branding.secondaryColor}
                      </span>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="text-center py-4">
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  No branding customization configured
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <button
            onClick={() => navigate(`/org/${orgId}/members`)}
            className="flex items-center justify-center px-6 py-4 bg-white dark:bg-gray-800 rounded-lg shadow-md hover:shadow-lg transition-shadow"
          >
            <UserGroupIcon className="h-6 w-6 text-blue-600 dark:text-blue-400 mr-3" />
            <div className="text-left">
              <p className="text-sm font-medium text-gray-900 dark:text-white">Manage Members</p>
              <p className="text-xs text-gray-500 dark:text-gray-400">Invite and remove members</p>
            </div>
          </button>

          <button
            onClick={() => navigate(`/org/${orgId}/sso`)}
            className="flex items-center justify-center px-6 py-4 bg-white dark:bg-gray-800 rounded-lg shadow-md hover:shadow-lg transition-shadow"
          >
            <ShieldCheckIcon className="h-6 w-6 text-blue-600 dark:text-blue-400 mr-3" />
            <div className="text-left">
              <p className="text-sm font-medium text-gray-900 dark:text-white">Configure SSO</p>
              <p className="text-xs text-gray-500 dark:text-gray-400">Set up OIDC or SAML2</p>
            </div>
          </button>

          <button
            onClick={() => navigate(`/org/${orgId}/audit-logs`)}
            className="flex items-center justify-center px-6 py-4 bg-white dark:bg-gray-800 rounded-lg shadow-md hover:shadow-lg transition-shadow"
          >
            <DocumentTextIcon className="h-6 w-6 text-blue-600 dark:text-blue-400 mr-3" />
            <div className="text-left">
              <p className="text-sm font-medium text-gray-900 dark:text-white">View Audit Logs</p>
              <p className="text-xs text-gray-500 dark:text-gray-400">Track org activity</p>
            </div>
          </button>
        </div>
      </div>
    </div>
  );
};

export default OrganizationSettingsPage;
