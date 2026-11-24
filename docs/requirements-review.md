# Đối chiếu đề BTL và hướng dẫn cập nhật

Đọc ngày 2026-11-24 từ hai PDF ở thư mục cha của checkout. Nội dung cả 10/7 trang khớp bản trích xuất đã dùng để audit; không còn kết luận file PDF rỗng.

| Nguồn | Số trang PDF | Dung lượng | SHA-256 |
|---|---:|---:|---|
| 2026-BTL.pdf | 10 | 178948 byte | 64e3212b70cf69395e49a75eb55648b290a0937937e928b40d9bac53de0e71dd |
| Hướng dẫn BTL LTNC-2026.pdf | 7 | 235204 byte | 7c26b6f173ee7fb09562e343c666efc5b38b16e00608a65d07765651306ca9e4 |

Tuần 1–5 của kế hoạch nội bộ và tuần 6–15 của lịch môn học là hai mốc khác nhau. Không kết luận hoàn tất bài nộp chỉ vì xong milestone tuần 5.

## Yêu cầu và bằng chứng trong code

| Yêu cầu | Thực hiện và test | Kết luận local |
|---|---|---|
| Bidder/Seller/Admin; đăng ký/đăng nhập | AuthService, SessionManager, role domain, ClientHandler; management/socket/UI tests | Qua các luồng đã test; tài khoản đăng ký mới là Bidder |
| CRUD sản phẩm; tên/mô tả, giá và thời gian phiên | JdbcItemRepository, JdbcAuctionRepository, SellerDashboard; repository/management/client tests | Qua; item đã gắn phiên bị khóa sửa/xóa để giữ lịch sử |
| Giá cao hơn hiện tại, kiểm hợp lệ, người dẫn đầu | PlaceBidService + Auction.minimumBidCents; PlaceBidServiceTest gồm lượt đầu bằng/thiếu/đủ bước giá | Giá tối thiểu = hiện tại + bước giá cho mọi lượt |
| Tự đóng, xác định người thắng, chuyển trạng thái | Scheduler, SettlementService; e2e, AntiSnipeAndSchedulerTest, SettlementAndEventOrderTest | OPEN -> RUNNING ở bid đầu; hết giờ -> FINISHED -> PAID. Có CANCELLED khi hủy; không có winner thì settlement = 0 |
| Xử lý bid thấp/phiên đóng/dữ liệu/kết nối | ValidationException, BidRejectedException, ErrorCode và response; socket/reconnect/closed UI tests | Qua các kịch bản hiện có |
| JavaFX + FXML + MVC; các màn hình chính | controller/view, NetworkClient, server routes/services/repository | Test tải FXML, login/list/detail/Seller/Admin và state |
| Entity/User/Item/Auction/BidTransaction | server/domain; User -> Bidder/Seller/Admin; Item -> Card/Figure/Poster | 7 DomainTest cùng test tích hợp. Các nhóm sản phẩm trong đề là ví dụ; nhóm dùng catalogue sưu tầm |
| Encapsulation/inheritance/polymorphism/abstraction | private final + getters, abstract Entity/User/Item, subtype override và role policies | Có trong luồng server; class diagram khớp source |
| Singleton/Factory Method/Observer | NetworkClient/NavigationManager; ItemFactory concrete creators; EventPublisher/socket observers | Có code sử dụng thực tế và test |
| Client–Server, JSON Socket, chỉ server truy cập DB | common contract; server JDBC; client server dependency chỉ scope=test | client.jar không chứa server/SQLite |
| Concurrency không lost update/hai winner | databaseLock, ReentrantLock, transaction/version, receipt | ConcurrencyBidTest/ServerHardeningTest; phạm vi một server process |
| Realtime đến client qua Observer/Socket | EventPublisher -> SocketClientObserver -> client listeners | Có test sự kiện/sequence và client; nhóm cần demo nhiều cửa sổ/LAN |
| Maven, refactor, coding convention | Maven Wrapper multimodule; Checkstyle validate | 0 violation theo bộ baseline. Chưa chứng nhận toàn bộ Google Java Style |
| JUnit; coverage >=60% từ tuần 10 | JUnit5 + JaCoCo line gate 60% từng module | Xem verification-results.md cho số liệu; cần -Ui để đo toàn client |
| GitHub Actions tự build/test khi push | Windows/Linux, Xvfb, report/coverage/UI/JAR artifacts | Cấu hình local; chờ chạy trên commit do nhóm push |
| Auto-Bid maxBid/increment/PriorityQueue/tie-break | PlaceBidService + AutoBidRuleRepository | Có test trần, đối thủ, thời điểm ưu tiên và tắt rule |
| Anti-Snipe | AntiSnipePolicy + scheduler | Có test cửa sổ/gia hạn/tối đa số lần |
| Bid History Visualization | LineChart ở AuctionDetail + realtime events | UI flow/chart được chạy; cần nghiệm thu thị giác trên máy nhóm |

Ba chức năng nâng cao là tùy chọn, tổng tối đa +1.5 theo hướng dẫn; chưa thể cam kết điểm chấm từ test local. Đề/hướng dẫn không yêu cầu phải dùng Mockito; JUnit với DB/socket thật đang là bằng chứng kiểm thử.

## Các việc ngoài build còn cần nhóm làm

- Xác nhận nguồn/attribution của baseline Nhóm 8 và ảnh theo source-map.md.
- Chạy GitHub Actions trên commit mới, review chéo code và xử lý phát hiện của nhóm.
- Test bằng tay nhiều client/LAN, DPI 100%/125%, bàn phím, màn hình và trường hợp nội dung dài theo local-testing.md.
- Chuẩn bị slide/báo cáo, ghi đóng góp thực tế, rehearsal demo; mỗi thành viên phải hiểu toàn bộ mã nguồn như yêu cầu PDF.
