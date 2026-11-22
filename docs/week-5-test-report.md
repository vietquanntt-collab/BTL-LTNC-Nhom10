# Báo cáo Kiểm thử & Điều phối Rehearsal – Tuần 5 (TV4)

- **Dự án:** Nhóm 10 – Online Auction System
- **Phụ trách:** TV4 (Testing, CI/CD, Release & Documentation)
- **Mục tiêu:** Đóng băng tính năng, xác minh hệ thống chạy ổn định trên máy sạch, hoàn thiện CI (`build / test / quality / package`), xuất artifact và điều phối buổi chạy thử (rehearsal) trước khi nộp bài.

---

## 1. Tổng hợp kết quả Full Regression Testing

Toàn bộ bộ kiểm thử tự động được thực thi qua lệnh `.\mvnw.cmd clean test` trên môi trường sạch (SQLite khởi tạo mới từ thư mục `database/migrations`):

| Module | Test Suite | Số test | Trạng thái | Nội dung kiểm thử chính |
|---|---|---:|---|---|
| `common` | Contract & Serialization Tests | Passed | PASS | Kiểm tra cấu trúc `RequestEnvelope`, `ResponseEnvelope`, `EventEnvelope` và `JsonCodec` |
| `server` | `ServerSmokeTest` | 2 | PASS | Khởi động thành phần cốt lõi của Server |
| `server` | `SessionManagerTest` | 3 | PASS | Tạo, xác thực và hủy phiên đăng nhập (`sessionId`) |
| `server` | `ClientCommandParsingTest` | 2 | PASS | Phân tích cú pháp lệnh JSON từ TCP Socket |
| `server` | `RepositoryIntegrationTest` | 2 | PASS | Kiểm tra migration, schema và ràng buộc SQLite |
| `server` | `BidRepositoryIntegrationTest` | 4 | PASS | Lưu trữ và truy vấn lịch sử đặt giá (`bids`) |
| `server` | `AutoBidSettlementRepositoryIntegrationTest` | 5 | PASS | Truy vấn quy tắc Auto-Bid và dữ liệu tất toán |
| `server` | `PlaceBidServiceTest` | 6 | PASS | Kiểm tra điều kiện giá tối thiểu, bước giá, số dư khả dụng và khóa ví |
| `server` | `BidAuditLogTest` | 2 | PASS | Ghi nhận nhật ký kiểm toán đặt giá |
| `server` | `BidRealtimeEventTest` | 4 | PASS | Phát sự kiện realtime khi có lượt đặt giá mới |
| `server` | `ConcurrencyBidTest` | 3 | PASS | Đặt giá đồng thời từ nhiều luồng đảm bảo không tranh chấp sai lệch |
| `server` | `SocketIntegrationTest` | 1 | PASS | Kết nối TCP Socket đa client cơ bản |
| `server` | `AutoBidTest` | 3 | PASS | Giới hạn giá trần (`maxAmountCents`), tự động đấu giá đối kháng và hủy Auto-Bid |
| `server` | `AntiSnipeAndSchedulerTest` | 3 | PASS | Gia hạn phiên trong vùng cuối (`AntiSnipePolicy`), giới hạn số lần gia hạn và tự động đóng phiên |
| `server` | `SettlementAndEventOrderTest` | 2 | PASS | Tất toán idempotent dưới đa luồng và thứ tự `sequence` sự kiện tăng dần nghiêm ngặt |
| `server` | `AuctionEndToEndFlowTest` | 1 | PASS | Luồng E2E qua TCP Socket thật (`Login -> Create -> Bid -> Outbid -> Extend -> Close & Settle`) |
| `server` | `ManagementSocketIntegrationTest` | 7 | PASS | Phân quyền Admin/Seller, quản lý người dùng, hủy phiên và hoàn tiền đóng băng |

---

## 2. Xác minh chạy trên máy sạch (Clean-Machine Verification)

- [x] Khởi tạo database từ đầu (`database/migrations` + `seed`) không phát sinh lỗi ràng buộc SQLite (`[HEALTH] Open auctions at startup: 1`).
- [x] Server mở cổng `9999` thành công và đóng tài nguyên an toàn khi nhận tín hiệu dừng (`[HEALTH] Auction server stopped cleanly.`).
- [x] Quy trình CI (`.github/workflows/ci.yml`) thực thi đầy đủ 4 bước: **Quality Gate** $\rightarrow$ **Build** $\rightarrow$ **Test** $\rightarrow$ **Package** và xuất ra artifact `nhom10-online-auction-release`.
- [x] Tài liệu `README.md` đã chuẩn hóa hướng dẫn cài đặt JDK 21, build, chạy Server/Client và bảng **Troubleshooting**.

---

## 3. Kịch bản điều phối Rehearsal (Chạy thử toàn nhóm)

### Phân công vai trò trong buổi Rehearsal
- **TV1:** Kiểm tra trạng thái database, dữ liệu seed ban đầu và đối chiếu số dư bảng `wallets` / `auctions` sau khi tất toán.
- **TV2:** Vận hành Server, theo dõi log `[HEALTH]`, kiểm tra xử lý lỗi và tắt/mở lại Server an toàn.
- **TV3:** Thao tác trực tiếp trên giao diện JavaFX Client (Admin, Seller, 2 Bidder) và kiểm tra cập nhật UI realtime.
- **TV4 (Điều phối):** Bấm giờ, đọc kịch bản từng bước, đối chiếu kết quả với mốc nghiệm thu và ghi nhận lỗi phát sinh (nếu có).

### Bảng kịch bản & Nghiệm thu từng bước

| STT | Kịch bản kiểm thử | Thao tác thực hiện | Kết quả kỳ vọng | Trạng thái |
|---|---|---|---|---|
| 1 | **Đăng nhập & Phân quyền** | Đăng nhập lần lượt `admin`, `seller`, `bidder1`, `bidder2` trên các cửa sổ Client riêng biệt | Mỗi tài khoản vào đúng màn hình chức năng theo vai trò; hiển thị đúng số dư ví | [ ] |
| 2 | **Seller tạo vật phẩm & mở phiên** | `seller` tạo vật phẩm mới, sau đó mở phiên đấu giá (giá khởi điểm `100,000`, bước giá `5,000`, thời lượng `1` giờ) | Phiên xuất hiện ở trạng thái `OPEN` trên danh sách đấu giá của tất cả Client | [ ] |
| 3 | **Đấu giá Realtime (2 Bidder)** | `bidder1` đặt `100,000`; `bidder2` đặt `110,000` | Giá hiện tại cập nhật tức thì trên cả 2 màn hình; `bidder1` nhận thông báo `OUTBID` và được hoàn `100,000` frozen; `bidder2` bị đóng băng `110,000` | [ ] |
| 4 | **Auto-Bid** | `bidder1` bật Auto-Bid với giá trần `150,000`; `bidder2` đặt giá `120,000` | Hệ thống tự động đặt giá `125,000` thay cho `bidder1`; khi `bidder2` đặt `155,000`, Auto-Bid dừng lại vì chạm trần | [ ] |
| 5 | **Anti-Snipe** | Mở một phiên có thời gian còn lại dưới ngưỡng Anti-Snipe và thực hiện đặt giá hợp lệ | Thời gian kết thúc (`end_at`) tự động được cộng thêm; sự kiện `AUCTION_EXTENDED` hiển thị trên UI | [ ] |
| 6 | **Đóng phiên & Settlement** | Chờ phiên có người dẫn đầu hết giờ | Trạng thái chuyển `FINISHED` $\rightarrow$ `PAID`; tiền đóng băng của người thắng bị trừ hẳn và cộng vào ví `seller` | [ ] |
| 7 | **Bid History & Biểu đồ** | Mở chi tiết phiên vừa kết thúc | Hiển thị đầy đủ lịch sử các lượt đặt giá theo thứ tự thời gian và biểu đồ biến động giá | [ ] |
| 8 | **Admin quản trị & Hủy phiên** | `admin` xem danh sách người dùng, lọc phiên theo trạng thái và hủy một phiên `OPEN` đang có người đặt giá | Phiên chuyển sang `CANCELLED`; tiền đóng băng của người đang dẫn đầu được hoàn trả ngay lập tức | [ ] |