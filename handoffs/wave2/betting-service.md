# Betting Service — Wave 2 Handoff

## Purpose

This report records the completed Betting Service 1.0 ownership, its exact dependency contracts,
and the state that Settlement, Gateway, Risk, Wallet, Odds Feed, and orchestration must preserve.

## Final state

- Local branch: `betting-service`
- Final tip: `f712bdf389ee3fb63d8cdc84c49e2b84a346edde`
- History: 258 commits, one root, 257 single-parent commits, no merges
- Release artifact: `com.sportsbook:betting-service:1.0.0`
- Protocol dependency: `com.sportsbook:shared-protocol:1.0.0` from
  `f9de6bc1e533761ab4bb1454d8d4ab8175cdf001`
- Java target: 17
- Default HTTP port: 8082
- Flyway migrations: V1 through V10

The local branch points to the final tip. No tag, backup branch, or remote push was created.

## Ownership and trust boundary

Betting owns wager identity, request idempotency, placement and compensation state, durable
recovery, the accepted-bet outbox, user-scoped reads, and its local resolution projection. It does
not own balances, risk limits, effective odds, or payout calculation.

Every Betting business route under `/internal/**` or `/api/**` is denied unless the request has
the exact method and path allowlisted for caller `gateway`, one valid `BETTING_GATEWAY_API_KEY`,
and one canonical lowercase UUID in `X-User-Id`. The body cannot select its user. Missing or
invalid credentials return 401; a valid but unauthorized caller, route, or method returns 403.
Unknown methods do not fall through to a 405 boundary.

Normal startup requires three pairwise-distinct, deployment-managed secrets of at least 32
characters:

| Direction | Betting setting | Peer setting |
| --- | --- | --- |
| Gateway to Betting | `BETTING_GATEWAY_API_KEY` | `GATEWAY_BETTING_API_KEY` |
| Betting to Risk | `BETTING_RISK_API_KEY` | `INTERNAL_BETTING_SERVICE_API_KEY` |
| Betting to Wallet | `BETTING_WALLET_API_KEY` | `WALLET_BETTING_SERVICE_API_KEY` |

Risk and Wallet use separate HTTP clients with disjoint canonical origins. A credential is never
copied to the other dependency client or exposed through configuration output.

## Placement contract

The public Gateway routes terminate on these exact protected operations:

| Method and route | Success boundary |
| --- | --- |
| `POST /internal/v1/bets` | `201 ACCEPTED` or `202 PENDING`, both with `Location` |
| `GET /internal/v1/bets` | Stable user-scoped cursor page |
| `GET /internal/v1/bets/{betId}` | Owner-only snapshot or nondisclosing 404 |

`Idempotency-Key` claims a durable fingerprint of the trusted actor, slip shape, ordered
selections, submitted odds, and stake. Exact replay returns the stored result; semantic reuse is a
conflict. Validation and stable business rejections are durable, while infrastructure ambiguity
remains `PENDING`.

Placement advances monotonically:

```text
CREATED -> RISK_RESERVED -> WALLET_CONFIRMED -> RISK_COMMITTED -> ACCEPTED
```

Risk reservation evidence is persisted before Wallet can move money. Wallet debit uses the
canonical bet UUID as its stable idempotency key. Risk commit uses the persisted opaque token.
Acceptance and the `BetPlacedRequested` outbox row commit atomically.

If a durable failure occurs, Betting first completes the required idempotent compensation:

- release Risk when no Wallet debit succeeded; or
- credit `USER_LOCKED + REFUND` for the exact debited exposure when Risk cannot be committed.

The bet becomes `REJECTED` only after compensation is complete. A timeout or unknown dependency
response never invents a business verdict.

## Slip and exposure semantics

SINGLE and MULTIPLE `BetPlacedRequested` records carry null `systemMinWins` and
`systemTotalSelections`. SYSTEM records carry both fields. For SYSTEM bets, the stored, returned,
and published stake is the unit stake; Risk reservation, Wallet debit, and a whole-slip refund use
the full exposure:

```text
unit stake × C(total selections, minimum wins)
```

The full calculation is overflow checked. Settlement must preserve the same distinction when it
computes payouts or emits the original unit stake in settlement events.

## Dependency evidence

Risk reservation accepts only HTTP 200 with an explicit non-null approval decision. A missing
`approved` field is an unavailable or malformed dependency response, never a retained policy
rejection. Commit and release accept only exact HTTP 204. Only the documented 400
`VALIDATION_FAILED` is a durable validation result; other unexpected statuses remain failures.

Wallet debit, lookup, and refund accept only their exact documented status and a complete proof
matching operation group, trusted user, exact amount and currency, exact reason, and timestamp.
Only a 404 `WALLET_OPERATION_NOT_FOUND` proves absence. A 409 idempotency conflict triggers the
authoritative lookup under the original identity; Betting never creates a replacement key.

## Recovery

PostgreSQL is authoritative. Recovery claims at most 100 eligible `PENDING` rows, oldest first,
using database time and `FOR UPDATE SKIP LOCKED`. Every process has a random owner identity;
leases, heartbeats, completion, and release are owner fenced. Expired work can be reclaimed and a
slow former owner cannot clear or complete the new owner's claim.

Workers resume from persisted phase, Risk token, Wallet operation, commit observation, and
compensation state. Wallet Kafka records are only deduplicated wake-up hints; HTTP evidence decides
the monetary outcome.

## Settlement projection

Betting consumes raw Avro from:

| Topic | Key | Meaning |
| --- | --- | --- |
| `bet.settled.v1` | `eventId` | Logical revision 0, including normal market void |
| `bet.voided.v1` | `eventId` | Whole-slip lifecycle or administrative void |
| `bet.resolution.revised.v1` | `betId` | Full corrected snapshot, revision 1 or later |

`MatchResult.finalStatus=VOIDED` is represented by `BetSettled` with bet status `SETTLED` and
result `VOID`. `BetVoided` is reserved for `CANCELLED`, `POSTPONED`, or an authenticated
administrative whole-slip void. `MARKET_VOID` remains a wire enum symbol for compatibility but is
not a valid `BetVoided` reason.

The projection applies lower, duplicate, conflicting-equal, next, gap, revision-before-base, and
late-base cases under a row lock. A revision whose source result time is after its revision time is
rejected before ordering or replay classification. A gap is accepted from the full snapshot and
increments a bounded metric. Permanent key, payload, actor, identity, or state conflicts go to the
exact source partition of the uppercase `.DLT` topic only after broker acknowledgement.
Infrastructure failures remain on delayed source retry.

The read API omits `resolution` before terminal state. Afterwards it returns the current result or
void snapshot, payout where applicable, resolution time and event, and logical revision. Base and
legacy terminal rows expose revision 0; corrected rows expose their stable revision ID and number.
This is the authoritative reconnect and gap-recovery path for Gateway clients.

## Operations and rollout

Flyway applies V1 through V10 before Hibernate validates. All released V1 through V10 migration
artifacts are immutable; a future schema change must add V11 or later. Redis holds the effective
odds projection and an idempotency hint but cannot complete placement. Kafka publication is
at-least-once, and topic auto-creation is disabled.

Provision `bet.placed.v1`, the five consumed topics, and every uppercase `.DLT` companion of a
consumed topic with matching partitions. Enable Betting and Gateway revision consumers before the
Settlement revision producer. The outbox and placement recovery use independent schedulers.

## History and verification

The final history is linear and has one README-only root. Development commits use conventional
subjects with empty bodies, keep production and tests separate, and contain no fixup, squash,
development diary, changelog, reconstruction, or provenance material. The Maven baseline,
generated wrapper, and final documentation commit are the reviewed bulk exceptions. The release
commit immediately precedes the final documentation commit. Released V1 through V6 migration
blobs were preserved exactly through the reconstruction.

Static history and affected unit, contract, PostgreSQL 16, Kafka, and recovery-concurrency tests
passed. The final Java 17 `clean verify` ran 189 tests with zero failures, errors, or skips and also
passed Flyway V1 through V10, packaging, Spotless, and Checkstyle. Container-backed PostgreSQL
test classes close their Spring application context after each class so a dynamically restarted
database can never inherit a stale connection URL.
