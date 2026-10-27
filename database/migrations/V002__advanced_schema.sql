-- Nhóm 10 Online Auction System - advanced and audit tables
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS auto_bid_rules (
    rule_id            INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id         INTEGER NOT NULL,
    bidder_id          INTEGER NOT NULL,
    max_amount_cents   INTEGER NOT NULL CHECK (max_amount_cents > 0),
    increment_cents    INTEGER NOT NULL CHECK (increment_cents > 0),
    registered_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    active              INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE (auction_id, bidder_id)
);

CREATE TABLE IF NOT EXISTS settlements (
    settlement_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id          INTEGER NOT NULL UNIQUE,
    winner_id           INTEGER,
    seller_id           INTEGER NOT NULL,
    winning_amount_cents INTEGER NOT NULL DEFAULT 0 CHECK (winning_amount_cents >= 0),
    settled_at           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (winner_id) REFERENCES users(user_id) ON DELETE SET NULL,
    FOREIGN KEY (seller_id) REFERENCES users(user_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS auction_events (
    event_id            INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id          INTEGER NOT NULL,
    sequence            INTEGER NOT NULL,
    event_type          TEXT NOT NULL,
    payload_json        TEXT NOT NULL,
    occurred_at         TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    UNIQUE (auction_id, sequence)
);

CREATE TABLE IF NOT EXISTS request_receipts (
    request_id          TEXT PRIMARY KEY,
    action              TEXT NOT NULL,
    response_json       TEXT NOT NULL,
    created_at           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX IF NOT EXISTS idx_auto_bid_auction_active ON auto_bid_rules(auction_id, active, registered_at);
CREATE INDEX IF NOT EXISTS idx_events_auction_sequence ON auction_events(auction_id, sequence);
