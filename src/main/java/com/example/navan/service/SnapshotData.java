package com.example.navan.service;

import java.util.List;

/** Serialized into invoice_lines.snapshot_json — frozen copy of a transaction at invoice time. */
public record SnapshotData(
        String metadataJson,
        List<TaxLineSnap> taxLines,
        List<FeeLineSnap> feeLines
) {
    public record TaxLineSnap(String name, String rate, long amountMinor) {}
    public record FeeLineSnap(String name, long amountMinor) {}
}
