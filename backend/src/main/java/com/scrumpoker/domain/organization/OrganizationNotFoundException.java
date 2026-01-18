package com.scrumpoker.domain.organization;

import java.util.UUID;

/**
 * Exception thrown when an organization cannot be found or has been deleted.
 *
 * <p>This exception is thrown by {@link OrganizationService} when attempting to access
 * an organization that does not exist in the database or has been soft-deleted.</p>
 *
 * @see OrganizationService
 */
public class OrganizationNotFoundException extends RuntimeException {

    private final UUID orgId;

    /**
     * Creates a new OrganizationNotFoundException.
     *
     * @param orgId the UUID of the organization that was not found
     */
    public OrganizationNotFoundException(UUID orgId) {
        super("Organization not found: " + orgId);
        this.orgId = orgId;
    }

    /**
     * Creates a new OrganizationNotFoundException with a cause.
     *
     * @param orgId the UUID of the organization that was not found
     * @param cause the underlying cause of this exception
     */
    public OrganizationNotFoundException(UUID orgId, Throwable cause) {
        super("Organization not found: " + orgId, cause);
        this.orgId = orgId;
    }

    /**
     * Gets the UUID of the organization that was not found.
     *
     * @return the organization UUID
     */
    public UUID getOrgId() {
        return orgId;
    }
}
