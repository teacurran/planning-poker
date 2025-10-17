package com.scrumpoker.repository;

import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.Round;
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

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(() -> roundRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> roomRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given: a new round
        User testUser = createTestUser("rounduser@example.com", "google", "google-round");
        Room testRoom = createTestRoom("rnd001", "Round Test Room", testUser);
        Round round = createTestRound(testRoom, 1, "First Story");

        // When: persisting the round
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(round)
                )
            )
        ));

        // Then: the round can be retrieved
        asserter.assertThat(() -> Panache.withTransaction(() -> roundRepository.findById(round.roundId)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.roundNumber).isEqualTo(1);
            assertThat(found.storyTitle).isEqualTo("First Story");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByRoomId(UniAsserter asserter) {
        // Given: multiple rounds in a room
        User testUser = createTestUser("rounduser@example.com", "google", "google-round");
        Room testRoom = createTestRoom("rnd001", "Round Test Room", testUser);
        Round round1 = createTestRound(testRoom, 1, "Story 1");
        Round round2 = createTestRound(testRoom, 2, "Story 2");
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(round1).flatMap(r1 ->
                        roundRepository.persist(round2)
                    )
                )
            )
        ));

        // When: finding rounds by room ID
        // Then: all rounds are returned in order
        asserter.assertThat(() -> Panache.withTransaction(() -> roundRepository.findByRoomId("rnd001")), rounds -> {
            assertThat(rounds).hasSize(2);
            assertThat(rounds.get(0).roundNumber).isEqualTo(1);
            assertThat(rounds.get(1).roundNumber).isEqualTo(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByRoomIdAndRoundNumber(UniAsserter asserter) {
        // Given: rounds in a room
        User testUser = createTestUser("rounduser@example.com", "google", "google-round");
        Room testRoom = createTestRoom("rnd001", "Round Test Room", testUser);
        Round round = createTestRound(testRoom, 5, "Story 5");
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(round)
                )
            )
        ));

        // When: finding specific round
        // Then: the round is found
        asserter.assertThat(() -> Panache.withTransaction(() -> roundRepository.findByRoomIdAndRoundNumber("rnd001", 5)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.storyTitle).isEqualTo("Story 5");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindRevealedByRoomId(UniAsserter asserter) {
        // Given: revealed and unrevealed rounds
        User testUser = createTestUser("rounduser@example.com", "google", "google-round");
        Room testRoom = createTestRoom("rnd001", "Round Test Room", testUser);
        Round revealed = createTestRound(testRoom, 1, "Revealed Story");
        revealed.revealedAt = Instant.now();
        Round unrevealed = createTestRound(testRoom, 2, "Unrevealed Story");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(revealed).flatMap(r1 ->
                        roundRepository.persist(unrevealed)
                    )
                )
            )
        ));

        // When: finding revealed rounds
        // Then: only revealed rounds are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> roundRepository.findRevealedByRoomId("rnd001")), revealedRounds -> {
            assertThat(revealedRounds).hasSize(1);
            assertThat(revealedRounds.get(0).storyTitle).isEqualTo("Revealed Story");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindConsensusRoundsByRoomId(UniAsserter asserter) {
        // Given: rounds with and without consensus
        User testUser = createTestUser("rounduser@example.com", "google", "google-round");
        Room testRoom = createTestRoom("rnd001", "Round Test Room", testUser);
        Round consensus = createTestRound(testRoom, 1, "Consensus Story");
        consensus.consensusReached = true;
        Round noConsensus = createTestRound(testRoom, 2, "No Consensus Story");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(consensus).flatMap(r1 ->
                        roundRepository.persist(noConsensus)
                    )
                )
            )
        ));

        // When: finding consensus rounds
        // Then: only consensus rounds are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> roundRepository.findConsensusRoundsByRoomId("rnd001")), consensusRounds -> {
            assertThat(consensusRounds).hasSize(1);
            assertThat(consensusRounds.get(0).storyTitle).isEqualTo("Consensus Story");
        });
    }

    @Test
    @RunOnVertxContext
    void testCountByRoomId(UniAsserter asserter) {
        // Given: multiple rounds
        User testUser = createTestUser("rounduser@example.com", "google", "google-round");
        Room testRoom = createTestRoom("rnd001", "Round Test Room", testUser);
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(createTestRound(testRoom, 1, "Story 1")).flatMap(r1 ->
                        roundRepository.persist(createTestRound(testRoom, 2, "Story 2"))
                    )
                )
            )
        ));

        // When: counting rounds
        // Then: correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> roundRepository.countByRoomId("rnd001")), count -> {
            assertThat(count).isEqualTo(2);
        });
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
