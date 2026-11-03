package com.example.navan.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RailMetadata {

    private List<Leg> legs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Leg {
        private String origin;
        private String destination;
        private String departure;
        private String arrival;

        @JsonProperty("train_number")
        private String trainNumber;

        @JsonProperty("class")
        private String travelClass;
    }
}
