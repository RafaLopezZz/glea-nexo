-- Generated: 2026-02-20T17:14:27.368254Z
-- Source DB: /opt/glea-nexo/edge-data/sqlite/edge.db

-- Runtime recommendations:
-- PRAGMA journal_mode=WAL;
-- PRAGMA synchronous=NORMAL;

CREATE TABLE outbox (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  message_id TEXT NOT NULL UNIQUE,
  ts TEXT NOT NULL,
  topic TEXT NOT NULL,
  payload TEXT NOT NULL,
  state TEXT NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'DEAD_LETTER')),
  sent_at TEXT,
  tries INTEGER NOT NULL DEFAULT 0 CHECK (tries >= 0),
  last_error TEXT,
  next_attempt_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_outbox_state_next_attempt_at ON outbox (state, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_outbox_sent_at ON outbox (sent_at);

