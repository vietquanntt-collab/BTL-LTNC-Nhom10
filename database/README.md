# Database local

SQLite là đường chạy mặc định của repository đích. Chạy migration theo thứ tự:

1. `migrations/V001__core_schema.sql`
2. `migrations/V002__advanced_schema.sql`
3. `seed/V001__demo_seed.sql`

Ví dụ với SQLite CLI:

```bash
sqlite3 data/auction.db < database/migrations/V001__core_schema.sql
sqlite3 data/auction.db < database/migrations/V002__advanced_schema.sql
sqlite3 data/auction.db < database/seed/V001__demo_seed.sql
```

Ứng dụng server phải bật foreign key cho từng connection:

```sql
PRAGMA foreign_keys = ON;
```

Các migration có thể chạy lại an toàn nhờ `CREATE TABLE IF NOT EXISTS`, nhưng seed demo nên chạy một lần trên DB sạch. Không commit file `*.db`; thư mục `data/` chỉ là runtime.

## Tài khoản demo local

| Account | Password | Role | Balance |
|---|---|---|---:|
| `admin` | `admin123` | ADMIN | 0 |
| `seller` | `seller123` | SELLER | 0 |
| `bidder1` | `bidder123` | BIDDER | 2,000,000 cents |
| `bidder2` | `bidder123` | BIDDER | 2,000,000 cents |

Password trong seed là PBKDF2-HMAC-SHA256 có salt riêng cho từng tài khoản. Đây là dữ liệu demo local, không phải credential production.
