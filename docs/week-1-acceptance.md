# Week 1 acceptance record

## Phạm vi

Biên bản này chỉ đánh giá baseline tích hợp tuần 1 trên nhánh `fix/week1-cleanup`. Các luồng nghiệp vụ tuần 2 chưa được nghiệm thu.

## Deliverables đã có

- Maven multi-module dùng Java 21: `common`, `server`, `client`.
- Contract dùng chung: enum trạng thái, request/response/event envelope và error code.
- Contract test trong `common/src/test`.
- SQLite migrations, seed demo và data dictionary.
- ADR, protocol contract, class diagram, place-bid sequence diagram.
- Server entry point dạng skeleton, không mở socket và không xử lý nghiệp vụ.
- JavaFX shell có FXML, chưa kết nối network.
- UI state contract v1 trong `client/state/UIState`, gồm `LOADING`, `EMPTY`, `ERROR`, `RECONNECTING`, `CLOSED`.
- GitHub Actions chạy `clean verify` bằng JDK 21.

## Kiểm tra đã thực hiện

| Hạng mục | Kết quả | Ghi chú |
|---|---|---|
| `mvn validate` | PASS | Root POM nhận đủ ba module |
| Biên dịch source bằng Java 21 | PASS | `common`, `server`, `client` |
| Contract runtime harness | PASS | Enum, request identity, response success, event sequence |
| SQLite migration + seed in-memory | PASS | Chạy lặp migration/seed; 9 bảng, 4 user, 1 item, 1 auction |
| Conflict marker và file rác | PASS | Không còn marker hoặc fixture thử nghiệm ngoài hệ thống |
| JUnit qua Maven `verify` trong môi trường kiểm tra | BLOCKED BY ENVIRONMENT | Cache JAR JUnit tại `C:\Users\HP\.m2` trả `AccessDeniedException` khi javac đóng file |

## Phạm vi chưa đánh giá

- Socket server, authentication, repository/service và CRUD.
- Đặt giá, concurrency, realtime event, scheduler, settlement.
- Auto-Bid, Anti-Sniping và biểu đồ lịch sử.
- Các màn hình nghiệp vụ JavaFX và network adapter.

## Cách chuyển sang tuần 2

1. Tạo Pull Request từ `fix/week1-cleanup` vào `develop`.
2. Chờ CI và review chéo của nhóm.
3. Chỉ sau khi PR được merge mới tạo feature branch tuần 2 từ `develop` mới.
4. Chỉ đưa `develop` lên `main` khi toàn bộ nhóm nghiệm thu xong release tương ứng.
