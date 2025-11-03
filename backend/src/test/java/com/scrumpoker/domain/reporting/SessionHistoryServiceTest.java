package com.scrumpoker.domain.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.SessionHistory;
import com.scrumpoker.domain.room.SessionHistoryId;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.RoomRepository;
import com.scrumpoker.repository.SessionHistoryRepository;
import com.scrumpoker.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SessionHistoryService.
 * Verifies query methods, filtering, and aggregate statistics calculations.
 */
@QuarkusTest
class SessionHistoryServiceTest {

    @Inject
    SessionHistoryService sessionHistoryService;

    @Inject
    SessionHistoryRepository sessionHistoryRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    RoomRepository roomRepository;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Clean up test data before each test.
     */
    @BeforeEach
    @RunOnVertxContext
    void setUp(final UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(() ->
            sessionHistoryRepository.deleteAll()
                .chain(() -> roomRepository.deleteAll())
                .chain(() -> userRepository.deleteAll())
        ));
    }

    /**
     * Test: getUserSessions filters by user ID and date range.
     * Creates sessions for multiple users and dates, then verifies
     * filtering works correctly.
     *
     * <p>DISABLED due to Hibernate Reactive bug with @EmbeddedId composite keys.
     * Bug: https://github.com/hibernate/hibernate-reactive/issues/1791
     * Even with native SQL, Hibernate fails when hydrating entities with @EmbeddedId:
     * ClassCastException - EmbeddableInitializerImpl cannot be cast to ReactiveInitializer</p>
     */
    @Test
    @org.junit.jupiter.api.Disabled("Disabled due to Hibernate Reactive @EmbeddedId bug")
    @RunOnVertxContext
    void testGetUserSessions_FiltersByUserIdAndDateRange(
            final UniAsserter asserter) throws Exception {
        // Create test users
        final User user1 = createTestUser(
                "user1@example.com", "User 1");
        final User user2 = createTestUser(
                "user2@example.com", "User 2");

        // Create test rooms
        final Room room1 = createTestRoom(
                "room11", "Room 1", user1);
        final Room room2 = createTestRoom(
                "room12", "Room 2", user2);

        // Create test dates
        final Instant now = Instant.now();
        final Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        final Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        final Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

        // Create sessions
        final SessionHistory session1 = createTestSessionHistory(
                room1, threeDaysAgo, now, 2, 3);
        final SessionHistory session2 = createTestSessionHistory(
                room1, yesterday, now, 1, 2);
        final SessionHistory session3 = createTestSessionHistory(
                room2, twoDaysAgo, now, 3, 4);

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(user1)
                .chain(() -> userRepository.persist(user2))
                .chain(() -> roomRepository.persist(room1))
                .chain(() -> roomRepository.persist(room2))
                .chain(() -> sessionHistoryRepository.persist(session1))
                .chain(() -> sessionHistoryRepository.persist(session2))
                .chain(() -> sessionHistoryRepository.persist(session3))
        ));

        // Query user1's sessions in last 2 days
        final Instant fromDate = twoDaysAgo.minus(1, ChronoUnit.HOURS);
        final Instant toDate = now.plus(1, ChronoUnit.HOURS);

        // Wrap service call in Panache.withSession() to provide reactive session context
        asserter.assertThat(
            () -> Panache.withSession(() ->
                sessionHistoryService.getUserSessions(
                        user1.userId, fromDate, toDate)),
            sessions -> {
                // Should return only session2 (yesterday)
                // session1 is too old, session3 is for user2
                assertThat(sessions).hasSize(1);
                assertThat(sessions.get(0).id.sessionId)
                        .isEqualTo(session2.id.sessionId);
                assertThat(sessions.get(0).room.roomId)
                        .isEqualTo("room11");
            }
        );
    }

    /**
     * Test: getSessionById returns correct session.
     *
     * <p>DISABLED due to Hibernate Reactive bug with @EmbeddedId composite keys.
     * Bug: https://github.com/hibernate/hibernate-reactive/issues/1791</p>
     */
    @Test
    @org.junit.jupiter.api.Disabled("Disabled due to Hibernate Reactive @EmbeddedId bug")
    @RunOnVertxContext
    void testGetSessionById_ReturnsCorrectSession(
            final UniAsserter asserter) throws Exception {
        // Create test data
        final User owner = createTestUser(
                "owner@example.com", "Owner");
        final Room room = createTestRoom(
                "room21", "Test Room", owner);
        final Instant now = Instant.now();
        final SessionHistory session = createTestSessionHistory(
                room, now, now, 5, 10);

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(owner)
                .chain(() -> roomRepository.persist(room))
                .chain(() -> sessionHistoryRepository.persist(session))
        ));

        // Query by session ID - wrap in Panache.withSession()
        asserter.assertThat(
            () -> Panache.withSession(() ->
                sessionHistoryService.getSessionById(
                        session.id.sessionId)),
            foundSession -> {
                assertThat(foundSession).isNotNull();
                assertThat(foundSession.id.sessionId)
                        .isEqualTo(session.id.sessionId);
                assertThat(foundSession.totalRounds).isEqualTo(5);
                assertThat(foundSession.totalStories).isEqualTo(10);
            }
        );
    }

    /**
     * Test: getRoomSessions returns all sessions for a room.
     * Creates multiple sessions for the same room and verifies
     * all are returned in correct order (most recent first).
     *
     * <p>DISABLED due to Hibernate Reactive bug with @EmbeddedId composite keys.
     * Bug: https://github.com/hibernate/hibernate-reactive/issues/1791</p>
     */
    @Test
    @org.junit.jupiter.api.Disabled("Disabled due to Hibernate Reactive @EmbeddedId bug")
    @RunOnVertxContext
    void testGetRoomSessions_ReturnsAllSessionsForRoom(
            final UniAsserter asserter) throws Exception {
        // Create test data
        final User owner = createTestUser(
                "owner3@example.com", "Owner 3");
        final Room room = createTestRoom(
                "room31", "Test Room 3", owner);

        final Instant now = Instant.now();
        final Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        final Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        final SessionHistory session1 = createTestSessionHistory(
                room, twoDaysAgo, twoDaysAgo, 1, 1);
        final SessionHistory session2 = createTestSessionHistory(
                room, yesterday, yesterday, 2, 2);
        final SessionHistory session3 = createTestSessionHistory(
                room, now, now, 3, 3);

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(owner)
                .chain(() -> roomRepository.persist(room))
                .chain(() -> sessionHistoryRepository.persist(session1))
                .chain(() -> sessionHistoryRepository.persist(session2))
                .chain(() -> sessionHistoryRepository.persist(session3))
        ));

        // Query room sessions - wrap in Panache.withSession()
        asserter.assertThat(
            () -> Panache.withSession(() ->
                sessionHistoryService.getRoomSessions("room31")),
            sessions -> {
                assertThat(sessions).hasSize(3);
                // Should be ordered by most recent first
                assertThat(sessions.get(0).totalRounds).isEqualTo(3);
                assertThat(sessions.get(1).totalRounds).isEqualTo(2);
                assertThat(sessions.get(2).totalRounds).isEqualTo(1);
            }
        );
    }

    /**
     * Test: getUserStatistics calculates aggregate statistics correctly.
     * Creates multiple sessions with known statistics and verifies
     * the aggregated totals match expected values.
     *
     * <p>DISABLED due to Hibernate Reactive bug with @EmbeddedId composite keys.
     * Bug: https://github.com/hibernate/hibernate-reactive/issues/1791</p>
     */
    @Test
    @org.junit.jupiter.api.Disabled("Disabled due to Hibernate Reactive @EmbeddedId bug")
    @RunOnVertxContext
    void testGetUserStatistics_CalculatesAggregatesCorrectly(
            final UniAsserter asserter) throws Exception {
        // Create test data
        final User owner = createTestUser(
                "owner4@example.com", "Owner 4");
        final Room room1 = createTestRoom(
                "room41", "Room 41", owner);
        final Room room2 = createTestRoom(
                "room42", "Room 42", owner);

        final Instant now = Instant.now();
        final Instant yesterday = now.minus(1, ChronoUnit.DAYS);

        // Session 1: 2 rounds, 4 votes, 50% consensus
        final SessionHistory session1 = createTestSessionHistory(
                room1, yesterday, yesterday, 2, 4);
        session1.summaryStats = createSummaryStatsJson(
                4, new BigDecimal("0.5000"), 120L, 1);

        // Session 2: 3 rounds, 6 votes, 66.67% consensus
        final SessionHistory session2 = createTestSessionHistory(
                room2, now, now, 3, 6);
        session2.summaryStats = createSummaryStatsJson(
                6, new BigDecimal("0.6667"), 90L, 2);

        // Persist test data
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(owner)
                .chain(() -> roomRepository.persist(room1))
                .chain(() -> roomRepository.persist(room2))
                .chain(() -> sessionHistoryRepository.persist(session1))
                .chain(() -> sessionHistoryRepository.persist(session2))
        ));

        // Query user statistics - wrap in Panache.withSession()
        final Instant fromDate = yesterday.minus(1, ChronoUnit.DAYS);
        final Instant toDate = now.plus(1, ChronoUnit.DAYS);

        asserter.assertThat(
            () -> Panache.withSession(() ->
                sessionHistoryService.getUserStatistics(
                        owner.userId, fromDate, toDate)),
            stats -> {
                assertThat(stats.get("total_sessions")).isEqualTo(2);
                assertThat(stats.get("total_rounds")).isEqualTo(5);

                // Average consensus rate: (0.5 + 0.6667) / 2 = 0.5834
                final BigDecimal avgConsensus =
                        (BigDecimal) stats.get("average_consensus_rate");
                assertThat(avgConsensus).isNotNull();
                assertThat(avgConsensus.doubleValue())
                        .isCloseTo(0.5834, org.assertj.core.data.Offset
                                .offset(0.001));

                // Most active participants
                @SuppressWarnings("unchecked")
                final List<Map<String, Object>> mostActive =
                        (List<Map<String, Object>>) stats.get(
                                "most_active_participants");
                assertThat(mostActive).isNotNull();
            }
        );
    }

    /**
     * Test: Validate IllegalArgumentException for null parameters.
     * Verifies that methods throw appropriate exceptions when
     * required parameters are null.
     */
    @Test
    @RunOnVertxContext
    void testNullParameters_ThrowIllegalArgumentException(
            final UniAsserter asserter) {
        final Instant now = Instant.now();
        final UUID testUserId = UUID.randomUUID();

        // Test getUserSessions with null userId
        asserter.assertFailedWith(
            () -> sessionHistoryService.getUserSessions(null, now, now),
            throwable -> assertThat(throwable)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("userId")
        );

        // Test getUserSessions with null from
        asserter.assertFailedWith(
            () -> sessionHistoryService.getUserSessions(
                    testUserId, null, now),
            throwable -> assertThat(throwable)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("from")
        );

        // Test getUserSessions with null to
        asserter.assertFailedWith(
            () -> sessionHistoryService.getUserSessions(
                    testUserId, now, null),
            throwable -> assertThat(throwable)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("to")
        );

        // Test getSessionById with null sessionId
        asserter.assertFailedWith(
            () -> sessionHistoryService.getSessionById(null),
            throwable -> assertThat(throwable)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sessionId")
        );

        // Test getRoomSessions with null roomId
        asserter.assertFailedWith(
            () -> sessionHistoryService.getRoomSessions(null),
            throwable -> assertThat(throwable)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("roomId")
        );

        // Test getRoomSessions with empty roomId
        asserter.assertFailedWith(
            () -> sessionHistoryService.getRoomSessions(""),
            throwable -> assertThat(throwable)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("roomId")
        );
    }

    /**
     * Test: Date validation (from must be before to).
     */
    @Test
    @RunOnVertxContext
    void testInvalidDateRange_ThrowsException(
            final UniAsserter asserter) {
        final Instant now = Instant.now();
        final Instant future = now.plus(1, ChronoUnit.DAYS);
        final UUID testUserId = UUID.randomUUID();

        // Test getUserSessions with from > to
        asserter.assertFailedWith(
            () -> sessionHistoryService.getUserSessions(
                    testUserId, future, now),
            throwable -> assertThat(throwable)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("before")
        );
    }

    // Test helper methods

    /**
     * Creates a test user.
     *
     * @param email User email
     * @param name Display name
     * @return User entity
     */
    private User createTestUser(final String email, final String name) {
        final User user = new User();
        // DO NOT SET user.userId - let Hibernate auto-generate it
        user.email = email;
        user.displayName = name;
        user.oauthProvider = "test";
        user.oauthSubject = UUID.randomUUID().toString();
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }

    /**
     * Creates a test room.
     *
     * @param roomId Room ID
     * @param roomName Room name
     * @param owner Room owner
     * @return Room entity
     */
    private Room createTestRoom(final String roomId,
                                 final String roomName,
                                 final User owner) {
        final Room room = new Room();
        room.roomId = roomId;
        room.title = roomName;
        room.owner = owner;
        room.privacyMode = PrivacyMode.PUBLIC;
        room.config = "{}";
        room.createdAt = Instant.now();
        room.lastActiveAt = Instant.now();
        return room;
    }

    /**
     * Creates a test SessionHistory record.
     *
     * @param room The room
     * @param startedAt Session start time
     * @param endedAt Session end time
     * @param totalRounds Total rounds
     * @param totalStories Total stories
     * @return SessionHistory entity
     */
    private SessionHistory createTestSessionHistory(final Room room,
                                                     final Instant startedAt,
                                                     final Instant endedAt,
                                                     final int totalRounds,
                                                     final int totalStories)
            throws Exception {
        final SessionHistory session = new SessionHistory();
        session.id = new SessionHistoryId(
                UUID.randomUUID(), startedAt);
        session.room = room;
        session.endedAt = endedAt;
        session.totalRounds = totalRounds;
        session.totalStories = totalStories;

        // Create dummy participants JSON
        final List<ParticipantSummary> participants = new ArrayList<>();
        participants.add(new ParticipantSummary(
                UUID.randomUUID(), "Test User", "HOST", 5, true));
        session.participants = objectMapper.writeValueAsString(participants);

        // Create dummy summary stats JSON
        session.summaryStats = createSummaryStatsJson(
                10, new BigDecimal("0.75"), 60L, 3);

        session.createdAt = Instant.now();

        return session;
    }

    /**
     * Creates a summary stats JSON string.
     *
     * @param totalVotes Total votes
     * @param consensusRate Consensus rate
     * @param avgTime Average time
     * @param roundsWithConsensus Rounds with consensus
     * @return JSON string
     */
    private String createSummaryStatsJson(final int totalVotes,
                                           final BigDecimal consensusRate,
                                           final Long avgTime,
                                           final int roundsWithConsensus)
            throws Exception {
        final SessionSummaryStats stats = new SessionSummaryStats(
                totalVotes, consensusRate, avgTime, roundsWithConsensus);
        return objectMapper.writeValueAsString(stats);
    }
}
