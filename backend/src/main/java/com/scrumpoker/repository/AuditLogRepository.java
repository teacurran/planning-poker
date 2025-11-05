package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.AuditLog;
import com.scrumpoker.domain.organization.AuditLogId;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reactive Panache repository for AuditLog entity.
 * Uses composite primary key (logId + timestamp) for partition support.
 * AuditLog is partitioned by month for performance optimization.
 */
@ApplicationScoped
public class AuditLogRepository implements PanacheRepositoryBase<AuditLog, AuditLogId> {

    /**
     * Find all audit logs for an organization, ordered by timestamp descending.
     * Primary query for enterprise audit trail views.
     *
     * @param orgId The organization ID
     * @return Uni of list of audit log entries
     */
    public Uni<List<AuditLog>> findByOrgId(UUID orgId) {
        return find("organization.orgId = ?1 order by id.timestamp desc", orgId).list();
    }

    /**
     * Find audit logs within a date range.
     * Optimized for partition pruning when querying time-based partitions.
     *
     * @param startDate The start of the date range
     * @param endDate The end of the date range
     * @return Uni of list of audit logs within the date range
     */
    public Uni<List<AuditLog>> findByDateRange(Instant startDate, Instant endDate) {
        return find("id.timestamp >= ?1 and id.timestamp <= ?2 order by id.timestamp desc",
                    startDate, endDate).list();
    }

    /**
     * Find audit logs for an organization within a date range.
     *
     * @param orgId The organization ID
     * @param startDate The start of the date range
     * @param endDate The end of the date range
     * @return Uni of list of audit logs for the organization within the date range
     */
    public Uni<List<AuditLog>> findByOrgIdAndDateRange(UUID orgId, Instant startDate, Instant endDate) {
        return find("organization.orgId = ?1 and id.timestamp >= ?2 and id.timestamp <= ?3 order by id.timestamp desc",
                    orgId, startDate, endDate).list();
    }

    /**
     * Find all audit logs for a user.
     *
     * @param userId The user ID
     * @return Uni of list of audit log entries for the user
     */
    public Uni<List<AuditLog>> findByUserId(UUID userId) {
        return find("user.userId = ?1 order by id.timestamp desc", userId).list();
    }

    /**
     * Find audit logs by action type.
     *
     * @param action The action name (e.g., "USER_LOGIN", "ROOM_CREATED")
     * @return Uni of list of audit logs with the specified action
     */
    public Uni<List<AuditLog>> findByAction(String action) {
        return find("action = ?1 order by id.timestamp desc", action).list();
    }

    /**
     * Find audit logs by resource type and ID.
     * Used to track all actions on a specific resource.
     *
     * @param resourceType The resource type (e.g., "ROOM", "USER")
     * @param resourceId The resource ID
     * @return Uni of list of audit logs for the specified resource
     */
    public Uni<List<AuditLog>> findByResourceTypeAndId(String resourceType, String resourceId) {
        return find("resourceType = ?1 and resourceId = ?2 order by id.timestamp desc",
                    resourceType, resourceId).list();
    }

    /**
     * Find recent audit logs for an organization (last N days).
     *
     * @param orgId The organization ID
     * @param since The timestamp threshold
     * @return Uni of list of recent audit logs
     */
    public Uni<List<AuditLog>> findRecentByOrgId(UUID orgId, Instant since) {
        return find("organization.orgId = ?1 and id.timestamp >= ?2 order by id.timestamp desc",
                    orgId, since).list();
    }

    /**
     * Count audit logs for an organization.
     *
     * @param orgId The organization ID
     * @return Uni containing the audit log count
     */
    public Uni<Long> countByOrgId(UUID orgId) {
        return count("organization.orgId", orgId);
    }

    /**
     * Count audit logs by action type within a date range.
     * Useful for security analytics and reporting.
     *
     * @param action The action name
     * @param startDate The start of the date range
     * @param endDate The end of the date range
     * @return Uni containing the count of matching audit logs
     */
    public Uni<Long> countByActionAndDateRange(String action, Instant startDate, Instant endDate) {
        return count("action = ?1 and id.timestamp >= ?2 and id.timestamp <= ?3",
                     action, startDate, endDate);
    }

    /**
     * Find audit logs for an organization with filters and pagination.
     * Optimized for partition pruning when timestamp range is provided.
     *
     * @param orgId The organization ID
     * @param from Start timestamp (optional)
     * @param to End timestamp (optional)
     * @param action Action filter (optional)
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return Uni of paginated list of audit logs
     */
    public Uni<List<AuditLog>> findByOrgIdWithFilters(
            UUID orgId,
            Instant from,
            Instant to,
            String action,
            int page,
            int size) {

        // Build dynamic query based on provided filters
        StringBuilder query = new StringBuilder("organization.orgId = ?1");
        int paramIndex = 2;

        if (from != null) {
            query.append(" and id.timestamp >= ?").append(paramIndex++);
        }
        if (to != null) {
            query.append(" and id.timestamp <= ?").append(paramIndex++);
        }
        if (action != null && !action.isBlank()) {
            query.append(" and action = ?").append(paramIndex++);
        }

        query.append(" order by id.timestamp desc");

        // Execute query with parameters
        io.quarkus.panache.common.Page panachePage =
            io.quarkus.panache.common.Page.of(page, size);

        if (from != null && to != null && action != null && !action.isBlank()) {
            return find(query.toString(), orgId, from, to, action).page(panachePage).list();
        } else if (from != null && to != null) {
            return find(query.toString(), orgId, from, to).page(panachePage).list();
        } else if (from != null && action != null && !action.isBlank()) {
            return find(query.toString(), orgId, from, action).page(panachePage).list();
        } else if (to != null && action != null && !action.isBlank()) {
            return find(query.toString(), orgId, to, action).page(panachePage).list();
        } else if (from != null) {
            return find(query.toString(), orgId, from).page(panachePage).list();
        } else if (to != null) {
            return find(query.toString(), orgId, to).page(panachePage).list();
        } else if (action != null && !action.isBlank()) {
            return find(query.toString(), orgId, action).page(panachePage).list();
        } else {
            return find(query.toString(), orgId).page(panachePage).list();
        }
    }

    /**
     * Count audit logs for an organization with filters.
     *
     * @param orgId The organization ID
     * @param from Start timestamp (optional)
     * @param to End timestamp (optional)
     * @param action Action filter (optional)
     * @return Uni containing the count of matching audit logs
     */
    public Uni<Long> countByOrgIdWithFilters(
            UUID orgId,
            Instant from,
            Instant to,
            String action) {

        // Build dynamic query based on provided filters
        StringBuilder query = new StringBuilder("organization.orgId = ?1");
        int paramIndex = 2;

        if (from != null) {
            query.append(" and id.timestamp >= ?").append(paramIndex++);
        }
        if (to != null) {
            query.append(" and id.timestamp <= ?").append(paramIndex++);
        }
        if (action != null && !action.isBlank()) {
            query.append(" and action = ?").append(paramIndex++);
        }

        // Execute count query with parameters
        if (from != null && to != null && action != null && !action.isBlank()) {
            return count(query.toString(), orgId, from, to, action);
        } else if (from != null && to != null) {
            return count(query.toString(), orgId, from, to);
        } else if (from != null && action != null && !action.isBlank()) {
            return count(query.toString(), orgId, from, action);
        } else if (to != null && action != null && !action.isBlank()) {
            return count(query.toString(), orgId, to, action);
        } else if (from != null) {
            return count(query.toString(), orgId, from);
        } else if (to != null) {
            return count(query.toString(), orgId, to);
        } else if (action != null && !action.isBlank()) {
            return count(query.toString(), orgId, action);
        } else {
            return count(query.toString(), orgId);
        }
    }
}
