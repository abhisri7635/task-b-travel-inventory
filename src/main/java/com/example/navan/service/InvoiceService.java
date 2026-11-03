package com.example.navan.service;

import com.example.navan.dto.InvoiceRequest;
import com.example.navan.dto.InvoiceResponse;
import com.example.navan.dto.PageResponse;
import com.example.navan.entity.*;
import com.example.navan.exception.ApiException;
import com.example.navan.mapper.InvoiceMapper;
import com.example.navan.repository.InvoiceRepository;
import com.example.navan.repository.TransactionRepository;
import com.example.navan.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for invoices. An invoice is a <b>frozen snapshot</b> built from the
 * current state of the referenced transactions: type, totals, tax lines, fee lines,
 * and metadata are copied into {@code invoice_lines.snapshot_json} at build time, so a
 * later edit to a transaction never mutates an already-issued invoice.
 *
 * <p>Enforces the invoice invariants: all transactions must share one currency (else
 * 409), a transaction id may not appear twice in one request (else 409), and every
 * referenced transaction must exist (else 404). The snapshot build and persist happen
 * in a single transaction so an invoice is written all-or-nothing.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final SnapshotBuilder snapshotBuilder;
    private final InvoiceMapper invoiceMapper;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          TransactionRepository transactionRepository,
                          SnapshotBuilder snapshotBuilder,
                          InvoiceMapper invoiceMapper) {
        this.invoiceRepository = invoiceRepository;
        this.transactionRepository = transactionRepository;
        this.snapshotBuilder = snapshotBuilder;
        this.invoiceMapper = invoiceMapper;
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest req) {
        List<TransactionEntity> txns = resolveTransactions(req);
        log.info("Creating invoice from {} transaction(s)", txns.size());
        String currency = assertSingleCurrency(txns);

        SnapshotBuilder.SnapshotResult result = snapshotBuilder.build(txns);

        String now = Instant.now().toString();
        InvoiceEntity inv = new InvoiceEntity();
        inv.setId(IdGenerator.invoiceId());
        inv.setCurrency(currency);
        inv.setTotalMinor(result.totalMinor());
        inv.setTaxTotalMinor(result.taxTotalMinor());
        inv.setFeeTotalMinor(result.feeTotalMinor());
        inv.setCreatedAt(now);
        inv.setUpdatedAt(now);

        attachChildren(inv, result);
        InvoiceEntity saved = invoiceRepository.save(inv);
        initializeLazyCollections(saved);
        log.info("Created invoice id={} currency={} total_minor={} lines={}",
                saved.getId(), currency, result.totalMinor(), result.lines().size());
        return invoiceMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(String id) {
        InvoiceEntity inv = loadFull(id);
        return invoiceMapper.toResponse(inv);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(int limit, int offset) {
        if (limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "limit must be ≤ 100, got " + limit);
        }
        List<InvoiceResponse> items = invoiceRepository.findPage(limit, offset)
                .stream()
                .map(inv -> { initializeLazyCollections(inv); return invoiceMapper.toResponse(inv); })
                .collect(Collectors.toList());
        long total = invoiceRepository.countAll();
        long nextOffset = (long) offset + items.size();
        return new PageResponse<>(items, limit, offset, total,
                nextOffset < total ? nextOffset : null);
    }

    @Transactional
    public InvoiceResponse patch(String id, InvoiceRequest req) {
        log.info("Patching invoice id={} (rebuilding snapshot)", id);
        InvoiceEntity inv = loadFull(id);

        List<TransactionEntity> txns = resolveTransactions(req);
        String currency = assertSingleCurrency(txns);

        SnapshotBuilder.SnapshotResult result = snapshotBuilder.build(txns);

        // Clear old children; orphanRemoval deletes them
        inv.getLines().clear();
        inv.getTaxRollup().clear();
        inv.getFeeRollup().clear();

        inv.setCurrency(currency);
        inv.setTotalMinor(result.totalMinor());
        inv.setTaxTotalMinor(result.taxTotalMinor());
        inv.setFeeTotalMinor(result.feeTotalMinor());
        inv.setUpdatedAt(Instant.now().toString());

        attachChildren(inv, result);
        InvoiceEntity saved = invoiceRepository.save(inv);
        invoiceRepository.flush();
        initializeLazyCollections(saved);
        return invoiceMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String id) {
        log.info("Deleting invoice id={} (transactions are left intact)", id);
        InvoiceEntity inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found: " + id));
        invoiceRepository.delete(inv);
        log.info("Deleted invoice id={}", id);
    }

    // -------------------------------------------------------------------------

    private List<TransactionEntity> resolveTransactions(InvoiceRequest req) {
        List<String> ids = req.normalizedIds();

        // 409 on duplicate ids in request
        Set<String> seen = new HashSet<>();
        for (String txnId : ids) {
            if (!seen.add(txnId)) {
                log.warn("Rejecting invoice request with duplicate transaction id={}", txnId);
                throw new ApiException(HttpStatus.CONFLICT,
                        "Duplicate transaction id in request: " + txnId);
            }
        }

        return ids.stream()
                .map(txnId -> {
                    TransactionEntity txn = transactionRepository.findById(txnId)
                            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                                    "Transaction not found: " + txnId));
                    // Initialize lazy collections needed by SnapshotBuilder
                    txn.getTaxLines().size();
                    txn.getFeeLines().size();
                    return txn;
                })
                .collect(Collectors.toList());
    }

    private String assertSingleCurrency(List<TransactionEntity> txns) {
        long distinct = txns.stream().map(TransactionEntity::getCurrency).distinct().count();
        if (distinct > 1) {
            log.warn("Rejecting mixed-currency invoice: {} distinct currencies", distinct);
            throw new ApiException(HttpStatus.CONFLICT,
                    "All transactions on an invoice must share the same currency");
        }
        return txns.get(0).getCurrency();
    }

    private void attachChildren(InvoiceEntity inv, SnapshotBuilder.SnapshotResult result) {
        result.lines().forEach(l -> { l.setInvoice(inv); inv.getLines().add(l); });
        result.taxRollup().forEach(r -> { r.setInvoice(inv); inv.getTaxRollup().add(r); });
        result.feeRollup().forEach(r -> { r.setInvoice(inv); inv.getFeeRollup().add(r); });
    }

    private InvoiceEntity loadFull(String id) {
        InvoiceEntity inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Invoice not found: " + id));
        initializeLazyCollections(inv);
        return inv;
    }

    private void initializeLazyCollections(InvoiceEntity inv) {
        inv.getLines().size();
        inv.getTaxRollup().size();
        inv.getFeeRollup().size();
    }
}
