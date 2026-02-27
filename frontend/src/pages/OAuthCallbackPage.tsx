/**
 * OIDC callback page that handles the redirect from Keycloak.
 * Exchanges the authorization code for tokens directly with Keycloak's token endpoint.
 */

import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';

const KEYCLOAK_TOKEN_URL = import.meta.env.VITE_KEYCLOAK_TOKEN_URL
  || 'https://auth.villagecompute.com/realms/planning-poker/protocol/openid-connect/token';
const KEYCLOAK_USERINFO_URL = import.meta.env.VITE_KEYCLOAK_USERINFO_URL
  || 'https://auth.villagecompute.com/realms/planning-poker/protocol/openid-connect/userinfo';
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'planning-poker';

const OAuthCallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { setAuth } = useAuth();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleCallback = async () => {
      try {
        // Check for OAuth error
        const oauthError = searchParams.get('error');
        if (oauthError) {
          const errorDescription = searchParams.get('error_description') || 'Authentication failed';
          setError(`Authentication failed: ${errorDescription}`);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        // Extract authorization code
        const code = searchParams.get('code');
        if (!code) {
          setError('Authorization code not found in callback URL');
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        // Retrieve PKCE data from session storage
        const codeVerifier = sessionStorage.getItem('pkce_code_verifier');
        const redirectUri = sessionStorage.getItem('pkce_redirect_uri');
        sessionStorage.removeItem('pkce_code_verifier');
        sessionStorage.removeItem('pkce_redirect_uri');

        if (!codeVerifier || !redirectUri) {
          setError('PKCE session data not found. Please try logging in again.');
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        // Exchange code for tokens at Keycloak token endpoint
        const tokenResponse = await fetch(KEYCLOAK_TOKEN_URL, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({
            grant_type: 'authorization_code',
            client_id: KEYCLOAK_CLIENT_ID,
            code,
            redirect_uri: redirectUri,
            code_verifier: codeVerifier,
          }),
        });

        if (!tokenResponse.ok) {
          const errorData = await tokenResponse.json().catch(() => ({}));
          setError(`Token exchange failed: ${errorData.error_description || tokenResponse.statusText}`);
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        const tokens = await tokenResponse.json();

        // Fetch user info from Keycloak
        const userinfoResponse = await fetch(KEYCLOAK_USERINFO_URL, {
          headers: { Authorization: `Bearer ${tokens.access_token}` },
        });

        if (!userinfoResponse.ok) {
          setError('Failed to fetch user info');
          setTimeout(() => navigate('/login'), 3000);
          return;
        }

        const userinfo = await userinfoResponse.json();

        // Store auth state
        setAuth({
          accessToken: tokens.access_token,
          refreshToken: tokens.refresh_token,
          expiresIn: tokens.expires_in,
          user: {
            userId: userinfo.sub,
            email: userinfo.email || '',
            displayName: userinfo.preferred_username || userinfo.name || 'User',
          },
        });

        navigate('/dashboard', { replace: true });
      } catch (err) {
        console.error('Unexpected error during OAuth callback:', err);
        setError('An unexpected error occurred. Please try again.');
        setTimeout(() => navigate('/login'), 3000);
      }
    };

    handleCallback();
  }, [searchParams, navigate, setAuth]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
      <div className="max-w-md w-full p-8 text-center">
        {error ? (
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
          <div className="space-y-4">
            <div className="w-16 h-16 mx-auto">
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
                />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                />
              </svg>
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

export default OAuthCallbackPage;
