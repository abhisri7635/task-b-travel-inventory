package com.example.navan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
@Getter
@Setter
public class TransactionEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "external_id", unique = true, nullable = false)
    private String externalId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "occurred_at", nullable = false)
    private String occurredAt;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "metadata_json", nullable = false, columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaxLineEntity> taxLines = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FeeLineEntity> feeLines = new ArrayList<>();
}
