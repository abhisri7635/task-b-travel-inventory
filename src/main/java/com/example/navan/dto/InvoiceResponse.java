package com.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceResponse(
        String id,
        String currency,
        BigDecimal total,

        @JsonProperty("tax_total")
        BigDecimal taxTotal,

        @JsonProperty("fee_total")
        BigDecimal feeTotal,

        List<InvoiceLineResponse> lines,

        @JsonProperty("tax_rollup")
        List<TaxLineDto> taxRollup,

        @JsonProperty("fee_rollup")
        List<FeeLineDto> feeRollup,

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("updated_at")
        String updatedAt
) {
    public record InvoiceLineResponse(
            @JsonProperty("source_transaction_id")
            String sourceTransactionId,

            String type,
            String currency,
            BigDecimal total,
            Object metadata,

            @JsonProperty("tax_lines")
            List<TaxLineDto> taxLines,

            @JsonProperty("fee_lines")
            List<FeeLineDto> feeLines
    ) {}
}
