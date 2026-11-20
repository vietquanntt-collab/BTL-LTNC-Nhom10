# Bàn giao tuần 4 và hướng dẫn Git

## Trạng thái nhận bàn giao

BTL-week4-check là repository độc lập có lịch sử nhóm, lấy nền develop tại ba745b8. HEAD ở commit nền; các sửa đổi đều để chưa stage/chưa commit. Không copy nguyên thư mục này làm thư mục con trong repository khác.

Tạo nhánh mới trước khi commit:

    git status
    git switch -c feature/week4-check
    .\scripts\build.ps1
    git diff --check

## Chia commit theo thay đổi có ý nghĩa

Nhóm 1 – hoàn thiện các route và quyền server, client lọc ALL:

    git add server/src/main/java/com/btl/team10/server/network/ClientHandler.java server/src/main/java/com/btl/team10/server/repository/AuctionRepository.java server/src/main/java/com/btl/team10/server/repository/ItemRepository.java server/src/main/java/com/btl/team10/server/repository/JdbcItemRepository.java server/src/main/java/com/btl/team10/server/repository/JdbcUserRepository.java server/src/main/java/com/btl/team10/server/repository/UserRepository.java server/src/main/java/com/btl/team10/server/session/AuthService.java server/src/main/java/com/btl/team10/server/session/PasswordHasher.java client/src/main/java/com/btl/team10/client/service/NetworkClient.java
    git diff --cached --check
    git commit -m "fix: connect registration and seller admin management routes"

Nhóm 2 – hủy phiên/hoàn tiền, settlement/scheduler, seed và client trạng thái:

    git add common/src/main/java/com/btl/team10/common/protocol/EventType.java database/seed/V001__demo_seed.sql server/src/main/java/com/btl/team10/server/bidding server/src/main/java/com/btl/team10/server/repository/JdbcSettlementRepository.java client/src/main/java/com/btl/team10/client/controller
    git diff --cached --check
    git commit -m "fix: make auction cancellation and settlement consistent"

Nhóm 3 – kiểm thử hồi quy:

    git add server/src/test
    git diff --cached --check
    git commit -m "test: cover management routes and settlement recovery over sockets"

Nhóm 4 – chạy local và tài liệu:

    git add scripts README.md docs data/.gitkeep database/README.md database/data-dictionary.md server/src/main/java/com/btl/team10/server/ServerApplication.java .gitignore
    git diff --cached --check
    git commit -m "docs: prepare reproducible week four team handoff"

Code có phụ thuộc giữa nhóm 1 và 2; chỉ push bản cuối đã đủ nhóm và build xanh. Nếu muốn mỗi commit tự build được, gộp nhóm 1 và 2 thành một commit tích hợp. Không tạo commit rỗng chỉ để tăng số lượng.

Nếu muốn một commit cho toàn bộ, sau khi tạo nhánh và test:

    git add -A
    git diff --cached --stat
    git diff --cached --check
    git commit -m "fix: complete week four integration and handoff"

## Kiểm tra và push thủ công

    .\scripts\build.ps1
    git status
    git log --oneline -5
    git diff develop...HEAD --stat
    git push -u origin feature/week4-check

Không add target, DB runtime, cache hoặc JAR build. .mvn/wrapper/maven-wrapper.jar là ngoại lệ phải có để build.

Sau push, tạo Pull Request từ feature/week4-check vào develop, chờ CI và reviewer. Không merge hoặc push trực tiếp develop/main khi chưa nghiệm thu.

## Thành viên tải và test

    git clone --branch feature/week4-check --single-branch https://github.com/vietquanntt-collab/BTL-LTNC-Nhom10.git
    cd BTL-LTNC-Nhom10
    .\scripts\build.ps1
    .\scripts\run-server.ps1

Terminal khác cùng repository:

    .\scripts\run-client.ps1

Máy đã có repository nhưng có sửa đổi riêng: commit/stash phần của mình trước khi đổi nhánh. Có thể clone sang thư mục khác để test độc lập.

TV1: common/schema/contract; TV2: server/transaction; TV3: JavaFX và UX; TV4: test/event/CI. Cả nhóm dùng docs/local-testing.md để nghiệm thu.

