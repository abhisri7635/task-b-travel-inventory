package com.example.navan.service;

import com.example.navan.entity.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure function at the heart of invoicing: given a set of transactions it produces the
 * frozen {@link SnapshotResult} — one snapshot line per transaction plus the tax/fee
 * rollups and the three totals. It performs no I/O and opens no transaction, which is
 * exactly why the money math is isolated here and can be unit-tested on its own.
 *
 * <p>All arithmetic is on {@code long} minor units (no {@code BigDecimal} accumulation,
 * hence no rounding drift). Tax rollup groups by {@code name + rate}; fee rollup groups
 * by {@code name}. Insertion order is preserved via {@link LinkedHashMap} so rollups are
 * stable and reproducible.
 */
@Component
public class SnapshotBuilder {

    private final ObjectMapper objectMapper;

    public SnapshotBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Immutable result of a snapshot build: the lines, both rollups, and the three totals. */
    public record SnapshotResult(
            List<InvoiceLineEntity> lines,
            List<InvoiceTaxRollupEntity> taxRollup,
            List<InvoiceFeeRollupEntity> feeRollup,
            long totalMinor,
            long taxTotalMinor,
            long feeTotalMinor
    ) {}

    public SnapshotResult build(List<TransactionEntity> transactions) {
        List<InvoiceLineEntity> lines = new ArrayList<>();
        long totalMinor = 0;
        long taxTotalMinor = 0;
        long feeTotalMinor = 0;

        // Ordered maps to preserve insertion order for rollup
        Map<String, Long> taxAccum = new LinkedHashMap<>();
        Map<String, String> taxRateByKey = new LinkedHashMap<>();
        Map<String, Long> feeAccum = new LinkedHashMap<>();

        for (TransactionEntity txn : transactions) {
            SnapshotData snapData = buildSnapData(txn);
            String snapshotJson = serialize(snapData);

            InvoiceLineEntity line = new InvoiceLineEntity();
            line.setSourceTransactionId(txn.getId());
            line.setType(txn.getType());
            line.setCurrency(txn.getCurrency());
            line.setTotalMinor(txn.getTotalMinor());
            line.setSnapshotJson(snapshotJson);
            lines.add(line);

            totalMinor += txn.getTotalMinor();

            for (TaxLineEntity tax : txn.getTaxLines()) {
                String key = tax.getName() + "||" + (tax.getRate() != null ? tax.getRate() : "");
                taxAccum.merge(key, tax.getAmountMinor(), Long::sum);
                taxRateByKey.putIfAbsent(key, tax.getRate());
                taxTotalMinor += tax.getAmountMinor();
            }

            for (FeeLineEntity fee : txn.getFeeLines()) {
                feeAccum.merge(fee.getName(), fee.getAmountMinor(), Long::sum);
                feeTotalMinor += fee.getAmountMinor();
            }
        }

        List<InvoiceTaxRollupEntity> taxRollup = taxAccum.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    String[] parts = key.split("\\|\\|", 2);
                    String name = parts[0];
                    InvoiceTaxRollupEntity r = new InvoiceTaxRollupEntity();
                    r.setName(name);
                    r.setRate(taxRateByKey.get(key));
                    r.setAmountMinor(entry.getValue());
                    return r;
                })
                .collect(Collectors.toList());

        List<InvoiceFeeRollupEntity> feeRollup = feeAccum.entrySet().stream()
                .map(entry -> {
                    InvoiceFeeRollupEntity r = new InvoiceFeeRollupEntity();
                    r.setName(entry.getKey());
                    r.setAmountMinor(entry.getValue());
                    return r;
                })
                .collect(Collectors.toList());

        return new SnapshotResult(lines, taxRollup, feeRollup, totalMinor, taxTotalMinor, feeTotalMinor);
    }

    private SnapshotData buildSnapData(TransactionEntity txn) {
        List<SnapshotData.TaxLineSnap> taxLines = txn.getTaxLines().stream()
                .map(t -> new SnapshotData.TaxLineSnap(t.getName(), t.getRate(), t.getAmountMinor()))
                .collect(Collectors.toList());
        List<SnapshotData.FeeLineSnap> feeLines = txn.getFeeLines().stream()
                .map(f -> new SnapshotData.FeeLineSnap(f.getName(), f.getAmountMinor()))
                .collect(Collectors.toList());
        return new SnapshotData(txn.getMetadataJson(), taxLines, feeLines);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize snapshot data", e);
        }
    }
}
