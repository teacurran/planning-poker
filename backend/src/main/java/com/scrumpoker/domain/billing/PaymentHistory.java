package com.scrumpoker.domain.billing;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable payment transaction log from Stripe.
 * Records all payment events for audit and billing history.
 */
@Entity
@Table(name = "payment_history", uniqueConstraints = {
    @UniqueConstraint(name = "uq_payment_stripe_invoice", columnNames = "stripe_invoice_id")
})
public class PaymentHistory extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "payment_id")
    public UUID paymentId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payment_subscription"))
    public Subscription subscription;

    @NotNull
    @Size(max = 100)
    @Column(name = "stripe_invoice_id", nullable = false, length = 100)
    public String stripeInvoiceId;

    /**
     * Amount in cents (e.g., 1999 = $19.99).
     */
    @NotNull
    @Column(name = "amount", nullable = false)
    public Integer amount;

    @NotNull
    @Size(max = 3)
    @Column(name = "currency", nullable = false, length = 3)
    public String currency = "USD";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "payment_status_enum")
    public PaymentStatus status;

    @Column(name = "paid_at")
    public Instant paidAt;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
