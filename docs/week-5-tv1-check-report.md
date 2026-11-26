# TV1 tuần 5 · cập nhật tích hợp

Cập nhật ngày 2026-11-24 trong checkout BTL-week5-check, nền develop 8af20c4, đã tích hợp giao diện PR #36 (abe5265). [Báo cáo toàn nhóm](week-5-check-report.md) và [đối chiếu hai PDF mới](requirements-review.md) là kết quả hiện tại.

- Có 5 migration V001–V005; V005 nâng cấp cột ảnh cho DB tuần 4, tương thích DB đầu tuần 5 đã có cột. ItemImageUpgradeTest kiểm giữ dữ liệu và upgrade lặp lại.
- RepositoryIntegrationTest kiểm migration/seed chạy lại, rollback, constraint/index. ERD/data dictionary phản ánh unique theo phạm vi composite và schema thật.
- Domain Entity/User/Item + subtype, Auction/BidTransaction và ItemFactory đã được tích hợp vào auth/authorization, item insert/row mapping và bid policy; 7 DomainTest kiểm hierarchy/Factory. Class diagram/ADR-004 đã cập nhật.
- Contract dùng payloadJson; SESSION trả số dư và rebind observer khi reconnect; UPDATE ảnh thiếu field giữ ảnh, empty/null xóa. Mức bid tối thiểu luôn là currentPrice + minIncrement.
- Bản đồ mã nguồn liên kết các module, chức năng và bộ kiểm thử; phân công TV1–TV4 để review và nghiệm thu.
- Build đầy đủ JDK21, Checkstyle và gate coverage qua: common 24 test/87.86%, server 66 test/81.28%, client 8 test/68.57%. Tổng 98, không failure/error/skip với -Ui.

Chỉ thay đổi bản local. Người dùng tự tạo nhánh, commit và push theo git-handoff.md. Không đánh dấu CI remote/review chéo/rehearsal đã xong trước khi nhóm thực hiện.
