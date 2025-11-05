/**
 * Organization-related TypeScript types matching the OpenAPI specification.
 * These types ensure type safety for organization admin API requests and responses.
 */

/**
 * Organization role enum.
 */
export type OrgRole = 'ADMIN' | 'MEMBER';

/**
 * SSO protocol type.
 */
export type SsoProtocol = 'OIDC' | 'SAML2';

/**
 * SSO configuration data transfer object.
 */
export interface SsoConfigDTO {
  protocol: SsoProtocol;
  issuer?: string;
  clientId?: string;
  authorizationEndpoint?: string;
  tokenEndpoint?: string;
  jwksUri?: string;
  samlEntityId?: string;
  samlSsoUrl?: string;
}

/**
 * Organization branding customization.
 */
export interface BrandingDTO {
  logoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
}

/**
 * Organization data transfer object.
 */
export interface OrganizationDTO {
  orgId: string;
  name: string;
  domain: string;
  ssoConfig?: SsoConfigDTO | null;
  branding?: BrandingDTO | null;
  subscriptionId?: string | null;
  memberCount: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Organization member data transfer object.
 */
export interface OrgMemberDTO {
  userId: string;
  displayName: string;
  email: string;
  avatarUrl?: string | null;
  role: OrgRole;
  joinedAt: string;
}

/**
 * Audit log entry data transfer object.
 */
export interface AuditLogDTO {
  logId: string;
  orgId?: string | null;
  userId?: string | null;
  action: string;
  resourceType: string;
  resourceId?: string | null;
  ipAddress?: string | null;
  userAgent?: string | null;
  timestamp: string;
}

/**
 * Paginated audit log list response.
 */
export interface AuditLogListResponse {
  logs: AuditLogDTO[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Create organization request.
 */
export interface CreateOrganizationRequest {
  name: string;
  domain: string;
}

/**
 * SSO configuration request.
 */
export interface SsoConfigRequest {
  protocol: SsoProtocol;
  issuer?: string;
  clientId?: string;
  clientSecret?: string;
  authorizationEndpoint?: string;
  tokenEndpoint?: string;
  jwksUri?: string;
  samlEntityId?: string;
  samlSsoUrl?: string;
  samlCertificate?: string;
}

/**
 * Invite member request.
 */
export interface InviteMemberRequest {
  email: string;
  role: OrgRole;
}

/**
 * Audit log query filters.
 */
export interface AuditLogFilters {
  from?: string; // ISO-8601 timestamp
  to?: string; // ISO-8601 timestamp
  action?: string;
  page?: number;
  size?: number;
}
