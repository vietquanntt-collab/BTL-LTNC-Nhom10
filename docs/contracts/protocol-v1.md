# Protocol v1

## Transport

- TCP, UTF-8, one JSON object per line.
- Default server port: `9999` (configurable via `auction.server.port`).
- Client must tolerate reconnect and duplicate response; server must use `requestId` for idempotency.
- Timestamps are ISO-8601 UTC, for example `2026-10-22T12:30:00Z`.
- Money fields end in `Cents` and are signed 64-bit integer values.

## Request actions

| Action | Required payload | Authorization |
|---|---|---|
| `REGISTER` | `account`, `password` | public |
| `LOGIN` | `account`, `password` | public |
| `LOGOUT` | empty | authenticated |
| SESSION | empty; current sessionId | authenticated, refresh wallet and rebind observer |
| PING / HEALTH | empty | public |
| `LIST_AUCTIONS` | optional `status`, `page`, `pageSize` | authenticated |
| `GET_AUCTION` | `auctionId` | authenticated |
| `CREATE_ITEM` | `name`, `description`, `itemType`, `imageBase64?` | Seller |
| `LIST_ITEMS` | empty | Seller |
| `UPDATE_ITEM` | `itemId`, changed fields | owning Seller/Admin |
| `DELETE_ITEM` | `itemId` | owning Seller/Admin |
| `CREATE_AUCTION` | `itemId`, `startPriceCents`, `minIncrementCents`, `durationHours` | owning Seller |
| `PLACE_BID` | `auctionId`, `amountCents`, `clientRequestId?` | Bidder |
| LIST_BIDS | auctionId | authenticated |
| CANCEL_AUTO_BID | auctionId, clientRequestId? | Bidder |
| `SET_AUTO_BID` | `auctionId`, `maxAmountCents`, `incrementCents` | authenticated bidder |
| `CANCEL_AUCTION` | `auctionId` | owning Seller/Admin |
| `SETTLE_AUCTION` | `auctionId`, `clientRequestId?` | owning Seller/Admin |
| `ADMIN_LIST_USERS` | `role?`, `account?`, `page?`, `pageSize?` | Admin |

## Response envelope example

~~~json
{
  "protocolVersion": "v1",
  "requestId": "request-example",
  "action": "PLACE_BID",
  "ok": true,
  "errorCode": "NONE",
  "message": "OK",
  "sessionId": "session-example",
  "payloadJson": "{\"auctionId\":10,\"bidId\":45,\"currentPriceCents\":125000,\"auctionVersion\":7}"
}
~~~

## Event types

`BID_ACCEPTED`, `OUTBID`, `BID_REJECTED`, `AUCTION_EXTENDED`, `AUCTION_FINISHED`, `AUCTION_SETTLED`, `AUCTION_CANCELLED`, `BALANCE_CHANGED`, `ERROR`.

Every auction event includes `eventId`, `auctionId`, `sequence`, `occurredAt` and a payloadJson string. Clients ignore a sequence less than or equal to the last applied sequence for that auction.

## Error codes

`NONE`, `INVALID_REQUEST`, `AUTHENTICATION_FAILED`, `FORBIDDEN`, `NOT_FOUND`, `AUCTION_NOT_OPEN`, `BID_TOO_LOW`, `INSUFFICIENT_BALANCE`, `CONFLICT`, `DUPLICATE_REQUEST`, `VALIDATION_ERROR`, `INTERNAL_ERROR`.

## Compatibility rule

`AUCTION_CANCELLED` is emitted only after the cancellation transaction commits; a targeted `BALANCE_CHANGED` follows when frozen funds are returned. Field names and enum values in v1 are stable for the current five-week milestone. Any breaking change requires `v2`, an ADR update and an adapter period; do not silently change a shared DTO.

## Image and session details

CREATE_ITEM accepts optional Base64 imageBase64 up to 512 KiB decoded bytes. UPDATE_ITEM omitting the field preserves it; empty string or null clears it. Listed items cannot be edited/deleted. ItemType is CARD, FIGURE or POSTER.

SESSION returns payloadJson with balanceCents and frozenCents and binds the current socket observer to the authenticated user. Tokens exist in server memory; restart invalidates them. Reconnect does not replay old events: client refreshes details and balances. CANCEL_AUTO_BID delegates SET_AUTO_BID deactivation, so its response action is SET_AUTO_BID; correlate by requestId.

Idempotent receipts apply to bid/Auto-Bid/settlement, not registration or item/auction creation.

## Minimum bid rule

Every accepted bid, including the first one, must be at least currentPriceCents + minIncrementCents. A new auction initializes currentPriceCents to startPriceCents. Auto-Bid uses max(auction increment, rule increment) and never exceeds maxAmountCents. The enum CANCELLED corresponds to the cancelled/canceled state in the assignment.
