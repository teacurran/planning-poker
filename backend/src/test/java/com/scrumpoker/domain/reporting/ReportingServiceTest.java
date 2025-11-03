package com.scrumpoker.domain.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.RoomParticipant;
import com.scrumpoker.domain.room.Round;
import com.scrumpoker.domain.room.SessionHistory;
import com.scrumpoker.domain.room.SessionHistoryId;
import com.scrumpoker.domain.room.Vote;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.RoundRepository;
import com.scrumpoker.repository.VoteRepository;
import com.scrumpoker.security.FeatureGate;
import com.scrumpoker.security.FeatureNotAvailableException;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.stream.ReactiveStreamCommands;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ReportingService using mocked dependencies.
 * Tests tier-based access control, report generation, and export job enqueuing.
 */
@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock
    SessionHistoryService sessionHistoryService;

    @Mock
    FeatureGate featureGate;

    @Mock
    RoundRepository roundRepository;

    @Mock
    VoteRepository voteRepository;

    @Mock
    ReactiveRedisDataSource redisDataSource;

    @Mock
    ReactiveStreamCommands<String, String, String> streamCommands;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    com.fasterxml.jackson.databind.type.TypeFactory typeFactory;

    @Mock
    com.fasterxml.jackson.databind.type.CollectionType collectionType;

    @InjectMocks
    ReportingService reportingService;

    private User freeUser;
    private User proUser;
    private SessionHistory testSession;
    private Room testRoom;
    private Round testRound1;
    private Round testRound2;
    private Vote vote1;
    private Vote vote2;
    private Vote vote3;
    private RoomParticipant participant1;
    private RoomParticipant participant2;
    private UUID testSessionId;
    private String testRoomId;
    private SessionSummaryStats summaryStats;
    private List<ParticipantSummary> participants;

    @BeforeEach
    void setUp() throws Exception {
        testSessionId = UUID.randomUUID();
        testRoomId = "abc123";

        // Create test users
        freeUser = new User();
        freeUser.userId = UUID.randomUUID();
        freeUser.email = "free@example.com";
        freeUser.displayName = "Free User";
        freeUser.subscriptionTier = SubscriptionTier.FREE;

        proUser = new User();
        proUser.userId = UUID.randomUUID();
        proUser.email = "pro@example.com";
        proUser.displayName = "Pro User";
        proUser.subscriptionTier = SubscriptionTier.PRO;

        // Create test room
        testRoom = new Room();
        testRoom.roomId = testRoomId;
        testRoom.title = "Test Planning Session";
        testRoom.createdAt = Instant.now().minus(1, ChronoUnit.HOURS);

        // Create test participants
        participant1 = new RoomParticipant();
        participant1.participantId = UUID.randomUUID();
        participant1.room = testRoom;
        participant1.displayName = "Alice";

        participant2 = new RoomParticipant();
        participant2.participantId = UUID.randomUUID();
        participant2.room = testRoom;
        participant2.displayName = "Bob";

        // Create participant summaries for JSONB
        participants = List.of(
                new ParticipantSummary(participant1.participantId, "Alice", "VOTER", 2, true),
                new ParticipantSummary(participant2.participantId, "Bob", "VOTER", 2, true)
        );

        // Create summary stats for JSONB
        summaryStats = new SessionSummaryStats(
                4,  // totalVotes
                new BigDecimal("0.5000"),  // consensusRate (50%)
                120L,  // avgEstimationTimeSeconds
                1  // roundsWithConsensus
        );

        // Create test rounds
        testRound1 = new Round();
        testRound1.roundId = UUID.randomUUID();
        testRound1.room = testRoom;
        testRound1.roundNumber = 1;
        testRound1.storyTitle = "User Story 1";
        testRound1.startedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        testRound1.revealedAt = Instant.now().minus(28, ChronoUnit.MINUTES);
        testRound1.average = new BigDecimal("4.50");
        testRound1.median = "5";
        testRound1.consensusReached = true;

        testRound2 = new Round();
        testRound2.roundId = UUID.randomUUID();
        testRound2.room = testRoom;
        testRound2.roundNumber = 2;
        testRound2.storyTitle = "User Story 2";
        testRound2.startedAt = Instant.now().minus(20, ChronoUnit.MINUTES);
        testRound2.revealedAt = Instant.now().minus(18, ChronoUnit.MINUTES);
        testRound2.average = new BigDecimal("6.50");
        testRound2.median = "8";
        testRound2.consensusReached = false;

        // Create test votes
        vote1 = new Vote();
        vote1.voteId = UUID.randomUUID();
        vote1.round = testRound1;
        vote1.participant = participant1;
        vote1.cardValue = "5";
        vote1.votedAt = Instant.now().minus(29, ChronoUnit.MINUTES);

        vote2 = new Vote();
        vote2.voteId = UUID.randomUUID();
        vote2.round = testRound1;
        vote2.participant = participant2;
        vote2.cardValue = "3";
        vote2.votedAt = Instant.now().minus(29, ChronoUnit.MINUTES);

        vote3 = new Vote();
        vote3.voteId = UUID.randomUUID();
        vote3.round = testRound2;
        vote3.participant = participant1;
        vote3.cardValue = "8";
        vote3.votedAt = Instant.now().minus(19, ChronoUnit.MINUTES);

        // Create test session
        SessionHistoryId sessionHistoryId = new SessionHistoryId(
                testSessionId,
                Instant.now().minus(1, ChronoUnit.HOURS)
        );

        testSession = new SessionHistory();
        testSession.id = sessionHistoryId;
        testSession.room = testRoom;
        testSession.endedAt = Instant.now().minus(10, ChronoUnit.MINUTES);
        testSession.totalRounds = 2;
        testSession.totalStories = 2;
        testSession.participants = "[{\"participant_id\":\"" + participant1.participantId
                + "\",\"display_name\":\"Alice\",\"role\":\"VOTER\",\"vote_count\":2,\"is_authenticated\":true}]";
        testSession.summaryStats = "{\"total_votes\":4,\"consensus_rate\":0.5,\"avg_estimation_time_seconds\":120,\"rounds_with_consensus\":1}";
        testSession.createdAt = Instant.now();

        // Setup ObjectMapper mock for TypeFactory (lenient to avoid unnecessary stubbing exceptions)
        lenient().when(objectMapper.getTypeFactory()).thenReturn(typeFactory);
        lenient().when(typeFactory.constructCollectionType(List.class, ParticipantSummary.class))
                .thenReturn(collectionType);
        lenient().when(objectMapper.readValue(anyString(), eq(SessionSummaryStats.class)))
                .thenReturn(summaryStats);
        lenient().when(objectMapper.readValue(anyString(), eq(collectionType)))
                .thenReturn(participants);
    }

    // ===== Basic Session Summary Tests =====

    @Test
    void testGetBasicSessionSummary_ValidSession_ReturnsCorrectSummary() throws Exception {
        // Given
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().item(testSession));
        when(roundRepository.findByRoomId(testRoomId))
                .thenReturn(Uni.createFrom().item(List.of(testRound1, testRound2)));

        // When
        SessionSummaryDTO result = reportingService.getBasicSessionSummary(testSessionId)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSessionId()).isEqualTo(testSessionId);
        assertThat(result.getRoomTitle()).isEqualTo("Test Planning Session");
        assertThat(result.getTotalStories()).isEqualTo(2);
        assertThat(result.getTotalRounds()).isEqualTo(2);
        assertThat(result.getConsensusRate()).isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(result.getParticipantCount()).isEqualTo(2);
        assertThat(result.getTotalVotes()).isEqualTo(4);

        // Average of round averages: (4.50 + 6.50) / 2 = 5.50
        assertThat(result.getAverageVote()).isEqualByComparingTo(new BigDecimal("5.5000"));

        verify(sessionHistoryService).getSessionById(testSessionId);
        verify(roundRepository).findByRoomId(testRoomId);
    }

    @Test
    void testGetBasicSessionSummary_SessionNotFound_ThrowsException() {
        // Given
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                reportingService.getBasicSessionSummary(testSessionId)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");

        verify(sessionHistoryService).getSessionById(testSessionId);
        verifyNoInteractions(roundRepository);
    }

    @Test
    void testGetBasicSessionSummary_NullSessionId_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                reportingService.getBasicSessionSummary(null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId cannot be null");

        verifyNoInteractions(sessionHistoryService);
    }

    // ===== Detailed Session Report Tests =====

    @Test
    void testGetDetailedSessionReport_ProUser_ReturnsDetailedReport() throws Exception {
        // Given
        doNothing().when(featureGate).requireCanAccessAdvancedReports(proUser);
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().item(testSession));
        when(roundRepository.findByRoomId(testRoomId))
                .thenReturn(Uni.createFrom().item(List.of(testRound1, testRound2)));
        when(voteRepository.findByRoundId(testRound1.roundId))
                .thenReturn(Uni.createFrom().item(List.of(vote1, vote2)));
        when(voteRepository.findByRoundId(testRound2.roundId))
                .thenReturn(Uni.createFrom().item(List.of(vote3)));

        // When
        DetailedSessionReportDTO result = reportingService.getDetailedSessionReport(testSessionId, proUser)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSessionId()).isEqualTo(testSessionId);
        assertThat(result.getRoomTitle()).isEqualTo("Test Planning Session");
        assertThat(result.getTotalStories()).isEqualTo(2);
        assertThat(result.getTotalRounds()).isEqualTo(2);

        // Verify round details
        assertThat(result.getRounds()).hasSize(2);

        DetailedSessionReportDTO.RoundDetailDTO round1 = result.getRounds().get(0);
        assertThat(round1.getRoundNumber()).isEqualTo(1);
        assertThat(round1.getStoryTitle()).isEqualTo("User Story 1");
        assertThat(round1.getVotes()).hasSize(2);
        assertThat(round1.getAverage()).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(round1.getMedian()).isEqualTo("5");
        assertThat(round1.getConsensusReached()).isTrue();

        DetailedSessionReportDTO.RoundDetailDTO round2 = result.getRounds().get(1);
        assertThat(round2.getRoundNumber()).isEqualTo(2);
        assertThat(round2.getStoryTitle()).isEqualTo("User Story 2");
        assertThat(round2.getVotes()).hasSize(1);

        // Verify user consistency metrics
        assertThat(result.getUserConsistency()).isNotEmpty();
        assertThat(result.getUserConsistency()).containsKey("Alice");
        // Alice voted [5, 8], mean = 6.5, variance = ((5-6.5)² + (8-6.5)²)/2 = (2.25 + 2.25)/2 = 2.25, stddev = 1.5
        assertThat(result.getUserConsistency().get("Alice")).isEqualByComparingTo(new BigDecimal("1.5000"));

        verify(featureGate).requireCanAccessAdvancedReports(proUser);
        verify(sessionHistoryService).getSessionById(testSessionId);
        verify(roundRepository).findByRoomId(testRoomId);
        verify(voteRepository).findByRoundId(testRound1.roundId);
        verify(voteRepository).findByRoundId(testRound2.roundId);
    }

    @Test
    void testGetDetailedSessionReport_FreeUser_ThrowsFeatureNotAvailableException() {
        // Given
        doThrow(new FeatureNotAvailableException(
                SubscriptionTier.PRO,
                SubscriptionTier.FREE,
                "Advanced Reports"
        )).when(featureGate).requireCanAccessAdvancedReports(freeUser);

        // When/Then
        assertThatThrownBy(() ->
                reportingService.getDetailedSessionReport(testSessionId, freeUser)
                        .await().indefinitely()
        )
                .isInstanceOf(FeatureNotAvailableException.class)
                .hasMessageContaining("Advanced Reports");

        verify(featureGate).requireCanAccessAdvancedReports(freeUser);
        verifyNoInteractions(sessionHistoryService);
    }

    @Test
    void testGetDetailedSessionReport_NullParameters_ThrowsException() {
        // When/Then - null sessionId
        assertThatThrownBy(() ->
                reportingService.getDetailedSessionReport(null, proUser)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId and user cannot be null");

        // When/Then - null user
        assertThatThrownBy(() ->
                reportingService.getDetailedSessionReport(testSessionId, null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId and user cannot be null");
    }

    @Test
    void testGetDetailedSessionReport_SessionWithNoRounds_ReturnsEmptyRoundsList() throws Exception {
        // Given
        doNothing().when(featureGate).requireCanAccessAdvancedReports(proUser);
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().item(testSession));
        when(objectMapper.readValue(eq(testSession.summaryStats), eq(SessionSummaryStats.class)))
                .thenReturn(summaryStats);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.databind.JavaType.class)))
                .thenReturn(participants);
        when(roundRepository.findByRoomId(testRoomId))
                .thenReturn(Uni.createFrom().item(List.of()));

        // When
        DetailedSessionReportDTO result = reportingService.getDetailedSessionReport(testSessionId, proUser)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRounds()).isEmpty();
        assertThat(result.getUserConsistency()).isEmpty();

        verify(roundRepository).findByRoomId(testRoomId);
        verifyNoInteractions(voteRepository);
    }

    @Test
    void testGetDetailedSessionReport_WithNonNumericVotes_ExcludesFromConsistency() throws Exception {
        // Given
        Vote nonNumericVote = new Vote();
        nonNumericVote.voteId = UUID.randomUUID();
        nonNumericVote.round = testRound2;
        nonNumericVote.participant = participant2;
        nonNumericVote.cardValue = "?";  // Non-numeric
        nonNumericVote.votedAt = Instant.now().minus(19, ChronoUnit.MINUTES);

        doNothing().when(featureGate).requireCanAccessAdvancedReports(proUser);
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().item(testSession));
        when(objectMapper.readValue(eq(testSession.summaryStats), eq(SessionSummaryStats.class)))
                .thenReturn(summaryStats);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.databind.JavaType.class)))
                .thenReturn(participants);
        when(roundRepository.findByRoomId(testRoomId))
                .thenReturn(Uni.createFrom().item(List.of(testRound1, testRound2)));
        when(voteRepository.findByRoundId(testRound1.roundId))
                .thenReturn(Uni.createFrom().item(List.of(vote1, vote2)));
        when(voteRepository.findByRoundId(testRound2.roundId))
                .thenReturn(Uni.createFrom().item(List.of(vote3, nonNumericVote)));

        // When
        DetailedSessionReportDTO result = reportingService.getDetailedSessionReport(testSessionId, proUser)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();

        // Alice has numeric votes [5, 8] so should have consistency metric
        assertThat(result.getUserConsistency()).containsKey("Alice");

        // Bob has one numeric vote [3] and one non-numeric [?]
        // Only 1 numeric vote, so should NOT have consistency metric (need >= 2)
        assertThat(result.getUserConsistency()).doesNotContainKey("Bob");

        // But verify the non-numeric vote is included in round details
        DetailedSessionReportDTO.RoundDetailDTO round2 = result.getRounds().get(1);
        assertThat(round2.getVotes()).hasSize(2);
        assertThat(round2.getVotes()).anyMatch(v -> v.getCardValue().equals("?"));
    }

    // ===== Export Job Generation Tests =====

    @Test
    void testGenerateExport_ProUserWithCSV_EnqueuesJobSuccessfully() {
        // Given
        String messageId = "1234567890-0";
        doNothing().when(featureGate).requireCanAccessAdvancedReports(proUser);
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().item(testSession));
        when(redisDataSource.stream(String.class, String.class, String.class)).thenReturn(streamCommands);
        when(streamCommands.xadd(eq("jobs:reports"), any(Map.class)))
                .thenReturn(Uni.createFrom().item(messageId));

        // When
        String result = reportingService.generateExport(testSessionId, "CSV", proUser)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");  // UUID format

        verify(featureGate).requireCanAccessAdvancedReports(proUser);
        verify(sessionHistoryService).getSessionById(testSessionId);

        ArgumentCaptor<Map<String, String>> jobDataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(streamCommands).xadd(eq("jobs:reports"), jobDataCaptor.capture());

        Map<String, String> jobData = jobDataCaptor.getValue();
        assertThat(jobData).containsKeys("jobId", "sessionId", "format", "userId", "requestedAt");
        assertThat(jobData.get("sessionId")).isEqualTo(testSessionId.toString());
        assertThat(jobData.get("format")).isEqualTo("CSV");
        assertThat(jobData.get("userId")).isEqualTo(proUser.userId.toString());
    }

    @Test
    void testGenerateExport_ProUserWithPDF_EnqueuesJobSuccessfully() {
        // Given
        String messageId = "1234567890-0";
        doNothing().when(featureGate).requireCanAccessAdvancedReports(proUser);
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().item(testSession));
        when(redisDataSource.stream(String.class, String.class, String.class)).thenReturn(streamCommands);
        when(streamCommands.xadd(eq("jobs:reports"), any(Map.class)))
                .thenReturn(Uni.createFrom().item(messageId));

        // When
        String result = reportingService.generateExport(testSessionId, "PDF", proUser)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<Map<String, String>> jobDataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(streamCommands).xadd(eq("jobs:reports"), jobDataCaptor.capture());

        Map<String, String> jobData = jobDataCaptor.getValue();
        assertThat(jobData.get("format")).isEqualTo("PDF");
    }

    @Test
    void testGenerateExport_FreeUser_ThrowsFeatureNotAvailableException() {
        // Given
        doThrow(new FeatureNotAvailableException(
                SubscriptionTier.PRO,
                SubscriptionTier.FREE,
                "Advanced Reports"
        )).when(featureGate).requireCanAccessAdvancedReports(freeUser);

        // When/Then
        assertThatThrownBy(() ->
                reportingService.generateExport(testSessionId, "CSV", freeUser)
                        .await().indefinitely()
        )
                .isInstanceOf(FeatureNotAvailableException.class)
                .hasMessageContaining("Advanced Reports");

        verify(featureGate).requireCanAccessAdvancedReports(freeUser);
        verifyNoInteractions(sessionHistoryService);
        verifyNoInteractions(streamCommands);
    }

    @Test
    void testGenerateExport_InvalidFormat_ThrowsException() {
        // Given
        doNothing().when(featureGate).requireCanAccessAdvancedReports(proUser);
        // Note: sessionHistoryService is not stubbed because validation happens first

        // When/Then
        assertThatThrownBy(() ->
                reportingService.generateExport(testSessionId, "INVALID", proUser)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format must be 'CSV' or 'PDF'");

        verifyNoInteractions(streamCommands);
    }

    @Test
    void testGenerateExport_SessionNotFound_ThrowsException() {
        // Given
        doNothing().when(featureGate).requireCanAccessAdvancedReports(proUser);
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                reportingService.generateExport(testSessionId, "CSV", proUser)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");

        verify(sessionHistoryService).getSessionById(testSessionId);
        verifyNoInteractions(streamCommands);
    }

    @Test
    void testGenerateExport_NullParameters_ThrowsException() {
        // When/Then - null sessionId
        assertThatThrownBy(() ->
                reportingService.generateExport(null, "CSV", proUser)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId, format, and user cannot be null");

        // When/Then - null format
        assertThatThrownBy(() ->
                reportingService.generateExport(testSessionId, null, proUser)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId, format, and user cannot be null");

        // When/Then - null user
        assertThatThrownBy(() ->
                reportingService.generateExport(testSessionId, "CSV", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId, format, and user cannot be null");
    }

    // ===== User Consistency Calculation Tests =====

    @Test
    void testUserConsistencyCalculation_PerfectConsistency_ReturnsZero() throws Exception {
        // Given - participant votes same value every time
        Vote consistentVote1 = new Vote();
        consistentVote1.voteId = UUID.randomUUID();
        consistentVote1.round = testRound1;
        consistentVote1.participant = participant1;
        consistentVote1.cardValue = "5";
        consistentVote1.votedAt = Instant.now();

        Vote consistentVote2 = new Vote();
        consistentVote2.voteId = UUID.randomUUID();
        consistentVote2.round = testRound2;
        consistentVote2.participant = participant1;
        consistentVote2.cardValue = "5";
        consistentVote2.votedAt = Instant.now();

        doNothing().when(featureGate).requireCanAccessAdvancedReports(proUser);
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().item(testSession));
        when(objectMapper.readValue(eq(testSession.summaryStats), eq(SessionSummaryStats.class)))
                .thenReturn(summaryStats);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.databind.JavaType.class)))
                .thenReturn(participants);
        when(roundRepository.findByRoomId(testRoomId))
                .thenReturn(Uni.createFrom().item(List.of(testRound1, testRound2)));
        when(voteRepository.findByRoundId(testRound1.roundId))
                .thenReturn(Uni.createFrom().item(List.of(consistentVote1)));
        when(voteRepository.findByRoundId(testRound2.roundId))
                .thenReturn(Uni.createFrom().item(List.of(consistentVote2)));

        // When
        DetailedSessionReportDTO result = reportingService.getDetailedSessionReport(testSessionId, proUser)
                .await().indefinitely();

        // Then
        assertThat(result.getUserConsistency()).containsKey("Alice");
        assertThat(result.getUserConsistency().get("Alice")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void testUserConsistencyCalculation_SingleVote_ExcludedFromResults() throws Exception {
        // Given - participant with only one numeric vote
        Vote singleVote = new Vote();
        singleVote.voteId = UUID.randomUUID();
        singleVote.round = testRound1;
        singleVote.participant = participant1;
        singleVote.cardValue = "5";
        singleVote.votedAt = Instant.now();

        doNothing().when(featureGate).requireCanAccessAdvancedReports(proUser);
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().item(testSession));
        when(objectMapper.readValue(eq(testSession.summaryStats), eq(SessionSummaryStats.class)))
                .thenReturn(summaryStats);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.databind.JavaType.class)))
                .thenReturn(participants);
        when(roundRepository.findByRoomId(testRoomId))
                .thenReturn(Uni.createFrom().item(List.of(testRound1)));
        when(voteRepository.findByRoundId(testRound1.roundId))
                .thenReturn(Uni.createFrom().item(List.of(singleVote)));

        // When
        DetailedSessionReportDTO result = reportingService.getDetailedSessionReport(testSessionId, proUser)
                .await().indefinitely();

        // Then - Alice should NOT be in consistency map (need >= 2 votes)
        assertThat(result.getUserConsistency()).doesNotContainKey("Alice");
    }

    @Test
    void testUserConsistencyCalculation_KnownVariance_ReturnsCorrectStdDev() throws Exception {
        // Given - votes with known variance
        // Alice votes: [1, 3, 8, 13]
        // Mean: (1 + 3 + 8 + 13) / 4 = 25 / 4 = 6.25
        // Variance: ((1-6.25)² + (3-6.25)² + (8-6.25)² + (13-6.25)²) / 4
        //         = (27.5625 + 10.5625 + 3.0625 + 45.5625) / 4
        //         = 86.75 / 4 = 21.6875
        // StdDev: sqrt(21.6875) ≈ 4.6569
        Round round3 = createRound(3);
        Round round4 = createRound(4);

        Vote aliceVote1 = createVote(testRound1, participant1, "1");
        Vote aliceVote2 = createVote(testRound2, participant1, "3");
        Vote aliceVote3 = createVote(round3, participant1, "8");
        Vote aliceVote4 = createVote(round4, participant1, "13");

        doNothing().when(featureGate).requireCanAccessAdvancedReports(proUser);
        when(sessionHistoryService.getSessionById(testSessionId))
                .thenReturn(Uni.createFrom().item(testSession));
        when(objectMapper.readValue(eq(testSession.summaryStats), eq(SessionSummaryStats.class)))
                .thenReturn(summaryStats);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.databind.JavaType.class)))
                .thenReturn(participants);
        when(roundRepository.findByRoomId(testRoomId))
                .thenReturn(Uni.createFrom().item(List.of(testRound1, testRound2, round3, round4)));
        when(voteRepository.findByRoundId(testRound1.roundId))
                .thenReturn(Uni.createFrom().item(List.of(aliceVote1)));
        when(voteRepository.findByRoundId(testRound2.roundId))
                .thenReturn(Uni.createFrom().item(List.of(aliceVote2)));
        when(voteRepository.findByRoundId(round3.roundId))
                .thenReturn(Uni.createFrom().item(List.of(aliceVote3)));
        when(voteRepository.findByRoundId(round4.roundId))
                .thenReturn(Uni.createFrom().item(List.of(aliceVote4)));

        // When
        DetailedSessionReportDTO result = reportingService.getDetailedSessionReport(testSessionId, proUser)
                .await().indefinitely();

        // Then
        assertThat(result.getUserConsistency()).containsKey("Alice");
        BigDecimal stdDev = result.getUserConsistency().get("Alice");
        assertThat(stdDev.doubleValue()).isCloseTo(4.6569, org.assertj.core.data.Offset.offset(0.0001));
    }

    // Helper methods

    private Round createRound(int roundNumber) {
        Round round = new Round();
        round.roundId = UUID.randomUUID();
        round.room = testRoom;
        round.roundNumber = roundNumber;
        round.storyTitle = "Story " + roundNumber;
        round.startedAt = Instant.now().minus(roundNumber * 10, ChronoUnit.MINUTES);
        round.revealedAt = Instant.now().minus((roundNumber * 10) - 2, ChronoUnit.MINUTES);
        round.average = BigDecimal.valueOf(5);
        round.median = "5";
        round.consensusReached = false;
        return round;
    }

    private Vote createVote(Round round, RoomParticipant participant, String cardValue) {
        Vote vote = new Vote();
        vote.voteId = UUID.randomUUID();
        vote.round = round;
        vote.participant = participant;
        vote.cardValue = cardValue;
        vote.votedAt = Instant.now();
        return vote;
    }
}
