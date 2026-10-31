# Nhóm 10 - Week 1 handoff

**Phạm vi:** baseline tích hợp tuần 1 của cả nhóm
**Nhánh phát hành:** `main` chỉ giữ bản đã nghiệm thu
**Nhánh tích hợp:** `develop`

## Đã hoàn thành

- Root Maven POM và Maven Wrapper dùng Java 21.
- Ba module có cấu trúc rõ ràng: `common`, `server`, `client`.
- Protocol v1 trong `common`, gồm enum, request/response/event envelope và contract test.
- SQLite core/advanced schema, index, constraint, migration và demo seed.
- Data dictionary, ADR, protocol contract, class diagram và place-bid sequence diagram.
- Server skeleton và JavaFX shell để các thành viên bắt đầu tuần 2 trên cùng cấu trúc.
- GitHub Actions chạy build/test baseline.

## Tiêu chí nghiệm thu tuần 1

- Clone sạch nhận đúng Maven Wrapper và build được bằng JDK 21.
- `common` biên dịch được; contract test kiểm tra enum, request identity, response error và event sequence.
- Migration chạy theo thứ tự trên SQLite sạch; seed tạo được dữ liệu demo.
- Không có file build, database runtime, conflict marker hoặc file thử nghiệm ngoài hệ thống.
- Không có tài liệu truy nguyên nguồn code cũ trong baseline phát hành.

## Lệnh xác minh

```powershell
.\mvnw.cmd -B -pl common,server,client -am clean verify
```

Khi chỉ cần kiểm tra contract tuần 1:

```powershell
.\mvnw.cmd -B -pl common -am clean verify
```

Kiểm tra migration và seed trên SQLite sạch:

```powershell
sqlite3 :memory: ".read database/migrations/V001__core_schema.sql" ".read database/migrations/V002__advanced_schema.sql" ".read database/seed/V001__demo_seed.sql"
```

## Kết quả kiểm tra trước khi chuyển sang tuần 2

- `mvn validate`: PASS.
- Biên dịch `common`, `server`, `client` bằng Java 21: PASS.
- Migration/seed SQLite in-memory, chạy lặp migration và seed: PASS; có 9 bảng đích, 4 user demo, 1 item và 1 auction demo.
- Không còn conflict marker, file thử nghiệm rác, file build hoặc database runtime được theo dõi trong Git.
- Contract test JUnit cần chạy lại trên máy thành viên bằng terminal VS Code bình thường. Môi trường kiểm tra tự động bị `AccessDeniedException` khi javac đóng JAR `junit-jupiter-engine` trong cache `C:\Users\HP\.m2`; đây là giới hạn môi trường, chưa kết luận test logic hỏng.

## Phạm vi tuần 2

Tuần 2 mới được thêm socket server, repository/service, authentication, JavaFX screen, network adapter và các luồng nghiệp vụ. Các thành phần này phải đi qua feature branch và Pull Request vào `develop`.
