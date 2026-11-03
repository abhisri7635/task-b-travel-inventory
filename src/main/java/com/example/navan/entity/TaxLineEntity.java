package com.example.navan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tax_lines")
@Getter
@Setter
public class TaxLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private TransactionEntity transaction;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "rate")
    private String rate;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;
}
