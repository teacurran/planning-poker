/**
 * OAuth2 callback page that handles the redirect from OAuth providers.
 * Extracts authorization code, exchanges it for tokens, and redirects to dashboard.
 */

import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { retrieveAndClearPKCESession } from '@/utils/pkce';
import type { OAuthCallbackRequest, TokenResponse, ErrorResponse } from '@/types/auth';

const OAuthCallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { setAuth } = useAuth();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleOAuthCallback = async () => {
      try {
        // Step 1: Check for OAuth error (user denied consent)
        const oauthError = searchParams.get('error');
        if (oauthError) {
          const errorDescription = searchParams.get('error_description') || 'Authentication failed';
          console.error('OAuth error:', oauthError, errorDescription);
          setError(`Authentication failed: ${errorDescription}`);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        // Step 2: Extract authorization code from URL
        const code = searchParams.get('code');
        if (!code) {
          setError('Authorization code not found in callback URL');
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        // Step 3: Retrieve PKCE session data from sessionStorage
        const pkceSession = retrieveAndClearPKCESession();
        if (!pkceSession) {
          setError('PKCE session data not found. Please try logging in again.');
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        // Step 4: Build request payload for token exchange
        const requestPayload: OAuthCallbackRequest = {
          code,
          provider: pkceSession.provider,
          redirectUri: pkceSession.redirectUri,
          codeVerifier: pkceSession.codeVerifier,
        };

        // Step 5: Call backend API to exchange code for tokens
        console.log(`Exchanging authorization code for ${pkceSession.provider} tokens...`);
        const response = await fetch('/api/v1/auth/oauth/callback', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(requestPayload),
        });

        // Step 6: Handle response
        if (!response.ok) {
          const errorData: ErrorResponse = await response.json();
          console.error('Token exchange failed:', errorData);
          setError(`Authentication failed: ${errorData.message}`);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        // Step 7: Parse token response
        const tokenResponse: TokenResponse = await response.json();
        console.log('Authentication successful:', {
          user: tokenResponse.user.email,
          tier: tokenResponse.user.subscriptionTier,
        });

        // Step 8: Store tokens and user data in auth store
        setAuth(tokenResponse);

        // Step 9: Redirect to dashboard
        navigate('/dashboard', { replace: true });
      } catch (err) {
        console.error('Unexpected error during OAuth callback:', err);
        setError('An unexpected error occurred. Please try again.');
        setTimeout(() => navigate('/login'), 3000);
      }
    };

    handleOAuthCallback();
  }, [searchParams, navigate, setAuth]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
      <div className="max-w-md w-full p-8 text-center">
        {error ? (
          // Error state
          <div className="space-y-4">
            <div className="w-16 h-16 mx-auto text-red-500">
              <svg
                className="w-full h-full"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                />
              </svg>
            </div>
            <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
              Authentication Failed
            </h2>
            <p className="text-gray-600 dark:text-gray-400">{error}</p>
            <p className="text-sm text-gray-500 dark:text-gray-500">
              Redirecting to login page...
            </p>
          </div>
        ) : (
          // Loading state
          <div className="space-y-4">
            <div className="w-16 h-16 mx-auto">
              <LoadingSpinner />
            </div>
            <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
              Completing Sign In
            </h2>
            <p className="text-gray-600 dark:text-gray-400">
              Please wait while we verify your credentials...
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

/**
 * Loading spinner component.
 */
const LoadingSpinner: React.FC = () => (
  <svg
    className="animate-spin h-full w-full text-primary-600"
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
  >
    <circle
      className="opacity-25"
      cx="12"
      cy="12"
      r="10"
      stroke="currentColor"
      strokeWidth="4"
    ></circle>
    <path
      className="opacity-75"
      fill="currentColor"
      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
    ></path>
  </svg>
);

export default OAuthCallbackPage;
