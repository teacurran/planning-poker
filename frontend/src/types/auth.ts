/**
 * Authentication-related TypeScript types for Keycloak OIDC.
 */

/**
 * User data transfer object.
 */
export interface UserDTO {
  userId: string;
  email: string;
  displayName: string;
  avatarUrl?: string | null;
}

/**
 * Token response stored after OIDC authentication.
 */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserDTO;
}

/**
 * Error response structure from the API.
 */
export interface ErrorResponse {
  error: string;
  message: string;
  timestamp?: string;
}
