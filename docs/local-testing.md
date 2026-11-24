# Kịch bản nghiệm thu nhóm

Mở terminal ở thư mục gốc có README/pom.xml. Các lệnh Windows dùng bypass chỉ ở tiến trình hiện tại; không cần đổi ExecutionPolicy toàn máy.

## 1. Bộ kiểm thử tự động

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Clean -Ui
~~~

Phải có BUILD SUCCESS; đọc từng module/target/surefire-reports, target/site/jacoco và target/checkstyle-result.xml. Gate line coverage 60% áp riêng từng module khi verify. Bỏ -Ui trong build.ps1 chạy package để test nhanh, chưa nghiệm thu coverage/UI. Có -Ui mới thực sự chạy ClientUiTest; bỏ -Ui sẽ skip test giao diện. Các test dùng DB riêng (memory hoặc thư mục tạm), không sửa data/auction.db của bạn.

Chạy nhanh các nhóm cần kiểm tra (JAVA_HOME=JDK21):

~~~powershell
.\mvnw.cmd -B test '-Dtest=RepositoryIntegrationTest,ItemImageUpgradeTest' '-Dsurefire.failIfNoSpecifiedTests=false'
.\mvnw.cmd -B test '-Dtest=AutoBidTest,AntiSnipeAndSchedulerTest,SettlementAndEventOrderTest,AuctionEndToEndFlowTest' '-Dsurefire.failIfNoSpecifiedTests=false'
.\mvnw.cmd -B test '-Dtest=NetworkClientTest,ClientUiTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DrunUiTests=true'
~~~

Luôn chạy từ gốc reactor. Không dùng -pl server test đơn lẻ nếu chưa install common. Luồng UI dùng FXML/controller thật và server TCP thật; các ảnh scene được tạo tại client/target/ui-snapshots. Test so chiều rộng nội dung ScrollPane với viewport ở 1100×720 và 1280×800. Test không thay thế người dùng xem độ đẹp, độ rõ chữ, DPI, bàn phím và bố cục trên màn hình máy nhóm.

## 2. Khởi động để test bằng tay

Terminal server:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-server.ps1 -Port 10000 -DatabasePath data/rehearsal-new.db
~~~

Nếu file rehearsal-new.db đã tồn tại, dùng tên DB mới khác để có seed mới, không xóa DB đang dùng. Chờ log server listening on port 10000.

Mở bốn terminal client, cùng chạy:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-client.ps1 -HostName 127.0.0.1 -Port 10000
~~~

Đăng nhập lần lượt admin/admin123, seller/seller123, bidder1/bidder123 và bidder2/bidder123. Sau khi build có thể chạy nhanh java -Dauction.server.port=10000 -jar client/target/client.jar từ gốc dự án.

## 3. Checklist thực hành 10–15 phút

Các ô dưới để nhóm tự tick sau khi thao tác trên máy của mình, chưa phải kết quả người dùng đã nghiệm thu.

| Kiểm tra | Thao tác và kết quả cần thấy | Người kiểm tra |
|---|---|---|
| [ ] Đăng nhập/đăng ký | Mật khẩu sai báo lỗi, xác nhận mật khẩu sai không gửi; tài khoản mới là BIDDER ví 0; không có menu Seller/Admin. | TV2 + TV3 |
| [ ] Seller CRUD | Chọn preset Cúp Euro, tạo được FIGURE có ảnh; sửa tên, đổi ảnh, bỏ ảnh; tải lại đúng. Xóa được vật phẩm chưa có auction. | TV1 + TV3 |
| [ ] Mở phiên | Tạo item mới; mở phiên giá 100000, bước 5000, 1 giờ; có mã phiên mới. Phiên có thể ở OPEN tới lượt bid hợp lệ đầu tiên. | TV2 |
| [ ] Hạn chế sau mở phiên | Sửa/xóa item đã mở phiên bị từ chối; Seller khác không được sửa hoặc hủy item/phiên của người khác (có test socket). | TV2 + TV4 |
| [ ] Realtime | Hai Bidder mở cùng mã phiên mới. Bidder1 trả 105000; Bidder2 trả 110000. Cả hai thấy giá/lịch sử/biểu đồ, bidder1 được giải phóng hold của phiên, bidder2 giữ 110000. | TV3 + TV4 |
| [ ] Bid sai | Thử chữ, số âm, giá dưới tối thiểu, giá vượt số dư; báo lỗi và DB không có bid sai. Nhấp nhiều lần không tạo hai yêu cầu đang gửi. | TV2 + TV3 |
| [ ] Auto-Bid | Bidder1 nhập trần 150000, bước 5000 và bật; xem các lượt tự động. Bidder2 trả mức hợp lệ lớn hơn giá hiện tại và cuối cùng 155000; Auto-Bid không vượt trần. Tắt Auto-Bid, trạng thái đổi, giá dẫn đầu hiện có vẫn giữ tiền tới khi outbid/đóng phiên. | TV2 + TV4 |
| [ ] Admin | Xem 4 tài khoản seed/số dư, chọn đúng phiên và hủy. Phiên CANCELLED, hold của phiên được trả, phiên khác không bị ảnh hưởng. | TV1 + TV4 |
| [ ] Phiên đóng | Mở lại phiên CANCELLED/PAID; xem kết quả/lịch sử, không lỗi NullPointerException; nút bid/Auto-Bid bị khóa. | TV3 |
| [ ] Chuyển màn hình | Đi danh sách/chi tiết/Seller/Admin nhiều lần, đăng xuất/đăng nhập vai trò khác; không thêm timer/listener cũ hay hiển thị dữ liệu tài khoản trước. | TV3 |
| [ ] Mất kết nối | Dừng đúng server bằng Ctrl+C, quan sát trạng thái reconnect. Khởi động lại với cùng DB/cổng: session trong RAM đã hết hạn, client cần về login, đăng nhập lại. | TV2 + TV3 |
| [ ] LAN | Một máy chạy server, các máy client dùng IP LAN của server. Cùng mã phiên phải thấy cùng dữ liệu; mỗi người chạy DB riêng thì không thể thấy realtime của nhau. | Cả nhóm |
| [ ] Bố cục | Cửa sổ 1100×720/1280×800; thử Windows scaling 100% và 125%, tên/mô tả dài, bảng rỗng, loading/lỗi. Cuộn dọc thấy đủ nút và chart/history. | TV3 |

Auto-Bid có thể đặt giá ngay khi bật. Luôn đọc giá hiện tại/mức tối thiểu trước lượt tiếp theo; không dựa vào mức giá cố định từ kịch bản cũ.

## 4. Anti-Snipe và settlement

UI hiện tạo phiên theo số giờ (1–720), nên kịch bản nhanh dùng test server thay vì sửa trực tiếp DB đang chạy:

~~~powershell
.\mvnw.cmd -B test '-Dtest=AntiSnipeAndSchedulerTest,SettlementAndEventOrderTest,AuctionEndToEndFlowTest,ManagementSocketIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
~~~

Các test tạo auction thời hạn ngắn trong DB riêng, kiểm tra anti-snipe 30 giây/gia hạn 30 giây/tối đa 5 lần (có policy fixture riêng), scheduler đóng một lần, retry FINISHED, chuyển tiền đúng và không có hai winner. Demo UI thật cuối phiên cần chuẩn bị phiên đủ gần giờ kết thúc bằng dữ liệu demo có kiểm soát hoặc chờ phiên; không rút ngắn/xóa dữ liệu người khác để demo.

## 5. Gói bàn giao và điều kiện PR

- Chạy package-release.ps1 theo README; gửi gói native đúng OS, người nhận mở terminal tại gốc gói và java -jar jars/server.jar / jars/client.jar ở hai terminal.
- TV1 kiểm schema/migration, số dư trước-sau và ERD; TV2 kiểm socket/auth/transaction; TV3 xem UI trên máy thật; TV4 đối chiếu test report/CI và điều phối rehearsal.
- Lưu lỗi kèm vai trò, mã phiên, bước tái hiện, log liên quan và commit kiểm tra. Không đưa mật khẩu/token thật vào issue.
- Chỉ đề xuất PR develop -> main khi checklist nhóm, source attribution, đối chiếu rubric, CI và báo cáo/slide được xác nhận trong week-5-check-report.md.
