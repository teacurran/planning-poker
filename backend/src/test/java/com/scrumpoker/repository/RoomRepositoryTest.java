package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.Organization;
import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RoomRepository.
 * Tests CRUD operations with String IDs, JSONB field serialization, relationship navigation,
 * and soft delete behavior using Testcontainers PostgreSQL.
 */
@QuarkusTest
class RoomRepositoryTest {

    @Inject
    RoomRepository roomRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    OrganizationRepository organizationRepository;

    private User testOwner;
    private Organization testOrg;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any existing test data
        roomRepository.deleteAll().await().indefinitely();
        userRepository.deleteAll().await().indefinitely();
        organizationRepository.deleteAll().await().indefinitely();

        // Create test owner user
        testOwner = createTestUser("owner@example.com", "google", "google-owner");
        userRepository.persist(testOwner).await().indefinitely();

        // Create test organization
        testOrg = createTestOrganization("Test Org", "test.com");
        organizationRepository.persist(testOrg).await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindById() {
        // Given: a new room with String ID
        Room room = createTestRoom("room01", "Test Room", testOwner);

        // When: persisting the room
        roomRepository.persist(room).await().indefinitely();

        // Then: the room can be retrieved by String ID
        Room found = roomRepository.findById("room01").await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.roomId).isEqualTo("room01");
        assertThat(found.title).isEqualTo("Test Room");
        assertThat(found.privacyMode).isEqualTo(PrivacyMode.PUBLIC);
        assertThat(found.config).isNotNull();
        assertThat(found.createdAt).isNotNull();
        assertThat(found.lastActiveAt).isNotNull();
        assertThat(found.deletedAt).isNull();
    }

    @Test
    @Transactional
    void testJsonbConfigField() {
        // Given: a room with JSONB config
        Room room = createTestRoom("room02", "JSONB Test Room", testOwner);
        String jsonConfig = "{\"deckType\":\"fibonacci\",\"timerEnabled\":true,\"timerDuration\":300}";
        room.config = jsonConfig;

        // When: persisting and retrieving the room
        roomRepository.persist(room).await().indefinitely();
        Room found = roomRepository.findById("room02").await().indefinitely();

        // Then: JSONB field round-trips correctly
        assertThat(found.config).isEqualTo(jsonConfig);
        assertThat(found.config).contains("fibonacci");
        assertThat(found.config).contains("timerEnabled");
    }

    @Test
    @Transactional
    void testRelationshipNavigationToOwner() {
        // Given: a room with an owner
        Room room = createTestRoom("room03", "Owner Test Room", testOwner);
        roomRepository.persist(room).await().indefinitely();

        // When: retrieving the room
        Room found = roomRepository.findById("room03").await().indefinitely();

        // Then: the owner relationship can be navigated
        assertThat(found.owner).isNotNull();
        User owner = found.owner;
        assertThat(owner.userId).isEqualTo(testOwner.userId);
        assertThat(owner.email).isEqualTo("owner@example.com");
    }

    @Test
    @Transactional
    void testRelationshipNavigationToOrganization() {
        // Given: a room with an organization
        Room room = createTestRoom("room04", "Org Test Room", testOwner);
        room.organization = testOrg;
        roomRepository.persist(room).await().indefinitely();

        // When: retrieving the room
        Room found = roomRepository.findById("room04").await().indefinitely();

        // Then: the organization relationship can be navigated
        assertThat(found.organization).isNotNull();
        Organization org = found.organization;
        assertThat(org.orgId).isEqualTo(testOrg.orgId);
        assertThat(org.name).isEqualTo("Test Org");
    }

    @Test
    @Transactional
    void testFindActiveByOwnerId() {
        // Given: multiple rooms with some soft-deleted
        Room activeRoom1 = createTestRoom("room05", "Active Room 1", testOwner);
        Room activeRoom2 = createTestRoom("room06", "Active Room 2", testOwner);
        Room deletedRoom = createTestRoom("room07", "Deleted Room", testOwner);
        deletedRoom.deletedAt = Instant.now();

        roomRepository.persist(activeRoom1).await().indefinitely();
        roomRepository.persist(activeRoom2).await().indefinitely();
        roomRepository.persist(deletedRoom).await().indefinitely();

        // When: finding active rooms by owner ID
        List<Room> activeRooms = roomRepository.findActiveByOwnerId(testOwner.userId)
                .await().indefinitely();

        // Then: only active rooms are returned
        assertThat(activeRooms).hasSize(2);
        assertThat(activeRooms).extracting(r -> r.roomId)
                .containsExactlyInAnyOrder("room05", "room06");
    }

    @Test
    @Transactional
    void testFindByOrgId() {
        // Given: rooms in an organization
        Room orgRoom1 = createTestRoom("room08", "Org Room 1", testOwner);
        orgRoom1.organization = testOrg;
        Room orgRoom2 = createTestRoom("room09", "Org Room 2", testOwner);
        orgRoom2.organization = testOrg;
        Room nonOrgRoom = createTestRoom("room10", "Non-Org Room", testOwner);

        roomRepository.persist(orgRoom1).await().indefinitely();
        roomRepository.persist(orgRoom2).await().indefinitely();
        roomRepository.persist(nonOrgRoom).await().indefinitely();

        // When: finding rooms by organization ID
        List<Room> orgRooms = roomRepository.findByOrgId(testOrg.orgId).await().indefinitely();

        // Then: only organization rooms are returned
        assertThat(orgRooms).hasSize(2);
        assertThat(orgRooms).extracting(r -> r.roomId)
                .containsExactlyInAnyOrder("room08", "room09");
    }

    @Test
    @Transactional
    void testFindPublicRooms() {
        // Given: rooms with different privacy modes
        Room publicRoom1 = createTestRoom("room11", "Public Room 1", testOwner);
        publicRoom1.privacyMode = PrivacyMode.PUBLIC;

        Room publicRoom2 = createTestRoom("room12", "Public Room 2", testOwner);
        publicRoom2.privacyMode = PrivacyMode.PUBLIC;

        Room privateRoom = createTestRoom("room13", "Private Room", testOwner);
        privateRoom.privacyMode = PrivacyMode.INVITE_ONLY;

        roomRepository.persist(publicRoom1).await().indefinitely();
        roomRepository.persist(publicRoom2).await().indefinitely();
        roomRepository.persist(privateRoom).await().indefinitely();

        // When: finding public rooms
        List<Room> publicRooms = roomRepository.findPublicRooms().await().indefinitely();

        // Then: only public rooms are returned
        assertThat(publicRooms).hasSize(2);
        assertThat(publicRooms).extracting(r -> r.roomId)
                .containsExactlyInAnyOrder("room11", "room12");
    }

    @Test
    @Transactional
    void testFindByPrivacyMode() {
        // Given: rooms with different privacy modes
        Room inviteOnlyRoom = createTestRoom("room14", "Invite Only Room", testOwner);
        inviteOnlyRoom.privacyMode = PrivacyMode.INVITE_ONLY;

        Room orgRestrictedRoom = createTestRoom("room15", "Org Restricted Room", testOwner);
        orgRestrictedRoom.privacyMode = PrivacyMode.ORG_RESTRICTED;

        roomRepository.persist(inviteOnlyRoom).await().indefinitely();
        roomRepository.persist(orgRestrictedRoom).await().indefinitely();

        // When: finding rooms by privacy mode
        List<Room> inviteOnlyRooms = roomRepository.findByPrivacyMode(PrivacyMode.INVITE_ONLY)
                .await().indefinitely();

        // Then: only matching privacy mode rooms are returned
        assertThat(inviteOnlyRooms).hasSize(1);
        assertThat(inviteOnlyRooms.get(0).roomId).isEqualTo("room14");
    }

    @Test
    @Transactional
    void testFindInactiveSince() {
        // Given: rooms with different lastActiveAt timestamps
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

        Room inactiveRoom = createTestRoom("room16", "Inactive Room", testOwner);
        inactiveRoom.lastActiveAt = twoDaysAgo;

        Room activeRoom = createTestRoom("room17", "Active Room", testOwner);
        activeRoom.lastActiveAt = oneHourAgo;

        roomRepository.persist(inactiveRoom).await().indefinitely();
        roomRepository.persist(activeRoom).await().indefinitely();

        // When: finding rooms inactive since 1 day ago
        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        List<Room> inactiveRooms = roomRepository.findInactiveSince(oneDayAgo)
                .await().indefinitely();

        // Then: only rooms inactive before the threshold are returned
        assertThat(inactiveRooms).hasSize(1);
        assertThat(inactiveRooms.get(0).roomId).isEqualTo("room16");
    }

    @Test
    @Transactional
    void testCountActiveByOwnerId() {
        // Given: rooms with some soft-deleted
        Room room1 = createTestRoom("room18", "Count Room 1", testOwner);
        Room room2 = createTestRoom("room19", "Count Room 2", testOwner);
        Room deletedRoom = createTestRoom("room20", "Deleted Count Room", testOwner);
        deletedRoom.deletedAt = Instant.now();

        roomRepository.persist(room1).await().indefinitely();
        roomRepository.persist(room2).await().indefinitely();
        roomRepository.persist(deletedRoom).await().indefinitely();

        // When: counting active rooms by owner
        Long count = roomRepository.countActiveByOwnerId(testOwner.userId).await().indefinitely();

        // Then: only active rooms are counted
        assertThat(count).isEqualTo(2);
    }

    @Test
    @Transactional
    void testCountByOrgId() {
        // Given: organization rooms
        Room orgRoom1 = createTestRoom("room21", "Org Count Room 1", testOwner);
        orgRoom1.organization = testOrg;
        Room orgRoom2 = createTestRoom("room22", "Org Count Room 2", testOwner);
        orgRoom2.organization = testOrg;

        roomRepository.persist(orgRoom1).await().indefinitely();
        roomRepository.persist(orgRoom2).await().indefinitely();

        // When: counting rooms by organization
        Long count = roomRepository.countByOrgId(testOrg.orgId).await().indefinitely();

        // Then: all organization rooms are counted
        assertThat(count).isEqualTo(2);
    }

    @Test
    @Transactional
    void testSoftDelete() {
        // Given: a persisted room
        Room room = createTestRoom("room23", "Soft Delete Test Room", testOwner);
        roomRepository.persist(room).await().indefinitely();

        // When: soft deleting the room
        room.deletedAt = Instant.now();
        roomRepository.persist(room).await().indefinitely();

        // Then: the room still exists but has deletedAt set
        Room found = roomRepository.findById("room23").await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.deletedAt).isNotNull();

        // And: soft-deleted room is excluded from active queries
        List<Room> activeRooms = roomRepository.findActiveByOwnerId(testOwner.userId)
                .await().indefinitely();
        assertThat(activeRooms).extracting(r -> r.roomId)
                .doesNotContain("room23");
    }

    @Test
    @Transactional
    void testUpdateRoom() {
        // Given: a persisted room
        Room room = createTestRoom("room24", "Update Test Room", testOwner);
        roomRepository.persist(room).await().indefinitely();

        // When: updating the room
        room.title = "Updated Room Title";
        room.privacyMode = PrivacyMode.INVITE_ONLY;
        room.config = "{\"deckType\":\"tshirt\"}";
        roomRepository.persist(room).await().indefinitely();

        // Then: the changes are persisted
        Room updated = roomRepository.findById("room24").await().indefinitely();
        assertThat(updated.title).isEqualTo("Updated Room Title");
        assertThat(updated.privacyMode).isEqualTo(PrivacyMode.INVITE_ONLY);
        assertThat(updated.config).contains("tshirt");
    }

    /**
     * Helper method to create test users.
     */
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

    /**
     * Helper method to create test organizations.
     */
    private Organization createTestOrganization(String name, String domain) {
        Organization org = new Organization();
        org.orgId = UUID.randomUUID();
        org.name = name;
        org.domain = domain;
        org.ssoConfig = "{}";
        org.branding = "{}";
        return org;
    }

    /**
     * Helper method to create test rooms with 6-character String IDs.
     */
    private Room createTestRoom(String roomId, String title, User owner) {
        Room room = new Room();
        room.roomId = roomId;
        room.title = title;
        room.owner = owner;
        room.privacyMode = PrivacyMode.PUBLIC;
        room.config = "{\"deckType\":\"fibonacci\",\"timerEnabled\":false}";
        return room;
    }
}
