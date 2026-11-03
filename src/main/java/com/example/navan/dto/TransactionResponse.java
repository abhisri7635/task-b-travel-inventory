package com.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record TransactionResponse(
        String id,
        String type,

        @JsonProperty("external_id")
        String externalId,

        String currency,

        @JsonProperty("occurred_at")
        String occurredAt,

        BigDecimal total,

        @JsonProperty("tax_lines")
        List<TaxLineDto> taxLines,

        @JsonProperty("fee_lines")
        List<FeeLineDto> feeLines,

        Object metadata,

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("updated_at")
        String updatedAt
) {}
