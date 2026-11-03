package com.example.navan.controller;

import com.example.navan.dto.PageResponse;
import com.example.navan.dto.TransactionRequest;
import com.example.navan.dto.TransactionResponse;
import com.example.navan.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for transactions:
 * <pre>
 *   POST   /transactions        create (201)
 *   GET    /transactions/{id}   read   (200 / 404)
 *   GET    /transactions        paginated list (limit default 50, max 100)
 *   PATCH  /transactions/{id}   update (200 / 404 / 409 if invoiced)
 *   DELETE /transactions/{id}   delete (204 / 404 / 409 if invoiced)
 * </pre>
 * This layer only binds HTTP to {@link TransactionService}; all rules live in the service.
 */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<TransactionResponse>> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(transactionService.list(limit, offset));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TransactionResponse> patch(
            @PathVariable String id,
            @Valid @RequestBody TransactionRequest req) {
        return ResponseEntity.ok(transactionService.patch(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
