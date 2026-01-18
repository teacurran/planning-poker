package com.scrumpoker.security;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitingFilter.
 *
 * Verifies rate limiting logic for both anonymous and authenticated users.
 */
class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private ReactiveRedisDataSource redisDataSource;
    private ReactiveValueCommands<String, String> valueCommands;
    private SecurityIdentity securityIdentity;
    private ContainerRequestContext requestContext;
    private UriInfo uriInfo;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();

        // Mock Redis
        redisDataSource = Mockito.mock(ReactiveRedisDataSource.class);
        valueCommands = Mockito.mock(ReactiveValueCommands.class);
        when(redisDataSource.value(String.class)).thenReturn(valueCommands);

        // Mock SecurityIdentity
        securityIdentity = Mockito.mock(SecurityIdentity.class);

        // Mock request context
        requestContext = Mockito.mock(ContainerRequestContext.class);
        uriInfo = Mockito.mock(UriInfo.class);
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        // Inject mocks
        filter.redisDataSource = redisDataSource;
        filter.securityIdentity = securityIdentity;
        filter.initialize();
    }

    @Test
    void shouldAllowFirstRequestForNewUser() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/rooms");
        when(securityIdentity.isAnonymous()).thenReturn(true);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("192.168.1.1");

        // Mock Redis - first request (key doesn't exist)
        when(valueCommands.get(anyString())).thenReturn(Uni.createFrom().nullItem());
        when(valueCommands.setex(anyString(), anyLong(), anyString())).thenReturn(Uni.createFrom().voidItem());

        // When
        filter.filter(requestContext);

        // Then
        verify(requestContext, never()).abortWith(any());
        verify(valueCommands).setex(eq("rate_limit:192.168.1.1"), eq(60L), eq("1"));
    }

    @Test
    void shouldAllowRequestUnderRateLimit() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/rooms");
        when(securityIdentity.isAnonymous()).thenReturn(true);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("192.168.1.1");

        // Mock Redis - 5th request (under limit of 10)
        when(valueCommands.get(anyString())).thenReturn(Uni.createFrom().item("5"));
        when(valueCommands.incr(anyString())).thenReturn(Uni.createFrom().item(6L));

        // When
        filter.filter(requestContext);

        // Then
        verify(requestContext, never()).abortWith(any());
        verify(valueCommands).incr(eq("rate_limit:192.168.1.1"));
    }

    @Test
    void shouldBlockRequestOverRateLimit() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/rooms");
        when(securityIdentity.isAnonymous()).thenReturn(true);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("192.168.1.1");

        // Mock Redis - 11th request (over limit of 10)
        when(valueCommands.get(anyString())).thenReturn(Uni.createFrom().item("10"));

        // When
        filter.filter(requestContext);

        // Then
        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());

        Response response = responseCaptor.getValue();
        assertEquals(429, response.getStatus());
        assertEquals("10", response.getHeaderString("X-RateLimit-Limit"));
        assertEquals("60s", response.getHeaderString("X-RateLimit-Window"));
        assertEquals("60", response.getHeaderString("Retry-After"));
    }

    @Test
    void shouldUseHigherLimitForAuthenticatedUsers() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/rooms");
        when(securityIdentity.isAnonymous()).thenReturn(false);

        Principal principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn("user123");
        when(securityIdentity.getPrincipal()).thenReturn(principal);

        // Mock Redis - 50th request (under authenticated limit of 100)
        when(valueCommands.get(anyString())).thenReturn(Uni.createFrom().item("50"));
        when(valueCommands.incr(anyString())).thenReturn(Uni.createFrom().item(51L));

        // When
        filter.filter(requestContext);

        // Then
        verify(requestContext, never()).abortWith(any());
        verify(valueCommands).incr(eq("rate_limit:user123"));
    }

    @Test
    void shouldBlockAuthenticatedUserOverLimit() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/rooms");
        when(securityIdentity.isAnonymous()).thenReturn(false);

        Principal principal = Mockito.mock(Principal.class);
        when(principal.getName()).thenReturn("user123");
        when(securityIdentity.getPrincipal()).thenReturn(principal);

        // Mock Redis - 101st request (over authenticated limit of 100)
        when(valueCommands.get(anyString())).thenReturn(Uni.createFrom().item("100"));

        // When
        filter.filter(requestContext);

        // Then
        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        verify(requestContext).abortWith(responseCaptor.capture());

        Response response = responseCaptor.getValue();
        assertEquals(429, response.getStatus());
        assertEquals("100", response.getHeaderString("X-RateLimit-Limit"));
    }

    @Test
    void shouldSkipRateLimitingForHealthEndpoint() {
        // Given
        when(uriInfo.getPath()).thenReturn("q/health/live");

        // When
        filter.filter(requestContext);

        // Then
        verify(requestContext, never()).abortWith(any());
        verify(valueCommands, never()).get(anyString());
    }

    @Test
    void shouldSkipRateLimitingForMetricsEndpoint() {
        // Given
        when(uriInfo.getPath()).thenReturn("q/metrics");

        // When
        filter.filter(requestContext);

        // Then
        verify(requestContext, never()).abortWith(any());
        verify(valueCommands, never()).get(anyString());
    }

    @Test
    void shouldSkipRateLimitingForOAuthCallback() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/auth/oauth/callback");

        // When
        filter.filter(requestContext);

        // Then
        verify(requestContext, never()).abortWith(any());
        verify(valueCommands, never()).get(anyString());
    }

    @Test
    void shouldExtractIpFromXForwardedForHeader() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/rooms");
        when(securityIdentity.isAnonymous()).thenReturn(true);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("203.0.113.1, 198.51.100.1");

        // Mock Redis
        when(valueCommands.get(anyString())).thenReturn(Uni.createFrom().item("5"));
        when(valueCommands.incr(anyString())).thenReturn(Uni.createFrom().item(6L));

        // When
        filter.filter(requestContext);

        // Then - should use first IP from X-Forwarded-For
        verify(valueCommands).get(eq("rate_limit:203.0.113.1"));
    }

    @Test
    void shouldExtractIpFromXRealIpHeaderIfXForwardedForMissing() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/rooms");
        when(securityIdentity.isAnonymous()).thenReturn(true);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn(null);
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn("203.0.113.1");

        // Mock Redis
        when(valueCommands.get(anyString())).thenReturn(Uni.createFrom().item("5"));
        when(valueCommands.incr(anyString())).thenReturn(Uni.createFrom().item(6L));

        // When
        filter.filter(requestContext);

        // Then
        verify(valueCommands).get(eq("rate_limit:203.0.113.1"));
    }

    @Test
    void shouldUseUnknownIfNoIpHeadersPresent() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/rooms");
        when(securityIdentity.isAnonymous()).thenReturn(true);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn(null);
        when(requestContext.getHeaderString("X-Real-IP")).thenReturn(null);

        // Mock Redis
        when(valueCommands.get(anyString())).thenReturn(Uni.createFrom().item("5"));
        when(valueCommands.incr(anyString())).thenReturn(Uni.createFrom().item(6L));

        // When
        filter.filter(requestContext);

        // Then
        verify(valueCommands).get(eq("rate_limit:unknown"));
    }

    @Test
    void shouldAllowRequestOnRedisFailure() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/rooms");
        when(securityIdentity.isAnonymous()).thenReturn(true);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("192.168.1.1");

        // Mock Redis failure
        when(valueCommands.get(anyString()))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Redis connection failed")));

        // When
        filter.filter(requestContext);

        // Then - should fail open (allow request)
        verify(requestContext, never()).abortWith(any());
    }

    @Test
    void shouldResetCounterOnInvalidRedisValue() {
        // Given
        when(uriInfo.getPath()).thenReturn("api/v1/rooms");
        when(securityIdentity.isAnonymous()).thenReturn(true);
        when(requestContext.getHeaderString("X-Forwarded-For")).thenReturn("192.168.1.1");

        // Mock Redis - invalid counter value
        when(valueCommands.get(anyString())).thenReturn(Uni.createFrom().item("invalid"));
        when(valueCommands.setex(anyString(), anyLong(), anyString())).thenReturn(Uni.createFrom().voidItem());

        // When
        filter.filter(requestContext);

        // Then - should reset counter and allow request
        verify(requestContext, never()).abortWith(any());
        verify(valueCommands).setex(eq("rate_limit:192.168.1.1"), eq(60L), eq("1"));
    }
}
