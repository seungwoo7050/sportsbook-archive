# HTTP Contract

## Authentication

Private routes require an HTTP bearer token signed with RS256. The configured RSA public key must
use X.509 `PUBLIC KEY` PEM encoding and have a modulus of at least 2048 bits. The decoder accepts a
literal multiline PEM or an environment value containing escaped newline sequences.

Every accepted token has:

- an `exp` claim that is still in the future, with no clock-skew allowance; and
- a nonblank `sub` equal to the lowercase canonical string form of a UUID.

The `roles` claim is optional. When present, it must be an array of at most 16 distinct strings.
Every role must match `[A-Z][A-Z0-9_]{0,31}`. The gateway does not evaluate issuer or audience and
does not maintain a token revocation store.

Authentication failure returns `401 GATEWAY_UNAUTHORIZED`. An authenticated request outside the
method/path allowlist returns `403 GATEWAY_FORBIDDEN`. Error dispatches are allowed through the
security boundary so that a public-route proxy failure is not replaced by an authentication error.

## Public routes

| External request | Authentication | Downstream request |
|---|---|---|
| `POST /api/v1/bets` | Required | `POST /internal/v1/bets` on betting |
| `GET /api/v1/bets` | Required | `GET /internal/v1/bets` on betting |
| `GET /api/v1/bets/{betId}` | Required | Same suffix under `/internal/v1/bets` |
| `GET /api/v1/wallet/balance` | Required | `GET /internal/v1/wallet/accounts/{sub}/balance` |
| `GET /api/v1/events` | Anonymous allowed | Same path on odds feed |
| `GET /api/v1/events/{eventId}` | Anonymous allowed | Same path on odds feed |
| `GET /api/v1/odds/{eventId}/{marketId}/{selectionId}` | Anonymous allowed | Same path on odds feed |

No other application route or method is exposed. The gateway treats `{betId}` as an opaque path
segment; the betting service remains responsible for validating that resource identifier.

For the bet collection read, the gateway overwrites any caller-supplied `userId` query value with
the verified JWT subject. Other query parameters are retained. The individual bet path is forwarded
unchanged after the public prefix is replaced.

## Header boundary

The following inbound headers are always hidden before authentication and routing, using
case-insensitive name matching:

- `X-User-Id`
- `X-User-Roles`
- `X-Internal-Service`
- `X-Internal-Api-Key`

Every downstream request also has `Authorization` removed. The gateway then adds only the headers
required by that route:

| Header | Betting | Wallet balance | Public odds feed |
|---|---|---|---|
| `X-User-Id` | Verified `sub` | Verified `sub` | Not sent |
| `X-User-Roles` | Verified roles, when nonempty | Verified roles, when nonempty | Not sent |
| `X-Internal-Service` | `gateway` | `gateway` | Not sent |
| `X-Internal-Api-Key` | Configured betting key | Configured wallet key | Not sent |

Betting and wallet credentials are required to be distinct. Servlet startup rejects a missing,
short, or shared value without including either secret in the failure. The gateway injects each
only on its corresponding route after removing any caller-supplied internal headers.

## Request and response relay

The proxy retains request bodies and ordinary application headers, including `Idempotency-Key`.
It also retains an inbound `traceparent` only when there is exactly one valid version `00` value
with lowercase hexadecimal, nonzero trace and span identifiers, and flags `00` or `01`. Invalid or
ambiguous values are removed. When tracing has an active span, the gateway can replace a removed or
absent value with that span's valid `traceparent`.

Downstream status, body, content type, `Location`, and `Retry-After` are relayed. A downstream
application error therefore remains the downstream service's contract rather than being rewritten
as a gateway error.

The HTTP client uses these default bounds:

| Stage | Default | Environment setting |
|---|---:|---|
| Connection establishment | 500 ms | `GATEWAY_DOWNSTREAM_CONNECT_TIMEOUT` |
| Response read | 3 s | `GATEWAY_DOWNSTREAM_READ_TIMEOUT` |

A connection or other downstream I/O failure returns `502 GATEWAY_BAD_GATEWAY`. A read timeout
returns `504 GATEWAY_TIMEOUT`.

## Gateway problem response

Gateway-owned failures use `application/problem+json` and the shared protocol problem shape:

```json
{
  "type": "https://sportsbook/errors/upstream-timeout",
  "title": "Gateway Timeout",
  "status": 504,
  "errorCode": "GATEWAY_TIMEOUT",
  "detail": "An upstream service timed out.",
  "instance": "/api/v1/events/example",
  "correlationId": "generated-or-active-trace-id"
}
```

| Status | Error code | Meaning |
|---:|---|---|
| 401 | `GATEWAY_UNAUTHORIZED` | A private route lacks valid authentication |
| 403 | `GATEWAY_FORBIDDEN` | The method or path is not allowed |
| 429 | `GATEWAY_RATE_LIMITED` | The distributed token bucket rejected the request |
| 502 | `GATEWAY_BAD_GATEWAY` | The downstream connection or I/O path failed |
| 504 | `GATEWAY_TIMEOUT` | The downstream read exceeded its bound |

The correlation ID is the active trace ID when one exists; otherwise it is a newly generated UUID.

## Request limiting

The gateway applies one token bucket to each application request when limiting is enabled; Actuator
and error-dispatch paths are excluded. Authenticated requests use the canonical JWT subject, and
anonymous requests use a client address.

| Class | Default capacity | Redis key prefix |
|---|---:|---|
| Authenticated user | 120 per minute | `gateway:ratelimit:user:` |
| Anonymous address | 60 per minute | `gateway:ratelimit:ip:` |

Successful Redis-backed decisions include `X-RateLimit-Remaining`. A rejection returns 429 with
`X-RateLimit-Remaining: 0` and an integer-seconds `Retry-After` value of at least one.

The trusted-proxy CIDR list is empty by default. When the socket peer is not trusted, the gateway
ignores `X-Forwarded-For`. When the peer is trusted, it parses every hop and walks from right to
left to select the first untrusted address. An invalid hop or a chain containing only trusted hops
falls back to the socket peer.

Redis connection and command operations are bounded at 300 ms and 500 ms respectively. A Redis
failure admits the current request without a remaining-token header and increments
`gateway.ratelimit.fail.open`. The next requests continue attempting to use the distributed limit.

Invalid capacities, refill periods, or trusted CIDRs prevent application startup.
