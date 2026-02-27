package com.scrumpoker.api.websocket;

import com.scrumpoker.domain.room.*;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.*;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.scrumpoker.api.websocket.WebSocketTestClient.payload;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for complete voting flow using WebSocket.
 * Tests WebSocket message handling, Redis Pub/Sub, and voting lifecycle.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Requires Keycloak instance for OIDC token generation")
class VotingFlowIntegrationTest {

    @Inject
    UserRepository userRepository;

    @Inject
    RoomRepository roomRepository;

    @Inject
    RoomParticipantRepository participantRepository;

    @Inject
    RoundRepository roundRepository;

    @Inject
    VoteRepository voteRepository;

    private static final String WS_BASE_URL = "ws://localhost:8081/ws/room/";
    private static final Duration MESSAGE_TIMEOUT = Duration.ofSeconds(5);

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up test data (children first, then parents)
        asserter.execute(() -> Panache.withTransaction(() ->
            voteRepository.deleteAll()
                .chain(() -> roundRepository.deleteAll())
                .chain(() -> participantRepository.deleteAll())
                .chain(() -> roomRepository.deleteAll())
                .chain(() -> userRepository.deleteAll())
        ));
    }

    /**
     * Test: Complete voting flow (cast → reveal → reset)
     */
    @Test
    @Order(1)
    @RunOnVertxContext
    void testCompleteVotingFlow_CastRevealReset(UniAsserter asserter) throws Exception {
        // Setup test data
        User alice = createTestUser("alice@example.com", "Alice");
        User bob = createTestUser("bob@example.com", "Bob");
        Room room = createTestRoom("flow01", "Test Room", alice);
        RoomParticipant aliceParticipant = createTestParticipant(room, alice, "Alice", RoomRole.HOST);
        RoomParticipant bobParticipant = createTestParticipant(room, bob, "Bob", RoomRole.VOTER);
        Round round = createTestRound(room, 1, "Test Story");

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(alice)
                .chain(() -> userRepository.persist(bob))
                .chain(() -> roomRepository.persist(room))
                .chain(() -> participantRepository.persist(aliceParticipant))
                .chain(() -> participantRepository.persist(bobParticipant))
                .chain(() -> roundRepository.persist(round))
        ));

        // Generate OIDC tokens and run WebSocket test - all in worker thread (not on event loop)
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // TODO: Generate tokens via Keycloak test realm
                    String aliceToken = "test-token-alice";
                    String bobToken = "test-token-bob";

                    // Run WebSocket test
                    runCompleteVotingFlowTest(aliceToken, bobToken, aliceParticipant, bobParticipant);
                    emitter.complete(null);
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        }));
    }

    private void runCompleteVotingFlowTest(String aliceToken, String bobToken,
                                            RoomParticipant aliceParticipant, RoomParticipant bobParticipant) throws Exception {
        WebSocketTestClient aliceClient = new WebSocketTestClient();
        WebSocketTestClient bobClient = new WebSocketTestClient();

        try {
            // Connect Alice
            aliceClient.connect(WS_BASE_URL + "flow01?token=" + aliceToken);
            aliceClient.send("room.join.v1", payload("displayName", "Alice"));
            assertThat(aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT)).isNotNull();

            // Connect Bob
            bobClient.connect(WS_BASE_URL + "flow01?token=" + bobToken);
            bobClient.send("room.join.v1", payload("displayName", "Bob"));
            
            // Both clients receive Bob's join event
            assertThat(aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT)).isNotNull();
            assertThat(bobClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT)).isNotNull();

            // Alice casts vote "5"
            aliceClient.send("vote.cast.v1", payload("cardValue", "5"));
            assertThat(aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();
            assertThat(bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();

            // Bob casts vote "8"
            bobClient.send("vote.cast.v1", payload("cardValue", "8"));
            assertThat(aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();
            assertThat(bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();

            // Alice reveals round
            aliceClient.send("round.reveal.v1", payload());
            WebSocketMessage revealAlice = aliceClient.awaitMessage("round.revealed.v1", MESSAGE_TIMEOUT);
            WebSocketMessage revealBob = bobClient.awaitMessage("round.revealed.v1", MESSAGE_TIMEOUT);

            assertThat(revealAlice).isNotNull();
            assertThat(revealBob).isNotNull();

            // Verify statistics
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) revealAlice.getPayload().get("stats");
            assertThat(stats.get("avg")).isEqualTo(6.5);
            assertThat(stats.get("median")).isEqualTo("6.5");
            assertThat(stats.get("consensus")).isEqualTo(false);

            // Verify votes array
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> votes = (List<Map<String, Object>>) revealAlice.getPayload().get("votes");
            assertThat(votes).hasSize(2);
            assertThat(votes).extracting(v -> v.get("cardValue")).containsExactlyInAnyOrder("5", "8");

            // Alice resets round
            aliceClient.send("round.reset.v1", payload());
            assertThat(aliceClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT)).isNotNull();
            assertThat(bobClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT)).isNotNull();

        } finally {
            aliceClient.close();
            bobClient.close();
        }
    }

    /**
     * Test: Multiple clients receive synchronized events via Redis Pub/Sub
     */
    @Test
    @Order(2)
    @RunOnVertxContext
    void testMultipleClientsReceiveSynchronizedEvents(UniAsserter asserter) throws Exception {
        User alice = createTestUser("alice2@example.com", "Alice");
        User bob = createTestUser("bob2@example.com", "Bob");
        Room room = createTestRoom("sync01", "Sync Test Room", alice);
        RoomParticipant aliceParticipant = createTestParticipant(room, alice, "Alice", RoomRole.HOST);
        RoomParticipant bobParticipant = createTestParticipant(room, bob, "Bob", RoomRole.VOTER);
        Round round = createTestRound(room, 1, "Sync Story");

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(alice)
                .chain(() -> userRepository.persist(bob))
                .chain(() -> roomRepository.persist(room))
                .chain(() -> participantRepository.persist(aliceParticipant))
                .chain(() -> participantRepository.persist(bobParticipant))
                .chain(() -> roundRepository.persist(round))
        ));

        // Generate OIDC tokens and run WebSocket test - all in worker thread (not on event loop)
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // TODO: Generate tokens via Keycloak test realm
                    String aliceToken = "test-token-alice";
                    String bobToken = "test-token-bob";

                    WebSocketTestClient aliceClient = new WebSocketTestClient();
                    WebSocketTestClient bobClient = new WebSocketTestClient();

                    try {
                        aliceClient.connect(WS_BASE_URL + "sync01?token=" + aliceToken);
                        aliceClient.send("room.join.v1", payload("displayName", "Alice"));
                        aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

                        bobClient.connect(WS_BASE_URL + "sync01?token=" + bobToken);
                        bobClient.send("room.join.v1", payload("displayName", "Bob"));
                        aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);
                        bobClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

                        // Alice casts vote
                        aliceClient.send("vote.cast.v1", payload("cardValue", "13"));

                        // Bob receives the vote through Redis Pub/Sub
                        WebSocketMessage bobReceivedVote = bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
                        assertThat(bobReceivedVote).isNotNull();
                        assertThat(bobReceivedVote.getPayload().get("participantId"))
                            .isEqualTo(aliceParticipant.participantId.toString());

                        emitter.complete(null);
                    } finally {
                        aliceClient.close();
                        bobClient.close();
                    }
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        }));
    }

    /**
     * Test: Non-host cannot reveal round (authorization failure)
     */
    @Test
    @Order(3)
    @RunOnVertxContext
    void testNonHostCannotRevealRound_ReturnsForbidden(UniAsserter asserter) throws Exception {
        User alice = createTestUser("alice3@example.com", "Alice");
        User bob = createTestUser("bob3@example.com", "Bob");
        Room room = createTestRoom("auth01", "Auth Test Room", alice);
        RoomParticipant aliceParticipant = createTestParticipant(room, alice, "Alice", RoomRole.HOST);
        RoomParticipant bobParticipant = createTestParticipant(room, bob, "Bob", RoomRole.VOTER);
        Round round = createTestRound(room, 1, "Auth Story");

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(alice)
                .chain(() -> userRepository.persist(bob))
                .chain(() -> roomRepository.persist(room))
                .chain(() -> participantRepository.persist(aliceParticipant))
                .chain(() -> participantRepository.persist(bobParticipant))
                .chain(() -> roundRepository.persist(round))
        ));

        // Generate OIDC token and run WebSocket test - all in worker thread (not on event loop)
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // TODO: Generate token via Keycloak test realm
                    String bobToken = "test-token-bob";

                    WebSocketTestClient bobClient = new WebSocketTestClient();

                    try {
                        bobClient.connect(WS_BASE_URL + "auth01?token=" + bobToken);
                        bobClient.send("room.join.v1", payload("displayName", "Bob"));
                        bobClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

                        // Bob tries to reveal round (should fail with FORBIDDEN)
                        String requestId = bobClient.send("round.reveal.v1", payload());
                        WebSocketMessage errorMsg = bobClient.awaitMessage("error.v1", MESSAGE_TIMEOUT);

                        assertThat(errorMsg).isNotNull();
                        assertThat(errorMsg.getRequestId()).isEqualTo(requestId);
                        assertThat(errorMsg.getPayload().get("code")).isEqualTo(4003);
                        assertThat(errorMsg.getPayload().get("error")).isEqualTo("FORBIDDEN");

                        emitter.complete(null);
                    } finally {
                        bobClient.close();
                    }
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        }));
    }

    /**
     * Test: Reconnection preserves room state
     */
    @Test
    @Order(4)
    @RunOnVertxContext
    void testReconnectionPreservesRoomState(UniAsserter asserter) throws Exception {
        User alice = createTestUser("alice4@example.com", "Alice");
        Room room = createTestRoom("recon1", "Reconnection Test Room", alice);
        RoomParticipant aliceParticipant = createTestParticipant(room, alice, "Alice", RoomRole.HOST);
        Round round = createTestRound(room, 1, "Reconnection Story");

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(alice)
                .chain(() -> roomRepository.persist(room))
                .chain(() -> participantRepository.persist(aliceParticipant))
                .chain(() -> roundRepository.persist(round))
        ));

        // Generate OIDC token and run WebSocket test - all in worker thread (not on event loop)
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // TODO: Generate token via Keycloak test realm
                    String aliceToken = "test-token-alice";

                    WebSocketTestClient aliceClient1 = new WebSocketTestClient();

                    aliceClient1.connect(WS_BASE_URL + "recon1?token=" + aliceToken);
                    aliceClient1.send("room.join.v1", payload("displayName", "Alice"));
                    aliceClient1.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

                    aliceClient1.send("vote.cast.v1", payload("cardValue", "3"));
                    assertThat(aliceClient1.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();

                    aliceClient1.close();
                    Thread.sleep(500);

                    // Reconnect
                    WebSocketTestClient aliceClient2 = new WebSocketTestClient();
                    aliceClient2.connect(WS_BASE_URL + "recon1?token=" + aliceToken);
                    aliceClient2.send("room.join.v1", payload("displayName", "Alice"));
                    aliceClient2.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

                    // Can still cast vote (update)
                    aliceClient2.send("vote.cast.v1", payload("cardValue", "5"));
                    assertThat(aliceClient2.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();

                    aliceClient2.close();
                    emitter.complete(null);
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        }));
    }

    // Helper methods

    private User createTestUser(String email, String displayName) {
        User user = new User();
        user.email = email;
        user.oauthProvider = "google";
        user.oauthSubject = "google-" + UUID.randomUUID();
        user.displayName = displayName;
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }

    private Room createTestRoom(String roomId, String title, User owner) {
        Room room = new Room();
        room.roomId = roomId;
        room.title = title;
        room.owner = owner;
        room.privacyMode = PrivacyMode.PUBLIC;
        room.config = "{\"deckType\":\"fibonacci\"}";
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

    private Round createTestRound(Room room, Integer roundNumber, String storyTitle) {
        Round round = new Round();
        round.room = room;
        round.roundNumber = roundNumber;
        round.storyTitle = storyTitle;
        round.startedAt = Instant.now();
        return round;
    }

    /**
     * Test: Statistics calculation edge cases (consensus, mixed votes, all non-numeric)
     */
    @Test
    @Order(5)
    @RunOnVertxContext
    void testStatisticsCalculation_EdgeCases(UniAsserter asserter) throws Exception {
        User alice = createTestUser("alice5@example.com", "Alice");
        User bob = createTestUser("bob5@example.com", "Bob");
        User charlie = createTestUser("charlie5@example.com", "Charlie");
        Room room = createTestRoom("stats1", "Stats Test Room", alice);
        RoomParticipant aliceParticipant = createTestParticipant(room, alice, "Alice", RoomRole.HOST);
        RoomParticipant bobParticipant = createTestParticipant(room, bob, "Bob", RoomRole.VOTER);
        RoomParticipant charlieParticipant = createTestParticipant(room, charlie, "Charlie", RoomRole.VOTER);
        Round round = createTestRound(room, 1, "Stats Story");

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(alice)
                .chain(() -> userRepository.persist(bob))
                .chain(() -> userRepository.persist(charlie))
                .chain(() -> roomRepository.persist(room))
                .chain(() -> participantRepository.persist(aliceParticipant))
                .chain(() -> participantRepository.persist(bobParticipant))
                .chain(() -> participantRepository.persist(charlieParticipant))
                .chain(() -> roundRepository.persist(round))
        ));

        // Generate OIDC tokens and run WebSocket test - all in worker thread
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // TODO: Generate tokens via Keycloak test realm
                    String aliceToken = "test-token-alice";
                    String bobToken = "test-token-bob";
                    String charlieToken = "test-token-charlie";

                    // Test Scenario 1: All same vote (consensus = true)
                    runConsensusTest(aliceToken, bobToken, charlieToken, aliceParticipant, bobParticipant, charlieParticipant);

                    emitter.complete(null);
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        }));
    }

    private void runConsensusTest(String aliceToken, String bobToken, String charlieToken,
                                   RoomParticipant aliceParticipant, RoomParticipant bobParticipant,
                                   RoomParticipant charlieParticipant) throws Exception {
        WebSocketTestClient aliceClient = new WebSocketTestClient();
        WebSocketTestClient bobClient = new WebSocketTestClient();
        WebSocketTestClient charlieClient = new WebSocketTestClient();

        try {
            // Connect all clients
            aliceClient.connect(WS_BASE_URL + "stats1?token=" + aliceToken);
            aliceClient.send("room.join.v1", payload("displayName", "Alice"));
            aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

            bobClient.connect(WS_BASE_URL + "stats1?token=" + bobToken);
            bobClient.send("room.join.v1", payload("displayName", "Bob"));
            aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

            charlieClient.connect(WS_BASE_URL + "stats1?token=" + charlieToken);
            charlieClient.send("room.join.v1", payload("displayName", "Charlie"));
            aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

            // Scenario 1: All vote same value (5) - consensus should be TRUE
            aliceClient.send("vote.cast.v1", payload("cardValue", "5"));
            aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);

            bobClient.send("vote.cast.v1", payload("cardValue", "5"));
            aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);

            charlieClient.send("vote.cast.v1", payload("cardValue", "5"));
            aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);

            // Reveal and verify consensus
            aliceClient.send("round.reveal.v1", payload());
            WebSocketMessage reveal1 = aliceClient.awaitMessage("round.revealed.v1", MESSAGE_TIMEOUT);

            @SuppressWarnings("unchecked")
            Map<String, Object> stats1 = (Map<String, Object>) reveal1.getPayload().get("stats");
            assertThat(stats1.get("avg")).isEqualTo(5.0);
            assertThat(stats1.get("median")).isEqualTo("5");
            assertThat(stats1.get("consensus")).isEqualTo(true); // All same = consensus

            // Reset round for next test
            aliceClient.send("round.reset.v1", payload());
            aliceClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT);

            // Scenario 2: Mix of numeric and non-numeric votes ("5", "8", "?")
            aliceClient.send("vote.cast.v1", payload("cardValue", "5"));
            aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);

            bobClient.send("vote.cast.v1", payload("cardValue", "8"));
            aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);

            charlieClient.send("vote.cast.v1", payload("cardValue", "?"));
            aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);

            // Reveal and verify statistics exclude "?" from avg, but median is "mixed"
            aliceClient.send("round.reveal.v1", payload());
            WebSocketMessage reveal2 = aliceClient.awaitMessage("round.revealed.v1", MESSAGE_TIMEOUT);

            @SuppressWarnings("unchecked")
            Map<String, Object> stats2 = (Map<String, Object>) reveal2.getPayload().get("stats");
            assertThat(stats2.get("avg")).isEqualTo(6.5); // (5 + 8) / 2 = 6.5, "?" excluded from avg
            assertThat(stats2.get("median")).isEqualTo("mixed"); // Mixed numeric/non-numeric, no majority
            assertThat(stats2.get("consensus")).isEqualTo(false); // Has non-numeric vote

            // Reset for next test
            aliceClient.send("round.reset.v1", payload());
            aliceClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT);

            // Scenario 3: All non-numeric votes ("?", "?", "?")
            aliceClient.send("vote.cast.v1", payload("cardValue", "?"));
            aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);

            bobClient.send("vote.cast.v1", payload("cardValue", "?"));
            aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);

            charlieClient.send("vote.cast.v1", payload("cardValue", "?"));
            aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);
            charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT);

            // Reveal and verify null avg/median
            aliceClient.send("round.reveal.v1", payload());
            WebSocketMessage reveal3 = aliceClient.awaitMessage("round.revealed.v1", MESSAGE_TIMEOUT);

            @SuppressWarnings("unchecked")
            Map<String, Object> stats3 = (Map<String, Object>) reveal3.getPayload().get("stats");
            assertThat(stats3.get("avg")).isNull(); // No numeric votes
            assertThat(stats3.get("median")).isEqualTo("?"); // All same non-numeric
            assertThat(stats3.get("consensus")).isEqualTo(false); // No numeric votes

        } finally {
            aliceClient.close();
            bobClient.close();
            charlieClient.close();
        }
    }

    /**
     * Test: Payload validation errors (missing fields, invalid values)
     * NOTE: This test is currently disabled because card value validation against deck type
     * is not yet implemented in VotingService. The test documents expected behavior for future implementation.
     */
    @org.junit.jupiter.api.Disabled("Card value validation not yet implemented in VotingService")
    @Test
    @Order(6)
    @RunOnVertxContext
    void testPayloadValidation_Returns4004Error(UniAsserter asserter) throws Exception {
        User alice = createTestUser("alice6@example.com", "Alice");
        Room room = createTestRoom("valid1", "Validation Test Room", alice);
        RoomParticipant aliceParticipant = createTestParticipant(room, alice, "Alice", RoomRole.HOST);
        Round round = createTestRound(room, 1, "Validation Story");

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(alice)
                .chain(() -> roomRepository.persist(room))
                .chain(() -> participantRepository.persist(aliceParticipant))
                .chain(() -> roundRepository.persist(round))
        ));

        // Generate OIDC token and run WebSocket test - all in worker thread
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // TODO: Generate token via Keycloak test realm
                    String aliceToken = "test-token-alice";

                    WebSocketTestClient aliceClient = new WebSocketTestClient();

                    try {
                        aliceClient.connect(WS_BASE_URL + "valid1?token=" + aliceToken);
                        aliceClient.send("room.join.v1", payload("displayName", "Alice"));
                        aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

                        // Test 1: Missing required field (vote without cardValue)
                        String requestId1 = aliceClient.send("vote.cast.v1", payload()); // Empty payload
                        WebSocketMessage error1 = aliceClient.awaitMessage("error.v1", MESSAGE_TIMEOUT);

                        assertThat(error1).isNotNull();
                        assertThat(error1.getRequestId()).isEqualTo(requestId1);
                        assertThat(error1.getPayload().get("code")).isIn(4004, 4002); // VALIDATION_ERROR or INVALID_VOTE

                        // Test 2: Invalid card value for fibonacci deck (send "100")
                        String requestId2 = aliceClient.send("vote.cast.v1", payload("cardValue", "100"));
                        WebSocketMessage error2 = aliceClient.awaitMessage("error.v1", MESSAGE_TIMEOUT);

                        assertThat(error2).isNotNull();
                        assertThat(error2.getRequestId()).isEqualTo(requestId2);
                        assertThat(error2.getPayload().get("code")).isEqualTo(4002); // INVALID_VOTE
                        assertThat(error2.getPayload().get("error")).isEqualTo("INVALID_VOTE");

                        emitter.complete(null);
                    } finally {
                        aliceClient.close();
                    }
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        }));
    }

    /**
     * Test: Invalid state transitions (reveal without round, vote without round)
     */
    @Test
    @Order(7)
    @RunOnVertxContext
    void testInvalidStateTransitions_Returns4005Error(UniAsserter asserter) throws Exception {
        User alice = createTestUser("alice7@example.com", "Alice");
        Room room = createTestRoom("state1", "State Test Room", alice);
        RoomParticipant aliceParticipant = createTestParticipant(room, alice, "Alice", RoomRole.HOST);
        // Note: NO round created - room has no active round

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(alice)
                .chain(() -> roomRepository.persist(room))
                .chain(() -> participantRepository.persist(aliceParticipant))
        ));

        // Generate OIDC token and run WebSocket test - all in worker thread
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // TODO: Generate token via Keycloak test realm
                    String aliceToken = "test-token-alice";

                    WebSocketTestClient aliceClient = new WebSocketTestClient();

                    try {
                        aliceClient.connect(WS_BASE_URL + "state1?token=" + aliceToken);
                        aliceClient.send("room.join.v1", payload("displayName", "Alice"));
                        aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

                        // Test 1: Reveal when no round started
                        String requestId1 = aliceClient.send("round.reveal.v1", payload());
                        WebSocketMessage error1 = aliceClient.awaitMessage("error.v1", MESSAGE_TIMEOUT);

                        assertThat(error1).isNotNull();
                        assertThat(error1.getRequestId()).isEqualTo(requestId1);
                        assertThat(error1.getPayload().get("code")).isEqualTo(4005); // INVALID_STATE
                        assertThat(error1.getPayload().get("error")).isEqualTo("INVALID_STATE");

                        // Test 2: Cast vote when no active round
                        String requestId2 = aliceClient.send("vote.cast.v1", payload("cardValue", "5"));
                        WebSocketMessage error2 = aliceClient.awaitMessage("error.v1", MESSAGE_TIMEOUT);

                        assertThat(error2).isNotNull();
                        assertThat(error2.getRequestId()).isEqualTo(requestId2);
                        assertThat(error2.getPayload().get("code")).isEqualTo(4005); // INVALID_STATE
                        assertThat(error2.getPayload().get("error")).isEqualTo("INVALID_STATE");

                        emitter.complete(null);
                    } finally {
                        aliceClient.close();
                    }
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        }));
    }

    /**
     * Test: All event types are broadcast correctly via Redis Pub/Sub
     */
    @Test
    @Order(8)
    @RunOnVertxContext
    void testEventBroadcast_AllMessageTypes(UniAsserter asserter) throws Exception {
        User alice = createTestUser("alice8@example.com", "Alice");
        User bob = createTestUser("bob8@example.com", "Bob");
        User charlie = createTestUser("charlie8@example.com", "Charlie");
        Room room = createTestRoom("bcast1", "Broadcast Test Room", alice);
        RoomParticipant aliceParticipant = createTestParticipant(room, alice, "Alice", RoomRole.HOST);
        RoomParticipant bobParticipant = createTestParticipant(room, bob, "Bob", RoomRole.VOTER);
        RoomParticipant charlieParticipant = createTestParticipant(room, charlie, "Charlie", RoomRole.VOTER);
        Round round = createTestRound(room, 1, "Broadcast Story");

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(alice)
                .chain(() -> userRepository.persist(bob))
                .chain(() -> userRepository.persist(charlie))
                .chain(() -> roomRepository.persist(room))
                .chain(() -> participantRepository.persist(aliceParticipant))
                .chain(() -> participantRepository.persist(bobParticipant))
                .chain(() -> participantRepository.persist(charlieParticipant))
                .chain(() -> roundRepository.persist(round))
        ));

        // Generate OIDC tokens and run WebSocket test - all in worker thread
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // TODO: Generate tokens via Keycloak test realm
                    String aliceToken = "test-token-alice";
                    String bobToken = "test-token-bob";
                    String charlieToken = "test-token-charlie";

                    WebSocketTestClient aliceClient = new WebSocketTestClient();
                    WebSocketTestClient bobClient = new WebSocketTestClient();
                    WebSocketTestClient charlieClient = new WebSocketTestClient();

                    try {
                        // Connect Alice (HOST)
                        aliceClient.connect(WS_BASE_URL + "bcast1?token=" + aliceToken);
                        aliceClient.send("room.join.v1", payload("displayName", "Alice"));
                        aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

                        // Connect Bob (VOTER)
                        bobClient.connect(WS_BASE_URL + "bcast1?token=" + bobToken);
                        bobClient.send("room.join.v1", payload("displayName", "Bob"));

                        // Both clients receive Bob's join event
                        WebSocketMessage bobJoinAlice = aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);
                        WebSocketMessage bobJoinBob = bobClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);
                        assertThat(bobJoinAlice).isNotNull();
                        assertThat(bobJoinBob).isNotNull();

                        // Connect Charlie (VOTER)
                        charlieClient.connect(WS_BASE_URL + "bcast1?token=" + charlieToken);
                        charlieClient.send("room.join.v1", payload("displayName", "Charlie"));

                        // All three clients receive Charlie's join event
                        WebSocketMessage charlieJoinAlice = aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);
                        WebSocketMessage charlieJoinBob = bobClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);
                        WebSocketMessage charlieJoinCharlie = charlieClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);
                        assertThat(charlieJoinAlice).isNotNull();
                        assertThat(charlieJoinBob).isNotNull();
                        assertThat(charlieJoinCharlie).isNotNull();

                        // Test vote.recorded.v1 broadcast (already tested, but verify 3 clients)
                        aliceClient.send("vote.cast.v1", payload("cardValue", "5"));
                        assertThat(aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();
                        assertThat(bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();
                        assertThat(charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();

                        bobClient.send("vote.cast.v1", payload("cardValue", "8"));
                        assertThat(aliceClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();
                        assertThat(bobClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();
                        assertThat(charlieClient.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();

                        // Test round.revealed.v1 broadcast
                        aliceClient.send("round.reveal.v1", payload());
                        WebSocketMessage revealAlice = aliceClient.awaitMessage("round.revealed.v1", MESSAGE_TIMEOUT);
                        WebSocketMessage revealBob = bobClient.awaitMessage("round.revealed.v1", MESSAGE_TIMEOUT);
                        WebSocketMessage revealCharlie = charlieClient.awaitMessage("round.revealed.v1", MESSAGE_TIMEOUT);
                        assertThat(revealAlice).isNotNull();
                        assertThat(revealBob).isNotNull();
                        assertThat(revealCharlie).isNotNull();

                        // Verify all clients received same statistics
                        @SuppressWarnings("unchecked")
                        Map<String, Object> statsAlice = (Map<String, Object>) revealAlice.getPayload().get("stats");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> statsBob = (Map<String, Object>) revealBob.getPayload().get("stats");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> statsCharlie = (Map<String, Object>) revealCharlie.getPayload().get("stats");

                        assertThat(statsAlice.get("avg")).isEqualTo(statsBob.get("avg")).isEqualTo(statsCharlie.get("avg"));

                        // Test round.reset.v1 broadcast
                        aliceClient.send("round.reset.v1", payload());
                        WebSocketMessage resetAlice = aliceClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT);
                        WebSocketMessage resetBob = bobClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT);
                        WebSocketMessage resetCharlie = charlieClient.awaitMessage("round.reset.v1", MESSAGE_TIMEOUT);
                        assertThat(resetAlice).isNotNull();
                        assertThat(resetBob).isNotNull();
                        assertThat(resetCharlie).isNotNull();

                        emitter.complete(null);
                    } finally {
                        aliceClient.close();
                        bobClient.close();
                        charlieClient.close();
                    }
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        }));
    }
}
