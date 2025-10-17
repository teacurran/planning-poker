package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.OrgMember;
import com.scrumpoker.domain.organization.OrgMemberId;
import com.scrumpoker.domain.organization.OrgRole;
import com.scrumpoker.domain.organization.Organization;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OrgMemberRepository.
 * Tests CRUD operations with composite key and role-based queries.
 */
@QuarkusTest
class OrgMemberRepositoryTest {

    @Inject
    OrgMemberRepository orgMemberRepository;

    @Inject
    OrganizationRepository organizationRepository;

    @Inject
    UserRepository userRepository;

    private Organization testOrg;
    private User testUser;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));

        testOrg = createTestOrganization("Test Org", "test.com");
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.persist(testOrg)));

        testUser = createTestUser("orgmember@example.com", "google", "google-orgmember");
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(testUser)));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindByCompositeId(UniAsserter asserter) {
        // Given: a new org member with composite key
        OrgMember member = createTestOrgMember(testOrg, testUser, OrgRole.MEMBER);

        // When: persisting the org member
        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(member)));

        // Then: the org member can be retrieved by composite ID
        OrgMemberId id = new OrgMemberId(testOrg.orgId, testUser.userId);
        asserter.assertThat(() -> Panache.withTransaction(() -> orgMemberRepository.findById(id)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.role).isEqualTo(OrgRole.MEMBER);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByOrgId(UniAsserter asserter) {
        // Given: multiple members in an organization
        User user1 = createTestUser("user1@example.com", "google", "google-1");
        User user2 = createTestUser("user2@example.com", "google", "google-2");
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(user1)));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(user2)));

        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(createTestOrgMember(testOrg, user1, OrgRole.ADMIN))));
        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(createTestOrgMember(testOrg, user2, OrgRole.MEMBER))));

        // When: finding members by org ID
        // Then: all members are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> orgMemberRepository.findByOrgId(testOrg.orgId)), members -> {
            assertThat(members).hasSize(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByUserId(UniAsserter asserter) {
        // Given: user is member of multiple organizations
        Organization org2 = createTestOrganization("Org 2", "org2.com");
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.persist(org2)));

        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(createTestOrgMember(testOrg, testUser, OrgRole.MEMBER))));
        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(createTestOrgMember(org2, testUser, OrgRole.ADMIN))));

        // When: finding memberships by user ID
        // Then: all memberships are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> orgMemberRepository.findByUserId(testUser.userId)), memberships -> {
            assertThat(memberships).hasSize(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByOrgIdAndRole(UniAsserter asserter) {
        // Given: members with different roles
        User admin = createTestUser("admin@example.com", "google", "google-admin");
        User member = createTestUser("member@example.com", "google", "google-member");
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(admin)));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(member)));

        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(createTestOrgMember(testOrg, admin, OrgRole.ADMIN))));
        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(createTestOrgMember(testOrg, member, OrgRole.MEMBER))));

        // When: finding admins
        // Then: only admins are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> orgMemberRepository.findByOrgIdAndRole(testOrg.orgId, OrgRole.ADMIN)), admins -> {
            assertThat(admins).hasSize(1);
            assertThat(admins.get(0).role).isEqualTo(OrgRole.ADMIN);
        });
    }

    @Test
    @RunOnVertxContext
    void testIsAdmin(UniAsserter asserter) {
        // Given: an admin member
        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(createTestOrgMember(testOrg, testUser, OrgRole.ADMIN))));

        // When: checking if user is admin
        // Then: true is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> orgMemberRepository.isAdmin(testOrg.orgId, testUser.userId)), isAdmin -> {
            assertThat(isAdmin).isTrue();
        });
    }

    @Test
    @RunOnVertxContext
    void testIsAdminReturnsFalseForMember(UniAsserter asserter) {
        // Given: a non-admin member
        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(createTestOrgMember(testOrg, testUser, OrgRole.MEMBER))));

        // When: checking if user is admin
        // Then: false is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> orgMemberRepository.isAdmin(testOrg.orgId, testUser.userId)), isAdmin -> {
            assertThat(isAdmin).isFalse();
        });
    }

    @Test
    @RunOnVertxContext
    void testCountByOrgId(UniAsserter asserter) {
        // Given: multiple members
        User user1 = createTestUser("count1@example.com", "google", "google-count1");
        User user2 = createTestUser("count2@example.com", "google", "google-count2");
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(user1)));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(user2)));

        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(createTestOrgMember(testOrg, user1, OrgRole.MEMBER))));
        asserter.execute(() -> Panache.withTransaction(() -> orgMemberRepository.persist(createTestOrgMember(testOrg, user2, OrgRole.MEMBER))));

        // When: counting members
        // Then: correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> orgMemberRepository.countByOrgId(testOrg.orgId)), count -> {
            assertThat(count).isEqualTo(2);
        });
    }

    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        // DO NOT SET user.userId - let Hibernate auto-generate it
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
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

    private OrgMember createTestOrgMember(Organization org, User user, OrgRole role) {
        OrgMember member = new OrgMember();
        member.id = new OrgMemberId(org.orgId, user.userId);
        member.organization = org;
        member.user = user;
        member.role = role;
        return member;
    }
}
