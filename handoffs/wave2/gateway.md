# API Gateway — Wave 2 Handoff

## Purpose

This report records the Wave 2 Gateway boundary that completes authenticated integration with
Betting Service. The broader HTTP, WebSocket, Kafka, rate-limit, DLT, and operational contracts in
the Wave 1 handoff remain in force.

## Final state

- Local branch: `gateway`
- Final tip: `8248a3233f0fce7ca36a503ee71b7a8a0802d733`
- History: 117 commits, one root, 116 single-parent commits, no merges
- Release artifact: `com.sportsbook:gateway:1.0.0`
- Protocol dependency: `com.sportsbook:shared-protocol:1.0.0`
- Java target: 17
- Default HTTP port: 8080

The local branch points to the final tip. No tag, backup branch, or remote push was created.

## Betting trust boundary

Gateway now requires `GATEWAY_BETTING_API_KEY` in every servlet runtime. It must be nonblank, at
least 32 characters, and different from `GATEWAY_WALLET_API_KEY`. Invalid or duplicated
credentials fail application startup.

For the three allowlisted Betting routes, Gateway removes every client-supplied internal and actor
header, validates the user JWT, then sends exactly:

```text
X-Internal-Service: gateway
X-Internal-Api-Key: ${GATEWAY_BETTING_API_KEY}
X-User-Id: <canonical JWT subject>
X-User-Roles: <validated roles, only when nonempty>
```

The Betting credential is read by configuration and startup validation but attached only to
Betting outbound requests; it is never attached to Wallet or public Odds Feed requests. Conversely,
the Wallet credential is attached only to Wallet requests. User `Authorization` is removed before
proxying, while request bodies, `Idempotency-Key`, valid tracing, and downstream response status
and evidence retain the Wave 1 relay contract.

The final authenticated routes are:

| External route | Betting route |
| --- | --- |
| `POST /api/v1/bets` | `POST /internal/v1/bets` |
| `GET /api/v1/bets` | `GET /internal/v1/bets` with the actor forced from JWT |
| `GET /api/v1/bets/{betId}` | `GET /internal/v1/bets/{betId}` |

No Risk, Settlement, Wallet mutation, Betting admin, or arbitrary internal route is exposed.

`BetVoided.reason=MARKET_VOID` is a permanent contract failure and is quarantined through the
exact source partition of `bet.voided.v1.DLT`. A market-result void arrives as `BetSettled`, and
the client projection remains `status=SETTLED`, `result=VOID`.

## Deployment obligation

The deployment secret system must inject one matching value into:

```text
Gateway: GATEWAY_BETTING_API_KEY
Betting: BETTING_GATEWAY_API_KEY
```

It must also inject a different matching Gateway-to-Wallet pair. Neither value may be tracked,
logged, reused for another service direction, or forwarded through a public request. Rotating a
pair requires coordinated process restarts.

All other Gateway Wave 1 obligations remain unchanged, including a single replica, explicit
source and uppercase `.DLT` topic provisioning with matching partition counts, and client-side
revision reconciliation after reconnect or a detected gap.

## History and verification

The final history is linear and has one README-only root. Development commits use conventional
subjects with empty bodies, keep production and tests separate, and contain no fixup, squash,
development diary, changelog, reconstruction, or provenance material. The Maven wrapper and the
final documentation commit are the only reviewed bulk exceptions. The release commit immediately
precedes the final documentation commit.

Static history and affected route-authentication, invalid-configuration,
credential-isolation, void-contract, JWT actor, header-scrubbing, and downstream-relay tests
passed. The sole final Java 17 `clean verify` ran 127 tests with zero failures, errors, or skips and
also passed embedded-Kafka integration, packaging, Spotless, and Checkstyle.
