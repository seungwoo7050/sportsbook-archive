# Operations

## Runtime dependencies

Betting Service requires PostgreSQL, Redis, Kafka, Risk Service, and Wallet Service. PostgreSQL is
mandatory and authoritative. Redis is on the placement critical path for odds reads; an unavailable
or malformed snapshot prevents a safe placement decision. Kafka is required for accepted-bet
publication and settlement/wallet hint consumption.

Default endpoints are suitable only for local development:

| Dependency | Default |
| --- | --- |
| PostgreSQL | `jdbc:postgresql://localhost:5432/betting` |
| Redis | `localhost:6379` |
| Kafka | `localhost:9092` |
| Risk Service | `http://localhost:8083` |
| Wallet Service | `http://localhost:8081` |
| Betting HTTP | `8082` |

## Environment

| Variable | Required | Default or meaning |
| --- | --- | --- |
| `BETTING_GATEWAY_API_KEY` | yes | Gateway-to-Betting secret, at least 32 nonblank characters |
| `BETTING_RISK_API_KEY` | yes | Betting-to-Risk secret, at least 32 nonblank characters |
| `BETTING_WALLET_API_KEY` | yes | Betting-to-Wallet secret, at least 32 nonblank characters |
| `BETTING_DB_URL` | no | Local PostgreSQL JDBC URL |
| `BETTING_DB_USER` | no | `betting` |
| `BETTING_DB_PASSWORD` | no | `betting` |
| `BETTING_REDIS_HOST` | no | `localhost` |
| `BETTING_REDIS_PORT` | no | `6379` |
| `BETTING_KAFKA_BOOTSTRAP` | no | `localhost:9092` |
| `RISK_BASE_URL` | no | `http://localhost:8083` |
| `WALLET_BASE_URL` | no | `http://localhost:8081` |
| `BETTING_HTTP_PORT` | no | `8082` |
| `SPRING_PROFILES_ACTIVE` | no | Add `json` for structured JSON console logs |

All three secrets must be pairwise distinct and isolated from secrets used by other
service-to-service directions. Configuration object string output redacts the two outbound keys.
Risk and Wallet base URLs must be absolute HTTP(S) roots without credentials, query, or fragment,
and their canonical scheme, host, and effective port must identify different destinations.

## Database startup

Flyway applies migrations V1 through V10 before Hibernate validates the mapping.
`ddl-auto=validate` is intentional; the application must never create or mutate production schema
through Hibernate.

Before rollout:

1. Back up the Betting database according to the platform recovery policy.
2. Confirm V1 through V10 checksums match the released immutable migration files.
3. Confirm Flyway applies the complete V1 through V10 chain before Hibernate validation.
4. Confirm the application role can read and write service tables but cannot alter schema outside
   the migration process.
5. Start one instance and confirm Flyway and Hibernate validation before adding traffic.

Do not edit any released V1 through V10 migration. Add V11 or later for future schema changes.

## Kafka provisioning

Provision these topics and the uppercase `.DLT` companions of every consumed topic before enabling
consumers:

- `bet.placed.v1`
- `wallet.debited.v1`
- `wallet.debit-failed.v1`
- `bet.settled.v1`
- `bet.voided.v1`
- `bet.resolution.revised.v1`

Consumer topic auto-creation is disabled. Every source and `.DLT` topic, with matching partition
counts, must exist before rollout.

The outbox producer uses `acks=all`, idempotence, and no more than five in-flight requests per
connection. Consumers disable automatic commit, use record acknowledgement, and receive raw
`byte[]` keys and values. Permanent wire, key, and identity failures route to the exact source
partition of the `.DLT` topic; missing DLT partitions therefore fail recovery instead of falling
back to producer partitioning. The dedicated DLT producer bounds `max.block.ms` and
`request.timeout.ms` at five seconds, `delivery.timeout.ms` at ten seconds, and waits eleven seconds
for the acknowledged result. Transient infrastructure failures are not sent to DLT and remain on
delayed retry without recovering the source offset.

Deploy revision consumers in Betting and Gateway before enabling the Settlement revision producer.
The revision topic must be partitioned by `betId`; base resolution topics remain keyed by
`eventId`.

## Schedules

| Job | Default interval | Role |
| --- | ---: | --- |
| Outbox publisher | 1 second | Publish pending accepted-bet records and mark acknowledgement |
| Placement reconciliation | 10 seconds | Claim and resume eligible `PENDING` bets older than 30 seconds |

Each job has its own named single-thread scheduler, so a blocked outbox batch cannot starve
reconciliation. The ordinary producer bounds `max.block.ms` and `request.timeout.ms` at five
seconds and `delivery.timeout.ms` at ten seconds; publication waits at most eleven seconds for an
acknowledgement. Both jobs are idempotent and safe to re-enter after process restart. For tests,
their intervals are parked and Kafka listeners are not auto-started.

## Timeouts and circuit breakers

Risk and Wallet clients use a 200 ms connection timeout and 500 ms read timeout by default. The
shared circuit-breaker configuration uses a count-based window of 20 calls, opens at 50 percent
failure after at least 10 calls, and waits five seconds before half-open probes.

Only dependency availability failures count toward the breaker. Durable business verdicts do not
poison availability metrics and must not be retried under a new identity.

Redis uses a 200 ms command timeout so a slow odds cache fails the placement safely rather than
holding servlet capacity.

## Health and metrics

Spring Boot Actuator exposes health, info, Prometheus, and metrics endpoints configured for the
deployment network boundary. Health probes are enabled and detailed health is shown only when
authorized by the surrounding platform.

Prometheus metrics carry the bounded service tag `service=betting-service`. Never add user, bet,
selection, idempotency key, reservation token, or wallet operation identifiers as metric labels.

The `json` logging profile writes structured Logstash JSON and preserves `traceId` and `spanId`
from MDC. The default profile writes readable console logs. Logs must never include API keys,
reservation tokens, idempotency keys, wallet bodies, or raw Problem Details from dependencies.

## Placement incident triage

### Growing PENDING population

1. Check Risk and Wallet availability and circuit states.
2. Check reconciliation job execution and database lock pressure.
3. Inspect lease expiry, owner-fenced claim clearing, and eligibility age; do not edit
   `updated_at` to force a retry.
4. Group bets by `placement_phase` and `compensation_state` rather than retrying manually.
5. Confirm stored risk tokens exist for `RISK_RESERVED` and later phases.
6. Confirm Wallet lookup returns exact user, amount, reason, operation, and timestamp evidence for
   the original canonical bet UUID.
7. Restore the dependency and let reconciliation reuse stored evidence.

Do not delete PENDING rows, clear their proof columns, or submit a replacement reservation/debit
identity. Those actions can duplicate exposure or money movement.

### Outbox backlog

1. Check Kafka broker acknowledgement latency and topic availability.
2. Confirm the idempotent producer configuration is active.
3. Inspect pending outbox age and count.
4. Restore Kafka and allow the publisher to retry existing rows.

Do not synthesize a new outbox event for an existing accepted bet. Publication is at-least-once,
and downstream consumers must accept duplicate delivery of the original event.

### Wallet-event receipt backlog

Wallet events are wake hints. An unprocessed receipt indicates reconciliation did not complete,
not that Wallet failed to commit. Check the referenced bet, authoritative Wallet debit lookup, and
dependency availability before retrying the original record.

### Resolution DLT records

Check topic, raw Avro schema, canonical key, actor ownership, stored revision identity, and payload
hash. A revision keyed by `eventId` is invalid; `bet.resolution.revised.v1` requires `betId`.
Conflicting equal revisions require producer investigation and must not be rewritten locally.

## Backup and restore

Back up all Betting tables consistently. A restore that omits placement requests, compensation
proofs, wallet-event receipts, or outbox rows can break idempotency even if the main `bet` table is
present.

After restore:

1. Run Flyway validation.
2. Confirm outbox and receipt foreign keys.
3. Compare service time to UTC and database timestamps.
4. Start consumers before producers resume traffic.
5. Allow reconciliation to inspect restored PENDING bets.

Kafka is not a replacement for the Betting database. The service does not rebuild placement or
idempotency state from the event log.

## Release verification

Use Java 17 and the fixed `shared-protocol:1.0.0` artifact. The release gate is:

```bash
./mvnw clean verify
```

The resulting Spring Boot artifact is `target/betting-service-1.0.0.jar`. Verify deployment secret
injection, PostgreSQL migration validation, Redis connectivity, Kafka metadata, dependency
authentication, placement 201/202 `Location`, and resolution key behavior in the target
environment before shifting production traffic.
