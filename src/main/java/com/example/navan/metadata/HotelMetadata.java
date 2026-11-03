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
public class HotelMetadata {

    private String name;
    private Address address;

    @JsonProperty("check_in")
    private String checkIn;

    @JsonProperty("check_out")
    private String checkOut;

    private int nights;

    @JsonProperty("room_rate_per_night")
    private BigDecimal roomRatePerNight;

    private List<Extra> extras;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String street;
        private String city;
        private String country;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Extra {
        private String name;
        private BigDecimal amount;
    }
}
