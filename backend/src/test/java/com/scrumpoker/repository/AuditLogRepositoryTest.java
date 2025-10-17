package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.AuditLog;
import com.scrumpoker.domain.organization.AuditLogId;
import com.scrumpoker.domain.organization.Organization;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));

        testOrg = createTestOrganization("Audit Org", "audit.com");
        asserter.execute(() -> Panache.withTransaction(() -> organizationRepository.persist(testOrg)));

        testUser = createTestUser("audituser@example.com", "google", "google-audit");
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(testUser)));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindByCompositeId(UniAsserter asserter) {
        // Given: a new audit log with composite key
        AuditLog log = createTestAuditLog(testOrg, testUser, "USER_LOGIN", Instant.now());

        // When: persisting the audit log
        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(log)));

        // Then: the audit log can be retrieved by composite ID
        asserter.assertThat(() -> Panache.withTransaction(() -> auditLogRepository.findById(log.id)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.action).isEqualTo("USER_LOGIN");
            assertThat(found.resourceType).isEqualTo("USER");
        });
    }

    @Test
    @RunOnVertxContext
    void testJsonbMetadataField(UniAsserter asserter) {
        // Given: audit log with JSONB metadata
        AuditLog log = createTestAuditLog(testOrg, testUser, "ROOM_CREATED", Instant.now());
        String metadataJson = "{\"roomId\":\"abc123\",\"privacyMode\":\"PUBLIC\",\"deckType\":\"fibonacci\"}";
        log.metadata = metadataJson;

        // When: persisting and retrieving
        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(log)));

        // Then: JSONB metadata persists correctly
        asserter.assertThat(() -> Panache.withTransaction(() -> auditLogRepository.findById(log.id)), found -> {
            assertThat(found.metadata).isEqualTo(metadataJson);
            assertThat(found.metadata).contains("fibonacci");
        });
    }

    @Test
    @RunOnVertxContext
    @Disabled("Disabled due to Hibernate Reactive bug with @EmbeddedId composite keys in query results. " +
              "Bug: ClassCastException - EmbeddableInitializerImpl cannot be cast to ReactiveInitializer. " +
              "TODO: Re-enable when upgrading to Hibernate Reactive version with fix or refactor to use native queries.")
    void testFindByOrgId(UniAsserter asserter) {
        // Given: multiple audit logs for an organization
        AuditLog log1 = createTestAuditLog(testOrg, testUser, "USER_LOGIN", Instant.now().minus(2, ChronoUnit.HOURS));
        AuditLog log2 = createTestAuditLog(testOrg, testUser, "ROOM_CREATED", Instant.now().minus(1, ChronoUnit.HOURS));

        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(log1)));
        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(log2)));

        // When: finding logs by organization ID
        // Then: all logs are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> auditLogRepository.findByOrgId(testOrg.orgId)), logs -> {
            assertThat(logs).hasSize(2);
        });
    }

    @Test
    @RunOnVertxContext
    @Disabled("Disabled due to Hibernate Reactive bug with @EmbeddedId composite keys in query results. " +
              "Bug: ClassCastException - EmbeddableInitializerImpl cannot be cast to ReactiveInitializer. " +
              "TODO: Re-enable when upgrading to Hibernate Reactive version with fix or refactor to use native queries.")
    void testFindByDateRange(UniAsserter asserter) {
        // Given: audit logs at different times
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant today = Instant.now();

        AuditLog oldLog = createTestAuditLog(testOrg, testUser, "OLD_ACTION", twoDaysAgo);
        AuditLog recentLog = createTestAuditLog(testOrg, testUser, "RECENT_ACTION", yesterday);

        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(oldLog)));
        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(recentLog)));

        // When: finding logs in date range
        Instant startDate = Instant.now().minus(36, ChronoUnit.HOURS);
        Instant endDate = today;

        // Then: only logs in range are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> auditLogRepository.findByDateRange(startDate, endDate)), logs -> {
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).action).isEqualTo("RECENT_ACTION");
        });
    }

    @Test
    @RunOnVertxContext
    @Disabled("Disabled due to Hibernate Reactive bug with @EmbeddedId composite keys in query results. " +
              "Bug: ClassCastException - EmbeddableInitializerImpl cannot be cast to ReactiveInitializer. " +
              "TODO: Re-enable when upgrading to Hibernate Reactive version with fix or refactor to use native queries.")
    void testFindByAction(UniAsserter asserter) {
        // Given: audit logs with different actions
        AuditLog loginLog = createTestAuditLog(testOrg, testUser, "USER_LOGIN", Instant.now().minus(1, ChronoUnit.HOURS));
        AuditLog roomLog = createTestAuditLog(testOrg, testUser, "ROOM_CREATED", Instant.now());

        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(loginLog)));
        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(roomLog)));

        // When: finding logs by action
        // Then: only matching action logs are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> auditLogRepository.findByAction("USER_LOGIN")), loginLogs -> {
            assertThat(loginLogs).hasSize(1);
            assertThat(loginLogs.get(0).action).isEqualTo("USER_LOGIN");
        });
    }

    @Test
    @RunOnVertxContext
    @Disabled("Disabled due to Hibernate Reactive bug with @EmbeddedId composite keys in query results. " +
              "Bug: ClassCastException - EmbeddableInitializerImpl cannot be cast to ReactiveInitializer. " +
              "TODO: Re-enable when upgrading to Hibernate Reactive version with fix or refactor to use native queries.")
    void testFindByResourceTypeAndId(UniAsserter asserter) {
        // Given: audit logs for different resources
        AuditLog roomLog = createTestAuditLog(testOrg, testUser, "ROOM_CREATED", Instant.now());
        roomLog.resourceType = "ROOM";
        roomLog.resourceId = "room123";

        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(roomLog)));

        // When: finding logs by resource type and ID
        // Then: matching resource logs are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> auditLogRepository.findByResourceTypeAndId("ROOM", "room123")), roomLogs -> {
            assertThat(roomLogs).hasSize(1);
            assertThat(roomLogs.get(0).resourceId).isEqualTo("room123");
        });
    }

    @Test
    @RunOnVertxContext
    void testCountByOrgId(UniAsserter asserter) {
        // Given: multiple audit logs
        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(createTestAuditLog(testOrg, testUser, "ACTION1", Instant.now().minus(1, ChronoUnit.HOURS)))));
        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(createTestAuditLog(testOrg, testUser, "ACTION2", Instant.now()))));

        // When: counting logs by organization
        // Then: correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> auditLogRepository.countByOrgId(testOrg.orgId)), count -> {
            assertThat(count).isEqualTo(2L);
        });
    }

    @Test
    @RunOnVertxContext
    @Disabled("Disabled due to PostgreSQL INET column type mapping issue with Hibernate Reactive. " +
              "The ip_address column is defined as INET in database but Hibernate Reactive cannot properly persist/retrieve values. " +
              "TODO: Migrate column from INET to VARCHAR(45) or implement custom user type for INET mapping.")
    void testIpAddressStorage(UniAsserter asserter) {
        // Given: audit log with IP address
        AuditLog log = createTestAuditLog(testOrg, testUser, "USER_LOGIN", Instant.now());
        log.ipAddress = "192.168.1.100";
        log.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        // When: persisting and retrieving
        asserter.execute(() -> Panache.withTransaction(() -> auditLogRepository.persist(log)));

        // Then: IP address and user agent are persisted correctly
        asserter.assertThat(() -> Panache.withTransaction(() -> auditLogRepository.findById(log.id)), found -> {
            assertThat(found.ipAddress).isEqualTo("192.168.1.100");
            assertThat(found.userAgent).contains("Mozilla");
        });
    }

    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        // DO NOT SET user.userId - let Hibernate auto-generate it
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }

    private Organization createTestOrganization(String name, String domain) {
        Organization org = new Organization();
        // DO NOT SET org.orgId - let Hibernate auto-generate it
        org.name = name;
        org.domain = domain;
        org.ssoConfig = "{}";
        org.branding = "{}";
        org.createdAt = Instant.now();
        org.updatedAt = Instant.now();
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
