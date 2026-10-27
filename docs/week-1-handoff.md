# TV1 week 1 handoff

**Owner:** TV1 - Kiến trúc & dữ liệu
**Deadline:** Chủ nhật tuần 1, 20:00 (PR vào `develop`)

## Delivered

- Repository root POM, Maven Wrapper configuration and `common` module skeleton.
- Protocol v1 enums/envelopes under `com.btl.team10.common`.
- Contract tests for enum values, request identity, response error and event sequence.
- SQLite core/advanced schema and idempotency/audit tables.
- Demo seed and data dictionary using cents, UTC timestamps and hashed demo passwords.
- ADR-001 target architecture, ADR-002 protocol/database and ADR-003 identity/config.
- Class diagram and place-bid sequence diagram in Mermaid.

## Contract for other members

- Import package `com.btl.team10.common.*`.
- Do not add JDBC/JavaFX dependency to `common`.
- Use `AuctionStatus` and `BidStatus` values exactly as defined in protocol v1.
- Server actions/events must use `requestId`, `errorCode`, `eventId` and `sequence` fields.
- Schema changes require a new migration file and an ADR update in the same PR.

## Verification command

From `BTL/`:

```bash
mvn -B -pl common -am clean verify
```

The command requires JDK 21 or a Maven installation. The repository includes the Maven Wrapper script, properties and JAR, so a clean clone does not need Maven preinstalled. The parent build sets `useModulePath=false` for this contract-only module so JUnit test sources compile on the ordinary classpath while the common API remains Java 21 compatible. In this sandbox, `mvn -DskipTests=true -pl common -am verify` succeeds and produces `common/target/auction-common-0.1.0-SNAPSHOT.jar`; the test source also compiles. Running the final Surefire phase is blocked only because this local Maven cache lacks `surefire-junit-platform`, while CI will resolve it normally. If the wrapper cannot download its Maven distribution in a restricted environment, use any local Maven 3.8+ installation to run the same command.

## Next owners

- TV2: consume common protocol and create server/network skeleton without changing v1 fields.
- TV3: consume common enums/envelopes through a client adapter; use mock JSON until TV2 endpoints are ready.
- TV4: extend contract tests and CI with server/client modules after their skeletons are merged.
