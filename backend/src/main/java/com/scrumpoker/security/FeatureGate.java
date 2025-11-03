package com.scrumpoker.security;

import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Service for enforcing tier-based feature access control.
 * <p>
 * This service provides centralized business logic for checking
 * whether a user's subscription tier grants access to specific
 * features. It implements the feature gating matrix defined in
 * the product specifications:
 * </p>
 * <ul>
 *   <li><strong>Free Tier:</strong> Basic planning poker, public
 *   rooms only</li>
 *   <li><strong>Pro Tier:</strong> Ad removal, advanced reports,
 *   CSV/JSON/PDF export</li>
 *   <li><strong>Pro+ Tier:</strong> Invite-only rooms, enhanced
 *   privacy controls</li>
 *   <li><strong>Enterprise Tier:</strong> Organization
 *   management, SSO, audit logs, org-restricted rooms</li>
 * </ul>
 * <p>
 * <strong>Tier Hierarchy:</strong><br>
 * The tier system is hierarchical:
 * {@code FREE < PRO < PRO_PLUS < ENTERPRISE}<br>
 * Higher tiers inherit all features from lower tiers. For
 * example, Enterprise users can access all Pro and Pro+
 * features.
 * </p>
 * <p>
 * <strong>Usage Example (Imperative Checks):</strong>
 * <pre>
 * {@code @Inject}
 * FeatureGate featureGate;
 *
 * public void createInviteOnlyRoom(User owner) {
 *     featureGate.requireCanCreateInviteOnlyRoom(owner);
 *     // Proceed with room creation
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Usage Example (Boolean Checks):</strong>
 * <pre>
 * if (featureGate.canAccessAdvancedReports(user)) {
 *     return generateDetailedReport();
 * } else {
 *     return generateBasicSummary();
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Integration Points:</strong>
 * <ul>
 *   <li>REST Controllers: Inject FeatureGate for business logic
 *   checks</li>
 *   <li>Domain Services: Use for conditional feature
 *   availability</li>
 *   <li>TierEnforcementInterceptor: Uses FeatureGate for
 *   declarative {@code @RequiresTier} enforcement</li>
 * </ul>
 * </p>
 *
 * @see FeatureNotAvailableException
 * @see RequiresTier
 * @see TierEnforcementInterceptor
 */
@ApplicationScoped
public class FeatureGate {

    /**
     * Checks if a user's subscription tier is sufficient for the
     * required tier.
     * <p>
     * Uses enum ordinal comparison to implement tier hierarchy.
     * Higher ordinal values represent higher tiers.
     * </p>
     *
     * @param user The user whose tier to check
     * @param requiredTier The minimum required tier
     * @return true if user's tier is equal to or higher than
     *         required tier
     */
    public boolean hasSufficientTier(final User user,
            final SubscriptionTier requiredTier) {
        if (user == null || user.subscriptionTier == null) {
            return false;
        }
        return user.subscriptionTier.ordinal()
            >= requiredTier.ordinal();
    }

    /**
     * Checks if a user can create invite-only rooms.
     * <p>
     * <strong>Tier Requirement:</strong> PRO_PLUS or higher
     * </p>
     * <p>
     * Invite-only rooms allow room owners to explicitly whitelist
     * participants, providing enhanced privacy controls for
     * sensitive planning sessions.
     * </p>
     *
     * @param user The user attempting to create an invite-only
     *             room
     * @return true if user has PRO_PLUS or ENTERPRISE tier
     */
    public boolean canCreateInviteOnlyRoom(final User user) {
        return hasSufficientTier(user, SubscriptionTier.PRO_PLUS);
    }

    /**
     * Requires that a user can create invite-only rooms, throwing
     * exception if not.
     * <p>
     * This is a convenience method for imperative enforcement. Use
     * this when you want to immediately fail if the user lacks
     * sufficient tier.
     * </p>
     *
     * @param user The user attempting to create an invite-only
     *             room
     * @throws FeatureNotAvailableException if user's tier is below
     *                                      PRO_PLUS
     */
    public void requireCanCreateInviteOnlyRoom(final User user) {
        if (!canCreateInviteOnlyRoom(user)) {
            throw new FeatureNotAvailableException(
                SubscriptionTier.PRO_PLUS,
                user.subscriptionTier,
                "Invite-Only Rooms"
            );
        }
    }

    /**
     * Checks if a user can access advanced reports with
     * round-level detail.
     * <p>
     * <strong>Tier Requirement:</strong> PRO or higher
     * </p>
     * <p>
     * Advanced reports include:
     * <ul>
     *   <li>Round-by-round voting breakdown</li>
     *   <li>User consistency metrics</li>
     *   <li>CSV/JSON/PDF export capabilities</li>
     *   <li>Historical session comparisons</li>
     * </ul>
     * Free tier users only see basic session summaries (story
     * count, consensus rate).
     * </p>
     *
     * @param user The user attempting to access advanced reports
     * @return true if user has PRO, PRO_PLUS, or ENTERPRISE tier
     */
    public boolean canAccessAdvancedReports(final User user) {
        return hasSufficientTier(user, SubscriptionTier.PRO);
    }

    /**
     * Requires that a user can access advanced reports, throwing
     * exception if not.
     *
     * @param user The user attempting to access advanced reports
     * @throws FeatureNotAvailableException if user's tier is FREE
     */
    public void requireCanAccessAdvancedReports(final User user) {
        if (!canAccessAdvancedReports(user)) {
            throw new FeatureNotAvailableException(
                SubscriptionTier.PRO,
                user.subscriptionTier,
                "Advanced Reports"
            );
        }
    }

    /**
     * Checks if a user can remove advertisements from the UI.
     * <p>
     * <strong>Tier Requirement:</strong> PRO or higher
     * </p>
     * <p>
     * Free tier users see non-intrusive banner ads to support
     * platform maintenance. Pro and higher tiers enjoy an ad-free
     * experience.
     * </p>
     *
     * @param user The user whose ad-free status to check
     * @return true if user has PRO, PRO_PLUS, or ENTERPRISE tier
     */
    public boolean canRemoveAds(final User user) {
        return hasSufficientTier(user, SubscriptionTier.PRO);
    }

    /**
     * Requires that a user can remove ads, throwing exception if
     * not.
     *
     * @param user The user attempting to access ad-free features
     * @throws FeatureNotAvailableException if user's tier is FREE
     */
    public void requireCanRemoveAds(final User user) {
        if (!canRemoveAds(user)) {
            throw new FeatureNotAvailableException(
                SubscriptionTier.PRO,
                user.subscriptionTier,
                "Ad-Free Experience"
            );
        }
    }

    /**
     * Checks if a user can manage organization settings and
     * members.
     * <p>
     * <strong>Tier Requirement:</strong> ENTERPRISE only
     * </p>
     * <p>
     * Organization management includes:
     * <ul>
     *   <li>Creating and configuring organizations</li>
     *   <li>Inviting and removing organization members</li>
     *   <li>Creating organization-restricted rooms</li>
     *   <li>Viewing organization-wide analytics</li>
     *   <li>Configuring SSO and audit log settings</li>
     * </ul>
     * </p>
     *
     * @param user The user attempting to manage organization
     *             features
     * @return true if user has ENTERPRISE tier
     */
    public boolean canManageOrganization(final User user) {
        return hasSufficientTier(user, SubscriptionTier.ENTERPRISE);
    }

    /**
     * Requires that a user can manage organizations, throwing
     * exception if not.
     *
     * @param user The user attempting to manage organization
     *             features
     * @throws FeatureNotAvailableException if user's tier is below
     *                                      ENTERPRISE
     */
    public void requireCanManageOrganization(final User user) {
        if (!canManageOrganization(user)) {
            throw new FeatureNotAvailableException(
                SubscriptionTier.ENTERPRISE,
                user.subscriptionTier,
                "Organization Management"
            );
        }
    }
}
