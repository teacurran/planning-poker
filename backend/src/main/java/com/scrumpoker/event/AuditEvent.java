package com.scrumpoker.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * CDI event for asynchronous audit log processing.
 * <p>
 * This event is fired by business logic when an auditable action occurs
 * (SSO login, org config change, member management, etc.) and is processed
 * asynchronously by the {@link com.scrumpoker.domain.organization.AuditLogService}
 * to persist audit logs without blocking the main request thread.
 * </p>
 * <p>
 * The event carries all necessary data to create an {@link com.scrumpoker.domain.organization.AuditLog}
 * entry, including contextual information (IP address, user agent) and
 * change details (serialized as JSONB metadata).
 * </p>
 */
public class AuditEvent {

    /**
     * The organization ID associated with this audit event.
     * Required for all enterprise audit logs.
     */
    private final UUID orgId;

    /**
     * The user ID who performed the audited action.
     * May be null for system-initiated actions.
     */
    private final UUID userId;

    /**
     * The action type (e.g., "SSO_LOGIN", "ORG_CONFIG_UPDATED", "MEMBER_ADDED").
     * Should follow uppercase underscore naming convention.
     */
    private final String action;

    /**
     * The type of resource affected by the action (e.g., "ORGANIZATION", "USER", "ROOM").
     * Should follow uppercase naming convention.
     */
    private final String resourceType;

    /**
     * The ID of the specific resource affected (e.g., user ID, room ID).
     * May be null for organization-level actions.
     */
    private final String resourceId;

    /**
     * The IP address of the client making the request.
     * May be null for internal/system actions.
     */
    private final String ipAddress;

    /**
     * The User-Agent header from the HTTP request.
     * May be null for internal/system actions.
     */
    private final String userAgent;

    /**
     * Additional context for the audit event, already serialized as JSON string.
     * Used for storing before/after values for configuration changes,
     * role assignments, etc.
     */
    private final String metadata;

    /**
     * Timestamp when the event was created (not when it's processed).
     * Used as part of the AuditLog composite primary key.
     */
    private final Instant timestamp;

    /**
     * Private constructor - use Builder to create instances.
     */
    private AuditEvent(final Builder builder) {
        this.orgId = builder.orgId;
        this.userId = builder.userId;
        this.action = builder.action;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.ipAddress = builder.ipAddress;
        this.userAgent = builder.userAgent;
        this.metadata = builder.metadata;
        this.timestamp = builder.timestamp;
    }

    /**
     * Creates a new builder for constructing AuditEvent instances.
     *
     * @return A new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    /**
     * Gets the organization ID.
     *
     * @return The organization ID
     */
    public UUID getOrgId() {
        return orgId;
    }

    /**
     * Gets the user ID.
     *
     * @return The user ID
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * Gets the action.
     *
     * @return The action type
     */
    public String getAction() {
        return action;
    }

    /**
     * Gets the resource type.
     *
     * @return The resource type
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * Gets the resource ID.
     *
     * @return The resource ID
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * Gets the IP address.
     *
     * @return The IP address
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Gets the user agent.
     *
     * @return The user agent
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * Gets the metadata JSON string.
     *
     * @return The metadata as JSON string
     */
    public String getMetadata() {
        return metadata;
    }

    /**
     * Gets the event timestamp.
     *
     * @return The timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AuditEvent that = (AuditEvent) o;
        return Objects.equals(orgId, that.orgId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(action, that.action)
                && Objects.equals(resourceType, that.resourceType)
                && Objects.equals(resourceId, that.resourceId)
                && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, userId, action, resourceType, resourceId, timestamp);
    }

    @Override
    public String toString() {
        return "AuditEvent{"
                + "orgId=" + orgId
                + ", userId=" + userId
                + ", action='" + action + '\''
                + ", resourceType='" + resourceType + '\''
                + ", resourceId='" + resourceId + '\''
                + ", ipAddress='" + ipAddress + '\''
                + ", timestamp=" + timestamp
                + '}';
    }

    /**
     * Builder for constructing AuditEvent instances with fluent API.
     */
    public static class Builder {
        private UUID orgId;
        private UUID userId;
        private String action;
        private String resourceType;
        private String resourceId;
        private String ipAddress;
        private String userAgent;
        private String metadata;
        private Instant timestamp;

        private Builder() {
            // Private constructor - use AuditEvent.builder()
            this.timestamp = Instant.now(); // Default to current time
        }

        /**
         * Sets the organization ID.
         *
         * @param orgId The organization ID
         * @return This builder
         */
        public Builder orgId(final UUID orgId) {
            this.orgId = orgId;
            return this;
        }

        /**
         * Sets the user ID.
         *
         * @param userId The user ID
         * @return This builder
         */
        public Builder userId(final UUID userId) {
            this.userId = userId;
            return this;
        }

        /**
         * Sets the action.
         *
         * @param action The action type
         * @return This builder
         */
        public Builder action(final String action) {
            this.action = action;
            return this;
        }

        /**
         * Sets the resource type.
         *
         * @param resourceType The resource type
         * @return This builder
         */
        public Builder resourceType(final String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        /**
         * Sets the resource ID.
         *
         * @param resourceId The resource ID
         * @return This builder
         */
        public Builder resourceId(final String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        /**
         * Sets the IP address.
         *
         * @param ipAddress The IP address
         * @return This builder
         */
        public Builder ipAddress(final String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        /**
         * Sets the user agent.
         *
         * @param userAgent The user agent
         * @return This builder
         */
        public Builder userAgent(final String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Sets the metadata JSON string.
         *
         * @param metadata The metadata as JSON string
         * @return This builder
         */
        public Builder metadata(final String metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the timestamp. If not called, defaults to Instant.now().
         *
         * @param timestamp The timestamp
         * @return This builder
         */
        public Builder timestamp(final Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Builds the AuditEvent instance.
         *
         * @return A new AuditEvent
         * @throws IllegalArgumentException if required fields are missing
         */
        public AuditEvent build() {
            // Validate required fields
            if (orgId == null) {
                throw new IllegalArgumentException("orgId is required");
            }
            if (action == null || action.isBlank()) {
                throw new IllegalArgumentException("action is required");
            }
            if (resourceType == null || resourceType.isBlank()) {
                throw new IllegalArgumentException("resourceType is required");
            }
            if (timestamp == null) {
                throw new IllegalArgumentException("timestamp is required");
            }

            return new AuditEvent(this);
        }
    }
}
