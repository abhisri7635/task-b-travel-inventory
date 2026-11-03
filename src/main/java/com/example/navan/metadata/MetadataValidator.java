package com.example.navan.metadata;

import com.example.navan.enums.TransactionType;
import com.example.navan.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MetadataValidator {

    private final ObjectMapper objectMapper;

    public MetadataValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validates the raw metadata JsonNode against the expected type-specific POJO.
     * Throws ApiException(400) on unknown type, unknown fee_kind, or missing required fields.
     * Returns the serialized JSON string ready for storage.
     */
    public String validateAndSerialize(String typeStr, JsonNode rawMetadata) {
        TransactionType type = parseType(typeStr);
        Object pojo = deserialize(type, rawMetadata);
        validatePojo(type, pojo);
        return serialize(pojo);
    }

    public Object deserializeMetadata(String typeStr, String metadataJson) {
        TransactionType type = parseType(typeStr);
        try {
            return switch (type) {
                case FLIGHT   -> objectMapper.readValue(metadataJson, FlightMetadata.class);
                case HOTEL    -> objectMapper.readValue(metadataJson, HotelMetadata.class);
                case RAIL     -> objectMapper.readValue(metadataJson, RailMetadata.class);
                case NAVAN_FEE -> objectMapper.readValue(metadataJson, NavanFeeMetadata.class);
            };
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to deserialize metadata: " + e.getMessage());
        }
    }

    private TransactionType parseType(String typeStr) {
        try {
            return TransactionType.fromValue(typeStr);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Object deserialize(TransactionType type, JsonNode rawMetadata) {
        try {
            return switch (type) {
                case FLIGHT    -> objectMapper.treeToValue(rawMetadata, FlightMetadata.class);
                case HOTEL     -> objectMapper.treeToValue(rawMetadata, HotelMetadata.class);
                case RAIL      -> objectMapper.treeToValue(rawMetadata, RailMetadata.class);
                case NAVAN_FEE -> objectMapper.treeToValue(rawMetadata, NavanFeeMetadata.class);
            };
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid metadata for type '" + type.toValue() + "': " + e.getMessage());
        }
    }

    private void validatePojo(TransactionType type, Object pojo) {
        switch (type) {
            case FLIGHT -> {
                FlightMetadata f = (FlightMetadata) pojo;
                if (f.getOrigin() == null || f.getOrigin().isBlank())
                    throw new ApiException(HttpStatus.BAD_REQUEST, "flight metadata must include 'origin'");
                if (f.getDestination() == null || f.getDestination().isBlank())
                    throw new ApiException(HttpStatus.BAD_REQUEST, "flight metadata must include 'destination'");
                if (f.getFare() == null)
                    throw new ApiException(HttpStatus.BAD_REQUEST, "flight metadata must include 'fare'");
                if (f.getCoupons() == null)
                    throw new ApiException(HttpStatus.BAD_REQUEST, "flight metadata must include 'coupons' (empty list is allowed)");
            }
            case HOTEL -> {
                HotelMetadata h = (HotelMetadata) pojo;
                if (h.getName() == null || h.getName().isBlank())
                    throw new ApiException(HttpStatus.BAD_REQUEST, "hotel metadata must include 'name'");
                if (h.getAddress() == null)
                    throw new ApiException(HttpStatus.BAD_REQUEST, "hotel metadata must include 'address'");
                if (h.getAddress().getStreet() == null || h.getAddress().getCity() == null || h.getAddress().getCountry() == null)
                    throw new ApiException(HttpStatus.BAD_REQUEST, "hotel address must include 'street', 'city', 'country'");
            }
            case RAIL -> {
                RailMetadata r = (RailMetadata) pojo;
                if (r.getLegs() == null || r.getLegs().isEmpty())
                    throw new ApiException(HttpStatus.BAD_REQUEST, "rail metadata must include at least one leg");
            }
            case NAVAN_FEE -> {
                NavanFeeMetadata n = (NavanFeeMetadata) pojo;
                if (n.getFeeKind() == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "navan_fee metadata must include 'fee_kind' (trip_fee or agent_call_fee)");
                }
            }
        }
    }

    private String serialize(Object pojo) {
        try {
            return objectMapper.writeValueAsString(pojo);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize metadata");
        }
    }
}
