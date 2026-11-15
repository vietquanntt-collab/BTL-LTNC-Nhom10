# Báo cáo kiểm tra tích hợp tuần 3

## Baseline

- Nguồn kiểm tra: `origin/develop` tại commit `22ea3e8c4453c7e375e812ee36cb8c85b5a77c53`.
- Phạm vi: toàn bộ deliverable tuần 1, tuần 2 và các yêu cầu tuần 3 trong tài liệu BTL.
- Quy trình nhánh: `feature/* -> develop -> main`. Bản kiểm tra này không sửa trực tiếp
  `develop` hoặc `main`.

## Tiêu chí tuần 3

| Nhóm yêu cầu | Kết quả |
|---|---|
| Bảng `bids`, `bid_history`, version và index | Đạt |
| Transaction ghi bid, cập nhật giá/version và lịch sử | Đạt |
| Minimum increment, trạng thái, `start_at`/`end_at` | Đạt sau sửa |
| Idempotency theo `requestId` | Đạt |
| Optimistic locking và per-auction lock | Đạt |
| Chống lost update, bid sau khi đóng | Đạt |
| `BID_ACCEPTED`, `OUTBID`, `BID_REJECTED`, `AUCTION_FINISHED` | Đạt sau sửa sequence/payload |
| Auction detail, countdown, history và chart | Đạt sau sửa socket `GET_AUCTION` |
| Chống double-submit và reconnect | Đạt sau sửa bind channel |
| Test nhiều thread/client và audit log | Có test trong source; chạy lại bằng lệnh bên dưới |

Auto-Bid, Anti-Snipe và settlement đầy đủ là phạm vi tuần 4, vì vậy không dùng
chúng để kết luận thiếu chức năng tuần 3.

## Lỗi tích hợp đã sửa

1. `AuctionSocketServer` đã truyền `PlaceBidService` và `EventPublisher` vào
   `ClientHandler`; request đặt giá không còn rơi vào nhánh “service unavailable”.
2. `ClientHandler` đã xử lý `GET_AUCTION`, trả `auction`, `item` và `bids` cho
   JavaFX detail.
3. `PLACE_BID` nhận `clientRequestId` của payload và trả payload ổn định gồm
   `auctionId`, `bidId`, `bidderId`, `amountCents`, `currentPriceCents`,
   `auctionVersion`, `bidTime`, `status` và `clientRequestId`.
4. Service từ chối bid trước `start_at`.
5. `OUTBID` dùng sequence riêng sau `BID_ACCEPTED`, tránh bị client bỏ qua do
   sequence trùng.
6. Payload event đồng nhất với các trường mà JavaFX controller đọc.
7. Event phát sinh trong lúc xử lý request được xếp hàng để response được gửi
   trước event; channel được bind lại khi request có session sau reconnect.
8. Maven compiler không fork `javac`, giảm lỗi khóa JAR trên Windows/OneDrive.
9. README, CI và tài liệu TV1 đã được cập nhật từ tuần 2 sang baseline tuần 3.

## Kiểm tra nguồn

```powershell
rg -n '^(<<<<<<<|=======|>>>>>>>)' .
git diff --check
```

Hai lệnh trên phải không in conflict marker hoặc whitespace error.

## Build và test

Từ thư mục project, dùng JDK 21:

```powershell
$env:JAVA_HOME = '<duong-dan-toi-JDK-21>'
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\build.ps1
```

Nếu Maven bị `AccessDeniedException` khi xóa `target` hoặc ghi JAR trong cache,
đóng các tiến trình Java/Maven và chạy lại không có `-Clean`. Đây là giới hạn
khóa file của Windows/OneDrive; CI GitHub Actions vẫn là phép kiểm tra sạch.

## Handoff

Bản này được đóng gói độc lập: không chứa `.git`, đường dẫn tuyệt đối, file build
hoặc database cá nhân. Các thành viên chỉ cần clone/copy repository, cài JDK 21
và để Maven Wrapper tự tải dependency.

Sau khi kiểm tra trên máy thành viên, commit các thay đổi lên một branch feature
tuần 3, push branch và mở Pull Request vào `develop`. Chỉ merge vào `develop`
khi CI xanh và đã review; chỉ đưa `develop` vào `main` khi nhóm nghiệm thu.

## Kiểm tra thực tế sau khi chỉnh

- GitHub `origin/develop` đã được fetch và xác nhận tại commit `22ea3e8c4453c7e375e812ee36cb8c85b5a77c53` (merge PR tuần 3).
- `mvnw.cmd -B validate`: PASS.
- `mvnw.cmd -B verify` với JDK 21: PASS; common 15 test, server 27 test, client biên dịch thành công.
- Đã sửa JavaFX `AuctionDetailController`: sự kiện `BID_REJECTED` không làm thay đổi cursor sequence của auction; khi nhận bid mới, bid ACTIVE trước đó được hiển thị thành OUTBID trong bảng realtime.
- Bản bàn giao không chứa `target/`, `.javafx-cache/` hoặc database runtime; Maven Wrapper JAR trong `.mvn/wrapper/` được giữ lại.
- Không còn conflict marker hoặc file rác trong source; bản bàn giao có 117 file nguồn/tài liệu cần thiết.

## Giới hạn phạm vi

Tuần 3 đã đạt mốc đấu giá thật, lịch sử, realtime và concurrency theo kế hoạch nhóm. `REGISTER`, CRUD sửa/xóa item, Auto-Bid, Anti-Snipe và settlement/PAID chưa được triển khai trong baseline này; chúng thuộc phần bắt buộc còn lại hoặc kế hoạch tuần 4 và phải được hoàn thiện trước nghiệm thu cuối kỳ.

