-- Settlement idempotency: persist the request key used by settlement retries.
PRAGMA foreign_keys = ON;

ALTER TABLE settlements ADD COLUMN request_id TEXT;

UPDATE settlements
SET request_id = 'legacy-settlement-' || settlement_id
WHERE request_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_settlements_request_id
    ON settlements(request_id);

CREATE INDEX IF NOT EXISTS idx_settlements_winner
    ON settlements(winner_id, settled_at);
