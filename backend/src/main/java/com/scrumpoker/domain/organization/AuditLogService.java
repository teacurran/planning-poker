package com.scrumpoker.domain.organization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.event.AuditEvent;
import com.scrumpoker.repository.AuditLogRepository;
import com.scrumpoker.repository.OrganizationRepository;
import com.scrumpoker.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for enterprise audit logging and compliance tracking.
 * <p>
 * This service provides asynchronous audit logging for security and
 * administrative events in Enterprise-tier organizations. Events are
 * published via CDI events and processed asynchronously to avoid
 * blocking main request threads.
 * </p>
 * <p>
 * <strong>Audited Events:</strong>
 * <ul>
 *   <li>User authentication (SSO login, logout)</li>
 *   <li>Organization configuration changes (SSO settings, branding)</li>
 *   <li>Member management (invite, role change, removal)</li>
 *   <li>Administrative actions (room deletion, user account operations)</li>
 *   <li>Sensitive data access</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Storage:</strong> Audit logs are stored in a time-partitioned
 * {@link AuditLog} table (partitioned by month) with immutable records.
 * Each entry includes contextual data: IP address, user agent, timestamp,
 * and change details (JSONB).
 * </p>
 * <p>
 * <strong>Error Handling:</strong> Audit logging failures are logged but
 * do NOT propagate exceptions to business logic. This ensures that audit
 * logging issues never break core functionality.
 * </p>
 */
@ApplicationScoped
public class AuditLogService {

    /**
     * Logger instance for this class.
     */
    private static final Logger LOG = Logger.getLogger(AuditLogService.class);

    /**
     * Repository for audit log persistence operations.
     */
    @Inject
    private AuditLogRepository auditLogRepository;

    /**
     * Repository for organization entity lookups.
     */
    @Inject
    private OrganizationRepository organizationRepository;

    /**
     * Repository for user entity lookups.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * CDI event publisher for asynchronous audit event processing.
     */
    @Inject
    private Event<AuditEvent> auditEventPublisher;

    /**
     * JSON serialization mapper for metadata objects.
     */
    @Inject
    private ObjectMapper objectMapper;

    // Action type constants

    /**
     * Action: User authenticated via SSO.
     */
    public static final String ACTION_SSO_LOGIN = "SSO_LOGIN";

    /**
     * Action: Organization configuration updated.
     */
    public static final String ACTION_ORG_CONFIG_UPDATED = "ORG_CONFIG_UPDATED";

    /**
     * Action: SSO configuration updated.
     */
    public static final String ACTION_SSO_CONFIG_UPDATED = "SSO_CONFIG_UPDATED";

    /**
     * Action: Organization branding updated.
     */
    public static final String ACTION_BRANDING_UPDATED = "BRANDING_UPDATED";

    /**
     * Action: Member added to organization.
     */
    public static final String ACTION_MEMBER_ADDED = "MEMBER_ADDED";

    /**
     * Action: Member removed from organization.
     */
    public static final String ACTION_MEMBER_REMOVED = "MEMBER_REMOVED";

    /**
     * Action: Member role changed.
     */
    public static final String ACTION_MEMBER_ROLE_CHANGED = "MEMBER_ROLE_CHANGED";

    /**
     * Action: Room deleted.
     */
    public static final String ACTION_ROOM_DELETED = "ROOM_DELETED";

    /**
     * Action: Sensitive data accessed.
     */
    public static final String ACTION_SENSITIVE_DATA_ACCESSED = "SENSITIVE_DATA_ACCESSED";

    // Resource type constants

    /**
     * Resource type: Organization.
     */
    public static final String RESOURCE_TYPE_ORGANIZATION = "ORGANIZATION";

    /**
     * Resource type: User.
     */
    public static final String RESOURCE_TYPE_USER = "USER";

    /**
     * Resource type: Room.
     */
    public static final String RESOURCE_TYPE_ROOM = "ROOM";

    /**
     * Resource type: SSO configuration.
     */
    public static final String RESOURCE_TYPE_SSO_CONFIG = "SSO_CONFIG";

    /**
     * Logs an audit event with full context (fire-and-forget).
     * <p>
     * This method publishes an audit event asynchronously via CDI events.
     * The actual persistence is handled by {@link #processAuditEvent(AuditEvent)}
     * which runs in a separate transaction context.
     * </p>
     * <p>
     * <strong>Usage:</strong> Call this method from business logic after
     * performing auditable actions. The method returns immediately without
     * waiting for the audit log to be persisted.
     * </p>
     *
     * @param orgId The organization ID (required)
     * @param userId The user ID who performed the action (may be null for system actions)
     * @param action The action type (e.g., "SSO_LOGIN", "MEMBER_ADDED")
     * @param resourceType The resource type affected (e.g., "ORGANIZATION", "USER")
     * @param resourceId The specific resource ID (may be null)
     * @param ipAddress The client IP address (may be null)
     * @param userAgent The client User-Agent header (may be null)
     * @param changeDetails Additional context as key-value map (may be null)
     */
    public void logEvent(final UUID orgId,
                         final UUID userId,
                         final String action,
                         final String resourceType,
                         final String resourceId,
                         final String ipAddress,
                         final String userAgent,
                         final Map<String, Object> changeDetails) {
        try {
            // Serialize metadata to JSON string
            final String metadataJson;
            if (changeDetails != null && !changeDetails.isEmpty()) {
                try {
                    metadataJson = objectMapper.writeValueAsString(changeDetails);
                } catch (JsonProcessingException e) {
                    LOG.errorf(e, "Failed to serialize audit event metadata for "
                            + "action %s (orgId: %s)", action, orgId);
                    // Use empty JSON object as fallback
                    return;
                }
            } else {
                metadataJson = null;
            }

            // Truncate user agent if too long (max 500 chars in AuditLog entity)
            final String truncatedUserAgent = truncateString(userAgent, 500);

            // Truncate IP address if too long (max 45 chars in AuditLog entity)
            final String truncatedIpAddress = truncateString(ipAddress, 45);

            // Build and fire audit event
            final AuditEvent event = AuditEvent.builder()
                    .orgId(orgId)
                    .userId(userId)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .ipAddress(truncatedIpAddress)
                    .userAgent(truncatedUserAgent)
                    .metadata(metadataJson)
                    .timestamp(Instant.now())
                    .build();

            auditEventPublisher.fireAsync(event);

            LOG.debugf("Fired audit event: action=%s, orgId=%s, userId=%s, resourceType=%s",
                    action, orgId, userId, resourceType);

        } catch (Exception e) {
            // NEVER propagate exceptions from audit logging
            LOG.errorf(e, "Failed to fire audit event: action=%s, orgId=%s, userId=%s",
                    action, orgId, userId);
        }
    }

    /**
     * Logs an audit event without change details.
     * <p>
     * Convenience method for events that don't require before/after values
     * or additional context.
     * </p>
     *
     * @param orgId The organization ID (required)
     * @param userId The user ID who performed the action (may be null)
     * @param action The action type
     * @param resourceType The resource type affected
     * @param resourceId The specific resource ID (may be null)
     * @param ipAddress The client IP address (may be null)
     * @param userAgent The client User-Agent header (may be null)
     */
    public void logEvent(final UUID orgId,
                         final UUID userId,
                         final String action,
                         final String resourceType,
                         final String resourceId,
                         final String ipAddress,
                         final String userAgent) {
        logEvent(orgId, userId, action, resourceType, resourceId,
                ipAddress, userAgent, null);
    }

    /**
     * Logs an SSO login event.
     * <p>
     * Convenience method for SSO authentication events.
     * </p>
     *
     * @param orgId The organization ID
     * @param userId The user ID who logged in
     * @param ipAddress The client IP address
     * @param userAgent The client User-Agent header
     */
    public void logSsoLogin(final UUID orgId,
                            final UUID userId,
                            final String ipAddress,
                            final String userAgent) {
        logEvent(orgId, userId, ACTION_SSO_LOGIN, RESOURCE_TYPE_USER,
                userId.toString(), ipAddress, userAgent);
    }

    /**
     * Logs an organization configuration change event with before/after values.
     * <p>
     * The changeDetails map should contain "before" and "after" keys with
     * the previous and new configuration values.
     * </p>
     *
     * @param orgId The organization ID
     * @param userId The user ID who made the change
     * @param ipAddress The client IP address
     * @param userAgent The client User-Agent header
     * @param changeDetails Map with "before" and "after" configuration values
     */
    public void logOrgConfigChange(final UUID orgId,
                                    final UUID userId,
                                    final String ipAddress,
                                    final String userAgent,
                                    final Map<String, Object> changeDetails) {
        logEvent(orgId, userId, ACTION_ORG_CONFIG_UPDATED,
                RESOURCE_TYPE_ORGANIZATION, orgId.toString(),
                ipAddress, userAgent, changeDetails);
    }

    /**
     * Logs a member added event.
     * <p>
     * The changeDetails map should include the assigned role.
     * </p>
     *
     * @param orgId The organization ID
     * @param actorUserId The user ID who performed the action
     * @param addedUserId The user ID of the member being added
     * @param role The role assigned to the new member
     * @param ipAddress The client IP address
     * @param userAgent The client User-Agent header
     */
    public void logMemberAdded(final UUID orgId,
                               final UUID actorUserId,
                               final UUID addedUserId,
                               final OrgRole role,
                               final String ipAddress,
                               final String userAgent) {
        final Map<String, Object> details = Map.of(
                "addedUserId", addedUserId.toString(),
                "role", role.name()
        );
        logEvent(orgId, actorUserId, ACTION_MEMBER_ADDED,
                RESOURCE_TYPE_USER, addedUserId.toString(),
                ipAddress, userAgent, details);
    }

    /**
     * Logs a member removed event.
     *
     * @param orgId The organization ID
     * @param actorUserId The user ID who performed the action
     * @param removedUserId The user ID of the member being removed
     * @param ipAddress The client IP address
     * @param userAgent The client User-Agent header
     */
    public void logMemberRemoved(final UUID orgId,
                                 final UUID actorUserId,
                                 final UUID removedUserId,
                                 final String ipAddress,
                                 final String userAgent) {
        final Map<String, Object> details = Map.of(
                "removedUserId", removedUserId.toString()
        );
        logEvent(orgId, actorUserId, ACTION_MEMBER_REMOVED,
                RESOURCE_TYPE_USER, removedUserId.toString(),
                ipAddress, userAgent, details);
    }

    /**
     * Logs a room deletion event.
     *
     * @param orgId The organization ID
     * @param userId The user ID who deleted the room
     * @param roomId The room ID
     * @param ipAddress The client IP address
     * @param userAgent The client User-Agent header
     */
    public void logRoomDeleted(final UUID orgId,
                               final UUID userId,
                               final String roomId,
                               final String ipAddress,
                               final String userAgent) {
        logEvent(orgId, userId, ACTION_ROOM_DELETED,
                RESOURCE_TYPE_ROOM, roomId,
                ipAddress, userAgent);
    }

    /**
     * Asynchronous observer method that processes audit events and persists them.
     * <p>
     * This method is triggered automatically when an {@link AuditEvent} is fired
     * via {@link #logEvent}. It runs in a separate thread pool to avoid blocking
     * the caller and operates in its own transaction context.
     * </p>
     * <p>
     * <strong>Error Handling:</strong> All exceptions are caught and logged.
     * No exceptions are propagated to ensure audit logging failures don't
     * affect business logic.
     * </p>
     *
     * @param event The audit event to process
     * @return A Uni that completes when the audit log is persisted
     */
    @WithTransaction
    public Uni<Void> processAuditEvent(@ObservesAsync final AuditEvent event) {
        LOG.debugf("Processing audit event: action=%s, orgId=%s, userId=%s",
                event.getAction(), event.getOrgId(), event.getUserId());

        return organizationRepository.findById(event.getOrgId())
                .onItem().transformToUni(organization -> {
                    if (organization == null) {
                        LOG.warnf("Cannot create audit log - organization not found: %s",
                                event.getOrgId());
                        return Uni.createFrom().voidItem();
                    }

                    // Load user entity (optional - may be null for system actions)
                    final Uni<com.scrumpoker.domain.user.User> userUni;
                    if (event.getUserId() != null) {
                        userUni = userRepository.findById(event.getUserId())
                                .onItem().invoke(user -> {
                                    if (user == null) {
                                        LOG.warnf("User not found for audit log (will store with null user): %s",
                                                event.getUserId());
                                    }
                                });
                    } else {
                        userUni = Uni.createFrom().nullItem();
                    }

                    return userUni.onItem().transformToUni(user -> {
                        // Create AuditLog entity
                        final AuditLog auditLog = new AuditLog();
                        auditLog.id = new AuditLogId(UUID.randomUUID(), event.getTimestamp());
                        auditLog.organization = organization;
                        auditLog.user = user;
                        auditLog.action = event.getAction();
                        auditLog.resourceType = event.getResourceType();
                        auditLog.resourceId = event.getResourceId();
                        auditLog.ipAddress = event.getIpAddress();
                        auditLog.userAgent = event.getUserAgent();
                        auditLog.metadata = event.getMetadata();

                        // Persist audit log
                        return auditLogRepository.persist(auditLog)
                                .onItem().invoke(() ->
                                        LOG.infof("Audit log created: logId=%s, action=%s, orgId=%s, userId=%s",
                                                auditLog.id.logId, event.getAction(), event.getOrgId(), event.getUserId())
                                )
                                .replaceWithVoid();
                    });
                })
                .onFailure().invoke(e -> {
                    // NEVER propagate exceptions from audit logging
                    LOG.errorf(e, "Failed to persist audit log: action=%s, orgId=%s, userId=%s",
                            event.getAction(), event.getOrgId(), event.getUserId());
                })
                .onFailure().recoverWithNull();
    }

    /**
     * Extracts the client IP address from the HTTP request context.
     * <p>
     * Checks multiple headers in order of precedence:
     * <ol>
     *   <li>X-Forwarded-For (most common for proxied requests)</li>
     *   <li>X-Real-IP (common in Nginx configurations)</li>
     *   <li>Falls back to provided default (remote address)</li>
     * </ol>
     * </p>
     * <p>
     * X-Forwarded-For may contain multiple IPs (client, proxy1, proxy2, ...).
     * This method returns the first IP (original client).
     * </p>
     *
     * @param xForwardedFor The X-Forwarded-For header value (may be null)
     * @param xRealIp The X-Real-IP header value (may be null)
     * @param remoteAddress The remote address from request context (may be null)
     * @return The client IP address, or null if none available
     */
    public static String extractIpAddress(final String xForwardedFor,
                                          final String xRealIp,
                                          final String remoteAddress) {
        // Try X-Forwarded-For first (most accurate for proxied requests)
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For format: "client, proxy1, proxy2"
            // Take the first IP (original client)
            final String firstIp = xForwardedFor.split(",")[0].trim();
            if (!firstIp.isBlank()) {
                return firstIp;
            }
        }

        // Try X-Real-IP (Nginx)
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }

        // Fallback to remote address
        return remoteAddress;
    }

    /**
     * Truncates a string to the specified maximum length.
     * <p>
     * Used to ensure field values don't exceed database column limits.
     * </p>
     *
     * @param value The string to truncate (may be null)
     * @param maxLength The maximum length
     * @return The truncated string, or null if input is null
     */
    private static String truncateString(final String value, final int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
