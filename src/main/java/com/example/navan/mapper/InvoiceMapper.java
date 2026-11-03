package com.example.navan.mapper;

import com.example.navan.dto.FeeLineDto;
import com.example.navan.dto.InvoiceResponse;
import com.example.navan.dto.TaxLineDto;
import com.example.navan.entity.InvoiceEntity;
import com.example.navan.entity.InvoiceFeeRollupEntity;
import com.example.navan.entity.InvoiceLineEntity;
import com.example.navan.entity.InvoiceTaxRollupEntity;
import com.example.navan.metadata.MetadataValidator;
import com.example.navan.service.SnapshotData;
import com.example.navan.util.Money;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class InvoiceMapper {

    private final MetadataValidator metadataValidator;
    private final ObjectMapper objectMapper;

    public InvoiceMapper(MetadataValidator metadataValidator, ObjectMapper objectMapper) {
        this.metadataValidator = metadataValidator;
        this.objectMapper = objectMapper;
    }

    public InvoiceResponse toResponse(InvoiceEntity inv) {
        List<InvoiceResponse.InvoiceLineResponse> lines = inv.getLines().stream()
                .map(this::toLineResponse)
                .collect(Collectors.toList());

        List<TaxLineDto> taxRollup = inv.getTaxRollup().stream()
                .map(r -> new TaxLineDto(
                        r.getName(),
                        r.getRate() != null ? new BigDecimal(r.getRate()) : null,
                        Money.fromLong(r.getAmountMinor())))
                .collect(Collectors.toList());

        List<FeeLineDto> feeRollup = inv.getFeeRollup().stream()
                .map(r -> new FeeLineDto(r.getName(), Money.fromLong(r.getAmountMinor())))
                .collect(Collectors.toList());

        return new InvoiceResponse(
                inv.getId(),
                inv.getCurrency(),
                Money.fromLong(inv.getTotalMinor()),
                Money.fromLong(inv.getTaxTotalMinor()),
                Money.fromLong(inv.getFeeTotalMinor()),
                lines,
                taxRollup,
                feeRollup,
                inv.getCreatedAt(),
                inv.getUpdatedAt()
        );
    }

    private InvoiceResponse.InvoiceLineResponse toLineResponse(InvoiceLineEntity line) {
        SnapshotData snap = deserializeSnapshot(line.getSnapshotJson());
        Object metadata = metadataValidator.deserializeMetadata(line.getType(), snap.metadataJson());

        List<TaxLineDto> taxLines = snap.taxLines().stream()
                .map(t -> new TaxLineDto(
                        t.name(),
                        t.rate() != null ? new BigDecimal(t.rate()) : null,
                        Money.fromLong(t.amountMinor())))
                .collect(Collectors.toList());

        List<FeeLineDto> feeLines = snap.feeLines().stream()
                .map(f -> new FeeLineDto(f.name(), Money.fromLong(f.amountMinor())))
                .collect(Collectors.toList());

        return new InvoiceResponse.InvoiceLineResponse(
                line.getSourceTransactionId(),
                line.getType(),
                line.getCurrency(),
                Money.fromLong(line.getTotalMinor()),
                metadata,
                taxLines,
                feeLines
        );
    }

    private SnapshotData deserializeSnapshot(String json) {
        try {
            return objectMapper.readValue(json, SnapshotData.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize snapshot: " + e.getMessage(), e);
        }
    }
}
