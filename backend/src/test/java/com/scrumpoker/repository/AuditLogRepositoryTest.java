package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.AuditLog;
import com.scrumpoker.domain.organization.AuditLogId;
import com.scrumpoker.domain.organization.Organization;
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
 * Integration tests for AuditLogRepository.
 * Tests CRUD operations with composite key, JSONB metadata, and audit queries.
 */
@QuarkusTest
class AuditLogRepositoryTest {

    @Inject
    AuditLogRepository auditLogRepository;

    @Inject
    OrganizationRepository organizationRepository;

    @Inject
    UserRepository userRepository;

    private Organization testOrg;
    private User testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        auditLogRepository.deleteAll().await().indefinitely();
        organizationRepository.deleteAll().await().indefinitely();
        userRepository.deleteAll().await().indefinitely();

        testOrg = createTestOrganization("Audit Org", "audit.com");
        organizationRepository.persist(testOrg).await().indefinitely();

        testUser = createTestUser("audituser@example.com", "google", "google-audit");
        userRepository.persist(testUser).await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindByCompositeId() {
        // Given: a new audit log with composite key
        AuditLog log = createTestAuditLog(testOrg, testUser, "USER_LOGIN", Instant.now());

        // When: persisting the audit log
        auditLogRepository.persist(log).await().indefinitely();

        // Then: the audit log can be retrieved by composite ID
        AuditLog found = auditLogRepository.findById(log.id).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.action).isEqualTo("USER_LOGIN");
        assertThat(found.resourceType).isEqualTo("USER");
    }

    @Test
    @Transactional
    void testJsonbMetadataField() {
        // Given: audit log with JSONB metadata
        AuditLog log = createTestAuditLog(testOrg, testUser, "ROOM_CREATED", Instant.now());
        String metadataJson = "{\"roomId\":\"abc123\",\"privacyMode\":\"PUBLIC\",\"deckType\":\"fibonacci\"}";
        log.metadata = metadataJson;

        // When: persisting and retrieving
        auditLogRepository.persist(log).await().indefinitely();
        AuditLog found = auditLogRepository.findById(log.id).await().indefinitely();

        // Then: JSONB metadata persists correctly
        assertThat(found.metadata).isEqualTo(metadataJson);
        assertThat(found.metadata).contains("fibonacci");
    }

    @Test
    @Transactional
    void testFindByOrgId() {
        // Given: multiple audit logs for an organization
        AuditLog log1 = createTestAuditLog(testOrg, testUser, "USER_LOGIN", Instant.now().minus(2, ChronoUnit.HOURS));
        AuditLog log2 = createTestAuditLog(testOrg, testUser, "ROOM_CREATED", Instant.now().minus(1, ChronoUnit.HOURS));

        auditLogRepository.persist(log1).await().indefinitely();
        auditLogRepository.persist(log2).await().indefinitely();

        // When: finding logs by organization ID
        List<AuditLog> logs = auditLogRepository.findByOrgId(testOrg.orgId).await().indefinitely();

        // Then: all logs are returned
        assertThat(logs).hasSize(2);
    }

    @Test
    @Transactional
    void testFindByDateRange() {
        // Given: audit logs at different times
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant today = Instant.now();

        AuditLog oldLog = createTestAuditLog(testOrg, testUser, "OLD_ACTION", twoDaysAgo);
        AuditLog recentLog = createTestAuditLog(testOrg, testUser, "RECENT_ACTION", yesterday);

        auditLogRepository.persist(oldLog).await().indefinitely();
        auditLogRepository.persist(recentLog).await().indefinitely();

        // When: finding logs in date range
        Instant startDate = Instant.now().minus(36, ChronoUnit.HOURS);
        Instant endDate = today;
        List<AuditLog> logs = auditLogRepository.findByDateRange(startDate, endDate).await().indefinitely();

        // Then: only logs in range are returned
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).action).isEqualTo("RECENT_ACTION");
    }

    @Test
    @Transactional
    void testFindByAction() {
        // Given: audit logs with different actions
        AuditLog loginLog = createTestAuditLog(testOrg, testUser, "USER_LOGIN", Instant.now().minus(1, ChronoUnit.HOURS));
        AuditLog roomLog = createTestAuditLog(testOrg, testUser, "ROOM_CREATED", Instant.now());

        auditLogRepository.persist(loginLog).await().indefinitely();
        auditLogRepository.persist(roomLog).await().indefinitely();

        // When: finding logs by action
        List<AuditLog> loginLogs = auditLogRepository.findByAction("USER_LOGIN").await().indefinitely();

        // Then: only matching action logs are returned
        assertThat(loginLogs).hasSize(1);
        assertThat(loginLogs.get(0).action).isEqualTo("USER_LOGIN");
    }

    @Test
    @Transactional
    void testFindByResourceTypeAndId() {
        // Given: audit logs for different resources
        AuditLog roomLog = createTestAuditLog(testOrg, testUser, "ROOM_CREATED", Instant.now());
        roomLog.resourceType = "ROOM";
        roomLog.resourceId = "room123";

        auditLogRepository.persist(roomLog).await().indefinitely();

        // When: finding logs by resource type and ID
        List<AuditLog> roomLogs = auditLogRepository.findByResourceTypeAndId("ROOM", "room123")
                .await().indefinitely();

        // Then: matching resource logs are returned
        assertThat(roomLogs).hasSize(1);
        assertThat(roomLogs.get(0).resourceId).isEqualTo("room123");
    }

    @Test
    @Transactional
    void testCountByOrgId() {
        // Given: multiple audit logs
        auditLogRepository.persist(createTestAuditLog(testOrg, testUser, "ACTION1", Instant.now().minus(1, ChronoUnit.HOURS))).await().indefinitely();
        auditLogRepository.persist(createTestAuditLog(testOrg, testUser, "ACTION2", Instant.now())).await().indefinitely();

        // When: counting logs by organization
        Long count = auditLogRepository.countByOrgId(testOrg.orgId).await().indefinitely();

        // Then: correct count is returned
        assertThat(count).isEqualTo(2);
    }

    @Test
    @Transactional
    void testIpAddressStorage() {
        // Given: audit log with IP address
        AuditLog log = createTestAuditLog(testOrg, testUser, "USER_LOGIN", Instant.now());
        log.ipAddress = "192.168.1.100";
        log.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        // When: persisting and retrieving
        auditLogRepository.persist(log).await().indefinitely();
        AuditLog found = auditLogRepository.findById(log.id).await().indefinitely();

        // Then: IP address and user agent are persisted correctly
        assertThat(found.ipAddress).isEqualTo("192.168.1.100");
        assertThat(found.userAgent).contains("Mozilla");
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

    private Organization createTestOrganization(String name, String domain) {
        Organization org = new Organization();
        org.orgId = UUID.randomUUID();
        org.name = name;
        org.domain = domain;
        org.ssoConfig = "{}";
        org.branding = "{}";
        return org;
    }

    private AuditLog createTestAuditLog(Organization org, User user, String action, Instant timestamp) {
        AuditLog log = new AuditLog();
        log.id = new AuditLogId(UUID.randomUUID(), timestamp);
        log.organization = org;
        log.user = user;
        log.action = action;
        log.resourceType = "USER";
        log.resourceId = user.userId.toString();
        return log;
    }
}
