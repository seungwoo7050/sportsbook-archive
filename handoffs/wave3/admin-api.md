# Admin API — Wave 3 Handoff

## Purpose

This report fixes the Admin API 1.0 contract consumed by orchestration. It records the operator
security boundary, exact downstream delegation, fail-closed audit lifecycle, release identity, and
the integration obligations that later work must preserve.

## Final state

- Released local branch: `admin-api`
- Final tip: `2fb55910475b31084e6489bf01c34cc970c96874`
- Release commit: `7d09112a81573d49909a36261f425fec08b82028`
- Release artifact: `com.sportsbook:admin-api:1.0.0`
- Protocol dependency: `com.sportsbook:shared-protocol:1.0.0` from
  `f9de6bc1e533761ab4bb1454d8d4ab8175cdf001`
- Java target: 17, class-file major version 61
- Default HTTP port: 8090
- Flyway migrations: V1 and V2
- History: 239 commits, one root, 238 single-parent commits, no merges

The local `admin-api` branch points to the final tip. No tag, backup ref, remote push, or tracked
verification evidence was created.

## History quality

The fixed history has these properties:

- The root commit contains only the ownership README.
- Commit subjects are conventional and bodies are empty.
- Development production and test changes are separate adjacent commits.
- A development commit changes at most two production files.
- Handwritten development changes stay within the 100-line review gate.
- Maven baseline, wrapper, single migrations, the single Avro schema, and final README are the
  reviewed size exceptions.
- There are no merge, fixup, squash, diary, changelog, provenance, reconstruction, or tracked load
  result artifacts.
- The penultimate commit releases 1.0.0 by changing only `pom.xml`.
- The final commit is the single README-only project documentation commit.

The repository-owned history guard traverses the full chain. Its long-history self-test places a
production/test pair before 240 later commits so a partial traversal or SIGPIPE cannot appear as a
successful policy result.

## Authentication and request identity

Every `/admin/**` request requires both an allowed source address and a bearer JWT. JWT validation
requires:

- RS256 with an SPKI RSA public key of at least 2048 bits;
- a required unexpired `exp` with zero clock skew and a valid `nbf` when present;
- a nonblank `sub` of at most 128 code points without surrounding whitespace or control
  characters;
- one exact string `role`: `ADMIN`, `TRADER`, `CS`, or `READONLY`; and
- an exact issuer match when `ADMIN_JWT_ISSUER` is configured.

`X-Forwarded-For` is ignored unless the direct peer belongs to
`ADMIN_TRUSTED_PROXY_CIDRS`. Malformed or ambiguous proxy chains fail closed. The default
`ADMIN_IP_ALLOWLIST` permits loopback only.

Every authenticated `POST`, `PATCH`, or `DELETE` under `/admin/v1/` receives one UUIDv7 action ID.
The response carries the same identity in `X-Admin-Action-Id`, including request and authorization
failures reached after authentication.

## Public API and roles

| Method and path | Roles | Success contract |
| --- | --- | --- |
| `POST /admin/v1/wallet/{userId}/refund` | `ADMIN`, `CS` | `200` proof; exactly one `Idempotency-Key` |
| `GET /admin/v1/risk/users/{userId}/limits` | all four roles | typed complete limit snapshot |
| `PATCH /admin/v1/risk/users/{userId}/limits` | `ADMIN`, `TRADER` | empty `204`; body `{type,currency,value}` |
| `DELETE /admin/v1/risk/users/{userId}/limits/{type}?currency=` | `ADMIN`, `TRADER` | empty `204` |
| `POST /admin/v1/events/{eventId}/markets/{marketId}/suspend` | `ADMIN`, `TRADER` | empty `202`; idempotency key and reason required |
| `POST /admin/v1/events/{eventId}/markets/{marketId}/close` | `ADMIN`, `TRADER` | empty `202`; idempotency key and reason required |
| `POST /admin/v1/events/{eventId}/markets/{marketId}/reopen` | `ADMIN`, `TRADER` | empty `202`; idempotency key and reason required |
| `GET /admin/v1/settlements/result-candidates/{candidateId}` | all four roles | typed candidate evidence |
| `GET /admin/v1/settlements/revisions/{revisionId}` | all four roles | typed revision and Wallet evidence |
| `POST /admin/v1/settlements/result-candidates/{candidateId}/approve` | `ADMIN`, `TRADER` | verified receipt with `200` |
| `POST /admin/v1/settlements/result-candidates/{candidateId}/reject` | `ADMIN`, `TRADER` | verified receipt with `200` |
| `POST /admin/v1/settlements/revisions/{revisionId}/retry` | `ADMIN`, `TRADER` | verified queued receipt with `202` |
| `GET /admin/v1/audit-logs` | all four roles | filtered, newest-first offset page |
| `GET /admin/v1/audit-logs/{actionId}` | all four roles | exact action or `404` |

Refund bodies are `{amount,currency,reason}` with positive amount and a trimmed 1–256 character
reason. Odds and rejection reasons are also limited to 1–256 characters. Settlement mutations
require a UUID `Idempotency-Key`; arbitrary nonempty bounded keys remain valid for Wallet and Odds.

Risk limit types are `STAKE_DAILY`, `STAKE_WEEKLY`, `STAKE_MONTHLY`, and
`SELECTIONS_PER_MINUTE`. Stake limits require a `KRW` or `USD` currency; the selection-rate limit
forbids one. Values are nonnegative and bounded by JavaScript's maximum safe integer.

There are no Admin settlement void or replay endpoints.

## Downstream credentials and delegation

Admin requires four nonblank keys of at least 32 characters. They must be distinct and each is
injected into only its dedicated `RestClient`.

| Owner | Admin setting | Base URL setting | Authentication sent |
| --- | --- | --- | --- |
| Wallet | `ADMIN_WALLET_API_KEY` | `ADMIN_WALLET_BASE_URL` | `X-Internal-Service: admin-api`, `X-Internal-Api-Key` |
| Risk | `ADMIN_RISK_API_KEY` | `ADMIN_RISK_BASE_URL` | `X-Internal-Service: admin-api`, `X-Internal-Api-Key` |
| Odds Feed | `ADMIN_ODDS_FEED_API_KEY` | `ADMIN_ODDS_FEED_BASE_URL` | `X-Internal-Service: admin-api`, `X-Internal-Api-Key` |
| Settlement | `ADMIN_SETTLEMENT_API_KEY` | `ADMIN_SETTLEMENT_BASE_URL` | `X-Service-Name: admin-api`, `X-API-Key` |

Wallet refund delegates to `POST /internal/v1/wallet/transactions/credit`. The caller's
`Idempotency-Key` is forwarded unchanged. Admin creates an internal body with the path user,
`Money`, `source=HOUSE_POOL`, and `reason=REFUND`. HTTP 200 is accepted only when the returned
operation group, user, amount, currency, reason, and timestamp form complete matching evidence.

Risk delegates to `/internal/v1/risk/limits/{userId}` with exact GET, PATCH, and DELETE shapes.
Odds delegates to the matching `/internal/v1/events/{eventId}/markets/{marketId}/{action}` path and
forwards both the idempotency key and `X-Admin-Action-Id`.

Settlement delegates to:

```text
GET  /internal/admin/result-candidates/{candidateId}
GET  /internal/admin/revisions/{revisionId}
POST /internal/admin/result-candidates/{candidateId}/approve
POST /internal/admin/result-candidates/{candidateId}/reject
POST /internal/admin/revisions/{revisionId}/retry
```

Candidate receipts, revision evidence, Wallet proof, idempotency identity, attempt bounds, and
status/body combinations are verified before a downstream success is trusted.

Downstream 4xx responses are relayed with their body and content type. A timeout becomes a 504
`GATEWAY_TIMEOUT`. Other transport or downstream 5xx failures become 502 `BAD_GATEWAY`. An invalid
success response becomes 502 `DOWNSTREAM_CONTRACT_VIOLATION`. These unknown outcomes are never
converted into a domain rejection.

## Fail-closed audit lifecycle

PostgreSQL `audit_log` is authoritative.

1. Before an audited downstream call, Admin inserts a `STARTED` row in a separate transaction.
2. STARTED failure prevents the downstream call and returns 503 `AUDIT_UNAVAILABLE` with the same
   action ID.
3. Completion uses one guarded STARTED-to-terminal update.
4. Finalization failure returns 503 `AUDIT_FINALIZATION_FAILED`; any earlier application failure is
   retained as suppressed context.
5. A terminal row is copied best effort to Kafka `admin.action` as local
   `AdminActionRecorded` Avro, keyed by actor ID.

Successful 2xx results become `SUCCESS` with the exact status. Explicit 4xx and authorization
denials become `FAILED`. Timeouts, 5xx, malformed success bodies, and unexpected failures become
`UNKNOWN`.

The stale scanner defaults to a 30-second interval and a five-minute age boundary. It claims a
bounded batch with `FOR UPDATE SKIP LOCKED`, performs one guarded transition to `UNKNOWN`, and
publishes the same terminal representation. Kafka publication is best effort and cannot replace or
roll back the authoritative database result.

Audit search accepts optional ISO-8601 `from`, `to`, and `actor` filters. Page size defaults to 20
and is capped at 200. Exact action lookup is by UUIDv7 action ID.

## Persistence, health, and telemetry

Admin owns only `audit_log`:

- V1 creates the original audit table and indexes and has a locked checksum.
- V2 adds the lifecycle state, terminal fields and constraints, and stale-row index.
- Both clean V1-to-V2 creation and legacy V1 upgrade preserve their required evidence.

Hibernate uses `ddl-auto=validate`. Readiness contains only `readinessState` and PostgreSQL `db`;
Kafka and downstream services are intentionally evaluated per operation. Liveness, readiness, and
Prometheus endpoints are unauthenticated, while component details remain hidden.

Structured logs redact bearer tokens, idempotency keys, API keys, internal API keys, password-like
values, and matching stack-trace material. Audit metrics include stale claims, stale scan failures,
and publication failures. The k6 fixture does not write persistent results.

## Verification completed

At the final docs tip, the exact shared tip was installed into
`/private/tmp/sportsbook-wave3-m2`, then one Java 17 `clean verify` ran against Admin API.

The release gate passed:

- 203 tests, zero failures, errors, or skips;
- PostgreSQL 16 clean migration, legacy V1 upgrade, repository, race, HTTP, and readiness paths;
- real Kafka publication plus failure handling and Avro fingerprint/round-trip checks;
- WireMock downstream correlation, exact request contracts, and cross-client secret isolation;
- JWT, role, trusted-proxy, CIDR-boundary, request identity, method-security, and audit ordering;
- history, archive workflow, load-fixture, structured-log redaction, Spotless, and Checkstyle gates.

Release artifact evidence:

| Artifact | Size | SHA-256 |
| --- | ---: | --- |
| `admin-api-1.0.0.jar` | 79,403,488 bytes | `625daa40342b16e02d64dd4a41503330bd64c06a84ede91acee476446d7c30cb` |

The executable JAR contains exactly `BOOT-INF/lib/shared-protocol-1.0.0.jar`. A compiled application
class reports major version 61.

## Wave 4 obligations

Orchestration must:

- lock Admin to final tip `2fb55910475b31084e6489bf01c34cc970c96874`;
- build and stage exactly `target/admin-api-1.0.0.jar` after installing the fixed shared artifact;
- supply canonical `ADMIN_DB_URL`, `ADMIN_DB_USER`, `ADMIN_DB_PASSWORD`, and
  `ADMIN_KAFKA_BOOTSTRAP` values;
- pass the JWT public key as inline PEM and fix an issuer for deterministic E2E tokens;
- map each Admin caller key to the matching callee key:
  - Wallet `WALLET_ADMIN_API_KEY`;
  - Risk `INTERNAL_ADMIN_API_KEY`;
  - Odds Feed `ADMIN_API_INTERNAL_KEY`; and
  - Settlement `SETTLEMENT_ADMIN_API_KEY`;
- keep those four logical secrets distinct from every other service and platform credential;
- create `admin.action` explicitly with three partitions while Kafka auto-create is disabled;
- apply Admin V1 and V2 before accepting traffic;
- treat PostgreSQL readiness as the Admin startup gate; and
- invoke Admin E2E requests from an allowed address or explicitly configure the E2E CIDR, without
  weakening the production allowlist contract.

Admin starts after Wallet, Risk, Odds Feed, Settlement, their required migrations, and the Kafka
topic gate. The generated action ID must correlate the public response, downstream Odds request
where applicable, authoritative audit row, and best-effort `admin.action` record.
