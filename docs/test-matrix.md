\# MA TRẬN KIỂM THỬ (TEST MATRIX) - HỆ THỐNG ĐẤU GIÁ



| Mã test | Hạng mục kiểm thử | Kịch bản kiểm thử | Kết quả mong đợi | Loại kiểm thử |

| :--- | :--- | :--- | :--- | :--- |

| \*\*TC-AUTH-01\*\* | Xác thực tài khoản | Đăng nhập tài khoản hợp lệ | Cấp Token xác thực thành công, phân đúng Role | Unit Test |

| \*\*TC-AUTH-02\*\* | Phân quyền bảo mật | Bidder gọi chức năng duyệt hàng của Admin | Bị chặn, trả về lỗi PERMISSION\_DENIED | Security Test |

| \*\*TC-ITEM-01\*\* | Quản lý sản phẩm | Seller đăng thẻ Pokémon mới | Tạo thành công, trạng thái PENDING chờ duyệt | Unit Test |

| \*\*TC-BID-01\*\* | Đặt giá hợp lệ | Bidder đặt giá > giá hiện tại + bước giá | Server chấp nhận, gửi thông báo OUTBID cho người cũ | Unit Test |

| \*\*TC-BID-02\*\* | Đặt giá không hợp lệ | Bidder đặt giá thấp hơn giá hiện tại | Báo lỗi INVALID\_BID\_AMOUNT, từ chối | Unit Test |

| \*\*TC-CONC-01\*\* | Xử lý đa luồng | 10 luồng cùng đặt giá vào 1 phiên trong 1 giây | Không mất dữ liệu, chỉ đúng 1 người dẫn đầu | Concurrency Test |

| \*\*TC-AUTO-01\*\* | Tự động đặt giá | Đặt giá trần, có người vượt mặt | Tự động tăng 1 bước giá, không vượt trần | Policy Test |

| \*\*TC-SNIP-01\*\* | Chống bắn tỉa | Có lượt đặt giá trong 30 giây cuối cùng | Đồng hồ tự động gia hạn thêm +30 giây | Integration Test |

| \*\*TC-SETTLE-01\*\*| Chốt phiên | Scheduler chạy quét hết giờ nhiều lần | Trừ tiền người thắng đúng 1 lần, không lặp | Idempotent Test |

