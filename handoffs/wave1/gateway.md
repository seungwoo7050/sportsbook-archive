# API Gateway - Wave 1 Handoff

## Purpose

This consolidated report records Gateway 1.0 after the Wave 2 authentication and void-contract
corrections, which supersede the earlier Wave 1 pending obligations. It covers only gateway-owned
behavior, the contracts other services must satisfy, and the limits that later sessions must
preserve.

## Current status

- Released local branch: `gateway`
- Final tip: `8248a3233f0fce7ca36a503ee71b7a8a0802d733`
- History: 117 commits, one root, 116 single-parent commits, no merges
- Release artifact: `com.sportsbook:gateway:1.0.0`
- Protocol dependency: `com.sportsbook:shared-protocol:1.0.0`
- Java target: 17
- Intended deployment topology: one gateway replica

The local `gateway` branch already points to the final tip. No tag, backup branch, or remote push
was created. There is no remaining gateway release gate in Wave 1.

## History quality

The final history has the following verified properties:

- The root commit contains only the ownership README.
- Every later commit has exactly one parent.
- Commit subjects follow `type(scope): subject`; commit bodies are empty.
- Production and test changes are separated in development commits.
- Handwritten development changes stay within the 100-line review boundary.
- The Maven wrapper and final project documentation are the only bulk exceptions.
- The penultimate development commit releases `1.0.0`; the final commit is the single bulk
  documentation commit.
- Reachable history and the final tree contain no development diary, changelog, provenance note,
  old project version, reconstruction narrative, or tracked load result.

Future sessions do not need to replay every historical commit. Validate the final tip and run the
targeted tests relevant to any new change.

## Delivered runtime behavior

### Authentication and trust boundary

The normal runtime requires `GATEWAY_JWT_PUBLIC_KEY`; no PEM file is tracked. The public key must
be an RSA key of at least 2048 bits. Gateway accepts RS256 only and applies the same decoder and
claim validation to HTTP bearer authentication and STOMP `CONNECT`/`STOMP` authentication.

JWT requirements are:

- `exp` is required and must be in the future, with zero clock skew.
- `sub` is required and must be a lowercase canonical UUID.
- `roles` is optional. When present, it must be an array of at most 16 unique values matching
  `[A-Z][A-Z0-9_]{0,31}`.
- Issuer, audience, and revocation validation are not part of the 1.0 contract.

At the outermost boundary, gateway removes client-supplied `X-User-Id`, `X-User-Roles`,
`X-Internal-Service`, and `X-Internal-Api-Key` headers without relying on header case. It also
removes the user's `Authorization` header before every downstream request. Verified identity
headers are generated inside gateway only.

The security policy permits error dispatches so a failure on an anonymous public downstream route
is not transformed into an unrelated 401 response.

### HTTP routes

Gateway exposes only the following downstream operations:

| External route | Downstream route | Authentication |
|---|---|---|
| `POST /api/v1/bets` | `POST /internal/v1/bets` | Bearer JWT |
| `GET /api/v1/bets` | `GET /internal/v1/bets` with forced `userId` | Bearer JWT |
| `GET /api/v1/bets/{betId}` | Same suffix under `/internal/v1/bets` | Bearer JWT |
| `GET /api/v1/wallet/balance` | `/internal/v1/wallet/accounts/{sub}/balance` | Bearer JWT |
| `GET /api/v1/events` | Same public route | Anonymous |
| `GET /api/v1/events/{eventId}` | Same public route | Anonymous |
| `GET /api/v1/odds/{eventId}/{marketId}/{selectionId}` | Same public route | Anonymous |

For authenticated betting requests, gateway injects the canonical JWT subject as `X-User-Id`
and the validated roles as `X-User-Roles`. The collection GET always replaces any user-supplied
actor query with the authenticated subject.

The wallet balance route additionally injects exactly:

```text
X-Internal-Service: gateway
X-Internal-Api-Key: ${GATEWAY_WALLET_API_KEY}
```

`GATEWAY_BETTING_API_KEY` and `GATEWAY_WALLET_API_KEY` are required for a servlet runtime and must
each contain at least 32 characters. They must be distinct. Each credential is injected only on
its matching downstream route and neither is forwarded to odds.

Gateway preserves request bodies, `Idempotency-Key`, a valid `traceparent`, and applicable
downstream status, body, content type, `Location`, and `Retry-After` values. Default downstream
timeouts are 500 ms for connection establishment and 3 seconds for reads; both are
environment-configurable. Connection and I/O failures return a shared-shape 502
`GATEWAY_BAD_GATEWAY` ProblemDetail. Read timeouts return 504 `GATEWAY_TIMEOUT`.

There are no public gateway routes for risk, settlement, admin, wallet mutations, or arbitrary
downstream paths.

### Distributed rate limiting

Default limits are 120 requests per minute for an authenticated user and 60 requests per minute
for an anonymous client IP. Redis keys are isolated under:

```text
gateway:ratelimit:user:*
gateway:ratelimit:ip:*
```

The trusted-proxy CIDR list is empty by default. Direct clients cannot rotate
`X-Forwarded-For` to evade a bucket. Gateway considers that header only when the socket peer is in
an explicitly configured trusted CIDR, then selects the rightmost untrusted hop. Invalid capacity,
refill, or CIDR configuration fails startup.

Redis connection and command bounds are 300 ms and 500 ms. Gateway fails open during Redis
outages and resumes distributed limiting after recovery. Concurrent cold-start failures use a
single in-flight connection attempt so waiters are not serialized behind repeated connection
timeouts.

A normal rejection returns a 429 ProblemDetail with `Retry-After` and
`X-RateLimit-Remaining: 0`. Fail-open requests increment the
`gateway.ratelimit.fail.open` counter, exposed by Prometheus as
`gateway_ratelimit_fail_open_total`.

### WebSocket and STOMP

Handshake endpoints are:

- `/ws/v1/odds`
- `/ws/v1/bets`

The only public subscription is `/topic/odds/{canonicalEventId}`. The only private subscription
is `/user/queue/bets`, and it requires an authenticated JWT principal. Client commands are limited
to `CONNECT`/`STOMP`, `SUBSCRIBE`, `UNSUBSCRIBE`, and `DISCONNECT`. Client `SEND`, forged
server-side `MESSAGE`, acknowledgement, transaction, and other server-only commands are rejected.

Transport defaults include an allowed-origin list, a 64 KiB message limit, a 512 KiB send buffer,
and a 10-second send-time limit.

For each authenticated session, gateway schedules the exact JWT expiry and closes the underlying
WebSocket with code 1008 at expiration. Early disconnect cancels that task. Anonymous odds
sessions do not receive an expiry task. A client must reconnect with a fresh token after expiry.

### Kafka inputs and client projections

Gateway consumes raw `byte[]` keys and values from these topics:

| Input topic | Shared record | Consumer group | Required key |
|---|---|---|---|
| `odds.changed` | `OddsChanged` | `gateway-odds` | `eventId` |
| `bet.settled.v1` | `BetSettled` | `gateway-bets` | `eventId` |
| `bet.voided.v1` | `BetVoided` | `gateway-bets` | `eventId` |
| `bet.resolution.revised.v1` | `BetResolutionRevised` | `gateway-bets` | `betId` |

Keys must be strict UTF-8 canonical UUIDs and must equal the corresponding record field. Avro
records must decode without trailing bytes. A resolution revision must have a revision number of
at least 1, consistent payout currencies, and a source-result timestamp no later than its revision
timestamp.

Odds updates are sent to `/topic/odds/{eventId}`. Settlement, void, and resolution-revision
updates are sent only to the owning user's `/user/queue/bets` destination.

The public `BetStatusUpdate` shape is:

```text
betId, userId, eventId, status, result, amount, reason,
revisionId, revisionNumber, updatedAt
```

Projection rules are:

- `BetSettled`: `revisionId=null`, `revisionNumber=0`
- `BetVoided`: `revisionId=null`, `revisionNumber=null`
- `BetResolutionRevised`: actual revision ID and number, `newResult`, `newPayout`, and `revisedAt`

`BetVoided.reason=MARKET_VOID` is a permanent contract failure and follows the exact-partition
`bet.voided.v1.DLT` path. A market-result void is represented by `BetSettled`; its client
projection has `status=SETTLED` and `result=VOID`.

Gateway intentionally stores no revision state. Clients must retain the maximum revision number
seen for each bet and ignore duplicates or a late logical revision 0 after a correction.

### Dead-letter handling

The four quarantine topics are:

- `odds.changed.DLT`
- `bet.settled.v1.DLT`
- `bet.voided.v1.DLT`
- `bet.resolution.revised.v1.DLT`

Permanent decode and contract failures go directly to the corresponding DLT. Transient delivery
failures are retried twice at one-second intervals, for three total delivery attempts. Consumers
use record acknowledgement with auto-commit disabled.

The DLT producer uses raw byte-array serializers, `acks=all`, and Kafka idempotence. Its configured
bounds are:

| Setting | Value |
|---|---:|
| `max.block.ms` | 5000 ms |
| `request.timeout.ms` | 5000 ms |
| `delivery.timeout.ms` | 10000 ms |
| Recoverer send-result wait | 11 seconds |
| Recoverer timeout buffer | 1 second |

DLT publication preserves the original partition and raw key, value, and application headers. It
adds the original topic, partition, offset, consumer group, timestamp, and exception evidence.
Gateway deliberately disables the recoverer's partition lookup because Spring Kafka otherwise
falls back to producer-selected partitioning when the requested DLT partition does not exist.
There is no separate partition-info preflight contract. An exact-partition send failure propagates
and leaves the source offset uncommitted.

Orchestration must therefore create every DLT with at least the same partition count as its input
topic and a retention period of at least seven days. Topic auto-creation must remain disabled.

Gateway has no automatic DLT replay consumer. Manual replay must happen only after the cause is
corrected. The operator removes the explicit Kafka DLT, original-record, exception, delivery, and
deserializer metadata, preserves application headers, and republishes the exact raw key and value
to the original topic at the original DLT partition tail. The gateway consumer-group offset is not
reset.

### Operations

Actuator exposes only `health`, `info`, and `prometheus`. Liveness and readiness groups contain
only their application-state contributors, and health details and components are never exposed.
Redis, Kafka, or a downstream failure therefore does not remove a healthy gateway process from
service; the affected request path applies its own fail-open or 502/504 behavior.

Logs are newline-delimited JSON on stdout. Their stable fields include timestamp, level, logger,
message, service, and trace/span identifiers when present. A sanitized `stack_trace` can be
included for exceptions. Authorization, internal API key, password, token, and matching message or
stack values are redacted.

## Verification baseline and final gate

The Wave 1 baseline `08afce620b1feab8c805409cc412423df6645c70` passed:

- Temurin Java 17 and Maven 3.9.11 `clean verify`
- 122 tests, with zero failures, errors, or skips
- Spotless and Checkstyle
- JWT, header-isolation, exact-route, downstream relay, 502, and 504 integration tests
- Redis 7 user/IP, trusted-proxy, fail-open, recovery, and cold-start concurrency tests
- STOMP authentication, destination policy, token-expiry close, reconnect, and anonymous-session
  tests
- Embedded Kafka odds, settled, voided, and revision fan-out tests
- Immediate DLT, transient retry, recovery failure, exact partition, offset retention, and manual
  replay tests
- Concurrent HTTP identity/header-isolation and public/private subscriber-isolation gates
- CI workflow syntax and semantic checks
- Documentation link, command, and contract inventory checks
- Boot JAR `gateway-1.0.0.jar`
- Java class-file major version 61
- Exactly one bundled `shared-protocol-1.0.0` JAR containing the expected shared record classes
- Direct gateway bytecode references to `OddsChanged`, `BetSettled`, `BetVoided`, and
  `BetResolutionRevised`

The Wave 2 final tip passed its static-history audit and affected authentication, isolation, and
void-contract tests. Its sole final Java 17 `clean verify` ran 127 tests with zero failures,
errors, or skips and passed embedded-Kafka integration, packaging, Spotless, and Checkstyle.

Validation should remain proportional to a future change: run the directly affected tests during
development, then run one final Java 17 `clean verify` and any relevant external integration smoke
before release. Rechecking every historical SHA is not a gateway maintenance requirement.

## Wave 2 integration obligations

### Betting service

Betting implements the exact internal endpoints listed in the HTTP table and trusts only
gateway-generated canonical actor headers. Gateway calls it with exactly
`X-Internal-Service: gateway` and the credential shared between `GATEWAY_BETTING_API_KEY` and
`BETTING_GATEWAY_API_KEY`. That secret is route-specific and must not equal the wallet credential.

### Wallet service

Wallet must accept the balance read from caller `gateway`. Orchestration must inject one matching
32-character-or-longer secret into wallet's gateway caller configuration and gateway's
`GATEWAY_WALLET_API_KEY`. The secret must remain outside tracked files. Gateway must never receive
permission for wallet mutations.

### Odds feed service

Odds feed must keep publishing `OddsChanged` on `odds.changed` with `eventId` as the key. Its public
event and odds routes must match the exact paths exposed by gateway. Gateway does not consume
market status, lifecycle, or match-result topics.

### Settlement service

Settlement must publish the base settled and voided events with the existing topic and key
contracts. After a wallet adjustment and durable revision/outbox commit, it must publish
`BetResolutionRevised` on `bet.resolution.revised.v1` with `betId` as the key and a stable revision
ID/number. Gateway's consumer is already in place.

### Orchestration

Orchestration must:

- enforce exactly one gateway replica for 1.0;
- provision the four input topics and four DLTs with matching partition counts;
- give every DLT at least seven days of retention and disable topic auto-creation;
- inject the JWT public key, wallet API key, downstream URIs, allowed origins, Redis settings, and
  Kafka settings without tracking credentials;
- inject one distinct matching Betting credential as `GATEWAY_BETTING_API_KEY` and
  `BETTING_GATEWAY_API_KEY`;
- use the existing `gateway-odds` and `gateway-bets` consumer groups;
- route health, info, Prometheus, HTTP, and WebSocket traffic according to the documented exposure;
- install `shared-protocol:1.0.0` before building gateway in an isolated workspace.

If gateway becomes multi-replica in a later release, the Kafka-to-local-STOMP fan-out strategy and
consumer-group design must change first. Simply increasing the replica count will cause clients on
one replica to miss events consumed by another.

### Client applications

Clients must use revision numbers to reconcile base settlement and correction messages. For each
bet, keep the highest revision observed, discard duplicates and lower revisions, and refresh state
through the betting read API when a gap or reconnect requires reconciliation.

## Deferred and intentional limits

The following are not gateway 1.0 guarantees:

- multi-replica or high-availability WebSocket fan-out;
- exactly-once Kafka or DLT delivery;
- automatic DLT replay;
- gateway-side durable event or revision storage;
- JWT issuer, audience, or revocation validation;
- downstream dependency health as a readiness condition;
- direct gateway integration with risk, settlement operations, admin, or wallet mutations.

A crash after a DLT broker acknowledgement but before the source offset commit can duplicate a DLT
record. Operators should identify quarantine records by original topic, partition, and offset, and
consumers and clients must remain idempotent.
