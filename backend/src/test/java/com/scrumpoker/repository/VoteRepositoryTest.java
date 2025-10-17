package com.scrumpoker.repository;

import com.scrumpoker.domain.room.*;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
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
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findById(vote.voteId).flatMap(found ->
            // Fetch the round separately to verify the relationship
            roundRepository.findById(testRound.roundId).map(round -> {
                assertThat(found).isNotNull();
                assertThat(found.round).isNotNull();
                assertThat(found.round.roundId).isEqualTo(round.roundId);
                assertThat(round.roundNumber).isEqualTo(1);
                assertThat(round.storyTitle).isEqualTo("Test Story");
                return true;
            })
        )), result -> {
            assertThat(result).isTrue();
        });
    }

    @Test
    @RunOnVertxContext
    void testRelationshipNavigationToParticipant(UniAsserter asserter) {
        // Given: a persisted vote
        User testUser = createTestUser("voter@example.com", "google", "google-voter");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", testUser);
        Round testRound = createTestRound(testRoom, 1, "Test Story");
        RoomParticipant testParticipant = createTestParticipant(testRoom, testUser, "Test Voter");
        Vote vote = createTestVote(testRound, testParticipant, "13");

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
        // Then: the participant relationship can be navigated
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findById(vote.voteId).flatMap(found ->
            // Fetch the participant separately to verify the relationship
            participantRepository.findById(testParticipant.participantId).map(participant -> {
                assertThat(found).isNotNull();
                assertThat(found.participant).isNotNull();
                assertThat(found.participant.participantId).isEqualTo(participant.participantId);
                assertThat(participant.displayName).isEqualTo("Test Voter");
                return true;
            })
        )), result -> {
            assertThat(result).isTrue();
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByRoundId(UniAsserter asserter) {
        // Given: setup test hierarchy with 3 participants
        User user1 = createTestUser("alice@example.com", "google", "google-alice");
        User user2 = createTestUser("bob@example.com", "google", "google-bob");
        User user3 = createTestUser("charlie@example.com", "google", "google-charlie");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", user1);
        Round testRound = createTestRound(testRoom, 1, "Test Story");

        RoomParticipant participant1 = createTestParticipant(testRoom, user1, "Alice");
        RoomParticipant participant2 = createTestParticipant(testRoom, user2, "Bob");
        RoomParticipant participant3 = createTestParticipant(testRoom, user3, "Charlie");

        // Persist hierarchy - room owner first, then room, then round, then all users and participants
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(user1).flatMap(u1 ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(testRound).flatMap(round ->
                        userRepository.persist(user2).flatMap(u2 ->
                            userRepository.persist(user3).flatMap(u3 ->
                                participantRepository.persist(participant1).flatMap(p1 ->
                                    participantRepository.persist(participant2).flatMap(p2 ->
                                        participantRepository.persist(participant3)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ));

        // Given: multiple votes in a round
        Vote vote1 = createTestVote(testRound, participant1, "3");
        Vote vote2 = createTestVote(testRound, participant2, "5");
        Vote vote3 = createTestVote(testRound, participant3, "8");

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
        // Given: setup test hierarchy with 2 participants
        User user1 = createTestUser("alice@example.com", "google", "google-alice");
        User user2 = createTestUser("bob@example.com", "google", "google-bob");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", user1);
        Round testRound = createTestRound(testRoom, 1, "Test Story");

        RoomParticipant participant1 = createTestParticipant(testRoom, user1, "Alice");
        RoomParticipant participant2 = createTestParticipant(testRoom, user2, "Bob");

        // Persist hierarchy - room owner first, then room, then round, then other users and participants
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(user1).flatMap(u1 ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(testRound).flatMap(round ->
                        userRepository.persist(user2).flatMap(u2 ->
                            participantRepository.persist(participant1).flatMap(p1 ->
                                participantRepository.persist(participant2)
                            )
                        )
                    )
                )
            )
        ));

        // Given: votes in a specific round
        Vote vote1 = createTestVote(testRound, participant1, "2");
        Vote vote2 = createTestVote(testRound, participant2, "3");

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
        // Given: setup test hierarchy with 2 rounds for the same participant
        User testUser = createTestUser("voter@example.com", "google", "google-voter");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", testUser);
        Round round1 = createTestRound(testRoom, 1, "Story 1");
        Round round2 = createTestRound(testRoom, 2, "Story 2");
        RoomParticipant testParticipant = createTestParticipant(testRoom, testUser, "Test Voter");

        // Persist hierarchy
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(round1).flatMap(r1 ->
                        roundRepository.persist(round2).flatMap(r2 ->
                            participantRepository.persist(testParticipant)
                        )
                    )
                )
            )
        ));

        // Given: multiple votes by the same participant in different rounds
        Vote vote1 = createTestVote(round1, testParticipant, "5");
        Vote vote2 = createTestVote(round2, testParticipant, "8");

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
        // Given: setup test hierarchy
        User testUser = createTestUser("voter@example.com", "google", "google-voter");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", testUser);
        Round testRound = createTestRound(testRoom, 1, "Test Story");
        RoomParticipant testParticipant = createTestParticipant(testRoom, testUser, "Test Voter");

        // Persist hierarchy
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(testRound).flatMap(round ->
                        participantRepository.persist(testParticipant)
                    )
                )
            )
        ));

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
        // Given: setup test hierarchy with 3 participants
        User user1 = createTestUser("alice@example.com", "google", "google-alice");
        User user2 = createTestUser("bob@example.com", "google", "google-bob");
        User user3 = createTestUser("charlie@example.com", "google", "google-charlie");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", user1);
        Round testRound = createTestRound(testRoom, 1, "Test Story");

        RoomParticipant participant1 = createTestParticipant(testRoom, user1, "Alice");
        RoomParticipant participant2 = createTestParticipant(testRoom, user2, "Bob");
        RoomParticipant participant3 = createTestParticipant(testRoom, user3, "Charlie");

        // Persist hierarchy - room owner first, then room, then round, then other users and participants
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(user1).flatMap(u1 ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(testRound).flatMap(round ->
                        userRepository.persist(user2).flatMap(u2 ->
                            userRepository.persist(user3).flatMap(u3 ->
                                participantRepository.persist(participant1).flatMap(p1 ->
                                    participantRepository.persist(participant2).flatMap(p2 ->
                                        participantRepository.persist(participant3)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ));

        // Given: multiple votes in a round
        Vote vote1 = createTestVote(testRound, participant1, "1");
        Vote vote2 = createTestVote(testRound, participant2, "2");
        Vote vote3 = createTestVote(testRound, participant3, "3");

        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote1)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote2)));
        asserter.execute(() -> Panache.withTransaction(() -> voteRepository.persist(vote3)));

        // When: counting votes in the round
        // Then: the correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.countByRoundId(testRound.roundId)), count -> {
            assertThat(count).isEqualTo(3L);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByRoundIdAndCardValue(UniAsserter asserter) {
        // Given: setup test hierarchy with 3 participants
        User user1 = createTestUser("alice@example.com", "google", "google-alice");
        User user2 = createTestUser("bob@example.com", "google", "google-bob");
        User user3 = createTestUser("charlie@example.com", "google", "google-charlie");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", user1);
        Round testRound = createTestRound(testRoom, 1, "Test Story");

        RoomParticipant participant1 = createTestParticipant(testRoom, user1, "Alice");
        RoomParticipant participant2 = createTestParticipant(testRoom, user2, "Bob");
        RoomParticipant participant3 = createTestParticipant(testRoom, user3, "Charlie");

        // Persist hierarchy - room owner first, then room, then round, then other users and participants
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(user1).flatMap(u1 ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(testRound).flatMap(round ->
                        userRepository.persist(user2).flatMap(u2 ->
                            userRepository.persist(user3).flatMap(u3 ->
                                participantRepository.persist(participant1).flatMap(p1 ->
                                    participantRepository.persist(participant2).flatMap(p2 ->
                                        participantRepository.persist(participant3)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ));

        // Given: multiple votes with different card values
        Vote vote1 = createTestVote(testRound, participant1, "5");
        Vote vote2 = createTestVote(testRound, participant2, "5");
        Vote vote3 = createTestVote(testRound, participant3, "8");

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
        // Given: setup test hierarchy with 3 participants
        User user1 = createTestUser("alice@example.com", "google", "google-alice");
        User user2 = createTestUser("bob@example.com", "google", "google-bob");
        User user3 = createTestUser("charlie@example.com", "google", "google-charlie");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", user1);
        Round testRound = createTestRound(testRoom, 1, "Test Story");

        RoomParticipant participant1 = createTestParticipant(testRoom, user1, "Alice");
        RoomParticipant participant2 = createTestParticipant(testRoom, user2, "Bob");
        RoomParticipant participant3 = createTestParticipant(testRoom, user3, "Charlie");

        // Persist hierarchy - room owner first, then room, then round, then other users and participants
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(user1).flatMap(u1 ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(testRound).flatMap(round ->
                        userRepository.persist(user2).flatMap(u2 ->
                            userRepository.persist(user3).flatMap(u3 ->
                                participantRepository.persist(participant1).flatMap(p1 ->
                                    participantRepository.persist(participant2).flatMap(p2 ->
                                        participantRepository.persist(participant3)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ));

        // Given: votes with special card values
        Vote unknownVote = createTestVote(testRound, participant1, "?");
        Vote infinityVote = createTestVote(testRound, participant2, "∞");
        Vote coffeeVote = createTestVote(testRound, participant3, "☕");

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
        // Given: setup test hierarchy with 3 participants
        User user1 = createTestUser("alice@example.com", "google", "google-alice");
        User user2 = createTestUser("bob@example.com", "google", "google-bob");
        User user3 = createTestUser("charlie@example.com", "google", "google-charlie");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", user1);
        Round testRound = createTestRound(testRoom, 1, "Test Story");

        RoomParticipant participant1 = createTestParticipant(testRoom, user1, "Alice");
        RoomParticipant participant2 = createTestParticipant(testRoom, user2, "Bob");
        RoomParticipant participant3 = createTestParticipant(testRoom, user3, "Charlie");

        // Persist hierarchy - room owner first, then room, then round, then other users and participants
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(user1).flatMap(u1 ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(testRound).flatMap(round ->
                        userRepository.persist(user2).flatMap(u2 ->
                            userRepository.persist(user3).flatMap(u3 ->
                                participantRepository.persist(participant1).flatMap(p1 ->
                                    participantRepository.persist(participant2).flatMap(p2 ->
                                        participantRepository.persist(participant3)
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ));

        // Given: votes cast at different times
        Vote vote1 = createTestVote(testRound, participant1, "1");
        vote1.votedAt = Instant.now().minusSeconds(30);

        Vote vote2 = createTestVote(testRound, participant2, "2");
        vote2.votedAt = Instant.now().minusSeconds(20);

        Vote vote3 = createTestVote(testRound, participant3, "3");
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
        // Given: setup test hierarchy
        User testUser = createTestUser("voter@example.com", "google", "google-voter");
        Room testRoom = createTestRoom("vote01", "Vote Test Room", testUser);
        Round testRound = createTestRound(testRoom, 1, "Test Story");
        RoomParticipant testParticipant = createTestParticipant(testRoom, testUser, "Test Voter");

        // Persist hierarchy
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                roomRepository.persist(testRoom).flatMap(room ->
                    roundRepository.persist(testRound).flatMap(round ->
                        participantRepository.persist(testParticipant)
                    )
                )
            )
        ));

        // Given: a persisted vote - capture the ID from the returned entity
        Vote vote = createTestVote(testRound, testParticipant, "21");

        // Create a holder for the vote ID
        final UUID[] voteIdHolder = new UUID[1];

        asserter.execute(() -> Panache.withTransaction(() ->
            voteRepository.persist(vote).map(persistedVote -> {
                voteIdHolder[0] = persistedVote.voteId;
                return persistedVote;
            })
        ));

        // When: deleting the vote (fetch it first within the transaction)
        asserter.execute(() -> Panache.withTransaction(() ->
            voteRepository.findById(voteIdHolder[0]).flatMap(foundVote -> {
                assertThat(foundVote).isNotNull(); // Verify it exists before delete
                return voteRepository.delete(foundVote);
            })
        ));

        // Then: the vote no longer exists
        asserter.assertThat(() -> Panache.withTransaction(() -> voteRepository.findById(voteIdHolder[0])), found -> {
            assertThat(found).isNull();
        });
    }

    /**
     * Helper method to create test users.
     * Note: userId is NOT set here - it will be auto-generated by Hibernate on persist.
     */
    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
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
        // Set timestamps manually since @CreationTimestamp/@UpdateTimestamp run after validation
        room.createdAt = Instant.now();
        room.lastActiveAt = Instant.now();
        return room;
    }

    /**
     * Helper method to create test rounds.
     * Note: roundId is NOT set here - it will be auto-generated by Hibernate on persist.
     */
    private Round createTestRound(Room room, Integer roundNumber, String storyTitle) {
        Round round = new Round();
        round.room = room;
        round.roundNumber = roundNumber;
        round.storyTitle = storyTitle;
        return round;
    }

    /**
     * Helper method to create test participants.
     * Note: participantId is NOT set here - it will be auto-generated by Hibernate on persist.
     */
    private RoomParticipant createTestParticipant(Room room, User user, String displayName) {
        RoomParticipant participant = new RoomParticipant();
        participant.room = room;
        participant.user = user;
        participant.displayName = displayName;
        participant.role = RoomRole.VOTER;
        return participant;
    }

    /**
     * Helper method to create test votes.
     * Note: voteId is NOT set here - it will be auto-generated by Hibernate on persist.
     */
    private Vote createTestVote(Round round, RoomParticipant participant, String cardValue) {
        Vote vote = new Vote();
        vote.round = round;
        vote.participant = participant;
        vote.cardValue = cardValue;
        vote.votedAt = Instant.now();
        return vote;
    }
}
