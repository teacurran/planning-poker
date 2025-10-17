package com.scrumpoker.repository;

import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.Round;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RoundRepository.
 * Tests CRUD operations and round-based queries.
 */
@QuarkusTest
class RoundRepositoryTest {

    @Inject
    RoundRepository roundRepository;

    @Inject
    RoomRepository roomRepository;

    @Inject
    UserRepository userRepository;

    private Room testRoom;

    @BeforeEach
    @Transactional
    void setUp() {
        roundRepository.deleteAll().await().indefinitely();
        roomRepository.deleteAll().await().indefinitely();
        userRepository.deleteAll().await().indefinitely();

        User testUser = createTestUser("rounduser@example.com", "google", "google-round");
        userRepository.persist(testUser).await().indefinitely();

        testRoom = createTestRoom("rnd001", "Round Test Room", testUser);
        roomRepository.persist(testRoom).await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindById() {
        // Given: a new round
        Round round = createTestRound(testRoom, 1, "First Story");

        // When: persisting the round
        roundRepository.persist(round).await().indefinitely();

        // Then: the round can be retrieved
        Round found = roundRepository.findById(round.roundId).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.roundNumber).isEqualTo(1);
        assertThat(found.storyTitle).isEqualTo("First Story");
    }

    @Test
    @Transactional
    void testFindByRoomId() {
        // Given: multiple rounds in a room
        Round round1 = createTestRound(testRoom, 1, "Story 1");
        Round round2 = createTestRound(testRoom, 2, "Story 2");
        roundRepository.persist(round1).await().indefinitely();
        roundRepository.persist(round2).await().indefinitely();

        // When: finding rounds by room ID
        List<Round> rounds = roundRepository.findByRoomId("rnd001").await().indefinitely();

        // Then: all rounds are returned in order
        assertThat(rounds).hasSize(2);
        assertThat(rounds.get(0).roundNumber).isEqualTo(1);
        assertThat(rounds.get(1).roundNumber).isEqualTo(2);
    }

    @Test
    @Transactional
    void testFindByRoomIdAndRoundNumber() {
        // Given: rounds in a room
        Round round = createTestRound(testRoom, 5, "Story 5");
        roundRepository.persist(round).await().indefinitely();

        // When: finding specific round
        Round found = roundRepository.findByRoomIdAndRoundNumber("rnd001", 5).await().indefinitely();

        // Then: the round is found
        assertThat(found).isNotNull();
        assertThat(found.storyTitle).isEqualTo("Story 5");
    }

    @Test
    @Transactional
    void testFindRevealedByRoomId() {
        // Given: revealed and unrevealed rounds
        Round revealed = createTestRound(testRoom, 1, "Revealed Story");
        revealed.revealedAt = Instant.now();
        Round unrevealed = createTestRound(testRoom, 2, "Unrevealed Story");

        roundRepository.persist(revealed).await().indefinitely();
        roundRepository.persist(unrevealed).await().indefinitely();

        // When: finding revealed rounds
        List<Round> revealedRounds = roundRepository.findRevealedByRoomId("rnd001").await().indefinitely();

        // Then: only revealed rounds are returned
        assertThat(revealedRounds).hasSize(1);
        assertThat(revealedRounds.get(0).storyTitle).isEqualTo("Revealed Story");
    }

    @Test
    @Transactional
    void testFindConsensusRoundsByRoomId() {
        // Given: rounds with and without consensus
        Round consensus = createTestRound(testRoom, 1, "Consensus Story");
        consensus.consensusReached = true;
        Round noConsensus = createTestRound(testRoom, 2, "No Consensus Story");

        roundRepository.persist(consensus).await().indefinitely();
        roundRepository.persist(noConsensus).await().indefinitely();

        // When: finding consensus rounds
        List<Round> consensusRounds = roundRepository.findConsensusRoundsByRoomId("rnd001").await().indefinitely();

        // Then: only consensus rounds are returned
        assertThat(consensusRounds).hasSize(1);
        assertThat(consensusRounds.get(0).storyTitle).isEqualTo("Consensus Story");
    }

    @Test
    @Transactional
    void testCountByRoomId() {
        // Given: multiple rounds
        roundRepository.persist(createTestRound(testRoom, 1, "Story 1")).await().indefinitely();
        roundRepository.persist(createTestRound(testRoom, 2, "Story 2")).await().indefinitely();

        // When: counting rounds
        Long count = roundRepository.countByRoomId("rnd001").await().indefinitely();

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

    private Room createTestRoom(String roomId, String title, User owner) {
        Room room = new Room();
        room.roomId = roomId;
        room.title = title;
        room.owner = owner;
        room.privacyMode = PrivacyMode.PUBLIC;
        room.config = "{}";
        return room;
    }

    private Round createTestRound(Room room, Integer roundNumber, String storyTitle) {
        Round round = new Round();
        round.roundId = UUID.randomUUID();
        round.room = room;
        round.roundNumber = roundNumber;
        round.storyTitle = storyTitle;
        return round;
    }
}
