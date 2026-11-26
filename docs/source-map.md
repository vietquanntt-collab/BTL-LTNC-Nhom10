# Bản đồ mã nguồn và phân công kiểm tra

Gala Auction được tổ chức thành ba module Maven: common, server và client. Tài liệu này giúp nhóm tìm đúng phần triển khai, xác định bộ kiểm thử liên quan và phân công review trước khi bàn giao.

## Cấu trúc dự án

~~~text
common/     DTO, enum, JSON codec và contract giao thức
server/     Domain, xác thực, socket, đấu giá và truy cập SQLite
client/     JavaFX/FXML, điều hướng, kết nối và trạng thái giao diện
database/   Migration, dữ liệu demo, ERD và data dictionary
config/     Cấu hình mẫu và quy tắc Checkstyle
scripts/    Build, chạy ứng dụng và tạo thư mục release
docs/       Kiến trúc, yêu cầu, kịch bản test và báo cáo bàn giao
~~~

Các package Java chính nằm trong com.btl.team10. Đường dẫn ở bảng dưới tính từ thư mục gốc repository.

## Chức năng, mã nguồn và kiểm thử

| Khu vực | Mã nguồn chính | Bằng chứng kiểm thử | Phụ trách kiểm tra |
|---|---|---|---|
| Đăng ký, đăng nhập và session | server/src/main/java/com/btl/team10/server/session/; server/src/main/java/com/btl/team10/server/network/ClientHandler.java | SessionManagerTest, ManagementSocketIntegrationTest, NetworkClientTest | TV2 |
| Domain OOP và Factory Method | server/src/main/java/com/btl/team10/server/domain/; server/src/main/java/com/btl/team10/server/repository/mapper/ | DomainTest, RepositoryIntegrationTest, PlaceBidServiceTest | TV1 |
| Schema, seed và repository | database/migrations/; database/seed/; server/src/main/java/com/btl/team10/server/persistence/; server/src/main/java/com/btl/team10/server/repository/ | RepositoryIntegrationTest, ItemImageUpgradeTest, BidRepositoryIntegrationTest | TV1 + TV2 |
| Vật phẩm và ảnh | JdbcItemRepository, ItemRowMapper, ClientHandler; client/src/main/java/com/btl/team10/client/controller/SellerDashboardController.java | ManagementSocketIntegrationTest, NetworkClientTest, ClientUiTest | TV1 + TV3 |
| Đặt giá, lịch sử và concurrency | server/src/main/java/com/btl/team10/server/bidding/PlaceBidService.java; server/src/main/java/com/btl/team10/server/event/; JdbcBidRepository | PlaceBidServiceTest, ConcurrencyBidTest, BidAuditLogTest, BidRealtimeEventTest | TV2 + TV4 |
| Auto-Bid, Anti-Snipe và settlement | server/src/main/java/com/btl/team10/server/bidding/; JdbcAutoBidRuleRepository, JdbcSettlementRepository, JdbcWalletRepository | AutoBidTest, AntiSnipeAndSchedulerTest, SettlementAndEventOrderTest, AuctionEndToEndFlowTest | TV2 + TV4 |
| Giao thức TCP/JSON | common/src/main/java/com/btl/team10/common/protocol/; server/src/main/java/com/btl/team10/server/network/; client/src/main/java/com/btl/team10/client/service/NetworkClient.java | ProtocolContractTest, ContractValidationTest, SocketIntegrationTest, NetworkClientTest | TV2 + TV3 |
| Giao diện và vòng đời client | client/src/main/java/com/btl/team10/client/controller/; client/src/main/java/com/btl/team10/client/navigation/; client/src/main/resources/com/btl/team10/client/ | ClientUiTest, NetworkClientTest; checklist màn hình/DPI/LAN | TV3 |
| Build, coverage và phát hành | pom.xml; common/pom.xml; server/pom.xml; client/pom.xml; config/checkstyle/; scripts/; .github/workflows/ci.yml | Maven clean verify, JaCoCo, Checkstyle, smoke test JAR và GitHub Actions | TV4 |

## Các điểm tích hợp cần nắm

- Migration V005 bổ sung cột ảnh cho database cũ. ItemImageUpgradeTest kiểm giữ dữ liệu và chạy nâng cấp lặp lại.
- UPDATE_ITEM giữ ảnh khi thiếu field, thay ảnh khi có dữ liệu và xóa ảnh khi nhận empty/null; server kiểm Base64 và giới hạn kích thước.
- SESSION trả số dư và tiền đang giữ, đồng thời gắn lại observer khi client nối lại. Restart server làm hết hiệu lực session lưu trong RAM.
- Mức đặt giá tối thiểu luôn là currentPriceCents + minIncrementCents. Auto-Bid, ví và settlement tuân theo cùng contract.
- Domain Entity/User/Item, các subtype, Auction/BidTransaction và ItemFactory được sử dụng trong các luồng server và repository.
- ClientUiTest chạy FXML/controller với server TCP thật; dependency server chỉ có scope=test trong client, không đi vào client.jar.
- Hai ảnh mc_present.jpg và mc_hype.jpg phục vụ sân khấu đấu giá; cần giữ trong tài nguyên client khi đóng gói.

## Nghiệm thu bàn giao

Phân công trong bảng phục vụ review; mỗi thành viên ghi kết quả sau khi thực hiện kiểm tra.

1. TV1 kiểm migration/seed, constraint/index, số dư và sự nhất quán giữa code, ERD, data dictionary.
2. TV2 kiểm phân quyền, request không hợp lệ, concurrency, session/reconnect và đóng server.
3. TV3 kiểm giao diện, ảnh, điều hướng, phiên đã đóng, DPI, bàn phím và nội dung dài trên máy thật.
4. TV4 kiểm CI Windows/Linux, artifact, hướng dẫn chạy, rehearsal và tổng hợp kết quả.

Xem [đối chiếu yêu cầu](requirements-review.md), [kịch bản test nhóm](local-testing.md), [kết quả kiểm thử](verification-results.md) và [báo cáo bàn giao](week-5-check-report.md). Báo cáo/slide cần phản ánh chức năng thực tế, kết quả test và đóng góp của từng thành viên.
