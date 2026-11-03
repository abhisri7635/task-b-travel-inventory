package com.example.navan.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlightMetadata {

    private String origin;
    private String destination;
    private BigDecimal fare;
    private List<Coupon> coupons;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coupon {
        private String carrier;

        @JsonProperty("flight_number")
        private String flightNumber;

        private String from;
        private String to;
        private String departure;
        private String arrival;
    }
}
