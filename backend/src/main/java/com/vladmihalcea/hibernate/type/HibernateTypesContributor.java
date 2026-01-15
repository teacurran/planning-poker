package com.vladmihalcea.hibernate.type;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.service.ServiceRegistry;

/**
 * Overrides the default contributor from hibernate-types to avoid registering array type descriptors
 * that are incompatible with Hibernate Reactive multi-id loaders. JSON handling is configured
 * explicitly via {@code @Type(JsonBinaryType.class)} on entities, so no implicit contributions
 * are required here.
 */
public class HibernateTypesContributor implements TypeContributor {

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        // Intentionally left blank.
    }
}
