# Database local

SQLite là đường chạy mặc định của repository đích. Chạy migration theo thứ tự:

1. `migrations/V001__core_schema.sql`
2. `migrations/V002__advanced_schema.sql`
3. `migrations/V003__bid_history.sql`
4. `migrations/V004__week4_settlement_request.sql` (settlement request idempotency; migration identifier)
5. `seed/V001__demo_seed.sql`

Ví dụ với SQLite CLI:

```bash
sqlite3 data/auction.db < database/migrations/V001__core_schema.sql
sqlite3 data/auction.db < database/migrations/V002__advanced_schema.sql
sqlite3 data/auction.db < database/migrations/V003__bid_history.sql
sqlite3 data/auction.db < database/migrations/V004__week4_settlement_request.sql
sqlite3 data/auction.db < database/seed/V001__demo_seed.sql
```

Ứng dụng server phải bật foreign key cho từng connection:

```sql
PRAGMA foreign_keys = ON;
```

Các migration V001–V003 có thể chạy lại an toàn nhờ `CREATE TABLE IF NOT EXISTS`,
còn V004 dùng `ALTER TABLE` và phải được chạy qua `DatabaseInitializer` để
`schema_migrations` ngăn chạy lặp. Nếu dùng SQLite CLI thủ công, chỉ chạy V004
một lần trên database đã có V001–V003. Seed demo nên chạy một lần trên DB sạch.
Không commit file `*.db`; thư mục `data/` chỉ là runtime.

`V003__bid_history.sql` bổ sung lịch sử các bid đã được chấp nhận theo phiên bản
optimistic của auction. Server tự chạy migration theo thứ tự tên file, vì vậy
không cần chạy thủ công khi dùng `DatabaseInitializer`.

Migration `V004__week4_settlement_request.sql` bổ sung khóa request của settlement và index
để retry cùng một yêu cầu chỉ đọc lại kết quả đã ghi. Migration cũng gán khóa ổn
định cho các settlement cũ nếu database đã tồn tại từ tuần 3.

## Tài khoản demo local

| Account | Password | Role | Balance |
|---|---|---|---:|
| `admin` | `admin123` | ADMIN | 0 |
| `seller` | `seller123` | SELLER | 0 |
| `bidder1` | `bidder123` | BIDDER | 2,000,000 cents |
| `bidder2` | `bidder123` | BIDDER | 2,000,000 cents |

Password trong seed là PBKDF2-HMAC-SHA256 có salt riêng cho từng tài khoản. Đây là dữ liệu demo local, không phải credential production.
