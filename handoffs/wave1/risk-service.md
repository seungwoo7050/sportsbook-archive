# Risk Service - Wave 1 Handoff

## Purpose

This report records the completed `risk-service` state for Wave 2 and later work. It covers only
Risk Service, the contracts that other services must honor, its runtime assumptions, and the
operational boundaries that orchestration must provide.

## Current status

- Final local branch: `risk-service`
- Final tip: `c64f67dbc437a18640dc4984dea4d8194fb5b164`
- Final tree: `d5c0ba54327d301f08d305b73d93d7dd6977387b`
- History: 278 commits, one root, 277 single-parent commits, no merges
- Release artifact: `com.sportsbook:risk-service:1.0.0`
- Protocol dependency: `com.sportsbook:shared-protocol:1.0.0`
- Java target: 17
- Default HTTP port: 8083

The local `risk-service` branch already points to the final tip. The temporary Risk Service ref and
worktree were removed. No Git tag, backup ref, or remote push was created.

## History quality

The final history has the following verified properties:

- The root commit contains only the service ownership README.
- Every later commit has exactly one parent.
- Commit subjects follow `type(scope): subject`; commit bodies are empty.
- Production and test changes are not mixed in the same development commit.
- No development commit changes more than two production files.
- No handwritten development commit exceeds the 100-line review boundary.
- The generated Maven wrapper and final project documentation are the only bulk exceptions.
- The penultimate commit releases `1.0.0`; the final commit is the single bulk documentation
  commit.
- Reachable Risk Service history and the final tree contain no development diary, changelog,
  provenance note, old project version, reconstruction wording, or tracked load result.

## Ownership and runtime boundary

Risk Service owns:

- atomic bet admission;
- short-lived capacity reservations;
- single-bet and rolling user limits;
- suspicious-pattern evaluation;
- per-user administrative limit overrides; and
- reconciliation of accepted `BetPlacedRequested` events.

Redis is the authoritative runtime state for admission, reservation lifecycle, committed rolling
counters, pattern history, and overrides. The service has no relational database and no
transactional Kafka outbox.

The supported Redis topology is one standalone Redis server. The Lua operations intentionally
touch bet-scoped and user-scoped keys that do not share a Redis Cluster hash slot. Do not deploy
this release on Redis Cluster.

Kafka is used for accepted-bet reconciliation and advisory risk signals. Kafka signal publication
does not authorize or reverse an admission decision.

## Internal authentication and ownership

Every internal request requires both headers:

```text
X-Internal-Service: <caller>
X-Internal-Api-Key: <caller-specific secret>
```

The configured principals are:

| Caller | Environment variable | Allowed operations |
| --- | --- | --- |
| `betting-service` | `INTERNAL_BETTING_SERVICE_API_KEY` | Reserve, commit, and release risk capacity |
| `admin-api` | `INTERNAL_ADMIN_API_KEY` | Read, set, and clear user limit overrides |
| `platform` | `INTERNAL_PLATFORM_API_KEY` | Run non-reserving diagnostic checks |

All three secrets are required, must contain at least 32 characters, and must be distinct. Startup
fails when the credential set is invalid. Risk Service stores SHA-256 digests and compares them in
constant time.

Missing or invalid credentials return 401. An authenticated principal using another principal's
route receives 403. Health probes and Prometheus are anonymous; all internal business routes are
protected.

## HTTP contracts

### Endpoint inventory

| Method and path | Caller | Success semantics |
| --- | --- | --- |
| `POST /internal/v1/risk/reservations` | `betting-service` | `200` with an approved lease or a retained policy rejection |
| `PUT /internal/v1/risk/reservations/{betId}/commit` | `betting-service` | `204` for an applied commit or exact replay |
| `DELETE /internal/v1/risk/reservations/{betId}` | `betting-service` | `204` for applied release, replay, missing state, or tombstoned state |
| `GET /internal/v1/risk/limits/{userId}` | `admin-api` | `200` with all effective configurable limits |
| `PATCH /internal/v1/risk/limits/{userId}` | `admin-api` | `204` after replacing one override |
| `DELETE /internal/v1/risk/limits/{userId}/{type}` | `admin-api` | `204` after clearing one override |
| `POST /internal/v1/risk/check` | `platform` | `200` with a point-in-time diagnostic result |

Shared `ProblemDetail` responses use `VALIDATION_FAILED` for invalid requests. Retained `betId`
reuse with a different fingerprint returns `409 DUPLICATE_BET`. Commit of missing or terminal
state returns `404 RISK_RESERVATION_NOT_FOUND`. Release of a committed reservation returns
`409 RISK_RESERVATION_COMMITTED`. Unexpected failures are returned as opaque `INTERNAL_ERROR`
responses without exposing exception text.

### Reservation request

Reservation admission and the diagnostic endpoint use the same request shape:

```json
{
  "userId": "10000000-0000-4000-8000-000000000001",
  "betId": "20000000-0000-4000-8000-000000000001",
  "stake": {"amount": 1000, "currency": "KRW"},
  "selectionIds": ["30000000-0000-4000-8000-000000000001"]
}
```

Identifiers must be UUIDs. There must be one to 15 unique selection IDs. Currency is `KRW` or
`USD`, and `stake.amount` must be in `1..9007199254740991`.

An approved response includes `reservationState=RESERVED`, `expiresAt`, pattern flags, and an
opaque `reservationToken`. A policy rejection is also a successful HTTP 200 application result
with `approved=false`; it is not an HTTP transport failure. Exact request replay returns the
retained result with `replayed=true`.

Commit must present the approved token:

```text
X-Risk-Reservation-Token: <64-character lowercase SHA-256 token>
```

The token binds the semantic request but is not an authentication credential. Caller
authentication remains mandatory.

The default lease is two minutes and the retained lifecycle window is 32 days. Betting must not
assume that an expired or released reservation can be committed.

### Limit administration

The configurable overrides are:

- `STAKE_DAILY` for `KRW` or `USD`;
- `STAKE_WEEKLY` for `KRW` or `USD`;
- `STAKE_MONTHLY` for `KRW` or `USD`; and
- currency-neutral `SELECTIONS_PER_MINUTE`.

`GET` returns seven entries: three monetary periods for two currencies plus one selection limit.
Each entry identifies `POLICY` or `OVERRIDE` as its source. The single-bet maximum is policy-owned
and is not part of this override API.

Monetary override mutation requires a matching currency. Selection-limit mutation must omit
currency. Values may be zero and must not exceed `9007199254740991`.

## Admission and reservation consistency

One `risk-reserve.lua` execution performs admission. It validates the relevant Redis types and
aggregates, removes expired user footprints, evaluates committed and active capacity, evaluates
patterns, and atomically stores either a rejection or a reservation.

Admission includes both committed counters and unexpired reservations. Concurrent requests for
the same user therefore cannot all ignore one another's provisional capacity.

Commit atomically:

1. verifies the reservation token;
2. removes active stake and selection footprints;
3. adds the full exposure to daily, weekly, and monthly currency-specific windows;
4. adds the selection count to the per-minute window;
5. records confirmed pattern facts; and
6. changes the lifecycle to `COMMITTED`.

Release removes active footprints and changes the lifecycle to `RELEASED`. Lazy expiry removes
active footprints and retains an `EXPIRED` lifecycle for bounded replay behavior. Script
inconsistency is an error; there is no fail-open admission fallback.

Pattern actions have these meanings:

- `SUSPECT` and `REVIEW` are advisory flags;
- `BLOCK` rejects admission and retains the result for replay.

The diagnostic endpoint reads current state but does not reserve capacity. Betting must never use
`POST /internal/v1/risk/check` as authorization to debit or accept a bet.

## Kafka contracts

### Accepted-bet input

| Property | Contract |
| --- | --- |
| Topic | `bet.placed.v1` |
| Kafka key | Lowercase canonical `userId` UUID |
| Value | Plain Avro `BetPlacedRequested` bytes with no schema-registry framing |
| Consumer group | `risk.bet-placed-consumer` |
| Offset handling | Manual immediate acknowledgment |

The consumer validates the Kafka key, canonical identifiers, idempotency key, selection identity,
odds, slip shape, selection count, and exactly representable exposure.

Slip exposure rules are:

- `SINGLE`: exactly one selection; event stake is total exposure.
- `MULTIPLE`: two to 15 selections; event stake is total exposure.
- `SYSTEM`: valid `systemTotalSelections` and `systemMinWins`; event stake is the unit stake, and
  Risk Service derives total exposure as `unit stake * C(totalSelections, systemMinWins)`.

For reservation HTTP calls, Betting Service must send the full amount to reserve. For the
subsequent `SYSTEM` Kafka event, it must retain the shared wire contract and publish the unit stake
plus valid system fields. Risk Service derives the same full exposure before reconciliation.

The event consumer first attempts to commit a matching retained reservation. If no lifecycle is
retained, it atomically projects the accepted fact into committed counters and pattern history,
guarded by `risk:event:fingerprint:<betId>`. Matching redelivery is a replay; conflicting identity
is a permanent failure.

### Dead-letter contract

Permanent accepted-event failures are published to `bet.placed.v1.DLT` with the original key and
payload plus the ASCII header:

```text
risk-dlt-reason: MALFORMED_EVENT | KEY_MISMATCH | FINGERPRINT_MISMATCH | TERMINAL_RESERVATION
```

The service waits up to ten seconds for the DLT broker acknowledgment before acknowledging the
source record. A crash between those acknowledgments can duplicate the DLT record. DLT processing
must be idempotent.

Unhandled Redis, Kafka, or application failures are not acknowledged and retry every second
without an attempt limit. One persistent transient-class failure can therefore hold its source
partition.

### Advisory outputs

| Topic | Kafka key | Plain Avro value | Role |
| --- | --- | --- | --- |
| `risk.limit.violated` | `userId` | `RiskLimitViolated` | Best-effort diagnostic signal |
| `risk.pattern.suspected` | `userId` | `RiskPatternSuspected` | Best-effort diagnostic signal |

Only `POST /internal/v1/risk/check` emits these signals. Reservation admission returns its result
directly and does not publish a risk signal. Signal delivery is best effort, has no outbox, and
does not change the diagnostic response.

Kafka key serialization is `String`; values use `ByteArray` serialization. Do not configure a
String value serializer or deserializer for these Avro payloads.

## Redis operational contract

Important state includes:

| Key pattern | Meaning |
| --- | --- |
| `risk:reservation:<betId>` | Fingerprint, identity, exposure, patterns, lifecycle, and timestamps |
| `risk:reservations:user:{<userId>}:*` | Active reservation footprints and exact aggregates |
| `risk:limit:{<userId>}:<dimension>:*` | Currency-scoped committed windows and selection windows |
| `risk:limit:override:{<userId>}` | Administrative overrides |
| `risk:history:{<userId>}:*` | Confirmed rapid, sudden-stake, and repeated-selection facts |
| `risk:event:fingerprint:<betId>` | Bounded first-seen accepted-event identity |

Active aggregate keys intentionally have no independent TTL. Lifecycle evidence is required for
safe lazy cleanup. Redis eviction or partial key repair can break consistency checks. Operate the
dataset with `noeviction`, persistence, coherent backup/restore, and restricted network access.

Risk Service does not reconstruct Redis from Kafka. Host-loss durability, replication, backup,
restore, and controlled disaster recovery are orchestration responsibilities.

## Health, readiness, and metrics

The readiness group contains:

- Spring `readinessState`;
- Redis health; and
- a Kafka metadata query with a two-second budget.

Readiness is `DOWN` when Redis or Kafka is unavailable. The process itself does not stop receiving
traffic; the runtime must remove an unready instance from service.

Anonymous operational routes are:

```text
/actuator/health
/actuator/health/liveness
/actuator/health/readiness
/actuator/prometheus
```

Core application metrics include diagnostic latency and decisions, signal delivery, reservation
Lua latency, expiration cleanup, admission results, transition results, accepted-event
reconciliation, and DLT counts. Metric tags are bounded enums only; user, bet, selection, and token
identifiers are not used as tags.

## Verification completed

The final Risk Service tip passed:

- Temurin Java 17.0.17 and Maven 3.9.11 `clean verify`;
- 222 tests with zero failures, errors, or skips;
- real Redis 7.4 snapshot, counter, reservation, commit, release, expiry, and corruption tests;
- embedded Kafka accepted-event projection and DLT tests;
- broker-to-listener-to-Redis projection and duplicate-delivery tests;
- Kafka byte-array serialization contract tests;
- caller authentication and endpoint ownership tests;
- Spotless over 201 files;
- Checkstyle with zero violations;
- final artifact `risk-service-1.0.0.jar` with Java 17 class-file version 61;
- exactly one bundled `shared-protocol-1.0.0` artifact and no other shared version; and
- the isolated Redis 7.4 and Kafka correctness gate for concurrent admission, reservation replay,
  lifecycle transitions, readiness, and metric cardinality.

## Wave 2 integration obligations

### Betting Service

Betting is the primary synchronous client and must implement the full reservation lifecycle:

1. Generate the final stable `betId` before risk admission.
2. Send the full monetary exposure and one to 15 unique selection UUIDs to the reservation API.
3. Preserve the same request across retry; do not reuse a retained `betId` for another semantic
   request.
4. Treat HTTP 200 with `approved=false` as a terminal policy rejection, not as success to continue.
5. Persist the returned reservation token with saga state before relying on it.
6. After persisting the exact Wallet debit proof, commit with `X-Risk-Reservation-Token` before
   atomically transitioning the bet to `ACCEPTED` with its outbox row.
7. Release on a compensated or abandoned placement. Release is intentionally idempotent.
8. Publish `BetPlacedRequested` with Kafka key `userId` and raw Avro bytes.
9. Keep the `SYSTEM` event's unit stake and system fields consistent with the full amount reserved
   over HTTP.

Betting must use its own service credential and must not call the platform diagnostic endpoint as
an admission shortcut.

### Admin API

Admin must call the limit endpoints with the `admin-api` principal and its own secret. It must
preserve the monetary limit's currency and omit currency for `SELECTIONS_PER_MINUTE`.

Risk Service owns the override truth. Admin audit storage may record the attempted and returned
operation, but Admin must not maintain a second authoritative risk-limit value.

### Orchestration

Orchestration must:

- run Risk Service on port 8083;
- inject three distinct secrets of at least 32 characters into the correct callers and Risk
  Service without tracking their values;
- provide standalone Redis 7 with persistence, `noeviction`, coherent backup/restore, and access
  controls;
- provide Kafka and provision `bet.placed.v1`, `bet.placed.v1.DLT`,
  `risk.limit.violated`, and `risk.pattern.suspected` explicitly;
- keep String key and byte-array value serializers/deserializers for Risk Service topics;
- deploy Risk Service and wait for readiness before enabling Betting Service traffic;
- route traffic only to ready instances; and
- alert on DLT growth, consumer lag, dependency readiness, failed signal delivery, and reservation
  script latency.

HTTP principal checks do not secure direct Redis or Kafka access. Network segmentation and
Redis/Kafka authentication and ACLs must prevent unrelated service containers from mutating Risk
Service state or publishing privileged input.

### Gateway, Wallet, Odds, and Settlement

These services have no direct Risk Service HTTP dependency in the 1.0 contract.

- Gateway must not expose Risk Service internal endpoints publicly.
- Wallet does not use risk reservations or risk signals.
- Odds does not write Risk Service Redis keys.
- Settlement does not call Risk Service for settlement or correction processing.

Their orchestration credentials and network permissions must not grant Risk Service mutation
access accidentally.

## Recommended rollout order

1. Install and pin `shared-protocol:1.0.0`.
2. Provision Redis, Kafka topics, service credentials, and network policy.
3. Deploy Risk Service and wait for readiness and consumer assignment.
4. Deploy the Betting Service reservation client and accepted-event producer.
5. Deploy Admin API limit delegation.
6. Run the cross-service placement, compensation, accepted-event replay, DLT, and dependency
   outage gates.

## Deliberate boundaries and deferred work

Wave 2 must not assume the following capabilities exist:

- Redis Cluster support;
- a relational journal or automatic Redis rebuild from Kafka;
- a transactional outbox for advisory risk signals;
- finite retry or automatic poison-record bypass for transient accepted-event failures;
- an internal DLT replay API;
- unbounded accepted-event idempotency after the 32-day retention window; or
- runtime traffic draining performed by the service itself.

These boundaries do not relax atomic admission, currency isolation, request fingerprinting,
reservation token validation, accepted-event reconciliation, caller authentication, or fail-closed
Redis behavior.
