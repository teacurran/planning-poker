/**
 * SSO configuration page component.
 * Form for configuring OIDC or SAML2 single sign-on settings.
 */

import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  ShieldCheckIcon,
  CheckCircleIcon,
  XCircleIcon,
  ArrowLeftIcon,
} from '@heroicons/react/24/outline';
import { useOrganization, useUpdateSsoConfig } from '@/services/organizationApi';
import { useAuthStore } from '@/stores/authStore';
import type { SsoProtocol, SsoConfigRequest } from '@/types/organization';

const SsoConfigPage: React.FC = () => {
  const { orgId } = useParams<{ orgId: string }>();
  const navigate = useNavigate();
  const { user } = useAuthStore();

  // Fetch organization data
  const { data: organization, isLoading, error } = useOrganization(orgId || '');

  // SSO update mutation
  const updateSso = useUpdateSsoConfig(orgId || '');

  // Form state
  const [protocol, setProtocol] = useState<SsoProtocol>('OIDC');
  const [formData, setFormData] = useState<Partial<SsoConfigRequest>>({
    protocol: 'OIDC',
  });
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});
  const [successMessage, setSuccessMessage] = useState<string>('');

  // Load existing SSO config into form
  useEffect(() => {
    if (organization?.ssoConfig) {
      const config = organization.ssoConfig;
      setProtocol(config.protocol);
      setFormData({
        protocol: config.protocol,
        issuer: config.issuer || '',
        clientId: config.clientId || '',
        authorizationEndpoint: config.authorizationEndpoint || '',
        tokenEndpoint: config.tokenEndpoint || '',
        jwksUri: config.jwksUri || '',
        samlEntityId: config.samlEntityId || '',
        samlSsoUrl: config.samlSsoUrl || '',
      });
    }
  }, [organization]);

  // Handle protocol change
  const handleProtocolChange = (newProtocol: SsoProtocol) => {
    setProtocol(newProtocol);
    setFormData({ ...formData, protocol: newProtocol });
    setValidationErrors({});
  };

  // Handle input change
  const handleInputChange = (field: keyof SsoConfigRequest, value: string) => {
    setFormData({ ...formData, [field]: value });
    // Clear validation error for this field
    if (validationErrors[field]) {
      setValidationErrors({ ...validationErrors, [field]: '' });
    }
  };

  // Handle file upload (SAML certificate)
  const handleCertificateUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = () => {
        const text = reader.result as string;
        setFormData({ ...formData, samlCertificate: text });
      };
      reader.readAsText(file);
    }
  };

  // Validate form
  const validateForm = (): boolean => {
    const errors: Record<string, string> = {};

    if (protocol === 'OIDC') {
      if (!formData.issuer) errors.issuer = 'Issuer URL is required';
      if (!formData.clientId) errors.clientId = 'Client ID is required';
    } else if (protocol === 'SAML2') {
      if (!formData.samlEntityId) errors.samlEntityId = 'Entity ID is required';
      if (!formData.samlSsoUrl) errors.samlSsoUrl = 'SSO URL is required';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  // Handle form submit
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSuccessMessage('');

    if (!validateForm()) {
      return;
    }

    updateSso.mutate(formData as SsoConfigRequest, {
      onSuccess: () => {
        setSuccessMessage('SSO configuration updated successfully');
        setTimeout(() => setSuccessMessage(''), 5000);
      },
      onError: (err: Error) => {
        const axiosError = err as { response?: { status?: number }; message?: string };
        if (axiosError.response?.status === 403) {
          setValidationErrors({
            submit: 'Only organization administrators can configure SSO',
          });
        } else {
          setValidationErrors({
            submit: axiosError.message || 'Failed to update SSO configuration',
          });
        }
      },
    });
  };

  // Test SSO configuration (client-side validation for MVP)
  const handleTestSso = () => {
    if (validateForm()) {
      alert('SSO configuration is valid. Save to apply changes.');
    } else {
      alert('Please fix validation errors before testing.');
    }
  };

  // Check if user has Enterprise tier
  const hasEnterpriseTier = user?.subscriptionTier === 'ENTERPRISE';

  // Loading state
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <div className="container mx-auto px-4 py-8">
          <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-9 w-64 rounded mb-8"></div>
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-6 w-3/4 rounded mb-4"></div>
            <div className="animate-pulse bg-gray-300 dark:bg-gray-700 h-4 w-full rounded mb-2"></div>
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
            <XCircleIcon className="w-6 h-6 text-red-600 dark:text-red-400 mb-2" />
            <h3 className="text-lg font-semibold text-red-800 dark:text-red-200">
              Failed to load organization
            </h3>
            <p className="text-sm text-red-700 dark:text-red-300">{error.message}</p>
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
              SSO configuration requires an Enterprise subscription.
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (!organization) {
    return null;
  }

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
            <ShieldCheckIcon className="h-8 w-8 text-blue-600 dark:text-blue-400 mr-3" />
            <div>
              <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
                SSO Configuration
              </h1>
              <p className="text-gray-600 dark:text-gray-300">
                Configure OIDC or SAML2 single sign-on for {organization.name}
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
              className="border-b-2 border-blue-600 pb-2 text-sm font-medium text-blue-600 dark:text-blue-400"
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

        {/* Success message */}
        {successMessage && (
          <div className="mb-6 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4">
            <div className="flex items-center">
              <CheckCircleIcon className="h-5 w-5 text-green-600 dark:text-green-400 mr-2" />
              <p className="text-sm text-green-800 dark:text-green-200">{successMessage}</p>
            </div>
          </div>
        )}

        {/* Error message */}
        {validationErrors.submit && (
          <div className="mb-6 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
            <div className="flex items-center">
              <XCircleIcon className="h-5 w-5 text-red-600 dark:text-red-400 mr-2" />
              <p className="text-sm text-red-800 dark:text-red-200">{validationErrors.submit}</p>
            </div>
          </div>
        )}

        {/* SSO Configuration Form */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
          <form onSubmit={handleSubmit}>
            {/* Protocol Selection */}
            <div className="mb-6">
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                SSO Protocol
              </label>
              <div className="flex space-x-4">
                <button
                  type="button"
                  onClick={() => handleProtocolChange('OIDC')}
                  className={`flex-1 px-4 py-3 border rounded-md text-sm font-medium transition-colors ${
                    protocol === 'OIDC'
                      ? 'border-blue-600 bg-blue-50 text-blue-700 dark:bg-blue-900 dark:text-blue-200'
                      : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300'
                  }`}
                >
                  OpenID Connect (OIDC)
                </button>
                <button
                  type="button"
                  onClick={() => handleProtocolChange('SAML2')}
                  className={`flex-1 px-4 py-3 border rounded-md text-sm font-medium transition-colors ${
                    protocol === 'SAML2'
                      ? 'border-blue-600 bg-blue-50 text-blue-700 dark:bg-blue-900 dark:text-blue-200'
                      : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300'
                  }`}
                >
                  SAML 2.0
                </button>
              </div>
            </div>

            {/* OIDC Fields */}
            {protocol === 'OIDC' && (
              <div className="space-y-4">
                {/* Issuer */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Issuer URL <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="url"
                    value={formData.issuer || ''}
                    onChange={(e) => handleInputChange('issuer', e.target.value)}
                    className={`w-full px-3 py-2 border rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white ${
                      validationErrors.issuer
                        ? 'border-red-500'
                        : 'border-gray-300 dark:border-gray-600'
                    }`}
                    placeholder="https://idp.example.com"
                  />
                  {validationErrors.issuer && (
                    <p className="mt-1 text-sm text-red-600 dark:text-red-400">
                      {validationErrors.issuer}
                    </p>
                  )}
                </div>

                {/* Client ID */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Client ID <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formData.clientId || ''}
                    onChange={(e) => handleInputChange('clientId', e.target.value)}
                    className={`w-full px-3 py-2 border rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white ${
                      validationErrors.clientId
                        ? 'border-red-500'
                        : 'border-gray-300 dark:border-gray-600'
                    }`}
                    placeholder="your-client-id"
                  />
                  {validationErrors.clientId && (
                    <p className="mt-1 text-sm text-red-600 dark:text-red-400">
                      {validationErrors.clientId}
                    </p>
                  )}
                </div>

                {/* Client Secret */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Client Secret
                  </label>
                  <input
                    type="password"
                    value={formData.clientSecret || ''}
                    onChange={(e) => handleInputChange('clientSecret', e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                    placeholder="Optional for public clients"
                  />
                </div>

                {/* Authorization Endpoint */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Authorization Endpoint
                  </label>
                  <input
                    type="url"
                    value={formData.authorizationEndpoint || ''}
                    onChange={(e) => handleInputChange('authorizationEndpoint', e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                    placeholder="https://idp.example.com/authorize"
                  />
                </div>

                {/* Token Endpoint */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Token Endpoint
                  </label>
                  <input
                    type="url"
                    value={formData.tokenEndpoint || ''}
                    onChange={(e) => handleInputChange('tokenEndpoint', e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                    placeholder="https://idp.example.com/token"
                  />
                </div>

                {/* JWKS URI */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    JWKS URI
                  </label>
                  <input
                    type="url"
                    value={formData.jwksUri || ''}
                    onChange={(e) => handleInputChange('jwksUri', e.target.value)}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                    placeholder="https://idp.example.com/.well-known/jwks.json"
                  />
                </div>
              </div>
            )}

            {/* SAML2 Fields */}
            {protocol === 'SAML2' && (
              <div className="space-y-4">
                {/* Entity ID */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    Entity ID <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formData.samlEntityId || ''}
                    onChange={(e) => handleInputChange('samlEntityId', e.target.value)}
                    className={`w-full px-3 py-2 border rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white ${
                      validationErrors.samlEntityId
                        ? 'border-red-500'
                        : 'border-gray-300 dark:border-gray-600'
                    }`}
                    placeholder="https://idp.example.com/entity"
                  />
                  {validationErrors.samlEntityId && (
                    <p className="mt-1 text-sm text-red-600 dark:text-red-400">
                      {validationErrors.samlEntityId}
                    </p>
                  )}
                </div>

                {/* SSO URL */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    SSO URL <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="url"
                    value={formData.samlSsoUrl || ''}
                    onChange={(e) => handleInputChange('samlSsoUrl', e.target.value)}
                    className={`w-full px-3 py-2 border rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white ${
                      validationErrors.samlSsoUrl
                        ? 'border-red-500'
                        : 'border-gray-300 dark:border-gray-600'
                    }`}
                    placeholder="https://idp.example.com/sso"
                  />
                  {validationErrors.samlSsoUrl && (
                    <p className="mt-1 text-sm text-red-600 dark:text-red-400">
                      {validationErrors.samlSsoUrl}
                    </p>
                  )}
                </div>

                {/* Certificate Upload */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    X.509 Certificate
                  </label>
                  <input
                    type="file"
                    accept=".pem,.cer,.crt"
                    onChange={handleCertificateUpload}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                  />
                  <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                    Upload the IdP signing certificate (PEM format)
                  </p>
                </div>
              </div>
            )}

            {/* Form Actions */}
            <div className="mt-8 flex space-x-4">
              <button
                type="submit"
                disabled={updateSso.isPending}
                className="flex-1 inline-flex justify-center items-center px-6 py-3 border border-transparent text-base font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {updateSso.isPending ? 'Saving...' : 'Save Configuration'}
              </button>
              <button
                type="button"
                onClick={handleTestSso}
                className="px-6 py-3 border border-gray-300 text-base font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-gray-300 dark:border-gray-600 dark:hover:bg-gray-600"
              >
                Test SSO
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};

export default SsoConfigPage;
