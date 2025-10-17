package com.scrumpoker.repository;

import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.RoomParticipant;
import com.scrumpoker.domain.room.RoomRole;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RoomParticipantRepository.
 * Tests CRUD operations, role-based queries, and participant management.
 */
@QuarkusTest
class RoomParticipantRepositoryTest {

    @Inject
    RoomParticipantRepository participantRepository;

    @Inject
    RoomRepository roomRepository;

    @Inject
    UserRepository userRepository;

    private Room testRoom;
    private User testUser;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(() -> participantRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> roomRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));

        // Create and persist test data within a single transaction
        asserter.execute(() -> Panache.withTransaction(() -> {
            testUser = createTestUser("participant@example.com", "google", "google-part");
            return userRepository.persist(testUser).flatMap(u -> {
                testRoom = createTestRoom("prt001", "Participant Test Room", u);
                return roomRepository.persist(testRoom);
            });
        }));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given: a new participant
        final UUID[] participantId = new UUID[1];

        // When: persisting the participant
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findById("prt001").flatMap(room ->
                userRepository.findByEmail("participant@example.com").flatMap(user -> {
                    RoomParticipant participant = createTestParticipant(room, user, "Test Participant", RoomRole.VOTER);
                    return participantRepository.persist(participant).map(p -> {
                        participantId[0] = p.participantId;
                        return p;
                    });
                })
            )
        ));

        // Then: the participant can be retrieved
        asserter.assertThat(() -> Panache.withTransaction(() -> participantRepository.findById(participantId[0])), found -> {
            assertThat(found).isNotNull();
            assertThat(found.displayName).isEqualTo("Test Participant");
            assertThat(found.role).isEqualTo(RoomRole.VOTER);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByRoomId(UniAsserter asserter) {
        // Given: multiple participants in a room
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findById("prt001").flatMap(room ->
                userRepository.findByEmail("participant@example.com").flatMap(user -> {
                    RoomParticipant p1 = createTestParticipant(room, user, "Participant 1", RoomRole.HOST);
                    RoomParticipant p2 = createTestParticipant(room, user, "Participant 2", RoomRole.VOTER);
                    return participantRepository.persist(p1)
                        .flatMap(v -> participantRepository.persist(p2));
                })
            )
        ));

        // When: finding participants by room ID
        // Then: all participants are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> participantRepository.findByRoomId("prt001")), participants -> {
            assertThat(participants).hasSize(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByRoomIdAndRole(UniAsserter asserter) {
        // Given: participants with different roles
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findById("prt001").flatMap(room ->
                userRepository.findByEmail("participant@example.com").flatMap(user -> {
                    RoomParticipant host = createTestParticipant(room, user, "Host", RoomRole.HOST);
                    RoomParticipant voter = createTestParticipant(room, user, "Voter", RoomRole.VOTER);
                    RoomParticipant observer = createTestParticipant(room, user, "Observer", RoomRole.OBSERVER);
                    return participantRepository.persist(host)
                        .flatMap(v -> participantRepository.persist(voter))
                        .flatMap(v -> participantRepository.persist(observer));
                })
            )
        ));

        // When: finding voters
        // Then: only voters are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> participantRepository.findByRoomIdAndRole("prt001", RoomRole.VOTER)), voters -> {
            assertThat(voters).hasSize(1);
            assertThat(voters.get(0).displayName).isEqualTo("Voter");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindVotersByRoomId(UniAsserter asserter) {
        // Given: participants with different roles
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findById("prt001").flatMap(room ->
                userRepository.findByEmail("participant@example.com").flatMap(user -> {
                    RoomParticipant voter1 = createTestParticipant(room, user, "Voter1", RoomRole.VOTER);
                    RoomParticipant voter2 = createTestParticipant(room, user, "Voter2", RoomRole.VOTER);
                    RoomParticipant observer = createTestParticipant(room, user, "Observer", RoomRole.OBSERVER);
                    return participantRepository.persist(voter1)
                        .flatMap(v -> participantRepository.persist(voter2))
                        .flatMap(v -> participantRepository.persist(observer));
                })
            )
        ));

        // When: finding voters
        // Then: only voters are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> participantRepository.findVotersByRoomId("prt001")), voters -> {
            assertThat(voters).hasSize(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testCountByRoomId(UniAsserter asserter) {
        // Given: multiple participants
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findById("prt001").flatMap(room ->
                userRepository.findByEmail("participant@example.com").flatMap(user -> {
                    RoomParticipant p1 = createTestParticipant(room, user, "P1", RoomRole.VOTER);
                    RoomParticipant p2 = createTestParticipant(room, user, "P2", RoomRole.VOTER);
                    return participantRepository.persist(p1)
                        .flatMap(v -> participantRepository.persist(p2));
                })
            )
        ));

        // When: counting participants
        // Then: correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> participantRepository.countByRoomId("prt001")), count -> {
            assertThat(count).isEqualTo(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testCountVotersByRoomId(UniAsserter asserter) {
        // Given: participants with different roles
        asserter.execute(() -> Panache.withTransaction(() ->
            roomRepository.findById("prt001").flatMap(room ->
                userRepository.findByEmail("participant@example.com").flatMap(user -> {
                    RoomParticipant voter = createTestParticipant(room, user, "Voter", RoomRole.VOTER);
                    RoomParticipant observer = createTestParticipant(room, user, "Observer", RoomRole.OBSERVER);
                    return participantRepository.persist(voter)
                        .flatMap(v -> participantRepository.persist(observer));
                })
            )
        ));

        // When: counting voters
        // Then: only voters are counted
        asserter.assertThat(() -> Panache.withTransaction(() -> participantRepository.countVotersByRoomId("prt001")), voterCount -> {
            assertThat(voterCount).isEqualTo(1);
        });
    }

    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }

    private Room createTestRoom(String roomId, String title, User owner) {
        Room room = new Room();
        room.roomId = roomId;
        room.title = title;
        room.owner = owner;
        room.privacyMode = PrivacyMode.PUBLIC;
        room.config = "{}";
        room.createdAt = Instant.now();
        room.lastActiveAt = Instant.now();
        return room;
    }

    private RoomParticipant createTestParticipant(Room room, User user, String displayName, RoomRole role) {
        RoomParticipant participant = new RoomParticipant();
        participant.room = room;
        participant.user = user;
        participant.displayName = displayName;
        participant.role = role;
        return participant;
    }
}
