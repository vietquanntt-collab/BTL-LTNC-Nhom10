# Báo cáo kiểm tra tích hợp hai tuần đầu

## Phạm vi

Bản kiểm tra này được thực hiện trên nhánh `feature/week2-check-fixes`, lấy nền từ `develop`. Phạm vi gồm toàn bộ deliverable tuần 1 và luồng tích hợp tuần 2 hiện có: database, repository, đăng nhập, session, socket JSON, Seller tạo item/auction, đọc danh sách auction và JavaFX client.

Các chức năng bid, realtime event, Auto-Bid, Anti-Snipe và settlement chưa thuộc bản nghiệm thu này.

## Kết quả mã nguồn

- Root Maven có ba module `common`, `server`, `client`, dùng Java 21.
- Database dùng một đường chạy thống nhất: `data/auction.db`; server chạy migration và seed trước khi mở socket.
- Repository dùng `PreparedStatement`, DTO chung và row mapper; password không đi qua DTO.
- Socket hỗ trợ protocol JSON v1 theo dòng UTF-8; protocol pipe cũ được giữ để tương thích.
- Session kiểm tra PBKDF2-HMAC-SHA256 từ seed demo.
- Chỉ Seller được tạo item và auction; Seller phải sở hữu item.
- Danh sách trả về cả auction `OPEN` và `RUNNING`.
- JavaFX login gọi server thật và hiển thị danh sách auction sau khi đăng nhập.
- Socket integration test bao phủ LOGIN → CREATE_ITEM → CREATE_AUCTION → LIST_AUCTIONS.

## Kiểm tra đã chạy

| Hạng mục | Kết quả |
|---|---|
| Biên dịch `common`, `server`, `client` | PASS với JDK 21 |
| `git diff --check` | PASS |
| Tìm conflict marker | PASS |
| Kiểm tra file build/database trong Git | PASS |
| Contract tests `common` | PASS (11 tests) |
| Repository integration tests | PASS (2 tests) |
| Socket integration test | PASS (1 test) |
| Server command/session/smoke tests | PASS (7 tests) |

Lệnh kiểm tra được khuyến nghị cho mọi thành viên:

```powershell
.\scripts\build.ps1
```

Lệnh này tương đương `mvnw.cmd -B verify` và không gọi `clean` mặc định để tránh lỗi khóa file trong thư mục `target` khi project nằm trong OneDrive. Sau khi đóng các tiến trình Java/Maven, có thể build sạch bằng:

```powershell
.\scripts\build.ps1 -Clean
```

Nếu máy Windows gặp `AccessDeniedException` khi Maven dọn thư mục `target`, hãy đóng các tiến trình Java/Maven còn sót và dùng lại `.\scripts\build.ps1` (không có `-Clean`). Đây là lỗi khóa file của môi trường OneDrive, không phải lỗi biên dịch source.

## Chạy local

```powershell
.\scripts\build.ps1
.\scripts\run-server.ps1
```

Mở terminal thứ hai để chạy client:

```powershell
.\scripts\run-client.ps1
```

`run-server.ps1` đã đưa SQLite JDBC, Gson và SLF4J API vào classpath. SQLite có thể in cảnh báo NOP logger nếu không có binding, nhưng cảnh báo này không ảnh hưởng đến chức năng. Nếu tự chạy `java` bằng classpath thủ công mà thiếu `slf4j-api`, server sẽ dừng với `NoClassDefFoundError: org/slf4j/LoggerFactory`.

`run-client.ps1` biên dịch qua reactor rồi chạy `ClientApp` trực tiếp với JavaFX native JAR trên module path. Cách này không cần cài module root vào Maven local repository và không dùng `javafx:run` dạng rút gọn từ root.

Tài khoản demo: `seller/seller123`, `bidder1/bidder123`, `bidder2/bidder123`, `admin/admin123`.

## Quy trình tiếp theo

Sau khi tự chạy verify trên máy, thành viên tạo commit trên branch `feature/week2-check-fixes`, push branch đó và mở Pull Request vào `develop`. Chỉ merge `develop` vào `main` sau khi cả nhóm review và nghiệm thu.
