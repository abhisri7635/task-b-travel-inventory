# Implementation Plan — Navan Travel Transactions & Invoice Service (Task B)

A Spring Boot REST service that ingests already-structured travel transactions
(`flight` / `hotel` / `rail` / `navan_fee`), rejects duplicates under concurrency, and builds
**frozen invoice snapshots** from stored data. Built to be correct, tested, and scalable within
the brief's constraints (no Redis/Kafka/lock service).

> This plan is the build-time companion to `ARCHITECTURE.md`. The two agree:
> **JSON metadata column** for type-specific data (taxes/fees/identity are real tables), and a
> **3-class service layer** (`TransactionService`, `InvoiceService`, `SnapshotBuilder`).

---

## 1. Stack & cross-cutting decisions

| Concern | Decision | Why |
|---|---|---|
| Language / framework | **Java 17 + Spring Boot 3**, REST (`spring-boot-starter-web`, Spring Data JPA, `spring-boot-starter-validation`) | Matches Navan's comparison baseline. |
| Build / run | **Maven** with `./mvnw` wrapper | One command to run, one to test. |
| Database | **SQLite** file `data/navan.db` via `org.xerial:sqlite-jdbc` + `org.hibernate.community:hibernate-community-dialects` (`SQLiteDialect`) | Real on-disk DB. **Not** `jdbc:h2:mem`. |
| Schema | Explicit `schema.sql` (Hibernate `ddl-auto=validate`) | Schema is a reviewed artifact, not a Hibernate guess; makes indexes/constraints auditable. |
| Money | **`BigDecimal` scale 2** at the edge; **integer minor units (`long` cents)** in DB; Jackson `USE_BIG_DECIMAL_FOR_FLOATS` | No IEEE `float` in the money path; exact integer rollups. |
| IDs | Server strings `txn_<ulid>` / `inv_<ulid>` | Sortable, collision-free, decoupled from `external_id`. |
| Validation | Bean Validation on DTOs + `MetadataValidator` for type/`fee_kind` | Unknown type / fee_kind → **400**, store nothing. |
| Errors | `ApiException` + `@RestControllerAdvice` | One consistent 400 / 404 / 409 JSON shape. |
| Time | `Instant` / ISO-8601 (`java.time`, JSR-310 module) | No legacy `Date`. |

**Documented policy choices** (README + ARCHITECTURE):
- Duplicate `external_id` → **409**, body includes existing `id`.
- Same transaction id twice on one invoice → **409**.
- `limit > 100` → **400** (strict).

---

## 2. Final repository structure

```
src/main/java/com/example/navan/
├── NavanApplication.java
├── config/
│   └── JacksonConfig.java              # BigDecimal-for-floats, JSR-310, scale=2
├── controller/
│   ├── TransactionController.java      # POST / GET / PATCH / DELETE / GET list
│   ├── InvoiceController.java          # POST / GET / PATCH / DELETE / GET list
│   └── HealthController.java           # GET /health
├── dto/
│   ├── TransactionRequest.java  TransactionResponse.java
│   ├── InvoiceRequest.java      InvoiceResponse.java
│   ├── TaxLineDto.java          FeeLineDto.java
│   ├── PageResponse.java                # { items, limit, next_cursor, total? }
│   └── ErrorResponse.java
├── enums/
│   ├── TransactionType.java             # flight | hotel | rail | navan_fee
│   └── FeeKind.java                     # trip_fee | agent_call_fee
├── metadata/
│   ├── FlightMetadata.java  HotelMetadata.java  RailMetadata.java  NavanFeeMetadata.java
│   └── MetadataValidator.java           # unknown type / fee_kind → 400
├── entity/
│   ├── TransactionEntity.java           # header + external_id UNIQUE + metadata_json
│   ├── TaxLineEntity.java  FeeLineEntity.java          # real cascade-child tables
│   ├── InvoiceEntity.java
│   ├── InvoiceLineEntity.java           # per-txn frozen snapshot (snapshot_json)
│   └── InvoiceTaxRollupEntity.java  InvoiceFeeRollupEntity.java
├── repository/
│   ├── TransactionRepository.java       # findByExternalId, keyset + offset list
│   ├── InvoiceRepository.java
│   └── InvoiceLineRepository.java       # existsBySourceTransactionId  ← 409 check
├── mapper/
│   ├── TransactionMapper.java           # entity ↔ dto, metadata JSON (de)serialize
│   └── InvoiceMapper.java
├── service/
│   ├── TransactionService.java          # create (+duplicate 409), read, list, patch/delete
│   ├── InvoiceService.java              # validate ids / mixed-currency, orchestrate, patch/delete
│   └── SnapshotBuilder.java             # build frozen snapshot + tax/fee rollups
└── exception/
    ├── ApiException.java
    └── GlobalExceptionHandler.java

src/main/resources/
├── application.yml                      # datasource, JPA validate, pool sizing
└── schema.sql                           # tables + indexes + constraints

src/test/java/com/example/navan/        # see §7
```

Rationale for the collapses (vs an earlier draft): dropped `TaxLineRepository` / `FeeLineRepository`
(tax/fee lines cascade from `TransactionEntity`, so dedicated repos would be dead code); dropped the
`TransactionWriteService`/read split (duplicate handling is ~5 lines — one `TransactionService`);
kept `SnapshotBuilder` separate because the snapshot+rollup is the hardest, most test-worthy logic.

---

## 3. Data model

### 3.0 How the tables get created (schema bootstrap)

The tables are **created by a real DDL script, not left to Hibernate guesswork** — so the schema
(constraints, indexes) is a reviewable artifact.

- `src/main/resources/schema.sql` (§3.1) holds every `CREATE TABLE` / `CREATE INDEX`.
- `application.yml` runs it at startup and makes Hibernate verify (not generate) the schema:
  ```yaml
  spring:
    sql:
      init:
        mode: always          # execute schema.sql on boot
        schema-locations: classpath:schema.sql
    jpa:
      hibernate:
        ddl-auto: validate     # entities must match the script; fail fast on drift
  datasource:
    url: jdbc:sqlite:data/navan.db   # real on-disk file (NOT jdbc:sqlite::memory:)
  ```
  Script is idempotent (`CREATE TABLE IF NOT EXISTS` / `CREATE ... IF NOT EXISTS` for indexes) so
  reboots don't fail on an existing file.
- **DB choice:** default **SQLite** (`org.xerial:sqlite-jdbc` + Hibernate community `SQLiteDialect`).
  **File-backed H2** is a drop-in alternative the brief also allows — swap the dependency, the
  dialect, and `url: jdbc:h2:file:./data/navan` (H2 mode). **Neither may be in-memory**
  (`jdbc:h2:mem:` / `jdbc:sqlite::memory:` are disallowed by the brief).
- **In tests** the same `schema.sql` is applied to a throwaway on-disk DB (fresh temp `.db` file
  per run, deleted after) — so tests exercise the real tables, constraints, and indexes, never an
  in-memory shortcut. See §7.

### 3.1 DDL (SQLite) — `schema.sql`

```sql
CREATE TABLE transactions (
  id            TEXT PRIMARY KEY,
  external_id   TEXT NOT NULL,
  type          TEXT NOT NULL,            -- flight|hotel|rail|navan_fee
  currency      TEXT NOT NULL,
  occurred_at   TEXT NOT NULL,            -- ISO-8601
  total_minor   INTEGER NOT NULL,         -- cents
  metadata_json TEXT NOT NULL,            -- typed per-type payload
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);
CREATE UNIQUE INDEX ux_transactions_external_id ON transactions(external_id);
CREATE INDEX ix_transactions_id_created ON transactions(created_at, id);  -- keyset list

CREATE TABLE tax_lines (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  transaction_id TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
  name           TEXT NOT NULL,
  rate           TEXT,                    -- nullable; BigDecimal as text
  amount_minor   INTEGER NOT NULL
);
CREATE INDEX ix_tax_lines_txn ON tax_lines(transaction_id);

CREATE TABLE fee_lines (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  transaction_id TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
  name           TEXT NOT NULL,
  amount_minor   INTEGER NOT NULL
);
CREATE INDEX ix_fee_lines_txn ON fee_lines(transaction_id);

CREATE TABLE invoices (
  id              TEXT PRIMARY KEY,
  currency        TEXT NOT NULL,
  total_minor     INTEGER NOT NULL,
  tax_total_minor INTEGER NOT NULL,
  fee_total_minor INTEGER NOT NULL,
  created_at      TEXT NOT NULL,
  updated_at      TEXT NOT NULL
);
CREATE INDEX ix_invoices_created ON invoices(created_at, id);

CREATE TABLE invoice_lines (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_id            TEXT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  source_transaction_id TEXT NOT NULL,
  type                  TEXT NOT NULL,
  currency              TEXT NOT NULL,
  total_minor           INTEGER NOT NULL,
  snapshot_json         TEXT NOT NULL     -- frozen copy: metadata + tax/fee lines
);
CREATE INDEX ix_invoice_lines_txn ON invoice_lines(source_transaction_id);  -- 409 check
CREATE INDEX ix_invoice_lines_invoice ON invoice_lines(invoice_id);

CREATE TABLE invoice_tax_rollup (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_id   TEXT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  rate         TEXT,
  amount_minor INTEGER NOT NULL
);
CREATE TABLE invoice_fee_rollup (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_id   TEXT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  amount_minor INTEGER NOT NULL
);
CREATE INDEX ix_tax_rollup_invoice ON invoice_tax_rollup(invoice_id);
CREATE INDEX ix_fee_rollup_invoice ON invoice_fee_rollup(invoice_id);
```

### 3.2 `metadata_json` by type (typed POJOs for validation + round-trip)

| type | POJO holds |
|---|---|
| `flight` | `origin`, `destination`, `fare`, `coupons[]` (`flight_number`, `carrier?`, `from`, `to`, `departure`, `arrival`) |
| `hotel` | `name`, `address{street, city, country}`, `check_in`, `check_out`, `nights`, `room_rate_per_night`, `extras?[]` |
| `rail` | `legs[]` (`origin`, `destination`, `departure`, `arrival`, `train_number?`, `class?`) |
| `navan_fee` | `fee_kind` (`trip_fee`\|`agent_call_fee`), `description?`, `related_external_id?` |

JSON is allowed by the brief because taxes/fees/identity are real columns/tables and GET
round-trips the structure via these POJOs. `MetadataValidator` rejects unknown `type` /
`fee_kind` with **400** before anything is written.

---

## 4. Endpoint specs

Each handler is thin: bind → validate → call service → map result to a status code.

### 4.1 `POST /transactions`
- **Validate** shared fields (Bean Validation) + `MetadataValidator`. Bad type/fee_kind → **400**.
- **Persist atomically** (`@Transactional`): header + `tax_lines` + `fee_lines` (metadata inside
  the header row). Cascade handles the children.
- **Duplicate:** insert; on `DataIntegrityViolationException` from `ux_transactions_external_id`,
  load by `external_id` and return it → **409** with the existing `id`.
- **201** on create; body = stored transaction incl. server `id`.

### 4.2 `GET /transactions/{id}`
- Load + map (coupons / address / legs / fee_kind round-trip). **404** if missing.

### 4.3 `GET /transactions` (list)
- Params: `limit` (default **50**, **>100 → 400**), and **`cursor`** (preferred) or `offset`.
- Keyset query: `WHERE (created_at, id) < (:cursor) ORDER BY created_at DESC, id DESC LIMIT :n`.
- Returns `PageResponse { items, limit, next_cursor }`. Never unbounded.

### 4.4 `POST /invoices`
- Body: `{ "transaction_id": ... }` **or** `{ "transaction_ids": [...] }`.
- Load all; **404** if any missing. Duplicate id in the list → **409** (documented).
- **Mixed currency → 409.**
- `SnapshotBuilder` copies type/total/tax/fee/metadata into `invoice_lines.snapshot_json`,
  computes `invoice_tax_rollup` / `invoice_fee_rollup` (group by name, +rate for tax), and the
  three totals (exact integer sums). One atomic write.
- **201**; body = invoice with line snapshots, rollups, totals.

### 4.5 `GET /invoices/{id}`
- Header + line snapshots + tax rollup + fee rollup + totals. **404** if missing. Reads only
  snapshot rows (never a live join to `transactions`).

### 4.6 `GET /invoices` (list) — same pagination contract as 4.3.

### 4.7 `PATCH /transactions/{id}`
- **404** if missing. If `invoiceLineRepository.existsBySourceTransactionId(id)` → **409**,
  persist nothing. Else atomic update of `total`/`tax_lines`/`fee_lines`/`metadata`/`occurred_at`
  (replace child lists wholesale inside the transaction; no torn list).

### 4.8 `DELETE /transactions/{id}`
- **404** if missing. If on any invoice → **409**, delete nothing. Else atomic delete (cascade
  removes tax/fee lines).

### 4.9 `PATCH /invoices/{id}`
- Replace `transaction_ids`. **404** if invoice or any id missing. Mixed currency → **409**,
  unchanged. Rebuild snapshot + rollups from **current** transactions in one atomic write
  (delete old snapshot rows, insert new). Existing snapshot never becomes a live join.

### 4.10 `DELETE /invoices/{id}`
- **404** if missing. Delete invoice + snapshot/rollup rows (cascade). **Transactions survive.**

### 4.11 `GET /health` → `{ "status": "UP" }`.

---

## 5. Concurrency, atomicity & money

- **Duplicate race:** correctness lives in the **DB unique index**, not an app-side check.
  Two overlapping POSTs: one commits, the other catches the constraint violation and returns the
  existing row. No `id = count+1`, no in-memory lock.
- **Atomic writes:** every multi-row mutation (create, patch, invoice build/rebuild, delete) runs
  in a single `@Transactional` unit — all rows persist or none. A reader never sees a half-written
  tax/fee list.
- **Concurrent PATCH/DELETE on one id:** rely on the DB transaction; SQLite serializes writers,
  so each write is atomic. Last-write-wins is acceptable because it is still all-or-nothing.
- **Money:** `BigDecimal` scale 2 in, `long` cents in DB, integer sums for rollups/totals, convert
  back at the edge. Documented scale = 2 (2-decimal currencies).

---

## 6. Scalability

The brief assumes **high create/update/list traffic** and forbids Redis/Kafka/lock services, so
scalability is designed into the **schema, query shapes, and app statelessness** — not bolted on.

### 6.1 Reads stay cheap
- **Every hot lookup is index-backed:** PK for `GET /{id}`; `ux_transactions_external_id` for
  duplicate detection; `ix_invoice_lines_txn` for the "is this transaction billed?" check
  (an index `EXISTS`, not a scan).
- **No N+1:** tax/fee children fetched with an explicit join / `@BatchSize`; invoice lines +
  rollups loaded in bounded queries per invoice.

### 6.2 Lists never degrade
- `limit` is mandatory (default 50, **max 100**); all list SQL is `LIMIT`-bounded; nothing is
  loaded into app memory to "paginate in memory".
- **Keyset (cursor) pagination is the primary contract**, with `offset` as a compatibility option.
  Offset pagination degrades `O(offset)` on deep pages (the DB still walks skipped rows); keyset on
  `(created_at, id)` with `ix_transactions_id_created` stays `O(limit)` at any depth — the behavior
  that matters under "many bookings per day".

### 6.3 Writes & connections
- **Connection pool** (HikariCP) sized explicitly in `application.yml`; pool, not unbounded
  threads, is the back-pressure point.
- SQLite in **WAL mode** + `busy_timeout` so concurrent readers don't block the single writer and
  transient write locks retry instead of failing — the realistic ceiling for SQLite write
  throughput, documented honestly.

### 6.4 Horizontal scale & the honest ceiling
- The **app is stateless** (no session/in-memory state; identity + snapshot live in the DB), so it
  scales horizontally behind a load balancer without code changes.
- **SQLite's single-writer model is the real throughput ceiling.** Because persistence is pure
  JPA/Spring Data against a `schema.sql`, the migration path to **Postgres** is a dependency +
  dialect + datasource swap — the entity/repository/service code is unchanged. Postgres then gives
  true concurrent writers, `ON CONFLICT` upserts, and partitioning/read-replica headroom. This is
  stated as the documented scaling story, not implemented (out of scope).

### 6.5 What fails first under load (and the mitigation)
| First to break | Mitigation in this design |
|---|---|
| Duplicate-POST storm racing check-then-insert | DB `UNIQUE(external_id)` + catch-and-return |
| Freeze check scanning `invoice_lines` on every PATCH/DELETE | `ix_invoice_lines_txn` → index `EXISTS` |
| Unbounded `SELECT *` list | mandatory `limit` ≤ 100, keyset pagination |
| Deep offset pages walking skipped rows | keyset/cursor on `(created_at, id)` |
| Write contention on SQLite | WAL + `busy_timeout`; documented Postgres path |

---

## 7. Tests (JUnit 5 + Spring Boot, real HTTP via `TestRestTemplate`, temp SQLite file)

`./mvnw test` runs all. Hitting the real HTTP API, not unit-testing JSON maps.

**Test DB & tables.** Tests boot the full Spring context against a **real on-disk** DB (a fresh
temp `.db` file per run, deleted afterward) with the production `schema.sql` applied — so every
test runs against the real tables, the `UNIQUE(external_id)` constraint, and the actual indexes.
No `jdbc:h2:mem:` / `jdbc:sqlite::memory:`. Config via `src/test/resources/application-test.yml`
(activated by `@ActiveProfiles("test")`); each test class starts from a clean schema.

```
src/test/java/com/example/navan/
├── TransactionApiTest.java    # create each type, GET round-trip, 400s, pagination, duplicate
├── InvoiceApiTest.java        # trip invoice totals, rollups, snapshot, mixed currency, patch/delete
├── FreezeRulesTest.java       # PATCH/DELETE invoiced txn → 409; DELETE invoice keeps txns
└── ConcurrencyTest.java       # overlapping duplicate POSTs → exactly one row
```

Mapping below shows which file covers each required case.

1. **Create** flight, hotel, rail, **both** navan_fee kinds from seed payloads; GET returns
   coupons / hotel address / rail legs / `fee_kind` — not an empty metadata object.
2. **Duplicate `external_id`:** second POST → no new row; **409** with existing `id`.
3. **Trip invoice** (flight + hotel + rail + both fees); grand total **685.70 EUR**; rollups match
   (`Airport tax 15`, `VAT 21.70`; `YQ 25`, `Booking fee 4`).
4. **Snapshot:** after invoicing, mutate the stored flight total **directly via repository**
   (bypassing the 409 guard); GET invoice still shows flight contribution **220.00** → proves a
   snapshot, not a live join.
5. **Mixed currency** (EUR + USD) invoice → **409**.
6. **Concurrent duplicate create:** `ExecutorService` + `CountDownLatch`, two overlapping POSTs
   same `external_id` → exactly **one** row (unique-constraint race).
7. **PATCH** invoiced transaction → **409**; invoice totals unchanged.
8. **DELETE** invoiced transaction → **409**; row still exists.
9. **DELETE invoice** → gone; underlying transactions still `GET`.
10. **`GET /transactions`** default page 50; `limit=1000` → **400**.

Plus: **unknown type / unknown fee_kind → 400**, and **PATCH on a non-invoiced transaction
succeeds** (positive counterpart to 7/8).

---

## 8. Build order (milestones)

1. Scaffold Spring Boot + `./mvnw`; `application.yml`; `schema.sql`; SQLite datasource + dialect.
2. Entities + 3 repositories; `JacksonConfig`; `Money` conversion helper.
3. `metadata/` POJOs + `MetadataValidator`; `TransactionMapper`.
4. `TransactionService` + `TransactionController`: create (atomic, duplicate 409) + GET. *(tests 1, 2)*
5. List + pagination (keyset + offset) + `/health`. *(test 10)*
6. `SnapshotBuilder` + `InvoiceService` + `InvoiceController`: create, GET, rollups, mixed-currency.
   *(tests 3, 4, 5)*
7. Freeze rules: PATCH/DELETE transaction (409 when invoiced), PATCH/DELETE invoice. *(tests 7, 8, 9)*
8. `GlobalExceptionHandler` + `ErrorResponse`; concurrency test. *(test 6)*
9. README (run + test + curls incl. duplicate-POST-twice proof) + verify against `expected-invoice.json`.
10. `.gitignore` (`data/*.db`, `.env`, `target/`); `git init` + initial commit.

---

## 9. Deliverables checklist

- [ ] Runs in one command (`./mvnw spring-boot:run`); tests in one (`./mvnw test`).
- [ ] `README.md` — run, test, curls for every operation + duplicate-POST proof + env-var note.
- [ ] `ARCHITECTURE.md` — external_id uniqueness, concurrent POSTs, snapshot, taxes-vs-fees &
      per-type modeling, High volume. *(done — kept in sync with this plan)*
- [ ] All 10 required tests green; grand total `685.70`.
- [ ] No secrets committed; `.db` / `.env` gitignored.
