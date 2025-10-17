package com.scrumpoker.repository;

import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.SessionHistory;
import com.scrumpoker.domain.room.SessionHistoryId;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SessionHistoryRepository.
 * Tests CRUD operations with composite key, JSONB fields, and date range queries.
 */
@QuarkusTest
class SessionHistoryRepositoryTest {

    @Inject
    SessionHistoryRepository sessionHistoryRepository;

    @Inject
    RoomRepository roomRepository;

    @Inject
    UserRepository userRepository;

    private Room testRoom;

    @BeforeEach
    @Transactional
    void setUp() {
        sessionHistoryRepository.deleteAll().await().indefinitely();
        roomRepository.deleteAll().await().indefinitely();
        userRepository.deleteAll().await().indefinitely();

        User testUser = createTestUser("sessionuser@example.com", "google", "google-session");
        userRepository.persist(testUser).await().indefinitely();

        testRoom = createTestRoom("ses001", "Session Test Room", testUser);
        roomRepository.persist(testRoom).await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindByCompositeId() {
        // Given: a new session history with composite key
        SessionHistory session = createTestSessionHistory(testRoom, Instant.now().minus(1, ChronoUnit.HOURS));

        // When: persisting the session history
        sessionHistoryRepository.persist(session).await().indefinitely();

        // Then: the session can be retrieved by composite ID
        SessionHistory found = sessionHistoryRepository.findById(session.id).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.totalRounds).isEqualTo(5);
        assertThat(found.totalStories).isEqualTo(3);
    }

    @Test
    @Transactional
    void testJsonbParticipantsField() {
        // Given: session with JSONB participants array
        SessionHistory session = createTestSessionHistory(testRoom, Instant.now());
        String participantsJson = "[{\"name\":\"Alice\",\"role\":\"VOTER\"},{\"name\":\"Bob\",\"role\":\"HOST\"}]";
        session.participants = participantsJson;

        // When: persisting and retrieving
        sessionHistoryRepository.persist(session).await().indefinitely();
        SessionHistory found = sessionHistoryRepository.findById(session.id).await().indefinitely();

        // Then: JSONB field round-trips correctly
        assertThat(found.participants).isEqualTo(participantsJson);
        assertThat(found.participants).contains("Alice");
    }

    @Test
    @Transactional
    void testJsonbSummaryStatsField() {
        // Given: session with JSONB summary stats
        SessionHistory session = createTestSessionHistory(testRoom, Instant.now());
        String statsJson = "{\"avgEstimationTime\":120,\"consensusRate\":0.8,\"totalVotes\":25}";
        session.summaryStats = statsJson;

        // When: persisting and retrieving
        sessionHistoryRepository.persist(session).await().indefinitely();
        SessionHistory found = sessionHistoryRepository.findById(session.id).await().indefinitely();

        // Then: JSONB summary stats persist correctly
        assertThat(found.summaryStats).isEqualTo(statsJson);
        assertThat(found.summaryStats).contains("consensusRate");
    }

    @Test
    @Transactional
    void testFindByRoomId() {
        // Given: multiple sessions for a room
        SessionHistory session1 = createTestSessionHistory(testRoom, Instant.now().minus(2, ChronoUnit.HOURS));
        SessionHistory session2 = createTestSessionHistory(testRoom, Instant.now().minus(1, ChronoUnit.HOURS));

        sessionHistoryRepository.persist(session1).await().indefinitely();
        sessionHistoryRepository.persist(session2).await().indefinitely();

        // When: finding sessions by room ID
        List<SessionHistory> sessions = sessionHistoryRepository.findByRoomId("ses001").await().indefinitely();

        // Then: all sessions are returned
        assertThat(sessions).hasSize(2);
    }

    @Test
    @Transactional
    void testFindByDateRange() {
        // Given: sessions at different times
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant today = Instant.now();

        SessionHistory oldSession = createTestSessionHistory(testRoom, twoDaysAgo);
        SessionHistory recentSession = createTestSessionHistory(testRoom, yesterday);

        sessionHistoryRepository.persist(oldSession).await().indefinitely();
        sessionHistoryRepository.persist(recentSession).await().indefinitely();

        // When: finding sessions in date range
        Instant startDate = Instant.now().minus(36, ChronoUnit.HOURS);
        Instant endDate = today;
        List<SessionHistory> sessions = sessionHistoryRepository.findByDateRange(startDate, endDate)
                .await().indefinitely();

        // Then: only sessions in range are returned
        assertThat(sessions).hasSize(1);
    }

    @Test
    @Transactional
    void testFindByMinRounds() {
        // Given: sessions with different round counts
        SessionHistory shortSession = createTestSessionHistory(testRoom, Instant.now().minus(2, ChronoUnit.HOURS));
        shortSession.totalRounds = 3;

        SessionHistory longSession = createTestSessionHistory(testRoom, Instant.now().minus(1, ChronoUnit.HOURS));
        longSession.totalRounds = 10;

        sessionHistoryRepository.persist(shortSession).await().indefinitely();
        sessionHistoryRepository.persist(longSession).await().indefinitely();

        // When: finding sessions with at least 5 rounds
        List<SessionHistory> longSessions = sessionHistoryRepository.findByMinRounds(5).await().indefinitely();

        // Then: only long sessions are returned
        assertThat(longSessions).hasSize(1);
        assertThat(longSessions.get(0).totalRounds).isEqualTo(10);
    }

    @Test
    @Transactional
    void testCountByRoomId() {
        // Given: multiple sessions
        sessionHistoryRepository.persist(createTestSessionHistory(testRoom, Instant.now().minus(2, ChronoUnit.HOURS))).await().indefinitely();
        sessionHistoryRepository.persist(createTestSessionHistory(testRoom, Instant.now().minus(1, ChronoUnit.HOURS))).await().indefinitely();

        // When: counting sessions
        Long count = sessionHistoryRepository.countByRoomId("ses001").await().indefinitely();

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

    private SessionHistory createTestSessionHistory(Room room, Instant startedAt) {
        SessionHistory session = new SessionHistory();
        session.id = new SessionHistoryId(UUID.randomUUID(), startedAt);
        session.room = room;
        session.endedAt = startedAt.plus(1, ChronoUnit.HOURS);
        session.totalRounds = 5;
        session.totalStories = 3;
        session.participants = "[]";
        session.summaryStats = "{}";
        return session;
    }
}
