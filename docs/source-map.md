# Source map and attribution - week 5 TV1

This file records the origin and adaptation of reusable components. The project package is com.btl.team10; source code used by the application is the code in this repository.

| Area | Repository files | Origin | Adaptation in this project | Evidence |
|---|---|---|---|---|
| Build and modules | pom.xml, mvnw, common/, server/, client/ | New project structure based on the team plan | Multi-module Java 21 build and module boundaries | mvnw -B verify |
| Shared contract | common/src/main/java/com/btl/team10/common/ | New contract informed by the team protocol | Immutable records, validation, JSON envelopes, versioned enums and error codes | common contract tests |
| Core schema | database/migrations/V001__core_schema.sql | Adapted local auction schema | SQLite constraints, foreign keys, integer cents, version and state fields | RepositoryIntegrationTest |
| Advanced schema | V002 and V004 migrations | Adapted week 4 data design | Auto-Bid, settlement, event sequence, receipt and settlement request idempotency | repository and socket tests |
| Bid history | V003__bid_history.sql | New week 3 audit layer | Append-only history keyed by auction version and request id | BidAuditLogTest |
| Server persistence | server/src/main/java/.../repository/ | JDBC repository boundary | Prepared statements, transactions, optimistic wallet updates and row mappers | repository integration tests |
| Server policy | server/src/main/java/.../bidding/ | New server-side policy layer | Auto-Bid, Anti-Snipe, scheduler, settlement and event ordering outside client | policy and e2e tests |
| Client | client/src/main/java and resources | Adapted JavaFX auction UI | Network-only client, role dashboards, loading/error/reconnect states | client compile and runbook |
| Test/release | server/src/test, .github/workflows, scripts | New regression and release checks | Socket management tests, clean verify, JDK 21 detection and local scripts | CI and build.ps1 |

No production credential, runtime database, target output or generated cache is part of the source map. If external source is retained, add permission and attribution details before submission.

## Week 5 owner review

- TV1 owns common, database, docs/architecture and contract portions of docs.
- TV2 reviews transaction and authorization effects of schema changes.
- TV4 reviews migration, constraint tests and CI evidence.
- Any breaking protocol or schema change requires an ADR and a versioned adapter.

