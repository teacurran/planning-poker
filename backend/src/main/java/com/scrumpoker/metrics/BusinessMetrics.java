package com.scrumpoker.metrics;

import com.scrumpoker.api.websocket.ConnectionRegistry;
import com.scrumpoker.domain.billing.EntityType;
import com.scrumpoker.domain.billing.SubscriptionStatus;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.repository.SubscriptionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Business metrics registry for Scrum Poker application.
 * <p>
 * This class registers custom business metrics with Micrometer for export to Prometheus.
 * It includes metrics for tracking:
 * - Active WebSocket connections and rooms
 * - Vote and round completion events
 * - Active subscriptions by tier
 * - Monthly Recurring Revenue (MRR)
 * </p>
 * <p>
 * Metrics are registered on application startup and exposed via the /q/metrics endpoint.
 * </p>
 */
@ApplicationScoped
public class BusinessMetrics {

    @Inject
    MeterRegistry registry;

    @Inject
    ConnectionRegistry connectionRegistry;

    @Inject
    SubscriptionRepository subscriptionRepository;

    /**
     * Map to store vote counters by deck type.
     * Key: deck type (e.g., "fibonacci", "t-shirt", "modified-fibonacci")
     * Value: Counter instance
     */
    private final Map<String, Counter> voteCounters = new ConcurrentHashMap<>();

    /**
     * Map to store round completion counters by consensus status.
     * Key: consensus status ("true" or "false")
     * Value: Counter instance
     */
    private final Map<String, Counter> roundCounters = new ConcurrentHashMap<>();

    /**
     * Cached subscription counts by tier.
     * Updated periodically by scheduled task to avoid blocking database queries on every scrape.
     */
    private final Map<SubscriptionTier, AtomicLong> subscriptionCounts = new ConcurrentHashMap<>();

    /**
     * Cached Monthly Recurring Revenue (MRR) in cents.
     * Updated periodically by scheduled task to avoid blocking database queries on every scrape.
     */
    private final AtomicReference<Double> cachedMRR = new AtomicReference<>(0.0);

    /**
     * Initializes all business metrics on application startup.
     * Registers gauges with lambda suppliers that provide real-time values.
     *
     * @param event Quarkus startup event
     */
    void onStartup(@Observes StartupEvent event) {
        Log.info("Initializing business metrics");

        // Initialize cached subscription counts for each tier
        for (SubscriptionTier tier : SubscriptionTier.values()) {
            subscriptionCounts.put(tier, new AtomicLong(0L));
        }

        // Note: Don't trigger initial update here - wait for first scheduled execution
        // The scheduled method will run shortly after startup when Vertx context is available

        // Register active sessions gauge
        Gauge.builder("scrumpoker_active_sessions_total", connectionRegistry,
                ConnectionRegistry::getActiveRoomCount)
                .description("Current number of active rooms with connections")
                .register(registry);

        // Register WebSocket connections gauge
        Gauge.builder("scrumpoker_websocket_connections_total", connectionRegistry,
                ConnectionRegistry::getTotalConnectionCount)
                .description("Total active WebSocket connections across all rooms")
                .register(registry);

        // Register subscription gauges for each tier (reads from cached values)
        for (SubscriptionTier tier : SubscriptionTier.values()) {
            Gauge.builder("scrumpoker_subscriptions_active_total",
                    () -> subscriptionCounts.get(tier).get())
                    .description("Active subscriptions by tier")
                    .tag("tier", tier.name())
                    .register(registry);
        }

        // Register MRR gauge (in cents, reads from cached value)
        Gauge.builder("scrumpoker_revenue_monthly_cents",
                () -> cachedMRR.get())
                .description("Monthly recurring revenue in cents")
                .register(registry);

        Log.info("Business metrics initialized successfully");
    }

    /**
     * Increments the vote counter for a specific deck type.
     * <p>
     * This method should be called from VotingService when a vote is cast.
     * Creates a new counter if one doesn't exist for the given deck type.
     * </p>
     *
     * @param deckType The deck type (e.g., "fibonacci", "t-shirt", "modified-fibonacci")
     */
    public void incrementVotesCast(String deckType) {
        if (deckType == null || deckType.trim().isEmpty()) {
            deckType = "unknown";
        }

        Counter counter = voteCounters.computeIfAbsent(deckType, type ->
                Counter.builder("scrumpoker_votes_cast_total")
                        .description("Cumulative votes cast")
                        .tag("deck_type", type)
                        .register(registry)
        );

        counter.increment();
    }

    /**
     * Increments the round completion counter based on whether consensus was reached.
     * <p>
     * This method should be called from VotingService when a round is revealed.
     * </p>
     *
     * @param consensusReached true if consensus was reached in the round, false otherwise
     */
    public void incrementRoundsCompleted(boolean consensusReached) {
        String consensusKey = String.valueOf(consensusReached);

        Counter counter = roundCounters.computeIfAbsent(consensusKey, key ->
                Counter.builder("scrumpoker_rounds_completed_total")
                        .description("Completed estimation rounds")
                        .tag("consensus_reached", key)
                        .register(registry)
        );

        counter.increment();
    }

    /**
     * Scheduled task that updates subscription metrics periodically.
     * <p>
     * NOTE: This scheduled task is currently disabled due to Hibernate Reactive session context issues.
     * For MVP, subscription metrics will show cached values (initialized to 0).
     * </p>
     * <p>
     * TODO: In production, implement this using a manual endpoint or separate service that can
     * properly manage the reactive session context for periodic updates.
     * </p>
     * <p>
     * Alternative approach: Call updateSubscriptionCounts() from a REST endpoint or from
     * billing service methods after subscription changes.
     * </p>
     */
    // @Scheduled(every = "60s")  // Disabled - see note above
    public void updateSubscriptionMetrics() {
        Log.debug("Updating subscription metrics cache");

        // Update subscription counts for each tier sequentially
        for (SubscriptionTier tier : SubscriptionTier.values()) {
            try {
                // Use fire-and-forget reactive pattern
                subscriptionRepository.count(
                        "tier = ?1 and (status = ?2 or status = ?3) and entityType = ?4",
                        tier, SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING, EntityType.USER
                )
                .subscribe().with(
                    count -> {
                        if (count != null) {
                            subscriptionCounts.get(tier).set(count);
                            Log.debugf("Updated subscription count for %s: %d", tier, count);
                            // Update MRR after all counts are updated
                            calculateAndUpdateMRR();
                        }
                    },
                    failure -> Log.warnf(failure, "Failed to update subscription count for tier %s", tier)
                );
            } catch (Exception e) {
                Log.warnf(e, "Exception while scheduling subscription count update for tier %s", tier);
            }
        }
    }

    /**
     * Calculates Monthly Recurring Revenue (MRR) asynchronously and updates the cached value.
     * <p>
     * This method sums up the expected monthly revenue from all active subscriptions
     * based on their tier pricing. The calculation uses standard tier pricing:
     * - PRO: $20/month (2000 cents)
     * - PRO_PLUS: $50/month (5000 cents)
     * - ENTERPRISE: $200/month (20000 cents)
     * - FREE: $0/month (0 cents)
     * </p>
     * <p>
     * Note: This is a simplified calculation. In production, you might want to
     * query actual Stripe pricing or store pricing in the database.
     * </p>
     */
    private void calculateAndUpdateMRR() {
        // Use cached subscription counts to calculate MRR (avoids additional database queries)
        double mrr = 0.0;

        for (SubscriptionTier tier : SubscriptionTier.values()) {
            if (tier == SubscriptionTier.FREE) {
                continue; // Skip FREE tier
            }

            long count = subscriptionCounts.get(tier).get();
            if (count > 0) {
                double priceInCents = getTierPriceInCents(tier);
                mrr += count * priceInCents;
            }
        }

        cachedMRR.set(mrr);
        Log.debugf("Updated MRR: $%.2f", mrr / 100.0);
    }

    /**
     * Gets the monthly price in cents for a subscription tier.
     * <p>
     * This is a helper method that maps tier enums to their pricing.
     * Pricing is based on the standard tiers defined in the architecture.
     * </p>
     *
     * @param tier The subscription tier
     * @return Price in cents per month
     */
    private double getTierPriceInCents(SubscriptionTier tier) {
        return switch (tier) {
            case PRO -> 2000.0; // $20/month
            case PRO_PLUS -> 5000.0; // $50/month
            case ENTERPRISE -> 20000.0; // $200/month
            case FREE -> 0.0;
        };
    }
}
