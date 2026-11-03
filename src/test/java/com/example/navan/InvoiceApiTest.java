package com.example.navan;

import com.example.navan.entity.TransactionEntity;
import com.example.navan.util.Money;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required cases: 3 (trip invoice totals 685.70), 4 (snapshot), 5 (mixed currency).
 */
class InvoiceApiTest extends NavanIntegrationTest {

    private static final String FLIGHT_BODY = """
            {"type":"flight","external_id":"flight-TXL-LHR-2026-03-11","currency":"EUR",
             "occurred_at":"2026-03-11T06:40:00Z","total":220.00,
             "tax_lines":[{"name":"Airport tax","amount":15.00}],
             "fee_lines":[{"name":"YQ","amount":25.00}],
             "metadata":{"origin":"TXL","destination":"LHR","fare":180.00,
               "coupons":[{"carrier":"BA","flight_number":"BA982","from":"TXL","to":"LHR",
                           "departure":"2026-03-11T07:00:00Z","arrival":"2026-03-11T08:20:00Z"}]}}""";

    private static final String HOTEL_BODY = """
            {"type":"hotel","external_id":"hotel-LON-2026-03-11","currency":"EUR",
             "occurred_at":"2026-03-11T15:00:00Z","total":331.70,
             "tax_lines":[{"name":"VAT","rate":0.07,"amount":21.70}],
             "fee_lines":[],
             "metadata":{"name":"Hotel Mitte London",
               "address":{"street":"12 Kingsway","city":"London","country":"GB"},
               "check_in":"2026-03-11","check_out":"2026-03-13","nights":2,
               "room_rate_per_night":140.00,"extras":[{"name":"Breakfast","amount":30.00}]}}""";

    private static final String RAIL_BODY = """
            {"type":"rail","external_id":"rail-STP-PAR-2026-03-13","currency":"EUR",
             "occurred_at":"2026-03-13T09:00:00Z","total":94.00,
             "tax_lines":[],"fee_lines":[{"name":"Booking fee","amount":4.00}],
             "metadata":{"legs":[
               {"origin":"London St Pancras","destination":"Lille Europe",
                "departure":"2026-03-13T09:00:00Z","arrival":"2026-03-13T10:30:00Z",
                "train_number":"EST9108","class":"standard"},
               {"origin":"Lille Europe","destination":"Paris Nord",
                "departure":"2026-03-13T11:00:00Z","arrival":"2026-03-13T12:05:00Z",
                "train_number":"EST9122","class":"standard"}]}}""";

    private static final String TRIP_FEE_BODY = """
            {"type":"navan_fee","external_id":"navan-trip-fee-2026-03-11","currency":"EUR",
             "occurred_at":"2026-03-11T06:40:00Z","total":25.00,
             "tax_lines":[],"fee_lines":[],
             "metadata":{"fee_kind":"trip_fee","description":"Navan trip fee",
               "related_external_id":"flight-TXL-LHR-2026-03-11"}}""";

    private static final String AGENT_FEE_BODY = """
            {"type":"navan_fee","external_id":"navan-agent-call-2026-03-11","currency":"EUR",
             "occurred_at":"2026-03-11T18:00:00Z","total":15.00,
             "tax_lines":[],"fee_lines":[],
             "metadata":{"fee_kind":"agent_call_fee","description":"Agent fee for support call",
               "related_external_id":"hotel-LON-2026-03-11"}}""";

    private static final String USD_TXN_BODY = """
            {"type":"navan_fee","external_id":"usd-fee-001","currency":"USD",
             "occurred_at":"2026-03-11T06:40:00Z","total":10.00,
             "tax_lines":[],"fee_lines":[],
             "metadata":{"fee_kind":"trip_fee","description":"USD fee"}}""";

    // ── Test 3: trip invoice grand total 685.70 ───────────────────────────────

    @Test
    void tripInvoice_grandTotal_685_70() {
        String flightId  = createTxn(FLIGHT_BODY);
        String hotelId   = createTxn(HOTEL_BODY);
        String railId    = createTxn(RAIL_BODY);
        String tripFeeId = createTxn(TRIP_FEE_BODY);
        String agentId   = createTxn(AGENT_FEE_BODY);

        String invoiceBody = String.format(
                "{\"transaction_ids\":[\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"]}",
                flightId, hotelId, railId, tripFeeId, agentId);

        ResponseEntity<JsonNode> resp = postJson("/invoices", invoiceBody);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode inv = resp.getBody();

        assertThat(inv.path("currency").asText()).isEqualTo("EUR");
        assertThat(new BigDecimal(inv.path("total").asText()))
                .isEqualByComparingTo(new BigDecimal("685.70"));
        assertThat(inv.path("lines").size()).isEqualTo(5);

        // Tax rollup: Airport tax 15.00, VAT 21.70
        JsonNode taxRollup = inv.path("tax_rollup");
        assertThat(taxRollup.size()).isEqualTo(2);
        boolean hasAirportTax = false, hasVAT = false;
        for (JsonNode t : taxRollup) {
            if ("Airport tax".equals(t.path("name").asText())) {
                assertThat(new BigDecimal(t.path("amount").asText()))
                        .isEqualByComparingTo(new BigDecimal("15.00"));
                hasAirportTax = true;
            }
            if ("VAT".equals(t.path("name").asText())) {
                assertThat(new BigDecimal(t.path("amount").asText()))
                        .isEqualByComparingTo(new BigDecimal("21.70"));
                hasVAT = true;
            }
        }
        assertThat(hasAirportTax).isTrue();
        assertThat(hasVAT).isTrue();

        // Fee rollup: YQ 25.00, Booking fee 4.00
        JsonNode feeRollup = inv.path("fee_rollup");
        assertThat(feeRollup.size()).isEqualTo(2);
        boolean hasYQ = false, hasBookingFee = false;
        for (JsonNode f : feeRollup) {
            if ("YQ".equals(f.path("name").asText())) {
                assertThat(new BigDecimal(f.path("amount").asText()))
                        .isEqualByComparingTo(new BigDecimal("25.00"));
                hasYQ = true;
            }
            if ("Booking fee".equals(f.path("name").asText())) {
                assertThat(new BigDecimal(f.path("amount").asText()))
                        .isEqualByComparingTo(new BigDecimal("4.00"));
                hasBookingFee = true;
            }
        }
        assertThat(hasYQ).isTrue();
        assertThat(hasBookingFee).isTrue();
    }

    // ── Test 4: invoice snapshot is frozen ───────────────────────────────────

    @Test
    void invoiceSnapshot_isFrozen_afterTransactionMutation() {
        String flightId = createTxn(FLIGHT_BODY);
        String invoiceBody = "{\"transaction_id\":\"" + flightId + "\"}";

        ResponseEntity<JsonNode> invoiceResp = postJson("/invoices", invoiceBody);
        assertThat(invoiceResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String invId = invoiceResp.getBody().path("id").asText();

        // Directly mutate the stored flight total via repository (bypasses 409 guard)
        TransactionEntity txn = transactionRepository.findById(flightId).orElseThrow();
        txn.setTotalMinor(Money.toLong(new BigDecimal("999.00")));
        transactionRepository.save(txn);

        // GET invoice must still show the original 220.00
        JsonNode inv = rest.getForEntity("/invoices/" + invId, JsonNode.class).getBody();
        JsonNode flightLine = inv.path("lines").get(0);
        assertThat(new BigDecimal(flightLine.path("total").asText()))
                .isEqualByComparingTo(new BigDecimal("220.00"));
        assertThat(new BigDecimal(inv.path("total").asText()))
                .isEqualByComparingTo(new BigDecimal("220.00"));
    }

    // ── Test 5: mixed currency → 409 ─────────────────────────────────────────

    @Test
    void mixedCurrencyInvoice_returns409() {
        String eurId = createTxn(FLIGHT_BODY);
        String usdId = createTxn(USD_TXN_BODY);

        String body = String.format("{\"transaction_ids\":[\"%s\",\"%s\"]}", eurId, usdId);
        ResponseEntity<JsonNode> resp = postJson("/invoices", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── Duplicate transaction_id in request → 409 ────────────────────────────

    @Test
    void duplicateTransactionIdInRequest_returns409() {
        String id = createTxn(FLIGHT_BODY);
        String body = String.format("{\"transaction_ids\":[\"%s\",\"%s\"]}", id, id);
        ResponseEntity<JsonNode> resp = postJson("/invoices", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── PATCH invoice rebuilds snapshot ──────────────────────────────────────

    @Test
    void patchInvoice_rebuildsSnapshot() {
        String flightId = createTxn(FLIGHT_BODY);
        String hotelId  = createTxn(HOTEL_BODY);

        String invId = postJson("/invoices", "{\"transaction_id\":\"" + flightId + "\"}")
                .getBody().path("id").asText();

        // Patch to include both flight and hotel
        String patchBody = String.format("{\"transaction_ids\":[\"%s\",\"%s\"]}", flightId, hotelId);
        ResponseEntity<JsonNode> patchResp = patchJson("/invoices/" + invId, patchBody);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode updated = patchResp.getBody();
        assertThat(updated.path("lines").size()).isEqualTo(2);
        assertThat(new BigDecimal(updated.path("total").asText()))
                .isEqualByComparingTo(new BigDecimal("551.70"));
    }

    // ── DELETE invoice → invoice gone, txns survive ───────────────────────────

    @Test
    void deleteInvoice_invoiceGone_transactionsSurvive() {
        String flightId = createTxn(FLIGHT_BODY);
        String invId = postJson("/invoices", "{\"transaction_id\":\"" + flightId + "\"}")
                .getBody().path("id").asText();

        ResponseEntity<Void> del = rest.exchange("/invoices/" + invId, HttpMethod.DELETE,
                null, Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(rest.getForEntity("/invoices/" + invId, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/transactions/" + flightId, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ── Missing transaction in invoice request → 404 ─────────────────────────

    @Test
    void invoiceWithMissingTransaction_returns404() {
        String body = "{\"transaction_id\":\"txn_doesnotexist\"}";
        ResponseEntity<JsonNode> resp = postJson("/invoices", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String createTxn(String body) {
        ResponseEntity<JsonNode> resp = postJson("/transactions", body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().path("id").asText();
    }

    private ResponseEntity<JsonNode> postJson(String path, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> patchJson(String path, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, h), JsonNode.class);
    }
}
