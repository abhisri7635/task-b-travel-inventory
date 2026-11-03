package com.example.navan.dto;

import java.math.BigDecimal;

public record FeeLineDto(
        String name,
        BigDecimal amount
) {}
