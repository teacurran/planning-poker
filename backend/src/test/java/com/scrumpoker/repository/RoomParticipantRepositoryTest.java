package com.scrumpoker.repository;

import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.RoomParticipant;
import com.scrumpoker.domain.room.RoomRole;
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
    @Transactional
    void setUp() {
        participantRepository.deleteAll().await().indefinitely();
        roomRepository.deleteAll().await().indefinitely();
        userRepository.deleteAll().await().indefinitely();

        testUser = createTestUser("participant@example.com", "google", "google-part");
        userRepository.persist(testUser).await().indefinitely();

        testRoom = createTestRoom("prt001", "Participant Test Room", testUser);
        roomRepository.persist(testRoom).await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindById() {
        // Given: a new participant
        RoomParticipant participant = createTestParticipant(testRoom, testUser, "Test Participant", RoomRole.VOTER);

        // When: persisting the participant
        participantRepository.persist(participant).await().indefinitely();

        // Then: the participant can be retrieved
        RoomParticipant found = participantRepository.findById(participant.participantId).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.displayName).isEqualTo("Test Participant");
        assertThat(found.role).isEqualTo(RoomRole.VOTER);
    }

    @Test
    @Transactional
    void testFindByRoomId() {
        // Given: multiple participants in a room
        RoomParticipant p1 = createTestParticipant(testRoom, testUser, "Participant 1", RoomRole.HOST);
        RoomParticipant p2 = createTestParticipant(testRoom, testUser, "Participant 2", RoomRole.VOTER);

        participantRepository.persist(p1).await().indefinitely();
        participantRepository.persist(p2).await().indefinitely();

        // When: finding participants by room ID
        List<RoomParticipant> participants = participantRepository.findByRoomId("prt001").await().indefinitely();

        // Then: all participants are returned
        assertThat(participants).hasSize(2);
    }

    @Test
    @Transactional
    void testFindByRoomIdAndRole() {
        // Given: participants with different roles
        RoomParticipant host = createTestParticipant(testRoom, testUser, "Host", RoomRole.HOST);
        RoomParticipant voter = createTestParticipant(testRoom, testUser, "Voter", RoomRole.VOTER);
        RoomParticipant observer = createTestParticipant(testRoom, testUser, "Observer", RoomRole.OBSERVER);

        participantRepository.persist(host).await().indefinitely();
        participantRepository.persist(voter).await().indefinitely();
        participantRepository.persist(observer).await().indefinitely();

        // When: finding voters
        List<RoomParticipant> voters = participantRepository.findByRoomIdAndRole("prt001", RoomRole.VOTER)
                .await().indefinitely();

        // Then: only voters are returned
        assertThat(voters).hasSize(1);
        assertThat(voters.get(0).displayName).isEqualTo("Voter");
    }

    @Test
    @Transactional
    void testFindVotersByRoomId() {
        // Given: participants with different roles
        participantRepository.persist(createTestParticipant(testRoom, testUser, "Voter1", RoomRole.VOTER)).await().indefinitely();
        participantRepository.persist(createTestParticipant(testRoom, testUser, "Voter2", RoomRole.VOTER)).await().indefinitely();
        participantRepository.persist(createTestParticipant(testRoom, testUser, "Observer", RoomRole.OBSERVER)).await().indefinitely();

        // When: finding voters
        List<RoomParticipant> voters = participantRepository.findVotersByRoomId("prt001").await().indefinitely();

        // Then: only voters are returned
        assertThat(voters).hasSize(2);
    }

    @Test
    @Transactional
    void testCountByRoomId() {
        // Given: multiple participants
        participantRepository.persist(createTestParticipant(testRoom, testUser, "P1", RoomRole.VOTER)).await().indefinitely();
        participantRepository.persist(createTestParticipant(testRoom, testUser, "P2", RoomRole.VOTER)).await().indefinitely();

        // When: counting participants
        Long count = participantRepository.countByRoomId("prt001").await().indefinitely();

        // Then: correct count is returned
        assertThat(count).isEqualTo(2);
    }

    @Test
    @Transactional
    void testCountVotersByRoomId() {
        // Given: participants with different roles
        participantRepository.persist(createTestParticipant(testRoom, testUser, "Voter", RoomRole.VOTER)).await().indefinitely();
        participantRepository.persist(createTestParticipant(testRoom, testUser, "Observer", RoomRole.OBSERVER)).await().indefinitely();

        // When: counting voters
        Long voterCount = participantRepository.countVotersByRoomId("prt001").await().indefinitely();

        // Then: only voters are counted
        assertThat(voterCount).isEqualTo(1);
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

    private Room createTestRoom(String roomId, String title, User owner) {
        Room room = new Room();
        room.roomId = roomId;
        room.title = title;
        room.owner = owner;
        room.privacyMode = PrivacyMode.PUBLIC;
        room.config = "{}";
        return room;
    }

    private RoomParticipant createTestParticipant(Room room, User user, String displayName, RoomRole role) {
        RoomParticipant participant = new RoomParticipant();
        participant.participantId = UUID.randomUUID();
        participant.room = room;
        participant.user = user;
        participant.displayName = displayName;
        participant.role = role;
        return participant;
    }
}
