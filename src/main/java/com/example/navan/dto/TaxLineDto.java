package com.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaxLineDto(
        String name,
        BigDecimal rate,
        BigDecimal amount
) {}
