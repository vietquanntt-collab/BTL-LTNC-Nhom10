# Nhóm 10 - Online Auction System

Đây là repository của hệ thống đấu giá trực tuyến, gồm luồng đấu giá realtime đã tích hợp và lớp dữ liệu cho Auto-Bid, wallet và settlement idempotent. TV1 đã khóa contract dùng chung, migration và repository boundary để TV2, TV3 và TV4 tiếp tục tích hợp chính sách nghiệp vụ, giao diện và kiểm thử end-to-end.

## Cấu trúc repository

- `common/`: DTO, enum, envelope protocol, JSON codec và contract tests.
- `server/`: SQLite persistence/repository, authentication/session, TCP socket server và integration tests.
- `client/`: JavaFX login, network adapter và màn hình danh sách auction.
- `database/`: migration, seed và data dictionary SQLite cho user, auction, bid, Auto-Bid, wallet và settlement.
- `docs/`: ADR, protocol contract, tài liệu bàn giao và báo cáo kiểm tra theo từng mốc.
- `.github/workflows/`: CI chạy `clean verify` bằng JDK 21.

## Công nghệ

- Java 21 và Maven Wrapper.
- SQLite local, tiền lưu bằng integer cents.
- TCP, mỗi request/response JSON là một dòng UTF-8; protocol pipe cũ vẫn được giữ để tương thích.
- JUnit 5 cho contract, persistence và socket smoke test.
- JavaFX 21 cho client.

## Cài đặt trước khi chạy

- JDK 21 (kiểm tra bằng `java -version`).
- Git.
- Không cần cài Maven riêng vì repository đã có Maven Wrapper (`mvnw.cmd`).
- Không cần tải JavaFX thủ công; Maven sẽ tải đúng JavaFX 21 cho Windows.

Sau khi clone repository, mở PowerShell tại thư mục project:

```powershell
git clone https://github.com/vietquanntt-collab/BTL-LTNC-Nhom10.git
cd BTL-LTNC-Nhom10
git fetch origin
git switch develop
git pull --ff-only origin develop
# Tạo branch riêng cho nhiệm vụ, ví dụ:
git switch -c feature/<ten-nhiem-vu>
```

Nếu branch nhiệm vụ đã tồn tại trên GitHub, dùng `git switch --track origin/<ten-branch>` thay cho lệnh `git switch -c`. Không làm việc trực tiếp trên `main` hoặc `develop`. Kiểm tra Java và Maven Wrapper trước khi build:

```powershell
java -version
.\mvnw.cmd -version
```

`java -version` cần là JDK 21. Nếu Windows có nhiều JDK, đặt `JAVA_HOME` tới thư mục JDK 21 rồi mở lại terminal:

```powershell
$env:JAVA_HOME = '<duong-dan-toi-JDK-21>'
```

Nếu PowerShell chặn script `.ps1`, chỉ áp dụng cho terminal hiện tại:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

## Kiểm tra toàn bộ project

Từ thư mục repository, chạy script đã được chuẩn hóa:

```powershell
.\scripts\build.ps1
```

Script trên chạy `verify`, biên dịch cả `common`, `server`, `client` và toàn bộ test. Nếu muốn dọn `target` trước khi build, sau khi đã đóng các tiến trình Java/Maven:

```powershell
.\scripts\build.ps1 -Clean
```

Nếu OneDrive đang giữ file trong `target`, dùng lệnh mặc định không có `-Clean`. Không commit `target/`, `*.class` hoặc database runtime.

Kiểm tra hiện tại với JDK 21: `verify` chạy PASS; `common` có 18 test,
`server` có 32 test, còn `client` biên dịch và đóng gói JAR thành công. Báo cáo
kiểm tra các mốc trước đó được giữ trong `docs/` để truy nguyên lịch sử tích hợp.

## Chạy server

Mở terminal thứ nhất tại thư mục repository:

```powershell
.\scripts\run-server.ps1
```

Script tự biên dịch server, tìm các JAR SQLite, Gson và SLF4J trong Maven local repository rồi khởi động `ServerApplication`. Server mặc định nghe tại `127.0.0.1:9999`, tạo database ở `data/auction.db`, chạy migration và seed demo.

Có thể đổi cổng hoặc tắt seed:

```powershell
.\scripts\run-server.ps1 -Port 10000
.\scripts\run-server.ps1 -Seed $false
```

Tài khoản seed nằm trong `database/README.md`; mật khẩu demo được lưu dưới dạng PBKDF2-HMAC-SHA256.

## Chạy JavaFX client

Giữ server đang chạy, mở terminal thứ hai tại thư mục repository:

```powershell
.\scripts\run-client.ps1
```

Script biên dịch client qua reactor rồi chạy `ClientApp` trực tiếp với JavaFX native JAR trên module path. Cách này tránh lỗi `No plugin found for prefix 'javafx'` và không cần cài module root vào Maven local repository.

Client đăng nhập qua TCP JSON, sau đó tải danh sách auction từ server. Có thể đổi host và cổng:

```powershell
.\scripts\run-client.ps1 -HostName 127.0.0.1 -Port 9999
```

Nếu không dùng script, hãy giữ nguyên nguyên tắc: build qua reactor trước, sau đó chạy JavaFX với native JAR trên module path. Không dùng `javafx:run` dạng rút gọn ở root vì Maven có thể không nhận plugin prefix.

## Chức năng hiện có

1. Migration và seed SQLite cho schema đấu giá, bid history và settlement request
   idempotency; ứng dụng tự kiểm soát thứ tự migration bằng `schema_migrations`.
2. Login kiểm tra password hash và tạo session role.
3. Seller tạo item; chỉ Seller được tạo item.
4. Seller chỉ tạo auction cho item thuộc tài khoản của mình.
5. Bidder/Seller đã đăng nhập đọc danh sách auction `OPEN` và `RUNNING`.
6. Client JavaFX nối server thật, có loading/error state và màn hình danh sách auction.
7. Auction detail hiển thị giá hiện tại, minimum bid, countdown, bid history và chart.
8. Place bid dùng transaction, minimum increment, kiểm tra thời gian mở/đóng, optimistic version, request idempotency, wallet locking và audit event.
9. Realtime `BID_ACCEPTED`, `OUTBID`, `BID_REJECTED` và `AUCTION_FINISHED` có sequence theo auction; client có chống double-submit và reconnect.
10. TV1 cung cấp `AutoBidRuleRepository`, `WalletRepository` và
    `SettlementRepository`, cùng DTO validation và integration test cho upsert
    rule, optimistic wallet update, winner marking và settlement retry.

## Phần đang chờ tích hợp

- TV2 tích hợp chính sách Auto-Bid, Anti-Snipe, scheduler, frozen balance và
  chuyển tiền settlement qua repository boundary.
- TV3 nối trạng thái Auto-Bid, auction extended/closed/winner và balance vào
  JavaFX client.
- TV4 bổ sung Observer/event flow, policy test, end-to-end test và demo script.
- Nhóm tiếp tục hoàn thiện `REGISTER`, CRUD sửa/xóa item và các tiêu chí release
  cuối kỳ.

Chi tiết contract, invariant và cách TV2/TV3/TV4 tiếp nhận lớp dữ liệu nằm trong
`docs/auto-bid-settlement-data-layer.md`.

## Git workflow

```text
feature/* -> develop -> main
```

Mỗi thành viên tạo feature branch, chạy kiểm tra, mở Pull Request vào `develop`; chỉ đưa `develop` lên `main` sau khi nhóm nghiệm thu release.

## Quy trình commit của thành viên

Không commit trực tiếp vào `main` hoặc `develop`. Nếu bắt đầu nhiệm vụ mới, tạo branch feature; nếu đã ở đúng branch thì giữ nguyên branch đó. Sau khi sửa code và chạy `build.ps1`, thực hiện:

```powershell
git switch -c feature/<ten-nhiem-vu>
git status
git add -A
git diff --cached --check
git commit -m "feat: describe the completed function"
git push -u origin HEAD
```

Không chạy lại `git switch -c` nếu branch đã tồn tại. Khi cập nhật một branch đã push, dùng `git add -A`, `git commit` và `git push` như bình thường.

Sau đó mở Pull Request từ branch feature vào `develop`. Chỉ merge khi CI xanh và một thành viên khác đã review. Không đưa `target/`, `data/auction.db`, file `.class`, JAR hoặc cấu hình mật khẩu lên GitHub.

