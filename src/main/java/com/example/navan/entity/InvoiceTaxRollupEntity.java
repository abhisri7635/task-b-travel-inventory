package com.example.navan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoice_tax_rollup")
@Getter
@Setter
public class InvoiceTaxRollupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private InvoiceEntity invoice;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "rate")
    private String rate;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;
}
