# Settlement Service — Wave 2 Handoff

## Purpose

This report records the completed Settlement Service 1.0 ownership, exact money and event
contracts, recovery guarantees, administrative boundary, and operational obligations that later
work must preserve.

## Final state

- Local branch: `settlement-service`
- Final tip: `fc53ee8bfbb99b083f504d414d84ae5a994e4b57`
- History: 504 commits, one root, 503 single-parent commits, no merges
- Release artifact: `com.sportsbook:settlement-service:1.0.0`
- Protocol dependency: `com.sportsbook:shared-protocol:1.0.0` from
  `f9de6bc1e533761ab4bb1454d8d4ab8175cdf001`
- Java target: 17
- Default HTTP port: 8084
- Flyway migrations: V1, V3 through V10

The local branch points to the final tip. No tag, backup branch, or remote push was created.

## Ownership and ordering

Settlement owns durable placement intake for resolution, terminal event lifecycle observations,
accepted match-result candidates, base payout calculation, whole-slip void execution, corrected
result revisions, Wallet evidence for its money operations, the transactional outbox, and the
authenticated operator control plane.

It consumes raw Avro from:

| Topic | Record | Required key |
| --- | --- | --- |
| `bet.placed.v1` | `BetPlacedRequested` | `userId` |
| `event.lifecycle` | `EventLifecycle` | `eventId` |
| `match.result` | `MatchResult` | `eventId` |

The three topics have no global order. A terminal lifecycle observation or result can arrive
before the corresponding placement. Settlement persists the earlier fact and catches it up after
placement. Duplicate and racing listeners remain idempotent under PostgreSQL constraints, row
locks, and immutable source fingerprints.

Every consumed source uses its exact uppercase `.DLT` companion. Permanent key, actor, payload,
chronology, or state failures publish to the exact source partition before acknowledgement.
Infrastructure failures remain on source retry. Topic auto-creation is disabled.

## Placement and SYSTEM semantics

SINGLE and MULTIPLE placements require null `systemMinWins` and `systemTotalSelections`. SYSTEM
placements require both. The stored and emitted SYSTEM stake remains its unit stake. Full monetary
exposure is:

```text
unit stake × C(systemTotalSelections, systemMinWins)
```

Selection identity and order are durable, and combination and money operations are overflow
checked. Conflicting reuse of placement identity is a permanent contract failure.

## Base resolution

An accepted result fans out to every unresolved bet containing that event. A multiple-event slip
can remain partial until every required selection has a terminal outcome. When a bet becomes
actionable, Settlement persists one complete immutable attempt before contacting Wallet.

Base Wallet operations use stable bet-and-purpose idempotency identities and require an exact
proof matching operation group, trusted user, amount, currency, reason, and timestamp. Settlement
is authorized for returned stake, whole-slip void refund, house-pool profit payout, and the
Settlement-owned locked-exposure forfeit.

After the required idempotent operations succeed, Settlement consumes an unexpired owner-fenced
lease, records the terminal bet timestamp, and inserts its outbox row in one transaction. A stale
owner cannot finalize or delete the new owner's attempt.

Settlement publishes:

| Topic | Record | Key | Meaning |
| --- | --- | --- | --- |
| `bet.settled.v1` | `BetSettled` | `eventId` | Base logical revision 0 |
| `bet.voided.v1` | `BetVoided` | `eventId` | Whole-slip terminal void |
| `bet.resolution.revised.v1` | `BetResolutionRevised` | `betId` | Corrected full snapshot |

`MatchResult.finalStatus=VOIDED` is normal market settlement and emits `BetSettled` with bet status
`SETTLED` and result `VOID`. `BetVoided` is reserved for lifecycle `CANCELLED`, lifecycle
`POSTPONED`, or an authenticated administrative whole-slip action. `MARKET_VOID` remains a fixed
wire enum symbol but is not a valid produced `BetVoided` reason.

## Corrected results

Every result is first a durable candidate. The accepted base candidate defines revision 0. A valid
replacement can create immutable per-bet revision plans numbered 1 or later. Settlement rejects a
candidate whose `sourceResultSettledAt` is after `revisedAt` before replay or ordering
classification.

Only `SETTLED` bets are revision targets. Whole-slip `VOIDED`, `REJECTED`, or other non-`SETTLED`
bets are excluded. A normal result `VOID` remains eligible because the bet status is `SETTLED`.

Each plan fixes the source candidate, predecessor, old and new snapshots, payout delta, stable
Wallet identity, and event payload before an external call. The target is locked and rechecked.
Only a newly inserted plan can execute directly; recovery reloads the exact stored plan.

A zero delta never contacts Wallet. Nonzero deltas use:

```text
settlement:revision:<revisionId>
```

Finalization requires an exact `APPLIED` Wallet proof. The revision number, bet snapshot, proof,
single database timestamp, and outbox row finalize atomically.

## Bounded revision recovery

Recovery uses PostgreSQL database time, oldest-first bounded batches, `FOR UPDATE SKIP LOCKED`,
random owner identities, and token-fenced leases. Every recovery path performs Wallet GET first.
POST can repeat only after the exact adjustment-not-found 404 and only with the original key.

Automatic execution is bounded to 12 attempts with capped exponential backoff:

- no durable Wallet proof becomes paused `EXHAUSTED`;
- durable queue proof remains paused `BLOCKED` with its positive sequence and timestamps intact;
- an authoritative Wallet semantic rejection becomes `REJECTED`; and
- exact `APPLIED` evidence permits finalization.

Timeouts, malformed bodies, unexpected statuses, and lost responses never invent Wallet rejection.
The correction catch-up scanner handles candidate, placement, and base-resolution races without
changing revision identity.

## Authentication

Settlement calls Wallet with exactly:

```text
X-Internal-Service: settlement-service
X-Internal-Api-Key: ${SETTLEMENT_WALLET_API_KEY}
```

The Wallet deployment supplies the matching `WALLET_SETTLEMENT_SERVICE_API_KEY`.

The administrative control plane requires exactly:

```text
X-Service-Name: admin-api
X-API-Key: ${SETTLEMENT_ADMIN_API_KEY}
```

Both configured secrets are required, nonblank, and at least 32 characters. They must be distinct;
duplicate values fail startup. Configuration rendering redacts both values.

## Administrative API

| Method and route | Purpose |
| --- | --- |
| `GET /internal/admin/result-candidates/{candidateId}` | Inspect candidate state and predecessor |
| `GET /internal/admin/revisions/{revisionId}` | Inspect revision, lease, and Wallet evidence |
| `POST /internal/admin/result-candidates/{candidateId}/approve` | Approve eligible pending candidate |
| `POST /internal/admin/result-candidates/{candidateId}/reject` | Reject candidate with printable reason |
| `POST /internal/admin/revisions/{revisionId}/retry` | Queue paused recovery |

Mutation requests require a UUID `Idempotency-Key`. Admin actions are append-only and bind that key
to a request fingerprint. Exact replay returns the prior result; semantic reuse conflicts.
Candidate approval is predecessor fenced, and rejection reasons are limited to 1 through 256
printable characters.

Revision retry is queue-only and never calls Wallet on the HTTP thread. An exhausted no-proof plan
returns to due `PENDING` attempt 0. A paused blocked plan keeps its proof and queue schedule. The
worker claims attempt 1 and then follows the same GET-first recovery rule. Replaying the admin
request does not reset attempts.

## Operations

Base recovery, lifecycle catch-up, result catch-up, correction recovery, and outbox publication
use isolated schedulers with bounded shutdown. Claims and finalization use database timestamps.
The tested PostgreSQL path claims 128 eligible rows across four concurrent workers without
duplicates and rejects stale-owner completion.

Kafka consumers use `earliest`, `read_committed`, disabled auto commit, and manual immediate
acknowledgement. Server shutdown is graceful, with a 20-second Spring phase. Wallet connect and
read timeouts are independently bounded.

Actuator exposes health, info, and Prometheus. Readiness includes the database-backed
`settlementDependencies` indicator. Metrics cover bounded flow outcomes and duration plus durable
backlogs for pending bets, blocked revisions, exhausted revisions, and unpublished outbox rows.

Flyway applies V1, V3 through V10 before Hibernate validation. Released migration files are
immutable; future schema work must append V11 or later.

## History and verification

The final history is linear and has one README-only root. Development commits use conventional
subjects with empty bodies and keep production, tests, configuration, and documentation in small
single-purpose atoms. It contains no fixup, squash, diary, changelog, reconstruction, or provenance
material. The generated wrapper, atomic V9 migration, and final README are the reviewed bulk
exceptions. The release commit immediately precedes the final documentation commit.

The final Java 17 `clean verify` ran 342 tests with zero failures, errors, or skips. It passed
PostgreSQL 16 integration and concurrent-claim tests, correction and admin contracts, migration
blob immutability, history guard, packaging, Spotless, and Checkstyle. Original V1, V3, V4, and V5
migration blobs are byte-for-byte identical to the prior released branch.
