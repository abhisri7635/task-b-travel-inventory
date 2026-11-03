package com.example.navan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required cases: 1 (create each type + GET round-trip), 2 (duplicate external_id), 10 (pagination cap).
 */
class TransactionApiTest extends NavanIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    // ── Fixture bodies from seed-payloads.json ───────────────────────────────

    private static final String FLIGHT_BODY = """
            {
              "type": "flight",
              "external_id": "flight-TXL-LHR-2026-03-11",
              "currency": "EUR",
              "occurred_at": "2026-03-11T06:40:00Z",
              "total": 220.00,
              "tax_lines": [{"name":"Airport tax","amount":15.00}],
              "fee_lines": [{"name":"YQ","amount":25.00}],
              "metadata": {
                "origin":"TXL","destination":"LHR","fare":180.00,
                "coupons":[{"carrier":"BA","flight_number":"BA982","from":"TXL","to":"LHR",
                            "departure":"2026-03-11T07:00:00Z","arrival":"2026-03-11T08:20:00Z"}]
              }
            }""";

    private static final String HOTEL_BODY = """
            {
              "type": "hotel",
              "external_id": "hotel-LON-2026-03-11",
              "currency": "EUR",
              "occurred_at": "2026-03-11T15:00:00Z",
              "total": 331.70,
              "tax_lines": [{"name":"VAT","rate":0.07,"amount":21.70}],
              "fee_lines": [],
              "metadata": {
                "name":"Hotel Mitte London",
                "address":{"street":"12 Kingsway","city":"London","country":"GB"},
                "check_in":"2026-03-11","check_out":"2026-03-13","nights":2,
                "room_rate_per_night":140.00,
                "extras":[{"name":"Breakfast","amount":30.00}]
              }
            }""";

    private static final String RAIL_BODY = """
            {
              "type": "rail",
              "external_id": "rail-STP-PAR-2026-03-13",
              "currency": "EUR",
              "occurred_at": "2026-03-13T09:00:00Z",
              "total": 94.00,
              "tax_lines": [],
              "fee_lines": [{"name":"Booking fee","amount":4.00}],
              "metadata": {
                "legs":[
                  {"origin":"London St Pancras","destination":"Lille Europe",
                   "departure":"2026-03-13T09:00:00Z","arrival":"2026-03-13T10:30:00Z",
                   "train_number":"EST9108","class":"standard"},
                  {"origin":"Lille Europe","destination":"Paris Nord",
                   "departure":"2026-03-13T11:00:00Z","arrival":"2026-03-13T12:05:00Z",
                   "train_number":"EST9122","class":"standard"}
                ]
              }
            }""";

    private static final String TRIP_FEE_BODY = """
            {
              "type": "navan_fee",
              "external_id": "navan-trip-fee-2026-03-11",
              "currency": "EUR",
              "occurred_at": "2026-03-11T06:40:00Z",
              "total": 25.00,
              "tax_lines": [],
              "fee_lines": [],
              "metadata": {
                "fee_kind":"trip_fee",
                "description":"Navan trip fee",
                "related_external_id":"flight-TXL-LHR-2026-03-11"
              }
            }""";

    private static final String AGENT_FEE_BODY = """
            {
              "type": "navan_fee",
              "external_id": "navan-agent-call-2026-03-11",
              "currency": "EUR",
              "occurred_at": "2026-03-11T18:00:00Z",
              "total": 15.00,
              "tax_lines": [],
              "fee_lines": [],
              "metadata": {
                "fee_kind":"agent_call_fee",
                "description":"Agent fee for support call",
                "related_external_id":"hotel-LON-2026-03-11"
              }
            }""";

    // ── Test 1a: create flight, GET returns coupons ───────────────────────────

    @Test
    void createFlight_getReturnsCoupons() throws Exception {
        ResponseEntity<JsonNode> resp = postJson(FLIGHT_BODY);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = resp.getBody().path("id").asText();
        assertThat(id).startsWith("txn_");

        ResponseEntity<JsonNode> get = rest.getForEntity("/transactions/" + id, JsonNode.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode meta = get.getBody().path("metadata");
        assertThat(meta.path("origin").asText()).isEqualTo("TXL");
        assertThat(meta.path("destination").asText()).isEqualTo("LHR");
        JsonNode coupon = meta.path("coupons").get(0);
        assertThat(coupon.path("flight_number").asText()).isEqualTo("BA982");
        assertThat(coupon.path("carrier").asText()).isEqualTo("BA");
        assertThat(coupon.path("from").asText()).isEqualTo("TXL");
        assertThat(coupon.path("to").asText()).isEqualTo("LHR");
    }

    // ── Test 1b: create hotel, GET returns name + address ────────────────────

    @Test
    void createHotel_getReturnsNameAndAddress() throws Exception {
        ResponseEntity<JsonNode> resp = postJson(HOTEL_BODY);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = resp.getBody().path("id").asText();

        JsonNode meta = rest.getForEntity("/transactions/" + id, JsonNode.class)
                .getBody().path("metadata");
        assertThat(meta.path("name").asText()).isEqualTo("Hotel Mitte London");
        assertThat(meta.path("address").path("street").asText()).isEqualTo("12 Kingsway");
        assertThat(meta.path("address").path("city").asText()).isEqualTo("London");
        assertThat(meta.path("address").path("country").asText()).isEqualTo("GB");
    }

    // ── Test 1c: create rail, GET returns legs ───────────────────────────────

    @Test
    void createRail_getReturnsLegs() throws Exception {
        ResponseEntity<JsonNode> resp = postJson(RAIL_BODY);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = resp.getBody().path("id").asText();

        JsonNode meta = rest.getForEntity("/transactions/" + id, JsonNode.class)
                .getBody().path("metadata");
        assertThat(meta.path("legs").size()).isEqualTo(2);
        assertThat(meta.path("legs").get(0).path("train_number").asText()).isEqualTo("EST9108");
        assertThat(meta.path("legs").get(1).path("train_number").asText()).isEqualTo("EST9122");
        assertThat(meta.path("legs").get(0).path("class").asText()).isEqualTo("standard");
    }

    // ── Test 1d+e: create both navan_fee kinds, GET returns fee_kind ─────────

    @Test
    void createTripFee_getReturnsFeeKind() throws Exception {
        ResponseEntity<JsonNode> resp = postJson(TRIP_FEE_BODY);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = resp.getBody().path("id").asText();

        JsonNode meta = rest.getForEntity("/transactions/" + id, JsonNode.class)
                .getBody().path("metadata");
        assertThat(meta.path("fee_kind").asText()).isEqualTo("trip_fee");
    }

    @Test
    void createAgentCallFee_getReturnsFeeKind() throws Exception {
        ResponseEntity<JsonNode> resp = postJson(AGENT_FEE_BODY);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = resp.getBody().path("id").asText();

        JsonNode meta = rest.getForEntity("/transactions/" + id, JsonNode.class)
                .getBody().path("metadata");
        assertThat(meta.path("fee_kind").asText()).isEqualTo("agent_call_fee");
    }

    // ── Test 2: duplicate external_id → 409 with existing id, one row ────────

    @Test
    void duplicateExternalId_returns409_andOnlyOneRowExists() throws Exception {
        ResponseEntity<JsonNode> first = postJson(FLIGHT_BODY);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String existingId = first.getBody().path("id").asText();

        ResponseEntity<JsonNode> second = postJson(FLIGHT_BODY);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().path("existing_id").asText()).isEqualTo(existingId);

        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    // ── Unknown type → 400 ────────────────────────────────────────────────────

    @Test
    void unknownType_returns400() {
        String body = FLIGHT_BODY.replace("\"flight\"", "\"spaceship\"");
        ResponseEntity<JsonNode> resp = postJson(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Unknown fee_kind → 400 ────────────────────────────────────────────────

    @Test
    void unknownFeeKind_returns400() {
        String body = TRIP_FEE_BODY.replace("\"trip_fee\"", "\"magic_fee\"");
        ResponseEntity<JsonNode> resp = postJson(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Test 10: pagination cap ───────────────────────────────────────────────

    @Test
    void listTransactions_limitOver100_returns400() {
        ResponseEntity<JsonNode> resp = rest.getForEntity("/transactions?limit=1000", JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listTransactions_defaultLimit50_inResponse() {
        ResponseEntity<JsonNode> resp = rest.getForEntity("/transactions", JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().path("limit").asInt()).isEqualTo(50);
    }

    // ── Positive: PATCH on non-invoiced txn succeeds ─────────────────────────

    @Test
    void patch_nonInvoicedTransaction_succeeds() throws Exception {
        String id = postJson(FLIGHT_BODY).getBody().path("id").asText();

        String patchBody = FLIGHT_BODY
                .replace("\"external_id\": \"flight-TXL-LHR-2026-03-11\"",
                         "\"external_id\": \"flight-TXL-LHR-2026-03-11\"")
                .replace("220.00", "250.00");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> patchResp = rest.exchange(
                "/transactions/" + id, HttpMethod.PATCH,
                new HttpEntity<>(patchBody, headers), JsonNode.class);
        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().path("total").asText()).isEqualTo("250.00");
    }

    // ── GET non-existent → 404 ────────────────────────────────────────────────

    @Test
    void getNonExistent_returns404() {
        ResponseEntity<JsonNode> resp = rest.getForEntity("/transactions/txn_nonexistent", JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private ResponseEntity<JsonNode> postJson(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/transactions", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }
}
