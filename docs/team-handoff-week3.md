# Hướng dẫn bàn giao bản tích hợp tuần 3

## Phạm vi

Thư mục này là bản mã nguồn độc lập để các thành viên clone hoặc tải từ GitHub về
máy riêng. Bản không chứa `.git`, đường dẫn tuyệt đối của máy người tạo, file
`target/`, cache JavaFX hoặc database runtime. Database sẽ được tạo tự động khi
chạy server.

## Yêu cầu trên máy thành viên

- Windows 10/11.
- JDK 21, có cả `java` và `javac` trong PATH.
- Git.
- VS Code với Extension Pack for Java (khuyến nghị).
- Không cần cài Maven riêng; Maven Wrapper đã nằm trong repository.
- Không cần tải JavaFX thủ công; Maven sẽ tải dependency JavaFX đúng platform.

Kiểm tra:

```powershell
java -version
javac -version
.\mvnw.cmd -version
```

Nếu `java -version` không phải Java 21, đặt `JAVA_HOME` tới thư mục JDK 21 của
chính máy đó. Không sao chép nguyên đường dẫn JDK từ máy khác.

## Lấy mã nguồn

```powershell
git clone https://github.com/vietquanntt-collab/BTL-LTNC-Nhom10.git
Set-Location -LiteralPath '.\BTL-LTNC-Nhom10'
git fetch origin
git switch develop
git pull --ff-only origin develop
```

Nếu đang kiểm tra bản handoff trước khi merge lên GitHub, mở trực tiếp thư mục
đã nhận rồi dùng đúng các lệnh build bên dưới.

## Build và test

Nếu PowerShell chặn script `.ps1`, chỉ mở khóa trong terminal hiện tại:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

Chạy kiểm tra đầy đủ:

```powershell
.\mvnw.cmd -B verify
```

Hoặc dùng script:

```powershell
.\scripts\build.ps1
```

Kết quả cần có là `BUILD SUCCESS`. `target/` sẽ được Maven tạo ra cục bộ và
được `.gitignore` loại khỏi commit.

## Chạy server và JavaFX client

Terminal 1:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\run-server.ps1
```

Terminal 2, tại cùng thư mục project:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\run-client.ps1
```

Server mặc định dùng `127.0.0.1:9999` và tạo `data/auction.db`. Có thể đổi cổng
bằng `-Port`; client dùng cùng cổng. Đóng server trước khi xóa database runtime.

## Quy trình Git của nhóm

Không làm việc trực tiếp trên `main` hoặc `develop`:

```powershell
git switch -c feature/<ten-nhiem-vu>
git add -A
git diff --cached --check
git commit -m "feat: <mo-ta-ngan>"
git push -u origin HEAD
```

Mở Pull Request từ `feature/*` vào `develop`. Chỉ merge khi CI xanh và đã review.
Không commit `target/`, `.javafx-cache/`, `data/auction.db`, `.class` hoặc mật khẩu.
