# Tuần 3 – TV1: bid data layer

## Phạm vi

TV1 triển khai lớp common/database/repository cho đấu giá thật. Bản tích hợp
tuần 3 cũng nối các contract này vào socket, service đặt giá và JavaFX detail
để kiểm tra đường chạy end-to-end. Auto-Bid, Anti-Snipe và settlement đầy đủ
được để cho tuần 4.

## Contract

- `PlaceBidCommand` chứa `auctionId`, `bidderId`, số tiền cents, `clientRequestId`
  và `expectedAuctionVersion`.
- `BidWriteResult` trả bid vừa ghi, version sau khi commit và giá hiện tại.
- `BidHistoryDto` biểu diễn một bản ghi append-only theo version của auction.
- `BidRejectedException` giữ `ErrorCode` để service/socket map thành response.

## Schema

Migration `V003__bid_history.sql` tạo bảng `bid_history` và hai index:

- `(auction_id, auction_version)` duy nhất để không có hai bid cùng version.
- `(auction_id, bid_time, history_id)` để đọc lịch sử ổn định theo thời gian.
- `bid_id` duy nhất để mỗi bid chỉ có một bản ghi audit.

Bảng `bids` và `auctions.version` của baseline được giữ nguyên. Seed tuần 3 có
hai bidder, hai mức giá và hai history version để demo/repository test có dữ liệu
thật.

## Transaction và optimistic version

`JdbcBidRepository.placeBid` thực hiện trong một transaction:

1. Tìm request cũ theo `(auction_id, bidder_id, client_request_id)`; nếu có,
   trả lại bid, version và giá của lần ghi ban đầu, không ghi thêm dòng.
2. Đọc auction và kiểm tra `RUNNING`, `end_at`, version và minimum increment.
3. `UPDATE auctions ... WHERE version = expectedVersion` để chống lost update.
4. Đánh dấu bid ACTIVE trước đó là `OUTBID`, thêm bid mới và thêm history.
5. Commit toàn bộ; mọi lỗi rollback.

Nếu version đã cũ, trả `CONFLICT`. Nếu giá thấp hơn current price cộng minimum
increment, trả `BID_TOO_LOW`. Auction không ở trạng thái `RUNNING` trả
`AUCTION_NOT_OPEN`.

## Kiểm tra

```powershell
.\scripts\build.ps1
```

Test mới trong `server/src/test/.../BidRepositoryIntegrationTest.java` bao phủ:

- version/history tăng theo thứ tự;
- duplicate request idempotent;
- bid ACTIVE trước đó chuyển thành OUTBID;
- minimum increment và stale version;
- auction đã đóng không nhận bid.

Các kiểm tra tích hợp của tuần 3 còn bao gồm:

- `ConcurrencyBidTest`: nhiều client cùng version, retry tăng version tuần tự và
  request trùng không tạo bản ghi thừa.
- `BidRealtimeEventTest`: `BID_ACCEPTED`, `OUTBID`, `BID_REJECTED`,
  `AUCTION_FINISHED` và thứ tự event qua socket.
- `BidAuditLogTest`: audit record có `requestId`, `auctionId` và `userId`.
