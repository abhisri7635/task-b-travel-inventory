package com.example.navan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoice_lines")
@Getter
@Setter
public class InvoiceLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private InvoiceEntity invoice;

    @Column(name = "source_transaction_id", nullable = false)
    private String sourceTransactionId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;
}
