package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.ErrorResponse;
import com.scrumpoker.api.rest.dto.OAuthCallbackRequest;
import com.scrumpoker.api.rest.dto.RefreshTokenRequest;
import com.scrumpoker.api.rest.dto.TokenResponse;
import com.scrumpoker.api.rest.dto.UserDTO;
import com.scrumpoker.api.rest.mapper.UserMapper;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.domain.user.UserService;
import com.scrumpoker.integration.oauth.OAuth2Adapter;
import com.scrumpoker.integration.oauth.OAuthUserInfo;
import com.scrumpoker.security.JwtTokenService;
import com.scrumpoker.security.TokenPair;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * REST controller for authentication endpoints.
 * Handles OAuth2 authentication flow, token refresh, and logout operations.
 * Implements OpenAPI specification from api/openapi.yaml.
 */
@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Authentication",
        description = "OAuth2 authentication and token management endpoints")
public class AuthController {

    private static final Logger LOG = Logger.getLogger(AuthController.class);

    @Inject
    OAuth2Adapter oauth2Adapter;

    @Inject
    UserService userService;

    @Inject
    JwtTokenService jwtTokenService;

    @Inject
    UserMapper userMapper;

    /**
     * POST /api/v1/auth/oauth/callback - Exchange OAuth2 code for JWT tokens.
     * <p>
     * Flow:
     * <ol>
     *   <li>Validate request parameters (code, provider, redirectUri, codeVerifier)</li>
     *   <li>Call OAuth2Adapter to exchange code for user info</li>
     *   <li>Find or create user in database (JIT provisioning)</li>
     *   <li>Generate JWT access token and refresh token</li>
     *   <li>Return TokenResponse with tokens and user profile</li>
     * </ol>
     * </p>
     * <p>
     * Security: Public endpoint (no authentication required).
     * </p>
     *
     * @param request OAuth callback request with code, provider, redirectUri, codeVerifier
     * @return 200 OK with TokenResponse (access token, refresh token, user profile)
     *         or 400 Bad Request if validation fails
     *         or 401 Unauthorized if OAuth authentication fails
     *         or 500 Internal Server Error on unexpected errors
     */
    @POST
    @Path("/oauth/callback")
    @PermitAll
    @Operation(summary = "Exchange OAuth2 authorization code for JWT tokens",
            description = "Exchanges OAuth2 authorization code from provider "
                    + "(Google/Microsoft) for access and refresh tokens. "
                    + "Returns JWT access token (15min expiry) and refresh token (30 days).")
    @APIResponse(responseCode = "200", description = "Successfully authenticated",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "OAuth authentication failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> oauthCallback(@Valid final OAuthCallbackRequest request) {
        LOG.infof("Processing OAuth callback for provider: %s", request.provider);

        // Validate inputs (additional layer beyond bean validation)
        if (request.code == null || request.code.trim().isEmpty()) {
            return createBadRequestResponse("INVALID_CODE",
                    "Authorization code is required");
        }
        if (request.provider == null || request.provider.trim().isEmpty()) {
            return createBadRequestResponse("INVALID_PROVIDER",
                    "Provider is required");
        }
        if (request.redirectUri == null || request.redirectUri.trim().isEmpty()) {
            return createBadRequestResponse("INVALID_REDIRECT_URI",
                    "Redirect URI is required");
        }
        if (request.codeVerifier == null || request.codeVerifier.trim().isEmpty()) {
            return createBadRequestResponse("INVALID_CODE_VERIFIER",
                    "Code verifier is required");
        }

        try {
            // Step 1: Exchange OAuth code for user info
            OAuthUserInfo oauthUserInfo = oauth2Adapter.exchangeCodeForToken(
                    request.provider,
                    request.code,
                    request.codeVerifier,
                    request.redirectUri
            );

            LOG.infof("OAuth token exchange successful for provider: %s, user: %s",
                    request.provider, oauthUserInfo.getEmail());

            // Step 2: Find or create user in database (JIT provisioning)
            return userService.findOrCreateUser(
                            oauthUserInfo.getProvider(),
                            oauthUserInfo.getSubject(),
                            oauthUserInfo.getEmail(),
                            oauthUserInfo.getName(),
                            oauthUserInfo.getAvatarUrl()
                    )
                    .flatMap(user -> {
                        LOG.infof("User provisioned successfully: %s (userId: %s)",
                                user.email, user.userId);

                        // Step 3: Generate JWT tokens
                        return jwtTokenService.generateTokens(user)
                                .map(tokenPair -> {
                                    // Step 4: Build TokenResponse
                                    TokenResponse response = buildTokenResponse(
                                            tokenPair, user);

                                    LOG.infof("Authentication successful for user: %s",
                                            user.userId);

                                    return Response.ok(response).build();
                                });
                    })
                    .onFailure().recoverWithItem(throwable -> {
                        LOG.errorf(throwable,
                                "OAuth callback failed for provider: %s",
                                request.provider);
                        return createErrorResponse(throwable);
                    });

        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid OAuth callback request: %s", e.getMessage());
            return createBadRequestResponse("INVALID_REQUEST", e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during OAuth callback");
            return createInternalServerErrorResponse();
        }
    }

    /**
     * POST /api/v1/auth/refresh - Refresh expired access token.
     * <p>
     * Flow:
     * <ol>
     *   <li>Validate refresh token exists in Redis</li>
     *   <li>Retrieve user ID from Redis</li>
     *   <li>Fetch user entity from database</li>
     *   <li>Generate new access token and refresh token (rotation)</li>
     *   <li>Invalidate old refresh token in Redis</li>
     *   <li>Return TokenResponse with new tokens</li>
     * </ol>
     * </p>
     * <p>
     * Security: Public endpoint (uses refresh token, not access token).
     * Refresh tokens are single-use and rotated on each refresh.
     * </p>
     *
     * @param request Refresh token request with refresh token
     * @return 200 OK with TokenResponse (new access token, new refresh token)
     *         or 401 Unauthorized if refresh token is invalid or expired
     *         or 500 Internal Server Error on unexpected errors
     */
    @POST
    @Path("/refresh")
    @PermitAll
    @Operation(summary = "Refresh expired access token",
            description = "Exchanges refresh token for new access token. "
                    + "Refresh tokens are single-use and rotated on each refresh.")
    @APIResponse(responseCode = "200", description = "Token refreshed successfully",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @APIResponse(responseCode = "401", description = "Invalid or expired refresh token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> refreshToken(@Valid final RefreshTokenRequest request) {
        LOG.debugf("Processing token refresh request");

        // Validate input
        if (request.refreshToken == null || request.refreshToken.trim().isEmpty()) {
            LOG.warnf("Refresh token request missing refresh token");
            return createUnauthorizedResponse("INVALID_REFRESH_TOKEN",
                    "Refresh token is required");
        }

        try {
            // Step 1: Get user ID from refresh token in Redis
            return jwtTokenService.getUserIdFromRefreshToken(request.refreshToken)
                    .onItem().ifNull().failWith(
                            () -> new RuntimeException("Invalid or expired refresh token")
                    )
                    .flatMap(userId -> {
                        LOG.debugf("Refresh token valid for user: %s", userId);

                        // Step 2: Fetch user entity from database
                        return userService.getUserById(userId)
                                .flatMap(user -> {
                                    // Step 3: Generate new tokens (rotates old refresh token)
                                    return jwtTokenService.refreshTokens(
                                                    request.refreshToken, user)
                                            .map(newTokenPair -> {
                                                // Step 4: Build TokenResponse
                                                TokenResponse response =
                                                        buildTokenResponse(
                                                                newTokenPair, user);

                                                LOG.infof(
                                                        "Token refresh successful for user: %s",
                                                        user.userId);

                                                return Response.ok(response).build();
                                            });
                                });
                    })
                    .onFailure().recoverWithItem(throwable -> {
                        LOG.warnf(throwable, "Token refresh failed: %s",
                                throwable.getMessage());
                        return createUnauthorizedResponse("INVALID_REFRESH_TOKEN",
                                "Invalid or expired refresh token").await().indefinitely();
                    });

        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid refresh token request: %s", e.getMessage());
            return createUnauthorizedResponse("INVALID_REFRESH_TOKEN", e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during token refresh");
            return createInternalServerErrorResponse();
        }
    }

    /**
     * POST /api/v1/auth/logout - Revoke refresh token.
     * <p>
     * Flow:
     * <ol>
     *   <li>Validate refresh token parameter</li>
     *   <li>Delete refresh token from Redis</li>
     *   <li>Return 204 No Content</li>
     * </ol>
     * </p>
     * <p>
     * Note: Access token remains valid until expiry (cannot be revoked server-side).
     * Clients should discard both tokens on logout.
     * </p>
     *
     * @param request Refresh token request with refresh token to revoke
     * @return 204 No Content on successful logout
     *         or 401 Unauthorized if refresh token is missing
     *         or 500 Internal Server Error on unexpected errors
     */
    @POST
    @Path("/logout")
    @PermitAll
    @Operation(summary = "Revoke refresh token",
            description = "Revokes the refresh token to prevent future token refreshes. "
                    + "Access token remains valid until expiry.")
    @APIResponse(responseCode = "204", description = "Logout successful")
    @APIResponse(responseCode = "401", description = "Invalid refresh token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> logout(@Valid final RefreshTokenRequest request) {
        LOG.debugf("Processing logout request");

        // Validate input
        if (request.refreshToken == null || request.refreshToken.trim().isEmpty()) {
            LOG.warnf("Logout request missing refresh token");
            return createUnauthorizedResponse("INVALID_REFRESH_TOKEN",
                    "Refresh token is required");
        }

        try {
            // Delete refresh token from Redis
            return jwtTokenService.invalidateRefreshToken(request.refreshToken)
                    .map(ignored -> {
                        LOG.infof("Logout successful - refresh token revoked");
                        return Response.noContent().build();
                    })
                    .onFailure().recoverWithItem(throwable -> {
                        LOG.errorf(throwable, "Logout failed: %s",
                                throwable.getMessage());
                        return createInternalServerErrorResponse()
                                .await().indefinitely();
                    });

        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid logout request: %s", e.getMessage());
            return createUnauthorizedResponse("INVALID_REFRESH_TOKEN", e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during logout");
            return createInternalServerErrorResponse();
        }
    }

    // ===== Private Helper Methods =====

    /**
     * Builds TokenResponse DTO from TokenPair and User entity.
     *
     * @param tokenPair Token pair with access and refresh tokens
     * @param user      User entity
     * @return TokenResponse DTO ready for JSON serialization
     */
    private TokenResponse buildTokenResponse(
            final TokenPair tokenPair, final User user) {
        UserDTO userDTO = userMapper.toDTO(user);
        int expiresIn = jwtTokenService.getAccessTokenExpirationSeconds();

        return new TokenResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                expiresIn,
                userDTO
        );
    }

    /**
     * Creates 400 Bad Request response with error details.
     *
     * @param errorCode    Error code for client reference
     * @param errorMessage Human-readable error message
     * @return Uni containing Response with 400 status
     */
    private Uni<Response> createBadRequestResponse(
            final String errorCode, final String errorMessage) {
        ErrorResponse error = new ErrorResponse(errorCode, errorMessage);
        return Uni.createFrom().item(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(error)
                        .build()
        );
    }

    /**
     * Creates 401 Unauthorized response with error details.
     *
     * @param errorCode    Error code for client reference
     * @param errorMessage Human-readable error message
     * @return Uni containing Response with 401 status
     */
    private Uni<Response> createUnauthorizedResponse(
            final String errorCode, final String errorMessage) {
        ErrorResponse error = new ErrorResponse(errorCode, errorMessage);
        return Uni.createFrom().item(
                Response.status(Response.Status.UNAUTHORIZED)
                        .entity(error)
                        .build()
        );
    }

    /**
     * Creates 500 Internal Server Error response.
     *
     * @return Uni containing Response with 500 status
     */
    private Uni<Response> createInternalServerErrorResponse() {
        ErrorResponse error = new ErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.");
        return Uni.createFrom().item(
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(error)
                        .build()
        );
    }

    /**
     * Creates appropriate error response based on exception type.
     * Maps domain exceptions to HTTP status codes.
     *
     * @param throwable Exception to convert to HTTP response
     * @return Response with appropriate status code and error details
     */
    private Response createErrorResponse(final Throwable throwable) {
        // OAuth2AuthenticationException is handled by exception mapper
        // but we can also handle it here for consistency
        if (throwable instanceof IllegalArgumentException) {
            ErrorResponse error = new ErrorResponse(
                    "INVALID_REQUEST", throwable.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(error)
                    .build();
        }

        // Default to 500 Internal Server Error
        ErrorResponse error = new ErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.");
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(error)
                .build();
    }
}
