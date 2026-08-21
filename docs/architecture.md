# Architecture and invariants

## Ownership and authoritative state

PostgreSQL is the sole correctness authority for accounts, operations, adjustments, ledger entries,
outbox events, recovery debt, and delivery leases. The wallet service is the only component allowed
to mutate `available` or `locked` funds. Redis and Kafka never decide whether a monetary operation
committed.

Each user account has one currency and two nonnegative buckets:

- `available`: funds that can be withdrawn or moved into a bet;
- `locked`: funds reserved by a debit while the bet is open.

The accepted currencies are `KRW` and `USD`. `available + locked` must fit in a signed 64-bit
integer and may not exceed `Long.MAX_VALUE`. `HOUSE` and `EXTERNAL_PAYMENT` are reserved ledger
counterparties and cannot be opened as user accounts.

## Monetary writes and the ledger

A successful money movement commits the account mutation, operation outcome, and matched ledger
pair in one PostgreSQL transaction. The two ledger rows share the operation group, amount,
currency, business reason, and timestamp, and use opposite ledger sides. A transaction or
infrastructure failure rolls back the write. A recognized business rejection commits no balance or
ledger mutation, but does commit immutable `REJECTED` operation facts, an adjustment proof when
applicable, and a debit-failed outbox event for a rejected debit.

The ledger reasons are:

- `DEPOSIT` and `WITHDRAW` for external payment movement;
- `BET_DEBIT` for available-to-locked movement;
- `BET_PAYOUT` and `BET_REFUND` for credits;
- `BET_FORFEIT` for locked funds moved to the house;
- `BET_ADJUSTMENT` for settlement corrections.

Integrity scans verify account snapshots, ledger topology, operation groups, recovery queues,
adjustment outcomes, failure facts, semantic fingerprints, and adjustment ledger pairs against one
repeatable-read database view.

## Idempotency and stored outcomes

Every monetary POST is identified by exactly one `Idempotency-Key`. The first writer is serialized
with a PostgreSQL advisory lock scoped to its transaction. A versioned canonical binary encoding of
the authenticated caller and all semantic request fields is hashed with SHA-256 and persisted with
the operation.

When the same key and semantic fields are submitted again, the service returns the stored operation
outcome. An adjustment can return a stored `BLOCKED` proof and later an `APPLIED` proof after
recovery. Reusing the key with different semantic fields returns `WALLET_IDEMPOTENCY_CONFLICT`.
Stored rejection facts include their status, title, detail, code, and any balance or
expected-currency fact; they are not rebuilt from mutable account state.

Authentication, route authorization, malformed JSON, header validation, and semantic credit
authorization occur before a durable operation is created. Those failures do not consume an
idempotency key. A retryable PostgreSQL availability failure also leaves the key free when no
operation committed.

Redis contains only a 24-hour best-effort marker at `idempotency:wallet:<key>`. A missing marker or
an unavailable Redis instance always falls back to PostgreSQL. Redis loss cannot change a committed
outcome.

## Settlement adjustment and recovery

An adjustment identifies a `(betId, revisionNumber)` pair and carries nonnegative previous and new
payout snapshots in the same currency. The delta must be nonzero.

- A positive delta credits available funds immediately.
- A negative delta applies immediately when the account can pay and has no blocked queue head.
- Otherwise, the negative delta becomes a `BLOCKED` proof with a positive per-account
  `queue_sequence`; the absolute delta is added to recovery debt.

Recovery debt sets `outboundFrozen`. Only withdraw and debit enforce this freeze. Deposits, credits,
forfeits, and settlement inflows remain available. A deposit, credit, or positive adjustment wakes
the queue head in the same transaction as the inflow; it never runs recovery inline. This lock
ordering prevents a missed wake between an inflow and the worker.

The worker processes one due account in its own transaction:

1. lock a due account with `FOR UPDATE SKIP LOCKED`;
2. lock its first `BLOCKED` proof by `queue_sequence`;
3. lock the linked operation;
4. either defer the head or apply the full correction.

Insufficient funds change only `retry_count`, `next_attempt_at`, and the proof observation time. No
balance, ledger row, or operation status changes. Sufficient funds atomically write the full two-leg
`BET_ADJUSTMENT` transfer, move the proof from `BLOCKED` to `APPLIED`, move the operation from
`BLOCKED_FUNDS` to `SUCCEEDED`, reduce debt, and wake the next head. The account unfreezes only when
debt reaches zero. Partial application and queue bypass are invalid states.

## Transactional outbox

Debit and credit transactions append their event in the same PostgreSQL transaction as the wallet
outcome. Adjustments do not emit these events.

| Topic | Avro record |
| --- | --- |
| `wallet.debited.v1` | `com.sportsbook.protocol.event.WalletDebited` |
| `wallet.debit-failed.v1` | `com.sportsbook.protocol.event.WalletDebitFailed` |
| `wallet.credited.v1` | `com.sportsbook.protocol.event.WalletCredited` |

The binary Avro contracts come from `shared-protocol` 1.0.0. Every Kafka record uses the user UUID
string as its key and has one US-ASCII `event-id` header containing the outbox event UUID. There is
no schema header.

The outbox assigns a monotonic `stream_sequence` per `(topic, partition_key)`. A row is claimable
only when no preceding unpublished row exists for that stream. Claims use a short
`REQUIRES_NEW` transaction with `FOR UPDATE SKIP LOCKED`, so independent user streams can proceed
concurrently.

Kafka send and broker acknowledgement occur outside the claim transaction. Only after the broker
acknowledges the send does a separate transaction mark the row published. Both publication and
retry completion require the exact owner and `lease_version`, preventing a stale worker from
completing a lease taken by another process. Expired leases can be claimed again.

Delivery is at-least-once. A process failure after broker acknowledgement and before the database
mark can produce a duplicate, so consumers must deduplicate by `event-id`. Failures are retried
without an attempt limit, using exponential delay capped at 60 seconds. Automatic publication is
off by default and requires deliberate operational enablement.

## Time and causal order

Lifecycle and event observations such as `created_at`, `updated_at`, `requested_at`,
`completed_at`, `queued_at`, `applied_at`, `published_at`, and event occurrence time are not causal
ordering authorities. Clock movement between transactions may make a later observation numerically
earlier than a prior observation.

Causal state and ordering come from operation and adjustment status, account version,
`queue_sequence`, `stream_sequence`, and `lease_version`. Operational deadlines use the PostgreSQL
clock:

- outbox `available_at` and `lease_until`;
- adjustment `next_attempt_at`.

Queries compare those deadlines with the database clock. Business correctness never depends on
cross-transaction timestamp chronology.
