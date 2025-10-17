package com.scrumpoker.integration.oauth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object containing user information extracted from OAuth2 ID token claims.
 * Maps ID token claims (sub, email, name, picture) to user profile fields.
 * This DTO is used to pass OAuth user information from the OAuth2Adapter to UserService
 * for JIT (Just-In-Time) user provisioning.
 */
public class OAuthUserInfo {

    /**
     * OAuth subject identifier (unique user ID from provider).
     * Extracted from 'sub' claim in ID token.
     */
    @NotNull
    @Size(max = 255)
    private String subject;

    /**
     * User's email address.
     * Extracted from 'email' claim in ID token.
     */
    @NotNull
    @Email
    @Size(max = 255)
    private String email;

    /**
     * User's display name.
     * Extracted from 'name' claim in ID token.
     */
    @NotNull
    @Size(max = 100)
    private String name;

    /**
     * URL to user's profile picture/avatar.
     * Extracted from 'picture' claim in ID token.
     * Google and Microsoft both provide this claim.
     */
    @Size(max = 500)
    private String avatarUrl;

    /**
     * OAuth provider identifier (e.g., "google", "microsoft").
     * This is not extracted from the token but set by the adapter
     * to track which provider authenticated the user.
     */
    @NotNull
    @Size(max = 50)
    private String provider;

    /**
     * Default constructor for Jackson deserialization.
     */
    public OAuthUserInfo() {
    }

    /**
     * Full constructor for creating OAuthUserInfo instances.
     *
     * @param subject OAuth subject identifier from 'sub' claim
     * @param email User's email from 'email' claim
     * @param name User's display name from 'name' claim
     * @param avatarUrl User's avatar URL from 'picture' claim
     * @param provider OAuth provider name ("google" or "microsoft")
     */
    public OAuthUserInfo(String subject, String email, String name,
                         String avatarUrl, String provider) {
        this.subject = subject;
        this.email = email;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.provider = provider;
    }

    // Getters and Setters

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    @Override
    public String toString() {
        return "OAuthUserInfo{" +
                "subject='" + subject + '\'' +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", provider='" + provider + '\'' +
                '}';
    }
}
