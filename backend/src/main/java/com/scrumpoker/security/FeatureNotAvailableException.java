package com.scrumpoker.security;

import com.scrumpoker.domain.user.SubscriptionTier;

/**
 * Exception thrown when a user attempts to access a feature that is not available
 * in their current subscription tier.
 * <p>
 * This exception is used to enforce tier-based feature gating throughout the application.
 * When thrown, it should be mapped to HTTP 403 Forbidden responses with an upgrade
 * call-to-action message to guide users toward purchasing a higher tier subscription.
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

    private final SubscriptionTier requiredTier;
    private final SubscriptionTier currentTier;
    private final String featureName;

    /**
     * Creates a new FeatureNotAvailableException with tier and feature context.
     *
     * @param requiredTier The minimum subscription tier required to access the feature
     * @param currentTier The user's current subscription tier
     * @param featureName The name of the feature being accessed (for error message clarity)
     */
    public FeatureNotAvailableException(SubscriptionTier requiredTier,
                                       SubscriptionTier currentTier,
                                       String featureName) {
        super(buildMessage(requiredTier, currentTier, featureName));
        this.requiredTier = requiredTier;
        this.currentTier = currentTier;
        this.featureName = featureName;
    }

    /**
     * Creates a new FeatureNotAvailableException with tier, feature context, and cause.
     *
     * @param requiredTier The minimum subscription tier required to access the feature
     * @param currentTier The user's current subscription tier
     * @param featureName The name of the feature being accessed
     * @param cause The underlying cause of the exception
     */
    public FeatureNotAvailableException(SubscriptionTier requiredTier,
                                       SubscriptionTier currentTier,
                                       String featureName,
                                       Throwable cause) {
        super(buildMessage(requiredTier, currentTier, featureName), cause);
        this.requiredTier = requiredTier;
        this.currentTier = currentTier;
        this.featureName = featureName;
    }

    /**
     * Builds a user-friendly error message with upgrade call-to-action.
     *
     * @param requiredTier The minimum tier required
     * @param currentTier The user's current tier
     * @param featureName The feature name
     * @return Formatted error message with upgrade CTA
     */
    private static String buildMessage(SubscriptionTier requiredTier,
                                      SubscriptionTier currentTier,
                                      String featureName) {
        return String.format(
            "This feature requires %s tier or higher. Your current tier is %s. Upgrade your subscription to access %s.",
            formatTierName(requiredTier),
            formatTierName(currentTier),
            featureName
        );
    }

    /**
     * Formats subscription tier name for display (e.g., PRO_PLUS -> "Pro+").
     *
     * @param tier The subscription tier to format
     * @return User-friendly tier name
     */
    private static String formatTierName(SubscriptionTier tier) {
        return switch (tier) {
            case FREE -> "Free";
            case PRO -> "Pro";
            case PRO_PLUS -> "Pro+";
            case ENTERPRISE -> "Enterprise";
        };
    }

    public SubscriptionTier getRequiredTier() {
        return requiredTier;
    }

    public SubscriptionTier getCurrentTier() {
        return currentTier;
    }

    public String getFeatureName() {
        return featureName;
    }
}
