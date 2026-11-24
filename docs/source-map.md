# Source map và attribution

Bản audit bắt đầu từ develop 8af20c4 của vietquanntt-collab/BTL-LTNC-Nhom10, đã tích hợp giao diện PR #36 (abe5265) và kiểm tra lại ngày 2026-11-24. Hai ảnh mc_present.jpg/mc_hype.jpg vẫn được giữ vì controller sử dụng; PR #36 xóa ảnh nhưng chưa bỏ các tham chiếu runtime. Các owner dưới là phân công trong kế hoạch; không tự suy ra ai đã viết từng dòng hoặc đã review. Lịch sử commit/PR của nhóm là bằng chứng tác giả cần giữ.

## Đối chiếu với baseline được cung cấp

Có thư mục tham khảo BTL_Nhom8/BTL_Nhom8 tại máy audit, chứa package com.btl.n8. Đây là bản file local không có Git remote/revision ở thư mục đã kiểm tra; chưa xác minh license, quyền dùng hay dòng mã nào đã sao chép. Bảng dưới chỉ nêu nguồn đối chiếu cụ thể và thay đổi đích, không khẳng định mọi mã đích là tác phẩm mới hoặc đã được cho phép.

| Khu vực | File baseline để đối chiếu (trong src/main/java/com/btl/n8/) | File đích | Thay đổi/evidence | Owner review |
|---|---|---|---|---|
| User/session | Service/UserService.java, Network/ServerSessionManager.java | server/.../session/AuthService.java, SessionManager.java | PBKDF2, session token, role server; SessionManagerTest, ManagementSocketIntegrationTest | TV2 |
| Item/repository | Connection/ItemDAO.java, ItemDAOImpl.java, Model/Mapper/ItemMapper.java | server/.../repository/ItemRepository.java, JdbcItemRepository.java, mapper/ItemRowMapper.java | SQLite local, prepared statement, ownership, ảnh, khóa item đã list; repository/socket/client tests | TV1 + TV2 |
| Bid/concurrency | Service/BidService.java | server/.../bidding/PlaceBidService.java | integer cents, transaction/version, frozen wallet, receipt/history; ConcurrencyBidTest, BidAuditLogTest | TV2 + TV4 |
| Advanced | Controller/AutoBidManager.java, Service/AutoBidService.java, Network/SettlementHandler.java | server/.../bidding/, repository/ | policy server, AntiSnipePolicy, settlement idempotency; AutoBidTest, AntiSnipeAndSchedulerTest, SettlementAndEventOrderTest | TV2 + TV4 |
| Domain design | Model/Entity/User.java, Item.java, Model/Factory/ItemFactory.java | server/.../domain/ | Entity/User/Item hierarchy, Auction, BidTransaction và Factory Method đã được tích hợp; DomainTest và regression DB/socket kiểm chứng. | TV1 |
| Network/UI | Network/ClientHandler.java, RequestHandler.java, các controller/FXML baseline | client/.../service/NetworkClient.java, controller/, resources/ | protocol v1 payloadJson, UI qua socket, lifecycle/role; NetworkClientTest, ClientUiTest | TV2 + TV3 |
| Build/test | pom.xml, src/test/java/com/btl/n8/Service/*Test.java | root/common/server/client POM, tests, .github/workflows/ci.yml | Java21 multimodule, regression DB/socket/UI và native JAR; clean verify | TV4 |

## Thay đổi trong đợt bàn giao

- Migration V005 và DatabaseInitializer xử lý database trước/sau khi có cột ảnh. ItemImageUpgradeTest là regression mới cho đường nâng cấp.
- ClientHandler/JdbcItemRepository/NetworkClient lưu, giữ hoặc xóa ảnh đúng contract. SESSION rebind ví khi reconnect; server socket và client tests kiểm chứng.
- CSS, FXML và LoginController được chỉnh trong bản audit; giữ tài nguyên sân khấu do develop cung cấp. Điều chỉnh lifecycle, countdown đóng phiên, Auto-Bid off, tìm kiếm tên và đóng gói client.
- Regression client dùng server dependency scope=test, không đưa server vào client production.
- Các tài liệu/sơ đồ cập nhật theo hai PDF mới; domain hierarchy/Factory, Checkstyle và JaCoCo có code/test thực tế.
