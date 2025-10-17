package com.scrumpoker.integration.oauth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object containing user information extracted from
 * OAuth2 ID token claims.
 * Maps ID token claims (sub, email, name, picture) to user profile
 * fields.
 * This DTO is used to pass OAuth user information from the
 * OAuth2Adapter to UserService for JIT (Just-In-Time) user
 * provisioning.
 */
public final class OAuthUserInfo {

    /** Maximum length for subject field. */
    private static final int MAX_SUBJECT_LENGTH = 255;

    /** Maximum length for email field. */
    private static final int MAX_EMAIL_LENGTH = 255;

    /** Maximum length for name field. */
    private static final int MAX_NAME_LENGTH = 100;

    /** Maximum length for avatar URL field. */
    private static final int MAX_AVATAR_URL_LENGTH = 500;

    /** Maximum length for provider field. */
    private static final int MAX_PROVIDER_LENGTH = 50;

    /**
     * OAuth subject identifier (unique user ID from provider).
     * Extracted from 'sub' claim in ID token.
     */
    @NotNull
    @Size(max = MAX_SUBJECT_LENGTH)
    private String subject;

    /**
     * User's email address.
     * Extracted from 'email' claim in ID token.
     */
    @NotNull
    @Email
    @Size(max = MAX_EMAIL_LENGTH)
    private String email;

    /**
     * User's display name.
     * Extracted from 'name' claim in ID token.
     */
    @NotNull
    @Size(max = MAX_NAME_LENGTH)
    private String name;

    /**
     * URL to user's profile picture/avatar.
     * Extracted from 'picture' claim in ID token.
     * Google and Microsoft both provide this claim.
     */
    @Size(max = MAX_AVATAR_URL_LENGTH)
    private String avatarUrl;

    /**
     * OAuth provider identifier (e.g., "google", "microsoft").
     * This is not extracted from the token but set by the adapter
     * to track which provider authenticated the user.
     */
    @NotNull
    @Size(max = MAX_PROVIDER_LENGTH)
    private String provider;

    /**
     * Default constructor for Jackson deserialization.
     */
    public OAuthUserInfo() {
    }

    /**
     * Full constructor for creating OAuthUserInfo instances.
     *
     * @param subjectId OAuth subject identifier from 'sub' claim
     * @param emailAddress User's email from 'email' claim
     * @param displayName User's display name from 'name' claim
     * @param avatarUrlParam User's avatar URL from 'picture' claim
     * @param providerName OAuth provider name ("google" or "microsoft")
     */
    public OAuthUserInfo(final String subjectId, final String emailAddress,
                         final String displayName,
                         final String avatarUrlParam,
                         final String providerName) {
        this.subject = subjectId;
        this.email = emailAddress;
        this.name = displayName;
        this.avatarUrl = avatarUrlParam;
        this.provider = providerName;
    }

    // Getters and Setters

    /**
     * Gets the OAuth subject identifier.
     *
     * @return OAuth subject identifier
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the OAuth subject identifier.
     *
     * @param subjectId OAuth subject identifier
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
     * Gets the user's avatar URL.
     *
     * @return User's avatar URL
     */
    public String getAvatarUrl() {
        return avatarUrl;
    }

    /**
     * Sets the user's avatar URL.
     *
     * @param avatarUrlParam User's avatar URL
     */
    public void setAvatarUrl(final String avatarUrlParam) {
        this.avatarUrl = avatarUrlParam;
    }

    /**
     * Gets the OAuth provider identifier.
     *
     * @return OAuth provider identifier
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Sets the OAuth provider identifier.
     *
     * @param providerName OAuth provider identifier
     */
    public void setProvider(final String providerName) {
        this.provider = providerName;
    }

    @Override
    public String toString() {
        return "OAuthUserInfo{"
                + "subject='" + this.subject + '\''
                + ", email='" + this.email + '\''
                + ", name='" + this.name + '\''
                + ", avatarUrl='" + this.avatarUrl + '\''
                + ", provider='" + this.provider + '\''
                + '}';
    }
}
