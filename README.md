# Nhóm 10 - Online Auction System

Repository cho BTL Lập trình nâng cao của Nhóm 10. Tuần 1 tập trung xây dựng nền tảng độc lập gồm contract dùng chung, schema dữ liệu, tài liệu kiến trúc và kiểm thử contract.

> Hai file thử nghiệm `test.md` và `testt.cpp` không thuộc hệ thống đấu giá và không được đưa vào project.

## Phạm vi hiện tại của TV1

- `common/`: contract dùng chung giữa client và server, không truy cập DB.
- `database/`: migration và seed SQLite local, cùng data dictionary.
- `docs/`: ADR, class/sequence diagram và protocol v1.
- `.mvn/wrapper/`: Maven Wrapper script, properties và JAR để build trên máy sạch.

Server và client sẽ được thêm vào các tuần tiếp theo. Repository này được tổ chức độc lập, không phụ thuộc vào thư mục hay dịch vụ bên ngoài.

## Các quyết định đã khóa ở tuần 1

- Java 21, Maven multi-module.
- TCP Socket + JSON; chỉ dùng một serializer ở module network.
- SQLite local là đường chạy mặc định; DB cloud là tùy chọn, không chứa credential trong Git.
- Tất cả tiền lưu bằng số nguyên `cents`; thời gian là ISO-8601 UTC.
- Chỉ server truy cập database; client chỉ gửi/nhận DTO.
- `requestId` dùng cho idempotency; `event sequence` dùng để giữ thứ tự realtime.

## Hướng dẫn đọc

1. Đọc [`docs/adr/ADR-001-target-architecture.md`](docs/adr/ADR-001-target-architecture.md) và ADR-002 trước khi sửa DTO/schema.
2. Chạy migration theo thứ tự trong [`database/README.md`](database/README.md).
3. Chỉ đưa thay đổi contract qua PR có review của TV1.

## Kiểm tra tuần 1

Nếu máy đã có Maven:

```bash
mvn -B -pl common -am clean verify
```

Khi Maven Wrapper đã được khởi tạo đầy đủ:

```bash
.\mvnw.cmd -B -pl common -am clean verify
```

Schema local có thể kiểm tra bằng SQLite CLI theo hướng dẫn trong `database/README.md`.

## Quy tắc thay đổi contract

Không đổi tên field, enum hoặc event đã công bố trong protocol v1 ở giữa một tuần mà không có version/adapter. Nếu cần thay đổi, cập nhật ADR và `docs/contracts/protocol-v1.md` trong cùng PR.
