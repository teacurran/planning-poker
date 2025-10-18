package com.scrumpoker.api.websocket;

import com.scrumpoker.domain.room.*;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.*;
import com.scrumpoker.security.JwtTokenService;
import com.scrumpoker.security.TokenPair;
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
class VotingFlowIntegrationTest {

    @Inject
    JwtTokenService jwtTokenService;

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

        // Generate JWT tokens and run WebSocket test - all in worker thread (not on event loop)
        final TokenPair[] tokens = new TokenPair[2];
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // Generate tokens (blocking Redis operations)
                    TokenPair aliceTokens = jwtTokenService.generateTokens(alice).await().indefinitely();
                    TokenPair bobTokens = jwtTokenService.generateTokens(bob).await().indefinitely();

                    // Run WebSocket test
                    runCompleteVotingFlowTest(aliceTokens, bobTokens, aliceParticipant, bobParticipant);
                    emitter.complete(null);
                } catch (Exception e) {
                    emitter.fail(e);
                }
            });
        }));
    }

    private void runCompleteVotingFlowTest(TokenPair aliceTokens, TokenPair bobTokens,
                                            RoomParticipant aliceParticipant, RoomParticipant bobParticipant) throws Exception {
        WebSocketTestClient aliceClient = new WebSocketTestClient();
        WebSocketTestClient bobClient = new WebSocketTestClient();

        try {
            // Connect Alice
            aliceClient.connect(WS_BASE_URL + "flow01?token=" + aliceTokens.accessToken());
            aliceClient.send("room.join.v1", payload("displayName", "Alice"));
            assertThat(aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT)).isNotNull();

            // Connect Bob
            bobClient.connect(WS_BASE_URL + "flow01?token=" + bobTokens.accessToken());
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

        // Generate JWT tokens and run WebSocket test - all in worker thread (not on event loop)
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // Generate tokens (blocking Redis operations)
                    TokenPair aliceTokens = jwtTokenService.generateTokens(alice).await().indefinitely();
                    TokenPair bobTokens = jwtTokenService.generateTokens(bob).await().indefinitely();

                    WebSocketTestClient aliceClient = new WebSocketTestClient();
                    WebSocketTestClient bobClient = new WebSocketTestClient();

                    try {
                        aliceClient.connect(WS_BASE_URL + "sync01?token=" + aliceTokens.accessToken());
                        aliceClient.send("room.join.v1", payload("displayName", "Alice"));
                        aliceClient.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

                        bobClient.connect(WS_BASE_URL + "sync01?token=" + bobTokens.accessToken());
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

        // Generate JWT token and run WebSocket test - all in worker thread (not on event loop)
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // Generate token (blocking Redis operations)
                    TokenPair bobTokens = jwtTokenService.generateTokens(bob).await().indefinitely();

                    WebSocketTestClient bobClient = new WebSocketTestClient();

                    try {
                        bobClient.connect(WS_BASE_URL + "auth01?token=" + bobTokens.accessToken());
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

        // Generate JWT token and run WebSocket test - all in worker thread (not on event loop)
        asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
            io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
                try {
                    // Generate token (blocking Redis operations)
                    TokenPair aliceTokens = jwtTokenService.generateTokens(alice).await().indefinitely();

                    WebSocketTestClient aliceClient1 = new WebSocketTestClient();

                    aliceClient1.connect(WS_BASE_URL + "recon1?token=" + aliceTokens.accessToken());
                    aliceClient1.send("room.join.v1", payload("displayName", "Alice"));
                    aliceClient1.awaitMessage("room.participant_joined.v1", MESSAGE_TIMEOUT);

                    aliceClient1.send("vote.cast.v1", payload("cardValue", "3"));
                    assertThat(aliceClient1.awaitMessage("vote.recorded.v1", MESSAGE_TIMEOUT)).isNotNull();

                    aliceClient1.close();
                    Thread.sleep(500);

                    // Reconnect
                    WebSocketTestClient aliceClient2 = new WebSocketTestClient();
                    aliceClient2.connect(WS_BASE_URL + "recon1?token=" + aliceTokens.accessToken());
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
}
