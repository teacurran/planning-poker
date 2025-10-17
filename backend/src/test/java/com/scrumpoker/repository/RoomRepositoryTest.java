package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.Organization;
import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up any existing test data
        asserter.execute(() -> Panache.withTransaction(() -> roomRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.deleteAll()));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given: a new room with String ID
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Room room = createTestRoom("room01", "Test Room", testOwner);

        // When: persisting the user and room
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user -> roomRepository.persist(room))
        ));

        // Then: the room can be retrieved by String ID
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findById("room01")), found -> {
            assertThat(found).isNotNull();
            assertThat(found.roomId).isEqualTo("room01");
            assertThat(found.title).isEqualTo("Test Room");
            assertThat(found.privacyMode).isEqualTo(PrivacyMode.PUBLIC);
            assertThat(found.config).isNotNull();
            assertThat(found.createdAt).isNotNull();
            assertThat(found.lastActiveAt).isNotNull();
            assertThat(found.deletedAt).isNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testJsonbConfigField(UniAsserter asserter) {
        // Given: a room with JSONB config
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Room room = createTestRoom("room02", "JSONB Test Room", testOwner);
        String jsonConfig = "{\"deckType\":\"fibonacci\",\"timerEnabled\":true,\"timerDuration\":300}";
        room.config = jsonConfig;

        // When: persisting and retrieving the room
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user -> roomRepository.persist(room))
        ));

        // Then: JSONB field round-trips correctly
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findById("room02")), found -> {
            assertThat(found.config).isEqualTo(jsonConfig);
            assertThat(found.config).contains("fibonacci");
            assertThat(found.config).contains("timerEnabled");
        });
    }

    @Test
    @RunOnVertxContext
    void testRelationshipNavigationToOwner(UniAsserter asserter) {
        // Given: a room with an owner
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Room room = createTestRoom("room03", "Owner Test Room", testOwner);
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user -> roomRepository.persist(room))
        ));

        // When: retrieving the room
        // Then: the owner relationship can be navigated
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findById("room03")), found -> {
            assertThat(found.owner).isNotNull();
            User owner = found.owner;
            assertThat(owner.userId).isEqualTo(testOwner.userId);
            assertThat(owner.email).isEqualTo("owner@example.com");
        });
    }

    @Test
    @RunOnVertxContext
    void testRelationshipNavigationToOrganization(UniAsserter asserter) {
        // Given: a room with an organization
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Organization testOrg = createTestOrganization("Test Org", "test.com");
        Room room = createTestRoom("room04", "Org Test Room", testOwner);
        room.organization = testOrg;
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user ->
                organizationRepository.persist(testOrg).flatMap(org -> roomRepository.persist(room))
            )
        ));

        // When: retrieving the room
        // Then: the organization relationship can be navigated
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findById("room04")), found -> {
            assertThat(found.organization).isNotNull();
            Organization org = found.organization;
            assertThat(org.orgId).isEqualTo(testOrg.orgId);
            assertThat(org.name).isEqualTo("Test Org");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindActiveByOwnerId(UniAsserter asserter) {
        // Given: multiple rooms with some soft-deleted
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Room activeRoom1 = createTestRoom("room05", "Active Room 1", testOwner);
        Room activeRoom2 = createTestRoom("room06", "Active Room 2", testOwner);
        Room deletedRoom = createTestRoom("room07", "Deleted Room", testOwner);
        deletedRoom.deletedAt = Instant.now();

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user ->
                roomRepository.persist(activeRoom1).flatMap(r1 ->
                    roomRepository.persist(activeRoom2).flatMap(r2 ->
                        roomRepository.persist(deletedRoom)
                    )
                )
            )
        ));

        // When: finding active rooms by owner ID
        // Then: only active rooms are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findActiveByOwnerId(testOwner.userId)), activeRooms -> {
            assertThat(activeRooms).hasSize(2);
            assertThat(activeRooms).extracting(r -> r.roomId)
                    .containsExactlyInAnyOrder("room05", "room06");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByOrgId(UniAsserter asserter) {
        // Given: rooms in an organization
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Organization testOrg = createTestOrganization("Test Org", "test.com");
        Room orgRoom1 = createTestRoom("room08", "Org Room 1", testOwner);
        orgRoom1.organization = testOrg;
        Room orgRoom2 = createTestRoom("room09", "Org Room 2", testOwner);
        orgRoom2.organization = testOrg;
        Room nonOrgRoom = createTestRoom("room10", "Non-Org Room", testOwner);

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user ->
                organizationRepository.persist(testOrg).flatMap(org ->
                    roomRepository.persist(orgRoom1).flatMap(r1 ->
                        roomRepository.persist(orgRoom2).flatMap(r2 ->
                            roomRepository.persist(nonOrgRoom)
                        )
                    )
                )
            )
        ));

        // When: finding rooms by organization ID
        // Then: only organization rooms are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findByOrgId(testOrg.orgId)), orgRooms -> {
            assertThat(orgRooms).hasSize(2);
            assertThat(orgRooms).extracting(r -> r.roomId)
                    .containsExactlyInAnyOrder("room08", "room09");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindPublicRooms(UniAsserter asserter) {
        // Given: rooms with different privacy modes
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Room publicRoom1 = createTestRoom("room11", "Public Room 1", testOwner);
        publicRoom1.privacyMode = PrivacyMode.PUBLIC;

        Room publicRoom2 = createTestRoom("room12", "Public Room 2", testOwner);
        publicRoom2.privacyMode = PrivacyMode.PUBLIC;

        Room privateRoom = createTestRoom("room13", "Private Room", testOwner);
        privateRoom.privacyMode = PrivacyMode.INVITE_ONLY;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user ->
                roomRepository.persist(publicRoom1).flatMap(r1 ->
                    roomRepository.persist(publicRoom2).flatMap(r2 ->
                        roomRepository.persist(privateRoom)
                    )
                )
            )
        ));

        // When: finding public rooms
        // Then: only public rooms are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findPublicRooms()), publicRooms -> {
            assertThat(publicRooms).hasSize(2);
            assertThat(publicRooms).extracting(r -> r.roomId)
                    .containsExactlyInAnyOrder("room11", "room12");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByPrivacyMode(UniAsserter asserter) {
        // Given: rooms with different privacy modes
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Room inviteOnlyRoom = createTestRoom("room14", "Invite Only Room", testOwner);
        inviteOnlyRoom.privacyMode = PrivacyMode.INVITE_ONLY;

        Room orgRestrictedRoom = createTestRoom("room15", "Org Restricted Room", testOwner);
        orgRestrictedRoom.privacyMode = PrivacyMode.ORG_RESTRICTED;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user ->
                roomRepository.persist(inviteOnlyRoom).flatMap(r1 ->
                    roomRepository.persist(orgRestrictedRoom)
                )
            )
        ));

        // When: finding rooms by privacy mode
        // Then: only matching privacy mode rooms are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findByPrivacyMode(PrivacyMode.INVITE_ONLY)), inviteOnlyRooms -> {
            assertThat(inviteOnlyRooms).hasSize(1);
            assertThat(inviteOnlyRooms.get(0).roomId).isEqualTo("room14");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindInactiveSince(UniAsserter asserter) {
        // Given: rooms with different lastActiveAt timestamps
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

        Room inactiveRoom = createTestRoom("room16", "Inactive Room", testOwner);
        inactiveRoom.lastActiveAt = twoDaysAgo;

        Room activeRoom = createTestRoom("room17", "Active Room", testOwner);
        activeRoom.lastActiveAt = oneHourAgo;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user ->
                roomRepository.persist(inactiveRoom).flatMap(r1 ->
                    roomRepository.persist(activeRoom)
                )
            )
        ));

        // When: finding rooms inactive since 1 day ago
        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        // Then: only rooms inactive before the threshold are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findInactiveSince(oneDayAgo)), inactiveRooms -> {
            assertThat(inactiveRooms).hasSize(1);
            assertThat(inactiveRooms.get(0).roomId).isEqualTo("room16");
        });
    }

    @Test
    @RunOnVertxContext
    void testCountActiveByOwnerId(UniAsserter asserter) {
        // Given: rooms with some soft-deleted
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Room room1 = createTestRoom("room18", "Count Room 1", testOwner);
        Room room2 = createTestRoom("room19", "Count Room 2", testOwner);
        Room deletedRoom = createTestRoom("room20", "Deleted Count Room", testOwner);
        deletedRoom.deletedAt = Instant.now();

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user ->
                roomRepository.persist(room1).flatMap(r1 ->
                    roomRepository.persist(room2).flatMap(r2 ->
                        roomRepository.persist(deletedRoom)
                    )
                )
            )
        ));

        // When: counting active rooms by owner
        // Then: only active rooms are counted
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.countActiveByOwnerId(testOwner.userId)), count -> {
            assertThat(count).isEqualTo(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testCountByOrgId(UniAsserter asserter) {
        // Given: organization rooms
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Organization testOrg = createTestOrganization("Test Org", "test.com");
        Room orgRoom1 = createTestRoom("room21", "Org Count Room 1", testOwner);
        orgRoom1.organization = testOrg;
        Room orgRoom2 = createTestRoom("room22", "Org Count Room 2", testOwner);
        orgRoom2.organization = testOrg;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user ->
                organizationRepository.persist(testOrg).flatMap(org ->
                    roomRepository.persist(orgRoom1).flatMap(r1 ->
                        roomRepository.persist(orgRoom2)
                    )
                )
            )
        ));

        // When: counting rooms by organization
        // Then: all organization rooms are counted
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.countByOrgId(testOrg.orgId)), count -> {
            assertThat(count).isEqualTo(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testSoftDelete(UniAsserter asserter) {
        // Given: a persisted room
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Room room = createTestRoom("room23", "Soft Delete Test Room", testOwner);
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user -> roomRepository.persist(room))
        ));

        // When: soft deleting the room
        asserter.execute(() -> Panache.withTransaction(() ->
                roomRepository.findById("room23").flatMap(r -> {
                    r.deletedAt = Instant.now();
                    return roomRepository.persist(r);
                })
        ));

        // Then: the room still exists but has deletedAt set
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findById("room23")), found -> {
            assertThat(found).isNotNull();
            assertThat(found.deletedAt).isNotNull();
        });

        // And: soft-deleted room is excluded from active queries
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findActiveByOwnerId(testOwner.userId)), activeRooms -> {
            assertThat(activeRooms).extracting(r -> r.roomId)
                    .doesNotContain("room23");
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdateRoom(UniAsserter asserter) {
        // Given: a persisted room
        User testOwner = createTestUser("owner@example.com", "google", "google-owner");
        Room room = createTestRoom("room24", "Update Test Room", testOwner);
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testOwner).flatMap(user -> roomRepository.persist(room))
        ));

        // When: updating the room
        asserter.execute(() -> Panache.withTransaction(() ->
                roomRepository.findById("room24").flatMap(r -> {
                    r.title = "Updated Room Title";
                    r.privacyMode = PrivacyMode.INVITE_ONLY;
                    r.config = "{\"deckType\":\"tshirt\"}";
                    return roomRepository.persist(r);
                })
        ));

        // Then: the changes are persisted
        asserter.assertThat(() -> Panache.withTransaction(() -> roomRepository.findById("room24")), updated -> {
            assertThat(updated.title).isEqualTo("Updated Room Title");
            assertThat(updated.privacyMode).isEqualTo(PrivacyMode.INVITE_ONLY);
            assertThat(updated.config).contains("tshirt");
        });
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
