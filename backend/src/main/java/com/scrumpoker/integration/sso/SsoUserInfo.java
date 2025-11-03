package com.scrumpoker.integration.sso;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object containing user information extracted from
 * SSO authentication (OIDC ID token or SAML2 assertion).
 * This DTO is used to pass SSO user information from the SsoAdapter
 * to UserService for JIT (Just-In-Time) user provisioning.
 * <p>
 * Unlike OAuthUserInfo (used for social login), SsoUserInfo includes:
 * </p>
 * <ul>
 * <li>Organization ID (SSO is organization-scoped)</li>
 * <li>Groups/roles from IdP for RBAC mapping</li>
 * <li>Protocol identifier (oidc or saml2)</li>
 * </ul>
 */
public final class SsoUserInfo {

    /** Maximum length for subject field. */
    private static final int MAX_SUBJECT_LENGTH = 255;

    /** Maximum length for email field. */
    private static final int MAX_EMAIL_LENGTH = 255;

    /** Maximum length for name field. */
    private static final int MAX_NAME_LENGTH = 100;

    /** Maximum length for provider field. */
    private static final int MAX_PROVIDER_LENGTH = 50;

    /**
     * SSO subject identifier (unique user ID from IdP).
     * For OIDC: Extracted from 'sub' claim in ID token.
     * For SAML2: Extracted from NameID or subject attribute.
     */
    @NotNull
    @Size(max = MAX_SUBJECT_LENGTH)
    private String subject;

    /**
     * User's email address.
     * For OIDC: Extracted from 'email' claim in ID token.
     * For SAML2: Extracted from email attribute
     * (mapping configured per organization).
     */
    @NotNull
    @Email
    @Size(max = MAX_EMAIL_LENGTH)
    private String email;

    /**
     * User's display name.
     * For OIDC: Extracted from 'name' claim in ID token.
     * For SAML2: Extracted from name attribute
     * (mapping configured per organization).
     */
    @NotNull
    @Size(max = MAX_NAME_LENGTH)
    private String name;

    /**
     * SSO protocol used for authentication.
     * Either "oidc" or "saml2".
     * This is set by the adapter to track which protocol
     * authenticated the user.
     */
    @NotNull
    @Size(max = MAX_PROVIDER_LENGTH)
    private String protocol;

    /**
     * Organization ID that owns this SSO configuration.
     * This is CRITICAL for SSO flows as users are automatically
     * assigned to this organization on JIT provisioning.
     * Extracted from Organization.ssoConfig during authentication.
     */
    @NotNull
    private UUID organizationId;

    /**
     * Groups or roles assigned to the user by the IdP.
     * For OIDC: Extracted from 'groups' or 'roles' claim.
     * For SAML2: Extracted from groups attribute
     * (mapping configured per organization).
     * Used for RBAC role mapping (e.g., IdP group
     * "Admins" → ORG_ADMIN role).
     */
    private List<String> groups = new ArrayList<>();

    /**
     * Default constructor for Jackson deserialization.
     */
    public SsoUserInfo() {
    }

    /**
     * Full constructor for creating SsoUserInfo instances.
     *
     * @param subjectId SSO subject identifier from IdP
     * @param emailAddress User's email from IdP
     * @param displayName User's display name from IdP
     * @param ssoProtocol SSO protocol ("oidc" or "saml2")
     * @param orgId Organization ID owning this SSO config
     * @param userGroups Groups/roles from IdP
     */
    public SsoUserInfo(final String subjectId,
                       final String emailAddress,
                       final String displayName,
                       final String ssoProtocol,
                       final UUID orgId,
                       final List<String> userGroups) {
        this.subject = subjectId;
        this.email = emailAddress;
        this.name = displayName;
        this.protocol = ssoProtocol;
        this.organizationId = orgId;
        this.groups = userGroups != null ? userGroups : new ArrayList<>();
    }

    /**
     * Simplified constructor without groups.
     *
     * @param subjectId SSO subject identifier from IdP
     * @param emailAddress User's email from IdP
     * @param displayName User's display name from IdP
     * @param ssoProtocol SSO protocol ("oidc" or "saml2")
     * @param orgId Organization ID owning this SSO config
     */
    public SsoUserInfo(final String subjectId,
                       final String emailAddress,
                       final String displayName,
                       final String ssoProtocol,
                       final UUID orgId) {
        this(subjectId, emailAddress, displayName,
                ssoProtocol, orgId, new ArrayList<>());
    }

    // Getters and Setters

    /**
     * Gets the SSO subject identifier.
     *
     * @return SSO subject identifier
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the SSO subject identifier.
     *
     * @param subjectId SSO subject identifier
     */
    public void setSubject(final String subjectId) {
        this.subject = subjectId;
    }

    /**
     * Gets the user's email address.
     *
     * @return User's email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the user's email address.
     *
     * @param emailAddress User's email address
     */
    public void setEmail(final String emailAddress) {
        this.email = emailAddress;
    }

    /**
     * Gets the user's display name.
     *
     * @return User's display name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user's display name.
     *
     * @param displayName User's display name
     */
    public void setName(final String displayName) {
        this.name = displayName;
    }

    /**
     * Gets the SSO protocol.
     *
     * @return SSO protocol ("oidc" or "saml2")
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Sets the SSO protocol.
     *
     * @param ssoProtocol SSO protocol ("oidc" or "saml2")
     */
    public void setProtocol(final String ssoProtocol) {
        this.protocol = ssoProtocol;
    }

    /**
     * Gets the organization ID.
     *
     * @return Organization ID
     */
    public UUID getOrganizationId() {
        return organizationId;
    }

    /**
     * Sets the organization ID.
     *
     * @param orgId Organization ID
     */
    public void setOrganizationId(final UUID orgId) {
        this.organizationId = orgId;
    }

    /**
     * Gets the user's groups from IdP.
     *
     * @return List of groups/roles
     */
    public List<String> getGroups() {
        return groups;
    }

    /**
     * Sets the user's groups from IdP.
     *
     * @param userGroups List of groups/roles
     */
    public void setGroups(final List<String> userGroups) {
        this.groups = userGroups;
    }

    /**
     * Extracts the email domain from the user's email address.
     * Used for organization domain verification.
     *
     * @return Email domain (e.g., "acme.com" from
     *         "user@acme.com")
     */
    public String getEmailDomain() {
        if (email == null || !email.contains("@")) {
            return null;
        }
        return email.substring(email.indexOf('@') + 1).toLowerCase();
    }

    @Override
    public String toString() {
        return "SsoUserInfo{"
                + "subject='" + subject + '\''
                + ", email='" + email + '\''
                + ", name='" + name + '\''
                + ", protocol='" + protocol + '\''
                + ", organizationId=" + organizationId
                + ", groups=" + groups
                + '}';
    }
}
