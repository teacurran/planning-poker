package com.scrumpoker.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS filter that adds security headers to all HTTP responses.
 *
 * Implements OWASP security best practices by adding the following headers:
 * - Strict-Transport-Security (HSTS): Forces HTTPS connections
 * - Content-Security-Policy (CSP): Prevents XSS attacks
 * - X-Frame-Options: Prevents clickjacking
 * - X-Content-Type-Options: Prevents MIME-sniffing
 * - Referrer-Policy: Controls referrer information leakage
 *
 * This filter runs late in the response pipeline (HEADER_DECORATOR priority)
 * to ensure headers are added to all responses regardless of processing path.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class SecurityHeadersFilter implements ContainerResponseFilter {

    // HSTS configuration: 1 year max-age with subdomains
    private static final String HSTS_HEADER = "Strict-Transport-Security";
    private static final String HSTS_VALUE = "max-age=31536000; includeSubDomains; preload";

    // Content Security Policy: Restrict resource loading to prevent XSS
    private static final String CSP_HEADER = "Content-Security-Policy";
    private static final String CSP_VALUE =
        "default-src 'self'; " +
        "script-src 'self' 'unsafe-inline'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: https:; " +
        "connect-src 'self' ws://localhost:8080 wss://app.scrumpoker.com wss://staging.scrumpoker.com; " +
        "font-src 'self'; " +
        "object-src 'none'; " +
        "frame-ancestors 'none';";

    // X-Frame-Options: Prevent clickjacking by denying iframe embedding
    private static final String X_FRAME_OPTIONS_HEADER = "X-Frame-Options";
    private static final String X_FRAME_OPTIONS_VALUE = "DENY";

    // X-Content-Type-Options: Prevent MIME-sniffing attacks
    private static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";
    private static final String X_CONTENT_TYPE_OPTIONS_VALUE = "nosniff";

    // Referrer-Policy: Control referrer information leakage
    private static final String REFERRER_POLICY_HEADER = "Referrer-Policy";
    private static final String REFERRER_POLICY_VALUE = "strict-origin-when-cross-origin";

    // X-XSS-Protection: Legacy XSS protection (still useful for older browsers)
    private static final String X_XSS_PROTECTION_HEADER = "X-XSS-Protection";
    private static final String X_XSS_PROTECTION_VALUE = "1; mode=block";

    // Permissions-Policy: Restrict browser features
    private static final String PERMISSIONS_POLICY_HEADER = "Permissions-Policy";
    private static final String PERMISSIONS_POLICY_VALUE =
        "geolocation=(), microphone=(), camera=(), payment=()";

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();

        // Add HSTS header (HTTP Strict Transport Security)
        // Forces browsers to use HTTPS for all future requests
        headers.add(HSTS_HEADER, HSTS_VALUE);

        // Add CSP header (Content Security Policy)
        // Prevents XSS attacks by restricting resource sources
        headers.add(CSP_HEADER, CSP_VALUE);

        // Add X-Frame-Options header
        // Prevents clickjacking by denying iframe embedding
        headers.add(X_FRAME_OPTIONS_HEADER, X_FRAME_OPTIONS_VALUE);

        // Add X-Content-Type-Options header
        // Prevents browsers from MIME-sniffing responses
        headers.add(X_CONTENT_TYPE_OPTIONS_HEADER, X_CONTENT_TYPE_OPTIONS_VALUE);

        // Add Referrer-Policy header
        // Controls how much referrer information is sent with requests
        headers.add(REFERRER_POLICY_HEADER, REFERRER_POLICY_VALUE);

        // Add X-XSS-Protection header (legacy support)
        // Enables browser's built-in XSS protection
        headers.add(X_XSS_PROTECTION_HEADER, X_XSS_PROTECTION_VALUE);

        // Add Permissions-Policy header
        // Restricts which browser features can be used
        headers.add(PERMISSIONS_POLICY_HEADER, PERMISSIONS_POLICY_VALUE);
    }
}
