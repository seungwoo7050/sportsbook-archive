# Internal HTTP API

All wallet routes are under `/internal/v1/wallet`. The boundary is stateless and closed: an
authenticated request with an unlisted method or path receives `403`, while a request without valid
credentials receives `401`.

## Authentication

Send exactly one value for each header:

```http
X-Internal-Service: <caller wire name>
X-Internal-Api-Key: <environment-provided secret>
```

The caller wire name is case-sensitive and is not trimmed.

| Caller | Wire name |
| --- | --- |
| Platform | `platform` |
| Gateway | `gateway` |
| Betting | `betting-service` |
| Settlement | `settlement-service` |
| Admin | `admin-api` |

Missing one header, duplicating either header, using an unknown caller, or presenting a bad key
returns `401 WALLET_AUTHENTICATION_REQUIRED`. Valid credentials for a forbidden route or credit
meaning return `403 WALLET_ACCESS_DENIED`.

Anonymous `GET` access is limited to `/actuator/health`, `/actuator/health/**`, and
`/actuator/prometheus`. Platform credentials are required for `/actuator`, `/actuator/**`, and all
other management endpoints.

## Route capabilities

| Method and path | Allowed caller | Success response |
| --- | --- | --- |
| `POST /internal/v1/wallet/accounts` | Platform | `200 AccountResponse` |
| `GET /internal/v1/wallet/accounts/{userId}/balance` | Platform, Gateway | `200 BalanceResponse` |
| `POST /internal/v1/wallet/transactions/deposit` | Platform | `200 WalletOperationResponse` |
| `POST /internal/v1/wallet/transactions/withdraw` | Platform | `200 WalletOperationResponse` |
| `POST /internal/v1/wallet/transactions/debit` | Betting | `200 WalletOperationResponse` |
| `GET /internal/v1/wallet/transactions/debit/{betId}` | Betting | `200 WalletOperationResponse` |
| `POST /internal/v1/wallet/transactions/credit` | Betting, Settlement, Admin | `200 WalletOperationResponse` |
| `POST /internal/v1/wallet/transactions/forfeit` | Settlement | `200 WalletOperationResponse` |
| `POST /internal/v1/wallet/transactions/adjustment` | Settlement | `200` or `202 AdjustmentProofResponse` |
| `GET /internal/v1/wallet/transactions/adjustment/{revisionId}` | Settlement | `200 AdjustmentProofResponse` |

Account, debit, and adjustment lookups return their specific `404` problem when the requested
resource does not exist.

Debit GET returns `200 WalletOperationResponse` for a stored success, the stored ProblemDetail and
status for a stored rejection, and `404 WALLET_OPERATION_NOT_FOUND` only when no matching debit
operation exists.

## Credit semantic allowlist

Route access alone is not enough for a credit. Exactly these five caller, source, and reason
combinations are allowed:

| Caller | `source` | `reason` |
| --- | --- | --- |
| `betting-service` | `USER_LOCKED` | `REFUND` |
| `settlement-service` | `USER_LOCKED` | `VOID` |
| `settlement-service` | `USER_LOCKED` | `REFUND` |
| `settlement-service` | `HOUSE_POOL` | `PAYOUT` |
| `admin-api` | `HOUSE_POOL` | `REFUND` |

Every other combination returns `403 WALLET_ACCESS_DENIED` before execution.

## Request identity

Every transaction POST requires exactly one `Idempotency-Key` header. Account creation is the only
POST in this API that does not use it. A general key must be nonblank printable ASCII, contain at
most 128 characters, and is used exactly as supplied without trimming or normalization. A missing,
duplicate, or invalid key returns `400 WALLET_INVALID_REQUEST` without calling the wallet service.

Debit identity has an additional rule: the POST key is the bet ID and must be a canonical lowercase
UUID string. The GET `{betId}` path must use the same canonical form. Uppercase, abbreviated, or
malformed UUID text returns `400`.

An adjustment key must exactly equal:

```text
settlement:revision:<revisionId>
```

The UUID text is the canonical form of the `revisionId` in the JSON body.

Submitting the same key and semantic fields again returns the stored operation outcome. Submitting
different semantic fields under that key returns `409 WALLET_IDEMPOTENCY_CONFLICT`.

## JSON contracts

`Money` requires these value fields:

```json
{"amount": 100, "currency": "KRW"}
```

`currency` is `KRW` or `USD`. Transaction amounts must be strictly positive.

### Requests

| Request | Required fields | Additional rules |
| --- | --- | --- |
| Open account | `userId`, `currency` | Both required; a reserved system UUID is invalid. |
| Deposit, withdraw, debit, forfeit | `userId`, `amount` | Both required; `amount` is positive. |
| Credit | `userId`, `amount`, `source`, `reason` | All required; `source` is `USER_LOCKED` or `HOUSE_POOL`; `reason` is `PAYOUT`, `VOID`, or `REFUND`. |
| Adjustment | `revisionId`, `betId`, `revisionNumber`, `userId`, `previousPayout`, `newPayout` | All object fields required; revision number is at least 1; payout amounts are nonnegative, share a currency, and have a nonzero delta; the user cannot be a reserved system account. |

### Account response

`AccountResponse` contains exactly:

1. `userId`
2. `currency`
3. `available`
4. `locked`
5. `outboundFrozen`
6. `version`
7. `createdAt`
8. `updatedAt`

### Balance response

`BalanceResponse` contains exactly:

1. `userId`
2. `available`
3. `locked`
4. `total`
5. `outboundFrozen`

Account and balance responses intentionally omit recovery debt, queue sequence, and freeze timing.

### Operation response

`WalletOperationResponse` contains exactly:

1. `operationGroupId`
2. `userId`
3. `amount`
4. `reason`
5. `at`

The `reason` is the committed ledger reason, such as `DEPOSIT`, `WITHDRAW`, `BET_DEBIT`,
`BET_PAYOUT`, `BET_REFUND`, or `BET_FORFEIT`.

### Adjustment proof response

`AdjustmentProofResponse` contains exactly these 14 fields:

1. `revisionId`
2. `betId`
3. `revisionNumber`
4. `userId`
5. `previousPayout`
6. `newPayout`
7. `deltaAmount`
8. `currency`
9. `status`
10. `queueSequence`
11. `operationGroupId`
12. `queuedAt`
13. `appliedAt`
14. `nextAttemptAt`

`deltaAmount` is signed. `status` is `APPLIED`, `BLOCKED`, or `REJECTED`. The nullable fields
`queueSequence`, `operationGroupId`, `queuedAt`, `appliedAt`, and `nextAttemptAt` remain present as
JSON `null` when absent. Retry count, idempotency key, and persistence observation fields are not
exposed.

## Adjustment HTTP semantics

- An immediately applied POST returns `200` and no `Location` header.
- A blocked negative adjustment returns `202` with
  `Location: /internal/v1/wallet/transactions/adjustment/{revisionId}`.
- A durable rejection returns its stored problem status and body. The POST response contains no
  proof representation or `Location`; the stored `REJECTED` proof remains available through GET.
- A repeated POST after worker completion returns the `APPLIED` proof with `200`.
- GET returns any `APPLIED`, `BLOCKED`, or `REJECTED` proof with `200`; an absent proof returns
  `404 WALLET_ADJUSTMENT_NOT_FOUND`.

## Problem details

Errors use `application/problem+json` and RFC 9457 fields:

- `type`
- `title`
- `status`
- `detail`
- `instance`
- `errorCode`

`instance` contains the request path without its query string. A stored business rejection may add
`balance` or `expectedCurrency`; no request credential, idempotency key, database diagnostic, or
exception text is reflected.

| Status | `errorCode` | Title | `type` |
| --- | --- | --- | --- |
| 400 | `WALLET_INVALID_REQUEST` | Invalid wallet request | `https://sportsbook/errors/wallet/invalid-request` |
| 401 | `WALLET_AUTHENTICATION_REQUIRED` | Authentication required | `https://sportsbook/errors/wallet/authentication-required` |
| 403 | `WALLET_ACCESS_DENIED` | Wallet access denied | `https://sportsbook/errors/wallet/access-denied` |
| 404 | `WALLET_ACCOUNT_NOT_FOUND` | Account not found | `https://sportsbook/errors/wallet/account-not-found` |
| 404 | `WALLET_OPERATION_NOT_FOUND` | Wallet operation not found | `https://sportsbook/errors/wallet/operation-not-found` |
| 404 | `WALLET_ADJUSTMENT_NOT_FOUND` | Wallet adjustment not found | `https://sportsbook/errors/wallet/adjustment-not-found` |
| 409 | `WALLET_IDEMPOTENCY_CONFLICT` | Idempotency key conflict | `https://sportsbook/errors/wallet/idempotency-conflict` |
| 422 | `WALLET_CURRENCY_MISMATCH` | Currency mismatch | `https://sportsbook/errors/wallet/currency-mismatch` |
| 422 | `WALLET_INSUFFICIENT_BALANCE` | Insufficient balance | `https://sportsbook/errors/wallet/insufficient-balance` |
| 422 | `WALLET_AMOUNT_OUT_OF_RANGE` | Amount out of range | `https://sportsbook/errors/wallet/amount-out-of-range` |
| 423 | `WALLET_ACCOUNT_RECOVERY_BLOCKED` | Wallet account blocked for recovery | `https://sportsbook/errors/wallet/account-recovery-blocked` |
| 500 | `WALLET_INTERNAL_ERROR` | Internal server error | `https://sportsbook/errors/wallet/internal-error` |
| 503 | `WALLET_BUSY` | Wallet temporarily busy | `https://sportsbook/errors/wallet/busy` |

`503 WALLET_BUSY` includes `Retry-After: 1`. Retryable PostgreSQL connection, availability, lock,
timeout, deadlock, and serialization failures use this response. A transient failure can be retried
with the same key and does not claim that key when no operation committed. Permanent database
failures remain an opaque `500 WALLET_INTERNAL_ERROR`.
