# Architecture

## Ownership boundary

Betting Service owns the lifecycle of a submitted wager from its first durable idempotency claim
through acceptance, compensated rejection, and the local projection of a later resolution. It
does not own user balances, risk limits, market truth, or payout calculation.

Authoritative responsibilities are divided as follows:

| Concern | Authority |
| --- | --- |
| Bet identity, slip, phase, and status | Betting PostgreSQL |
| Placement request idempotency | Betting PostgreSQL |
| Risk reservation and committed exposure | Risk Service |
| Wallet debit and refund | Wallet Service |
| Effective odds snapshot | Redis data written by Odds Feed |
| Settlement and payout calculation | Settlement Service |
| Betting's settlement projection | Betting PostgreSQL |
| Accepted-bet publication | Betting transactional outbox |

Redis is not authoritative for placement completion. Its idempotency entry is only a completed
request hint, and a cache miss falls back to PostgreSQL.

## Placement state machine

A newly persisted bet starts with `status=PENDING`, `placement_phase=CREATED`, and no
compensation. Forward progress is monotonic:

```text
CREATED
  -> RISK_RESERVED
  -> WALLET_CONFIRMED
  -> RISK_COMMITTED
  -> ACCEPTED
```

Each external success is stored before the next external action:

1. Validate the slip and current odds.
2. Persist the bet and placement request.
3. Reserve full exposure in Risk Service.
4. Persist the opaque reservation token and expiry.
5. Debit full exposure in Wallet Service with the canonical bet UUID as idempotency key.
6. Persist the wallet operation group.
7. Commit Risk using the persisted token.
8. Atomically mark the bet `ACCEPTED` and append `BetPlacedRequested` to the outbox.

SYSTEM stake has two deliberate meanings. The bet's stored and published `stake` is the unit stake.
The amount sent to Risk and Wallet is `unit stake × number of K-of-N combinations`.

## Compensation

Compensation intent is durable and fences forward placement. It has its own monotonic state:

```text
NONE -> REQUIRED -> IN_PROGRESS -> COMPLETED
```

Two actions are supported:

- `RISK_RELEASE`: Wallet rejected before a debit succeeded. Risk release is idempotent; a provider
  response that the reservation was already committed is terminal evidence, not a transient retry.
- `WALLET_REFUND`: Wallet debit succeeded but Risk commit returned a definitive not-found or
  conflict verdict. Betting credits the exact committed exposure using `refund:<betId>`.

The bet becomes `REJECTED` only after the required action is complete. Dependency timeouts never
invent a rejection and leave the bet `PENDING` for reconciliation.

## Recovery and evidence

The scheduled reconciliation workers claim at most 100 eligible `PENDING` bets in oldest-first
order. A PostgreSQL CTE uses database time and `FOR UPDATE SKIP LOCKED` to give concurrent replicas
disjoint work. Each process receives a non-configurable random owner identity; owner-fenced leases
exclude active workers, expire after a crash, and can only be cleared by their current owner. Claim
heartbeats never change the user-visible `updated_at`. A retry eligibility timestamp prevents the
oldest failures from starving later bets. Workers resume from persisted phase and evidence rather
than restarting placement:

- A stored risk token prevents a new reservation identity.
- A stored wallet operation prevents a second debit.
- Wallet lookup distinguishes `WALLET_OPERATION_NOT_FOUND` from every other 404 problem.
- A stored risk-commit observation prevents a false release path.
- A stored compensation action prevents forward progression.

Risk commit and both compensation checkpoints are monotonic and idempotent under the locked bet
row, so a replay after lease expiry cannot repeat a completed transition with different evidence.

Wallet `wallet.debited.v1` and `wallet.debit-failed.v1` records accelerate the same reconciliation
path. They are deduplicated by the exact single `event-id` header and checked against topic, bet,
user, and payload SHA-256. Receiving an event does not itself prove the monetary outcome.

## Idempotency

`Idempotency-Key` is owned by a durable placement request row. Its fingerprint includes actor,
slip shape, ordered selections, submitted odds, and stake.

- An exact replay returns the existing bet or the stored business rejection.
- Reuse by another user or with different semantics is a conflict.
- A concurrent unique-key collision is reread and resolved through the same replay rules.
- Validation, closed-market, and odds-drift verdicts are durable.
- Infrastructure failure does not claim a terminal business result.

## Transactional outbox

Acceptance and outbox insertion share one PostgreSQL transaction. The publisher reads pending rows,
sends raw Avro bytes with Kafka acknowledgement, and only then records `published_at`. A crash
after broker acknowledgement can cause redelivery, so consumers must remain idempotent.

`BetPlacedRequested` is published to `bet.placed.v1` with `userId` as Kafka key. For non-SYSTEM
slips both `systemMinWins` and `systemTotalSelections` are null. For SYSTEM slips both fields are
present and `stake` remains the unit stake.

## Resolution projection

Betting consumes three settlement topics:

| Topic | Record | Kafka key |
| --- | --- | --- |
| `bet.settled.v1` | `BetSettled` | `eventId` |
| `bet.voided.v1` | `BetVoided` | `eventId` |
| `bet.resolution.revised.v1` | `BetResolutionRevised` | `betId` |

Values and keys enter the listener as raw bytes. A key mismatch, malformed identifier, schema
mismatch, actor mismatch, or conflicting replay is a permanent failure and is published to the
same partition of the topic's uppercase `.DLT` topic. The DLT send must be acknowledged before the
source offset is recovered. Infrastructure failures remain on unlimited delayed source retry.

Base `BetSettled.stake` must equal the original wager or SYSTEM unit stake, and its `eventId` must
belong to a selected leg. A whole-slip `BetVoided.refund` must equal the full committed exposure and
its `eventId` must also be selected. `MARKET_VOID` is represented only by `BetSettled` with
`status=SETTLED` and `result=VOID`; a `BetVoided` carrying that reason is a permanent contract
failure.

Base events are logical revision 0. `BetResolutionRevised` starts at revision 1 and carries full
previous and replacement snapshots. Its `sourceResultSettledAt` must not be after `revisedAt`;
impossible chronology is rejected before replay or revision-order classification. Under a locked
bet row:

- a lower revision is ignored;
- the same revision identity and payload is a duplicate;
- an equal number with conflicting identity or payload is rejected;
- the next number replaces the projection after previous-snapshot validation;
- a higher number with a gap replaces the projection and reports the gap result;
- a revision may establish a terminal projection for an `ACCEPTED` bet before base delivery;
- a late base event is ignored after revision 1 or later;
- revisions for `VOIDED`, `REJECTED`, and other out-of-scope states are rejected.

Every applied gap increments the low-cardinality `betting.resolution.revision.gaps` counter;
ordinary sequential revisions and duplicate deliveries do not.

## Persistence layout

Flyway applies migrations V1 through V10 and Hibernate validates the resulting schema at startup.

| Migration | Responsibility |
| --- | --- |
| V1 | Bet aggregate, leg order, wager values, and idempotency uniqueness |
| V2 | Transactional outbox |
| V3 | Settlement result and payout projection |
| V4 | Whole-slip void reason |
| V5 | Durable placement request and recovery phase |
| V6 | Compensation state, wallet proof, and risk commit observation |
| V7 | Opaque risk reservation token |
| V8 | Wallet-event receipt and reconciliation checkpoint |
| V9 | Base/revision identity, number, payload hash, and source result time |
| V10 | Database-time reconciliation eligibility and owner-fenced recovery leases |

All released migrations V1 through V10 are immutable compatibility artifacts. Future schema
changes must use V11 or later rather than editing those files.
