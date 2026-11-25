# Kết quả kiểm thử bản bàn giao

Ngày kiểm tra: 2026-11-25 (Asia/Bangkok). Temurin JDK 21.0.13, Maven Wrapper 3.8.5, Windows x64.
Lượt clean verify có JavaFX kết thúc lúc **22:52:28 +07**, **BUILD SUCCESS**, thời gian Maven khoảng 40 giây. Số liệu lấy từ XML Surefire/JaCoCo/Checkstyle của lượt này trên nền `develop` (**f957216**, sau PR #42 Plushie Gala) kèm bản sửa hoàn thiện nhánh `fix/pre-main-polish`. Đây là kết quả local; GitHub Actions trên commit mới sẽ xác nhận thêm sau khi push.

## Tổng theo module

| Module | Test | Failure | Error | Skipped | Line coverage | Checkstyle |
|---|---:|---:|---:|---:|---:|---:|
| common | 24 | 0 | 0 | 0 | 275/313 (87.86%) | 0 |
| server | 66 | 0 | 0 | 0 | 1811/2228 (81.28%) | 0 |
| client | 9 | 0 | 0 | 0 | 1695/2446 (69.30%) | 0 |
| **Tổng** | **99** | **0** | **0** | **0** | **từng module >=60%** | **0** |

## Kết quả từng suite

| Module | Suite | Test | Failure | Error | Skipped |
|---|---|---:|---:|---:|---:|
| common | common.AutoBidSettlementContractTest | 3 | 0 | 0 | 0 |
| common | common.BidContractTest | 4 | 0 | 0 | 0 |
| common | common.ContractValidationTest | 6 | 0 | 0 | 0 |
| common | common.JsonCodecTest | 2 | 0 | 0 | 0 |
| common | common.protocol.Week2ContractTest$AuthenticationTests | 2 | 0 | 0 | 0 |
| common | common.protocol.Week2ContractTest$ItemAndAuctionTests | 3 | 0 | 0 | 0 |
| common | common.ProtocolContractTest | 4 | 0 | 0 | 0 |
| server | server.AuctionEndToEndFlowTest | 1 | 0 | 0 | 0 |
| server | server.BidAuditLogTest | 2 | 0 | 0 | 0 |
| server | server.bidding.AntiSnipeAndSchedulerTest | 3 | 0 | 0 | 0 |
| server | server.bidding.AutoBidTest | 3 | 0 | 0 | 0 |
| server | server.bidding.PlaceBidServiceTest | 7 | 0 | 0 | 0 |
| server | server.bidding.SettlementAndEventOrderTest | 2 | 0 | 0 | 0 |
| server | server.BidRealtimeEventTest | 4 | 0 | 0 | 0 |
| server | server.ConcurrencyBidTest | 3 | 0 | 0 | 0 |
| server | server.domain.DomainTest | 7 | 0 | 0 | 0 |
| server | server.network.ClientCommandParsingTest | 2 | 0 | 0 | 0 |
| server | server.network.ManagementSocketIntegrationTest | 8 | 0 | 0 | 0 |
| server | server.network.SocketIntegrationTest | 1 | 0 | 0 | 0 |
| server | server.persistence.AutoBidSettlementRepositoryIntegrationTest | 5 | 0 | 0 | 0 |
| server | server.persistence.BidRepositoryIntegrationTest | 4 | 0 | 0 | 0 |
| server | server.persistence.ItemImageUpgradeTest | 1 | 0 | 0 | 0 |
| server | server.persistence.RepositoryIntegrationTest | 3 | 0 | 0 | 0 |
| server | server.ServerHardeningTest | 5 | 0 | 0 | 0 |
| server | server.ServerSmokeTest | 2 | 0 | 0 | 0 |
| server | server.session.SessionManagerTest | 3 | 0 | 0 | 0 |
| client | client.ClientUiTest | 5 | 0 | 0 | 0 |
| client | client.service.NetworkClientTest | 4 | 0 | 0 | 0 |

Week2ContractTest là container 0 test; các nested suite được cộng riêng, không cộng test hai lần.

## Phạm vi kết quả

- Checkstyle chạy ở validate cho production và test; bộ baseline naming/modifier/file hygiene nằm trong config/checkstyle. Root POM và cả ba module có 0 violation. Chưa enforce toàn bộ Google Java Style.
- JaCoCo áp line gate 0.60 cho từng module ở verify; không exclude class sản phẩm khỏi báo cáo. Root POM không có class/test nên không có gate riêng.
- ClientUiTest có 5 test: luồng FXML/server TCP thật qua các vai trò (gồm preset Báo Hồng), tách nền ảnh giữ chi tiết bên trong, fallback ảnh sân khấu & ảnh MC (`mc_present.jpg`/`mc_hype.jpg`), mở CANCELLED/FINISHED/PAID với nút bị khóa, form đăng ký kèm lỗi nằm trong cửa sổ 1100x720.
- Luồng UI kiểm cả ảnh MC được load, độ tương phản chữ MC và hàng giá dẫn đầu >=4.5. Bốn NetworkClientTest kiểm socket/ảnh/logout-login/EOF/reconnect và ví.
- Ảnh UI nằm trong client/target/ui-snapshots; số đo client/target/ui-layout.tsv xác nhận nội dung ScrollPane không tràn chiều rộng ở 1100x720 và 1280x800 trong kịch bản. Đã kiểm tra trực quan các ảnh render sau sửa lỗi cắt chữ ticker/bảng lịch sử và clip hạt rơi màn hình đăng nhập.
- CI Windows/Linux và rehearsal nhóm cần được xác nhận trên commit sau khi push.

## Kiểm tra JAR

| JAR | Byte | SHA-256 |
|---|---:|---|
| server.jar | 13998394 | 86c3c7ccf719dcd25a5c1f8f9298151ff42560450ff2c9b73e667ea4457c6081 |
| client.jar | 10906089 | 5275e40387d4701b97e2b943f2dc897e3666425d212f768f5cdb6a8e8b29a55b |

Hash chỉ áp dụng các JAR của lượt build trên. Build lại có thể sinh hash khác. SHA256SUMS.txt trong gói runtime ghi hash thực tế của gói đó.

## Smoke test bản đóng gói

Đã chạy độc lập hai JAR trong release-bundle/local-6a4fc8b0 bằng JDK 21.0.13, kết thúc lúc 10:03:57 +07 ngày 2026-11-24:

- Hash bản copy khớp hai JAR của lượt build cuối; manifest đúng. client.jar không chứa class server hoặc org.sqlite. Ảnh mc_present/mc_hype, CSS và FXML cần thiết có trong JAR.
- Server chạy tại gốc gói runtime, dùng cổng ngẫu nhiên và DB riêng trong target/package-smoke: HEALTH, LOGIN, SESSION, LIST_AUCTIONS, GET_AUCTION, từ chối bid bằng giá hiện tại, nhận bid đúng mức tối thiểu, refresh tiền đang giữ, LOGOUT và từ chối session cũ đều qua.
- Helper gọi ClientLauncher từ JAR đóng gói: JavaFX Stage hiển thị form có usernameField/passwordField/submitButton, sau đó tự đóng và exit 0.
- Tiến trình thử nghiệm server và client đã dừng. Bằng chứng tại target/package-smoke/results.json cùng log/helper/DB; chúng bị ignore và không được đưa vào gói gửi nhóm.
- Tài liệu trong gói được đồng bộ sau smoke test; hai JAR giữ nguyên hash. Người nhận đọc START-HERE.md để chạy bằng java -jar.
