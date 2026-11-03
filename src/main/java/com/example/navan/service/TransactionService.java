package com.example.navan.service;

import com.example.navan.dto.PageResponse;
import com.example.navan.dto.TransactionRequest;
import com.example.navan.dto.TransactionResponse;
import com.example.navan.entity.FeeLineEntity;
import com.example.navan.entity.TaxLineEntity;
import com.example.navan.entity.TransactionEntity;
import com.example.navan.exception.ApiException;
import com.example.navan.mapper.TransactionMapper;
import com.example.navan.metadata.MetadataValidator;
import com.example.navan.repository.InvoiceLineRepository;
import com.example.navan.repository.TransactionRepository;
import com.example.navan.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic for travel transactions: create (with duplicate-safe identity),
 * read, paginated list, patch, and delete. Enforces the freeze rule — a transaction
 * that is already billed on an invoice cannot be modified or deleted.
 *
 * <p>All money is handled as {@code long} minor units internally (see {@link Money});
 * HTTP/JSON concerns live in the controller, not here.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final MetadataValidator metadataValidator;
    private final TransactionMapper transactionMapper;

    /** Programmatic template used only by {@link #create} to isolate the duplicate-insert path. */
    private final TransactionTemplate transactionTemplate;

    public TransactionService(TransactionRepository transactionRepository,
                               InvoiceLineRepository invoiceLineRepository,
                               MetadataValidator metadataValidator,
                               TransactionMapper transactionMapper,
                               PlatformTransactionManager txManager) {
        this.transactionRepository = transactionRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.metadataValidator = metadataValidator;
        this.transactionMapper = transactionMapper;
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Creates a transaction atomically. On duplicate external_id returns 409 with the existing id.
     * The save runs in a dedicated transaction so that a constraint violation can be caught cleanly
     * and a subsequent read can execute without a rolled-back session.
     */
    public TransactionResponse create(TransactionRequest req) {
        log.info("Creating transaction external_id={} type={} currency={}",
                req.externalId(), req.type(), req.currency());
        String metadataJson = metadataValidator.validateAndSerialize(req.type(), req.metadata());
        TransactionEntity entity = transactionMapper.toEntity(req, metadataJson);

        try {
            // Execute in its own transaction so a constraint violation rolls back cleanly
            // and we can do a fresh read outside the rolled-back session.
            TransactionResponse created = transactionTemplate.execute(status -> {
                TransactionEntity s = transactionRepository.save(entity);
                transactionRepository.flush();
                // toResponse uses in-memory collections — no lazy loading needed here
                return transactionMapper.toResponse(s);
            });
            log.info("Created transaction id={} external_id={}", created.id(), req.externalId());
            return created;
        } catch (RuntimeException e) {
            // The insert failed. Two interesting cases both resolve to 409:
            //   - UNIQUE constraint on external_id (duplicate). Note: Hibernate's community
            //     SQLite dialect surfaces this as JpaSystemException/GenericJDBCException
            //     rather than DataIntegrityViolationException, so we cannot match on type.
            //   - SQLITE_BUSY after the busy timeout under heavy concurrent writes.
            // In both cases the winning row is already committed, so if a row with this
            // external_id now exists it is definitively a duplicate.
            TransactionEntity existing = transactionRepository.findByExternalId(req.externalId()).orElse(null);
            if (existing != null) {
                log.warn("Duplicate external_id='{}' rejected; returning existing id={}",
                        req.externalId(), existing.getId());
                throw new ApiException(HttpStatus.CONFLICT,
                        "Transaction with external_id '" + req.externalId() + "' already exists",
                        existing.getId());
            }
            // No duplicate row — this was a genuine failure; let it surface as 500.
            log.error("Transaction create failed for external_id='{}' with no duplicate present",
                    req.externalId(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(String id) {
        return transactionMapper.toResponse(loadFull(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(int limit, int offset) {
        if (limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "limit must be ≤ 100, got " + limit);
        }
        List<TransactionResponse> items = transactionRepository.findPage(limit, offset)
                .stream()
                .map(transactionMapper::toResponse)
                .collect(Collectors.toList());
        long total = transactionRepository.countAll();
        long nextOffset = (long) offset + items.size();
        return new PageResponse<>(items, limit, offset, total,
                nextOffset < total ? nextOffset : null);
    }

    @Transactional
    public TransactionResponse patch(String id, TransactionRequest req) {
        log.info("Patching transaction id={}", id);
        TransactionEntity entity = loadFull(id);
        if (invoiceLineRepository.existsBySourceTransactionId(id)) {
            log.warn("Rejecting patch of invoiced transaction id={} (frozen)", id);
            throw new ApiException(HttpStatus.CONFLICT,
                    "Transaction " + id + " is included in an invoice and cannot be modified");
        }

        String metadataJson = metadataValidator.validateAndSerialize(req.type(), req.metadata());

        entity.setType(req.type().toLowerCase());
        entity.setCurrency(req.currency());
        entity.setOccurredAt(req.occurredAt());
        entity.setTotalMinor(Money.toLong(req.total()));
        entity.setMetadataJson(metadataJson);
        entity.setUpdatedAt(Instant.now().toString());

        // Replace tax/fee lines wholesale; orphanRemoval deletes the old rows
        entity.getTaxLines().clear();
        req.taxLines().forEach(dto -> {
            TaxLineEntity t = new TaxLineEntity();
            t.setTransaction(entity);
            t.setName(dto.name());
            t.setRate(dto.rate() != null ? dto.rate().toPlainString() : null);
            t.setAmountMinor(Money.toLong(dto.amount()));
            entity.getTaxLines().add(t);
        });

        entity.getFeeLines().clear();
        req.feeLines().forEach(dto -> {
            FeeLineEntity f = new FeeLineEntity();
            f.setTransaction(entity);
            f.setName(dto.name());
            f.setAmountMinor(Money.toLong(dto.amount()));
            entity.getFeeLines().add(f);
        });

        return transactionMapper.toResponse(transactionRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        log.info("Deleting transaction id={}", id);
        TransactionEntity entity = transactionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));
        if (invoiceLineRepository.existsBySourceTransactionId(id)) {
            log.warn("Rejecting delete of invoiced transaction id={} (frozen)", id);
            throw new ApiException(HttpStatus.CONFLICT,
                    "Transaction " + id + " is included in an invoice and cannot be deleted");
        }
        transactionRepository.delete(entity);
        log.info("Deleted transaction id={}", id);
    }

    /** Load entity with lazy collections initialized within an active transaction. */
    private TransactionEntity loadFull(String id) {
        TransactionEntity entity = transactionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));
        // Touch lazy collections to initialize them
        entity.getTaxLines().size();
        entity.getFeeLines().size();
        return entity;
    }
}
