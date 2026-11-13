# Tuần 3 – TV1: bid data layer

## Phạm vi

TV1 triển khai lớp common/database/repository cho đấu giá thật. Branch này
không triển khai `PlaceBidService`, socket dispatcher, realtime event hoặc UI.
Các thành phần đó nhận contract này từ các branch của TV2, TV3 và TV4.

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
