/**
 * Login page that redirects to Keycloak for OIDC authentication.
 * Keycloak can be configured with Google/Microsoft as identity providers,
 * so the frontend only needs a single login button.
 */

import React from 'react';
import Button from '@/components/common/Button';

/** Keycloak OIDC configuration */
const KEYCLOAK_AUTH_URL = import.meta.env.VITE_KEYCLOAK_AUTH_URL
  || 'https://auth.villagecompute.com/realms/planning-poker/protocol/openid-connect/auth';
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'planning-poker';

/**
 * Generate a random code verifier for PKCE (43-128 characters).
 */
function generateCodeVerifier(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

/**
 * Generate code challenge from code verifier using S256 method.
 */
async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64UrlEncode(new Uint8Array(digest));
}

function base64UrlEncode(buffer: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < buffer.length; i++) {
    binary += String.fromCharCode(buffer[i]);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

const LoginPage: React.FC = () => {
  const handleLogin = async () => {
    try {
      const codeVerifier = generateCodeVerifier();
      const codeChallenge = await generateCodeChallenge(codeVerifier);
      const redirectUri = `${window.location.origin}/auth/callback`;

      // Store PKCE verifier for the callback page
      sessionStorage.setItem('pkce_code_verifier', codeVerifier);
      sessionStorage.setItem('pkce_redirect_uri', redirectUri);

      const params = new URLSearchParams({
        client_id: KEYCLOAK_CLIENT_ID,
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'openid email profile',
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
      });

      window.location.href = `${KEYCLOAK_AUTH_URL}?${params.toString()}`;
    } catch (error) {
      console.error('Failed to initiate login:', error);
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

        <div className="mt-8">
          <Button
            onClick={handleLogin}
            className="w-full flex items-center justify-center gap-3 py-3"
          >
            <span>Sign In</span>
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

export default LoginPage;
