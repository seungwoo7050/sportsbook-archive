# Redis keyspace and reservation lifecycle

All UUID components are lowercase canonical UUID strings. Currency suffixes in keys are lowercase.
Amounts and aggregate counts are base-10 integers limited to Redis Lua's exact range,
`0..9007199254740991`; candidate stakes must be positive.

## Key inventory

| Key pattern | Type | Contents and lifetime |
| --- | --- | --- |
| `risk:reservation:<betId>` | hash | Request fingerprint, identity, stake, selections, pattern result, lifecycle state, and timestamps. Its TTL is the configured reservation retention. |
| `risk:reservations:active` | string | Global count of active reservations. It is deleted when the count reaches zero. |
| `risk:reservations:user:{<userId>}:bets` | sorted set | Active `betId` members scored by reservation time. |
| `risk:reservations:user:{<userId>}:stakes:<currency>:entries` | sorted set | Active `<betId>|<amount>` members. |
| `risk:reservations:user:{<userId>}:stakes:<currency>:sum` | string | Exact aggregate paired with the active stake entries. |
| `risk:reservations:user:{<userId>}:selections:entries` | sorted set | Active `<betId>|<selectionCount>` members. |
| `risk:reservations:user:{<userId>}:selections:sum` | string | Exact aggregate paired with the active selection entries. |
| `risk:reservations:user:{<userId>}:selection:<selectionId>` | sorted set | Active bet IDs containing one selection. |
| `risk:limit:{<userId>}:<dimension>:entries` | sorted set | Committed `<betId>|<amount>` or `<betId>|<selectionCount>` members scored by commit time. |
| `risk:limit:{<userId>}:<dimension>:sum` | string | Exact aggregate paired with one committed window. |
| `risk:limit:override:{<userId>}` | hash | Administrative limits. Fields are `<LIMIT_TYPE>:<CURRENCY>` or `SELECTIONS_PER_MINUTE`; they have no service-assigned TTL. |
| `risk:history:{<userId>}:bets` | sorted set | Confirmed bet IDs used by rapid-betting evaluation. |
| `risk:history:{<userId>}:stakes:<currency>` | sorted set | Bounded confirmed `<betId>|<amount>` samples for sudden-stake evaluation. |
| `risk:history:{<userId>}:selection:<selectionId>` | sorted set | Confirmed bet IDs used by repeated-selection evaluation. |
| `risk:event:fingerprint:<betId>` | string | Fingerprint of a first-seen accepted event. Its TTL is the reservation retention. |

Committed dimensions are `stake-daily:<currency>`, `stake-weekly:<currency>`,
`stake-monthly:<currency>`, and `selections-per-minute`. Their windows are one day, seven days,
30 days, and one minute respectively. Non-empty committed keys receive a TTL equal to the window
plus five minutes. Confirmed pattern keys receive the configured idle retention, seven days by
default; stake samples are also capped at 100 by default.

Active aggregate keys do not have independent TTLs. Expired footprints are cleaned while a script
examines that user's active set, and the lifecycle hash remains the evidence needed to perform the
cleanup. Operational Redis eviction must therefore be disabled for these keys.

## Fingerprint and token

The reservation token is the lowercase SHA-256 fingerprint of a canonical request. Version
`risk-reservation-v1`, user ID, bet ID, stake amount, currency, and sorted selection IDs are fed to
the digest as length-prefixed UTF-8 fields. Selection order therefore does not change the token,
while any semantic request change does.

The token is an idempotency binding, not a caller credential. It is returned only for approved
reservations and is required in `X-Risk-Reservation-Token` for commit. Authentication still uses
the caller-specific internal headers.

## Lifecycle

The configured lease is two minutes and retention is 32 days by default.

```text
absent ── reserve approved ──> RESERVED ── commit with token ──> COMMITTED
   │                              │
   └─ reserve rejected ───────> REJECTED
                                  │
RESERVED ── release ──────────> RELEASED
RESERVED ── lazy expiry ──────> EXPIRED
```

- Repeating reserve with the same fingerprint returns the retained result with `replayed=true`.
- Reusing a retained `betId` with a different fingerprint returns a conflict.
- Repeating commit on `COMMITTED` succeeds as a replay. Commit on a missing, expired, released, or
  rejected lifecycle is reported as not found at the HTTP boundary.
- Repeating release on `RELEASED` succeeds as a replay. Release of missing or tombstoned state is
  also an idempotent HTTP success; release of `COMMITTED` is a conflict.
- After the lifecycle TTL expires, the service no longer has a reservation replay record for that
  `betId`.

An approved reserve creates active capacity. Commit moves the full stake and full selection count
from active aggregates into all applicable committed windows and confirmed pattern facts. Release
or expiry removes the active capacity without adding committed facts. No partial stake or partial
selection reservation is supported.

## Pattern state

Admission evaluates confirmed facts together with unexpired active reservations, so concurrent
candidates cannot all ignore one another. The candidate receives zero or more flags:

- `SUSPECT` and `REVIEW` are advisory and do not by themselves reject admission.
- `BLOCK` rejects admission and stores the rejection for bounded replay.

If an already-accepted Kafka fact has no retained lifecycle, accepted projection adds its full
exposure directly to committed counters and confirmed pattern facts. It does not create an active
reservation or retroactively reject the accepted bet.

See [Internal API and event contracts](internal-api-and-event-contracts.md) for transition status
mapping and [Runtime and consistency boundaries](runtime-and-consistency-boundaries.md) for the
supported Redis topology.
