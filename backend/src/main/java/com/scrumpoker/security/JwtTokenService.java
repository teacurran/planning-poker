package com.scrumpoker.security;

import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.auth.principal.DefaultJWTParser;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for JWT access token and refresh token management.
 * <p>
 * This service provides comprehensive JWT token lifecycle management including:
 * <ul>
 *   <li>Token generation (access + refresh tokens)</li>
 *   <li>Token validation (signature, expiration, claims extraction)</li>
 *   <li>Token refresh with rotation (enhanced security)</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Token Specifications:</strong>
 * <ul>
 *   <li><strong>Access Token:</strong> RS256-signed JWT with 1-hour expiration, contains user claims</li>
 *   <li><strong>Refresh Token:</strong> UUID stored in Redis with 30-day TTL</li>
 *   <li><strong>Claims:</strong> sub (userId), email, roles, tier, iss, exp, iat</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Security Features:</strong>
 * <ul>
 *   <li>RSA-256 asymmetric signing (public key for verification, private key for signing)</li>
 *   <li>Refresh token rotation on each refresh (prevents reuse attacks)</li>
 *   <li>Refresh tokens stored in Redis with automatic TTL expiration</li>
 *   <li>Role-based access control (RBAC) via dynamic role mapping from subscription tier</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Production Security Notes:</strong>
 * <ul>
 *   <li>Private keys MUST be loaded from Kubernetes Secrets in production</li>
 *   <li>Never log full token values, only metadata (user ID, expiration)</li>
 *   <li>Tokens should be transmitted over HTTPS only</li>
 *   <li>Consider rotating RSA keys periodically (recommended: every 90 days)</li>
 * </ul>
 * </p>
 *
 * @see TokenPair
 * @see com.scrumpoker.security.JwtClaims
 */
@ApplicationScoped
public class JwtTokenService {

    private static final Logger LOG = Logger.getLogger(JwtTokenService.class);

    /**
     * Redis key prefix for refresh tokens.
     * Format: "refresh_token:{uuid}" -> userId
     */
    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh_token:";

    @Inject
    RedisDataSource redisDataSource;

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "mp.jwt.token.expiration")
    Long accessTokenExpirationSeconds;

    @ConfigProperty(name = "mp.jwt.refresh.token.expiration")
    Long refreshTokenExpirationSeconds;

    /**
     * Generates a new pair of access token and refresh token for the given user.
     * <p>
     * The access token is a signed JWT containing user claims (userId, email, roles, tier)
     * with a 1-hour expiration. The refresh token is a UUID stored in Redis with a 30-day TTL.
     * </p>
     * <p>
     * <strong>Process:</strong>
     * <ol>
     *   <li>Map user's subscription tier to roles array</li>
     *   <li>Generate JWT access token with claims (sub, email, roles, tier, iss, exp, iat)</li>
     *   <li>Sign token with RSA private key (RS256 algorithm)</li>
     *   <li>Generate UUID for refresh token</li>
     *   <li>Store refresh token in Redis with userId as value and 30-day TTL</li>
     *   <li>Return TokenPair containing both tokens</li>
     * </ol>
     * </p>
     *
     * @param user The user for whom to generate tokens (must not be null)
     * @return Uni containing TokenPair with access token and refresh token
     * @throws IllegalArgumentException if user is null
     */
    public Uni<TokenPair> generateTokens(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        LOG.infof("Generating token pair for user: %s (tier: %s)",
                  user.userId, user.subscriptionTier);

        try {
            // Map subscription tier to roles
            List<String> roles = mapTierToRoles(user.subscriptionTier);

            // Calculate expiration times
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(accessTokenExpirationSeconds);

            // Build and sign JWT access token
            String accessToken = Jwt.issuer(issuer)
                .subject(user.userId.toString())
                .claim("email", user.email)
                .claim("roles", roles)
                .claim("tier", user.subscriptionTier.name())
                .expiresAt(expiresAt.getEpochSecond())
                .issuedAt(now.getEpochSecond())
                .sign();

            // Generate refresh token (UUID)
            String refreshToken = UUID.randomUUID().toString();

            // Store refresh token in Redis with TTL
            return storeRefreshToken(refreshToken, user.userId)
                .map(ignored -> {
                    LOG.infof("Token pair generated successfully for user: %s (expires at: %s)",
                              user.userId, expiresAt);
                    return new TokenPair(accessToken, refreshToken);
                })
                .onFailure().invoke(throwable ->
                    LOG.errorf(throwable, "Failed to store refresh token in Redis for user: %s",
                               user.userId)
                );

        } catch (Exception e) {
            LOG.errorf(e, "Failed to generate tokens for user: %s", user.userId);
            return Uni.createFrom().failure(
                new RuntimeException("Failed to generate tokens: " + e.getMessage(), e)
            );
        }
    }

    /**
     * Validates a JWT access token and extracts claims.
     * <p>
     * Performs comprehensive validation including:
     * <ul>
     *   <li>Signature verification using RSA public key</li>
     *   <li>Expiration check</li>
     *   <li>Issuer verification</li>
     *   <li>Claims extraction and validation</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Note:</strong> This method uses SmallRye JWT's DefaultJWTParser which automatically
     * validates the signature using the RSA public key configured in application.properties
     * (mp.jwt.verify.publickey.location). The parser also verifies expiration and issuer claims.
     * </p>
     *
     * @param token The JWT access token to validate (must not be null or blank)
     * @return Uni containing JwtClaims with extracted user claims (userId, email, roles, tier)
     * @throws IllegalArgumentException if token is null or blank
     * @throws RuntimeException if token is invalid, expired, or signature verification fails
     */
    public Uni<com.scrumpoker.security.JwtClaims> validateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or blank");
        }

        LOG.debugf("Validating access token (first 10 chars: %s...)",
                   token.substring(0, Math.min(10, token.length())));

        return Uni.createFrom().item(() -> {
            try {
                // Use SmallRye JWT parser which automatically validates signature using public key
                // from mp.jwt.verify.publickey.location config property
                DefaultJWTParser parser = new DefaultJWTParser();
                JsonWebToken jwt = parser.parse(token);

                // Verify issuer matches expected value
                if (!issuer.equals(jwt.getIssuer())) {
                    throw new RuntimeException("Invalid token issuer: expected '" + issuer
                        + "' but got '" + jwt.getIssuer() + "'");
                }

                // Extract claims
                UUID userId = UUID.fromString(jwt.getSubject());
                String email = jwt.getClaim("email");
                @SuppressWarnings("unchecked")
                List<String> roles = jwt.getClaim("roles");
                String tier = jwt.getClaim("tier");

                LOG.infof("Access token validated successfully for user: %s", userId);

                return new com.scrumpoker.security.JwtClaims(userId, email, roles, tier);

            } catch (Exception e) {
                LOG.errorf(e, "Token validation failed: %s", e.getMessage());
                throw new RuntimeException("Invalid or expired token: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Refreshes an access token using a valid refresh token.
     * <p>
     * Implements token rotation for enhanced security:
     * <ol>
     *   <li>Validates refresh token exists in Redis</li>
     *   <li>Retrieves associated userId</li>
     *   <li>Generates new access token with fresh expiration</li>
     *   <li>Generates new refresh token (rotation)</li>
     *   <li>Invalidates old refresh token in Redis</li>
     *   <li>Stores new refresh token in Redis with full TTL</li>
     *   <li>Returns new TokenPair</li>
     * </ol>
     * </p>
     * <p>
     * <strong>Security Note:</strong> Token rotation prevents refresh token reuse attacks.
     * Once a refresh token is used, it is immediately invalidated and a new one is issued.
     * </p>
     *
     * @param refreshToken The refresh token to use (must not be null or blank)
     * @param user         The user for whom to refresh tokens (must not be null, must match refresh token)
     * @return Uni containing new TokenPair with rotated access and refresh tokens
     * @throws IllegalArgumentException if refreshToken or user is null/blank
     * @throws RuntimeException         if refresh token is invalid or expired
     */
    public Uni<TokenPair> refreshTokens(String refreshToken, User user) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token cannot be null or blank");
        }
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        LOG.infof("Refreshing tokens using refresh token for user: %s", user.userId);

        return validateRefreshToken(refreshToken, user.userId)
            .chain(isValid -> {
                if (!isValid) {
                    LOG.errorf("Invalid or expired refresh token for user: %s", user.userId);
                    return Uni.createFrom().failure(
                        new RuntimeException("Invalid or expired refresh token")
                    );
                }

                // Generate new token pair
                return generateTokens(user)
                    .chain(newTokenPair -> {
                        // Invalidate old refresh token (rotation)
                        return invalidateRefreshToken(refreshToken)
                            .map(ignored -> {
                                LOG.infof("Tokens refreshed successfully for user: %s (old token rotated)",
                                          user.userId);
                                return newTokenPair;
                            });
                    });
            })
            .onFailure().invoke(throwable ->
                LOG.errorf(throwable, "Failed to refresh tokens for user: %s", user.userId)
            );
    }

    /**
     * Maps a subscription tier to a list of role names for RBAC.
     * <p>
     * Role mapping:
     * <ul>
     *   <li>FREE → ["USER"]</li>
     *   <li>PRO → ["USER", "PRO_USER"]</li>
     *   <li>PRO_PLUS → ["USER", "PRO_USER"]</li>
     *   <li>ENTERPRISE → ["USER", "PRO_USER", "ORG_MEMBER"]</li>
     * </ul>
     * </p>
     *
     * @param tier The subscription tier (must not be null)
     * @return List of role names for authorization
     */
    private List<String> mapTierToRoles(SubscriptionTier tier) {
        List<String> roles = new ArrayList<>();
        roles.add("USER");

        switch (tier) {
            case PRO:
            case PRO_PLUS:
                roles.add("PRO_USER");
                break;
            case ENTERPRISE:
                roles.add("PRO_USER");
                roles.add("ORG_MEMBER");
                break;
            case FREE:
            default:
                // FREE tier only has USER role
                break;
        }

        return roles;
    }

    /**
     * Stores a refresh token in Redis with the user ID as value and 30-day TTL.
     *
     * @param refreshToken The refresh token (UUID)
     * @param userId       The user ID to associate with the refresh token
     * @return Uni<Void> that completes when the token is stored
     */
    private Uni<Void> storeRefreshToken(String refreshToken, UUID userId) {
        String key = REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        ValueCommands<String, String> commands = redisDataSource.value(String.class);

        return Uni.createFrom().item(() -> {
            commands.setex(key, refreshTokenExpirationSeconds, userId.toString());
            LOG.debugf("Refresh token stored in Redis: %s... -> user %s (TTL: %d seconds)",
                       refreshToken.substring(0, 8), userId, refreshTokenExpirationSeconds);
            return null;
        });
    }

    /**
     * Validates a refresh token by checking if it exists in Redis and belongs to the user.
     *
     * @param refreshToken The refresh token to validate
     * @param userId       The expected user ID
     * @return Uni<Boolean> true if valid, false otherwise
     */
    private Uni<Boolean> validateRefreshToken(String refreshToken, UUID userId) {
        String key = REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        ValueCommands<String, String> commands = redisDataSource.value(String.class);

        return Uni.createFrom().item(() -> {
            String storedUserId = commands.get(key);
            if (storedUserId == null) {
                LOG.debugf("Refresh token not found in Redis: %s...",
                           refreshToken.substring(0, 8));
                return false;
            }
            boolean matches = storedUserId.equals(userId.toString());
            LOG.debugf("Refresh token validation: %s... -> %s (expected: %s)",
                       refreshToken.substring(0, 8), storedUserId, userId);
            return matches;
        });
    }

    /**
     * Invalidates a refresh token by deleting it from Redis.
     *
     * @param refreshToken The refresh token to invalidate
     * @return Uni<Void> that completes when the token is deleted
     */
    private Uni<Void> invalidateRefreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        ValueCommands<String, String> commands = redisDataSource.value(String.class);

        return Uni.createFrom().item(() -> {
            commands.getdel(key);
            LOG.debugf("Refresh token invalidated in Redis: %s...",
                       refreshToken.substring(0, 8));
            return null;
        });
    }
}
