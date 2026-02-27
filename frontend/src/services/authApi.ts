/**
 * Authentication API service for Keycloak OIDC.
 */

const KEYCLOAK_TOKEN_URL = import.meta.env.VITE_KEYCLOAK_TOKEN_URL
  || 'https://auth.villagecompute.com/realms/planning-poker/protocol/openid-connect/token';
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'planning-poker';
const KEYCLOAK_LOGOUT_URL = import.meta.env.VITE_KEYCLOAK_LOGOUT_URL
  || 'https://auth.villagecompute.com/realms/planning-poker/protocol/openid-connect/logout';

/**
 * Refresh the access token using the Keycloak token endpoint.
 */
export async function refreshAccessToken(refreshToken: string): Promise<{
  access_token: string;
  refresh_token: string;
  expires_in: number;
}> {
  const response = await fetch(KEYCLOAK_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: KEYCLOAK_CLIENT_ID,
      refresh_token: refreshToken,
    }),
  });

  if (!response.ok) {
    throw new Error(`Token refresh failed: ${response.statusText}`);
  }

  return response.json();
}

/**
 * Logout by redirecting to Keycloak's end session endpoint.
 */
export function logout(): void {
  const redirectUri = `${window.location.origin}/login`;
  window.location.href = `${KEYCLOAK_LOGOUT_URL}?post_logout_redirect_uri=${encodeURIComponent(redirectUri)}&client_id=${KEYCLOAK_CLIENT_ID}`;
}
