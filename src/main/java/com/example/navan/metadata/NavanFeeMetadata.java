package com.example.navan.metadata;

import com.example.navan.enums.FeeKind;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NavanFeeMetadata {

    @JsonProperty("fee_kind")
    private FeeKind feeKind;

    private String description;

    @JsonProperty("related_external_id")
    private String relatedExternalId;
}
