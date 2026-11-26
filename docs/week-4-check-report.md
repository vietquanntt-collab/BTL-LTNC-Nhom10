# Báo cáo rà soát tích hợp bốn tuần đầu

Bản bàn giao lấy nền từ nhánh develop tại commit ba745b80db4b922375df0d0306fbe3004bb85a08. SHA này đã được đối chiếu với GitHub trong phiên kiểm tra. Các sửa đổi bổ sung đang chưa commit trong BTL-week4-check.

## Lỗi tích hợp đã sửa

| Vấn đề ở develop | Kết quả trong bản bàn giao |
|---|---|
| UI gửi REGISTER nhưng server chưa nhận route | Tạo BIDDER và wallet, PBKDF2, duplicate/validation; client không tự chọn Admin |
| Login không trả số dư mà shell cần | Response có balanceCents và frozenCents |
| UI Seller gửi LIST/UPDATE/DELETE_ITEM nhưng server thiếu | Đủ route, kiểm tra role/ownership; item đã niêm yết không sửa/xóa |
| Bộ lọc bị bỏ qua; client không gửi ALL | Lọc trạng thái và phân trang; ALL gồm phiên đã đóng |
| Admin thiếu API users/cancel | Users chỉ dành cho Admin; Seller chỉ hủy phiên của mình |
| Hủy phiên có thể để tiền bị khóa | Transaction giải phóng tiền của phiên, tắt Auto-Bid, xóa leader, event sau commit, retry an toàn |
| Settlement phiên không có winner so sánh sai với giá khởi điểm | Số tiền settlement bằng 0; không chuyển tiền |
| Scheduler chỉ thử settle ngay lúc đóng, có thể bỏ lại FINISHED khi lỗi | Retry FINISHED, transaction thanh toán độc lập; không publish thanh toán chưa commit |
| Seed có bid ACTIVE nhưng thiếu winner/frozen balance | Leader và số tiền giữ được đồng bộ; seed không ghi đè balance hoặc bid mới |
| JAVA_HOME có thể trỏ JDK khác PATH; chọn JAR không cố định | Script chọn JDK 21 và dependency đúng phiên bản POM |
| Admin thấy menu Seller; Seller/Admin có thể bấm bid | Menu và controls khớp quyền server |
| README vẫn nói chờ tích hợp TV2/TV3/TV4 | README mô tả bản bốn tuần và cách chạy/commit/push |

## Bằng chứng kiểm thử

Bộ test trước bổ sung: 18 common + 43 server PASS. Bảy test mới trong ManagementSocketIntegrationTest đã PASS bằng JDK 21:

1. Đăng ký, password hash, whitespace password, duplicate và không nâng role từ payload.
2. Seller/Admin sửa/xóa item, sở hữu, partial update và danh sách của Seller.
3. Khóa item đã niêm yết và duplicate auction conflict.
4. ALL/status, phân trang kể cả số lớn, danh sách/filter user và phân quyền Admin.
5. Hủy phiên, release hold, giữ tiền phiên khác, event và retry không hoàn lặp.
6. Scheduler không winner và rollback/retry transfer lỗi, không double payment.
7. Input sai, chưa login/logout và socket tiếp tục dùng được.

Bộ test kế thừa bao phủ Auto-Bid trần/tie-break, Anti-Snipe/max extension, concurrency, idempotency, history, Observer, socket realtime và luồng end-to-end.

Lệnh xác nhận cuối, kết quả và giới hạn kiểm tra giao diện được cập nhật sau khi chạy trên chính thư mục bàn giao.

## Các việc nhóm vẫn phải nghiệm thu

- Review chéo phần sửa server với TV2 và contract/test với TV4; GitHub CI chỉ có kết quả sau khi người dùng push/mở PR.
- Chạy giao diện trên máy từng thành viên, kiểm tra layout, reconnect và demo nhiều client theo docs/local-testing.md.
- Chốt báo cáo/slide, stress test và artifact chạy độc lập theo kế hoạch tuần 5; JAR mặc định của Maven chưa là gói release kèm toàn bộ dependency.
- Domain hiện dùng record DTO và enum Role/ItemType; sơ đồ User/Bidder/Seller và Item/Card/... ban đầu là thiết kế dự kiến, chưa có hệ kế thừa và Factory tương ứng trong code. Nếu rubric bắt buộc các lớp/pattern này, nhóm phải hoàn thiện trước nộp.
- Tài khoản đăng ký mới có ví 0; dùng Bidder seed để test tiền. Chưa có chức năng nạp tiền.

Vì các mục trên, bản này bàn giao để cả nhóm chạy test tích hợp; không dùng báo cáo này để khẳng định đã đạt toàn bộ rubric cuối kỳ.

