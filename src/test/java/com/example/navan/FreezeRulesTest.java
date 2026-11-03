package com.example.navan;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required cases: 7 (PATCH invoiced txn → 409), 8 (DELETE invoiced txn → 409), 9 (DELETE invoice keeps txns).
 */
class FreezeRulesTest extends NavanIntegrationTest {

    private static final String FLIGHT_BODY = """
            {"type":"flight","external_id":"freeze-flight-001","currency":"EUR",
             "occurred_at":"2026-03-11T06:40:00Z","total":220.00,
             "tax_lines":[{"name":"Airport tax","amount":15.00}],
             "fee_lines":[{"name":"YQ","amount":25.00}],
             "metadata":{"origin":"TXL","destination":"LHR","fare":180.00,
               "coupons":[{"carrier":"BA","flight_number":"BA982","from":"TXL","to":"LHR",
                           "departure":"2026-03-11T07:00:00Z","arrival":"2026-03-11T08:20:00Z"}]}}""";

    // ── Test 7: PATCH invoiced transaction → 409 ─────────────────────────────

    @Test
    void patchInvoicedTransaction_returns409_snapshotUnchanged() {
        String txnId = createTxn(FLIGHT_BODY);
        String invId = createInvoice(txnId);

        // PATCH the invoiced transaction
        String patchBody = FLIGHT_BODY
                .replace("\"external_id\":\"freeze-flight-001\"",
                         "\"external_id\":\"freeze-flight-001\"")
                .replace("220.00", "999.00");

        ResponseEntity<JsonNode> patchResp = patchJson("/transactions/" + txnId, patchBody);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Transaction total must be unchanged
        BigDecimal txnTotal = new BigDecimal(
                rest.getForEntity("/transactions/" + txnId, JsonNode.class)
                        .getBody().path("total").asText());
        assertThat(txnTotal).isEqualByComparingTo(new BigDecimal("220.00"));

        // Invoice total must be unchanged
        BigDecimal invTotal = new BigDecimal(
                rest.getForEntity("/invoices/" + invId, JsonNode.class)
                        .getBody().path("total").asText());
        assertThat(invTotal).isEqualByComparingTo(new BigDecimal("220.00"));
    }

    // ── Test 8: DELETE invoiced transaction → 409, row still exists ──────────

    @Test
    void deleteInvoicedTransaction_returns409_rowExists() {
        String txnId = createTxn(FLIGHT_BODY);
        createInvoice(txnId);

        ResponseEntity<Void> del = rest.exchange("/transactions/" + txnId,
                HttpMethod.DELETE, null, Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(transactionRepository.findById(txnId)).isPresent();
    }

    // ── Test 9: DELETE invoice → gone, transactions survive ──────────────────

    @Test
    void deleteInvoice_invoiceGone_transactionsSurvive() {
        String txnId = createTxn(FLIGHT_BODY);
        String invId = createInvoice(txnId);

        ResponseEntity<Void> del = rest.exchange("/invoices/" + invId,
                HttpMethod.DELETE, null, Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(rest.getForEntity("/invoices/" + invId, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/transactions/" + txnId, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ── After invoice deleted, transaction can be patched / deleted ──────────

    @Test
    void afterInvoiceDeleted_transactionCanBePatched() {
        String txnId = createTxn(FLIGHT_BODY);
        String invId = createInvoice(txnId);

        rest.exchange("/invoices/" + invId, HttpMethod.DELETE, null, Void.class);

        String patchBody = FLIGHT_BODY
                .replace("\"external_id\":\"freeze-flight-001\"",
                         "\"external_id\":\"freeze-flight-001\"")
                .replace("220.00", "250.00");
        ResponseEntity<JsonNode> patchResp = patchJson("/transactions/" + txnId, patchBody);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String createTxn(String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> resp = rest.exchange("/transactions", HttpMethod.POST,
                new HttpEntity<>(body, h), JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().path("id").asText();
    }

    private String createInvoice(String txnId) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> resp = rest.exchange("/invoices", HttpMethod.POST,
                new HttpEntity<>("{\"transaction_id\":\"" + txnId + "\"}", h), JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().path("id").asText();
    }

    private ResponseEntity<JsonNode> patchJson(String path, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, h), JsonNode.class);
    }
}
