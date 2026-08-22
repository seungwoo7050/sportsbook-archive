# Realtime Contract

## STOMP endpoints

The gateway exposes native WebSocket STOMP handshakes at:

- `/ws/v1/odds` for public odds clients; and
- `/ws/v1/bets` for authenticated bet-status clients.

Both handshake paths pass through the same STOMP authorization policy. The default allowed origins
are local HTTP origins on any port for `localhost` and `127.0.0.1`; deployments set
`GATEWAY_WS_ALLOWED_ORIGINS` to the required origin patterns.

Transport limits are fixed:

| Limit | Value |
|---|---:|
| Inbound message | 64 KiB |
| Send buffer | 512 KiB |
| Send time | 10 s |

## CONNECT authentication

Authentication is carried in the STOMP `CONNECT` or `STOMP` frame as exactly one native
`Authorization: Bearer <token>` header. The token uses the same decoder and claim validator as HTTP.

The header may be omitted for an anonymous odds session. A malformed, repeated, invalid, or expired
bearer header rejects the connection. An authenticated session is registered under the canonical
UUID `sub` and may subscribe to its user destination.

Each authenticated connection receives an expiry task for its JWT `exp`. At expiry the gateway
closes the underlying WebSocket immediately with close code 1008. An early disconnect cancels the
task. Anonymous sessions have no token-expiry task.

## Client commands and destinations

Clients may use only `CONNECT` or `STOMP`, `SUBSCRIBE`, `UNSUBSCRIBE`, and `DISCONNECT`. Every client
`SEND` is rejected, as are other client commands.

| Destination | Authentication | Rule |
|---|---|---|
| `/topic/odds/{eventId}` | Optional | `eventId` must be one lowercase canonical UUID segment |
| `/user/queue/bets` | Required | Delivery is scoped to the authenticated JWT subject |

No wildcard, nested, application, or alternate queue destination is accepted.

## Odds update

An `OddsChanged` event is projected to `/topic/odds/{eventId}` with this JSON shape:

| Field | Type | Source |
|---|---|---|
| `eventId` | string | event identity |
| `marketId` | string | market identity |
| `selectionId` | string | selection identity |
| `previousOdds` | string | previous normalized decimal odds |
| `newOdds` | string | new normalized decimal odds |
| `changedAt` | ISO-8601 instant | event time |

The event, market, and selection identities must all be canonical UUIDs. The raw Kafka key must be
strict UTF-8 and equal `eventId`.

## Bet status update

Terminal bet events are projected to `/user/queue/bets` for the event's `userId`. The JSON fields
are:

```text
betId, userId, eventId, status, result, amount, reason,
revisionId, revisionNumber, updatedAt
```

`amount` has the shared Money shape `{ "amount": <integer>, "currency": <code> }`.

| Source event | `status` | `result` | `amount` | `reason` | `revisionId` | `revisionNumber` | `updatedAt` |
|---|---|---|---|---|---|---:|---|
| `BetSettled` | `SETTLED` | settlement result | payout | `null` | `null` | `0` | `settledAt` |
| `BetVoided` | `VOIDED` | `null` | refund | void reason | `null` | `null` | `voidedAt` |
| `BetResolutionRevised` | `SETTLED` | new result | new payout | `null` | actual revision ID | actual revision number | `revisedAt` |

The raw key for settled and voided events must equal `eventId`. The raw key for a resolution
revision must equal `betId`. All identity fields must be canonical UUIDs.

`BetVoided` is limited to whole-slip lifecycle or administrative voids. The retained
`MARKET_VOID` wire enum is rejected as a permanent contract failure: a market void is represented
by `BetSettled` with `status=SETTLED` and `result=VOID`.

A revision must have a number of at least one, matching previous and new payout currencies, and a
`revisedAt` value that is not before `sourceResultSettledAt`.

## Client state rule

The gateway does not store a latest revision. Each client keeps the greatest revision number seen
for each bet. Initial settlement is logical revision zero. Once a positive revision has been
applied, the client ignores duplicate revisions, lower revisions, and a late revision-zero
settlement for that bet.

Voided updates have no revision number and must be handled according to the client's terminal-state
rules.

WebSocket delivery is live and nondurable. A reconnect does not replay missed messages. Consumers
must refresh authoritative state over HTTP when continuity is uncertain.

## Kafka inputs

Gateway 1.0 consumes four raw Avro binary streams:

| Default input | Group | Expected record | Required key |
|---|---|---|---|
| `odds.changed` | `gateway-odds` | `OddsChanged` | `eventId` |
| `bet.settled.v1` | `gateway-bets` | `BetSettled` | `eventId` |
| `bet.voided.v1` | `gateway-bets` | `BetVoided` | `eventId` |
| `bet.resolution.revised.v1` | `gateway-bets` | `BetResolutionRevised` | `betId` |

Both key and value deserializers return `byte[]`. The value must contain exactly one binary Avro
record of the expected generated type; extra trailing bytes are a contract failure.

The listener uses record acknowledgment with Kafka auto-commit disabled. A successful projection
allows that record's offset to advance. Duplicate delivery remains possible around process or broker
failures, so clients cannot use arrival count as business state.

## Dead-letter boundary

Each input has a dead-letter topic formed by appending uppercase `.DLT`:

- `odds.changed.DLT`
- `bet.settled.v1.DLT`
- `bet.voided.v1.DLT`
- `bet.resolution.revised.v1.DLT`

Decode and event-contract failures bypass transient retries and go directly to the matching DLT.
With the default settings, other delivery failures are retried twice at one-second intervals, for
no more than three listener attempts before quarantine.

The dead-letter producer uses raw byte serializers, `acks=all`, and Kafka idempotence. Publication
uses the source partition and preserves the original key, value, and application headers. Recovery
metadata includes original topic, partition, offset, consumer group, and exception information.

DLT publication is fail-closed: without a separate destination-partition preflight, the producer
sends to the exact source partition number, and a send failure is raised back to the listener
container. The source offset is not committed, and the source record is eligible for redelivery.
The publisher has bounded block, request, delivery, result-wait, and buffer times documented in
[Operations](operations.md).

## Manual replay

There is no automatic replay consumer. After the record's cause is corrected, an operator-controlled
tool can use `DltReplayRecordFactory` to prepare a source record:

1. accept only a record from one of the four exact configured DLT names;
2. select the paired source topic and the same partition;
3. clone the raw key and value;
4. preserve application headers, including duplicate and null-valued headers;
5. remove Kafka recovery, exception, delivery-attempt, and deserializer-exception headers; and
6. publish the result at the tail of that source partition and wait for broker acknowledgment.

The factory creates the `ProducerRecord`; it does not send it. The operator must not reset the
`gateway-odds` or `gateway-bets` consumer-group offsets.
