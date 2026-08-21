# Internal API and event contracts

All internal requests use JSON, with identifiers represented as UUID strings. Kafka identifiers
must be lowercase canonical UUID strings. Monetary amounts are positive integer `Money.amount`
values paired with `KRW` or `USD`; the service does not perform decimal or exchange-rate
conversion.

## Authentication and ownership

Every internal request supplies both headers:

```text
X-Internal-Service: <caller>
X-Internal-Api-Key: <caller-specific secret>
```

| Caller | Owned operations |
| --- | --- |
| `betting-service` | Reservation admission, commit, and release |
| `admin-api` | Read, set, and clear user limit overrides |
| `platform` | Non-reserving risk check and any additionally exposed actuator endpoint |

Secrets come from `INTERNAL_BETTING_SERVICE_API_KEY`, `INTERNAL_ADMIN_API_KEY`, and
`INTERNAL_PLATFORM_API_KEY`. Startup fails when a secret is missing, blank, shorter than 32
characters, or equal to another caller's secret. The application keeps SHA-256 digests and uses a
constant-time comparison.

Missing or invalid credentials return `401`. A valid caller using another caller's route returns
`403`. Health endpoints and Prometheus are anonymous.

## Endpoint inventory

| Method and path | Caller | Success |
| --- | --- | --- |
| `POST /internal/v1/risk/reservations` | `betting-service` | `200` with an approved lease or retained rejection |
| `PUT /internal/v1/risk/reservations/{betId}/commit` | `betting-service` | `204` for applied or matching replay |
| `DELETE /internal/v1/risk/reservations/{betId}` | `betting-service` | `204` for applied, matching replay, missing, or tombstoned state |
| `GET /internal/v1/risk/limits/{userId}` | `admin-api` | `200` with every effective limit |
| `PATCH /internal/v1/risk/limits/{userId}` | `admin-api` | `204` after replacing one override |
| `DELETE /internal/v1/risk/limits/{userId}/{type}` | `admin-api` | `204` after clearing one override |
| `POST /internal/v1/risk/check` | `platform` | `200` with a point-in-time diagnostic decision |

Request validation failures use the shared `ProblemDetail` JSON shape with
`errorCode=VALIDATION_FAILED`. Conflicting reuse of a reservation `betId` uses `DUPLICATE_BET` and
`409`. Commit of missing or tombstoned state uses `RISK_RESERVATION_NOT_FOUND` and `404`; release of
an already committed reservation uses `RISK_RESERVATION_COMMITTED` and `409`. Unhandled failures
are rendered as an opaque `INTERNAL_ERROR` without exposing the exception.

## Candidate and reservation payloads

Reservation and diagnostic check use the same request body:

```json
{
  "userId": "10000000-0000-4000-8000-000000000001",
  "betId": "20000000-0000-4000-8000-000000000001",
  "stake": {"amount": 1000, "currency": "KRW"},
  "selectionIds": ["30000000-0000-4000-8000-000000000001"]
}
```

There must be one to 15 unique selection IDs. `stake.amount` must be in
`1..9007199254740991`.

An approved reservation returns its expiry and opaque token:

```json
{
  "approved": true,
  "replayed": false,
  "patterns": [],
  "reservationState": "RESERVED",
  "expiresAt": "2026-08-21T10:02:00Z",
  "reservationToken": "0000000000000000000000000000000000000000000000000000000000000000"
}
```

A policy rejection is also a `200` application result. It has `approved=false`, a
`rejectionReason`, any pattern flags, and no reservation token. Repeating the same request returns
the retained result with `replayed=true` while its lifecycle exists.

Commit requires the returned token:

```http
PUT /internal/v1/risk/reservations/{betId}/commit
X-Risk-Reservation-Token: <reservationToken>
```

The diagnostic response contains `approved`, optional `rejectionReason`, optional limit details,
and `patterns`. It does not create a lease and must not replace reservation admission.

## Limit administration

`GET /internal/v1/risk/limits/{userId}` returns seven entries: daily, weekly, and monthly stake
limits for both currencies, plus the currency-neutral selection limit. Each entry identifies its
`POLICY` or `OVERRIDE` source.

Set one monetary override:

```json
{"type":"STAKE_DAILY","currency":"KRW","value":750000}
```

Set the selection override by omitting currency:

```json
{"type":"SELECTIONS_PER_MINUTE","value":20}
```

Values may be zero and must not exceed `9007199254740991`. Clearing a monetary override requires
the matching query parameter, for example:

```http
DELETE /internal/v1/risk/limits/{userId}/STAKE_DAILY?currency=KRW
```

Clearing `SELECTIONS_PER_MINUTE` requires currency to be omitted.

## Accepted-bet input

| Property | Contract |
| --- | --- |
| Topic | `bet.placed.v1` by default |
| Kafka key | Canonical `userId` string |
| Value | Plain Avro binary `com.sportsbook.protocol.event.BetPlacedRequested` with no registry framing |
| Consumer group | `risk.bet-placed-consumer` by default |

The consumer validates canonical IDs, a shared-protocol idempotency key of at most 128 printable
ASCII characters, selection identity and odds, positive exactly representable exposure, and a
selection count from one to 15. The key must not be blank and selection IDs must be unique.

- `SINGLE` has exactly one selection and no system fields.
- `MULTIPLE` has at least two selections and no system fields; exposure is the event stake.
- `SYSTEM` supplies matching `systemTotalSelections` and a valid `systemMinWins`; exposure is the
  event stake multiplied by `C(totalSelections, systemMinWins)`.

The consumer observation time is used for rolling windows. `requestedAt` is validated and carried
through decoding but is not used as the Redis score.

Permanent failures go to `bet.placed.v1.DLT` with the original key and payload and an ASCII
`risk-dlt-reason` header. Values are `MALFORMED_EVENT`, `KEY_MISMATCH`, `FINGERPRINT_MISMATCH`, or
`TERMINAL_RESERVATION`.

## Risk signal output

| Topic | Kafka key | Plain Avro value | Delivery role |
| --- | --- | --- | --- |
| `risk.limit.violated` | `userId` | `RiskLimitViolated` | Best-effort diagnostic signal for daily stake or per-minute selection violations |
| `risk.pattern.suspected` | `userId` | `RiskPatternSuspected` | Best-effort diagnostic signal for rapid betting, sudden stake increase, or repeated selection |

Signals are emitted by `POST /internal/v1/risk/check`; reservation admission does not publish its
decision. Single-bet, weekly, and monthly diagnostic rejections have no corresponding shared
risk-limit signal in this service. Signal topics and input/DLT topics are configurable under
`risk.topics`.

See [Runtime and consistency boundaries](runtime-and-consistency-boundaries.md) for acknowledgment
and retry behavior.
