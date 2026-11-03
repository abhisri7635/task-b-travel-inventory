package com.example.navan.controller;

import com.example.navan.dto.InvoiceRequest;
import com.example.navan.dto.InvoiceResponse;
import com.example.navan.dto.PageResponse;
import com.example.navan.service.InvoiceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for invoices:
 * <pre>
 *   POST   /invoices        create from transaction_id(s) (201)
 *   GET    /invoices/{id}   read   (200 / 404)
 *   GET    /invoices        paginated list (limit default 50, max 100)
 *   PATCH  /invoices/{id}   replace transaction set, rebuild snapshot (200 / 404 / 409)
 *   DELETE /invoices/{id}   delete invoice, keep transactions (204 / 404)
 * </pre>
 * This layer only binds HTTP to {@link InvoiceService}; all rules live in the service.
 */
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@RequestBody InvoiceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(invoiceService.getById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<InvoiceResponse>> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(invoiceService.list(limit, offset));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<InvoiceResponse> patch(
            @PathVariable String id,
            @RequestBody InvoiceRequest req) {
        return ResponseEntity.ok(invoiceService.patch(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
