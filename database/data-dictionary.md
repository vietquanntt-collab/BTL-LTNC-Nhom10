# Data dictionary - schema v1 (finalized for week 5)

The schema is applied in filename order by DatabaseInitializer. Migration filenames are stable identifiers recorded in schema_migrations; V004 must not be renamed after it has been applied.

## Core tables

| Table | Key columns | Meaning and invariants |
|---|---|---|
| `users` | `user_id`, `account`, `role` | Account is unique case-insensitively. `role` is `ADMIN`, `BIDDER` or `SELLER`. Password is a hash only. |
| `wallets` | `user_id`, `balance_cents`, `frozen_cents`, `version` | One wallet per user. `0 <= frozen_cents <= balance_cents`. Money is integer cents. |
| `items` | `item_id`, `seller_id`, `item_type` | Item belongs to a seller. Type is `CARD`, `FIGURE` or `POSTER`. |
| `auctions` | `auction_id`, `item_id`, `status`, `version` | One auction per item. `end_at > start_at`. `current_price_cents >= start_price_cents`. |
| `bids` | `bid_id`, `auction_id`, `bidder_id`, `client_request_id` | Every bid belongs to one auction and bidder. Request id is unique for a bidder retry on an auction. |
| `bid_history` | `history_id`, `bid_id`, `auction_id`, `auction_version` | Append-only audit row for each accepted bid version. One history row belongs to one bid; version is unique and ordered within an auction. |

## Advanced and audit tables

| Table | Key columns | Meaning and invariants |
|---|---|---|
| `auto_bid_rules` | `(auction_id, bidder_id)` | At most one active rule per bidder/auction; max and increment are positive. |
| `settlements` | `auction_id` UNIQUE, `request_id` UNIQUE | At most one settlement per auction/request. This is the idempotency guard for winner/payment. |
| `auction_events` | `(auction_id, sequence)` UNIQUE | Event sequence is monotonic inside one auction; client uses it to ignore stale events. |
| `request_receipts` | `request_id` PRIMARY KEY | Stores the response for a retried request so side effects are not repeated. |

## Index and constraint inventory

| Object | Protection |
|---|---|
| `users.account` UNIQUE COLLATE NOCASE | Prevents duplicate accounts with different case |
| `auctions.item_id` UNIQUE | Prevents a second listing for one item, including after a closed listing |
| `bids(auction_id, bidder_id, client_request_id)` UNIQUE | Makes a bidder retry idempotent |
| `auto_bid_rules(auction_id, bidder_id)` UNIQUE | One rule per bidder and auction |
| `settlements.auction_id` UNIQUE | One settlement per auction |
| `settlements.request_id` UNIQUE | One settlement request receipt |
| `auction_events(auction_id, sequence)` UNIQUE | Monotonic event stream per auction |
| `bid_history(auction_id, auction_version)` UNIQUE | One audit row per accepted version |
| `idx_auctions_status_end` | Scheduler/list queries by status and end time |
| `idx_bids_auction_time`, `idx_bids_auction_amount` | History and winner queries |
| `idx_bid_history_auction_version` | Ordered history reads |
| `idx_auto_bid_auction_active` | Active Auto-Bid evaluation |
| `idx_events_auction_sequence` | Event replay/order |

All foreign keys are enabled per SQLite connection. Money, wallet, status, item type and role invariants are enforced with CHECK constraints.

## State transitions

| From | Event/condition | To |
|---|---|---|
| `OPEN` | first accepted bid inside start/end window | `RUNNING` |
| `OPEN`/`RUNNING` | end time reached | `FINISHED` |
| `FINISHED` | settlement transaction committed | `PAID` |
| `OPEN`/`RUNNING` | authorized cancellation | `CANCELLED` |

PlaceBidService accepts OPEN/RUNNING inside the start/end window and sets RUNNING on accepted bid. Settlement retry returns the recorded result without another payment. See ADR-004 for the implemented contract.

## Integrity checks required in repository tests

1. Foreign keys reject an item with an unknown seller.
2. A second auction for the same item is rejected.
3. Negative money and `frozen_cents > balance_cents` are rejected.
4. A duplicate `(auction_id, bidder_id, client_request_id)` is rejected or returns the saved receipt.
5. A second settlement for one auction does not change balances.

## Week 3 bid write contract

`JdbcBidRepository.placeBid` reads the auction version, checks `RUNNING` and
minimum increment, conditionally updates `current_price_cents` and `version`,
inserts the bid, and appends `bid_history` in one transaction. A stale version
returns a conflict and a repeated `(auction_id, bidder_id, client_request_id)`
returns the original result without inserting a second bid.

## Item images (V005)

image_base64 is optional Base64, at most 512 KiB decoded at the socket boundary. V005 upgrades old databases while preserving existing rows. UPDATE_ITEM without the field preserves it; empty/null clears it. See ItemImageUpgradeTest and NetworkClientTest.
