# Data dictionary - schema v1

## Core tables

| Table | Key columns | Meaning and invariants |
|---|---|---|
| `users` | `user_id`, `account`, `role` | Account is unique case-insensitively. `role` is `ADMIN`, `BIDDER` or `SELLER`. Password is a hash only. |
| `wallets` | `user_id`, `balance_cents`, `frozen_cents`, `version` | One wallet per user. `0 <= frozen_cents <= balance_cents`. Money is integer cents. |
| `items` | `item_id`, `seller_id`, `item_type` | Item belongs to a seller. Type is `CARD`, `FIGURE` or `POSTER`. |
| `auctions` | `auction_id`, `item_id`, `status`, `version` | One auction per item. `end_at > start_at`. `current_price_cents >= start_price_cents`. |
| `bids` | `bid_id`, `auction_id`, `bidder_id`, `client_request_id` | Every bid belongs to one auction and bidder. Request id is unique for a bidder retry on an auction. |

## Advanced and audit tables

| Table | Key columns | Meaning and invariants |
|---|---|---|
| `auto_bid_rules` | `(auction_id, bidder_id)` | At most one active rule per bidder/auction; max and increment are positive. |
| `settlements` | `auction_id` UNIQUE | At most one settlement per auction. This is the idempotency guard for winner/payment. |
| `auction_events` | `(auction_id, sequence)` UNIQUE | Event sequence is monotonic inside one auction; client uses it to ignore stale events. |
| `request_receipts` | `request_id` PRIMARY KEY | Stores the response for a retried request so side effects are not repeated. |

## State transitions

| From | Event/condition | To |
|---|---|---|
| `OPEN` | start time reached | `RUNNING` |
| `RUNNING` | end time reached | `FINISHED` |
| `FINISHED` | settlement transaction committed | `PAID` |
| `OPEN`/`RUNNING` | authorized cancellation | `CANCELLED` |

No code may place a bid unless the auction is `RUNNING`. No code may settle an auction that is already `PAID` or has a row in `settlements`.

## Integrity checks required in repository tests

1. Foreign keys reject an item with an unknown seller.
2. A second auction for the same item is rejected.
3. Negative money and `frozen_cents > balance_cents` are rejected.
4. A duplicate `(auction_id, bidder_id, client_request_id)` is rejected or returns the saved receipt.
5. A second settlement for one auction does not change balances.
