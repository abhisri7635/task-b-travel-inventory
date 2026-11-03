package com.example.navan.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FeeKind {
    TRIP_FEE, AGENT_CALL_FEE;

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static FeeKind fromValue(String value) {
        if (value == null) throw new IllegalArgumentException("fee_kind must not be null");
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown fee_kind: " + value +
                    ". Allowed: trip_fee, agent_call_fee");
        }
    }
}
