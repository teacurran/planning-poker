package com.scrumpoker.security;

import com.scrumpoker.api.rest.dto.ErrorResponse;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.HashSet;

/**
 * JAX-RS request filter for JWT-based authentication.
 * <p>
 * This filter intercepts all incoming HTTP requests to protected endpoints and performs
 * JWT token validation before allowing the request to proceed. It integrates with Quarkus
 * Security to enable role-based access control via {@code @RolesAllowed} annotations.
 * </p>
 * <p>
 * <strong>Authentication Flow:</strong>
 * <ol>
 *   <li>Check if endpoint is public (auth endpoints, health checks, OPTIONS requests) - skip authentication</li>
 *   <li>Extract JWT token from {@code Authorization: Bearer <token>} header</li>
 *   <li>Validate token signature, expiration, and claims using {@link JwtTokenService}</li>
 *   <li>Extract user claims (userId, email, roles, tier) from validated token</li>
 *   <li>Create {@link SecurityIdentity} with user principal and roles</li>
 *   <li>Set security context in request for downstream authorization checks</li>
 *   <li>Return 401 Unauthorized if token is missing, invalid, or expired</li>
 * </ol>
 * </p>
 * <p>
 * <strong>Public Endpoints (No Authentication Required):</strong>
 * <ul>
 *   <li>{@code /api/v1/auth/*} - OAuth callback, token refresh, logout endpoints</li>
 *   <li>{@code /q/health/*} - Health check endpoints (for Kubernetes probes)</li>
 *   <li>{@code /q/swagger-ui/*} - Swagger UI documentation</li>
 *   <li>{@code /q/openapi} - OpenAPI specification</li>
 *   <li>{@code /q/metrics} - Prometheus metrics endpoint</li>
 *   <li>{@code OPTIONS} requests - CORS preflight requests</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Protected Endpoints:</strong>
 * All other endpoints require a valid JWT access token. The filter will:
 * <ul>
 *   <li>Validate token signature using RSA public key</li>
 *   <li>Verify token has not expired</li>
 *   <li>Extract user roles from token claims</li>
 *   <li>Populate security context for {@code @RolesAllowed} enforcement</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Error Handling:</strong>
 * Returns 401 Unauthorized with {@link ErrorResponse} JSON body when:
 * <ul>
 *   <li>Authorization header is missing</li>
 *   <li>Authorization header does not start with "Bearer "</li>
 *   <li>JWT token is invalid or malformed</li>
 *   <li>JWT token has expired</li>
 *   <li>JWT signature verification fails</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Security Context Integration:</strong>
 * This filter populates the Quarkus security context with a {@link SecurityIdentity} containing:
 * <ul>
 *   <li>Principal: User ID (UUID as string)</li>
 *   <li>Roles: Roles from JWT claims (e.g., "USER", "PRO_USER", "ORG_MEMBER")</li>
 *   <li>Attributes: Full {@link JwtClaims} object for access to email, tier, etc.</li>
 * </ul>
 * This enables controllers to:
 * <ul>
 *   <li>Use {@code @RolesAllowed} annotations for method-level authorization</li>
 *   <li>Inject {@link SecurityIdentity} to access current user information</li>
 *   <li>Retrieve full JWT claims via security identity attributes</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Implementation Notes:</strong>
 * <ul>
 *   <li>Filter priority is {@code AUTHENTICATION} to run before authorization checks</li>
 *   <li>Uses reactive {@link JwtTokenService#validateAccessToken} with blocking await</li>
 *   <li>Blocking is acceptable here since JWT validation is fast (&lt;50ms)</li>
 *   <li>Never logs full token values, only metadata for security</li>
 * </ul>
 * </p>
 *
 * @see JwtTokenService
 * @see JwtClaims
 * @see jakarta.annotation.security.RolesAllowed
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class JwtAuthenticationFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(JwtAuthenticationFilter.class);

    /**
     * Key for storing SecurityIdentity in request context properties.
     * This allows Quarkus Security to retrieve the identity for authorization checks.
     */
    private static final String SECURITY_IDENTITY_KEY = "quarkus.security.identity";

    /**
     * Key for storing JwtClaims in SecurityIdentity attributes.
     * Controllers can retrieve full claims via: identity.getAttribute("jwt.claims")
     */
    private static final String JWT_CLAIMS_ATTRIBUTE = "jwt.claims";

    @Inject
    JwtTokenService jwtTokenService;

    /**
     * Filters incoming HTTP requests to perform JWT authentication.
     * <p>
     * This method is invoked for every HTTP request before it reaches the controller.
     * It checks if the endpoint is public, and if not, validates the JWT token from
     * the Authorization header and sets up the security context.
     * </p>
     *
     * @param requestContext The request context containing headers, URI, method, etc.
     */
    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();

        LOG.debugf("Authentication filter invoked for: %s %s", method, path);

        // Step 1: Check if endpoint is public (skip authentication)
        if (isPublicEndpoint(path, method)) {
            LOG.debugf("Public endpoint detected, skipping authentication: %s %s", method, path);
            return;
        }

        // Step 2: Extract Authorization header
        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            LOG.warnf("Missing Authorization header for protected endpoint: %s %s", method, path);
            abortWithUnauthorized(requestContext, "MISSING_TOKEN",
                "Authorization header is required for protected endpoints");
            return;
        }

        // Step 3: Validate Bearer token format
        if (!authHeader.startsWith("Bearer ")) {
            LOG.warnf("Invalid Authorization header format (expected 'Bearer <token>'): %s",
                authHeader.substring(0, Math.min(20, authHeader.length())));
            abortWithUnauthorized(requestContext, "INVALID_TOKEN_FORMAT",
                "Authorization header must use Bearer token format: 'Bearer <token>'");
            return;
        }

        // Step 4: Extract token from "Bearer <token>" header
        String token = authHeader.substring(7).trim();
        if (token.isBlank()) {
            LOG.warnf("Empty token in Authorization header");
            abortWithUnauthorized(requestContext, "EMPTY_TOKEN",
                "Bearer token cannot be empty");
            return;
        }

        LOG.debugf("Extracted Bearer token (first 10 chars: %s...) for path: %s",
            token.substring(0, Math.min(10, token.length())), path);

        try {
            // Step 5: Validate token using JwtTokenService (reactive with blocking await)
            // Blocking is acceptable here since JWT validation is fast (<50ms)
            // and must complete before request proceeds
            Uni<JwtClaims> claimsUni = jwtTokenService.validateAccessToken(token);
            JwtClaims claims = claimsUni.await().indefinitely();

            LOG.infof("JWT validated successfully for user: %s (roles: %s)",
                claims.userId(), claims.roles());

            // Step 6: Create SecurityIdentity with user principal and roles
            SecurityIdentity securityIdentity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(claims.userId().toString()))
                .addRoles(new HashSet<>(claims.roles()))
                .addAttribute(JWT_CLAIMS_ATTRIBUTE, claims)
                .build();

            // Step 7: Set security context in request for Quarkus Security
            requestContext.setProperty(SECURITY_IDENTITY_KEY, securityIdentity);

            LOG.debugf("Security context populated for user: %s with roles: %s",
                claims.userId(), claims.roles());

        } catch (Exception e) {
            // Token validation failed (invalid signature, expired, malformed, etc.)
            LOG.warnf(e, "JWT validation failed for path %s: %s", path, e.getMessage());

            // Determine appropriate error message based on exception
            String errorMessage = "Invalid or expired authentication token";
            if (e.getMessage() != null && e.getMessage().contains("expired")) {
                errorMessage = "Authentication token has expired";
            } else if (e.getMessage() != null && e.getMessage().contains("signature")) {
                errorMessage = "Invalid token signature";
            }

            abortWithUnauthorized(requestContext, "INVALID_TOKEN", errorMessage);
        }
    }

    /**
     * Checks if the given request path and method correspond to a public endpoint
     * that does not require authentication.
     * <p>
     * Public endpoints include:
     * <ul>
     *   <li>Authentication endpoints: /api/v1/auth/*</li>
     *   <li>Health checks: /q/health/*</li>
     *   <li>Swagger UI: /q/swagger-ui/*</li>
     *   <li>OpenAPI spec: /q/openapi</li>
     *   <li>Metrics: /q/metrics</li>
     *   <li>OPTIONS requests (CORS preflight)</li>
     * </ul>
     * </p>
     *
     * @param path   The request URI path (e.g., "/api/v1/auth/oauth/callback")
     * @param method The HTTP method (e.g., "GET", "POST", "OPTIONS")
     * @return true if the endpoint is public, false if authentication is required
     */
    private boolean isPublicEndpoint(String path, String method) {
        // OPTIONS requests are always allowed (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Authentication endpoints are public
        if (path.startsWith("api/v1/auth/") || path.startsWith("/api/v1/auth/")) {
            return true;
        }

        // Quarkus management endpoints are public
        if (path.startsWith("q/health") || path.startsWith("/q/health")) {
            return true;
        }
        if (path.startsWith("q/swagger-ui") || path.startsWith("/q/swagger-ui")) {
            return true;
        }
        if (path.equals("q/openapi") || path.equals("/q/openapi")) {
            return true;
        }
        if (path.equals("q/metrics") || path.equals("/q/metrics")) {
            return true;
        }

        // All other endpoints require authentication
        return false;
    }

    /**
     * Aborts the request with a 401 Unauthorized response.
     * <p>
     * Constructs a JSON error response using {@link ErrorResponse} with the given
     * error code and message. This method terminates request processing immediately.
     * </p>
     *
     * @param requestContext The request context to abort
     * @param errorCode      Error code for client reference (e.g., "MISSING_TOKEN", "INVALID_TOKEN")
     * @param errorMessage   Human-readable error message
     */
    private void abortWithUnauthorized(ContainerRequestContext requestContext,
                                      String errorCode, String errorMessage) {
        ErrorResponse error = new ErrorResponse(errorCode, errorMessage);
        Response response = Response.status(Response.Status.UNAUTHORIZED)
            .entity(error)
            .type(MediaType.APPLICATION_JSON)
            .build();

        requestContext.abortWith(response);

        LOG.debugf("Request aborted with 401 Unauthorized: %s - %s", errorCode, errorMessage);
    }
}
