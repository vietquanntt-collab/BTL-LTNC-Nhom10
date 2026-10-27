# BẢNG QUẢN LÝ KIẾN TRÚC VÀ THEO DÕI NGUỒN GỐC MÔ-ĐUN (SOURCE-MAP)

Quy định: Mọi thành phần mã nguồn cốt lõi khi tích hợp vào nhánh develop đều cần ghi nhận rõ phân loại kiến trúc và bài kiểm thử đi kèm.

| Mô-đun nền tảng (Baseline) | Gói đích trong dự án (Target Package) | Người phụ trách | Phân loại (KEEP / ADAPT / REWRITE) | Mục đích & Thay đổi chính | Kiểm thử kiểm chứng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| pom.xml | pom.xml | TV4 | ADAPT | Thiết lập môi trường Java 21, đóng gói Fat JAR qua Shade plugin | Tự động biên dịch trên CI |
| User.java | core.domain.User | TV1 | KEEP | Định nghĩa thực thể người dùng, kế thừa 3 vai trò (Bidder, Seller, Admin) | UserServiceTest |
| RequestHandler.java | server.RequestHandler | TV2 | ADAPT | Định tuyến gói tin TCP/JSON, thêm mã lỗi (ErrorCode) và RequestId | ContractTest |
| LoginController.java | client.LoginController | TV3 | ADAPT | Tách biệt giao diện JavaFX khỏi database, giao tiếp qua Socket | Kiểm thử giao diện (UI) |
| AutoBidService.java | server.policy.AutoBid | TV4 | REWRITE | Xây dựng logic tự động đấu giá chạy hoàn toàn tại Server, chống tranh chấp | AutoBidConcurrencyTest |