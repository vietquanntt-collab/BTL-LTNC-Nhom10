# Tự commit và push bản bàn giao tuần 5

Bản source đã kiểm tra nằm trong BTL-week5-check. Bạn tự thực hiện toàn bộ lệnh tạo nhánh, stage, commit, push và mở PR. Audit chỉ sửa file, build/test và xuất gói.

Checkout bàn giao vẫn ở develop 8af20c4 và có thay đổi chưa commit. GitHub develop đã tiến tới abe5265 (PR #36); các thay đổi UI của PR này đã được tích hợp vào source bàn giao. Vì các file trùng nhau đã được chỉnh tiếp và hai ảnh MC phải được giữ, quy trình dưới tạo một checkout mới từ develop mới nhất rồi chép source đã kiểm tra vào đó. Cách này giữ lịch sử của nhóm và tránh xung đột khi bắt đầu từ checkout cũ.

## 1. Chuẩn bị checkout để upload

Mở PowerShell tại BTL-week5-check. Bạn tự nén ZIP khi cần; các bước upload dưới đây chép trực tiếp source sang checkout mới nên không phụ thuộc ZIP.

~~~powershell
$handoffRoot = (Resolve-Path -LiteralPath '.').Path
$uploadDirectory = Join-Path (Split-Path $handoffRoot -Parent) 'BTL-week5-upload'
if (Test-Path -LiteralPath $uploadDirectory) { throw 'BTL-week5-upload đã tồn tại. Chọn tên thư mục mới trước khi tiếp tục.' }
git clone --branch develop https://github.com/vietquanntt-collab/BTL-LTNC-Nhom10.git "$uploadDirectory"
if ($LASTEXITCODE -ne 0) { throw 'Clone chưa thành công; dừng tại đây.' }
git -C "$uploadDirectory" rev-parse HEAD
~~~

Mốc đã đối chiếu là **abe5265a0e9d309b7c4b0699ff3bc8317473029b**. Nếu HEAD clone khác mốc này, develop đã thay đổi thêm: cần đối chiếu thay đổi mới trước khi chép source; không mặc định ghi đè các đóng góp mới chưa review.

Khi đúng mốc trên, tiếp tục trong cùng terminal:

~~~powershell
$sourceFiles = @(git -C "$handoffRoot" -c core.quotePath=false ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'Không đọc được danh sách source; dừng tại đây.' }
foreach ($relative in ($sourceFiles | Sort-Object -Unique)) {
    $sourceFile = Join-Path $handoffRoot $relative
    if (Test-Path -LiteralPath $sourceFile -PathType Leaf) {
        $destinationFile = Join-Path $uploadDirectory $relative
        New-Item -ItemType Directory -Path (Split-Path $destinationFile -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $sourceFile -Destination $destinationFile -Force
    }
}
Set-Location -LiteralPath "$uploadDirectory"
git switch -c feature/week5-check
if ($LASTEXITCODE -ne 0) { throw 'Chưa tạo được nhánh; kiểm tra tên nhánh trước khi tiếp tục.' }
git status --short --branch
git remote -v
~~~

Nếu tên nhánh đã có trên GitHub, dùng tên mới như feature/week5-check-review và thay tên ở lệnh push/clone phía dưới. Không copy cả BTL-week5-check thành thư mục lồng trong repository và không copy .git từ bản bàn giao.

## 2. Bốn commit theo nội dung thay đổi

Các phần có liên quan; hoàn tất cả bốn nhóm rồi chạy build đầy đủ trước khi push. Đọc git diff --cached trước mỗi commit. Các nhóm sau tính từ develop abe5265; những chỉnh sửa đã có trong PR #36 không bị tạo lại thành commit riêng.

**Commit 1: domain OOP/Factory, dữ liệu ảnh, quy tắc đặt giá và session server**

~~~powershell
git add database/migrations/V005__item_images.sql server/src/main/java server/src/test/java
git diff --cached --stat
git diff --cached
git commit -m "feat(server): integrate domain policies and harden auction persistence"
~~~

Gồm hierarchy Entity/User/Item, ItemFactory, Auction/BidTransaction, regression giá đầu tiên, nâng cấp DB ảnh và ví sau reconnect.

**Commit 2: giao diện và vòng đời client realtime**

~~~powershell
git add client/src/main
git diff --cached --stat
git diff --cached
git commit -m "feat(client): polish auction workspace and harden realtime lifecycle"
~~~

Gồm layout/form, Auto-Bid off, trạng thái phiên đóng, reconnect, stylesheet và ảnh cần thiết. Hai ảnh mc_present.jpg/mc_hype.jpg được thêm lại vì code sân khấu còn dùng chúng; test UI kiểm ảnh MC được load từ tài nguyên đóng gói.

**Commit 3: kiểm thử, coverage, Checkstyle, đóng gói và CI**

~~~powershell
git add common/src/test client/src/test pom.xml client/pom.xml server/pom.xml config .github/workflows/ci.yml .gitignore scripts
git diff --cached --stat
git diff --cached
git commit -m "test(build): enforce coverage and verify JavaFX release flows"
~~~

config/local.properties tiếp tục bị ignore. Không add DB runtime, target, cache hoặc release-bundle.

**Commit 4: tài liệu, sơ đồ và bằng chứng bàn giao**

~~~powershell
git add README.md docs database/README.md database/data-dictionary.md
git diff --cached --stat
git diff --cached
git commit -m "docs: align week five handoff with updated requirements and verification"
~~~

Mỗi commit chứa thay đổi có ý nghĩa. Có thể gộp các phần liên quan; không cần commit rỗng hoặc viết lại lịch sử để tăng số lượng.

## 3. Kiểm tra và tự push

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Clean -Ui
git diff --check
git status --short
git log -4 --oneline
~~~

Kỳ vọng ở source bàn giao: BUILD SUCCESS; common 24, server 66, client 8, tổng 98 test; 0 failure/error/skipped. Coverage có thể dao động nhẹ theo luồng bất đồng bộ nhưng từng module phải qua gate 60%. Checkstyle phải 0 violation. git status --short phải trống sau khi commit đủ.

Sau khi các kiểm tra trên đạt, bạn tự chạy:

~~~powershell
git push -u origin feature/week5-check
~~~

## 4. Các thành viên lấy code và chạy

~~~powershell
git clone --branch feature/week5-check https://github.com/vietquanntt-collab/BTL-LTNC-Nhom10.git BTL-week5-test
cd BTL-week5-test
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Clean -Ui
~~~

Chạy java -jar server/target/server.jar và java -jar client/target/client.jar trong hai terminal tại gốc source sau khi build. Muốn test LAN, dùng cùng một server/IP/cổng theo README. Người chỉ cần chạy có thể nhận gói release-bundle/local-* và đọc START-HERE.md, không cần Maven/Git.

## 5. Nghiệm thu PR

1. Mở PR feature/week5-check vào develop. Đợi GitHub Actions Windows/Linux xanh và review chéo các thay đổi.
2. Cả nhóm chạy checklist local-testing.md; TV3 nghiệm thu giao diện/DPI; TV1 kiểm DB/số dư; TV2 kiểm auth/socket; TV4 kiểm CI/artifact và rehearsal.
3. Bổ sung nguồn tham khảo/ảnh, báo cáo/slide và đóng góp thực tế theo source-map.md và requirements-review.md.
4. Khi nghiệm thu xong, nhóm tự merge PR vào develop, đợi CI của develop rồi mở PR develop vào main.

Nếu develop có thay đổi mới sau mốc đã review, tích hợp và test lại trên nhánh feature trước PR. Không reset/force-push develop hoặc main.
