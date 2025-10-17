package com.scrumpoker.repository;

import com.scrumpoker.domain.room.*;
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
 * Integration tests for VoteRepository.
 * Tests CRUD operations, relationship navigation to Round and Participant,
 * and complex queries using Testcontainers PostgreSQL.
 */
@QuarkusTest
class VoteRepositoryTest {

    @Inject
    VoteRepository voteRepository;

    @Inject
    RoundRepository roundRepository;

    @Inject
    RoomRepository roomRepository;

    @Inject
    RoomParticipantRepository participantRepository;

    @Inject
    UserRepository userRepository;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up any existing test data (children first, then parents)
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> roundRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> participantRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> roomRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given: a new vote
        User testUser = createTestUser("voter@example.com", "google", "google-voter");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", testUser);
        Round testRound = createTestRound(testRoom, 1, "Test Story");
        RoomParticipant testParticipant = createTestParticipant(testRoom, testUser, "Test Voter");
        Vote vote = createTestVote(testRound, testParticipant, "5");

        // When: persisting the vote
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(testRound).flatMap(round ->
                        participantRepository.persist(testParticipant).flatMap(participant ->
                            voteRepository.persist(vote)
                        )
                    )
                )
            )
        ));

        // Then: the vote can be retrieved by ID
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findById(vote.voteId)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.cardValue).isEqualTo("5");
            assertThat(found.votedAt).isNotNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testRelationshipNavigationToRound(UniAsserter asserter) {
        // Given: a persisted vote
        User testUser = createTestUser("voter@example.com", "google", "google-voter");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", testUser);
        Round testRound = createTestRound(testRoom, 1, "Test Story");
        RoomParticipant testParticipant = createTestParticipant(testRoom, testUser, "Test Voter");
        Vote vote = createTestVote(testRound, testParticipant, "8");
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(testRound).flatMap(round ->
                        participantRepository.persist(testParticipant).flatMap(participant ->
                            voteRepository.persist(vote)
                        )
                    )
                )
            )
        ));

        // When: retrieving the vote
        // Then: the round relationship can be navigated
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findById(vote.voteId)), found -> {
            assertThat(found.round).isNotNull();
            Round round = found.round;
            assertThat(round.roundId).isEqualTo(testRound.roundId);
            assertThat(round.roundNumber).isEqualTo(1);
            assertThat(round.storyTitle).isEqualTo("Test Story");
        });
    }

    @Test
    @RunOnVertxContext
    void testRelationshipNavigationToParticipant(UniAsserter asserter) {
        // Given: a persisted vote
        Vote vote = createTestVote(testRound, testParticipant, "13");
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote)));

        // When: retrieving the vote
        // Then: the participant relationship can be navigated
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findById(vote.voteId)), found -> {
            assertThat(found.participant).isNotNull();
            RoomParticipant participant = found.participant;
            assertThat(participant.participantId).isEqualTo(testParticipant.participantId);
            assertThat(participant.displayName).isEqualTo("Test Voter");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByRoundId(UniAsserter asserter) {
        // Given: multiple votes in a round
        Vote vote1 = createTestVote(testRound, testParticipant, "3");
        Vote vote2 = createTestVote(testRound, testParticipant, "5");
        Vote vote3 = createTestVote(testRound, testParticipant, "8");

        vote1.votedAt = Instant.now().minusMillis(30);
        vote2.votedAt = Instant.now().minusMillis(20);
        vote3.votedAt = Instant.now().minusMillis(10);

        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote1)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote2)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote3)));

        // When: finding votes by round ID
        // Then: all votes in the round are returned, ordered by votedAt
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findByRoundId(testRound.roundId)), votes -> {
            assertThat(votes).hasSize(3);
            assertThat(votes).extracting(v -> v.cardValue)
                    .containsExactly("3", "5", "8");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByRoomIdAndRoundNumber(UniAsserter asserter) {
        // Given: votes in a specific round
        Vote vote1 = createTestVote(testRound, testParticipant, "2");
        Vote vote2 = createTestVote(testRound, testParticipant, "3");

        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote1)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote2)));

        // When: finding votes by room ID and round number
        // Then: votes from the specified round are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findByRoomIdAndRoundNumber("vote01", 1)), votes -> {
            assertThat(votes).hasSize(2);
            assertThat(votes).extracting(v -> v.cardValue)
                    .containsExactlyInAnyOrder("2", "3");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByParticipantId(UniAsserter asserter) {
        // Given: multiple votes by the same participant
        Vote vote1 = createTestVote(testRound, testParticipant, "5");
        Vote vote2 = createTestVote(testRound, testParticipant, "8");

        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote1)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote2)));

        // When: finding votes by participant ID
        // Then: all votes by the participant are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findByParticipantId(testParticipant.participantId)), votes -> {
            assertThat(votes).hasSize(2);
            assertThat(votes).extracting(v -> v.cardValue)
                    .containsExactlyInAnyOrder("5", "8");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByRoundIdAndParticipantId(UniAsserter asserter) {
        // Given: a vote by a specific participant in a round
        Vote vote = createTestVote(testRound, testParticipant, "13");
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote)));

        // When: finding the specific vote
        // Then: the vote is found
        asserter.assertThat(() -> Panache.withTransaction(() ->
                voteRepository.findByRoundIdAndParticipantId(testRound.roundId, testParticipant.participantId)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.cardValue).isEqualTo("13");
        });
    }

    @Test
    @RunOnVertxContext
    void testCountByRoundId(UniAsserter asserter) {
        // Given: multiple votes in a round
        Vote vote1 = createTestVote(testRound, testParticipant, "1");
        Vote vote2 = createTestVote(testRound, testParticipant, "2");
        Vote vote3 = createTestVote(testRound, testParticipant, "3");

        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote1)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote2)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote3)));

        // When: counting votes in the round
        // Then: the correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.countByRoundId(testRound.roundId)), count -> {
            assertThat(count).isEqualTo(3);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByRoundIdAndCardValue(UniAsserter asserter) {
        // Given: multiple votes with different card values
        Vote vote1 = createTestVote(testRound, testParticipant, "5");
        Vote vote2 = createTestVote(testRound, testParticipant, "5");
        Vote vote3 = createTestVote(testRound, testParticipant, "8");

        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote1)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote2)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote3)));

        // When: finding votes with a specific card value
        // Then: only votes with the specified card value are returned
        asserter.assertThat(() -> Panache.withTransaction(() ->
                voteRepository.findByRoundIdAndCardValue(testRound.roundId, "5")), fiveVotes -> {
            assertThat(fiveVotes).hasSize(2);
            assertThat(fiveVotes).allMatch(v -> v.cardValue.equals("5"));
        });
    }

    @Test
    @RunOnVertxContext
    void testVoteWithSpecialCardValues(UniAsserter asserter) {
        // Given: votes with special card values
        Vote unknownVote = createTestVote(testRound, testParticipant, "?");
        Vote infinityVote = createTestVote(testRound, testParticipant, "∞");
        Vote coffeeVote = createTestVote(testRound, testParticipant, "☕");

        // When: persisting votes with special characters
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(unknownVote)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(infinityVote)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(coffeeVote)));

        // Then: special card values are persisted correctly
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findByRoundId(testRound.roundId)), votes -> {
            assertThat(votes).hasSize(3);
            assertThat(votes).extracting(v -> v.cardValue)
                    .containsExactlyInAnyOrder("?", "∞", "☕");
        });
    }

    @Test
    @RunOnVertxContext
    void testVoteOrderingByVotedAt(UniAsserter asserter) {
        // Given: votes cast at different times
        Vote vote1 = createTestVote(testRound, testParticipant, "1");
        vote1.votedAt = Instant.now().minusSeconds(30);

        Vote vote2 = createTestVote(testRound, testParticipant, "2");
        vote2.votedAt = Instant.now().minusSeconds(20);

        Vote vote3 = createTestVote(testRound, testParticipant, "3");
        vote3.votedAt = Instant.now().minusSeconds(10);

        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote1)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote2)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote3)));

        // When: finding votes by round ID
        // Then: votes are ordered by votedAt (earliest first)
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findByRoundId(testRound.roundId)), votes -> {
            assertThat(votes).hasSize(3);
            assertThat(votes.get(0).cardValue).isEqualTo("1");
            assertThat(votes.get(1).cardValue).isEqualTo("2");
            assertThat(votes.get(2).cardValue).isEqualTo("3");
        });
    }

    @Test
    @RunOnVertxContext
    void testDeleteVote(UniAsserter asserter) {
        // Given: a persisted vote
        Vote vote = createTestVote(testRound, testParticipant, "21");
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote)));
        UUID voteId = vote.voteId;

        // When: deleting the vote
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.delete(vote)));

        // Then: the vote no longer exists
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findById(voteId)), found -> {
            assertThat(found).isNull();
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
     * Helper method to create test rooms.
     */
    private Room createTestRoom(String roomId, String title, User owner) {
        Room room = new Room();
        room.roomId = roomId;
        room.title = title;
        room.owner = owner;
        room.privacyMode = PrivacyMode.PUBLIC;
        room.config = "{\"deckType\":\"fibonacci\"}";
        return room;
    }

    /**
     * Helper method to create test rounds.
     */
    private Round createTestRound(Room room, Integer roundNumber, String storyTitle) {
        Round round = new Round();
        round.roundId = UUID.randomUUID();
        round.room = room;
        round.roundNumber = roundNumber;
        round.storyTitle = storyTitle;
        return round;
    }

    /**
     * Helper method to create test participants.
     */
    private RoomParticipant createTestParticipant(Room room, User user, String displayName) {
        RoomParticipant participant = new RoomParticipant();
        participant.participantId = UUID.randomUUID();
        participant.room = room;
        participant.user = user;
        participant.displayName = displayName;
        participant.role = RoomRole.VOTER;
        return participant;
    }

    /**
     * Helper method to create test votes.
     */
    private Vote createTestVote(Round round, RoomParticipant participant, String cardValue) {
        Vote vote = new Vote();
        vote.voteId = UUID.randomUUID();
        vote.round = round;
        vote.participant = participant;
        vote.cardValue = cardValue;
        vote.votedAt = Instant.now();
        return vote;
    }
}
