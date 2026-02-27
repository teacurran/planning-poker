package com.scrumpoker.metrics;

import com.scrumpoker.api.websocket.ConnectionRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business metrics registry for Scrum Poker application.
 * <p>
 * Registers custom business metrics with Micrometer for export to Prometheus.
 * Tracks active WebSocket connections, rooms, votes, and round completions.
 * </p>
 */
@ApplicationScoped
public class BusinessMetrics {

    @Inject
    MeterRegistry registry;

    @Inject
    ConnectionRegistry connectionRegistry;

    private final Map<String, Counter> voteCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> roundCounters = new ConcurrentHashMap<>();

    void onStartup(@Observes StartupEvent event) {
        Log.info("Initializing business metrics");

        Gauge.builder("scrumpoker_active_sessions_total", connectionRegistry,
                ConnectionRegistry::getActiveRoomCount)
                .description("Current number of active rooms with connections")
                .register(registry);

        Gauge.builder("scrumpoker_websocket_connections_total", connectionRegistry,
                ConnectionRegistry::getTotalConnectionCount)
                .description("Total active WebSocket connections across all rooms")
                .register(registry);

        Log.info("Business metrics initialized successfully");
    }

    /**
     * Increments the vote counter for a specific deck type.
     *
     * @param deckType The deck type (e.g., "fibonacci", "t-shirt")
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
     *
     * @param consensusReached true if consensus was reached
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
}
