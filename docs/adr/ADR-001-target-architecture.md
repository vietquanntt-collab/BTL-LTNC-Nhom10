# ADR-001: Kiến trúc đích Client - Common - Server

- **Status:** Accepted
- **Date:** 2026-10-22
- **Owner:** TV1
- **Reviewers:** TV2, TV3, TV4

## Context

Dự án cần hỗ trợ client JavaFX, server socket và cơ sở dữ liệu trong cùng một sản phẩm. Nếu trộn các lớp này vào một module, client dễ phụ thuộc DB, các DTO bị lặp và protocol khó thay đổi an toàn.

## Decision

Repository đích được tổ chức thành các vùng trách nhiệm:

```text
team10-auction/
├── common/       # enum, DTO, request/response/event contract; không JDBC/JavaFX
├── server/       # socket, auth, service, domain, repository, scheduler, settlement
├── client/       # JavaFX/FXML/CSS, controller, network adapter, UI event state
├── database/     # migration, seed, data dictionary
└── docs/         # ADR, diagrams, protocol, runbook, test plan
```

- Chỉ `server` được truy cập database.
- `client` chỉ dùng DTO trong `common` qua network adapter.
- `common` không chứa logic persistence, credential hoặc UI.
- Network dùng TCP socket và JSON. Serializer được chọn ở module network; common chỉ chứa Java types.
- Mỗi request có `requestId`; mỗi event có `eventId`, `auctionId` và `sequence` để xử lý retry/order.
- Package của dự án bắt đầu bằng `com.btl.team10`.
- `main` chỉ nhận release đã nghiệm thu; `develop` phải build được; feature branch có owner.

## Alternatives considered

1. **Gộp tất cả vào một module:** loại vì giữ coupling giữa UI, network và persistence.
2. **Maven multi-module đầy đủ ngay tuần 1:** chưa chọn server/client module rỗng; tuần 1 chỉ chốt contract/common/database để không tạo skeleton giả làm hỏng tích hợp.
3. **REST thay socket:** loại vì yêu cầu bài cho phép socket và TCP phù hợp với mô hình realtime của đấu giá.

## Consequences

- TV2 và TV3 có contract độc lập để phát triển song song.
- Thay đổi schema/protocol cần review TV1 và có migration/version.
- Các module phụ thuộc contract dùng chung, tránh hai bộ DTO/DAO cùng tồn tại.
- SQLite local giúp máy sạch chạy được; triển khai cloud có thể thêm repository/config sau.
