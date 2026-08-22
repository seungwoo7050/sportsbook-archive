# Internal API and dependency contracts

## Trust boundary

All `/internal/**` and `/api/**` requests are denied by default unless they satisfy the Betting
Gateway boundary. A permitted request supplies exactly one value for both headers:

```text
X-Internal-Service: gateway
X-Internal-Api-Key: <BETTING_GATEWAY_API_KEY>
```

Missing, duplicated, unknown, or invalid credentials return `401`. Valid credentials from a
non-gateway caller or for an unlisted exact method-and-path route return `403`; an unsupported
method never falls through to a framework `405`. Matching uses the path within any configured
servlet context. API-key comparison is constant-time.

The authenticated user is derived only from exactly one canonical lowercase UUID in `X-User-Id`.
The request body has no `userId` field. Gateway must remove client-supplied identity and internal
authentication headers before adding trusted values.

## Place a bet

```http
POST /internal/v1/bets
X-Internal-Service: gateway
X-Internal-Api-Key: ...
X-User-Id: 0191f7ad-2d80-7c12-8fd2-761e82f0964a
Idempotency-Key: wager-20260822-0001
Content-Type: application/json
```

SINGLE example:

```json
{
  "slipType": {
    "type": "SINGLE",
    "minWins": null,
    "totalSelections": null
  },
  "selections": [
    {
      "eventId": "0191f7ad-52b4-7c11-929e-fb788940aa50",
      "marketId": "0191f7ad-6188-7f9a-a6da-c329afe3bca3",
      "selectionId": "0191f7ad-6c68-7080-a1d5-411782c45ace",
      "odds": 2.15
    }
  ],
  "stake": {
    "amount": 1000,
    "currency": "KRW"
  }
}
```

SYSTEM requests use `type=SYSTEM` and require both `minWins` and `totalSelections`. SINGLE and
MULTIPLE requests omit their SYSTEM shape by using null values. SYSTEM requires both fields and
non-SYSTEM requests reject either field when non-null. Selection count (at most 15), duplicate
selection IDs, repeated markets, stake limits, total odds, market state, and submitted-odds drift
are validated before external money movement.

Response semantics:

| Outcome | Status | Meaning |
| --- | ---: | --- |
| `ACCEPTED` | 201 | Risk and Wallet proofs are committed and the accepted outbox row exists |
| `PENDING` | 202 | A dependency result is ambiguous; reconciliation owns completion |
| Durable business rejection | Error status | Rejection is stable for exact idempotent replay |

Both 201 and 202 include:

```text
Location: /api/v1/bets/{betId}
```

The response contains `betId`, `betReference`, trusted `userId`, slip shape, status, unit stake,
maximum payout, ordered selections, optional rejection reason, and creation time. Before resolution,
the nullable `resolution` object is omitted. A terminal projection adds the current snapshot:

```json
{
  "resolution": {
    "settlementResult": "WON",
    "settledPayout": {"amount": 2000, "currency": "KRW"},
    "resolvedAt": "2026-08-22T00:00:00Z",
    "resolutionEventId": "0191f7ad-52b4-7c11-929e-fb788940aa50",
    "resolutionRevisionNumber": 0
  }
}
```

Whole-slip voids expose `voidReason` instead of settlement result and payout. Base projections use
revision number 0; corrected projections use number 1 or later and include
`resolutionRevisionId`. Null members are omitted. The API does not invent an unpersisted refund or
expose the internal payload hash.

## Query bets

```http
GET /internal/v1/bets?cursor=<betId>&limit=<n>
```

The actor comes from `X-User-Id`; a user cannot select another actor through a query parameter.
The response is:

```json
{
  "items": [],
  "nextCursor": null,
  "hasMore": false
}
```

`limit` defaults to the service query policy and is bounded. Cursor pagination is stable by bet
identity.

```http
GET /internal/v1/bets/{betId}
```

The route returns only a bet owned by the trusted actor. Missing or foreign bets use the same 404
boundary and do not disclose ownership.

## Problem responses

Business and validation failures use RFC 9457-compatible JSON with these stable fields:

```json
{
  "type": "https://sportsbook/errors/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "errorCode": "VALIDATION_FAILED",
  "detail": "...",
  "instance": "/internal/v1/bets"
}
```

Placement verdicts retain the shared protocol's `ErrorCode` and HTTP mapping. `BET_NOT_FOUND` is
the stable local query error. Internal secrets, reservation tokens, SQL text, request bodies, and
downstream exception bodies are never included.

## Risk Service calls

Betting uses a dedicated client with:

```text
X-Internal-Service: betting-service
X-Internal-Api-Key: <BETTING_RISK_API_KEY>
```

The risk client never receives the wallet key.

### Reserve

```http
POST /internal/v1/risk/reservations
```

The request identifies bet, user, full exposure, and selection IDs. Only HTTP `200` is success.
Its body must contain an explicit `approved` boolean. Missing or null approval is a malformed
dependency response; `false` is the durable risk-limit verdict. An approved response must contain
a valid `RESERVED` or `COMMITTED` state, expiry, and lowercase SHA-256 reservation token. The token
is opaque and must be reused unchanged. A `400` with
`errorCode=VALIDATION_FAILED` is a durable business rejection; authentication failures and other
unexpected 4xx responses remain deployment/dependency failures rather than being stored as a
customer verdict.

### Commit

```http
PUT /internal/v1/risk/reservations/{betId}/commit
X-Risk-Reservation-Token: <persisted token>
```

| Provider result | Betting interpretation |
| --- | --- |
| 204 | Committed |
| 404 | Definitive missing reservation; refund Wallet and reject |
| 409 | Definitive committed conflict; refund Wallet and reject |
| timeout, 5xx, circuit open | Ambiguous; keep `PENDING` and retry with the same proof |

### Release

```http
DELETE /internal/v1/risk/reservations/{betId}
```

Only `204`, including provider-side missing/replay behavior, completes release. A 409 committed
reservation is terminal conflict evidence; it does not become an availability retry. Every other
status, including 3xx and a different 2xx, is rejected.

## Wallet Service calls

Betting uses a separate client with:

```text
X-Internal-Service: betting-service
X-Internal-Api-Key: <BETTING_WALLET_API_KEY>
```

The wallet client never receives the risk key.

### Debit

```http
POST /internal/v1/wallet/transactions/debit
Idempotency-Key: <canonical betId>
```

The amount is full committed exposure. Only HTTP `200` is success, and the representation must
prove the exact non-null `operationGroupId`, trusted `userId`, amount, `reason=BET_DEBIT`, and
timestamp. Forward-compatible extra fields are tolerated, but a mismatched body is never evidence
that this bet was debited.

### Lookup

```http
GET /internal/v1/wallet/transactions/debit/{betId}
```

Only a 404 Problem Detail with `errorCode=WALLET_OPERATION_NOT_FOUND` means no debit proof exists.
Lookup also requires HTTP `200` and the same exact debit proof. Account absence and all other
errors retain their own verdict.

### Refund

```http
POST /internal/v1/wallet/transactions/credit
Idempotency-Key: refund:<betId>
```

The only requested meaning is `source=USER_LOCKED`, `reason=REFUND`, for the exact full exposure.
Only HTTP `200` plus an exact proof for the trusted user, amount, `reason=BET_REFUND`, operation
group, and timestamp completes compensation. A mismatched success body remains incomplete and is
never persisted as a refund proof.

Wallet Problem Details are decoded with their full RFC fields. Durable rejections include account
absence, insufficient balance, currency mismatch, amount out of range, and account recovery
blocking. `WALLET_IDEMPOTENCY_CONFLICT` triggers an authoritative lookup under the original
identity. An exact debit proof may be adopted; missing or mismatched evidence releases Risk and
durably rejects the bet without refunding another operation. Betting never evades a conflict by
inventing a new key. Timeouts, 5xx, and `WALLET_BUSY` remain retryable with the same identity.

## Kafka contracts

All Kafka values are raw Avro binary without Schema Registry framing.

| Direction | Topic | Key | Additional identity |
| --- | --- | --- | --- |
| Produce | `bet.placed.v1` | `userId` | outbox UUID |
| Consume | `wallet.debited.v1` | `userId` | exact single ASCII `event-id` header |
| Consume | `wallet.debit-failed.v1` | `userId` | exact single ASCII `event-id` header |
| Consume | `bet.settled.v1` | `eventId` | event payload identity |
| Consume | `bet.voided.v1` | `eventId` | event payload identity |
| Consume | `bet.resolution.revised.v1` | `betId` | revision ID and number |

Every identifier carried as text must be a canonical lowercase UUID. Consumer redelivery is
expected and is handled by durable state rather than process memory. Base and revision settlement
event identities must refer to one of the bet's selected events.
