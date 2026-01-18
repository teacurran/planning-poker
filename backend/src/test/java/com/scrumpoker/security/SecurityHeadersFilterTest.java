package com.scrumpoker.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SecurityHeadersFilter.
 *
 * Verifies that all required security headers are added to HTTP responses.
 */
class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;
    private ContainerRequestContext requestContext;
    private ContainerResponseContext responseContext;
    private MultivaluedMap<String, Object> headers;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
        requestContext = Mockito.mock(ContainerRequestContext.class);
        responseContext = Mockito.mock(ContainerResponseContext.class);
        headers = new MultivaluedHashMap<>();

        when(responseContext.getHeaders()).thenReturn(headers);
    }

    @Test
    void shouldAddStrictTransportSecurityHeader() {
        // When
        filter.filter(requestContext, responseContext);

        // Then
        assertTrue(headers.containsKey("Strict-Transport-Security"));
        assertEquals("max-age=31536000; includeSubDomains; preload",
            headers.getFirst("Strict-Transport-Security"));
    }

    @Test
    void shouldAddContentSecurityPolicyHeader() {
        // When
        filter.filter(requestContext, responseContext);

        // Then
        assertTrue(headers.containsKey("Content-Security-Policy"));
        String csp = (String) headers.getFirst("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'self'"));
        assertTrue(csp.contains("script-src 'self' 'unsafe-inline'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
    }

    @Test
    void shouldAddXFrameOptionsHeader() {
        // When
        filter.filter(requestContext, responseContext);

        // Then
        assertTrue(headers.containsKey("X-Frame-Options"));
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
    }

    @Test
    void shouldAddXContentTypeOptionsHeader() {
        // When
        filter.filter(requestContext, responseContext);

        // Then
        assertTrue(headers.containsKey("X-Content-Type-Options"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
    }

    @Test
    void shouldAddReferrerPolicyHeader() {
        // When
        filter.filter(requestContext, responseContext);

        // Then
        assertTrue(headers.containsKey("Referrer-Policy"));
        assertEquals("strict-origin-when-cross-origin",
            headers.getFirst("Referrer-Policy"));
    }

    @Test
    void shouldAddXXSSProtectionHeader() {
        // When
        filter.filter(requestContext, responseContext);

        // Then
        assertTrue(headers.containsKey("X-XSS-Protection"));
        assertEquals("1; mode=block", headers.getFirst("X-XSS-Protection"));
    }

    @Test
    void shouldAddPermissionsPolicyHeader() {
        // When
        filter.filter(requestContext, responseContext);

        // Then
        assertTrue(headers.containsKey("Permissions-Policy"));
        String policy = (String) headers.getFirst("Permissions-Policy");
        assertNotNull(policy);
        assertTrue(policy.contains("geolocation=()"));
        assertTrue(policy.contains("microphone=()"));
        assertTrue(policy.contains("camera=()"));
    }

    @Test
    void shouldAddAllSecurityHeaders() {
        // When
        filter.filter(requestContext, responseContext);

        // Then - verify all 7 security headers are present
        assertEquals(7, headers.size(), "Should add 7 security headers");
        assertTrue(headers.containsKey("Strict-Transport-Security"));
        assertTrue(headers.containsKey("Content-Security-Policy"));
        assertTrue(headers.containsKey("X-Frame-Options"));
        assertTrue(headers.containsKey("X-Content-Type-Options"));
        assertTrue(headers.containsKey("Referrer-Policy"));
        assertTrue(headers.containsKey("X-XSS-Protection"));
        assertTrue(headers.containsKey("Permissions-Policy"));
    }

    @Test
    void shouldNotOverrideExistingHeaders() {
        // Given - response already has a custom header
        headers.add("Custom-Header", "custom-value");

        // When
        filter.filter(requestContext, responseContext);

        // Then - custom header should still be present
        assertTrue(headers.containsKey("Custom-Header"));
        assertEquals("custom-value", headers.getFirst("Custom-Header"));
        assertEquals(8, headers.size(), "Should have 7 security headers + 1 custom header");
    }
}
