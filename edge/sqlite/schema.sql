-- Generated: 2026-02-20T17:14:27.368254Z
-- Source DB: /opt/glea-nexo/edge-data/sqlite/edge.db

-- Runtime recommendations:
-- PRAGMA journal_mode=WAL;
-- PRAGMA synchronous=NORMAL;

CREATE TABLE outbox (   id INTEGER PRIMARY KEY AUTOINCREMENT,   message_id TEXT NOT NULL UNIQUE,   ts TEXT NOT NULL,   topic TEXT NOT NULL,   payload TEXT NOT NULL,   sent_at TEXT,   tries INTEGER NOT NULL DEFAULT 0,   last_error TEXT );

