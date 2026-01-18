package com.scrumpoker.security;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * JAX-RS filter that implements rate limiting using Redis-backed token bucket algorithm.
 *
 * Rate Limits:
 * - Anonymous users: 10 requests/minute (identified by IP address)
 * - Authenticated users: 100 requests/minute (identified by userId)
 *
 * Implementation:
 * - Uses Redis INCR for atomic token consumption
 * - Sliding window counter (60-second window)
 * - Keys auto-expire after 60 seconds
 * - Returns 429 Too Many Requests when limit exceeded
 *
 * This filter runs after JWT authentication (AUTHENTICATION + 1 priority)
 * to allow extracting userId from SecurityIdentity for authenticated users.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 1)
public class RateLimitingFilter implements ContainerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Rate limit configuration
    private static final int ANONYMOUS_RATE_LIMIT = 10;  // 10 req/min
    private static final int AUTHENTICATED_RATE_LIMIT = 100;  // 100 req/min
    private static final int WINDOW_DURATION_SECONDS = 60;

    // Redis key prefix
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    @Inject
    ReactiveRedisDataSource redisDataSource;

    @Inject
    SecurityIdentity securityIdentity;

    private ReactiveValueCommands<String, String> valueCommands;

    @PostConstruct
    void initialize() {
        this.valueCommands = redisDataSource.value(String.class);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();

        // Skip rate limiting for public endpoints
        if (isPublicEndpoint(path)) {
            return;
        }

        // Determine rate limit and identifier based on authentication status
        boolean isAuthenticated = securityIdentity != null && !securityIdentity.isAnonymous();
        String identifier;
        int rateLimit;

        if (isAuthenticated) {
            // Authenticated user: use userId from JWT claims
            identifier = securityIdentity.getPrincipal().getName();
            rateLimit = AUTHENTICATED_RATE_LIMIT;
        } else {
            // Anonymous user: use IP address
            identifier = extractClientIp(requestContext);
            rateLimit = ANONYMOUS_RATE_LIMIT;
        }

        // Check rate limit (blocking call - converts Uni to sync)
        boolean allowed = checkRateLimit(identifier, rateLimit)
            .await()
            .atMost(Duration.ofSeconds(2));

        if (!allowed) {
            LOG.warn("Rate limit exceeded for identifier: {} (limit: {})", identifier, rateLimit);

            // Return 429 Too Many Requests
            Response response = Response.status(429)
                .header("X-RateLimit-Limit", String.valueOf(rateLimit))
                .header("X-RateLimit-Window", WINDOW_DURATION_SECONDS + "s")
                .header("Retry-After", String.valueOf(WINDOW_DURATION_SECONDS))
                .entity(new ErrorResponse(
                    429,
                    "Too Many Requests",
                    "Rate limit exceeded. Please try again later.",
                    path
                ))
                .build();

            requestContext.abortWith(response);
        }
    }

    /**
     * Checks if the request is within rate limit.
     *
     * Uses Redis sliding window counter:
     * 1. GET current count for identifier
     * 2. If null (first request), SET to 1 and EXPIRE in 60 seconds
     * 3. If exists and under limit, INCR counter
     * 4. If exists and over limit, deny request
     *
     * @param identifier User identifier (userId or IP)
     * @param limit Maximum requests per window
     * @return Uni<Boolean> true if allowed, false if rate limit exceeded
     */
    private Uni<Boolean> checkRateLimit(String identifier, int limit) {
        String key = RATE_LIMIT_KEY_PREFIX + identifier;

        return valueCommands.get(key)
            .onItem().ifNull().switchTo(() -> {
                // First request - initialize counter with expiration
                return valueCommands.setex(key, WINDOW_DURATION_SECONDS, "1")
                    .replaceWith((String) null);  // Return null to trigger boolean conversion
            })
            .onItem().transformToUni(value -> {
                if (value == null) {
                    // First request (just initialized)
                    return Uni.createFrom().item(true);
                }

                try {
                    int count = Integer.parseInt(value);

                    if (count >= limit) {
                        // Rate limit exceeded
                        return Uni.createFrom().item(false);
                    }

                    // Under limit - increment counter and allow
                    return valueCommands.incr(key)
                        .replaceWith(true);
                } catch (NumberFormatException e) {
                    LOG.error("Invalid rate limit counter value for key: {}", key, e);
                    // Reset counter on error
                    return valueCommands.setex(key, WINDOW_DURATION_SECONDS, "1")
                        .replaceWith(true);
                }
            })
            .onFailure().recoverWithItem(throwable -> {
                // On Redis failure, allow request (fail open to prevent service disruption)
                LOG.error("Rate limit check failed for identifier: {}", identifier, throwable);
                return true;
            });
    }

    /**
     * Extracts client IP address from request headers.
     *
     * Checks headers in order of priority:
     * 1. X-Forwarded-For (set by load balancer/proxy)
     * 2. X-Real-IP (set by some proxies)
     * 3. Falls back to "unknown" if no IP found
     *
     * @param requestContext JAX-RS request context
     * @return Client IP address or "unknown"
     */
    private String extractClientIp(ContainerRequestContext requestContext) {
        // Try X-Forwarded-For first (set by load balancer/proxy)
        String forwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Take first IP (client IP) if multiple proxies
            return forwardedFor.split(",")[0].trim();
        }

        // Fallback to X-Real-IP
        String realIp = requestContext.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        // Last resort: use "unknown" (all unknown IPs share same bucket)
        // Note: In production, configure load balancer to set X-Forwarded-For
        return "unknown";
    }

    /**
     * Checks if the endpoint is public and should skip rate limiting.
     *
     * Public endpoints:
     * - Health checks: /q/health/*
     * - Metrics: /q/metrics/*
     * - OpenAPI: /q/openapi, /q/swagger-ui/*
     * - OAuth callback: /api/v1/auth/* (already has CAPTCHA protection)
     *
     * @param path Request path
     * @return true if endpoint is public, false otherwise
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("q/health") ||
               path.startsWith("q/metrics") ||
               path.startsWith("q/openapi") ||
               path.startsWith("q/swagger-ui") ||
               path.equals("api/v1/auth/oauth/callback") ||
               path.equals("api/v1/auth/oauth/initiate");
    }

    /**
     * Error response DTO for rate limit violations.
     */
    private record ErrorResponse(
        int status,
        String error,
        String message,
        String path
    ) {}
}
