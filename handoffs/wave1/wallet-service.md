# Wallet Service - Wave 1 Handoff

## Purpose

This report records the completed `wallet-service` 1.0 state for Wave 2 and later sessions. It
covers only Wallet Service, the contracts that its callers and event consumers must honor, and the
runtime boundaries that orchestration must preserve.

## Current status

- Final local branch: `wallet-service`
- Final tip: `c9a05f4d652f24ac97d3e1cd753f69cef2725ff3`
- History: 407 commits, one root, 406 single-parent commits, no merges
- Release artifact: `com.sportsbook:wallet-service:1.0.0`
- Protocol dependency: `com.sportsbook:shared-protocol:1.0.0`
- Java target: 17
- Default HTTP port: 8081

The local `wallet-service` branch points to the final tip. Its temporary branch and worktree were
removed. No tag, backup branch, or remote push was created, and there is no remaining Wallet
Service release gate from Wave 1.

## History and future verification

The final history has these verified properties:

- The root commit contains only the service ownership README.
- Every later commit has exactly one parent.
- Commit subjects follow `type(scope): subject`; commit bodies are empty.
- Production and test changes are separated in development commits.
- Handwritten development changes stay within the 100-line review boundary.
- The generated Maven wrapper and final documentation are the only bulk exceptions.
- The penultimate commit releases `1.0.0`; the final commit contains only the project README and
  the architecture, internal API, and operations documentation.

Future sessions must not check out and rebuild every historical SHA. Validate the final branch,
run tests directly related to a change during development, and run one final Java 17
`clean verify` plus the relevant integration smoke before release.

## Ownership and authoritative state

Wallet Service is the only component allowed to mutate wallet `available` or `locked` funds.
PostgreSQL is authoritative for:

- accounts and balances;
- immutable operation outcomes and semantic request fingerprints;
- matched ledger entries;
- settlement adjustment proofs and recovery debt;
- transactional outbox rows, stream sequences, and delivery leases; and
- integrity scan state.

Redis is only a 24-hour best-effort idempotency hint under
`idempotency:wallet:<Idempotency-Key>`. A missing marker or Redis outage falls back to PostgreSQL
and cannot alter a committed result. Kafka acknowledges event delivery but never decides whether a
wallet transaction committed.

Each user account has one currency, `KRW` or `USD`, and nonnegative `available` and `locked`
buckets. Their sum must fit in a signed 64-bit integer. The reserved ledger counterparties
`HOUSE` (`00000000-0000-7000-8000-000000000001`) and `EXTERNAL_PAYMENT`
(`00000000-0000-7000-8000-000000000002`) cannot be opened as user accounts.

A successful monetary operation commits its account mutation, one durable `SUCCEEDED` operation,
and one matched two-leg ledger group in the same PostgreSQL transaction. A recognized business
rejection commits an immutable `REJECTED` operation without changing the account or ledger; a
rejected debit also commits its debit-failed outbox event. Infrastructure failures roll back.

## Internal authentication and route authorization

Every protected request sends exactly one value for both headers:

```text
X-Internal-Service: <caller wire name>
X-Internal-Api-Key: <caller-specific secret>
```

| Caller | Wire name | Required Wallet Service environment variable |
| --- | --- | --- |
| Platform | `platform` | `WALLET_PLATFORM_API_KEY` |
| Gateway | `gateway` | `WALLET_GATEWAY_API_KEY` |
| Betting | `betting-service` | `WALLET_BETTING_SERVICE_API_KEY` |
| Settlement | `settlement-service` | `WALLET_SETTLEMENT_SERVICE_API_KEY` |
| Admin | `admin-api` | `WALLET_ADMIN_API_KEY` |

All five secrets are required, distinct, nonblank, and at least 32 characters. Missing, unknown,
duplicated, or invalid credentials return `401 WALLET_AUTHENTICATION_REQUIRED`. Valid credentials
used on a forbidden route or credit meaning return `403 WALLET_ACCESS_DENIED`. Unlisted methods and
paths are denied by default.

Anonymous GET access is limited to `/actuator/health`, `/actuator/health/**`, and
`/actuator/prometheus`. Platform credentials are required for `/actuator`, `/actuator/info`,
`/actuator/metrics`, and every other management route.

## HTTP contract

All wallet business routes are under `/internal/v1/wallet`.

| Method and path | Allowed caller | Success |
| --- | --- | --- |
| `POST /accounts` | Platform | `200 AccountResponse` |
| `GET /accounts/{userId}/balance` | Platform, Gateway | `200 BalanceResponse` |
| `POST /transactions/deposit` | Platform | `200 WalletOperationResponse` |
| `POST /transactions/withdraw` | Platform | `200 WalletOperationResponse` |
| `POST /transactions/debit` | Betting | `200 WalletOperationResponse` |
| `GET /transactions/debit/{betId}` | Betting | Stored result or operation `404` |
| `POST /transactions/credit` | Betting, Settlement, Admin | `200 WalletOperationResponse` |
| `POST /transactions/forfeit` | Settlement | `200 WalletOperationResponse` |
| `POST /transactions/adjustment` | Settlement | `200` applied or `202` blocked proof |
| `GET /transactions/adjustment/{revisionId}` | Settlement | Stored proof or adjustment `404` |

### Credit allowlist

Credit route access is not sufficient by itself. Exactly these meanings are authorized:

| Caller | Source | Reason |
| --- | --- | --- |
| `betting-service` | `USER_LOCKED` | `REFUND` |
| `settlement-service` | `USER_LOCKED` | `VOID` |
| `settlement-service` | `USER_LOCKED` | `REFUND` |
| `settlement-service` | `HOUSE_POOL` | `PAYOUT` |
| `admin-api` | `HOUSE_POOL` | `REFUND` |

All other caller, source, and reason combinations fail with `403` before a durable operation is
created.

### Idempotency identity

Every transaction POST requires exactly one `Idempotency-Key`; account creation is the only POST
that does not. A general key is nonblank printable ASCII, at most 128 characters, and is used
without trimming or normalization.

- Debit POST uses the canonical lowercase bet UUID as its key. Debit GET requires the same
  canonical UUID text in the path.
- Adjustment POST requires exactly
  `settlement:revision:<canonical-lowercase-revisionId>`.
- Repeating a key with the same authenticated caller and semantic fields returns the stored
  result, including a stored rejection or blocked adjustment.
- Reusing a key for different semantics returns `409 WALLET_IDEMPOTENCY_CONFLICT`.
- Authentication, authorization, malformed request, and transient database availability failures
  do not claim the key when no operation committed.

### Public representations

`AccountResponse` contains `userId`, `currency`, `available`, `locked`, `outboundFrozen`, `version`,
`createdAt`, and `updatedAt`.

`BalanceResponse` contains `userId`, `available`, `locked`, `total`, and `outboundFrozen`.

`WalletOperationResponse` contains `operationGroupId`, `userId`, `amount`, `reason`, and `at`.

Account and balance responses deliberately omit recovery debt, recovery timestamps, and queue
sequence state.

An adjustment proof always contains all 14 keys, including nullable values:

```text
revisionId, betId, revisionNumber, userId, previousPayout, newPayout,
deltaAmount, currency, status, queueSequence, operationGroupId,
queuedAt, appliedAt, nextAttemptAt
```

The proof does not expose retry count, its idempotency key, or persistence observation fields.

### Error boundary

Errors use RFC 9457 Problem Details with `type`, `title`, `status`, `detail`, `instance`, and
`errorCode`. Stored business errors may additionally include `balance` or `expectedCurrency`.
Credentials, idempotency keys, request bodies, SQL diagnostics, and exception text are not exposed.

| Status | Error codes |
| --- | --- |
| 400 | `WALLET_INVALID_REQUEST` |
| 401 | `WALLET_AUTHENTICATION_REQUIRED` |
| 403 | `WALLET_ACCESS_DENIED` |
| 404 | `WALLET_ACCOUNT_NOT_FOUND`, `WALLET_OPERATION_NOT_FOUND`, `WALLET_ADJUSTMENT_NOT_FOUND` |
| 409 | `WALLET_IDEMPOTENCY_CONFLICT` |
| 422 | `WALLET_CURRENCY_MISMATCH`, `WALLET_INSUFFICIENT_BALANCE`, `WALLET_AMOUNT_OUT_OF_RANGE` |
| 423 | `WALLET_ACCOUNT_RECOVERY_BLOCKED` |
| 500 | `WALLET_INTERNAL_ERROR` |
| 503 | `WALLET_BUSY` |

`WALLET_BUSY` includes `Retry-After: 1` and covers classified transient PostgreSQL availability,
connection, lock, timeout, deadlock, and serialization failures. Retry it with the same key.
Permanent database failures remain opaque `500` responses.

## Settlement adjustment and recovery

An adjustment identifies a unique `(betId, revisionNumber)` and contains nonnegative previous and
new payout snapshots in the same currency. `revisionNumber` is at least 1 and the delta is nonzero.

- A positive delta credits the account immediately and can wake an existing blocked head.
- A negative delta applies immediately only when sufficient available funds exist and there is no
  older blocked head.
- Otherwise the full negative amount is queued as one `BLOCKED` proof, returns HTTP `202` with a
  relative proof `Location`, increases recovery debt, and freezes outbound withdrawal and debit.
- Credits, deposits, forfeits, and positive adjustments remain allowed while the account is
  frozen. Inflows wake the queue head but do not perform recovery inline.

The recovery worker orders proofs only by `queue_sequence`; wall-clock timestamps are observations,
not causal order. One recovery transaction locks the account, FIFO head, and linked operation. It
either defers the complete head without changing money or applies the complete correction with a
two-leg `BET_ADJUSTMENT` group. Partial recovery and queue bypass are invalid.

When recovery completes, the proof becomes `APPLIED`, the operation becomes `SUCCEEDED`, recovery
debt is reduced, and the next head is woken. The account unfreezes only when debt reaches zero.
POST replay then returns the final applied proof with `200`; GET returns `APPLIED`, `BLOCKED`, and
`REJECTED` proofs with `200`.

## Transactional outbox and Kafka

Debit and credit outcomes append their outbox event in the same PostgreSQL transaction. Settlement
adjustments do not emit these wallet events.

| Topic | Shared Avro record | Kafka key |
| --- | --- | --- |
| `wallet.debited.v1` | `WalletDebited` | `userId` |
| `wallet.debit-failed.v1` | `WalletDebitFailed` | `userId` |
| `wallet.credited.v1` | `WalletCredited` | `userId` |

Values are plain Avro bytes from `shared-protocol` 1.0.0. Each record has exactly one US-ASCII
`event-id` header containing the outbox event UUID. There is no schema-registry framing or schema
header.

The outbox preserves FIFO order by monotonic `stream_sequence` within each `(topic, userId)` stream.
Claims use `FOR UPDATE SKIP LOCKED`; Kafka send and acknowledgement occur outside the claim
transaction. Completion is fenced by owner and `lease_version`, and expired leases can be taken
over safely.

Delivery is at-least-once. A crash after broker acknowledgement but before the PostgreSQL publish
mark can duplicate a record, so every consumer must deduplicate by `event-id`. Delivery retries
continue without an attempt limit using capped exponential backoff.

Automatic outbox delivery is deliberately off by default. Production orchestration must set
`WALLET_OUTBOX_ENABLED=true`; otherwise durable outbox rows accumulate without publication.

## Runtime and operations

PostgreSQL is mandatory. Flyway applies exactly `V1` through `V4`, and Hibernate validates the
schema. Redis is optional and best effort. Kafka is required when outbox delivery is enabled.

Important defaults are:

| Setting | Default |
| --- | --- |
| HTTP port | `8081` |
| Hikari maximum/minimum | `20` / `5` |
| Connection / lock / statement timeout | `2s` / `2s` / `5s` |
| Integrity scheduling | enabled, every `PT30S` |
| Recovery scheduling | enabled, every `PT1S` |
| Recovery retry base/cap | `PT1S` / `PT60S` |
| Outbox scheduling | disabled |
| Outbox poll / batch / max in flight | `PT1S` / `20` / `100` |
| Outbox lease | `PT30S` |
| Outbox retry base/cap | `PT1S` / `PT60S` |

Outbox owners default to `${HOSTNAME:wallet-service}-${random.uuid}` and must be unique per
process for lease fencing. Kafka producer delivery and maximum-block bounds are both five seconds.

The `walletIntegrityHealth` contributor is `UNKNOWN` before its first scan, `UP` after a clean
scan, and `DOWN` after scan failure or detected drift. Integrity checks use one repeatable-read
view and cover account snapshots, ledger topology, operation groups, recovery queues, adjustment
outcomes/failures/fingerprints/ledger groups, and aggregate drift.

Operational timestamps may move backward across transactions. Ordering must use status, account
version, `queue_sequence`, `stream_sequence`, and `lease_version`. The PostgreSQL clock owns only
operational deadlines such as outbox availability/lease times and adjustment retry time.

The complete environment mapping and Micrometer meter inventory are documented on the service
branch in `docs/operations.md`. Do not add unverified environment aliases in deployment manifests.

## Verification completed

The final Wallet Service state was verified with:

- Temurin Java 17.0.20 and Maven 3.9.11;
- the complete 451-test range with zero assertion failures, errors, or skips after rerunning one
  transient PostgreSQL-container startup failure;
- `-Psemantic-gates clean verify`: 83 tests covering real PostgreSQL 16, Redis 7, Kafka 3.8,
  authenticated HTTP, idempotency, recovery, authorization, and fenced outbox behavior;
- Spotless over 259 Java files and Checkstyle with zero violations;
- a Boot JAR restart gate that preserved PostgreSQL while flushing Redis, returned byte-identical
  deposit replay, rebuilt the Redis hint, and recovered a blocked negative adjustment;
- exact post-recovery proof, operation, account, and two-leg ledger assertions;
- Boot JAR `wallet-service-1.0.0.jar`, class-file major version 61, and exactly one bundled
  `shared-protocol-1.0.0.jar`;
- all four Flyway migrations in the Boot JAR; and
- `commons-lang3` 3.13.0 in the production artifact, with the Testcontainers-only 3.14.0 override
  excluded from packaging.

## Wave 2 integration obligations

### Platform and Gateway

- Platform owns account opening, deposits, withdrawals, and authenticated management access.
- Gateway may call only the balance route and must derive `{userId}` from its validated JWT subject.
- Gateway must inject `X-Internal-Service: gateway` and the secret corresponding to
  `WALLET_GATEWAY_API_KEY`; client-supplied internal headers must never reach Wallet Service.
- Platform must preserve transaction keys across retries. A `503` is transient and should be
  retried with the same key after the advertised delay.

### Betting Service

- Use the canonical lowercase `betId` UUID as both debit POST `Idempotency-Key` and debit GET path.
- Debit exact replay may return either the stored success or the original stored business problem.
- The only Betting credit meaning is `USER_LOCKED + REFUND`.
- Consume `wallet.debited.v1` and `wallet.debit-failed.v1` idempotently by `event-id`.
- Do not infer monetary success from Kafka arrival; the authoritative synchronous result is the
  wallet HTTP outcome and its durable PostgreSQL operation.

### Settlement Service

- Preserve payout snapshots, `(betId, revisionNumber)`, `revisionId`, and the exact adjustment key
  across retries.
- Treat `202 BLOCKED` as a durable queued result, not a failure. Follow the relative proof
  `Location` or GET by revision ID until terminal state.
- A later exact POST replay may change from `202 BLOCKED` to `200 APPLIED` after worker recovery.
- Settlement credit meanings are `USER_LOCKED + VOID`, `USER_LOCKED + REFUND`, and
  `HOUSE_POOL + PAYOUT`. Settlement also owns forfeit.

### Admin API

Admin may use only `HOUSE_POOL + REFUND` on the credit route. It has no account, balance, debit,
forfeit, adjustment, or management capability.

### Event consumers and orchestration

- Provision the three wallet topics with partitions suitable for user-key ordering; do not rely on
  topic auto-creation.
- Deduplicate every wallet event by its `event-id` header because publication is at-least-once.
- Install `shared-protocol:1.0.0` before building Wallet Service.
- Supply five distinct secrets through a secret manager and never commit example real values.
- Enable outbox scheduling explicitly in an environment that expects wallet Kafka events.
- Run PostgreSQL migrations before accepting traffic and monitor integrity health, outbox backlog,
  oldest pending age, retries, lease takeovers, and fenced completions.
- Keep recovery scheduling enabled on at least one replica. Multi-replica recovery and outbox
  workers are supported through PostgreSQL locking, `SKIP LOCKED`, and lease fencing.

The canonical service-owned detail remains in `README.md`, `docs/architecture.md`,
`docs/internal-api.md`, and `docs/operations.md` on the `wallet-service` branch.
