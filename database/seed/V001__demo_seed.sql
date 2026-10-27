-- Demo seed for a clean local database.
-- Password hashes use PBKDF2-HMAC-SHA256, 120000 iterations, unique demo salts.
PRAGMA foreign_keys = ON;

INSERT OR IGNORE INTO users(user_id, account, password_hash, role) VALUES
  (1, 'admin',   'pbkdf2_sha256$120000$dGVhbTQtYWRtaW4tc2FsdA==$rjgWIcGHPKJ/KQM5nGTc0gfE0h4uHFHSlMV+eaoPW4Q=', 'ADMIN'),
  (2, 'seller',  'pbkdf2_sha256$120000$dGVhbTQtc2VsbGVyLXNhbHQ=$pFcLqpF4i4gsbmnfXVIV7obHhqu9ybb4Og0vGEfJfzc=', 'SELLER'),
  (3, 'bidder1', 'pbkdf2_sha256$120000$dGVhbTQtYmlkZGVyMS1zYWx0$c4bThLaTK1oqTHhjBB6F91B445jX9mw6y79hm5JBR/4=', 'BIDDER'),
  (4, 'bidder2', 'pbkdf2_sha256$120000$dGVhbTQtYmlkZGVyMi1zYWx0$LorGNqA0jpLTaEw7bisCkxkWx0ZTa0CZvuiT1D8nCPM=', 'BIDDER');

INSERT OR IGNORE INTO wallets(user_id, balance_cents, frozen_cents) VALUES
  (1, 0, 0), (2, 0, 0), (3, 2000000, 0), (4, 2000000, 0);

INSERT OR IGNORE INTO items(item_id, seller_id, name, description, item_type)
VALUES (1, 2, 'Demo trading card', 'Seed item for local demo', 'CARD');

INSERT OR IGNORE INTO auctions(
  auction_id, item_id, start_price_cents, current_price_cents,
  min_increment_cents, start_at, end_at, status
)
VALUES (
  1, 1, 100000, 100000, 5000,
  strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-5 minutes'),
  strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '+2 hours'),
  'RUNNING'
);
