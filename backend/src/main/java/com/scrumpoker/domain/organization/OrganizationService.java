package com.scrumpoker.domain.organization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.integration.sso.SsoConfig;
import com.scrumpoker.repository.OrgMemberRepository;
import com.scrumpoker.repository.OrganizationRepository;
import com.scrumpoker.repository.UserRepository;
import com.scrumpoker.security.FeatureGate;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain service for enterprise organization management operations.
 * Handles organization creation, SSO configuration, member management,
 * branding customization, and organization queries.
 * <p>
 * This service enforces Enterprise tier requirements for organization
 * management and validates domain ownership for organization creation.
 * </p>
 */
@ApplicationScoped
public class OrganizationService {

    /**
     * Repository for organization persistence operations.
     */
    @Inject
    private OrganizationRepository organizationRepository;

    /**
     * Repository for organization member persistence operations.
     */
    @Inject
    private OrgMemberRepository orgMemberRepository;

    /**
     * Repository for user persistence operations.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * Service for tier-based feature access control.
     */
    @Inject
    private FeatureGate featureGate;

    /**
     * JSON serialization/deserialization mapper.
     */
    @Inject
    private ObjectMapper objectMapper;

    /**
     * Creates a new enterprise organization with domain ownership validation.
     * <p>
     * The user creating the organization must:
     * <ul>
     *   <li>Have Enterprise tier subscription</li>
     *   <li>Have an email domain matching the organization domain</li>
     * </ul>
     * The user is automatically added as an ADMIN member.
     * </p>
     *
     * @param name The organization name
     * @param domain The organization email domain
     *               (e.g., "company.com")
     * @param ownerId The user ID of the organization owner
     * @return Uni containing the created Organization
     * @throws IllegalArgumentException if user not found or domain
     *         doesn't match
     * @throws com.scrumpoker.security.FeatureNotAvailableException if
     *         user lacks Enterprise tier
     */
    @WithTransaction
    public Uni<Organization> createOrganization(final String name,
                                                 final String domain,
                                                 final UUID ownerId) {
        return userRepository.findById(ownerId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("User not found: " + ownerId))
            .invoke(user -> {
                // Enforce Enterprise tier requirement
                featureGate.requireCanManageOrganization(user);

                // Extract email domain from user's email
                final String userEmailDomain = extractEmailDomain(user.email);

                // Validate user's email domain matches organization domain
                if (!userEmailDomain.equalsIgnoreCase(domain)) {
                    throw new IllegalArgumentException(
                        "User email domain does not match organization "
                        + "domain. Expected: " + domain
                        + ", but user has: " + userEmailDomain
                    );
                }
            })
            .flatMap(user -> {
                // Create organization entity
                final Organization organization = new Organization();
                organization.name = name;
                organization.domain = domain;
                organization.ssoConfig = null;
                organization.branding = null;
                organization.createdAt = Instant.now();

                // Persist organization
                return organizationRepository.persist(organization)
                    .flatMap(org -> {
                        // Create organization member for owner with ADMIN role
                        final OrgMember orgMember = new OrgMember();
                        orgMember.id = new OrgMemberId(org.orgId, user.userId);
                        orgMember.organization = org;
                        orgMember.user = user;
                        orgMember.role = OrgRole.ADMIN;
                        orgMember.joinedAt = Instant.now();

                        // Persist membership and return organization
                        return orgMemberRepository.persist(orgMember)
                            .replaceWith(org);
                    });
            });
    }

    /**
     * Updates the SSO configuration for an organization.
     * <p>
     * Serializes the SsoConfig object to JSON and stores it in the
     * Organization.ssoConfig JSONB field.
     * </p>
     *
     * @param orgId The organization ID
     * @param ssoConfig The SSO configuration (OIDC or SAML2)
     * @return Uni containing the updated Organization
     * @throws IllegalArgumentException if organization not found
     * @throws RuntimeException if JSON serialization fails
     */
    @WithTransaction
    public Uni<Organization> updateSsoConfig(
            final UUID orgId,
            final SsoConfig ssoConfig) {
        return organizationRepository.findById(orgId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException(
                    "Organization not found: " + orgId))
            .flatMap(organization -> {
                try {
                    // Serialize SsoConfig to JSON string
                    final String ssoConfigJson =
                        objectMapper.writeValueAsString(ssoConfig);
                    organization.ssoConfig = ssoConfigJson;

                    // Persist updated organization
                    return organizationRepository.persist(organization);
                } catch (JsonProcessingException e) {
                    return Uni.createFrom().failure(
                        new RuntimeException(
                            "Failed to serialize SSO config to JSON", e)
                    );
                }
            });
    }

    /**
     * Adds a user as a member to an organization with the specified role.
     * <p>
     * Validates that both organization and user exist, and prevents
     * duplicate memberships.
     * </p>
     *
     * @param orgId The organization ID
     * @param userId The user ID to add as member
     * @param role The organization role (ADMIN or MEMBER)
     * @return Uni containing the created OrgMember
     * @throws IllegalArgumentException if organization or user not found
     * @throws IllegalStateException if user is already a member
     */
    @WithTransaction
    public Uni<OrgMember> addMember(final UUID orgId,
                                     final UUID userId,
                                     final OrgRole role) {
        // Validate organization exists
        final Uni<Organization> orgUni =
            organizationRepository.findById(orgId)
                .onItem().ifNull().failWith(() ->
                    new IllegalArgumentException(
                        "Organization not found: " + orgId));

        // Validate user exists
        final Uni<User> userUni = userRepository.findById(userId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("User not found: " + userId));

        // Check if member already exists
        final Uni<OrgMember> existingMemberUni =
            orgMemberRepository.findByOrgIdAndUserId(orgId, userId);

        // Combine validations and create member
        return Uni.combine().all().unis(orgUni, userUni, existingMemberUni)
            .asTuple()
            .flatMap(tuple -> {
                final Organization organization = tuple.getItem1();
                final User user = tuple.getItem2();
                final OrgMember existingMember = tuple.getItem3();

                // Prevent duplicate membership
                if (existingMember != null) {
                    return Uni.createFrom().failure(
                        new IllegalStateException(
                            "User is already a member of this organization"
                        )
                    );
                }

                // Create new organization member
                final OrgMember orgMember = new OrgMember();
                orgMember.id = new OrgMemberId(orgId, userId);
                orgMember.organization = organization;
                orgMember.user = user;
                orgMember.role = role;
                orgMember.joinedAt = Instant.now();

                // Persist and return
                return orgMemberRepository.persist(orgMember);
            });
    }

    /**
     * Removes a user from an organization.
     * <p>
     * Prevents removal of the last admin to ensure organizations
     * always have at least one admin. Uses hard delete since
     * OrgMember entity does not support soft deletion.
     * </p>
     *
     * @param orgId The organization ID
     * @param userId The user ID to remove
     * @return Uni<Void> on successful removal
     * @throws IllegalArgumentException if member not found
     * @throws IllegalStateException if attempting to remove last admin
     */
    @WithTransaction
    public Uni<Void> removeMember(final UUID orgId, final UUID userId) {
        final OrgMemberId compositeId = new OrgMemberId(orgId, userId);

        return orgMemberRepository.findById(compositeId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException(
                    "Member not found in organization: " + userId
                ))
            .flatMap(member -> {
                // If member is ADMIN, check if they're the last admin
                if (member.role == OrgRole.ADMIN) {
                    return orgMemberRepository
                        .count("id.orgId = ?1 and role = ?2",
                            orgId, OrgRole.ADMIN)
                        .flatMap(adminCount -> {
                            if (adminCount <= 1) {
                            return Uni.createFrom().failure(
                                new IllegalStateException(
                                    "Cannot remove the last admin from "
                                    + "organization")
                            );
                        }
                            // Safe to delete - more admins remain
                            return orgMemberRepository.delete(member);
                        });
                } else {
                    // Not an admin - safe to delete directly
                    return orgMemberRepository.delete(member);
                }
            })
            .replaceWithVoid();
    }

    /**
     * Updates the branding configuration for an organization.
     * <p>
     * Serializes the BrandingConfig to JSON and stores it in the
     * Organization.branding JSONB field.
     * </p>
     *
     * @param orgId The organization ID
     * @param logoUrl URL to organization logo image
     * @param primaryColor Primary brand color (hex format)
     * @param secondaryColor Secondary brand color (hex format)
     * @return Uni containing the updated Organization
     * @throws IllegalArgumentException if organization not found
     * @throws RuntimeException if JSON serialization fails
     */
    @WithTransaction
    public Uni<Organization> updateBranding(
            final UUID orgId,
            final String logoUrl,
            final String primaryColor,
            final String secondaryColor) {
        return organizationRepository.findById(orgId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException(
                    "Organization not found: " + orgId))
            .flatMap(organization -> {
                try {
                    // Create branding config
                    final BrandingConfig brandingConfig =
                        new BrandingConfig(
                            logoUrl, primaryColor, secondaryColor);

                    // Serialize to JSON string
                    final String brandingJson =
                        objectMapper.writeValueAsString(brandingConfig);
                    organization.branding = brandingJson;

                    // Persist updated organization
                    return organizationRepository.persist(organization);
                } catch (JsonProcessingException e) {
                    return Uni.createFrom().failure(
                        new RuntimeException(
                            "Failed to serialize branding config to JSON",
                            e)
                    );
                }
            });
    }

    /**
     * Retrieves an organization by ID.
     *
     * @param orgId The organization ID
     * @return Uni containing the Organization if found, or null
     */
    public Uni<Organization> getOrganization(final UUID orgId) {
        return organizationRepository.findById(orgId);
    }

    /**
     * Retrieves all organizations that a user is a member of.
     *
     * @param userId The user ID
     * @return Multi stream of Organizations the user belongs to
     */
    public Multi<Organization> getUserOrganizations(final UUID userId) {
        return orgMemberRepository.findByUserId(userId)
            .onItem().transformToMulti(members ->
                Multi.createFrom().iterable(members))
            .flatMap(member ->
                organizationRepository.findById(member.id.orgId)
                    .toMulti()
            );
    }

    /**
     * Extracts the domain from an email address.
     * <p>
     * Example: "user@company.com" returns "company.com"
     * </p>
     *
     * @param email The email address
     * @return The domain portion of the email
     * @throws IllegalArgumentException if email format is invalid
     */
    private String extractEmailDomain(final String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException(
                "Invalid email format: " + email);
        }
        final int atIndex = email.lastIndexOf('@');
        return email.substring(atIndex + 1);
    }
}
