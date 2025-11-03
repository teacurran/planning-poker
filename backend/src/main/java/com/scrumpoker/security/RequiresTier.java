package com.scrumpoker.security;

import com.scrumpoker.domain.user.SubscriptionTier;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for declarative subscription tier enforcement on REST endpoints.
 * <p>
 * This annotation can be applied to JAX-RS resource methods or classes to automatically
 * enforce minimum subscription tier requirements. When applied, the {@link TierEnforcementInterceptor}
 * will validate the authenticated user's subscription tier before allowing the request to proceed.
 * </p>
 * <p>
 * <strong>Class-Level Application:</strong>
 * <pre>
 * {@code @RequiresTier(SubscriptionTier.PRO)}
 * {@code @Path("/api/v1/reports")}
 * public class ReportsResource {
 *     // All methods require PRO tier or higher
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Method-Level Application:</strong>
 * <pre>
 * {@code @GET}
 * {@code @Path("/advanced")}
 * {@code @RequiresTier(SubscriptionTier.PRO_PLUS)}
 * public Response getAdvancedReport() {
 *     // Requires PRO_PLUS or ENTERPRISE tier
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Tier Hierarchy:</strong><br>
 * The tier system is hierarchical: {@code FREE < PRO < PRO_PLUS < ENTERPRISE}<br>
 * When {@code @RequiresTier(SubscriptionTier.PRO)} is specified, users with PRO,
 * PRO_PLUS, or ENTERPRISE tiers will all be granted access.
 * </p>
 * <p>
 * <strong>Error Handling:</strong><br>
 * If the user's tier is insufficient, a {@link FeatureNotAvailableException} is thrown,
 * which is automatically mapped to HTTP 403 Forbidden by {@link FeatureNotAvailableExceptionMapper}
 * with a user-friendly upgrade prompt.
 * </p>
 * <p>
 * <strong>Execution Order:</strong><br>
 * This interceptor runs AFTER {@link JwtAuthenticationFilter} (which populates the security
 * context with user identity), ensuring that tier validation always operates on an authenticated
 * user session.
 * </p>
 *
 * @see TierEnforcementInterceptor
 * @see FeatureGate
 * @see FeatureNotAvailableException
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresTier {

    /**
     * The minimum subscription tier required to access the annotated endpoint.
     * <p>
     * Higher tiers automatically satisfy lower tier requirements due to the tier hierarchy.
     * </p>
     *
     * @return The required subscription tier
     */
    @Nonbinding
    SubscriptionTier value();
}
