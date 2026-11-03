package com.example.navan.repository;

import com.example.navan.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    Optional<TransactionEntity> findByExternalId(String externalId);

    @Query(value = "SELECT * FROM transactions ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
           nativeQuery = true)
    List<TransactionEntity> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Query(value = "SELECT COUNT(*) FROM transactions", nativeQuery = true)
    long countAll();
}
