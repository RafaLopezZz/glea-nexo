SELECT
  state,
  COUNT(*) AS total,
  MIN(next_attempt_at) AS next_due_at,
  MAX(sent_at) AS last_sent_at,
  MAX(ts) AS last_event_ts
FROM outbox
GROUP BY state
ORDER BY CASE state
  WHEN 'PENDING' THEN 1
  WHEN 'SENDING' THEN 2
  WHEN 'FAILED' THEN 3
  WHEN 'DEAD_LETTER' THEN 4
  WHEN 'SENT' THEN 5
  ELSE 99
END;

SELECT
  id,
  message_id,
  state,
  tries,
  ts,
  next_attempt_at,
  sent_at,
  last_error
FROM outbox
WHERE state IN ('PENDING', 'FAILED', 'DEAD_LETTER')
ORDER BY next_attempt_at ASC, id ASC
LIMIT 100;

SELECT
  id,
  message_id,
  state,
  tries,
  substr(last_error, 1, 200) AS last_error,
  next_attempt_at,
  sent_at
FROM outbox
WHERE state = 'FAILED'
ORDER BY tries DESC, next_attempt_at ASC, id ASC
LIMIT 100;
