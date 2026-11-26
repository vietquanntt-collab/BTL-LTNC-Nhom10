# Tuần 2 – TV1: data layer

## Mục tiêu

TV1 cung cấp lớp dữ liệu dùng chung cho các thành phần server ở tuần 2. Phần này giữ ranh giới rõ ràng:

- `common` chứa DTO, command và validation contract.
- `server/persistence` mở kết nối SQLite, chạy migration và seed.
- `server/repository` chứa interface repository, JDBC implementation và row mapper.
- `database` vẫn là nguồn duy nhất của schema; repository không tự tạo bảng.

Socket, authentication service, nghiệp vụ đấu giá, JavaFX screen và Auto-Bid thuộc các feature branch khác.

## Contract dùng chung

Các record trong `common/src/main/java/com/btl/team10/common/dto` dùng tiền ở đơn vị **cent** và thời gian ở `Instant`:

- `UserDto`: thông tin tài khoản và wallet, không trả password hash.
- `ItemDto`: sản phẩm và người bán.
- `AuctionDto`: giá, thời gian, trạng thái, phiên bản và winner.
- `BidDto`: bản ghi bid để các service tuần 2 sử dụng.
- `CreateItemCommand`, `CreateAuctionCommand`: input đã được validate trước khi vào repository.

`ValidationException` giữ tên field lỗi và ánh xạ về `ErrorCode.VALIDATION_ERROR`. Các DTO không chấp nhận id không dương, tiền âm, chuỗi bắt buộc rỗng hoặc mốc thời gian không hợp lệ.

`JsonCodec` dùng chung Gson cho client/server và ghi `Instant` thành chuỗi ISO-8601 UTC. Codec giữ các trường nullable trong JSON để response có cấu trúc ổn định; các module không tự tạo cấu hình Gson khác nhau cho cùng DTO.

## Khởi tạo SQLite

`DatabaseConnectionFactory` bật `foreign_keys` và `busy_timeout` cho từng connection. `DatabaseInitializer` chạy các file `.sql` trong `database/migrations` theo thứ tự tên và lưu tên file đã áp dụng vào bảng metadata `schema_migrations`; mỗi migration chỉ chạy một lần. Seed được chạy khi được yêu cầu. Toàn bộ quá trình nằm trong một transaction và được rollback khi có lỗi. Hàm khởi tạo yêu cầu connection chưa nằm trong transaction của caller để không commit nhầm công việc khác.

Ví dụ tích hợp ở server:

```java
try (Connection connection = DatabaseConnectionFactory.open(Path.of("data/auction.db"))) {
    DatabaseInitializer.initialize(connection, Path.of("database"), true);
}
```

Seed demo chỉ nên bật cho môi trường local. File database runtime không được commit.

## Repository đã cung cấp

| Repository | Đọc | Ghi |
| --- | --- | --- |
| `UserRepository` | `findById`, `findByAccount` | `insert` user và wallet trong cùng transaction |
| `ItemRepository` | `findById`, `findBySeller` | `insert(CreateItemCommand)` |
| `AuctionRepository` | `findById`, `findByStatus` | `insert(CreateAuctionCommand)` với trạng thái ban đầu `OPEN` |

Các implementation JDBC dùng `PreparedStatement`, trả `Optional` cho truy vấn theo id và trả danh sách bất biến cho truy vấn nhiều dòng. Mapper tách riêng việc đọc `ResultSet`, kiểm tra enum và parse timestamp; repository chỉ giữ SQL và luồng truy vấn.

## Quy ước transaction

- Các phương thức `find*` không commit và không thay đổi transaction của caller.
- `JdbcUserRepository.insert` tự quản lý transaction chỉ khi connection đang ở auto-commit; nếu caller đã mở transaction thì thao tác được giữ trong transaction của caller.
- `DatabaseInitializer` sở hữu transaction khởi tạo riêng và từ chối connection đã ở trong transaction.
- Các service tuần 2 phải mở transaction ở boundary nghiệp vụ khi một use case chạm nhiều repository.

## Handoff cho các thành viên

- TV2 có thể dùng các repository này để nối socket handler với service; không đặt SQL trong handler.
- TV3 chỉ phụ thuộc DTO/common contract và network adapter, không truy cập JDBC.
- TV4 xây dựng bid/auto-bid/concurrency trên service; không sửa trực tiếp mapper để thay đổi nghiệp vụ.
- Thay đổi schema phải thêm migration mới và cập nhật data dictionary/ADR trước khi merge vào `develop`.

## Kiểm tra trước khi tạo Pull Request

1. Dùng JDK 21 và chạy compile/verify ở máy local.
2. Kiểm tra `git diff --check` và tìm conflict marker.
3. Xác nhận không có `target/`, `*.db`, file thử nghiệm hoặc secret trong commit.
4. Tạo PR từ `feature/week2-tv1-data-layer` vào `develop`; không push trực tiếp vào `develop` hoặc `main`.
