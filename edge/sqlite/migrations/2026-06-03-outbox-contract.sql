ALTER TABLE outbox ADD COLUMN state TEXT NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'DEAD_LETTER'));
ALTER TABLE outbox ADD COLUMN next_attempt_at TEXT;

UPDATE outbox
SET state = CASE
  WHEN sent_at IS NOT NULL THEN 'SENT'
  WHEN COALESCE(NULLIF(last_error, ''), NULL) IS NOT NULL THEN 'FAILED'
  ELSE 'PENDING'
END
WHERE state IS NULL OR state = '';

UPDATE outbox
SET next_attempt_at = COALESCE(next_attempt_at, ts)
WHERE next_attempt_at IS NULL OR next_attempt_at = '';

CREATE INDEX IF NOT EXISTS idx_outbox_state_next_attempt_at ON outbox (state, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_outbox_sent_at ON outbox (sent_at);
