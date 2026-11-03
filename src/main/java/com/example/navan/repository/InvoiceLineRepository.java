package com.example.navan.repository;

import com.example.navan.entity.InvoiceLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLineEntity, Long> {

    boolean existsBySourceTransactionId(String sourceTransactionId);
}
