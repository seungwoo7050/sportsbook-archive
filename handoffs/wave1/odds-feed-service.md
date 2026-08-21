# Odds Feed Service - Wave 1 Handoff

## Purpose

This report records the current `odds-feed-service` state for Wave 2 work. It covers only the
odds feed service, its published contracts, its downstream obligations, and the release work that
still must be completed.

## Current status

- Candidate branch: `tmp-odds-feed-service-1-0`
- Candidate tip: `574e83d2862f086ae07ff56fd95a8336f78a72da`
- Candidate worktree: `/private/tmp/odds-feed-service-1-0`
- Candidate history: 229 commits, one root, 228 single-parent commits, no merges
- Release artifact: `com.sportsbook:odds-feed-service:1.0.0`
- Protocol dependency: `com.sportsbook:shared-protocol:1.0.0`
- Java target: 17

The local `odds-feed-service` branch still points to
`88e55281114c5ed13e43a0f2fbf37bca1df99d5a`. This is intentional: the candidate must not replace
that branch until the full HTTP performance gate and the final root-to-tip replay pass. No tag,
backup branch, or remote push was created.

## History quality

The candidate history has the following verified properties:

- The root commit contains only the ownership README.
- Every later commit has exactly one parent.
- Commit subjects follow `type(scope): subject`; commit bodies are empty.
- Production and test changes are not mixed in the same development commit.
- No development commit changes more than two production files.
- No handwritten development commit reaches the 100-line review boundary.
- The Maven wrapper and the final project documentation are the only bulk exceptions.
- The final development commit releases `1.0.0`; the final commit is the single bulk documentation
  commit.
- Reachable history and the final tree contain no development diary, changelog, provenance note,
  old project version, reconstruction wording, or tracked load result.

## Delivered runtime behavior

### Provider and read model

- The default mock provider emits deterministic event, market, odds, lifecycle, and result data.
- The real provider path has bounded rate and quota handling, stable identifiers, normalized DTOs,
  and polling diff support.
- Events are exposed through cursor-based reads.
- Odds are exposed as a single event/market/selection projection.
- Redis remains the betting-facing projection store.

Public HTTP routes are:

- `GET /api/v1/events`
- `GET /api/v1/events/{eventId}`
- `GET /api/v1/odds/{eventId}/{marketId}/{selectionId}`

### Kafka contracts

The service continues to publish raw Avro payloads on the existing topics:

| Topic | Contract | Kafka key |
|---|---|---|
| `odds.changed` | `OddsChanged` | `eventId` |
| `market.status.changed` | `MarketStatusChanged` | `eventId` |
| `event.lifecycle` | `EventLifecycle` | `eventId` |
| `match.result` | `MatchResult` | `eventId` |

Kafka producer acknowledgement is required before an OPEN projection becomes externally usable.
Delivery remains at-least-once, so consumers must remain idempotent.

### Redis state and precedence

The effective market state uses this strict precedence:

```text
event terminal or provider terminal CLOSED
  > operator CLOSED/SUSPENDED
  > feed-availability SUSPENDED
  > provider OPEN/SUSPENDED
```

Important keys are:

| Key | Meaning |
|---|---|
| `odds:{eventId}:{marketId}:{selectionId}` | Current decimal odds |
| `event:{eventId}` | Current event projection |
| `market:{eventId}:{marketId}` | Effective betting-facing market state |
| `market:provider:{eventId}:{marketId}` | Provider market state |
| `market:override:{eventId}:{marketId}` | Operator override |
| `event:markets:{eventId}` | Durable event-to-market registry |
| `event:terminal:{eventId}` | Non-expiring terminal event latch |
| `market:terminal:{eventId}:{marketId}` | Non-expiring provider terminal latch |
| `market:feed-hold:{eventId}:{marketId}` | Broker-outage fail-close hold |

The durable registry lets a restarted process close all previously known markets when a terminal
event arrives. Terminal latches prevent late provider OPEN updates and operator reopen commands
from reviving a completed market.

Restrictive transitions are persisted before Kafka publication. OPEN is published only after an
atomic Redis preview confirms that no terminal latch, operator override, or feed hold makes it
unsafe. A broker outage creates a feed hold; broker recovery alone does not clear it. The hold is
removed only after the current odds snapshot receives a Kafka acknowledgement.

### Critical delivery

Critical provider events use Redis Stream `oddsfeed:critical-events`. The queue supports unread
delivery, pending-entry reclaim, restart recovery, and an atomic Lua `XACK` plus `XDEL` cleanup
boundary. Existing consumer groups are recognized through the complete exception cause chain,
including a wrapped Redis `BUSYGROUP` response.

Readiness includes Redis and the critical-delivery health indicator. Kafka availability is probed
independently so recovery does not depend on a successful business publish.

### Operator API

The internal endpoint is:

```http
POST /internal/v1/events/{eventId}/markets/{marketId}/{suspend|close|reopen}
X-Internal-Service: admin-api
X-Internal-Api-Key: <secret>
Idempotency-Key: <stable key>
X-Admin-Action-Id: <UUID>
Content-Type: application/json

{"reason":"trimmed text between 1 and 256 characters"}
```

Required behavior:

- `ADMIN_API_INTERNAL_KEY` must be supplied from the environment and must contain at least 32
  characters. Missing or short configuration fails startup.
- Missing or invalid credentials return 401.
- A valid key with a caller other than `admin-api` returns 403.
- Accepted durable queue submission returns 202. It does not mean Kafka delivery has completed.
- An exact idempotency replay returns the same 202 result without creating another command.
- Reusing a key with a different fingerprint returns 409.
- Reopening a terminal event or market returns 409.
- Pending idempotency and sequence state does not expire.
- State for a fully completed latest sequence expires after seven days.
- Close and suspend fail closed in the same Redis Lua submission boundary.
- Reopen keeps the restrictive override until Kafka acknowledgement and predecessor checks pass;
  the final removal is compare-and-set.
- A command superseded by a newer sequence, invalidated by a terminal latch, or blocked by current
  effective precedence does not publish an unsafe OPEN event.

The request fingerprint is SHA-256 over length-prefixed canonical UTF-8 fields: version, caller,
action, lowercase canonical event UUID, lowercase canonical market UUID, requested state, and
trimmed reason. `X-Admin-Action-Id` and occurrence time are deliberately excluded so a retried
logical request remains stable.

### HTTP exposure

The service listens on port 8085. Anonymous access is limited to public event/odds GET routes,
`/actuator/health/**`, and `/actuator/prometheus`. Health details remain authorization-dependent.
`/actuator/info` and all unspecified routes are denied.

## Verification completed

The fixed candidate tip passed:

- Temurin Java 17 Docker `clean verify`
- 156 tests, with zero failures, errors, or skips
- Real Redis 7 cache precedence and terminal-latch tests
- Real Redis 7 critical Stream enqueue, ordering, reclaim, and cleanup tests
- Real Redis 7 operator idempotency, ordering, terminal-race, feed-hold, and recovery tests
- Embedded Kafka broker-acknowledged throughput gate above 50 events/second
- Spotless over 112 files
- Checkstyle with zero violations
- Compose configuration validation
- Shell syntax and ShellCheck
- Four k6 configuration inspections
- CI workflow syntax and semantic checks
- Documentation link and inventory checks
- Boot JAR name `odds-feed-service-1.0.0.jar`
- Exactly one bundled `shared-protocol-1.0.0` dependency
- Java class-file major version 61

A runtime smoke test also started the final JAR on Java 17 and port 8085 with Redis 7. With Kafka
intentionally absent, anonymous health returned 503 with no component details, Prometheus returned
200, and `/actuator/info` returned 403. Graceful shutdown completed.

## Release gates still pending

Do not treat the candidate as released and do not move the `odds-feed-service` branch yet.

### HTTP performance gate

The tracked gate uses a fresh Redis/Kafka stack per endpoint, a 60-second warm-up, and five
60-second measurements for events followed by the same sequence for odds. Every measurement must
satisfy:

- p99 below 50 ms
- HTTP error rate below 0.1%
- successful checks above 99.9%
- zero dropped iterations

The official configured request rate is currently 1,000 requests/second per endpoint. One events
measurement passed at p99 43.51 ms, 0% errors, 100% checks, and zero drops. A later measurement was
invalidated by heavy concurrent Docker work from the Wave 1 risk-service history replay; process
inspection confirmed that replay was consuming several CPU cores in the shared Docker VM. Further
runs under the same contention were stopped and are not release evidence.

Rerun the complete gate only when other Docker-intensive branch replays are idle. Use a new result
directory outside the repository. Do not commit its output.

```sh
cd /private/tmp/odds-feed-service-1-0
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH" \
RESULT_ROOT=/private/tmp/odds-http-gate-final \
MAVEN_REPO_LOCAL=/private/tmp/odds-release-m2/repository \
COMPOSE_PROJECT_NAME=odds-http-gate-final \
SERVER_PORT=8085 REDIS_PORT=6392 KAFKA_PORT=9096 \
REQUEST_RATE=1000 PREALLOCATED_VUS=200 MAX_VUS=500 \
./load-test/run-http-gate.sh
```

### Root-to-tip replay

The final 229-commit history still needs its complete Java 17 root-to-tip replay. The external
harness is:

```text
/private/tmp/odds-feed-history-replay/replay-odds-history.sh
```

Run it against the fixed candidate tip with `EXPECTED_COUNT=229`. The README-only root and
repository-default commit are structural checks; commit 3 uses system Maven; later buildable
commits use the wrapper. Unit-only commits may run at up to four-way parallelism, while
Testcontainers and Embedded Kafka commits must run sequentially.

### Final branch transition

After both gates pass:

1. Verify that local `odds-feed-service` still points to
   `88e55281114c5ed13e43a0f2fbf37bca1df99d5a`.
2. Move it to candidate tip `574e83d2862f086ae07ff56fd95a8336f78a72da` with a compare-and-swap
   ref update.
3. Remove only odds-related temporary worktrees and temporary refs.
4. Do not create a tag, backup ref, or remote push.

## Wave 2 integration obligations

### Admin API

The admin service must send all four stable headers required by the operator API:

- `X-Internal-Service: admin-api`
- `X-Internal-Api-Key`
- `Idempotency-Key`
- `X-Admin-Action-Id`

It must preserve the same `Idempotency-Key` across user retries and treat 202 as durable acceptance,
not completed Kafka delivery. Generating only a new action ID on each attempt is insufficient for
retry safety.

### Orchestration

Orchestration must inject the same 32-character-or-longer `ADMIN_API_INTERNAL_KEY` into odds and
admin without tracking the secret. It must provision the existing four Kafka topics and keep the
service on port 8085. It should use readiness rather than plain process startup as the business
availability signal.

### Settlement

Odds fail-close behavior narrows the lifecycle race but cannot eliminate it alone. Settlement must
persist a terminal lifecycle tombstone and apply terminal state during late `BetPlacedRequested`
catch-up. Otherwise a placement already in flight when cancellation is published can still arrive
after the initial cancellation fan-out and remain pending.

### Gateway and betting

Gateway public routes and its `odds.changed` consumer contract are unchanged. Betting continues to
read the Redis `market:*` effective status and `odds:*` decimal projection. Neither service should
infer provider state directly when the effective market key is restrictive.

## Deferred non-blocking scope

The following items remain outside the 1.0 release correction scope and must not be assumed to be
implemented:

- poison-record isolation
- per-stage delivery checkpoints
- multi-instance queue leases
- scheduler isolation
- complete backlog and freshness readiness
- full real-provider cadence and result support
- mock-generation isolation
- graceful queue drain

These are follow-up reliability and operational improvements. They do not relax the terminal,
operator, feed-hold, idempotency, or authentication guarantees documented above.
