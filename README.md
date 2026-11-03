# Navan Travel Transactions & Invoice Service

Spring Boot REST API for flight / hotel / rail / navan_fee transactions with frozen invoice snapshots.

## Prerequisites
- Java 17+
- Maven (or use the bundled `./mvnw` wrapper)

## Run

```bash
./mvnw spring-boot:run
```

Server starts on **http://localhost:8080**. The SQLite database is created at `data/navan.db`.

## Test

```bash
./mvnw test
```

All 10 required cases run against a real on-disk `data/test-navan.db`. Clean after with:

```bash
rm -f data/test-navan.db*   # also removes the -wal / -shm sidecars (WAL mode)
```

## Health

Provided by Spring Boot Actuator:

```bash
curl http://localhost:8080/actuator/health
```

---

## Example curls

### Create transactions

**Flight:**
```bash
curl -s -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" -d '{
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
}' | jq .
```

**Hotel:**
```bash
curl -s -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" -d '{
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
}' | jq .
```

**Rail:**
```bash
curl -s -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" -d '{
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
}' | jq .
```

**Navan trip fee:**
```bash
curl -s -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" -d '{
  "type": "navan_fee",
  "external_id": "navan-trip-fee-2026-03-11",
  "currency": "EUR",
  "occurred_at": "2026-03-11T06:40:00Z",
  "total": 25.00,
  "tax_lines": [], "fee_lines": [],
  "metadata": {"fee_kind":"trip_fee","description":"Navan trip fee",
               "related_external_id":"flight-TXL-LHR-2026-03-11"}
}' | jq .
```

**Navan agent call fee:**
```bash
curl -s -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" -d '{
  "type": "navan_fee",
  "external_id": "navan-agent-call-2026-03-11",
  "currency": "EUR",
  "occurred_at": "2026-03-11T18:00:00Z",
  "total": 15.00,
  "tax_lines": [], "fee_lines": [],
  "metadata": {"fee_kind":"agent_call_fee","description":"Agent fee for support call",
               "related_external_id":"hotel-LON-2026-03-11"}
}' | jq .
```

---

### Duplicate create — POST the same external_id twice

```bash
# First POST → 201
curl -s -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" -d '{
  "type":"navan_fee","external_id":"dupe-demo","currency":"EUR",
  "occurred_at":"2026-03-11T06:40:00Z","total":10.00,
  "tax_lines":[],"fee_lines":[],
  "metadata":{"fee_kind":"trip_fee"}
}' | jq '{id:.id, status:"CREATED"}'

# Second POST — same external_id → 409 with existing_id
curl -s -X POST http://localhost:8080/transactions \
  -H "Content-Type: application/json" -d '{
  "type":"navan_fee","external_id":"dupe-demo","currency":"EUR",
  "occurred_at":"2026-03-11T06:40:00Z","total":10.00,
  "tax_lines":[],"fee_lines":[],
  "metadata":{"fee_kind":"trip_fee"}
}' | jq .

# Confirm exactly one row
curl -s http://localhost:8080/transactions?limit=100 | jq '.total'
```

---

### Read & list transactions

```bash
# GET by id
curl -s http://localhost:8080/transactions/<id> | jq .

# Paginated list (default limit 50)
curl -s "http://localhost:8080/transactions" | jq '{total:.total, items:.items|length}'

# Limit=2 offset=0
curl -s "http://localhost:8080/transactions?limit=2&offset=0" | jq .

# limit > 100 → 400
curl -s "http://localhost:8080/transactions?limit=200" | jq .
```

---

### Patch & delete transaction

```bash
# PATCH — update total (only works when not on any invoice)
curl -s -X PATCH http://localhost:8080/transactions/<id> \
  -H "Content-Type: application/json" -d '{
  "type":"navan_fee","external_id":"dupe-demo","currency":"EUR",
  "occurred_at":"2026-03-11T06:40:00Z","total":20.00,
  "tax_lines":[],"fee_lines":[],
  "metadata":{"fee_kind":"trip_fee"}
}' | jq .

# PATCH when already invoiced → 409
# (first create invoice, then try to patch)
curl -s -X PATCH http://localhost:8080/transactions/<invoiced-id> \
  -H "Content-Type: application/json" -d '{ ... }' | jq .

# DELETE not invoiced → 204
curl -s -X DELETE http://localhost:8080/transactions/<id> -w "%{http_code}"

# DELETE invoiced → 409
curl -s -X DELETE http://localhost:8080/transactions/<invoiced-id> | jq .
```

---

### Create invoice

```bash
# Single transaction
curl -s -X POST http://localhost:8080/invoices \
  -H "Content-Type: application/json" \
  -d '{"transaction_id":"<txn-id>"}' | jq .

# Trip invoice (flight + hotel + rail + both fees)
curl -s -X POST http://localhost:8080/invoices \
  -H "Content-Type: application/json" \
  -d '{
    "transaction_ids": ["<flight-id>","<hotel-id>","<rail-id>","<trip-fee-id>","<agent-fee-id>"]
  }' | jq '{id:.id, currency:.currency, total:.total}'
```

---

### Read & list invoices

```bash
curl -s http://localhost:8080/invoices/<id> | jq .
curl -s "http://localhost:8080/invoices" | jq .
```

---

### Patch & delete invoice

```bash
# PATCH — replace transaction set (rebuilds snapshot)
curl -s -X PATCH http://localhost:8080/invoices/<id> \
  -H "Content-Type: application/json" \
  -d '{"transaction_ids":["<new-txn-id>"]}' | jq .

# DELETE invoice (leaves transactions intact)
curl -s -X DELETE http://localhost:8080/invoices/<id> -w "%{http_code}"
```

---

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full architecture — HLD + LLD,
data model, schema, indexes, concurrency, and high volume.
