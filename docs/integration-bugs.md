# Nhật ký theo dõi lỗi tích hợp (Integration Bug Log) - Tuần 2

Tài liệu ghi chép các sự cố, sai lệch format giao thức TCP, lỗi serialization hoặc sai hỏng trạng thái UI giữa Server (TV2) và Client (TV3) trong quá trình tích hợp baseline Tuần 2.

## Bảng theo dõi sự cố

| STT | Mã lỗi / Tính năng | Thành phần | Mô tả chi tiết lỗi | Trạng thái | Người xử lý | Ghi chú / Phương án khắc phục |
|---|---|---|---|---|---|---|
| 01 | AUTH_01 | Client <-> Server | Client phải kiểm tra dữ liệu đăng nhập rỗng trước khi gửi socket | Closed | TV3 | `LoginController` chặn tài khoản hoặc mật khẩu trống và hiển thị lỗi ngay trên màn hình |
| 02 | AUCTION_01 | Server <-> DB | Tạo Auction mới trả về `ERR_500` do chưa migration xong dữ liệu seed | Closed | TV1 / TV2 | `DatabaseInitializer` chạy migration/seed idempotent trước khi server mở socket; đã có integration test |

---

## Quy trình ghi nhận lỗi của TV4

1. **Phát hiện:** Khi chạy Smoke Test hoặc kiểm thử thủ công luồng kết nối Client - Server.
2. **Ghi chép:** Thêm dòng mới vào bảng trên với trạng thái `Open`.
3. **Phân công:** Báo cho thành viên phụ trách (TV1/TV2/TV3) để xử lý.
4. **Nghiệm thu:** Chạy lại test suite, nếu PASS thì cập nhật trạng thái thành `Closed`.
