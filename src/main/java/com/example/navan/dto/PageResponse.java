package com.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(
        List<T> items,
        int limit,
        int offset,
        long total,

        @JsonProperty("next_offset")
        Long nextOffset
) {}
