package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.Organization;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OrganizationRepository.
 * Tests CRUD operations, JSONB fields, and custom finders.
 */
@QuarkusTest
class OrganizationRepositoryTest {

    @Inject
    OrganizationRepository organizationRepository;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.deleteAll()));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given: a new organization
        Organization org = createTestOrganization("Acme Corp", "acme.com");
        final UUID[] orgIdHolder = new UUID[1];

        // When: persisting the organization
        asserter.execute(() -> Panache.withTransaction(() ->
            organizationRepository.persist(org).map(o -> {
                orgIdHolder[0] = o.orgId;
                return o;
            })
        ));

        // Then: the organization can be retrieved
        asserter.assertThat(() -> Panache.withTransaction(() -> organizationRepository.findById(orgIdHolder[0])), found -> {
            assertThat(found).isNotNull();
            assertThat(found.name).isEqualTo("Acme Corp");
            assertThat(found.domain).isEqualTo("acme.com");
        });
    }

    @Test
    @RunOnVertxContext
    void testJsonbSsoConfig(UniAsserter asserter) {
        // Given: organization with JSONB SSO config
        Organization org = createTestOrganization("SSO Org", "sso.com");
        String ssoConfig = "{\"provider\":\"okta\",\"issuer\":\"https://okta.com\",\"clientId\":\"abc123\"}";
        org.ssoConfig = ssoConfig;
        final UUID[] orgIdHolder = new UUID[1];

        // When: persisting and retrieving
        asserter.execute(() -> Panache.withTransaction(() ->
            organizationRepository.persist(org).map(o -> {
                orgIdHolder[0] = o.orgId;
                return o;
            })
        ));

        // Then: JSONB SSO config persists correctly
        asserter.assertThat(() -> Panache.withTransaction(() -> organizationRepository.findById(orgIdHolder[0])), found -> {
            assertThat(found.ssoConfig).isEqualTo(ssoConfig);
            assertThat(found.ssoConfig).contains("okta");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByDomain(UniAsserter asserter) {
        // Given: persisted organization
        Organization org = createTestOrganization("Test Org", "test.com");
        final UUID[] orgIdHolder = new UUID[1];

        asserter.execute(() -> Panache.withTransaction(() ->
            organizationRepository.persist(org).map(o -> {
                orgIdHolder[0] = o.orgId;
                return o;
            })
        ));

        // When: finding by domain
        // Then: organization is found
        asserter.assertThat(() -> Panache.withTransaction(() -> organizationRepository.findByDomain("test.com")), found -> {
            assertThat(found).isNotNull();
            assertThat(found.orgId).isEqualTo(orgIdHolder[0]);
        });
    }

    @Test
    @RunOnVertxContext
    void testSearchByName(UniAsserter asserter) {
        // Given: multiple organizations
        Organization org1 = createTestOrganization("Acme Corporation", "acme.com");
        Organization org2 = createTestOrganization("Acme Inc", "acmeinc.com");
        Organization org3 = createTestOrganization("Test Corp", "test.com");

        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.persist(org1)));
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.persist(org2)));
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.persist(org3)));

        // When: searching by name pattern
        // Then: matching organizations are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> organizationRepository.searchByName("acme")), results -> {
            assertThat(results).hasSize(2);
            assertThat(results).extracting(o -> o.name)
                    .containsExactlyInAnyOrder("Acme Corporation", "Acme Inc");
        });
    }

    @Test
    @RunOnVertxContext
    void testCountAll(UniAsserter asserter) {
        // Given: multiple organizations
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.persist(createTestOrganization("Org1", "org1.com"))));
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.persist(createTestOrganization("Org2", "org2.com"))));

        // When: counting all organizations
        // Then: correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> organizationRepository.countAll()), count -> {
            assertThat(count).isEqualTo(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdateOrganization(UniAsserter asserter) {
        // Given: persisted organization
        Organization org = createTestOrganization("Old Name", "old.com");
        final UUID[] orgIdHolder = new UUID[1];

        asserter.execute(() -> Panache.withTransaction(() ->
            organizationRepository.persist(org).map(o -> {
                orgIdHolder[0] = o.orgId;
                return o;
            })
        ));

        // When: updating organization
        asserter.execute(() -> Panache.withTransaction(() ->
                organizationRepository.findById(orgIdHolder[0]).flatMap(o -> {
                    o.name = "New Name";
                    o.domain = "new.com";
                    return organizationRepository.persist(o);
                })
        ));

        // Then: changes are persisted
        asserter.assertThat(() -> Panache.withTransaction(() -> organizationRepository.findById(orgIdHolder[0])), updated -> {
            assertThat(updated.name).isEqualTo("New Name");
            assertThat(updated.domain).isEqualTo("new.com");
        });
    }

    private Organization createTestOrganization(String name, String domain) {
        Organization org = new Organization();
        // DO NOT SET org.orgId - let Hibernate auto-generate it
        org.name = name;
        org.domain = domain;
        org.ssoConfig = "{}";
        org.branding = "{}";
        org.createdAt = java.time.Instant.now();
        org.updatedAt = java.time.Instant.now();
        return org;
    }
}
