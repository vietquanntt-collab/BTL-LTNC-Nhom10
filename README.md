# Nhóm 10 – Online Auction System

Hệ thống đấu giá trực tuyến xây dựng theo kiến trúc **Client–Server (TCP/JSON Socket)** với giao diện **JavaFX** và cơ sở dữ liệu nhúng **SQLite**. Hệ thống hỗ trợ đầy đủ nghiệp vụ: đăng ký/đăng nhập, phân quyền (`ADMIN`, `SELLER`, `BIDDER`), quản lý vật phẩm & phiên đấu giá, đặt giá realtime, đấu giá tự động (**Auto-Bid**), chống đặt giá phút chót (**Anti-Snipe**), tự động đóng phiên & tất toán ví (**Settlement**), lịch sử đấu giá, biểu đồ biến động giá và bảng điều khiển quản trị.

---

## 1. Yêu cầu môi trường (Chạy trên máy sạch)

- **Hệ điều hành:** Windows 10/11, Linux hoặc macOS.
- **Java Development Kit (JDK):** Phiên bản **JDK 21** (khuyến nghị Eclipse Temurin 21 hoặc Oracle JDK 21).
- **Công cụ build & thư viện:** Maven Wrapper (`mvnw` / `mvnw.cmd`), SQLite JDBC và các thư viện JavaFX đã được cấu hình sẵn trong dự án. Máy chỉ cần kết nối Internet ở lần build đầu tiên để tải dependency.

Kiểm tra phiên bản Java trên máy:

```powershell
java -version
```

Nếu máy cài nhiều phiên bản JDK, trỏ `JAVA_HOME` về JDK 21 cho cửa sổ terminal hiện tại:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

---

## 2. Hướng dẫn Build và Chạy trên Windows

Mở **PowerShell** tại thư mục gốc của dự án (thư mục chứa file `README.md` này):

### Bước 1: Cấp quyền chạy script PowerShell (nếu bị chặn)

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

### Bước 2: Build toàn bộ dự án

```powershell
.\scripts\build.ps1
```

*(Hoặc build trực tiếp bằng Maven Wrapper: `.\mvnw.cmd clean package`)*

### Bước 3: Khởi động Server (Terminal 1)

```powershell
.\scripts\run-server.ps1
```

Server mặc định lắng nghe tại cổng **`9999`**, tự động chạy database migration và nạp dữ liệu mẫu vào **`data/auction.db`**.

Nếu muốn khởi tạo một database sạch mới hoàn toàn hoặc chạy ở cổng khác:

```powershell
.\scripts\run-server.ps1 -Port 10000 -DatabasePath data\demo-new.db
```

### Bước 4: Khởi động Client (Terminal 2, 3...)

Mở thêm một hoặc nhiều cửa sổ PowerShell mới để đăng nhập đồng thời các vai trò **Admin**, **Seller** và **Bidder**:

```powershell
.\scripts\run-client.ps1
```

Nếu Server chạy ở địa chỉ hoặc cổng khác mặc định (`127.0.0.1:9999`):

```powershell
.\scripts\run-client.ps1 -HostName 127.0.0.1 -Port 10000
```

---

## 3. Tài khoản Demo mặc định (Seed Data)

Khi khởi chạy với database mới, hệ thống tự động tạo sẵn 4 tài khoản mẫu:

| Account | Password | Role | Tổng số dư (cents) | Ghi chú |
|---|---|---|---:|---|
| `admin` | `admin123` | `ADMIN` | `0` | Quản lý người dùng & hủy phiên đấu giá |
| `seller` | `seller123` | `SELLER` | `0` | Tạo vật phẩm & mở/hủy phiên đấu giá của mình |
| `bidder1` | `bidder123` | `BIDDER` | `2,000,000` | Khả dụng `2,000,000` cents |
| `bidder2` | `bidder123` | `BIDDER` | `2,000,000` | Đang dẫn đầu phiên mẫu (`110,000` frozen, `1,890,000` khả dụng) |

> **Lưu ý:** Tài khoản đăng ký mới qua chức năng `REGISTER` mặc định có vai trò `BIDDER` và số dư ví bằng `0`.

---

## 4. Kiểm thử & Đóng gói Artifact

- **Chạy toàn bộ kiểm thử hồi quy (Unit, Integration & Socket E2E):**
  ```powershell
  .\mvnw.cmd test
  ```
- **Dọn output cũ và đóng gói các module `.jar`:**
  ```powershell
  .\mvnw.cmd clean package
  ```
  *(Hoặc dùng script nhóm: `.\scripts\build.ps1 -Clean`)*
- **Chạy riêng bộ kiểm thử nghiệp vụ đấu giá & E2E:**
  ```powershell
  .\mvnw.cmd -pl server test "-Dtest=AutoBidTest,AntiSnipeAndSchedulerTest,SettlementAndEventOrderTest,AuctionEndToEndFlowTest,ManagementSocketIntegrationTest"
  ```
- **Chạy riêng kiểm thử schema & migration database:**
  ```powershell
  .\mvnw.cmd -B -pl common,server -am test "-Dtest=RepositoryIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
  ```

---

## 5. Kịch bản Demo / Rehearsal chuẩn

1. **Seller tạo vật phẩm & mở phiên:** Đăng nhập `seller` / `seller123` $\rightarrow$ tạo vật phẩm mới $\rightarrow$ mở phiên đấu giá (giá khởi điểm `100,000`, bước giá `5,000`, thời lượng `1` giờ).
2. **Đấu giá Realtime (2 Bidder):** Mở 2 cửa sổ Client, đăng nhập `bidder1` và `bidder2` $\rightarrow$ đặt giá qua lại và quan sát giá cao nhất, thông báo `OUTBID`, lịch sử đặt giá, biểu đồ và số dư đóng băng cập nhật tức thì trên cả hai màn hình.
3. **Auto-Bid:** `bidder1` cấu hình đặt giá tự động với mức trần `150,000` $\rightarrow$ `bidder2` đặt giá `120,000` $\rightarrow$ hệ thống tự động nâng giá đối kháng thay cho `bidder1` và dừng đúng mức trần.
4. **Anti-Snipe:** Đặt giá trong những giây cuối trước khi hết giờ $\rightarrow$ thời gian kết thúc phiên (`end_at`) tự động được cộng thêm theo cấu hình.
5. **Settlement & Bid History:** Khi phiên hết giờ, trạng thái tự động chuyển `FINISHED` $\rightarrow$ `PAID`, tiền đóng băng của người thắng được tất toán sang ví Seller, người thua được hoàn tiền đóng băng và toàn bộ lượt đặt giá hiển thị đầy đủ trong **Bid History**.
6. **Admin quản trị:** Đăng nhập `admin` / `admin123` $\rightarrow$ kiểm tra danh sách người dùng, lọc phiên theo trạng thái (`OPEN`, `FINISHED`, `PAID`, `CANCELLED`) và hủy một phiên đang mở để kiểm tra hoàn tiền tức thì cho Bidder.

---

## 6. Chính sách & Quy tắc hệ thống

- **Phân quyền:** `REGISTER` luôn tạo tài khoản `BIDDER`; Seller chỉ quản lý vật phẩm và phiên đấu giá do chính mình tạo; Admin có quyền xem danh sách người dùng và hủy bất kỳ phiên `OPEN` nào.
- **Tài chính & Ví:** Khi đặt giá thành công, số tiền tương ứng được khóa vào `frozen_cents`; khi bị vượt giá (`OUTBID`) hoặc phiên bị hủy (`CANCELLED`), số tiền đóng băng được giải phóng ngay lập tức.
- **Tất toán Idempotent:** Khi phiên kết thúc có người thắng, quá trình tất toán (`SettlementService`) đảm bảo không bao giờ trừ hoặc cộng tiền trùng lặp ngay cả khi scheduler chạy lại.
- **Thứ tự sự kiện Realtime:** Mỗi phiên đấu giá duy trì bộ đếm `sequence` tăng dần nghiêm ngặt; `ResponseEnvelope` của request luôn được gửi về Client trước các `EventEnvelope` phát sinh từ request đó.

---

## 7. Troubleshooting (Xử lý sự cố thường gặp)

| Hiện tượng / Lỗi | Nguyên nhân | Cách khắc phục |
|---|---|---|
| `Execution of scripts is disabled on this system` khi chạy `.ps1` | Windows PowerShell mặc định chặn chạy script cục bộ | Chạy lệnh `Set-ExecutionPolicy -Scope Process Bypass` rồi chạy lại `.\scripts\...` |
| `java.net.BindException: Address already in use` | Cổng `9999` đang bị một tiến trình Server cũ chiếm dụng | Đóng terminal chạy Server cũ (hoặc chạy `Stop-Process -Name java -Force`), hoặc đổi cổng bằng `-Port 10000` |
| `Connection refused` khi mở Client | Client kết nối trước khi Server khởi động xong hoặc nhập sai cổng | Đợi Server in ra `[HEALTH] Auction socket server listening on port 9999` rồi mới khởi động Client |
| `UnsupportedClassVersionError` hoặc lỗi biên dịch Maven | Máy đang dùng JDK phiên bản thấp hơn 21 (JDK 8/11/17) | Cài JDK 21 và trỏ biến `$env:JAVA_HOME` về đúng thư mục cài đặt JDK 21 |
| `SQLITE_BUSY` hoặc lỗi schema do database cũ | File `data/auction.db` còn tiến trình giữ khóa hoặc dùng schema từ bản cũ | Dừng toàn bộ tiến trình Java, xóa file `data/auction.db` (hoặc truyền `-DatabasePath data\fresh.db`) để Server tự tạo lại từ đầu |
| Dòng log `SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"` | Thư viện SQLite JDBC dùng logger mặc định (NOP) khi không cấu hình SLF4J binding | Đây chỉ là thông báo thông tin của thư viện, **không phải lỗi** và không ảnh hưởng tới hoạt động của Server |

---

## 8. Tài liệu kỹ thuật & Báo cáo

- **Kiến trúc Database & ERD:** [ERD v1](docs/architecture/erd-v1.mmd) | [Database README](database/README.md) | [Báo cáo nghiệm thu TV1](docs/week-5-tv1-check-report.md)
- **Giao thức Socket & Contract:** [Protocol v1](docs/contracts/protocol-v1.md)
- **Bản đồ mã nguồn:** [Source Map](docs/source-map.md)
- **Hướng dẫn chạy & Demo chi tiết:** [Local Testing](docs/local-testing.md)
- **Báo cáo kiểm tra Tuần 4:** [Week 4 Check Report](docs/week-4-check-report.md)