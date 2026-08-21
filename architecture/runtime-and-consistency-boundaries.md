# Runtime and consistency boundaries

Risk Service has two state-changing boundaries: standalone Redis Lua scripts and Kafka consumer
offset acknowledgment. Keeping those boundaries explicit is necessary when evaluating failure
behavior.

## Admission and reservation

`POST /internal/v1/risk/reservations` runs one `risk-reserve.lua` invocation. The script validates
the relevant key types and aggregates, removes expired active footprints for the user, evaluates
configured limits and pattern rules, and either records a rejection or creates a reservation with
all active capacity footprints. There is no interval in which the reservation exists without its
active stake and selection totals.

The authoritative admission calculation includes both committed rolling counters and unexpired
active reservations. The standalone diagnostic endpoint reads the same categories of state but
does not reserve capacity; its result can therefore become stale immediately and must not be used
as authorization to debit funds.

Commit and release are separate atomic Lua operations:

- Commit validates the opaque token, removes the active footprints, adds the stake and selection
  counts to rolling committed windows, records confirmed pattern facts, and marks the lifecycle
  `COMMITTED`.
- Release removes active footprints and marks the lifecycle `RELEASED`.
- Expired reservations are removed lazily by reservation and snapshot scripts. Their lifecycle is
  retained as `EXPIRED` for replay behavior.

Scripts validate the keys and aggregates required for each transition and return an error on an
inconsistency. Expired-footprint cleanup is a deliberate side effect of admission and snapshot
reads and may occur even when the candidate is rejected. Script errors fail the HTTP request or
Kafka reconciliation; there is no fallback that approves a candidate without Redis.

## Redis topology and persistence

Reservation scripts touch `risk:reservation:<betId>` together with keys tagged by `userId`.
Because these keys are intentionally in different hash slots, the supported deployment is a
single Redis server, not Redis Cluster. The application configures a two-second connect timeout
and a two-second command timeout.

The guarantees above come from atomic Redis script execution. Survival across host loss depends on
the operator's Redis persistence, replication, and restore configuration. Risk Service has no
database journal and does not rebuild Redis state from Kafka.

## Accepted-bet reconciliation

The `risk.bet-placed-consumer` group reads `BetPlacedRequested` records from `bet.placed.v1` with
manual immediate acknowledgment.

1. The consumer validates Avro, identifiers, Kafka key, slip shape, exposure, and idempotency key.
2. It tries to commit a matching reservation using the canonical reservation fingerprint.
3. If no lifecycle exists, it atomically projects the accepted bet into committed counters and
   pattern facts, guarded by `risk:event:fingerprint:<betId>`.
4. It acknowledges the source record only after reconciliation succeeds.

The accepted-event fingerprint marker makes matching redelivery a replay while the marker is
retained. A different fingerprint for the same `betId` is a permanent failure. The marker uses the
reservation retention period, so this is a bounded idempotency window rather than an unbounded
ledger.

Unhandled Redis, Kafka, or application failures remain unacknowledged and are retried every second
without an attempt limit. A repeatedly failing record can therefore hold its source partition.

Malformed input, key mismatch, fingerprint mismatch, and terminal reservation state are sent to
`bet.placed.v1.DLT`. The publisher waits up to ten seconds for the broker acknowledgment before the
source offset is acknowledged. A process failure between those two acknowledgments can produce a
duplicate DLT record; downstream DLT handling must tolerate duplicates.

## Advisory risk signals

The diagnostic evaluator submits limit and pattern signals asynchronously to Kafka. Submission or
delivery failures are counted and logged, but they do not reverse the diagnostic result and are
not retried by an outbox. Reservation admission returns flags to its caller and does not publish
signals. These topics are observational signals, not a state-transfer mechanism.

## Availability boundary

The readiness group contains Spring's `readinessState`, Redis health, and a Kafka metadata query.
The Kafka indicator waits at most two seconds for the cluster ID. Readiness reports `DOWN` when
either dependency check fails, but an already-started process can still receive traffic unless the
runtime removes it from service.

See [Operations](../docs/operations.md) for probe and metric endpoints and
[Redis keyspace and reservation lifecycle](redis-keyspace-and-reservation-lifecycle.md) for the
retained state model.
