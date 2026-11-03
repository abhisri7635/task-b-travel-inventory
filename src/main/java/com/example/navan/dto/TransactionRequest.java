package com.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record TransactionRequest(
        @NotBlank(message = "type is required") String type,

        @NotBlank(message = "external_id is required")
        @JsonProperty("external_id")
        String externalId,

        @NotBlank(message = "currency is required") String currency,

        @NotBlank(message = "occurred_at is required")
        @JsonProperty("occurred_at")
        String occurredAt,

        @NotNull(message = "total is required") BigDecimal total,

        @NotNull(message = "tax_lines is required")
        @JsonProperty("tax_lines")
        List<TaxLineDto> taxLines,

        @NotNull(message = "fee_lines is required")
        @JsonProperty("fee_lines")
        List<FeeLineDto> feeLines,

        @NotNull(message = "metadata is required") JsonNode metadata
) {}
