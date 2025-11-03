package com.scrumpoker.api.rest.dto;

import java.util.List;

/**
 * Response DTO for paginated invoice list.
 * Maps to OpenAPI InvoiceListResponse schema.
 */
public class InvoiceListResponse {

    /**
     * List of payment history records.
     */
    public List<PaymentHistoryDTO> invoices;

    /**
     * Current page number (0-indexed).
     */
    public int page;

    /**
     * Page size.
     */
    public int size;

    /**
     * Total number of elements across all pages.
     */
    public long totalElements;

    /**
     * Total number of pages.
     */
    public int totalPages;

    /**
     * Default constructor for Jackson deserialization.
     */
    public InvoiceListResponse() {
    }

    /**
     * Constructor with all fields.
     *
     * @param invoices List of payment history DTOs
     * @param page Current page number
     * @param size Page size
     * @param totalElements Total elements
     * @param totalPages Total pages
     */
    public InvoiceListResponse(List<PaymentHistoryDTO> invoices, int page, int size,
                                long totalElements, int totalPages) {
        this.invoices = invoices;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }
}
