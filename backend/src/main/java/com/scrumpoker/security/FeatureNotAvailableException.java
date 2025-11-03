package com.scrumpoker.security;

import com.scrumpoker.domain.user.SubscriptionTier;

/**
 * Exception thrown when a user attempts to access a feature that
 * is not available in their current subscription tier.
 * <p>
 * This exception is used to enforce tier-based feature gating
 * throughout the application. When thrown, it should be mapped to
 * HTTP 403 Forbidden responses with an upgrade call-to-action
 * message to guide users toward purchasing a higher tier
 * subscription.
 * </p>
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>
 * if (user.subscriptionTier == SubscriptionTier.FREE) {
 *     throw new FeatureNotAvailableException(
 *         SubscriptionTier.PRO,
 *         user.subscriptionTier,
 *         "Advanced Reports"
 *     );
 * }
 * </pre>
 * </p>
 *
 * @see FeatureGate
 * @see FeatureNotAvailableExceptionMapper
 */
public class FeatureNotAvailableException extends RuntimeException {

    /**
     * The minimum subscription tier required for the feature.
     */
    private final SubscriptionTier requiredTier;

    /**
     * The user's current subscription tier.
     */
    private final SubscriptionTier currentTier;

    /**
     * The name of the feature being accessed.
     */
    private final String featureName;

    /**
     * Creates a new FeatureNotAvailableException with tier and
     * feature context.
     *
     * @param required The minimum subscription tier required to
     *                 access the feature
     * @param current The user's current subscription tier
     * @param feature The name of the feature being accessed (for
     *                error message clarity)
     */
    public FeatureNotAvailableException(
            final SubscriptionTier required,
            final SubscriptionTier current,
            final String feature) {
        super(buildMessage(required, current, feature));
        this.requiredTier = required;
        this.currentTier = current;
        this.featureName = feature;
    }

    /**
     * Creates a new FeatureNotAvailableException with tier,
     * feature context, and cause.
     *
     * @param required The minimum subscription tier required to
     *                 access the feature
     * @param current The user's current subscription tier
     * @param feature The name of the feature being accessed
     * @param cause The underlying cause of the exception
     */
    public FeatureNotAvailableException(
            final SubscriptionTier required,
            final SubscriptionTier current,
            final String feature,
            final Throwable cause) {
        super(buildMessage(required, current, feature), cause);
        this.requiredTier = required;
        this.currentTier = current;
        this.featureName = feature;
    }

    /**
     * Builds a user-friendly error message with upgrade
     * call-to-action.
     *
     * @param requiredTier The minimum tier required
     * @param currentTier The user's current tier
     * @param featureName The feature name
     * @return Formatted error message with upgrade CTA
     */
    private static String buildMessage(
            final SubscriptionTier requiredTier,
            final SubscriptionTier currentTier,
            final String featureName) {
        return String.format(
            "This feature requires %s tier or higher. Your current "
                + "tier is %s. Upgrade your subscription to access "
                + "%s.",
            formatTierName(requiredTier),
            formatTierName(currentTier),
            featureName
        );
    }

    /**
     * Formats subscription tier name for display (e.g.,
     * PRO_PLUS -&gt; "Pro+").
     *
     * @param tier The subscription tier to format
     * @return User-friendly tier name
     */
    private static String formatTierName(
            final SubscriptionTier tier) {
        return switch (tier) {
            case FREE -> "Free";
            case PRO -> "Pro";
            case PRO_PLUS -> "Pro+";
            case ENTERPRISE -> "Enterprise";
        };
    }

    /**
     * Gets the minimum subscription tier required for the
     * feature.
     *
     * @return The required tier
     */
    public SubscriptionTier getRequiredTier() {
        return requiredTier;
    }

    /**
     * Gets the user's current subscription tier.
     *
     * @return The current tier
     */
    public SubscriptionTier getCurrentTier() {
        return currentTier;
    }

    /**
     * Gets the name of the feature being accessed.
     *
     * @return The feature name
     */
    public String getFeatureName() {
        return featureName;
    }
}
