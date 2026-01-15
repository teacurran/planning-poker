package com.scrumpoker.repository;

import com.scrumpoker.domain.billing.WebhookEventLog;
import com.scrumpoker.domain.billing.WebhookEventStatus;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for WebhookEventLogRepository.
 * Tests CRUD operations, idempotency checks, and webhook event tracking queries.
 */
@QuarkusTest
@TestProfile(RepositoryTestProfile.class)
class WebhookEventLogRepositoryTest {

    @Inject
    WebhookEventLogRepository webhookEventLogRepository;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up webhook event logs
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.deleteAll()));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given: a new webhook event log
        WebhookEventLog log = createTestWebhookEventLog("evt_test_001", "customer.subscription.created");

        // When: persisting the event log
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(log)));

        // Then: the event log can be retrieved by event ID
        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.findById("evt_test_001")), found -> {
            assertThat(found).isNotNull();
            assertThat(found.eventId).isEqualTo("evt_test_001");
            assertThat(found.eventType).isEqualTo("customer.subscription.created");
            assertThat(found.status).isEqualTo(WebhookEventStatus.PROCESSED);
            assertThat(found.processedAt).isNotNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByEventId(UniAsserter asserter) {
        // Given: a webhook event log
        WebhookEventLog log = createTestWebhookEventLog("evt_test_002", "customer.subscription.updated");

        // When: persisting and finding by event ID
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(log)));

        // Then: findByEventId returns the same result as findById
        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.findByEventId("evt_test_002")), found -> {
            assertThat(found).isNotNull();
            assertThat(found.eventId).isEqualTo("evt_test_002");
            assertThat(found.eventType).isEqualTo("customer.subscription.updated");
        });
    }

    @Test
    @RunOnVertxContext
    void testIdempotencyCheck(UniAsserter asserter) {
        // Given: a processed event
        WebhookEventLog log = createTestWebhookEventLog("evt_test_003", "invoice.payment_succeeded");
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(log)));

        // When: checking for duplicate event ID
        // Then: the event is found, indicating it has already been processed
        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.findByEventId("evt_test_003")), found -> {
            assertThat(found).isNotNull();
            assertThat(found.eventId).isEqualTo("evt_test_003");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByDateRange(UniAsserter asserter) {
        // Given: webhook events processed at different times
        // Note: processedAt is @CreationTimestamp, so we cannot set it manually
        // Instead, we test that the query executes correctly
        Instant now = Instant.now();

        WebhookEventLog log1 = createTestWebhookEventLog("evt_range_001", "customer.created");
        WebhookEventLog log2 = createTestWebhookEventLog("evt_range_002", "customer.updated");

        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(log1)));
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(log2)));

        // When: finding logs in date range (last hour)
        Instant startDate = now.minus(1, ChronoUnit.HOURS);
        Instant endDate = now.plus(1, ChronoUnit.HOURS);

        // Then: both logs are returned (they were just created)
        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.findByDateRange(startDate, endDate)), logs -> {
            assertThat(logs).hasSize(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByStatus(UniAsserter asserter) {
        // Given: webhook events with different statuses
        WebhookEventLog processedLog = createTestWebhookEventLog("evt_success_001", "customer.subscription.created");
        processedLog.status = WebhookEventStatus.PROCESSED;

        WebhookEventLog failedLog = createTestWebhookEventLog("evt_failed_001", "customer.subscription.deleted");
        failedLog.status = WebhookEventStatus.FAILED;

        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(processedLog)));
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(failedLog)));

        // When: finding logs by PROCESSED status
        // Then: only processed logs are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.findByStatus(WebhookEventStatus.PROCESSED)), processedLogs -> {
            assertThat(processedLogs).hasSize(1);
            assertThat(processedLogs.get(0).eventId).isEqualTo("evt_success_001");
            assertThat(processedLogs.get(0).status).isEqualTo(WebhookEventStatus.PROCESSED);
        });

        // When: finding logs by FAILED status
        // Then: only failed logs are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.findByStatus(WebhookEventStatus.FAILED)), failedLogs -> {
            assertThat(failedLogs).hasSize(1);
            assertThat(failedLogs.get(0).eventId).isEqualTo("evt_failed_001");
            assertThat(failedLogs.get(0).status).isEqualTo(WebhookEventStatus.FAILED);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByEventType(UniAsserter asserter) {
        // Given: webhook events of different types
        WebhookEventLog subscriptionCreated = createTestWebhookEventLog("evt_sub_created_001", "customer.subscription.created");
        WebhookEventLog subscriptionUpdated = createTestWebhookEventLog("evt_sub_updated_001", "customer.subscription.updated");
        WebhookEventLog invoicePayment = createTestWebhookEventLog("evt_invoice_001", "invoice.payment_succeeded");

        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(subscriptionCreated)));
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(subscriptionUpdated)));
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(invoicePayment)));

        // When: finding logs by event type
        // Then: only matching event types are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.findByEventType("customer.subscription.created")), createdLogs -> {
            assertThat(createdLogs).hasSize(1);
            assertThat(createdLogs.get(0).eventType).isEqualTo("customer.subscription.created");
        });

        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.findByEventType("invoice.payment_succeeded")), invoiceLogs -> {
            assertThat(invoiceLogs).hasSize(1);
            assertThat(invoiceLogs.get(0).eventType).isEqualTo("invoice.payment_succeeded");
        });
    }

    @Test
    @RunOnVertxContext
    void testCountFailed(UniAsserter asserter) {
        // Given: multiple webhook events with different statuses
        WebhookEventLog processed1 = createTestWebhookEventLog("evt_ok_001", "customer.created");
        processed1.status = WebhookEventStatus.PROCESSED;

        WebhookEventLog failed1 = createTestWebhookEventLog("evt_fail_001", "customer.updated");
        failed1.status = WebhookEventStatus.FAILED;

        WebhookEventLog failed2 = createTestWebhookEventLog("evt_fail_002", "customer.deleted");
        failed2.status = WebhookEventStatus.FAILED;

        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(processed1)));
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(failed1)));
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(failed2)));

        // When: counting failed events
        // Then: correct count of failed events is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.countFailed()), count -> {
            assertThat(count).isEqualTo(2L);
        });
    }

    @Test
    @RunOnVertxContext
    void testMultipleEventsOrdering(UniAsserter asserter) {
        // Given: multiple events processed at different times
        // Note: processedAt is @CreationTimestamp, so we cannot control exact timestamps
        // We test that events are returned in descending order by processedAt
        Instant now = Instant.now();

        WebhookEventLog event1 = createTestWebhookEventLog("evt_ord_001", "customer.subscription.created");
        WebhookEventLog event2 = createTestWebhookEventLog("evt_ord_002", "customer.subscription.updated");
        WebhookEventLog event3 = createTestWebhookEventLog("evt_ord_003", "customer.subscription.deleted");

        // Persist events sequentially to ensure different timestamps
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(event1)));
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(event2)));
        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(event3)));

        // When: finding by date range
        Instant startDate = now.minus(1, ChronoUnit.HOURS);
        Instant endDate = now.plus(1, ChronoUnit.HOURS);

        // Then: all events are returned and ordered by processedAt descending
        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.findByDateRange(startDate, endDate)), logs -> {
            assertThat(logs).hasSize(3);
            // Verify descending order: most recent first (last persisted)
            assertThat(logs.get(0).eventId).isEqualTo("evt_ord_003");
            assertThat(logs.get(1).eventId).isEqualTo("evt_ord_002");
            assertThat(logs.get(2).eventId).isEqualTo("evt_ord_001");
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdateEventStatus(UniAsserter asserter) {
        // Given: a webhook event with PROCESSED status
        WebhookEventLog log = createTestWebhookEventLog("evt_update_001", "invoice.created");
        log.status = WebhookEventStatus.PROCESSED;

        asserter.execute(() -> Panache.withTransaction(() -> webhookEventLogRepository.persist(log)));

        // When: updating status to FAILED
        asserter.execute(() -> Panache.withTransaction(() ->
            webhookEventLogRepository.findById("evt_update_001").map(found -> {
                found.status = WebhookEventStatus.FAILED;
                return found;
            })
        ));

        // Then: the updated status is persisted
        asserter.assertThat(() -> Panache.withTransaction(() -> webhookEventLogRepository.findById("evt_update_001")), updated -> {
            assertThat(updated).isNotNull();
            assertThat(updated.status).isEqualTo(WebhookEventStatus.FAILED);
        });
    }

    private WebhookEventLog createTestWebhookEventLog(String eventId, String eventType) {
        WebhookEventLog log = new WebhookEventLog();
        log.eventId = eventId;
        log.eventType = eventType;
        log.status = WebhookEventStatus.PROCESSED;
        log.processedAt = Instant.now();
        return log;
    }
}
