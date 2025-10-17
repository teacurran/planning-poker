/**
 * Authentication-related TypeScript types matching the OpenAPI specification.
 * These types ensure type safety for API requests and responses.
 */

export type OAuthProvider = 'google' | 'microsoft';

export type SubscriptionTier = 'FREE' | 'PRO' | 'PRO_PLUS' | 'ENTERPRISE';

/**
 * User data transfer object matching the API response schema.
 */
export interface UserDTO {
  userId: string;
  email: string;
  oauthProvider?: OAuthProvider;
  displayName: string;
  avatarUrl?: string | null;
  subscriptionTier: SubscriptionTier;
  createdAt: string;
  updatedAt?: string;
}

/**
 * Token response from OAuth callback and refresh endpoints.
 */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user: UserDTO;
}

/**
 * OAuth callback request payload sent to the backend.
 * CRITICAL: The backend requires codeVerifier (see AuthController.java:113-116).
 */
export interface OAuthCallbackRequest {
  code: string;
  provider: OAuthProvider;
  redirectUri: string;
  codeVerifier: string;
}

/**
 * Error response structure from the API.
 */
export interface ErrorResponse {
  error: string;
  message: string;
  timestamp: string;
  details?: Record<string, unknown>;
}
