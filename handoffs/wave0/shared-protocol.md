# Shared Protocol — Wave 0 Handoff

## Purpose

This report records the completed `shared-protocol` contract for Wave 1 and later service work. It
covers only the shared Java values, error representation, Avro wire contracts, release state, and
the integration obligations created by those contracts.

## Current status

- Local branch: `shared-protocol`
- Final tip: `f9de6bc1e533761ab4bb1454d8d4ab8175cdf001`
- Maven artifact: `com.sportsbook:shared-protocol:1.0.0`
- Java target: 17
- Maven Wrapper: 3.9.11
- Apache Avro: 1.12.0
- History: 75 commits, one root, 74 single-parent commits, no merges
- Release state: complete and verified

The local `shared-protocol` branch already points to the final tip. No Git tag, backup ref, remote
push, or temporary worktree remains. Downstream projects must consume `1.0.0` from their first
buildable Maven scaffold; they must not introduce an intermediate shared-protocol version switch.

## History quality

The completed history has the following properties:

- The root commit contains only the ownership README.
- Every later commit has exactly one parent.
- Commit subjects follow `type(scope): subject`; commit bodies are empty.
- Production changes and their dedicated tests are separate adjacent commits.
- Development commits change one or two production files by default.
- The development-history p90 is 78 changed lines and two files.
- The Maven wrapper, one cohesive `BetSlip` aggregate, one wire fingerprint safety gate, and the
  final documentation commit are the reviewed size exceptions.
- The final development commit releases `1.0.0`; the final commit is the single bulk project
  documentation commit.
- Reachable history and the final tree contain no development diary, changelog, retired-version
  fixture directory, or temporary validation output.

## Artifact ownership

`shared-protocol` owns shapes and invariants that must mean the same thing across services. It does
not own service repositories, Spring components, Kafka clients, database models, balances, risk
limits, odds-drift policy, or settlement calculations.

The library provides:

- `Currency` and overflow-safe Java `Money`
- normalized decimal `Odds` with American and fractional display conversion
- `BetId`, `UserId`, `EventId`, `MarketId`, and `SelectionId`
- bounded printable-ASCII `IdempotencyKey`
- `MarketType`, `BetStatus`, `SettlementResult`, and `BetSlipType`
- structurally consistent `BetSelection` and `BetSlip`
- shared `ErrorCode` and framework-neutral JSON `ProblemDetail`
- generated Avro `SpecificRecord` classes for wire v1

Java `Money` and Avro `com.sportsbook.protocol.event.Money` are deliberately different types.
Service adapters must convert between domain, JSON, and Avro representations explicitly and apply
service-owned business validation at those boundaries.

## Wire v1 inventory

Wire v1 contains exactly 14 top-level Avro records. The event topology to be implemented across
the sportsbook is:

| Topic | Record | Producer | Kafka key | Intended first-party consumer |
| --- | --- | --- | --- | --- |
| `wallet.debited.v1` | `WalletDebited` | wallet | `userId` | none currently |
| `wallet.credited.v1` | `WalletCredited` | wallet | `userId` | none currently |
| `wallet.debit-failed.v1` | `WalletDebitFailed` | wallet | `userId` | none currently |
| `risk.limit.violated` | `RiskLimitViolated` | risk | `userId` | none currently |
| `risk.pattern.suspected` | `RiskPatternSuspected` | risk | `userId` | none currently |
| `odds.changed` | `OddsChanged` | odds-feed | `eventId` | gateway |
| `market.status.changed` | `MarketStatusChanged` | odds-feed | `eventId` | none currently |
| `event.lifecycle` | `EventLifecycle` | odds-feed | `eventId` | settlement |
| `match.result` | `MatchResult` | odds-feed | `eventId` | settlement |
| `bet.placed.v1` | `BetPlacedRequested` | betting | `userId` | risk, settlement |
| `bet.settled.v1` | `BetSettled` | settlement | `eventId` | betting, gateway |
| `bet.voided.v1` | `BetVoided` | settlement | `eventId` | betting, gateway |
| `bet.resolution.revised.v1` | `BetResolutionRevised` | settlement | `betId` | betting, gateway |

The fourteenth record is the shared Avro `Money` named type and has no topic of its own.
`SettlementResultAvro` is a named enum declared by `BetSettled.avsc`, not a separate top-level
schema.

The shared library defines record shapes only. Producers, listeners, topic configuration,
delivery guarantees, idempotency stores, and dead-letter handling belong to their owning services
and orchestration.

## Resolution revision contract

`BetResolutionRevised` represents only a completed `SETTLED` bet whose result is replaced after a
corrected `MatchResult`. Lifecycle-driven `VOIDED` corrections are outside this contract.

The schema is `com.sportsbook.protocol.event.BetResolutionRevised`. Its fields are required, have
no defaults, and occur in this exact order:

| Position | Field | Avro type | Meaning |
| ---: | --- | --- | --- |
| 1 | `revisionId` | `string` | Stable UUID for the durable per-bet revision |
| 2 | `revisionNumber` | `long` | Strictly increasing per bet from 1 |
| 3 | `betId` | `string` | Bet UUID and Kafka partition key |
| 4 | `userId` | `string` | Owning user UUID |
| 5 | `eventId` | `string` | Event whose corrected result caused the revision |
| 6 | `previousResult` | `SettlementResultAvro` | Immediately preceding committed result |
| 7 | `newResult` | `SettlementResultAvro` | Full replacement result |
| 8 | `previousPayout` | `Money` | Immediately preceding payout snapshot |
| 9 | `newPayout` | `Money` | Full replacement payout snapshot |
| 10 | `sourceResultSettledAt` | `long`, `timestamp-millis` | Corrected source result time |
| 11 | `revisedAt` | `long`, `timestamp-millis` | Durable revision/outbox completion time |

The initial `BetSettled` event is logical revision 0. Corrections start at revision 1 and increase
monotonically for each bet. Both the previous and new result and payout are full snapshots so a
consumer can recover from delivery across separate topics without relying on global ordering.

Producer requirements:

1. Accept the corrected match result.
2. Calculate the difference between the committed and replacement payouts.
3. Complete the wallet adjustment as an idempotent operation.
4. Persist the revision and outbox entry in one transaction.
5. Publish `bet.resolution.revised.v1` with `betId` as the key.
6. Reuse the same revision ID, number, and payload for retries and redelivery.

Consumer requirements:

- Ignore a revision number lower than the stored revision.
- Treat the same revision ID, number, and payload as an idempotent no-op.
- Isolate equal revision numbers with conflicting identity or payload.
- Replace the projection from the full snapshot when a higher revision arrives.
- Record and alert on a revision-number gap without inventing an intermediate state.
- Allow a revision to establish the terminal projection before the base settlement arrives.
- Ignore a late logical revision 0 after a higher revision is stored.
- Reject or isolate revisions for `VOIDED`, `REJECTED`, or other out-of-scope states.

Gateway messages derived from this event must include `revisionId` and `revisionNumber` so clients
can reject duplicate or out-of-order WebSocket updates.

## Schema generation and evolution rules

The runtime uses raw Avro binary payloads without a Schema Registry writer-schema identifier.
Consumers read with their locally generated specific schema. Consequently, an optional field
addition to an existing record is not considered safe for a mixed deployment.

The existing wire-v1 records are fixed at these boundaries:

- record name and namespace
- field name and order
- primitive, collection, union, and named types
- enum symbols and order
- default presence and value
- timestamp logical types

When new semantics are required, add a new record and a dedicated topic instead of changing an
existing record in place.

Named schemas must be processed in this order:

1. `Money.avsc`
2. `BetSettled.avsc`
3. `BetResolutionRevised.avsc` and the remaining schemas

The Maven build enforces the first two entries through the Avro plugin `imports` configuration.
`BetResolutionRevised` reuses the existing `Money` record and `SettlementResultAvro` enum. Do not
duplicate either named type. Generated sources under `target/generated-sources/avro` are build
output and must not be tracked or edited directly.

## Verification completed

The fixed 75-commit history passed a complete root-to-tip replay with:

- Eclipse Temurin/OpenJDK 17.0.17
- Apache Maven 3.9.11
- Linux `aarch64`
- network disabled during acceptance runs
- a read-only, prewarmed Maven cache
- commit 3 verified with system Maven
- commit 4 onward verified with the Maven wrapper

The README-only root and repository-default commit passed structural checks. Every buildable commit
from 3 through 75 passed its applicable clean Maven gate. There were no replay failures.

The final tip passed:

- `./mvnw -o clean verify`
- 95 tests, with zero failures, errors, or skips
- 42 main sources and 38 test sources compiled
- Spotless over 55 Java files
- Checkstyle with zero violations
- exact 14-schema inventory validation
- field order, required/default, named-type, and timestamp logical-type validation
- canonical fingerprint validation for every wire-v1 schema
- generated `SpecificRecord` binary round trips
- payout-increase and payout-decrease revision snapshots

Release artifacts:

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `shared-protocol-1.0.0.jar` | 143,260 bytes | `91b012c095ac6634f37a418f9c47b2d2d1a997dbe29eda8b9fd175f473a7613a` |
| `shared-protocol-1.0.0-sources.jar` | 83,841 bytes | `3b8161aaddf508ef7a1038db79acf1f3eed8cdf64997f7a385eaa45f97f89222` |

## Wave 1 and later integration obligations

### All Java services

- Declare `com.sportsbook:shared-protocol:1.0.0` from the first buildable scaffold.
- Install this exact artifact into an isolated Maven repository before building a service history.
- Do not add a later dependency-alignment commit for the shared protocol.
- Keep Java compiler release 17 and verify class-file major version 61.
- Convert domain, HTTP, and Avro representations explicitly at service boundaries.
- Treat Kafka delivery as potentially duplicated and do not assume ordering across topics.

### Settlement

- Persist terminal lifecycle state so lifecycle-before-placement delivery cannot leave funds locked.
- Implement result correction as a durable per-bet revision plan.
- Complete the idempotent wallet adjustment before committing the revision event.
- Store the revision and its outbox entry atomically.
- Publish `BetResolutionRevised` with `betId` as the Kafka key.
- Preserve the same event identity and payload across retries and recovery.

### Betting

- Add durable stored revision ID and revision number to the settlement projection.
- Consume `bet.resolution.revised.v1` before the settlement producer is enabled.
- Apply the lower/equal/higher revision rules from this report.
- Handle revision-before-base delivery and late revision-0 delivery deterministically.
- Add duplicate, conflicting, gap, out-of-order, and restart recovery tests.

### Gateway

- Consume `bet.resolution.revised.v1` before the settlement producer is enabled.
- Route private revision notifications to the owning user.
- Include revision identity, revision number, full new result, and full new payout in the client
  message.
- Preserve enough information for clients to reject duplicate and reversed delivery.

### Wallet

- Provide the durable idempotent adjustment operation used by settlement corrections.
- Keep adjustment request/result ownership in the wallet service; it is not an Avro contract in
  this library.
- Ensure retries can retrieve the completed result without applying the monetary delta again.

### Orchestration

- Install only `shared-protocol:1.0.0` into the clean build repository used for all Java services.
- Provision `bet.resolution.revised.v1` and its dead-letter topic explicitly.
- Keep topic names and Kafka keys aligned with this report.
- Roll out revision consumers before enabling the settlement producer.
- Add end-to-end checks for payout increase, payout decrease, duplicate delivery, out-of-order
  delivery, and replay invariance.

### Other services

Risk and odds-feed continue to use the existing wire-v1 records without modifying their shapes.
Admin may use the shared Java value and error contracts, but correction orchestration remains an
HTTP service contract rather than a new shared Avro command. No service should move business
repositories, framework components, or runtime clients into `shared-protocol` for test convenience.

## Completion boundary

No further Wave 0 work is pending in `shared-protocol`. Later changes to this branch require an
explicit contract-evolution decision and must preserve the fixed wire-v1 records. The immediate
next step is to build service histories against the final `1.0.0` artifact and validate the
consumer-before-producer rollout at the orchestration boundary.
