-- Nhóm 10 Online Auction System - core schema v1
-- SQLite local. Money is integer cents; timestamps are ISO-8601 UTC text.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
    user_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    account          TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash    TEXT NOT NULL,
    role             TEXT NOT NULL CHECK (role IN ('ADMIN', 'BIDDER', 'SELLER')),
    created_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE IF NOT EXISTS wallets (
    user_id          INTEGER PRIMARY KEY,
    balance_cents    INTEGER NOT NULL DEFAULT 0 CHECK (balance_cents >= 0),
    frozen_cents     INTEGER NOT NULL DEFAULT 0 CHECK (frozen_cents >= 0 AND frozen_cents <= balance_cents),
    version          INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS items (
    item_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    seller_id        INTEGER NOT NULL,
    name             TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 200),
    description      TEXT NOT NULL DEFAULT '',
    item_type        TEXT NOT NULL CHECK (item_type IN ('CARD', 'FIGURE', 'POSTER')),
    image_base64     TEXT,
    created_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at       TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    FOREIGN KEY (seller_id) REFERENCES users(user_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS auctions (
    auction_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    item_id             INTEGER NOT NULL UNIQUE,
    start_price_cents   INTEGER NOT NULL CHECK (start_price_cents >= 0),
    current_price_cents INTEGER NOT NULL CHECK (current_price_cents >= start_price_cents),
    min_increment_cents INTEGER NOT NULL CHECK (min_increment_cents > 0),
    start_at            TEXT NOT NULL,
    end_at              TEXT NOT NULL,
    status              TEXT NOT NULL CHECK (status IN ('OPEN', 'RUNNING', 'FINISHED', 'PAID', 'CANCELLED')),
    version             INTEGER NOT NULL DEFAULT 0,
    extension_count     INTEGER NOT NULL DEFAULT 0 CHECK (extension_count >= 0),
    winner_id           INTEGER,
    closed_at           TEXT,
    created_at          TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    CHECK (julianday(end_at) > julianday(start_at)),
    FOREIGN KEY (item_id) REFERENCES items(item_id) ON DELETE RESTRICT,
    FOREIGN KEY (winner_id) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS bids (
    bid_id             INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id         INTEGER NOT NULL,
    bidder_id          INTEGER NOT NULL,
    amount_cents       INTEGER NOT NULL CHECK (amount_cents > 0),
    bid_time           TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    status             TEXT NOT NULL CHECK (status IN ('ACTIVE', 'OUTBID', 'WINNER', 'REJECTED')),
    client_request_id  TEXT NOT NULL,
    FOREIGN KEY (auction_id) REFERENCES auctions(auction_id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id) REFERENCES users(user_id) ON DELETE RESTRICT,
    UNIQUE (auction_id, bidder_id, client_request_id)
);

CREATE INDEX IF NOT EXISTS idx_auctions_status_end ON auctions(status, end_at);
CREATE INDEX IF NOT EXISTS idx_bids_auction_time ON bids(auction_id, bid_time);
CREATE INDEX IF NOT EXISTS idx_bids_auction_amount ON bids(auction_id, amount_cents DESC, bid_time ASC);
CREATE INDEX IF NOT EXISTS idx_items_seller ON items(seller_id);
