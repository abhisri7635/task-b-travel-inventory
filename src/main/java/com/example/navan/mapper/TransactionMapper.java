package com.example.navan.mapper;

import com.example.navan.dto.FeeLineDto;
import com.example.navan.dto.TaxLineDto;
import com.example.navan.dto.TransactionRequest;
import com.example.navan.dto.TransactionResponse;
import com.example.navan.entity.FeeLineEntity;
import com.example.navan.entity.TaxLineEntity;
import com.example.navan.entity.TransactionEntity;
import com.example.navan.metadata.MetadataValidator;
import com.example.navan.util.IdGenerator;
import com.example.navan.util.Money;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TransactionMapper {

    private final MetadataValidator metadataValidator;

    public TransactionMapper(MetadataValidator metadataValidator) {
        this.metadataValidator = metadataValidator;
    }

    public TransactionEntity toEntity(TransactionRequest req, String metadataJson) {
        String now = Instant.now().toString();

        TransactionEntity entity = new TransactionEntity();
        entity.setId(IdGenerator.transactionId());
        entity.setExternalId(req.externalId());
        entity.setType(req.type().toLowerCase());
        entity.setCurrency(req.currency());
        entity.setOccurredAt(req.occurredAt());
        entity.setTotalMinor(Money.toLong(req.total()));
        entity.setMetadataJson(metadataJson);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        List<TaxLineEntity> taxLines = req.taxLines().stream()
                .map(dto -> toTaxLineEntity(dto, entity))
                .collect(Collectors.toList());
        entity.setTaxLines(taxLines);

        List<FeeLineEntity> feeLines = req.feeLines().stream()
                .map(dto -> toFeeLineEntity(dto, entity))
                .collect(Collectors.toList());
        entity.setFeeLines(feeLines);

        return entity;
    }

    public TransactionResponse toResponse(TransactionEntity entity) {
        Object metadata = metadataValidator.deserializeMetadata(entity.getType(), entity.getMetadataJson());

        List<TaxLineDto> taxLines = entity.getTaxLines().stream()
                .map(t -> new TaxLineDto(
                        t.getName(),
                        t.getRate() != null ? new java.math.BigDecimal(t.getRate()) : null,
                        Money.fromLong(t.getAmountMinor())))
                .collect(Collectors.toList());

        List<FeeLineDto> feeLines = entity.getFeeLines().stream()
                .map(f -> new FeeLineDto(f.getName(), Money.fromLong(f.getAmountMinor())))
                .collect(Collectors.toList());

        return new TransactionResponse(
                entity.getId(),
                entity.getType(),
                entity.getExternalId(),
                entity.getCurrency(),
                entity.getOccurredAt(),
                Money.fromLong(entity.getTotalMinor()),
                taxLines,
                feeLines,
                metadata,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private TaxLineEntity toTaxLineEntity(TaxLineDto dto, TransactionEntity parent) {
        TaxLineEntity e = new TaxLineEntity();
        e.setTransaction(parent);
        e.setName(dto.name());
        e.setRate(dto.rate() != null ? dto.rate().toPlainString() : null);
        e.setAmountMinor(Money.toLong(dto.amount()));
        return e;
    }

    private FeeLineEntity toFeeLineEntity(FeeLineDto dto, TransactionEntity parent) {
        FeeLineEntity e = new FeeLineEntity();
        e.setTransaction(parent);
        e.setName(dto.name());
        e.setAmountMinor(Money.toLong(dto.amount()));
        return e;
    }
}
