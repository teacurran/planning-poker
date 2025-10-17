package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.Organization;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/**
 * Reactive Panache repository for Organization entity.
 * Provides custom finder methods for domain-based lookups and subscription queries.
 */
@ApplicationScoped
public class OrganizationRepository implements PanacheRepositoryBase<Organization, UUID> {

    /**
     * Find organization by domain.
     * Used for SSO domain verification.
     *
     * @param domain The organization domain (e.g., "acme.com")
     * @return Uni containing the organization if found, or null if not found
     */
    public Uni<Organization> findByDomain(String domain) {
        return find("domain", domain).firstResult();
    }

    /**
     * Find all organizations with a specific subscription ID.
     *
     * @param subscriptionId The subscription ID to search for
     * @return Uni of list of organizations with the given subscription
     */
    public Uni<List<Organization>> findBySubscriptionId(UUID subscriptionId) {
        return find("subscription.subscriptionId", subscriptionId).list();
    }

    /**
     * Find organizations by name (case-insensitive partial match).
     * Useful for admin search functionality.
     *
     * @param namePattern The name pattern to search for
     * @return Uni of list of matching organizations
     */
    public Uni<List<Organization>> searchByName(String namePattern) {
        return find("lower(name) like lower(?1)", "%" + namePattern + "%").list();
    }

    /**
     * Count total organizations.
     *
     * @return Uni containing the total count of organizations
     */
    public Uni<Long> countAll() {
        return count();
    }
}
