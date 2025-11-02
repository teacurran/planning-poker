/**
 * Stripe integration adapters for subscription billing and payment processing.
 * <p>
 * This package provides a facade over the Stripe Java SDK for managing
 * subscription lifecycle operations: checkout session creation, customer
 * management, subscription retrieval, updates, and cancellations.
 * </p>
 * <p>
 * The integration supports four subscription tiers:
 * </p>
 * <ul>
 * <li><b>FREE</b> - No Stripe subscription (default tier)</li>
 * <li><b>PRO</b> - $10/month subscription</li>
 * <li><b>PRO_PLUS</b> - $30/month subscription</li>
 * <li><b>ENTERPRISE</b> - $100/month subscription</li>
 * </ul>
 * <p>
 * Main components:
 * </p>
 * <ul>
 * <li>{@link com.scrumpoker.integration.stripe.StripeAdapter} -
 *     Main service wrapping Stripe SDK operations</li>
 * <li>{@link com.scrumpoker.integration.stripe.StripeException} -
 *     Custom exception wrapping Stripe API errors</li>
 * <li>{@link com.scrumpoker.integration.stripe.CheckoutSessionResult} -
 *     DTO containing checkout session ID and URL</li>
 * <li>{@link com.scrumpoker.integration.stripe.StripeSubscriptionInfo} -
 *     DTO mapping Stripe subscription to domain model</li>
 * </ul>
 * <p>
 * <b>Configuration:</b> The adapter requires the following configuration properties
 * in {@code application.properties}:
 * </p>
 * <ul>
 * <li>{@code stripe.api-key} - Stripe secret API key (sk_test_... or sk_live_...)</li>
 * <li>{@code stripe.webhook-secret} - Webhook signing secret for event verification</li>
 * <li>{@code stripe.price.pro} - Stripe Price ID for PRO tier</li>
 * <li>{@code stripe.price.pro-plus} - Stripe Price ID for PRO_PLUS tier</li>
 * <li>{@code stripe.price.enterprise} - Stripe Price ID for ENTERPRISE tier</li>
 * </ul>
 * <p>
 * <b>Usage Example:</b>
 * </p>
 * <pre>{@code
 * @Inject
 * StripeAdapter stripeAdapter;
 *
 * // Create checkout session for upgrade
 * CheckoutSessionResult result = stripeAdapter.createCheckoutSession(
 *     userId,
 *     SubscriptionTier.PRO,
 *     "https://app.example.com/billing/success",
 *     "https://app.example.com/billing/cancel"
 * );
 *
 * // Redirect user to result.checkoutUrl()
 * }</pre>
 * <p>
 * <b>Security Notes:</b>
 * </p>
 * <ul>
 * <li>Use test mode API keys (sk_test_...) for development and staging</li>
 * <li>Never commit real API keys to source control</li>
 * <li>Production API keys must be provided via environment variables</li>
 * <li>Webhook secret is required for verifying webhook event signatures</li>
 * </ul>
 *
 * @since 1.0.0
 * @see com.scrumpoker.integration.stripe.StripeAdapter
 * @see com.scrumpoker.domain.billing.Subscription
 */
package com.scrumpoker.integration.stripe;
