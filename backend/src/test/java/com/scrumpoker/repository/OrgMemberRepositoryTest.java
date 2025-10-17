package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.OrgMember;
import com.scrumpoker.domain.organization.OrgMemberId;
import com.scrumpoker.domain.organization.OrgRole;
import com.scrumpoker.domain.organization.Organization;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
    @Transactional
    void setUp() {
        orgMemberRepository.deleteAll().await().indefinitely();
        organizationRepository.deleteAll().await().indefinitely();
        userRepository.deleteAll().await().indefinitely();

        testOrg = createTestOrganization("Test Org", "test.com");
        organizationRepository.persist(testOrg).await().indefinitely();

        testUser = createTestUser("orgmember@example.com", "google", "google-orgmember");
        userRepository.persist(testUser).await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindByCompositeId() {
        // Given: a new org member with composite key
        OrgMember member = createTestOrgMember(testOrg, testUser, OrgRole.MEMBER);

        // When: persisting the org member
        orgMemberRepository.persist(member).await().indefinitely();

        // Then: the org member can be retrieved by composite ID
        OrgMemberId id = new OrgMemberId(testOrg.orgId, testUser.userId);
        OrgMember found = orgMemberRepository.findById(id).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.role).isEqualTo(OrgRole.MEMBER);
    }

    @Test
    @Transactional
    void testFindByOrgId() {
        // Given: multiple members in an organization
        User user1 = createTestUser("user1@example.com", "google", "google-1");
        User user2 = createTestUser("user2@example.com", "google", "google-2");
        userRepository.persist(user1).await().indefinitely();
        userRepository.persist(user2).await().indefinitely();

        orgMemberRepository.persist(createTestOrgMember(testOrg, user1, OrgRole.ADMIN)).await().indefinitely();
        orgMemberRepository.persist(createTestOrgMember(testOrg, user2, OrgRole.MEMBER)).await().indefinitely();

        // When: finding members by org ID
        List<OrgMember> members = orgMemberRepository.findByOrgId(testOrg.orgId).await().indefinitely();

        // Then: all members are returned
        assertThat(members).hasSize(2);
    }

    @Test
    @Transactional
    void testFindByUserId() {
        // Given: user is member of multiple organizations
        Organization org2 = createTestOrganization("Org 2", "org2.com");
        organizationRepository.persist(org2).await().indefinitely();

        orgMemberRepository.persist(createTestOrgMember(testOrg, testUser, OrgRole.MEMBER)).await().indefinitely();
        orgMemberRepository.persist(createTestOrgMember(org2, testUser, OrgRole.ADMIN)).await().indefinitely();

        // When: finding memberships by user ID
        List<OrgMember> memberships = orgMemberRepository.findByUserId(testUser.userId).await().indefinitely();

        // Then: all memberships are returned
        assertThat(memberships).hasSize(2);
    }

    @Test
    @Transactional
    void testFindByOrgIdAndRole() {
        // Given: members with different roles
        User admin = createTestUser("admin@example.com", "google", "google-admin");
        User member = createTestUser("member@example.com", "google", "google-member");
        userRepository.persist(admin).await().indefinitely();
        userRepository.persist(member).await().indefinitely();

        orgMemberRepository.persist(createTestOrgMember(testOrg, admin, OrgRole.ADMIN)).await().indefinitely();
        orgMemberRepository.persist(createTestOrgMember(testOrg, member, OrgRole.MEMBER)).await().indefinitely();

        // When: finding admins
        List<OrgMember> admins = orgMemberRepository.findByOrgIdAndRole(testOrg.orgId, OrgRole.ADMIN)
                .await().indefinitely();

        // Then: only admins are returned
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).role).isEqualTo(OrgRole.ADMIN);
    }

    @Test
    @Transactional
    void testIsAdmin() {
        // Given: an admin member
        orgMemberRepository.persist(createTestOrgMember(testOrg, testUser, OrgRole.ADMIN)).await().indefinitely();

        // When: checking if user is admin
        Boolean isAdmin = orgMemberRepository.isAdmin(testOrg.orgId, testUser.userId).await().indefinitely();

        // Then: true is returned
        assertThat(isAdmin).isTrue();
    }

    @Test
    @Transactional
    void testIsAdminReturnsFalseForMember() {
        // Given: a non-admin member
        orgMemberRepository.persist(createTestOrgMember(testOrg, testUser, OrgRole.MEMBER)).await().indefinitely();

        // When: checking if user is admin
        Boolean isAdmin = orgMemberRepository.isAdmin(testOrg.orgId, testUser.userId).await().indefinitely();

        // Then: false is returned
        assertThat(isAdmin).isFalse();
    }

    @Test
    @Transactional
    void testCountByOrgId() {
        // Given: multiple members
        User user1 = createTestUser("count1@example.com", "google", "google-count1");
        User user2 = createTestUser("count2@example.com", "google", "google-count2");
        userRepository.persist(user1).await().indefinitely();
        userRepository.persist(user2).await().indefinitely();

        orgMemberRepository.persist(createTestOrgMember(testOrg, user1, OrgRole.MEMBER)).await().indefinitely();
        orgMemberRepository.persist(createTestOrgMember(testOrg, user2, OrgRole.MEMBER)).await().indefinitely();

        // When: counting members
        Long count = orgMemberRepository.countByOrgId(testOrg.orgId).await().indefinitely();

        // Then: correct count is returned
        assertThat(count).isEqualTo(2);
    }

    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        user.userId = UUID.randomUUID();
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
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

    private OrgMember createTestOrgMember(Organization org, User user, OrgRole role) {
        OrgMember member = new OrgMember();
        member.id = new OrgMemberId(org.orgId, user.userId);
        member.organization = org;
        member.user = user;
        member.role = role;
        return member;
    }
}
