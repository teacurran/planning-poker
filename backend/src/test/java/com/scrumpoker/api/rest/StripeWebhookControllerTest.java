package com.scrumpoker.api.rest;

import com.scrumpoker.domain.billing.EntityType;
import com.scrumpoker.domain.billing.PaymentHistory;
import com.scrumpoker.domain.billing.Subscription;
import com.scrumpoker.domain.billing.SubscriptionStatus;
import com.scrumpoker.domain.billing.WebhookEventLog;
import com.scrumpoker.domain.billing.WebhookEventStatus;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.repository.PaymentHistoryRepository;
import com.scrumpoker.repository.SubscriptionRepository;
import com.scrumpoker.repository.WebhookEventLogRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for StripeWebhookController.
 * Tests webhook signature verification, idempotency, event processing,
 * and database updates for subscription lifecycle events.
 * <p>
 * Uses @QuarkusTest with Testcontainers PostgreSQL for full integration testing.
 * </p>
 */
@QuarkusTest
@TestProfile(NoSecurityTestProfile.class)
public class StripeWebhookControllerTest {

    /**
     * Test webhook secret for signature generation.
     * Must match the value configured in application.properties.
     */
    private static final String TEST_WEBHOOK_SECRET = "whsec_test_1234567890abcdefghijklmnopqrstuvwxyz";

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    PaymentHistoryRepository paymentHistoryRepository;

    @Inject
    WebhookEventLogRepository webhookEventLogRepository;

    @Inject
    com.scrumpoker.repository.UserRepository userRepository;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up test data before each test
        asserter.execute(() -> Panache.withTransaction(() ->
            paymentHistoryRepository.deleteAll()
                .flatMap(ignored -> subscriptionRepository.deleteAll())
                .flatMap(ignored -> webhookEventLogRepository.deleteAll())
                .flatMap(ignored -> userRepository.deleteAll())
        ));
    }

    // ========================================
    // Subscription Lifecycle Event Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testSubscriptionCreated_ValidEvent_UpdatesDatabase(UniAsserter asserter) throws Exception {
        // Load webhook payload
        String payload = loadWebhookPayload("webhook_subscription_created.json");
        String signature = generateStripeSignature(payload, TEST_WEBHOOK_SECRET);

        // Create existing subscription in database (created during checkout)
        // Webhook will sync its status to ACTIVE
        asserter.execute(() -> Panache.withTransaction(() ->
            createAndPersistTestSubscription(
                "sub_test_created_123",
                SubscriptionStatus.TRIALING
            )
        ));

        // Verify subscription was created BEFORE sending webhook
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_created_123")),
            subscription -> {
                assertThat(subscription).isNotNull();
                assertThat(subscription.status).isEqualTo(SubscriptionStatus.TRIALING);
            }
        );

        // Send webhook event (NOW the subscription exists in DB)
        asserter.execute(() -> Uni.createFrom().item(() -> {
            given()
                .contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
            .when()
                .post("/api/v1/subscriptions/webhook")
            .then()
                .statusCode(200);
            return null;
        }));

        // Verify subscription status updated to ACTIVE
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_created_123")),
            subscription -> {
                assertThat(subscription).isNotNull();
                assertThat(subscription.status).isEqualTo(SubscriptionStatus.ACTIVE);
                assertThat(subscription.stripeSubscriptionId).isEqualTo("sub_test_created_123");
            }
        );

        // Verify WebhookEventLog entry created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            webhookEventLogRepository.findByEventId("evt_test_subscription_created")),
            eventLog -> {
                assertThat(eventLog).isNotNull();
                assertThat(eventLog.eventType).isEqualTo("customer.subscription.created");
                assertThat(eventLog.status).isEqualTo(WebhookEventStatus.PROCESSED);
            }
        );
    }

    @Test
    @RunOnVertxContext
    public void testSubscriptionUpdated_ValidEvent_UpdatesDatabase(UniAsserter asserter) throws Exception {
        // Load webhook payload
        String payload = loadWebhookPayload("webhook_subscription_updated.json");
        String signature = generateStripeSignature(payload, TEST_WEBHOOK_SECRET);

        // Create existing subscription in database
        asserter.execute(() -> Panache.withTransaction(() ->
            createAndPersistTestSubscription(
                "sub_test_updated_456",
                SubscriptionStatus.ACTIVE
            )
        ));

        // Verify subscription was created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_updated_456")),
            subscription -> assertThat(subscription).isNotNull()
        );

        // Send webhook event
        asserter.execute(() -> Uni.createFrom().item(() -> {
            given()
                .contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
            .when()
                .post("/api/v1/subscriptions/webhook")
            .then()
                .statusCode(200);
            return null;
        }));

        // Verify subscription status updated to PAST_DUE
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_updated_456")),
            subscription -> {
                assertThat(subscription).isNotNull();
                assertThat(subscription.status).isEqualTo(SubscriptionStatus.PAST_DUE);
            }
        );

        // Verify WebhookEventLog entry created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            webhookEventLogRepository.findByEventId("evt_test_subscription_updated")),
            eventLog -> {
                assertThat(eventLog).isNotNull();
                assertThat(eventLog.eventType).isEqualTo("customer.subscription.updated");
                assertThat(eventLog.status).isEqualTo(WebhookEventStatus.PROCESSED);
            }
        );
    }

    @Test
    @RunOnVertxContext
    public void testSubscriptionDeleted_ValidEvent_UpdatesDatabase(UniAsserter asserter) throws Exception {
        // Load webhook payload
        String payload = loadWebhookPayload("webhook_subscription_deleted.json");
        String signature = generateStripeSignature(payload, TEST_WEBHOOK_SECRET);

        // Create existing subscription in database
        asserter.execute(() -> Panache.withTransaction(() ->
            createAndPersistTestSubscription(
                "sub_test_deleted_789",
                SubscriptionStatus.ACTIVE
            )
        ));

        // Verify subscription was created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_deleted_789")),
            subscription -> assertThat(subscription).isNotNull()
        );

        // Send webhook event
        asserter.execute(() -> Uni.createFrom().item(() -> {
            given()
                .contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
            .when()
                .post("/api/v1/subscriptions/webhook")
            .then()
                .statusCode(200);
            return null;
        }));

        // Verify subscription status updated to CANCELED
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_deleted_789")),
            subscription -> {
                assertThat(subscription).isNotNull();
                assertThat(subscription.status).isEqualTo(SubscriptionStatus.CANCELED);
                assertThat(subscription.canceledAt).isNotNull();
            }
        );

        // Verify WebhookEventLog entry created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            webhookEventLogRepository.findByEventId("evt_test_subscription_deleted")),
            eventLog -> {
                assertThat(eventLog).isNotNull();
                assertThat(eventLog.eventType).isEqualTo("customer.subscription.deleted");
                assertThat(eventLog.status).isEqualTo(WebhookEventStatus.PROCESSED);
            }
        );
    }

    // ========================================
    // Invoice Payment Event Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testInvoicePaymentSucceeded_ValidEvent_CreatesPaymentHistory(UniAsserter asserter) throws Exception {
        // Load webhook payload
        String payload = loadWebhookPayload("webhook_invoice_payment_succeeded.json");
        String signature = generateStripeSignature(payload, TEST_WEBHOOK_SECRET);

        // Create existing subscription in database (required for payment association)
        asserter.execute(() -> Panache.withTransaction(() ->
            createAndPersistTestSubscription(
                "sub_test_payment_001",
                SubscriptionStatus.ACTIVE
            )
        ));

        // Verify subscription was created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_payment_001")),
            subscription -> assertThat(subscription).isNotNull()
        );

        // Send webhook event
        asserter.execute(() -> Uni.createFrom().item(() -> {
            given()
                .contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
            .when()
                .post("/api/v1/subscriptions/webhook")
            .then()
                .statusCode(200);
            return null;
        }));

        // Verify PaymentHistory record created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            paymentHistoryRepository.findByStripeInvoiceId("in_test_payment_succeeded_001")),
            payment -> {
                assertThat(payment).isNotNull();
                assertThat(payment.stripeInvoiceId).isEqualTo("in_test_payment_succeeded_001");
                assertThat(payment.amount).isEqualTo(2999);
                assertThat(payment.currency).isEqualTo("USD");
                assertThat(payment.status).isEqualTo(com.scrumpoker.domain.billing.PaymentStatus.SUCCEEDED);
                assertThat(payment.paidAt).isNotNull();
            }
        );

        // Verify WebhookEventLog entry created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            webhookEventLogRepository.findByEventId("evt_test_invoice_payment_succeeded")),
            eventLog -> {
                assertThat(eventLog).isNotNull();
                assertThat(eventLog.eventType).isEqualTo("invoice.payment_succeeded");
                assertThat(eventLog.status).isEqualTo(WebhookEventStatus.PROCESSED);
            }
        );
    }

    @Test
    @RunOnVertxContext
    public void testInvoicePaymentFailed_ValidEvent_UpdatesSubscriptionStatus(UniAsserter asserter) throws Exception {
        // Load webhook payload
        String payload = loadWebhookPayload("webhook_invoice_payment_failed.json");
        String signature = generateStripeSignature(payload, TEST_WEBHOOK_SECRET);

        // Create existing subscription in database
        asserter.execute(() -> Panache.withTransaction(() ->
            createAndPersistTestSubscription(
                "sub_test_payment_failed_002",
                SubscriptionStatus.ACTIVE
            )
        ));

        // Verify subscription was created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_payment_failed_002")),
            subscription -> assertThat(subscription).isNotNull()
        );

        // Send webhook event
        asserter.execute(() -> Uni.createFrom().item(() -> {
            given()
                .contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
            .when()
                .post("/api/v1/subscriptions/webhook")
            .then()
                .statusCode(200);
            return null;
        }));

        // Verify subscription status updated to PAST_DUE
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_payment_failed_002")),
            subscription -> {
                assertThat(subscription).isNotNull();
                assertThat(subscription.status).isEqualTo(SubscriptionStatus.PAST_DUE);
            }
        );

        // Verify WebhookEventLog entry created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            webhookEventLogRepository.findByEventId("evt_test_invoice_payment_failed")),
            eventLog -> {
                assertThat(eventLog).isNotNull();
                assertThat(eventLog.eventType).isEqualTo("invoice.payment_failed");
                assertThat(eventLog.status).isEqualTo(WebhookEventStatus.PROCESSED);
            }
        );
    }

    // ========================================
    // Signature Verification Tests
    // ========================================

    @Test
    public void testInvalidSignature_Returns401() throws Exception {
        // Load webhook payload
        String payload = loadWebhookPayload("webhook_subscription_created.json");

        // Send webhook event with invalid signature
        given()
            .contentType(ContentType.JSON)
            .header("Stripe-Signature", "invalid_signature_value")
            .body(payload)
        .when()
            .post("/api/v1/subscriptions/webhook")
        .then()
            .statusCode(401);
    }

    @Test
    public void testMissingSignature_Returns401() throws Exception {
        // Load webhook payload
        String payload = loadWebhookPayload("webhook_subscription_created.json");

        // Send webhook event without signature header
        given()
            .contentType(ContentType.JSON)
            .body(payload)
        .when()
            .post("/api/v1/subscriptions/webhook")
        .then()
            .statusCode(401);
    }

    // ========================================
    // Idempotency Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testIdempotency_DuplicateEvent_SkipsProcessing(UniAsserter asserter) throws Exception {
        // Load webhook payload
        String payload = loadWebhookPayload("webhook_subscription_created.json");
        String signature = generateStripeSignature(payload, TEST_WEBHOOK_SECRET);

        // Create existing subscription in database
        asserter.execute(() -> Panache.withTransaction(() ->
            createAndPersistTestSubscription(
                "sub_test_created_123",
                SubscriptionStatus.TRIALING
            )
        ));

        // Verify subscription was created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_created_123")),
            subscription -> assertThat(subscription).isNotNull()
        );

        // Send webhook event first time
        asserter.execute(() -> Uni.createFrom().item(() -> {
            given()
                .contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
            .when()
                .post("/api/v1/subscriptions/webhook")
            .then()
                .statusCode(200);
            return null;
        }));

        // Verify first processing created records
        asserter.assertThat(() -> Panache.withTransaction(() ->
            webhookEventLogRepository.findByEventId("evt_test_subscription_created")),
            eventLog -> assertThat(eventLog).isNotNull()
        );

        // Send same webhook event second time (duplicate)
        asserter.execute(() -> Uni.createFrom().item(() -> {
            given()
                .contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
            .when()
                .post("/api/v1/subscriptions/webhook")
            .then()
                .statusCode(200);
            return null;
        }));

        // Verify only ONE WebhookEventLog entry exists (idempotency working)
        asserter.assertThat(() -> Panache.withTransaction(() ->
            webhookEventLogRepository.count()),
            count -> assertThat(count).isEqualTo(1L)
        );

        // Verify only ONE subscription exists (no duplicate created)
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.count()),
            count -> assertThat(count).isEqualTo(1L)
        );
    }

    @Test
    @RunOnVertxContext
    public void testIdempotency_DuplicatePaymentEvent_SkipsPaymentCreation(UniAsserter asserter) throws Exception {
        // Load webhook payload
        String payload = loadWebhookPayload("webhook_invoice_payment_succeeded.json");
        String signature = generateStripeSignature(payload, TEST_WEBHOOK_SECRET);

        // Create existing subscription
        asserter.execute(() -> Panache.withTransaction(() ->
            createAndPersistTestSubscription(
                "sub_test_payment_001",
                SubscriptionStatus.ACTIVE
            )
        ));

        // Verify subscription was created
        asserter.assertThat(() -> Panache.withTransaction(() ->
            subscriptionRepository.findByStripeSubscriptionId("sub_test_payment_001")),
            subscription -> assertThat(subscription).isNotNull()
        );

        // Send payment webhook first time
        asserter.execute(() -> Uni.createFrom().item(() -> {
            given()
                .contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
            .when()
                .post("/api/v1/subscriptions/webhook")
            .then()
                .statusCode(200);
            return null;
        }));

        // Send same payment webhook second time
        asserter.execute(() -> Uni.createFrom().item(() -> {
            given()
                .contentType(ContentType.JSON)
                .header("Stripe-Signature", signature)
                .body(payload)
            .when()
                .post("/api/v1/subscriptions/webhook")
            .then()
                .statusCode(200);
            return null;
        }));

        // Verify only ONE PaymentHistory record exists
        asserter.assertThat(() -> Panache.withTransaction(() ->
            paymentHistoryRepository.count()),
            count -> assertThat(count).isEqualTo(1L)
        );

        // Verify only ONE WebhookEventLog entry exists
        asserter.assertThat(() -> Panache.withTransaction(() ->
            webhookEventLogRepository.count()),
            count -> assertThat(count).isEqualTo(1L)
        );
    }

    // ========================================
    // Error Handling Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testProcessingFailure_SubscriptionNotFound_Returns200AndLogsFailure(UniAsserter asserter) throws Exception {
        // Load payment webhook for non-existent subscription
        String payload = loadWebhookPayload("webhook_invoice_payment_succeeded.json");
        String signature = generateStripeSignature(payload, TEST_WEBHOOK_SECRET);

        // Send webhook event (subscription does not exist, so payment should fail)
        given()
            .contentType(ContentType.JSON)
            .header("Stripe-Signature", signature)
            .body(payload)
        .when()
            .post("/api/v1/subscriptions/webhook")
        .then()
            .statusCode(200);  // Still returns 200 to prevent Stripe retries

        // Verify NO PaymentHistory record created (processing failed)
        asserter.assertThat(() -> Panache.withTransaction(() ->
            paymentHistoryRepository.count()),
            count -> assertThat(count).isEqualTo(0L)
        );

        // Verify WebhookEventLog entry created with PROCESSED status
        // (The handler skips silently if subscription not found, logs warning, returns success)
        asserter.assertThat(() -> Panache.withTransaction(() ->
            webhookEventLogRepository.findByEventId("evt_test_invoice_payment_succeeded")),
            eventLog -> {
                assertThat(eventLog).isNotNull();
                assertThat(eventLog.status).isEqualTo(WebhookEventStatus.PROCESSED);
            }
        );
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Loads webhook payload from test resources.
     *
     * @param filename JSON file name in test/resources/stripe/
     * @return Webhook payload as string
     * @throws IOException if file not found or cannot be read
     */
    private String loadWebhookPayload(String filename) throws IOException {
        // Use classpath resource loading instead of file path
        // This works correctly regardless of the project structure
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("stripe/" + filename)) {
            if (stream == null) {
                throw new IOException("Resource not found: stripe/" + filename);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Generates Stripe webhook signature using HMAC-SHA256.
     * Matches Stripe's signature format: t=timestamp,v1=signature
     * <p>
     * Based on Stripe's webhook signature verification algorithm:
     * https://stripe.com/docs/webhooks/signatures
     * </p>
     *
     * @param payload Webhook payload JSON
     * @param secret Webhook signing secret
     * @return Formatted signature header value
     * @throws Exception if signature generation fails
     */
    private String generateStripeSignature(String payload, String secret) throws Exception {
        // Use current timestamp - Stripe has a 5 minute tolerance window
        // so we need to use a recent timestamp
        long timestamp = Instant.now().getEpochSecond();

        // Construct signed payload exactly as Stripe does: timestamp.payload
        String signedPayload = timestamp + "." + payload;

        // Compute HMAC-SHA256
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));

        // Convert to hex string (lowercase, as Stripe uses)
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        // Return Stripe signature format: t=timestamp,v1=signature
        // Note: No spaces around '=' or ','
        return "t=" + timestamp + ",v1=" + hexString.toString();
    }

    /**
     * Creates and persists a test subscription with associated user.
     * Returns a Uni that completes with the persisted subscription.
     *
     * @param stripeSubscriptionId Stripe subscription ID
     * @param status Subscription status
     * @return Uni<Subscription> that completes after both user and subscription are persisted
     */
    private Uni<Subscription> createAndPersistTestSubscription(String stripeSubscriptionId, SubscriptionStatus status) {
        // Create test user
        com.scrumpoker.domain.user.User testUser = new com.scrumpoker.domain.user.User();
        testUser.email = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        testUser.oauthProvider = "google";
        testUser.oauthSubject = "google-test-" + UUID.randomUUID().toString();
        testUser.displayName = "Test User";
        testUser.subscriptionTier = SubscriptionTier.FREE;

        // Persist user first to get generated userId
        return userRepository.persist(testUser)
            .onItem().transformToUni(persistedUser -> {
                // Now create subscription with the user's ID
                Subscription subscription = new Subscription();
                subscription.stripeSubscriptionId = stripeSubscriptionId;
                subscription.entityId = persistedUser.userId;
                subscription.entityType = EntityType.USER;
                subscription.tier = SubscriptionTier.PRO;
                subscription.status = status;
                subscription.currentPeriodStart = Instant.now();
                subscription.currentPeriodEnd = Instant.now().plusSeconds(2592000L); // 30 days
                subscription.createdAt = Instant.now();
                subscription.updatedAt = Instant.now();

                // Persist subscription
                return subscriptionRepository.persist(subscription);
            });
    }

    /**
     * Creates test subscription (without persisting).
     * DEPRECATED: Use createAndPersistTestSubscription instead for tests that need user entities.
     */
    private Subscription createTestSubscription(String stripeSubscriptionId, SubscriptionStatus status) {
        Subscription subscription = new Subscription();
        subscription.stripeSubscriptionId = stripeSubscriptionId;
        subscription.entityId = UUID.randomUUID();  // Random UUID (user won't exist!)
        subscription.entityType = EntityType.USER;
        subscription.tier = SubscriptionTier.PRO;
        subscription.status = status;
        subscription.currentPeriodStart = Instant.now();
        subscription.currentPeriodEnd = Instant.now().plusSeconds(2592000L);
        subscription.createdAt = Instant.now();
        subscription.updatedAt = Instant.now();
        return subscription;
    }
}
