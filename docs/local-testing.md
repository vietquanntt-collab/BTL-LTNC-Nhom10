# Chạy và demo local

## Build

PowerShell tại thư mục repository:

    java -version
    .\scripts\build.ps1

Script yêu cầu JDK 21 và tự kiểm tra JAVA_HOME/PATH. Nếu nhiều JDK, đặt `$env:JAVA_HOME` tới thư mục JDK 21.

## Server

Terminal 1:

    .\scripts\run-server.ps1

Server mặc định dùng `127.0.0.1:9999`, tạo `data/auction.db`, chạy migration và seed. Đổi cổng/DB:

    .\scripts\run-server.ps1 -Port 10000 -DatabasePath data\demo.db

Không chạy hai server cùng ghi một DB.

## Client

Mỗi client chạy ở một terminal riêng:

    .\scripts\run-client.ps1

Nếu server ở máy khác:

    .\scripts\run-client.ps1 -HostName 192.168.1.20 -Port 9999

Cho phép Java qua Windows Firewall trên mạng Private nếu Windows hỏi.

## Tài khoản

- `admin/admin123`: Admin dashboard, user list, hủy phiên.
- `seller/seller123`: tạo/sửa/xóa item chưa list, mở/hủy auction.
- `bidder1/bidder123`, `bidder2/bidder123`: bid và Auto-Bid.
- REGISTER tạo Bidder mới với ví 0.

## Demo

1. Seller tạo item, sửa item, mở auction.
2. Hai Bidder đặt giá; quan sát event, lịch sử và ví.
3. Bật Auto-Bid; thử giá vượt trần.
4. Đặt giá gần cuối để Anti-Snipe gia hạn.
5. Admin lọc users/status hoặc Seller hủy auction; kiểm tra hold được trả.
6. Chạy test scheduler thay vì chờ một giờ:

       .\mvnw.cmd -B -pl common,server -am test "-Dtest=ManagementSocketIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"

## Lỗi thường gặp

- `Can JDK 21`: đặt JAVA_HOME tới thư mục JDK 21, không trỏ `bin`.
- Thiếu dependency: chạy `scripts\build.ps1` trước để Maven tải dependency.
- Cổng đang dùng: đổi `-Port` và dùng cùng cổng ở client.
- Không sửa/xóa được item: item đã mở auction bị khóa giữ lịch sử.
- Không đặt giá được: chỉ Bidder được bid; dùng account seed vì tài khoản mới có balance 0.

