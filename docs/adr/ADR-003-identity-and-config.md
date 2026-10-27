# ADR-003: Password, demo account và cấu hình local

- **Status:** Accepted
- **Date:** 2026-10-22
- **Owner:** TV1
- **Reviewers:** TV2, TV4

## Context

Kết nối cơ sở dữ liệu và tài khoản demo không được làm cho máy sạch phụ thuộc vào dịch vụ bên ngoài. Credential hard-code cũng khiến việc thay đổi môi trường và thu hồi secret khó kiểm soát.

## Decision

- Bản đích không commit credential DB hoặc secret thật.
- Password người dùng được lưu dưới dạng PBKDF2-HMAC-SHA256 có salt và số vòng; server không trả password trong DTO.
- Seed local chỉ dùng tài khoản demo vô hại được ghi trong README, và hash nằm trong file seed.
- Cấu hình runtime đọc từ biến môi trường hoặc `config/local.properties` bị gitignore; có `config/application.example.properties` chỉ chứa placeholder.
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT` không được hard-code trong Java.

## Consequences

- TV2 phải viết password verifier và config loader trước khi làm login thật.
- Người chấm có thể chạy SQLite local mà không cần Railway.
- Nếu credential thật từng xuất hiện trong lịch sử Git, phải rotate credential trước khi chia sẻ repository.
