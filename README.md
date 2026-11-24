# Gala Auction · Nhóm 10

Ứng dụng đấu giá Java 21: JavaFX/FXML ở client, TCP/JSON và SQLite local ở server. Có đăng ký/đăng nhập, phân quyền Admin/Seller/Bidder, quản lý vật phẩm, đặt giá realtime, Auto-Bid, Anti-Snipe, lịch sử/biểu đồ, hủy phiên và tất toán ví.

Bản bàn giao tuần 5 bắt đầu từ develop **8af20c4**, đã tích hợp và kiểm tra phần giao diện PR #36 ở **abe5265** ngày 2026-11-24. Lượt clean verify có UI cuối: **98 test**, 0 failure/error/skipped; Checkstyle 0 violation; line coverage từng module trên 60%. Xem [báo cáo kiểm tra](docs/week-5-check-report.md) để biết kết quả kiểm thử và các việc nhóm cần xác nhận trước khi đưa lên main. [Hướng dẫn test](docs/local-testing.md) và [hướng dẫn tự commit/push](docs/git-handoff.md) nằm trong thư mục docs.

## 1. Chuẩn bị

- JDK **21**, có cả java và javac. Không cần cài Maven, SQLite server hoặc JavaFX riêng.
- Internet trong lần build đầu tiên để Maven Wrapper tải thư viện.
- Windows 10/11 x64 đã được kiểm thử local. Client cần desktop; kích thước cửa sổ tối thiểu 1100×720.
- Linux cần desktop hoặc Xvfb khi chạy test JavaFX. CI có cấu hình Windows/Linux; kết quả CI trên commit mới chỉ có sau khi nhóm push.

Mở PowerShell tại thư mục chứa README này. Kiểm tra:

~~~powershell
java -version
javac -version
~~~

Nếu có nhiều JDK, script tự tìm JDK 21 qua JAVA_HOME/PATH. Có thể chỉ định cho cửa sổ hiện tại (đổi thành đường dẫn thật trên máy bạn):

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
~~~

## 2. Build và kiểm thử trên Windows

Các lệnh dưới dùng ExecutionPolicy Bypass **chỉ cho tiến trình được mở**, nên dùng được khi Windows chặn chạy .ps1:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Clean -Ui
~~~

Lệnh này chạy clean verify với toàn bộ test gồm JavaFX và tạo:

- server/target/server.jar
- client/target/client.jar
- Báo cáo test tại mỗi module/target/surefire-reports.
- Coverage HTML/CSV/XML tại mỗi module/target/site/jacoco; line gate 60% cho từng module.
- Checkstyle tại mỗi module/target/checkstyle-result.xml theo bộ baseline naming/file hygiene.
- Ảnh scene JavaFX tại client/target/ui-snapshots và số đo tại client/target/ui-layout.tsv.

Build không cần desktop/test UI: bỏ -Ui để chạy package với JUnit/Checkstyle. Khi đó ClientUiTest được skip có chủ ý, không chạy gate coverage ở verify. Nghiệm thu đầy đủ cần lệnh có -Ui; không coi lượt skip UI là đã nghiệm thu giao diện.

Maven trực tiếp (JAVA_HOME phải trỏ JDK 21):

~~~powershell
.\mvnw.cmd -B clean verify '-DrunUiTests=true'
~~~

## 3. Chạy server và nhiều client

Terminal 1 tại thư mục gốc:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-server.ps1
~~~

Chờ log Auction socket server listening on port 9999. Server tự tạo data/auction.db, áp dụng 5 migration và seed. Mỗi server dùng một database riêng; không mở hai server ghi cùng file.

Terminal 2 (mỗi cửa sổ client thêm một terminal):

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-client.ps1
~~~

Sau khi build, có thể chạy nhanh **không gọi Maven**:

~~~powershell
java -jar .\server\target\server.jar
~~~

Ở terminal khác, cùng thư mục gốc:

~~~powershell
java -jar .\client\target\client.jar
~~~

Client JAR chứa native JavaFX của hệ điều hành build. Dùng gói Windows cho Windows và gói Linux cho Linux.

Đổi cổng và dùng một DB mới mà giữ dữ liệu cũ:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-server.ps1 -Port 10000 -DatabasePath data/test-fresh.db
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-client.ps1 -HostName 127.0.0.1 -Port 10000
~~~

Hai lệnh trên chạy trong **hai terminal riêng**. Để các máy trong nhóm dùng chung server, thay HostName bằng IP LAN máy server và dùng chung cổng.

## 4. Tài khoản demo

| Tài khoản | Mật khẩu | Vai trò | Tổng số dư ban đầu |
|---|---|---|---:|
| admin | admin123 | ADMIN | 0 cents |
| seller | seller123 | SELLER | 0 cents |
| bidder1 | bidder123 | BIDDER | 2,000,000 cents |
| bidder2 | bidder123 | BIDDER | 2,000,000 cents |

Database mới có phiên mẫu #1 giá 110,000 cents, bidder2 đang dẫn đầu với 110,000 cents đang giữ; số dư khả dụng còn 1,890,000 cents. Bidder1 đang có 2,000,000 cents khả dụng. Đăng ký mới tạo BIDDER có ví 0; dùng tài khoản seed để test đặt giá. Hệ thống chưa có chức năng nạp tiền thật. Tiền hiển thị là **integer cents**, chưa quy đổi thành VND.

## 5. Test nhanh trước khi nhóm review

1. Mở 4 client: Seller, Admin và hai Bidder. Kiểm tra menu đúng vai trò.
2. Seller tạo vật phẩm (có thể chọn mẫu Cúp Euro), sửa tên/ảnh khi chưa mở phiên, rồi mở phiên.
3. Hai Bidder cùng vào **một mã phiên**, đặt giá qua lại. Kiểm tra giá, lịch sử, biểu đồ và số dư cập nhật.
4. Bật Auto-Bid, thử đối thủ trả giá và tắt Auto-Bid. Không vượt giá trần.
5. Admin hủy phiên khác đang mở; xem người dẫn đầu được giải phóng tiền đang giữ.
6. Mở phiên đã hủy hoặc đã hết giờ: vẫn xem được kết quả/lịch sử, nút đặt giá bị khóa.
7. Chạy kiểm thử Anti-Snipe/settlement để kiểm chứng luồng cuối phiên mà không cần chờ một giờ. Kịch bản chi tiết nằm trong [local-testing](docs/local-testing.md).

## 6. Xuất gói để gửi nhóm

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\package-release.ps1
~~~

Script build/test rồi tạo thư mục mới release-bundle/local-... chứa jars/, database/, scripts/, docs/, README, START-HERE.md và SHA256SUMS.txt. Người nhận gói chạy đọc START-HERE.md trước. Bộ source dùng để tự commit vẫn là thư mục BTL-week5-check. Có thể dùng -SkipBuild sau khi chính bạn đã chạy build đầy đủ với -Ui thành công. Người nhận mở terminal **tại gốc gói**:

~~~powershell
java -jar jars/server.jar
~~~

Terminal khác tại gốc gói:

~~~powershell
java -jar jars/client.jar
~~~

Bạn tự tạo ZIP khi cần gửi nhóm. Hướng dẫn docs/git-handoff.md chép source trực tiếp sang checkout mới từ develop, giữ .mvn và data/.gitkeep, nên không cần ZIP để commit/push. Gói chạy release-bundle/local-* có thể nén riêng rồi gửi người chỉ cần chạy thử.

Gói runtime không có mã nguồn/Maven Wrapper; dùng lệnh java -jar trong gói. Các script build/run từ source chỉ dùng ở bản source đầy đủ. Không cần commit target/, cache, database runtime hoặc release-bundle/ lên GitHub.

## 7. Linux

Build/test không UI (package, chưa chạy gate coverage):

~~~bash
chmod +x mvnw
./mvnw -B clean package
~~~

Test UI trên desktop Linux hoặc màn hình ảo (cần gói xvfb):

~~~bash
xvfb-run -a ./mvnw -B clean verify -DrunUiTests=true
~~~

Chạy từ thư mục gốc source hoặc gốc gói release tương ứng:

~~~bash
java -jar server/target/server.jar
# Terminal khác:
java -jar client/target/client.jar
~~~

## 8. Xử lý lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| scripts is disabled | Dùng lệnh powershell.exe -ExecutionPolicy Bypass ở trên, hoặc Set-ExecutionPolicy -Scope Process Bypass cho đúng terminal hiện tại. |
| JAVA_HOME đang là JDK khác 21 | Đặt JAVA_HOME tới thư mục JDK21 có bin/javac; không trỏ tới bin. |
| Address already in use | Dừng đúng terminal server của dự án bằng Ctrl+C hoặc chọn cổng khác và cùng cổng ở client. |
| Connection refused | Đợi server bind cổng, kiểm tra IP/cổng. Khi test LAN, cấu hình firewall Private cho Java/cổng server. |
| Cannot find database/migrations | Chạy server từ thư mục có database/, hoặc truyền -Dauction.database.root=đường-dẫn-thật trước -jar. |
| Không sửa/xóa được item | Vật phẩm đã có phiên bị khóa để giữ lịch sử; tạo vật phẩm mới để test CRUD. |
| Ví 0, không đủ tiền | Dùng bidder1/bidder2 trên DB mới. Tài khoản đăng ký mới không có tiền demo. |
| SQLITE_BUSY | Kiểm tra có server khác dùng cùng DB; dừng đúng server hoặc chọn DatabasePath mới. |
| Không có DISPLAY trên Linux | Chạy test mặc định không UI, hoặc dùng xvfb-run cùng -DrunUiTests=true. |
| SLF4J binding / JavaFX unnamed module warning | Đây là cảnh báo dependency hiện tại. Kiểm tra test count, lỗi và exit code; cảnh báo không tự chứng minh pass/fail. |

## 9. Tài liệu

- [Bàn giao và đánh giá yêu cầu](docs/week-5-check-report.md)
- [Đối chiếu hai PDF cập nhật](docs/requirements-review.md)
- [Số liệu kiểm thử cuối](docs/verification-results.md)
- [Kịch bản test nhóm](docs/local-testing.md)
- [Tự commit/push và mở PR](docs/git-handoff.md)
- [Protocol thực tế](docs/contracts/protocol-v1.md)
- [Quyết định kiến trúc bản bàn giao](docs/adr/ADR-004-release-contract.md)
- [Class diagram](docs/architecture/class-diagram.mmd), [ERD](docs/architecture/erd-v1.mmd), [data dictionary](database/data-dictionary.md)
- [Source map và các mục cần chủ sở hữu xác nhận](docs/source-map.md)
