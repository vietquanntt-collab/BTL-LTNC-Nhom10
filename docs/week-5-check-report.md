# Bàn giao kiểm tra toàn nhóm · Tuần 5

## Kết luận

**Sẵn sàng để nhóm chạy thử và review trên nhánh mới.** Bản local đã được đối chiếu với hai PDF cập nhật và qua build/test/coverage/Checkstyle. Điều kiện merge main vẫn cần CI GitHub trên commit của nhóm, review chéo và nghiệm thu bằng tay.

- Audit cập nhật: 2026-11-25, Asia/Bangkok.
- Repository: https://github.com/vietquanntt-collab/BTL-LTNC-Nhom10.git
- Nền đối chiếu mới nhất: develop **f957216** (đã merge PR #38 và PR #42 Plushie Gala); main ở b2708346c6cb7febb791f3ea37816fb8e52dff36.
- Bản hoàn thiện trước khi merge main nằm trên nhánh `fix/pre-main-polish` (chưa commit/push, chờ nhóm review & mở PR vào `develop` → `main`).
- Căn cứ chính: 2026-BTL.pdf (10 trang) và Hướng dẫn BTL LTNC-2026.pdf (7 trang) đã được cập nhật tại thư mục cha. [Ma trận đối chiếu và hash PDF](requirements-review.md). Kế hoạch 5 tuần nội bộ là phạm vi milestone; lịch môn học trong hướng dẫn là tuần 6–15.

## 1. Kết quả kiểm thử cuối

Windows x64, Temurin JDK 21.0.13, Maven Wrapper 3.8.5. Chạy từ gốc thư mục bàn giao:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./scripts/build.ps1 -Clean -Ui
~~~

**BUILD SUCCESS** lúc 22:52:28 +07 ngày 2026-11-25; khoảng 40 giây với dependency cache. Số liệu lấy từ XML Surefire và JaCoCo của lượt clean verify cuối, sau khi tích hợp giao diện Plushie Gala (PR #42) và hoàn thiện polish ảnh/bố cục UI.

| Module | Test | Failure | Error | Skipped | Line coverage |
|---|---:|---:|---:|---:|---:|
| common | 24 | 0 | 0 | 0 | 87.86% (275/313) |
| server | 66 | 0 | 0 | 0 | 81.28% (1811/2228) |
| client | 9 | 0 | 0 | 0 | 69.30% (1695/2446) |
| **Tổng** | **99** | **0** | **0** | **0** | **từng module ≥60%** |

Checkstyle ở validate: **0 violation** trên root/common/server/client; kiểm source và test theo bộ baseline ở config/checkstyle/checkstyle.xml. Bộ này enforce naming, thứ tự modifier và vệ sinh file, chưa phải toàn bộ Google Java Style. JaCoCo 0.8.12 đặt gate line coverage 60% cho từng module; không loại class sản phẩm khỏi phép đo. [Chi tiết từng suite](verification-results.md).

Client có 4 test socket và 4 test JavaFX. Luồng FXML/controller/server TCP thật kiểm validation, login, tìm kiếm, bid, bật/tắt Auto-Bid, Seller/Admin, hủy phiên và các state. Ba regression bổ sung kiểm tách nền ảnh, mở phiên CANCELLED/FINISHED/PAID, form đăng ký kèm lỗi ở 1100x720; luồng chính kiểm ảnh MC và contrast chữ MC/hàng giá dẫn đầu >=4.5. Các test này chưa bao phủ mọi tình huống GUI.

Ảnh scene: client/target/ui-snapshots; số đo: client/target/ui-layout.tsv. Nội dung ScrollPane không vượt viewport ở 1100×720 và 1280×800 trong kịch bản. Công cụ xem ảnh phiên audit không truyền được hình cho đánh giá bằng mắt, nên TV3 vẫn cần nghiệm thu thẩm mỹ/DPI trên máy thật.

## 2. Các lỗi và khoảng trống đã xử lý

| Vấn đề | Thay đổi và bằng chứng |
|---|---|
| Preset vật phẩm mẫu (PR #42 Plushie Gala) | Đổi sang Báo Hồng / Doraemon Hồng / Doraemon Xanh (`FIGURE`), chuẩn hóa tên file `mc_present.jpg`/`mc_hype.jpg`, bỏ fallback tới ảnh đã xóa và thêm regression test ảnh trong `ClientUiTest`. |
| Lỗi hiển thị UI nhỏ sau PR #42 | Clip vùng hạt rơi ở màn hình đăng nhập (hết dải trắng trên cùng), cố định `minWidth` cho nhãn `LIVE GALA` (hết cắt `...`), và xếp dọc biểu đồ + bảng lịch sử full-width ở chi tiết phiên để không cắt chữ cột. |
| DB tuần 4 thiếu image_base64 | Migration V005 kiểm cột trước ALTER; ItemImageUpgradeTest giữ item cũ và chạy upgrade lại an toàn. |
| Seller update ảnh không lưu, bỏ ảnh không tác dụng | Đồng bộ client/server/repository: thiếu field giữ ảnh, có ảnh thay ảnh, empty/null xóa; regression socket/client. |
| Server thiếu kiểm Base64/kích thước ảnh | Giới hạn decoded 512 KiB; từ chối sai Base64/vượt giới hạn; regression socket. |
| Mở phiên đã đóng/hủy lỗi countdown null | Kiểm timer trước play; UI mở CANCELLED và kiểm nút bị khóa. |
| Reader socket cũ đóng kết nối mới sau logout/login | Socket ownership và connection epoch; test đăng nhập liên tiếp; EOF fail pending ngay. |
| Reconnect không refresh ví/rebind observer | SESSION refresh balance/frozen và rebind; server management và NetworkClientTest. |
| Timer/listener/phản hồi tải cũ tồn tại sau điều hướng | Cleanup/disposed/generation guard; UI chuyển vai trò và màn hình. |
| Tìm theo tên phụ thuộc metadata tải muộn | Tải metadata trước filter, bỏ kết quả loader cũ; UI regression tìm kiếm. |
| Client JAR thiếu launcher/dependency | ClientLauncher + shade; manifest đúng; không chứa class server/SQLite. |
| Chưa có hierarchy/Factory theo rubric | Domain Entity/User/Item + subtype, Auction/BidTransaction/ItemFactory được tích hợp vào auth/routes/repository/mappers/bid policy; 7 DomainTest. |
| Test/UI/server lệch giá đầu tiên | Tối thiểu luôn currentPrice + minIncrement. Test bằng giá hiện tại hoặc thiếu 1 cent bị từ chối, đúng bước được nhận; sửa số dư/e2e/Auto-Bid theo cùng contract. |
| Chưa đo coverage và enforce coding convention | JaCoCo 60% từng module; Checkstyle validate; workflow xuất báo cáo cùng artifact. |
| Tài liệu/sơ đồ/config lệch code hoặc coi PDF rỗng | Cập nhật ma trận đề, class/ERD/sequence, ADR, dictionary, README và hướng dẫn Git theo PDF mới. |

## 3. Giao diện

- app.css thống nhất nền sáng, xanh đậm, điểm nhấn vàng, typography, khoảng cách, form, nút và bảng.
- Login giữ ảnh nền Gala của PR #36, tab/validation rõ ràng; form đăng ký theo chiều cao nội dung để không cắt thông báo lỗi. Luồng demo dùng hai Bidder/Seller/Admin theo README.
- Sidebar thể hiện vai trò, tài khoản, khả dụng/đang giữ, kết nối và mục điều hướng hiện tại.
- Danh sách có KPI/bộ lọc, tìm theo tên/mã, card/table; Seller/Admin có panel/form cuộn dọc.
- Chi tiết giữ sân khấu của nhóm, bố trí giá/countdown/Auto-Bid/chart/history; có nút tắt Auto-Bid, wrap nội dung dài và phân tách hàng nghìn.

Tiền vẫn là integer cents. Nhóm cần kiểm độ rõ chữ, tương phản/DPI, bàn phím và cuộn trên máy thật theo [local-testing](local-testing.md).

## 4. Phạm vi 5 tuần và đề chính thức

| Phạm vi | Bằng chứng và giới hạn |
|---|---|
| Tuần 1: module/contract/DB/OOP | Maven multimodule, DTO/enum/JsonCodec, schema/ADR; hierarchy/Factory đã có code dùng thật và test. |
| Tuần 2: auth/role, item CRUD, phiên và UI | Repository/management/socket/client/UI tests qua; đăng ký mới là Bidder có ví 0, dùng seed để test tiền. |
| Tuần 3: bid/history/chart/realtime/concurrency | Bid/audit/event/concurrency/socket/e2e tests qua; một server process sở hữu SQLite. |
| Tuần 4: Auto-Bid/Anti-Snipe/wallet/settlement | PriorityQueue, cap/tie-break, extension, idempotency và transfer có test; UI bật/tắt Auto-Bid, hủy/đóng phiên. |
| Tuần 5 TV1: schema/seed/index/ERD/dictionary/source map | 5 migration; replay/rollback/DB upgrade qua; sơ đồ/domain khớp code; bản đồ mã nguồn và phân công kiểm tra đã cập nhật. |
| Tuần 5 TV2: stress/auth/reconnect/shutdown/error/health/artifact | ServerHardeningTest, management/socket, e2e; không khẳng định stress production hoặc nhiều server process. |
| Tuần 5 TV3: UI/client artifact | Flow/render/viewport qua; nghiệm thu bằng mắt/DPI/máy khác đang chờ nhóm. |
| Tuần 5 TV4: CI/runbook/package/rehearsal | Workflow Windows/Linux clean verify + UI/Xvfb, Checkstyle/JaCoCo/artifact; CI remote và rehearsal cần nhóm thực hiện. |

JUnit 5 dùng fixture DB/socket thật. Hai PDF không bắt buộc Mockito. Các class sản phẩm trong đề là gợi ý; nhóm dùng Card/Figure/Poster cho catalogue sưu tầm. Chi tiết từng yêu cầu nằm trong [requirements-review](requirements-review.md).

## 5. Đóng gói

Windows build cuối tạo server/target/server.jar và client/target/client.jar. Client manifest trỏ ClientLauncher; client JAR không có class server hoặc org.sqlite.

| Artifact | Byte | SHA-256 |
|---|---:|---|
| server.jar | 13998394 | 86c3c7ccf719dcd25a5c1f8f9298151ff42560450ff2c9b73e667ea4457c6081 |
| client.jar | 10906089 | 5275e40387d4701b97e2b943f2dc897e3666425d212f768f5cdb6a8e8b29a55b |

Hash chỉ áp dụng lần build cuối trên; build lại có thể tạo hash khác. Xuất gói mới bằng scripts/package-release.ps1 theo README. Không gửi DB test hoặc commit target/cache/release-bundle vào source Git. JAR client có native riêng cho OS build.

Smoke test JAR cuối đã qua: server chạy các lệnh HEALTH/auth/ví/phiên/bid bằng DB/cổng riêng; client qua ClientLauncher mở form đăng nhập rồi tự đóng. Tiến trình test đã dừng; xem [verification-results](verification-results.md). Gói cuối: release-bundle/local-6a4fc8b0, có START-HERE.md và SHA256SUMS.txt; tài liệu đã đồng bộ, hai JAR giữ nguyên hash. Bạn tự tạo ZIP nếu cần; không có ZIP source được tạo sẵn. Hướng dẫn Git chép source trực tiếp sang checkout mới, giữ .mvn và không chép .git/target/DB runtime.

## 6. Việc nhóm cần xác nhận trước main

1. Review chức năng và phân công kiểm tra theo source-map.md; xác nhận code, tài liệu và kết quả demo khớp nhau.
2. GitHub Actions trên commit mới do nhóm push: cả Windows và Linux xanh. Audit local chưa chạy Linux/macOS.
3. Rehearsal nhiều client/LAN, giao diện/DPI/bàn phím, tên dài/state lỗi; tick checklist sau khi thực hiện.
4. Review chéo, báo cáo/slide, đóng góp thực tế và demo; từng thành viên phải giải thích được toàn bộ code theo PDF.

Giới hạn: một server process/SQLite local, session RAM mất khi restart, chưa có nạp tiền thật, UI tạo phiên 1–720 giờ. Runtime đọc -Dauction.* hoặc tham số script; example.properties chỉ tham khảo. OPEN chuyển RUNNING khi có bid đầu hợp lệ; server luôn kiểm thời gian.

## 7. Git bàn giao

Checkout local vẫn ở develop 8af20c4; mọi thay đổi bàn giao chưa stage/commit/push. Vì develop GitHub đã ở abe5265, [git-handoff](git-handoff.md) hướng dẫn bạn clone develop vào BTL-week5-upload, chép trực tiếp các file source đã kiểm tra rồi tự tạo feature/week5-check và chia bốn commit theo chức năng. Không copy .git hoặc folder lồng nhau.

Trình tự nghiệm thu: nhánh review -> PR develop -> CI/review/rehearsal -> PR develop vào main khi nhóm chấp nhận các mục còn lại.
