# SQLite local và nâng cấp dữ liệu

Server tự chạy DatabaseInitializer trước khi nhận kết nối; không cần cài SQLite CLI hoặc DB cloud. Mỗi kết nối bật foreign_keys, tiền dùng integer cents và thời gian ISO-8601 UTC.

Có **5 migration** được ghi trong schema_migrations, theo thứ tự tên file:

1. V001__core_schema.sql: users, wallets, items, auctions, bids.
2. V002__advanced_schema.sql: auto_bid_rules, settlements, auction_events, request_receipts.
3. V003__bid_history.sql: audit lịch sử bid và index.
4. V004__week4_settlement_request.sql: request idempotency cho settlement.
5. V005__item_images.sql: thêm image_base64 cho database cũ.

Tên migration là định danh đã áp dụng, nên giữ nguyên tên V004 dù có chữ week4. Không đổi tên/xóa migration cũ. Các file chức năng Java/CSS/FXML có tên dùng lâu dài.

Một số bản develop đầu tuần 5 đã thêm image_base64 trực tiếp vào V001. DatabaseInitializer kiểm tra cột trước V005: nếu đã có cột thì chỉ ghi nhận migration, nếu chưa thì ALTER TABLE. Cả database tuần 4 và database đầu tuần 5 đều nâng cấp được, không xóa dữ liệu. ItemImageUpgradeTest tái hiện database cũ và kiểm tra chạy lại an toàn.

Chạy từ thư mục gốc có database/migrations:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .scripts
un-server.ps1
~~~

Database mặc định data/auction.db. Để thử một database mới mà giữ dữ liệu cũ:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .scripts
un-server.ps1 -Port 10000 -DatabasePath data/test-fresh.db
~~~

Seed chứa 4 tài khoản demo trong README. Seed dùng INSERT OR IGNORE và chỉ khởi tạo giá phiên mẫu khi version=0; test kiểm tra chạy migration/seed lại không nhân bản dữ liệu. Seed demo không phải công cụ phục hồi dữ liệu đã bị sửa/xóa thủ công. Không dùng seed cho dữ liệu production.

Ảnh item: Base64 tối đa 512 KiB. UPDATE_ITEM thiếu imageBase64 thì giữ ảnh cũ; chuỗi rỗng hoặc null thì xóa ảnh. Vật phẩm đã có auction không được sửa/xóa. Kiểm thử NetworkClientTest đi qua route thật để kiểm tra thay/giữ/xóa ảnh.

Không commit data/*.db, WAL/SHM, cache hoặc target/. Xem [data dictionary](data-dictionary.md), [ERD](../docs/architecture/erd-v1.mmd) và [báo cáo bàn giao](../docs/week-5-check-report.md).
