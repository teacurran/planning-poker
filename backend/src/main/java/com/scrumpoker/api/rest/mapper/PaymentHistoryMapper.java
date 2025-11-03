package com.scrumpoker.api.rest.mapper;

import com.scrumpoker.api.rest.dto.PaymentHistoryDTO;
import com.scrumpoker.domain.billing.PaymentHistory;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapper for converting between PaymentHistory entity and PaymentHistoryDTO.
 * Follows the same pattern as UserMapper for consistency.
 */
@ApplicationScoped
public class PaymentHistoryMapper {

    /**
     * Converts PaymentHistory entity to PaymentHistoryDTO.
     *
     * @param paymentHistory The payment history entity
     * @return PaymentHistoryDTO with all fields mapped
     */
    public PaymentHistoryDTO toDTO(final PaymentHistory paymentHistory) {
        if (paymentHistory == null) {
            return null;
        }

        PaymentHistoryDTO dto = new PaymentHistoryDTO();
        dto.paymentId = paymentHistory.paymentId;
        // Extract subscription ID from the subscription relationship
        dto.subscriptionId = paymentHistory.subscription != null
                ? paymentHistory.subscription.subscriptionId
                : null;
        dto.stripeInvoiceId = paymentHistory.stripeInvoiceId;
        dto.amount = paymentHistory.amount;
        dto.currency = paymentHistory.currency;
        dto.status = paymentHistory.status;
        dto.paidAt = paymentHistory.paidAt;

        return dto;
    }
}
