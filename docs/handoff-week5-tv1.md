# Bàn giao TV1 tuần 5 và hướng dẫn commit

## Nền làm việc

- Nền chức năng: develop commit 033612a.
- Thư mục: BTL-week5-tv1-check.
- Branch local: feature/week5-tv1-release.
- Chưa push thay đổi tuần 5; TV1 tự tạo các commit.

## Nhóm commit có ý nghĩa

### 1. Chốt ERD, source map và data dictionary

    git add docs/source-map.md docs/architecture/erd-v1.mmd database/data-dictionary.md database/README.md
    git diff --cached --check
    git commit -m "docs(tv1): finalize week five schema documentation"

### 2. Thêm kiểm thử constraint/index/migration

    git add server/src/test/java/com/btl/team10/server/persistence/RepositoryIntegrationTest.java
    git diff --cached --check
    .\mvnw.cmd -B -pl common,server -am test "-Dtest=RepositoryIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
    git commit -m "test(tv1): verify final schema constraints and indexes"

### 3. Ghi báo cáo nghiệm thu TV1

    git add docs/week-5-tv1-check-report.md docs/handoff-week5-tv1.md
    git diff --cached --check
    git commit -m "docs(tv1): record week five database acceptance"

### 4. Kiểm tra release và push branch

    .\scripts\build.ps1
    git diff --check
    git status
    git log --oneline -5
    git push -u origin feature/week5-tv1-release

Mỗi commit phải có thay đổi thật và test liên quan. Không tạo commit rỗng chỉ để tăng số lượng.

## Sau khi push

Mở Pull Request từ feature/week5-tv1-release vào develop. Tiêu đề gợi ý:

    docs/test(tv1): finalize week five schema and release evidence

PR description nên ghi migration/seed replay, constraint/index/foreign-key tests, ERD/data dictionary/source map, mvnw -B verify pass và không commit target/database/cache.

## Kiểm tra trước PR

    git status --short
    git diff develop...HEAD --stat
    git diff develop...HEAD --check
    git log --oneline --decorate -6

Nếu upstream local còn trỏ origin/feature/week4-check, lệnh git push -u origin feature/week5-tv1-release sẽ đặt upstream đúng branch tuần 5.

