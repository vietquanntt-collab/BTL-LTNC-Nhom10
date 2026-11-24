# Chạy bản Gala Auction đã build

Gói runtime cần JDK 21 và hệ điều hành trùng với nơi build JAR client. Bản bàn giao local được build trên Windows x64. Linux cần build source trên Linux hoặc lấy artifact Linux từ CI.

Mở hai terminal ngay tại thư mục chứa START-HERE.md này.

Terminal 1 (server):

~~~powershell
java -version
java -jar jars/server.jar
~~~

Chờ log Auction socket server listening on port 9999. Server tự tạo data/auction.db và seed khi chạy lần đầu.

Terminal 2 (client; chạy lặp lại trong terminal mới để mở thêm client):

~~~powershell
java -jar jars/client.jar
~~~

Tài khoản demo: admin/admin123, seller/seller123, bidder1/bidder123, bidder2/bidder123. Ví mới của hai Bidder: 2,000,000 cents; bidder2 đang giữ 110,000 cents ở phiên mẫu nên khả dụng còn 1,890,000 cents. Tài khoản đăng ký mới có ví 0; không có nạp tiền thật.

Nếu cổng 9999 bận, dùng hai lệnh sau ở hai terminal riêng:

~~~powershell
java -Dauction.server.port=10000 -Dauction.db.path=data/test-new.db -jar jars/server.jar
java -Dauction.server.port=10000 -jar jars/client.jar
~~~

Muốn dùng nhiều máy, máy client truyền thêm -Dauction.server.host=IP_LAN_MAY_SERVER trước -jar. Mọi client dùng cùng IP/cổng của một server. Mỗi server có một DB riêng.

Chạy checklist docs/local-testing.md. Các mục build/test từ source trong README/docs/scripts chỉ dùng ở repository đầy đủ có Maven Wrapper; gói runtime này chạy bằng java -jar. Muốn sửa code và commit, dùng source BTL-week5-check và làm theo docs/git-handoff.md.

SHA256SUMS.txt ghi hash hai JAR của gói. Đọc docs/week-5-check-report.md và docs/verification-results.md để xem bằng chứng local và các mục nhóm còn cần nghiệm thu. Dừng server bằng Ctrl+C ở đúng terminal khi test xong.
