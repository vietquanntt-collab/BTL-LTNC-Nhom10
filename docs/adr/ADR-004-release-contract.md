# ADR-004: Contract và kiến trúc của bản bàn giao

- Updated: 2026-11-24
- Status: Implemented locally; pending team review and remote CI
- Supersedes: các mô tả chưa khớp implementation trong ADR-001/002/003

## Quyết định theo code hiện tại

- common chứa immutable record DTO, enum, envelope và JsonCodec dùng Gson; không phụ thuộc JDBC/JavaFX. DTO vẫn giữ Role/ItemType để ổn định wire contract.
- server/domain triển khai Entity, User -> Bidder/Seller/Admin, Item -> Card/Figure/Poster, Auction và BidTransaction. Private final fields giữ trạng thái; abstract methods và override cung cấp abstraction/polymorphism. User policy được AuthService/ClientHandler dùng để xác thực và phân quyền.
- ItemFactory là Factory Method với các concrete creator cho Card/Figure/Poster; JdbcItemRepository dùng khi tạo draft, ItemRowMapper dùng khi khôi phục item. GET_AUCTION cung cấp categoryLabel theo subtype. BidRowMapper dùng BidTransaction để trả lịch sử.
- NetworkClient và NavigationManager dùng Singleton; EventPublisher/AuctionEventObserver/SocketClientObserver dùng Observer. DatabaseConnectionFactory tạo kết nối JDBC; không thay thế ItemFactory.
- client production phụ thuộc common/JavaFX/Gson. Dependency server trong client/pom.xml có scope=test để regression socket thật; client.jar không chứa server hoặc SQLite.
- Socket UTF-8, một JSON object mỗi dòng. Envelope có payloadJson là chuỗi JSON; JsonCodec là codec chung. requestId liên kết response và receipt; auction sequence sắp thứ tự event.
- SESSION xác thực sessionId, trả balanceCents/frozenCents và rebind observer ví. Restart server làm hết hiệu lực session trong RAM; client cần đăng nhập lại.
- Giá hợp lệ tối thiểu luôn là currentPriceCents + minIncrementCents, kể cả lượt đầu. Auto-Bid dùng bước lớn hơn giữa bước phiên và bước của rule; PriorityQueue ưu tiên trần cao, thời điểm đăng ký và ruleId. Test kiểm giá đầu tiên, tie-break, trần và giữ/giải phóng ví.
- CREATE_AUCTION tạo OPEN với startAt hiện tại. Bid hợp lệ đầu tiên chuyển RUNNING; không có scheduler chuyển RUNNING chỉ do thời gian. Server kiểm start/end; scheduler đóng OPEN/RUNNING hết giờ, retry FINISHED và tất toán PAID. CANCELLED là cách viết enum của protocol cho trạng thái hủy.
- Một server process sở hữu một SQLite connection. Request và scheduler dùng databaseLock chung; per-auction lock, transaction và update theo version bảo vệ bid. Không chạy nhiều server ghi cùng DB.
- Migration V005 kiểm cột ảnh trước ALTER để tương thích DB tuần 4 và DB đầu tuần 5 đã có cột. Không sửa tiếp migration đã phát hành.
- Runtime nhận Java system properties (-Dauction.*) hoặc tham số script. application.example.properties là tham khảo, không tự load. SQLite local không cần DB_USER/DB_PASSWORD.
- Checkstyle chạy ở validate cho source/test với bộ quy tắc baseline trong config/checkstyle. Đây là một phần Google Java Style, chưa enforce toàn bộ định dạng/import/Javadoc. JaCoCo ở verify áp ngưỡng line coverage 60% cho từng module, không loại class sản phẩm khỏi phép đo.
- Build đầy đủ dùng build.ps1 -Clean -Ui; headless dùng build.ps1 (package + JUnit/Checkstyle, skip UI, không chạy gate coverage). JavaFX coverage cần desktop/Xvfb. JAR client có native theo OS build; CI xuất artifact Windows/Linux riêng.

## Nghiệm thu

DomainTest và các regression DB/socket/UI đã kiểm tra các cấu trúc trên. Kết quả local không xác nhận CI remote hoặc thay thế review chéo, visual QA/rehearsal và báo cáo/slide. Xem requirements-review.md và week-5-check-report.md.
