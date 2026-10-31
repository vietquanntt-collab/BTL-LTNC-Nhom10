# Nhóm 10 - Online Auction System

Đây là baseline tích hợp của tuần 1 cho BTL Lập trình nâng cao.
Tuần 1 khóa contract dùng chung, schema dữ liệu, cấu trúc Maven, tài liệu kiến trúc và CI. Các luồng nghiệp vụ mạng, đấu giá và giao diện hoàn chỉnh được phát triển ở tuần 2 trở đi.

## Cấu trúc repository

- `common/`: enum, request/response/event contract và contract test; không chứa JDBC hoặc JavaFX.
- `server/`: server skeleton của tuần 1; chưa có socket hay nghiệp vụ đấu giá.
- `client/`: JavaFX shell của tuần 1; chưa kết nối network.
- `database/`: migration, seed và data dictionary SQLite.
- `docs/`: ADR, protocol contract, sơ đồ và handoff.
- `.github/workflows/`: CI chạy build/test bằng JDK 21.

## Công nghệ đã khóa

- Java 21.
- Maven Wrapper.
- TCP/JSON là protocol đích; serializer chỉ được đặt ở module network.
- SQLite local là đường chạy mặc định.
- JUnit 5 cho contract test.
- GitHub Actions cho CI.

## Kiểm tra baseline tuần 1

Từ thư mục repository:

```powershell
.\mvnw.cmd -B -pl common,server,client -am clean verify
```

Lệnh kiểm tra nhanh chỉ cho contract dùng chung:

```powershell
.\mvnw.cmd -B -pl common -am clean verify
```

Hai lệnh trên chỉ build các module skeleton của baseline tuần 1; không chạy server nghiệp vụ, client network, Auto-Bid, Anti-Snipe hoặc scheduler.

Nếu Maven Wrapper báo không ghi được vào `C:\.m2`, dùng thiết lập thư mục người dùng trong phiên terminal hiện tại:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Duser.home=$env:USERPROFILE"
.\mvnw.cmd -B -pl common -am clean verify
Remove-Item Env:JAVA_TOOL_OPTIONS
```

Không commit các thư mục `target/`, file `.class` hoặc database runtime.

## Database local

Chạy các script theo thứ tự trong `database/README.md`:

1. `database/migrations/V001__core_schema.sql`
2. `database/migrations/V002__advanced_schema.sql`
3. `database/seed/V001__demo_seed.sql`

Không commit file `*.db`; chỉ server được phép truy cập database khi phần nghiệp vụ được tích hợp.

## Quy tắc nhánh

```text
feature/* -> develop -> main
```

`main` chỉ nhận release đã review. Mỗi thành viên làm việc trên feature branch và tạo Pull Request vào `develop`.
