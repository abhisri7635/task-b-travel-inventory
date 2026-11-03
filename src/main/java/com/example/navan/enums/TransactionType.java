package com.example.navan.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TransactionType {
    FLIGHT, HOTEL, RAIL, NAVAN_FEE;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static TransactionType fromValue(String value) {
        if (value == null) throw new IllegalArgumentException("type must not be null");
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown transaction type: " + value);
        }
    }
}
