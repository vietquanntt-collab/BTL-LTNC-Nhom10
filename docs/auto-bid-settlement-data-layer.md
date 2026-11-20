# Auto-Bid và settlement: lớp dữ liệu

## Phạm vi đã hoàn thành

TV1 khóa contract dùng chung và lớp dữ liệu cho Auto-Bid và settlement. Server
phía nghiệp vụ có thể dùng các interface này mà không truy cập SQL trực tiếp:

- `ConfigureAutoBidCommand` và `AutoBidRuleDto` biểu diễn một rule theo cặp
  `(auctionId, bidderId)`, với `maxAmountCents > 0`, `0 < incrementCents <=
  maxAmountCents`.
- `WalletDto` biểu diễn số dư, số tiền đang khóa và optimistic `version`.
- `SettleAuctionCommand`, `SettlementDto` và `SettlementWriteResult` biểu diễn
  settlement có request id ổn định để retry an toàn.
- `AutoBidRuleRepository`, `WalletRepository` và `SettlementRepository` là
  boundary JDBC cho server policy/scheduler.

## Schema và invariant

`V002__advanced_schema.sql` đã có bảng `auto_bid_rules`, `settlements`,
`auction_events` và `request_receipts`. Migration `V004__week4_settlement_request.sql`
bổ sung `settlements.request_id` và unique index theo request. Các row settlement
cũ được gán khóa `legacy-settlement-<id>` khi database được nâng cấp; validation
request rỗng nằm trong DTO/repository để tương thích với SQL runner tối giản.

Các invariant mà repository kiểm tra:

1. Một bidder chỉ có một rule cho một auction; upsert rule cũ rồi bật lại rule.
2. Cập nhật wallet phải dùng đúng `version`; frozen amount luôn nằm trong
   khoảng `0..balance`.
3. Chỉ auction `FINISHED` mới được settle; seller và winner phải khớp dữ liệu
   auction, còn số tiền phải bằng `current_price_cents`.
4. Settlement đánh dấu đúng một bid `WINNER`, chuyển auction sang `PAID`, và
   retry cùng auction trả lại record cũ với `created=false`.

Việc trừ frozen balance, cộng tiền seller và chính sách Auto-Bid/Anti-Snipe
được giữ ở service của TV2; repository này cung cấp transaction boundary
và optimistic primitives để service thực hiện các bước đó nguyên tử.

## Kiểm tra

```powershell
.\mvnw.cmd -B -pl common,server -am test
```

Các test mới bao phủ validation contract, upsert/deactivate Auto-Bid, optimistic
wallet update, settlement idempotency, winner marking và reject settlement có
giá cuối không khớp. Baseline sau tích hợp: 18 test ở `common`, 32 test ở
`server`, không failure.

## Handoff cho TV2/TV3/TV4

- TV2 dùng `JdbcAutoBidRuleRepository` để đọc rule active, dùng
  `JdbcWalletRepository.adjustFrozen` với version hiện tại và gọi settlement
  trong transaction của service.
- TV3 dùng các field cents/Instant từ DTO, không tự quyết định winner hoặc
  settlement.
- TV4 có thể dùng `AutoBidSettlementRepositoryIntegrationTest` làm fixture cho test policy,
  scheduler và event order.
