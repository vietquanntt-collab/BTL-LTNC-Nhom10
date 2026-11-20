# Nhóm 10 – Online Auction System

Bản tích hợp bốn tuần đầu gồm TCP/JSON server, JavaFX client và SQLite local. Nhóm đã có đăng ký/đăng nhập, phân quyền, Seller CRUD item, mở/hủy auction, đấu giá realtime, Auto-Bid, Anti-Snipe, scheduler, settlement và dashboard Admin.

Bản rà soát lấy nền từ develop, commit ba745b80db4b922375df0d0306fbe3004bb85a08. Thay đổi bàn giao được để chưa commit để người nhận tự review, tạo nhánh và push.

## Chạy trên Windows

Cài **JDK 21** và Git. Maven Wrapper và JavaFX dependency đã có trong project; lần chạy đầu cần Internet để tải dependency.

Mở PowerShell tại thư mục chứa README:

    java -version
    .\scripts\build.ps1

Nếu máy có nhiều JDK, đặt JDK 21 cho terminal hiện tại:

    $env:JAVA_HOME = '<thu-muc-JDK-21>'

Nếu PowerShell chặn script:

    Set-ExecutionPolicy -Scope Process Bypass

Terminal 1 – server:

    .\scripts\run-server.ps1

Terminal 2 – client:

    .\scripts\run-client.ps1

Server mặc định dùng cổng 9999, tự migrate/seed database ở data/auction.db. Muốn dữ liệu demo mới:

    .\scripts\run-server.ps1 -Port 10000 -DatabasePath data\demo-new.db
    .\scripts\run-client.ps1 -HostName 127.0.0.1 -Port 10000

| Account | Password | Role | Tổng số dư demo |
|---|---|---|---:|
| admin | admin123 | ADMIN | 0 |
| seller | seller123 | SELLER | 0 |
| bidder1 | bidder123 | BIDDER | 2.000.000 cents |
| bidder2 | bidder123 | BIDDER | 2.000.000 cents |

Bidder2 đang dẫn đầu phiên seed với 110.000 cents đóng băng; khả dụng ban đầu là 1.890.000 cents. Tài khoản đăng ký mới có role BIDDER và ví bằng 0.

## Kiểm thử

    .\scripts\build.ps1

Có thể dọn output rồi build sau khi đóng tiến trình Java/Maven:

    .\scripts\build.ps1 -Clean

Chạy riêng bảy test socket quản lý:

    .\mvnw.cmd -B -pl common,server -am test "-Dtest=ManagementSocketIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"

Kết quả kiểm tra và phạm vi đã sửa được ghi ở [báo cáo tuần 4](docs/week-4-check-report.md).

## Luồng test cho cả nhóm

1. Đăng nhập seller, tạo item, sửa rồi xóa một item chưa mở auction.
2. Tạo item khác, mở auction: giá 100000, bước 5000, thời lượng 1 giờ.
3. Hai client đăng nhập bidder1/bidder2, đặt giá và quan sát realtime, lịch sử, biểu đồ và số dư ví.
4. Cấu hình Auto-Bid; client còn lại đặt giá để kiểm tra không vượt giá trần.
5. Admin xem users, lọc auction và hủy phiên đang mở. Bidder nhận CANCELLED và được giải phóng tiền.
6. Bộ test tự động kiểm tra Anti-Snipe, scheduler, no-winner, retry settlement và event order.

Kịch bản chi tiết: [hướng dẫn chạy và demo](docs/local-testing.md).

## Contract và chính sách

- REGISTER tạo BIDDER; client không tự chọn role.
- LOGIN trả userId, role, balanceCents, frozenCents và session.
- LIST_AUCTIONS không có status giữ tương thích OPEN/RUNNING; status ALL trả cả FINISHED/PAID/CANCELLED.
- Seller chỉ quản lý item/auction của mình; Admin xem users và hủy auction.
- Hủy phiên giải phóng đúng frozen balance, vô hiệu hóa Auto-Bid và phát event sau commit.
- Hết giờ chuyển FINISHED rồi PAID. Settlement retry không chuyển tiền lần hai.
- Event có sequence từng auction; response được trả trước event phát sinh từ request đó.

Chi tiết ở [protocol v1](docs/contracts/protocol-v1.md).

## Tự tạo nhánh, commit và push

Folder BTL-week4-check là repository độc lập có lịch sử nhóm. Mở terminal tại folder này:

    git status
    git switch -c feature/week4-check
    .\scripts\build.ps1
    git diff --check
    git add -A
    git diff --cached --stat
    git diff --cached --check
    git commit -m "fix: complete week four integration and handoff"
    git push -u origin feature/week4-check

Nếu muốn chia commit theo chức năng, xem [hướng dẫn commit](docs/handoff-week4.md). Mọi thay đổi được để unstaged; không có commit hoặc push tự động trong bản bàn giao.

Sau khi push, mở Pull Request từ feature/week4-check vào develop, chờ CI và review của các owner.

## Tài liệu bàn giao

- [Báo cáo kiểm tra bốn tuần](docs/week-4-check-report.md).
- [Checklist và hướng dẫn commit](docs/handoff-week4.md).
- [Hướng dẫn chạy và demo](docs/local-testing.md).
- [Protocol](docs/contracts/protocol-v1.md), [database](database/README.md), [nguồn và điều chỉnh](docs/source-map.md).

Target, JAR, database runtime, JavaFX cache và cấu hình riêng được ignore.

