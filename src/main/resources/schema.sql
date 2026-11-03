CREATE TABLE IF NOT EXISTS transactions (
  id            TEXT PRIMARY KEY,
  external_id   TEXT NOT NULL,
  type          TEXT NOT NULL,
  currency      TEXT NOT NULL,
  occurred_at   TEXT NOT NULL,
  total_minor   INTEGER NOT NULL,
  metadata_json TEXT NOT NULL,
  created_at    TEXT NOT NULL,
  updated_at    TEXT NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_external_id ON transactions(external_id);
CREATE INDEX IF NOT EXISTS ix_transactions_created ON transactions(created_at, id);

CREATE TABLE IF NOT EXISTS tax_lines (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  transaction_id TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
  name           TEXT NOT NULL,
  rate           TEXT,
  amount_minor   INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_tax_lines_txn ON tax_lines(transaction_id);

CREATE TABLE IF NOT EXISTS fee_lines (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  transaction_id TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
  name           TEXT NOT NULL,
  amount_minor   INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_fee_lines_txn ON fee_lines(transaction_id);

CREATE TABLE IF NOT EXISTS invoices (
  id              TEXT PRIMARY KEY,
  currency        TEXT NOT NULL,
  total_minor     INTEGER NOT NULL,
  tax_total_minor INTEGER NOT NULL,
  fee_total_minor INTEGER NOT NULL,
  created_at      TEXT NOT NULL,
  updated_at      TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_invoices_created ON invoices(created_at, id);

CREATE TABLE IF NOT EXISTS invoice_lines (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_id            TEXT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  source_transaction_id TEXT NOT NULL,
  type                  TEXT NOT NULL,
  currency              TEXT NOT NULL,
  total_minor           INTEGER NOT NULL,
  snapshot_json         TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_invoice_lines_txn ON invoice_lines(source_transaction_id);
CREATE INDEX IF NOT EXISTS ix_invoice_lines_inv ON invoice_lines(invoice_id);

CREATE TABLE IF NOT EXISTS invoice_tax_rollup (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_id   TEXT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  rate         TEXT,
  amount_minor INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_tax_rollup_inv ON invoice_tax_rollup(invoice_id);

CREATE TABLE IF NOT EXISTS invoice_fee_rollup (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_id   TEXT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  name         TEXT NOT NULL,
  amount_minor INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_fee_rollup_inv ON invoice_fee_rollup(invoice_id);
