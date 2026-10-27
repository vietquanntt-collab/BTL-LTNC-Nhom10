# Protocol v1

## Transport

- TCP, UTF-8, one JSON object per line.
- Default server port: `9090` (configurable via `SERVER_PORT`).
- Client must tolerate reconnect and duplicate response; server must use `requestId` for idempotency.
- Timestamps are ISO-8601 UTC, for example `2026-10-22T12:30:00Z`.
- Money fields end in `Cents` and are signed 64-bit integer values.

## Request actions

| Action | Required payload | Authorization |
|---|---|---|
| `REGISTER` | `account`, `password` | public |
| `LOGIN` | `account`, `password` | public |
| `LOGOUT` | empty | authenticated |
| `LIST_AUCTIONS` | optional `status`, `page`, `pageSize` | authenticated |
| `GET_AUCTION` | `auctionId` | authenticated |
| `CREATE_ITEM` | `name`, `description`, `type`, `imageBase64?` | Seller |
| `UPDATE_ITEM` | `itemId`, changed fields | owning Seller/Admin |
| `DELETE_ITEM` | `itemId` | owning Seller/Admin |
| `CREATE_AUCTION` | `itemId`, `startPriceCents`, `minIncrementCents`, `startAt`, `endAt` | owning Seller |
| `PLACE_BID` | `auctionId`, `amountCents` | Bidder/Seller allowed by policy |
| `SET_AUTO_BID` | `auctionId`, `maxAmountCents`, `incrementCents` | authenticated bidder |
| `CANCEL_AUCTION` | `auctionId` | Admin/policy owner |
| `ADMIN_LIST_USERS` | page/filter | Admin |

## Response envelope example

```json
{
  "protocolVersion": "v1",
  "requestId": "8bb8c1c0-...",
  "action": "PLACE_BID",
  "ok": true,
  "errorCode": "NONE",
  "message": "Bid accepted",
  "sessionId": "session-...",
  "payload": {
    "auctionId": 10,
    "bidId": 45,
    "amountCents": 125000,
    "currentPriceCents": 125000,
    "auctionVersion": 7
  }
}
```

## Event types

`BID_ACCEPTED`, `OUTBID`, `BID_REJECTED`, `AUCTION_EXTENDED`, `AUCTION_FINISHED`, `AUCTION_SETTLED`, `BALANCE_CHANGED`, `ERROR`.

Every auction event includes `eventId`, `auctionId`, `sequence`, `occurredAt` and a payload. Clients ignore a sequence less than or equal to the last applied sequence for that auction.

## Error codes

`NONE`, `INVALID_REQUEST`, `AUTHENTICATION_FAILED`, `FORBIDDEN`, `NOT_FOUND`, `AUCTION_NOT_OPEN`, `BID_TOO_LOW`, `INSUFFICIENT_BALANCE`, `CONFLICT`, `DUPLICATE_REQUEST`, `VALIDATION_ERROR`, `INTERNAL_ERROR`.

## Compatibility rule

Field names and enum values in v1 are stable for the current five-week milestone. Any breaking change requires `v2`, an ADR update and an adapter period; do not silently change a shared DTO.
