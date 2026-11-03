package com.example.navan.repository;

import com.example.navan.entity.InvoiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, String> {

    @Query(value = "SELECT * FROM invoices ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
           nativeQuery = true)
    List<InvoiceEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Query(value = "SELECT COUNT(*) FROM invoices", nativeQuery = true)
    long countAll();
}
