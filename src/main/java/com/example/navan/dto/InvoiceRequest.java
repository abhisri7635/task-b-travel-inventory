package com.example.navan.dto;

import com.example.navan.exception.ApiException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;

import java.util.List;

public record InvoiceRequest(
        @JsonProperty("transaction_id")
        String transactionId,

        @JsonProperty("transaction_ids")
        List<String> transactionIds
) {
    public List<String> normalizedIds() {
        if (transactionIds != null && !transactionIds.isEmpty()) {
            return transactionIds;
        }
        if (transactionId != null && !transactionId.isBlank()) {
            return List.of(transactionId);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST,
                "Either 'transaction_id' or 'transaction_ids' is required");
    }
}
