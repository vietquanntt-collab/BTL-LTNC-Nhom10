# TV1 week 5 check report

## Scope

This report closes the TV1 week 5 deliverables on develop commit 033612a: migration/seed replay, constraints and indexes, final ERD/data dictionary and source map.

## Final database contract

- SQLite is the local source of truth; every connection enables foreign keys.
- Money is integer cents and wallets enforce 0 <= frozen_cents <= balance_cents.
- Auction status is OPEN -> RUNNING -> FINISHED -> PAID; authorized cancellation goes to CANCELLED.
- auctions.item_id is unique, so an item has at most one listing.
- (auction_id, bidder_id, client_request_id) makes bid retries idempotent.
- bid_history is append-only per auction version.
- (auction_id, sequence) orders events; settlements.auction_id and settlements.request_id prevent repeated settlement effects.
- schema_migrations.version controls migration replay; seed uses INSERT OR IGNORE and derived demo winner/hold values.

## TV1 completion matrix

| Deliverable | Evidence |
|---|---|
| Migration from empty DB | DatabaseInitializer, RepositoryIntegrationTest |
| Migration replay | RepositoryIntegrationTest.migrationsSeedAndCoreRepositoriesWorkTogether |
| Rollback on bad migration | RepositoryIntegrationTest.failedMigrationRollsBackEarlierSchemaChanges |
| Constraints/indexes | database/migrations/*.sql, RepositoryIntegrationTest.finalSchemaExposesExpectedConstraintsAndIndexes |
| Final ERD | docs/architecture/erd-v1.mmd |
| Final data dictionary | database/data-dictionary.md |
| Reuse attribution | docs/source-map.md |
| Clean-build evidence | mvnw -B verify, .github/workflows/ci.yml |

## Remaining team checks

The new TV1 schema test passed with 3 tests and no failures in a writable temporary copy. OneDrive locks target output in the handoff folder, so full local verification should be run from a normal writable clone or after closing OneDrive-held build files. TV2/TV3/TV4 still run their week 5 hardening, UI rehearsal and release artifact checks. This TV1 change does not claim that the full final submission rubric is complete.

