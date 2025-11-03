package com.scrumpoker.security;

import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.UserRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * JAX-RS request filter for enforcing subscription tier
 * requirements on REST endpoints.
 * <p>
 * This interceptor automatically validates that authenticated
 * users have sufficient subscription tier to access endpoints
 * annotated with {@link RequiresTier}. It runs AFTER
 * authentication (via {@link JwtAuthenticationFilter}) but
 * BEFORE the target method is invoked.
 * </p>
 * <p>
 * <strong>Execution Flow:</strong>
 * <ol>
 *   <li>Check if target method or class has
 *   {@code @RequiresTier} annotation</li>
 *   <li>If not present, allow request to proceed without
 *   tier check</li>
 *   <li>Extract user ID from {@link SecurityIdentity}
 *   (populated by authentication filter)</li>
 *   <li>Fetch {@link User} entity from database to get current
 *   subscription tier</li>
 *   <li>Compare user's tier against required tier using enum
 *   ordinal hierarchy</li>
 *   <li>If insufficient, throw
 *   {@link FeatureNotAvailableException} (mapped to
 *   403 Forbidden)</li>
 *   <li>If sufficient, allow request to proceed to controller
 *   method</li>
 * </ol>
 * </p>
 * <p>
 * <strong>Annotation Precedence:</strong><br>
 * Method-level {@code @RequiresTier} annotations take
 * precedence over class-level annotations. This allows for
 * fine-grained tier requirements within a controller class.
 * </p>
 * <p>
 * <strong>Example Usage:</strong>
 * <pre>
 * {@code @GET}
 * {@code @Path("/advanced")}
 * {@code @RequiresTier(SubscriptionTier.PRO)}
 * public Response getAdvancedReport() {
 *     // This interceptor ensures user has PRO tier before
 *     // execution
 *     return Response.ok(report).build();
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Error Handling:</strong><br>
 * When tier is insufficient, throws
 * {@link FeatureNotAvailableException} with:
 * <ul>
 *   <li>Required tier from annotation</li>
 *   <li>User's current tier from database</li>
 *   <li>User-friendly upgrade call-to-action message</li>
 * </ul>
 * The exception is automatically mapped to HTTP 403 by
 * {@link FeatureNotAvailableExceptionMapper}.
 * </p>
 * <p>
 * <strong>Public Endpoints:</strong><br>
 * This interceptor only runs on authenticated endpoints.
 * Public endpoints (those handled by
 * {@link JwtAuthenticationFilter}'s exemption list) are never
 * reached by this interceptor.
 * </p>
 *
 * @see RequiresTier
 * @see FeatureGate
 * @see FeatureNotAvailableException
 * @see JwtAuthenticationFilter
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
@RequiresTier(SubscriptionTier.FREE)
public class TierEnforcementInterceptor
    implements ContainerRequestFilter {

    /**
     * Logger for tier enforcement operations.
     */
    private static final Logger LOG =
        Logger.getLogger(TierEnforcementInterceptor.class);

    /**
     * Security identity containing authenticated user info.
     */
    @Inject
    private SecurityIdentity securityIdentity;

    /**
     * Repository for fetching user entities.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * Service for checking tier-based feature access.
     */
    @Inject
    private FeatureGate featureGate;

    /**
     * JAX-RS resource metadata for annotation detection.
     */
    @Context
    private ResourceInfo resourceInfo;

    /**
     * Filters incoming HTTP requests to enforce tier
     * requirements.
     * <p>
     * This method is invoked for every HTTP request after
     * authentication but before the target controller method.
     * It checks for {@code @RequiresTier} annotations and
     * validates the user's subscription tier accordingly.
     * </p>
     *
     * @param requestContext The request context containing
     *                       headers, URI, method, etc.
     * @throws FeatureNotAvailableException if user's tier is
     *                                      insufficient
     */
    @Override
    public void filter(final ContainerRequestContext requestContext) {
        // Get the target method and class
        Method method = resourceInfo.getResourceMethod();
        Class<?> resourceClass = resourceInfo.getResourceClass();

        if (method == null || resourceClass == null) {
            LOG.debugf("No resource method found, skipping tier "
                + "enforcement");
            return;
        }

        // Check for @RequiresTier annotation (method-level takes
        // precedence over class-level)
        RequiresTier methodAnnotation =
            method.getAnnotation(RequiresTier.class);
        RequiresTier classAnnotation =
            resourceClass.getAnnotation(RequiresTier.class);

        RequiresTier requiresTier = methodAnnotation != null
            ? methodAnnotation : classAnnotation;

        // If no @RequiresTier annotation is present, skip tier
        // check
        if (requiresTier == null) {
            LOG.debugf("No @RequiresTier annotation on %s.%s(), "
                    + "skipping tier enforcement",
                resourceClass.getSimpleName(), method.getName());
            return;
        }

        SubscriptionTier requiredTier = requiresTier.value();
        LOG.debugf("Tier enforcement required: %s for %s.%s()",
            requiredTier, resourceClass.getSimpleName(),
            method.getName());

        // Check if user is authenticated
        if (securityIdentity.isAnonymous()) {
            LOG.warnf("Anonymous user attempting to access "
                    + "tier-protected endpoint: %s.%s()",
                resourceClass.getSimpleName(), method.getName());
            throw new FeatureNotAvailableException(
                requiredTier,
                SubscriptionTier.FREE,
                "this feature"
            );
        }

        // Extract user ID from security identity principal
        String userIdStr = securityIdentity.getPrincipal().getName();
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            LOG.errorf("Invalid user ID in security principal: %s",
                userIdStr);
            throw new IllegalStateException("Invalid user ID in "
                + "security context", e);
        }

        LOG.debugf("Checking tier for user: %s", userId);

        // Fetch user from database to get current subscription
        // tier. Using blocking await since this is a synchronous
        // filter
        User user = userRepository.findById(userId)
            .await()
            .indefinitely();

        if (user == null) {
            LOG.errorf("User not found in database: %s (present in "
                + "JWT but missing from DB)", userId);
            throw new IllegalStateException("User not found: "
                + userId);
        }

        LOG.debugf("User %s has tier: %s, required tier: %s",
            userId, user.subscriptionTier, requiredTier);

        // Check if user's tier is sufficient using FeatureGate
        if (!featureGate.hasSufficientTier(user, requiredTier)) {
            LOG.infof("Tier enforcement failed: User %s (tier: %s) "
                    + "attempted to access feature requiring %s",
                userId, user.subscriptionTier, requiredTier);

            throw new FeatureNotAvailableException(
                requiredTier,
                user.subscriptionTier,
                "this feature"
            );
        }

        LOG.debugf("Tier enforcement passed for user %s: has %s, "
                + "required %s",
            userId, user.subscriptionTier, requiredTier);
    }
}
