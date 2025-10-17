/**
 * Login page with OAuth2 authentication providers.
 * Displays "Sign in with Google" and "Sign in with Microsoft" buttons.
 * Implements PKCE flow for enhanced security.
 */

import React from 'react';
import Button from '@/components/common/Button';
import { generateCodeVerifier, generateCodeChallenge, storePKCESession } from '@/utils/pkce';
import type { OAuthProvider } from '@/types/auth';

const LoginPage: React.FC = () => {

  /**
   * Initiates OAuth2 authorization code flow with PKCE.
   * Generates PKCE parameters, stores session data, and redirects to OAuth provider.
   */
  const handleOAuthLogin = async (provider: OAuthProvider) => {
    try {
      // Step 1: Generate PKCE code verifier and challenge
      const codeVerifier = generateCodeVerifier();
      const codeChallenge = await generateCodeChallenge(codeVerifier);

      // Step 2: Determine OAuth provider configuration
      const redirectUri = `${window.location.origin}/auth/callback`;
      const config = getOAuthConfig(provider);

      // Step 3: Store PKCE session data in sessionStorage (needed for callback)
      storePKCESession({
        codeVerifier,
        redirectUri,
        provider,
      });

      // Step 4: Build authorization URL
      const authUrl = buildAuthorizationUrl(
        config,
        redirectUri,
        codeChallenge
      );

      // Step 5: Redirect to OAuth provider
      window.location.href = authUrl;
    } catch (error) {
      console.error(`Failed to initiate ${provider} OAuth flow:`, error);
      alert(`Failed to start login process. Please try again.`);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
      <div className="max-w-md w-full space-y-8 p-8">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-gray-900 dark:text-white mb-2">
            Planning Poker
          </h1>
          <p className="text-gray-600 dark:text-gray-400">
            Sign in to create and manage estimation rooms
          </p>
        </div>

        <div className="mt-8 space-y-4">
          {/* Google OAuth Button */}
          <Button
            onClick={() => handleOAuthLogin('google')}
            variant="secondary"
            className="w-full flex items-center justify-center gap-3 py-3"
          >
            <GoogleIcon />
            <span>Sign in with Google</span>
          </Button>

          {/* Microsoft OAuth Button */}
          <Button
            onClick={() => handleOAuthLogin('microsoft')}
            variant="secondary"
            className="w-full flex items-center justify-center gap-3 py-3"
          >
            <MicrosoftIcon />
            <span>Sign in with Microsoft</span>
          </Button>
        </div>

        <div className="mt-6 text-center">
          <p className="text-sm text-gray-600 dark:text-gray-400">
            By signing in, you agree to our Terms of Service and Privacy Policy
          </p>
        </div>
      </div>
    </div>
  );
};

/**
 * OAuth provider configuration interface.
 */
interface OAuthConfig {
  clientId: string;
  authorizationEndpoint: string;
  scope: string;
}

/**
 * Get OAuth configuration for a provider.
 * In production, these values should come from environment variables.
 * For development, using placeholder values that need to be configured.
 */
function getOAuthConfig(provider: OAuthProvider): OAuthConfig {
  switch (provider) {
    case 'google':
      return {
        clientId: import.meta.env.VITE_GOOGLE_CLIENT_ID || 'YOUR_GOOGLE_CLIENT_ID',
        authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
        scope: 'openid email profile',
      };
    case 'microsoft':
      return {
        clientId: import.meta.env.VITE_MICROSOFT_CLIENT_ID || 'YOUR_MICROSOFT_CLIENT_ID',
        authorizationEndpoint: 'https://login.microsoftonline.com/common/oauth2/v2.0/authorize',
        scope: 'openid email profile',
      };
    default:
      throw new Error(`Unsupported OAuth provider: ${provider}`);
  }
}

/**
 * Build OAuth2 authorization URL with PKCE parameters.
 */
function buildAuthorizationUrl(
  config: OAuthConfig,
  redirectUri: string,
  codeChallenge: string
): string {
  const params = new URLSearchParams({
    client_id: config.clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: config.scope,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
  });

  return `${config.authorizationEndpoint}?${params.toString()}`;
}

/**
 * Google icon component (SVG).
 */
const GoogleIcon: React.FC = () => (
  <svg
    width="20"
    height="20"
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path
      d="M47.532 24.5528C47.532 22.9214 47.3997 21.2811 47.1175 19.6761H24.48V28.9181H37.4434C36.9055 31.8988 35.177 34.5356 32.6461 36.2111V42.2078H40.3801C44.9217 38.0278 47.532 31.8547 47.532 24.5528Z"
      fill="#4285F4"
    />
    <path
      d="M24.48 48.0016C30.9529 48.0016 36.4116 45.8764 40.3888 42.2078L32.6549 36.2111C30.5031 37.675 27.7252 38.5039 24.4888 38.5039C18.2275 38.5039 12.9187 34.2798 11.0139 28.6006H3.03296V34.7825C7.10718 42.8868 15.4056 48.0016 24.48 48.0016Z"
      fill="#34A853"
    />
    <path
      d="M11.0051 28.6006C9.99973 25.6199 9.99973 22.3922 11.0051 19.4115V13.2296H3.03298C-0.371021 20.0112 -0.371021 28.0009 3.03298 34.7825L11.0051 28.6006Z"
      fill="#FBBC04"
    />
    <path
      d="M24.48 9.49932C27.9016 9.44641 31.2086 10.7339 33.6866 13.0973L40.5387 6.24523C36.2 2.17101 30.4414 -0.068932 24.48 0.00161733C15.4055 0.00161733 7.10718 5.11644 3.03296 13.2296L11.005 19.4115C12.901 13.7235 18.2187 9.49932 24.48 9.49932Z"
      fill="#EA4335"
    />
  </svg>
);

/**
 * Microsoft icon component (SVG).
 */
const MicrosoftIcon: React.FC = () => (
  <svg
    width="20"
    height="20"
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path d="M0 0H22.9091V22.9091H0V0Z" fill="#F25022" />
    <path d="M25.0909 0H48V22.9091H25.0909V0Z" fill="#7FBA00" />
    <path d="M0 25.0909H22.9091V48H0V25.0909Z" fill="#00A4EF" />
    <path d="M25.0909 25.0909H48V48H25.0909V25.0909Z" fill="#FFB900" />
  </svg>
);

export default LoginPage;
