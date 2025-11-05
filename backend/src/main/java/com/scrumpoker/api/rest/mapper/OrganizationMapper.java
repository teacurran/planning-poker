package com.scrumpoker.api.rest.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.api.rest.dto.*;
import com.scrumpoker.domain.organization.AuditLog;
import com.scrumpoker.domain.organization.BrandingConfig;
import com.scrumpoker.domain.organization.OrgMember;
import com.scrumpoker.domain.organization.Organization;
import com.scrumpoker.integration.sso.OidcConfig;
import com.scrumpoker.integration.sso.SsoConfig;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Mapper for converting between organization domain entities and DTOs.
 * Handles JSONB serialization/deserialization for SSO config and branding.
 */
@ApplicationScoped
public class OrganizationMapper {

    private static final Logger LOG = Logger.getLogger(OrganizationMapper.class);

    @Inject
    ObjectMapper objectMapper;

    /**
     * Maps Organization entity to OrganizationDTO.
     * Requires memberCount to be provided separately (calculated via repository).
     *
     * @param organization The organization entity
     * @param memberCount The total member count for this organization
     * @return OrganizationDTO with all fields mapped
     */
    public OrganizationDTO toDTO(final Organization organization, final Long memberCount) {
        final OrganizationDTO dto = new OrganizationDTO();
        dto.orgId = organization.orgId;
        dto.name = organization.name;
        dto.domain = organization.domain;
        dto.createdAt = organization.createdAt;
        dto.updatedAt = organization.updatedAt;
        dto.memberCount = memberCount != null ? memberCount.intValue() : 0;

        // Map subscription ID if subscription exists
        if (organization.subscription != null) {
            dto.subscriptionId = organization.subscription.subscriptionId;
        }

        // Deserialize SSO config from JSONB string
        if (organization.ssoConfig != null && !organization.ssoConfig.isBlank()) {
            dto.ssoConfig = deserializeSsoConfig(organization.ssoConfig);
        }

        // Deserialize branding from JSONB string
        if (organization.branding != null && !organization.branding.isBlank()) {
            dto.branding = deserializeBranding(organization.branding);
        }

        return dto;
    }

    /**
     * Maps OrgMember entity to OrgMemberDTO.
     * Extracts user information from the member's user relationship.
     *
     * @param member The organization member entity
     * @return OrgMemberDTO with all fields mapped
     */
    public OrgMemberDTO toDTO(final OrgMember member) {
        final OrgMemberDTO dto = new OrgMemberDTO();
        dto.userId = member.user.userId;
        dto.displayName = member.user.displayName;
        dto.email = member.user.email;
        dto.avatarUrl = member.user.avatarUrl;
        dto.role = member.role;
        dto.joinedAt = member.joinedAt;
        return dto;
    }

    /**
     * Maps AuditLog entity to AuditLogDTO.
     *
     * @param auditLog The audit log entity
     * @return AuditLogDTO with all fields mapped
     */
    public AuditLogDTO toDTO(final AuditLog auditLog) {
        final AuditLogDTO dto = new AuditLogDTO();
        dto.logId = auditLog.id.logId;
        dto.timestamp = auditLog.id.timestamp;
        dto.action = auditLog.action;
        dto.resourceType = auditLog.resourceType;
        dto.resourceId = auditLog.resourceId;
        dto.ipAddress = auditLog.ipAddress;
        dto.userAgent = auditLog.userAgent;

        // Map optional relationships
        if (auditLog.organization != null) {
            dto.orgId = auditLog.organization.orgId;
        }
        if (auditLog.user != null) {
            dto.userId = auditLog.user.userId;
        }

        return dto;
    }

    /**
     * Maps SsoConfigRequest to SsoConfig domain object.
     * Used when updating SSO configuration.
     *
     * @param request The SSO config request DTO
     * @return SsoConfig domain object ready for persistence
     */
    public SsoConfig toDomain(final SsoConfigRequest request) {
        // Currently only OIDC is supported
        final OidcConfig oidcConfig = new OidcConfig();
        oidcConfig.setIssuer(request.issuer);
        oidcConfig.setClientId(request.clientId);
        oidcConfig.setClientSecret(request.clientSecret);
        oidcConfig.setAuthorizationEndpoint(request.authorizationEndpoint);
        oidcConfig.setTokenEndpoint(request.tokenEndpoint);
        oidcConfig.setJwksUri(request.jwksUri);

        final SsoConfig ssoConfig = new SsoConfig();
        ssoConfig.setProtocol(request.protocol);
        ssoConfig.setOidc(oidcConfig);
        ssoConfig.setDomainVerificationRequired(true);
        ssoConfig.setJitProvisioningEnabled(true);

        return ssoConfig;
    }

    /**
     * Maps BrandingDTO to BrandingConfig domain object.
     *
     * @param dto The branding DTO
     * @return BrandingConfig domain object
     */
    public BrandingConfig toDomain(final BrandingDTO dto) {
        if (dto == null) {
            return null;
        }
        return new BrandingConfig(dto.logoUrl, dto.primaryColor, dto.secondaryColor);
    }

    /**
     * Deserializes SSO configuration from JSONB string to DTO.
     * Excludes clientSecret for security (never returned in responses).
     *
     * @param ssoConfigJson The SSO config JSONB string
     * @return SsoConfigDTO or null if deserialization fails
     */
    private SsoConfigDTO deserializeSsoConfig(final String ssoConfigJson) {
        try {
            final SsoConfig ssoConfig = objectMapper.readValue(ssoConfigJson, SsoConfig.class);
            final SsoConfigDTO dto = new SsoConfigDTO();
            dto.protocol = ssoConfig.getProtocol();

            // Map OIDC config if present
            if (ssoConfig.getOidc() != null) {
                final OidcConfig oidc = ssoConfig.getOidc();
                dto.issuer = oidc.getIssuer();
                dto.clientId = oidc.getClientId();
                // Intentionally exclude clientSecret for security
                dto.authorizationEndpoint = oidc.getAuthorizationEndpoint();
                dto.tokenEndpoint = oidc.getTokenEndpoint();
                dto.jwksUri = oidc.getJwksUri();
            }

            return dto;
        } catch (JsonProcessingException e) {
            LOG.error("Failed to deserialize SSO config from JSON: " + ssoConfigJson, e);
            return null;
        }
    }

    /**
     * Deserializes branding configuration from JSONB string to DTO.
     *
     * @param brandingJson The branding JSONB string
     * @return BrandingDTO or null if deserialization fails
     */
    private BrandingDTO deserializeBranding(final String brandingJson) {
        try {
            final BrandingConfig brandingConfig = objectMapper.readValue(brandingJson, BrandingConfig.class);
            final BrandingDTO dto = new BrandingDTO();
            dto.logoUrl = brandingConfig.getLogoUrl();
            dto.primaryColor = brandingConfig.getPrimaryColor();
            dto.secondaryColor = brandingConfig.getSecondaryColor();
            return dto;
        } catch (JsonProcessingException e) {
            LOG.error("Failed to deserialize branding config from JSON: " + brandingJson, e);
            return null;
        }
    }

    /**
     * Serializes BrandingConfig to JSONB string for database storage.
     *
     * @param brandingConfig The branding config object
     * @return JSON string or null if serialization fails
     */
    public String serializeBranding(final BrandingConfig brandingConfig) {
        if (brandingConfig == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(brandingConfig);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize branding config to JSON", e);
            return null;
        }
    }
}
