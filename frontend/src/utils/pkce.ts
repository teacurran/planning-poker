/**
 * PKCE (Proof Key for Code Exchange) utility functions for OAuth2 authorization code flow.
 * Implements RFC 7636 for enhanced security in OAuth2 flows.
 */

/**
 * Generates a cryptographically random code verifier.
 * The verifier is a base64url-encoded string of 43-128 characters.
 *
 * @returns A random code verifier string (96 random bytes = 128 base64url chars)
 */
export function generateCodeVerifier(): string {
  const array = new Uint8Array(96); // 96 bytes = 128 base64url characters
  crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

/**
 * Generates a code challenge from a code verifier using SHA-256.
 * The challenge is the base64url-encoded SHA-256 hash of the verifier.
 *
 * @param verifier - The code verifier to hash
 * @returns A promise that resolves to the code challenge string
 */
export async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  return base64UrlEncode(new Uint8Array(hashBuffer));
}

/**
 * Encodes a Uint8Array to a base64url string (no padding).
 * Base64url encoding is URL-safe: uses - instead of +, _ instead of /, and removes = padding.
 *
 * @param array - The byte array to encode
 * @returns The base64url-encoded string
 */
function base64UrlEncode(array: Uint8Array): string {
  // Convert byte array to base64 string
  const base64 = btoa(String.fromCharCode(...Array.from(array)));

  // Convert base64 to base64url: replace +/= with -_
  return base64
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, ''); // Remove padding
}

/**
 * PKCE session data stored in sessionStorage during OAuth flow.
 */
export interface PKCESession {
  codeVerifier: string;
  redirectUri: string;
  provider: 'google' | 'microsoft';
}

const PKCE_SESSION_KEY = 'oauth_pkce_session';

/**
 * Stores PKCE session data in sessionStorage.
 * This data is needed to complete the OAuth callback.
 *
 * @param session - The PKCE session data to store
 */
export function storePKCESession(session: PKCESession): void {
  sessionStorage.setItem(PKCE_SESSION_KEY, JSON.stringify(session));
}

/**
 * Retrieves and removes PKCE session data from sessionStorage.
 * This is a one-time operation - the session is cleared after retrieval.
 *
 * @returns The PKCE session data, or null if not found
 */
export function retrieveAndClearPKCESession(): PKCESession | null {
  const sessionData = sessionStorage.getItem(PKCE_SESSION_KEY);
  if (!sessionData) {
    return null;
  }

  // Clear the session immediately (one-time use)
  sessionStorage.removeItem(PKCE_SESSION_KEY);

  try {
    return JSON.parse(sessionData) as PKCESession;
  } catch (error) {
    console.error('Failed to parse PKCE session data:', error);
    return null;
  }
}
