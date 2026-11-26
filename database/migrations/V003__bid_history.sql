-- Week 3 bid audit history. The table is append-only: one row per
-- successfully committed auction version.
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS bid_history (
    history_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    bid_id             INTEGER NOT NULL,
    auction_id         INTEGER NOT NULL,
    bidder_id          INTEGER NOT NULL,
    amount_cents       INTEGER NOT NULL CHECK (amount_cents > 0),
    bid_time           TEXT NOT NULL,
    status             TEXT NOT NULL CHECK (status IN ('ACTIVE', 'OUTBID', 'WINNER', 'REJECTED')),
    client_request_id  TEXT NOT NULL,
    auction_version    INTEGER NOT NULL CHECK (auction_version > 0),
    recorded_at        TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (bid_id) REFERENCES bids(bid_id) ON DELETE CASCADE,
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    UNIQUE (bid_id),
    UNIQUE (auction_id, auction_version)
);

CREATE INDEX IF NOT EXISTS idx_bid_history_auction_version
    ON bid_history(auction_id, auction_version);
CREATE INDEX IF NOT EXISTS idx_bid_history_auction_time
    ON bid_history(auction_id, bid_time, history_id);
