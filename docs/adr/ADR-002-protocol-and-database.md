# ADR-002: Protocol v1, tiền và cơ sở dữ liệu local

- **Status:** Accepted
- **Date:** 2026-10-22
- **Owner:** TV1
- **Reviewers:** TV2, TV3, TV4

## Decision

### Protocol

TCP message là JSON UTF-8, một message mỗi dòng. Mỗi message thuộc một trong ba envelope ở `common`:

- `RequestEnvelope`: `protocolVersion`, `requestId`, `action`, `sessionId`, `payload`.
- `ResponseEnvelope`: `protocolVersion`, `requestId`, `action`, `ok`, `errorCode`, `message`, `sessionId`, `payload`.
- `EventEnvelope`: `protocolVersion`, `eventId`, `eventType`, `auctionId`, `sequence`, `occurredAt`, `payload`.

`payload` là JSON object được serialize bởi module network. `requestId` phải được client tạo và server ghi nhận để retry không tạo bid/settlement trùng. `sequence` tăng đơn điệu trong từng auction; client bỏ qua event cũ hơn.

### Database

- Đường chạy mặc định: SQLite local với migration `database/migrations`.
- Tất cả tiền dùng **integer cents**, không dùng `double` hoặc floating point.
- Thời gian lưu ISO-8601 UTC dạng text để SQLite và server cùng đọc được.
- Foreign key bật ở mỗi connection; schema dùng CHECK/UNIQUE để bảo vệ invariant.
- `settlements.auction_id` là khóa duy nhất để settlement idempotent.
- `bids.client_request_id` là khóa duy nhất trong phạm vi auction/bidder request để chống retry trùng.
- `auction_events(auction_id, sequence)` duy nhất để bảo toàn thứ tự broadcast.

### State machine

```text
OPEN -> RUNNING -> FINISHED -> PAID
  └──────────────────────────> CANCELLED
```

- `OPEN`: đã tạo, chờ `startAt`.
- `RUNNING`: nhận bid.
- `FINISHED`: hết giờ, không nhận bid, chờ settlement.
- `PAID`: settlement hoàn tất, có thể có hoặc không có winner.
- `CANCELLED`: admin/seller hủy theo policy.

## Consequences

- DAO của server phải dùng SQL phù hợp với migration SQLite và được bao bọc trong transaction.
- Nếu dùng cloud DB sau này, phải giữ semantics cents, state và unique constraint; thêm migration riêng, không sửa seed local.
- Per-auction `ReentrantLock` vẫn cần cho một server process; transaction/conditional update là lớp bảo vệ dữ liệu cuối cùng.
