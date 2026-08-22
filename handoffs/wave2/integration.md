# Wave 2 Integration Contract

## Purpose and authority

This report fixes the cross-service contract after Gateway, Betting Service, and Settlement
Service were completed against the Wave 0 and Wave 1 releases. When older handoffs conflict with
this report, apply authority in this order:

1. `shared-protocol:1.0.0` Avro schemas define the binary record shape, field order, types, and
   enum symbols.
2. The Wave 2 semantic corrections below define how those fixed fields and symbols are used.
3. The producing or consuming service handoff defines its local execution, persistence, retry,
   recovery, and operational behavior.

The protocol uses raw Avro without Schema Registry framing. A semantic clarification does not
authorize changing a wire-v1 schema in place.

## Fixed semantic corrections

### Placement shape and SYSTEM exposure

For `BetPlacedRequested`:

- SINGLE and MULTIPLE set both `systemMinWins` and `systemTotalSelections` to null.
- SYSTEM sets both fields.
- SYSTEM `stake` is the unit stake in the Betting database, API, `BetPlacedRequested`,
  `BetSettled`, and `BetResolutionRevised` snapshots.
- Risk reserve, Wallet debit, and a whole-slip refund use the full exposure:

```text
unit stake × C(systemTotalSelections, systemMinWins)
```

No consumer may infer that the published SYSTEM stake is already multiplied.

### Void boundaries

`MatchResult.finalStatus=VOIDED` is a normal settlement calculation. It produces `BetSettled`
with bet status `SETTLED`, result `VOID`, and the ordinary settlement payout rules.

`BetVoided` is reserved for a whole-slip terminal action caused by lifecycle `CANCELLED` or
`POSTPONED`, or by an authenticated administrative decision. `MARKET_VOID` remains in the fixed
wire enum for binary compatibility but is invalid as a produced or accepted `BetVoided` reason.

### Revision boundary

The first `BetSettled` event is logical revision 0. A corrected result creates a full replacement
snapshot at revision 1 or later. Settlement completes or durably queues the idempotent Wallet
adjustment before it commits the revision and its outbox record atomically. It publishes the same
revision identity and payload on every retry.

Settlement must not finalize or publish a revision whose `sourceResultSettledAt` is after
`revisedAt`. Betting and Gateway reject impossible chronology as a permanent contract failure
before duplicate, lower-revision, or delivery classification so their projections cannot diverge.

A revision does not apply to a whole-slip `VOIDED`, `REJECTED`, or other non-`SETTLED` bet. A
normal result of `VOID` can be corrected because the bet status remains `SETTLED`.

### Dead-letter names

Every Wave 2 source-specific dead-letter suffix is uppercase `.DLT`. Orchestration must provision
the DLT explicitly with at least the source partition count. A producer or recoverer must not
silently fall back to another partition or a lowercase topic.

## Service-to-service authentication

Every business service-to-service call supplies exactly one `X-Internal-Service` and one
`X-Internal-Api-Key`. Client-supplied values are removed at the public boundary. The minimum
matching credential pairs are:

| Direction | Caller identity | Caller setting | Callee setting |
| --- | --- | --- | --- |
| Gateway → Betting | `gateway` | `GATEWAY_BETTING_API_KEY` | `BETTING_GATEWAY_API_KEY` |
| Gateway → Wallet balance | `gateway` | `GATEWAY_WALLET_API_KEY` | `WALLET_GATEWAY_API_KEY` |
| Betting → Risk | `betting-service` | `BETTING_RISK_API_KEY` | `INTERNAL_BETTING_SERVICE_API_KEY` |
| Betting → Wallet | `betting-service` | `BETTING_WALLET_API_KEY` | `WALLET_BETTING_SERVICE_API_KEY` |
| Settlement → Wallet | `settlement-service` | `SETTLEMENT_WALLET_API_KEY` | `WALLET_SETTLEMENT_SERVICE_API_KEY` |

Every secret is deployment managed, nonblank, at least 32 characters, and distinct from other
principals and directions. HTTP clients with different trust or money authority must not share a
credential-bearing instance.

Settlement's separate administrative control plane requires exactly one
`X-Service-Name: admin-api` and one `X-API-Key` containing `SETTLEMENT_ADMIN_API_KEY`. That key must
differ from `SETTLEMENT_WALLET_API_KEY`, and duplicate values fail Settlement startup. Admin
mutation requests also carry a UUID `Idempotency-Key`.

## Placement path

The synchronous and durable path is:

```text
Client
  -> Gateway JWT and actor boundary
  -> Betting durable request and PENDING bet
  -> Risk reserve full exposure
  -> Wallet debit full exposure
  -> Risk commit
  -> Betting ACCEPTED plus transactional BetPlacedRequested outbox
  -> Risk reconciliation and Settlement intake
```

Only exact dependency status and response proof can advance the state machine. In particular:

- Risk reservation HTTP 200 must contain an explicit approval decision.
- Risk commit and release require exact HTTP 204.
- Wallet debit and refund require exact HTTP 200 and a complete proof matching user, amount,
  currency, reason, operation identity, and timestamp.
- Only Wallet's documented operation-not-found 404 proves absence.
- An HTTP timeout, unexpected status, malformed success body, or Kafka hint is not a business
  verdict.

Betting persists each proof before the next side effect and reconciles ambiguous work through
database-time, owner-fenced leases. Compensation completes before a durable rejection. The stable
idempotency identities are reused for every retry.

## Settlement input and ordering

Settlement consumes:

| Topic | Record | Key |
| --- | --- | --- |
| `bet.placed.v1` | `BetPlacedRequested` | `userId` |
| `event.lifecycle` | `EventLifecycle` | `eventId` |
| `match.result` | `MatchResult` | `eventId` |

Different topics have no global ordering. Settlement therefore persists terminal lifecycle and
result state independently, catches up a placement that arrives later, and makes duplicate or
racing intake idempotent under PostgreSQL constraints and row locks. A lifecycle tombstone must
not expire merely because no placement was present when it arrived.

Result candidates are ordered by their durable source identity and revision rules, not by process
arrival alone. Losing concurrent candidates are retained or marked superseded according to the
Settlement handoff; they do not overwrite the committed source result.

## Wallet settlement evidence

Initial settlement uses only the Wallet meanings authorized for caller `settlement-service`:

- `HOUSE_POOL + PAYOUT` credit;
- `USER_LOCKED + REFUND` or `USER_LOCKED + VOID` credit; and
- the Settlement-owned forfeit operation.

Every HTTP 200 must contain the exact operation group, user, amount, reason, and timestamp. A
mismatched or partial body is not monetary proof.

Corrected results use the Wallet adjustment contract and the stable key
`settlement:revision:<revisionId>`:

- HTTP 200 must contain an `APPLIED` proof.
- HTTP 202 must contain a `BLOCKED` proof with positive queue sequence and queue timestamps.
- GET returns the authoritative current proof with exact HTTP 200.
- `APPLIED` proves the ledger operation and may retain queue history only for a recovered negative
  adjustment.
- `REJECTED` contains neither queue nor ledger evidence.

After an ambiguous POST, Settlement performs GET first and retries POST only after the exact
adjustment-not-found 404, always with the same identity. Automatic execution is bounded to 12
database-timed attempts with capped exponential backoff. Exhaustion pauses the plan for
operator-visible intervention without inventing a Wallet rejection. A durable `BLOCKED` proof
remains `BLOCKED` with its queue identity intact. Only the authenticated idempotent administrative
retry contract may resume automatic work, and it reconciles Wallet state with GET before POST.

## Resolution outputs and consumers

Settlement publishes:

| Topic | Record | Key | Meaning |
| --- | --- | --- | --- |
| `bet.settled.v1` | `BetSettled` | `eventId` | Base logical revision 0 |
| `bet.voided.v1` | `BetVoided` | `eventId` | Whole-slip void |
| `bet.resolution.revised.v1` | `BetResolutionRevised` | `betId` | Corrected full snapshot |

Betting durably projects these events. Gateway sends owner-scoped realtime notifications but does
not store revision state. Betting accepts a valid higher full snapshot even across a revision gap,
records the gap, rejects an equal-number conflict, permits revision-before-base, and ignores a
late base after a higher revision.

The Betting read API is the reconnect and gap-recovery authority. Its terminal response includes
the current result or void, payout where applicable, resolution time and event, and the logical
revision identity and number. Clients keep the maximum revision observed, ignore duplicates and
lower revisions, and refresh that API after reconnect or a gap.

## Topic and deployment obligations

Orchestration must explicitly provision all source topics and uppercase `.DLT` companions used by
Gateway, Betting, Risk, and Settlement. Auto-creation is disabled. Topic keys must follow the
tables above; a consumer must not assume cross-topic ordering.

The safe rollout order is:

1. Install the exact `shared-protocol:1.0.0` artifact.
2. Provision PostgreSQL migrations, standalone Redis where required, Kafka topics, DLTs, secrets,
   and network policy.
3. Start Wallet, Risk, and Odds Feed and wait for their documented readiness boundaries.
4. Deploy Gateway and Betting with the matching, isolated credentials.
5. Enable Betting and Gateway revision consumers before enabling Settlement revision output.
6. Deploy Settlement, then enable placement and result traffic.
7. Exercise placement, compensation, lifecycle-before-placement, result-before-placement,
   base/revision reordering, blocked adjustment, retry exhaustion, and reconnect recovery gates.

Gateway remains a single replica in 1.0 because its Kafka-to-local-STOMP fan-out is not a
multi-replica broadcast design. Risk requires standalone Redis. No service may use direct Redis or
Kafka access as a substitute for its authenticated HTTP authority.

## Verification boundary

Static history and affected tests ran during development, including real-dependency integration
tests where necessary. The final Java 17 `clean verify` passed on the fixed tips: Gateway 127,
Betting 189, and Settlement 342 tests, all with zero failures, errors, or skips, followed by each
project's quality and packaging gates. Acceptance did not rebuild every historical SHA. Each final
history is linear, uses small single-purpose development commits, separates production from test
commits, and ends with one release commit followed by one bulk project-documentation commit.
