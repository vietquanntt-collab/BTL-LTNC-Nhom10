# Tuần 5 · test và rehearsal

Cập nhật 2026-11-24 sau khi đọc hai PDF mới. Báo cáo tổng: [week-5-check-report](week-5-check-report.md); số liệu từng suite: [verification-results](verification-results.md); checklist nhóm: [local-testing](local-testing.md).

Lượt clean verify có -Ui kết thúc 09:39:36 +07: common 24, server 66, client 8; tổng 98 test, 0 failure, 0 error, 0 skipped. Line coverage lần lượt 87.86%, 81.28%, 68.57%; gate 60% cho từng module qua. Checkstyle validate có 0 violation theo bộ baseline.

Bổ sung contract validation, 7 test domain OOP/Factory, test giá đầu tiên phải đủ bước, DB ảnh upgrade, SESSION rebind, client socket và UI regression. Số liệu được đọc từ XML Surefire/JaCoCo sau build, không dùng lại số test cũ của baseline.

CI Windows/Linux chạy clean verify có JavaFX, Xvfb trên Linux; upload Surefire, coverage/Checkstyle/UI và JAR theo OS. Cấu hình mới chưa được push, nên CI trên commit mới còn chờ nhóm. Test local trên Windows không xác nhận kết quả Linux.

TV1 kiểm schema/seed/ERD/số dư/domain, TV2 kiểm auth/concurrency/reconnect/shutdown, TV3 nghiệm thu màn hình thật, TV4 kiểm CI/artifact và điều phối rehearsal. Các ô checklist phải do nhóm tick sau khi thực hiện.
