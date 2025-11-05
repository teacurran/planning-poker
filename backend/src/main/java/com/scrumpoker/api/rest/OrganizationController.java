package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.*;
import com.scrumpoker.api.rest.mapper.OrganizationMapper;
import com.scrumpoker.domain.organization.*;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.integration.sso.SsoConfig;
import com.scrumpoker.repository.AuditLogRepository;
import com.scrumpoker.repository.OrgMemberRepository;
import com.scrumpoker.repository.UserRepository;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for organization management operations.
 * Provides endpoints for creating organizations, managing SSO configuration,
 * managing members, and querying audit logs.
 * Implements OpenAPI specification from I2.T1.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Organizations", description = "Organization management endpoints")
public class OrganizationController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Inject
    OrganizationService organizationService;

    @Inject
    AuditLogService auditLogService;

    @Inject
    OrgMemberRepository orgMemberRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    OrganizationMapper organizationMapper;

    @Inject
    SecurityIdentity securityIdentity;

    @Context
    ContainerRequestContext requestContext;

    /**
     * POST /api/v1/organizations - Create new organization
     * Security: Requires authenticated user with Enterprise tier subscription
     * Returns: 201 Created with OrganizationDTO
     */
    @POST
    @Path("/organizations")
    @RolesAllowed("USER")
    @Operation(summary = "Create organization workspace",
               description = "Creates a new organization workspace. Requires Enterprise tier subscription.")
    @APIResponse(responseCode = "201", description = "Organization created",
        content = @Content(schema = @Schema(implementation = OrganizationDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Requires Enterprise subscription",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> createOrganization(@Valid CreateOrganizationRequest request) {
        final UUID userId = UUID.fromString(securityIdentity.getPrincipal().getName());

        // Create organization (service enforces Enterprise tier via FeatureGate)
        return organizationService.createOrganization(request.name, request.domain, userId)
            .flatMap(organization -> {
                // Get member count (should be 1 - just the creator)
                return orgMemberRepository.countByOrgId(organization.orgId)
                    .map(memberCount -> {
                        // Map to DTO
                        final OrganizationDTO dto = organizationMapper.toDTO(organization, memberCount);
                        return Response.status(Response.Status.CREATED)
                            .entity(dto)
                            .build();
                    });
            });
    }

    /**
     * GET /api/v1/organizations/{orgId} - Get organization by ID
     * Security: Requires organization membership
     * Returns: 200 OK with OrganizationDTO, or 403/404
     */
    @GET
    @Path("/organizations/{orgId}")
    @RolesAllowed("USER")
    @Operation(summary = "Get organization settings",
               description = "Returns organization configuration, branding, and member count. Requires organization membership.")
    @APIResponse(responseCode = "200", description = "Organization retrieved",
        content = @Content(schema = @Schema(implementation = OrganizationDTO.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Not a member of this organization",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Organization not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> getOrganization(
            @Parameter(description = "Organization ID", required = true)
            @PathParam("orgId") UUID orgId) {

        final UUID userId = UUID.fromString(securityIdentity.getPrincipal().getName());

        // Verify user is a member of this organization
        return orgMemberRepository.findByOrgIdAndUserId(orgId, userId)
            .onItem().ifNull().failWith(() ->
                new ForbiddenException("Not a member of this organization"))
            .flatMap(member ->
                // Get organization details
                organizationService.getOrganization(orgId)
                    .onItem().ifNull().failWith(() ->
                        new NotFoundException("Organization not found"))
                    .flatMap(organization ->
                        // Get member count in parallel
                        orgMemberRepository.countByOrgId(orgId)
                            .map(memberCount -> {
                                final OrganizationDTO dto = organizationMapper.toDTO(organization, memberCount);
                                return Response.ok(dto).build();
                            })
                    )
            );
    }

    /**
     * PUT /api/v1/organizations/{orgId}/sso - Configure SSO settings
     * Security: Requires ADMIN role in the organization
     * Returns: 200 OK with updated OrganizationDTO
     */
    @PUT
    @Path("/organizations/{orgId}/sso")
    @RolesAllowed("USER")
    @Operation(summary = "Configure OIDC/SAML2 SSO settings",
               description = "Updates SSO configuration for organization. Requires ADMIN role.")
    @APIResponse(responseCode = "200", description = "SSO configuration updated",
        content = @Content(schema = @Schema(implementation = OrganizationDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Requires ADMIN role",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Organization not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> updateSsoConfig(
            @Parameter(description = "Organization ID", required = true)
            @PathParam("orgId") UUID orgId,
            @Valid SsoConfigRequest request) {

        final UUID userId = UUID.fromString(securityIdentity.getPrincipal().getName());

        // Verify user is an admin of this organization
        return requireOrgAdmin(orgId, userId)
            .flatMap(member -> {
                // Convert request DTO to domain object
                final SsoConfig ssoConfig = organizationMapper.toDomain(request);

                // Update SSO configuration
                return organizationService.updateSsoConfig(orgId, ssoConfig)
                    .flatMap(organization -> {
                        // Audit log the configuration change
                        final String ipAddress = AuditLogService.extractIpAddress(
                            requestContext.getHeaderString("X-Forwarded-For"),
                            requestContext.getHeaderString("X-Real-IP"),
                            requestContext.getHeaderString("Remote-Address")
                        );
                        final String userAgent = requestContext.getHeaderString("User-Agent");

                        final Map<String, Object> changeDetails = Map.of(
                            "protocol", request.protocol,
                            "issuer", request.issuer != null ? request.issuer : ""
                        );

                        auditLogService.logOrgConfigChange(orgId, userId, ipAddress, userAgent, changeDetails);

                        // Get member count and return DTO
                        return orgMemberRepository.countByOrgId(orgId)
                            .map(memberCount -> {
                                final OrganizationDTO dto = organizationMapper.toDTO(organization, memberCount);
                                return Response.ok(dto).build();
                            });
                    });
            });
    }

    /**
     * POST /api/v1/organizations/{orgId}/members - Invite member to organization
     * Security: Requires ADMIN role in the organization
     * Returns: 201 Created with OrgMemberDTO
     */
    @POST
    @Path("/organizations/{orgId}/members")
    @RolesAllowed("USER")
    @Operation(summary = "Invite member to organization",
               description = "Sends invitation email to join organization. Requires ADMIN role.")
    @APIResponse(responseCode = "201", description = "Member invited",
        content = @Content(schema = @Schema(implementation = OrgMemberDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Requires ADMIN role",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "User or organization not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> inviteMember(
            @Parameter(description = "Organization ID", required = true)
            @PathParam("orgId") UUID orgId,
            @Valid InviteMemberRequest request) {

        final UUID actorUserId = UUID.fromString(securityIdentity.getPrincipal().getName());

        // Verify user is an admin of this organization
        return requireOrgAdmin(orgId, actorUserId)
            .flatMap(member ->
                // Look up user by email
                userRepository.findByEmail(request.email)
                    .onItem().ifNull().failWith(() ->
                        new NotFoundException("User not found with email: " + request.email))
                    .flatMap(targetUser ->
                        // Add member to organization
                        organizationService.addMember(orgId, targetUser.userId, request.role)
                            .invoke(orgMember -> {
                                // Audit log the member addition
                                final String ipAddress = AuditLogService.extractIpAddress(
                                    requestContext.getHeaderString("X-Forwarded-For"),
                                    requestContext.getHeaderString("X-Real-IP"),
                                    requestContext.getHeaderString("Remote-Address")
                                );
                                final String userAgent = requestContext.getHeaderString("User-Agent");

                                auditLogService.logMemberAdded(
                                    orgId, actorUserId, targetUser.userId, request.role,
                                    ipAddress, userAgent
                                );
                            })
                            .map(orgMember -> {
                                final OrgMemberDTO dto = organizationMapper.toDTO(orgMember);
                                return Response.status(Response.Status.CREATED)
                                    .entity(dto)
                                    .build();
                            })
                    )
            );
    }

    /**
     * DELETE /api/v1/organizations/{orgId}/members/{userId} - Remove member from organization
     * Security: Requires ADMIN role in the organization
     * Returns: 204 No Content
     */
    @DELETE
    @Path("/organizations/{orgId}/members/{userId}")
    @RolesAllowed("USER")
    @Operation(summary = "Remove member from organization",
               description = "Removes user from organization. Requires ADMIN role. Cannot remove last admin.")
    @APIResponse(responseCode = "204", description = "Member removed")
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Requires ADMIN role",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Member or organization not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> removeMember(
            @Parameter(description = "Organization ID", required = true)
            @PathParam("orgId") UUID orgId,
            @Parameter(description = "User ID to remove", required = true)
            @PathParam("userId") UUID userIdToRemove) {

        final UUID actorUserId = UUID.fromString(securityIdentity.getPrincipal().getName());

        // Verify user is an admin of this organization
        return requireOrgAdmin(orgId, actorUserId)
            .flatMap(member ->
                // Remove member from organization
                organizationService.removeMember(orgId, userIdToRemove)
                    .invoke(() -> {
                        // Audit log the member removal
                        final String ipAddress = AuditLogService.extractIpAddress(
                            requestContext.getHeaderString("X-Forwarded-For"),
                            requestContext.getHeaderString("X-Real-IP"),
                            requestContext.getHeaderString("Remote-Address")
                        );
                        final String userAgent = requestContext.getHeaderString("User-Agent");

                        auditLogService.logMemberRemoved(
                            orgId, actorUserId, userIdToRemove,
                            ipAddress, userAgent
                        );
                    })
                    .map(v -> Response.noContent().build())
            );
    }

    /**
     * GET /api/v1/organizations/{orgId}/audit-logs - Query audit trail
     * Security: Requires ADMIN role in the organization
     * Returns: 200 OK with AuditLogListResponse (paginated)
     */
    @GET
    @Path("/organizations/{orgId}/audit-logs")
    @RolesAllowed("USER")
    @Operation(summary = "Query audit trail",
               description = "Returns paginated audit log entries for compliance. Requires ADMIN role.")
    @APIResponse(responseCode = "200", description = "Audit logs retrieved",
        content = @Content(schema = @Schema(implementation = AuditLogListResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Requires ADMIN role",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Organization not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> getAuditLogs(
            @Parameter(description = "Organization ID", required = true)
            @PathParam("orgId") UUID orgId,
            @Parameter(description = "Start timestamp (ISO 8601 format)")
            @QueryParam("from") String fromString,
            @Parameter(description = "End timestamp (ISO 8601 format)")
            @QueryParam("to") String toString,
            @Parameter(description = "Filter by action type")
            @QueryParam("action") String action,
            @Parameter(description = "Page number (0-indexed)")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size")
            @QueryParam("size") @DefaultValue("20") int size) {

        final UUID userId = UUID.fromString(securityIdentity.getPrincipal().getName());

        // Validate pagination parameters
        if (page < 0) {
            throw new BadRequestException("Page number must be >= 0");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        // Parse timestamps
        final Instant from = fromString != null && !fromString.isBlank()
            ? Instant.parse(fromString)
            : null;
        final Instant to = toString != null && !toString.isBlank()
            ? Instant.parse(toString)
            : null;

        // Verify user is an admin of this organization
        return requireOrgAdmin(orgId, userId)
            .flatMap(member -> {
                // Query audit logs with filters and pagination
                final Uni<List<AuditLog>> logsUni = auditLogRepository.findByOrgIdWithFilters(
                    orgId, from, to, action, page, size
                );

                // Get total count for pagination metadata
                final Uni<Long> countUni = auditLogRepository.countByOrgIdWithFilters(
                    orgId, from, to, action
                );

                // Execute queries in parallel
                return Uni.combine().all().unis(logsUni, countUni).asTuple()
                    .map(tuple -> {
                        final List<AuditLog> logs = tuple.getItem1();
                        final Long totalElements = tuple.getItem2();

                        // Calculate total pages
                        final int totalPages = (int) Math.ceil((double) totalElements / size);

                        // Map to DTOs
                        final List<AuditLogDTO> logDTOs = logs.stream()
                            .map(organizationMapper::toDTO)
                            .collect(Collectors.toList());

                        // Build response
                        final AuditLogListResponse response = new AuditLogListResponse();
                        response.logs = logDTOs;
                        response.page = page;
                        response.size = size;
                        response.totalElements = totalElements;
                        response.totalPages = totalPages;

                        return Response.ok(response).build();
                    });
            });
    }

    /**
     * Helper method to verify user is an admin of the organization.
     * Throws ForbiddenException if user is not an admin or not a member.
     *
     * @param orgId The organization ID
     * @param userId The user ID to check
     * @return Uni containing the OrgMember if user is admin, or failure
     */
    private Uni<OrgMember> requireOrgAdmin(UUID orgId, UUID userId) {
        return orgMemberRepository.findByOrgIdAndUserId(orgId, userId)
            .onItem().ifNull().failWith(() ->
                new ForbiddenException("Not a member of this organization"))
            .onItem().invoke(member -> {
                if (member.role != OrgRole.ADMIN) {
                    throw new ForbiddenException("Requires ADMIN role");
                }
            });
    }
}
