package com.example.navan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
public class InvoiceEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "tax_total_minor", nullable = false)
    private long taxTotalMinor;

    @Column(name = "fee_total_minor", nullable = false)
    private long feeTotalMinor;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InvoiceLineEntity> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InvoiceTaxRollupEntity> taxRollup = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InvoiceFeeRollupEntity> feeRollup = new ArrayList<>();
}
