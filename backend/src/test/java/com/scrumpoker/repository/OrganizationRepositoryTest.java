package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.Organization;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    @Transactional
    void setUp() {
        organizationRepository.deleteAll().await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindById() {
        // Given: a new organization
        Organization org = createTestOrganization("Acme Corp", "acme.com");

        // When: persisting the organization
        organizationRepository.persist(org).await().indefinitely();

        // Then: the organization can be retrieved
        Organization found = organizationRepository.findById(org.orgId).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.name).isEqualTo("Acme Corp");
        assertThat(found.domain).isEqualTo("acme.com");
    }

    @Test
    @Transactional
    void testJsonbSsoConfig() {
        // Given: organization with JSONB SSO config
        Organization org = createTestOrganization("SSO Org", "sso.com");
        String ssoConfig = "{\"provider\":\"okta\",\"issuer\":\"https://okta.com\",\"clientId\":\"abc123\"}";
        org.ssoConfig = ssoConfig;

        // When: persisting and retrieving
        organizationRepository.persist(org).await().indefinitely();
        Organization found = organizationRepository.findById(org.orgId).await().indefinitely();

        // Then: JSONB SSO config persists correctly
        assertThat(found.ssoConfig).isEqualTo(ssoConfig);
        assertThat(found.ssoConfig).contains("okta");
    }

    @Test
    @Transactional
    void testFindByDomain() {
        // Given: persisted organization
        Organization org = createTestOrganization("Test Org", "test.com");
        organizationRepository.persist(org).await().indefinitely();

        // When: finding by domain
        Organization found = organizationRepository.findByDomain("test.com").await().indefinitely();

        // Then: organization is found
        assertThat(found).isNotNull();
        assertThat(found.orgId).isEqualTo(org.orgId);
    }

    @Test
    @Transactional
    void testSearchByName() {
        // Given: multiple organizations
        Organization org1 = createTestOrganization("Acme Corporation", "acme.com");
        Organization org2 = createTestOrganization("Acme Inc", "acmeinc.com");
        Organization org3 = createTestOrganization("Test Corp", "test.com");

        organizationRepository.persist(org1).await().indefinitely();
        organizationRepository.persist(org2).await().indefinitely();
        organizationRepository.persist(org3).await().indefinitely();

        // When: searching by name pattern
        List<Organization> results = organizationRepository.searchByName("acme").await().indefinitely();

        // Then: matching organizations are returned
        assertThat(results).hasSize(2);
        assertThat(results).extracting(o -> o.name)
                .containsExactlyInAnyOrder("Acme Corporation", "Acme Inc");
    }

    @Test
    @Transactional
    void testCountAll() {
        // Given: multiple organizations
        organizationRepository.persist(createTestOrganization("Org1", "org1.com")).await().indefinitely();
        organizationRepository.persist(createTestOrganization("Org2", "org2.com")).await().indefinitely();

        // When: counting all organizations
        Long count = organizationRepository.countAll().await().indefinitely();

        // Then: correct count is returned
        assertThat(count).isEqualTo(2);
    }

    @Test
    @Transactional
    void testUpdateOrganization() {
        // Given: persisted organization
        Organization org = createTestOrganization("Old Name", "old.com");
        organizationRepository.persist(org).await().indefinitely();

        // When: updating organization
        org.name = "New Name";
        org.domain = "new.com";
        organizationRepository.persist(org).await().indefinitely();

        // Then: changes are persisted
        Organization updated = organizationRepository.findById(org.orgId).await().indefinitely();
        assertThat(updated.name).isEqualTo("New Name");
        assertThat(updated.domain).isEqualTo("new.com");
    }

    private Organization createTestOrganization(String name, String domain) {
        Organization org = new Organization();
        org.orgId = UUID.randomUUID();
        org.name = name;
        org.domain = domain;
        org.ssoConfig = "{}";
        org.branding = "{}";
        return org;
    }
}
