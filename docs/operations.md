# Operations and observability

## Runtime prerequisites

The service runs on Java 17. The Maven wrapper uses Maven 3.9.11, and
`com.sportsbook:shared-protocol:1.0.0` must be installed in the local Maven repository before the
wallet build starts.

An application instance requires PostgreSQL. Flyway applies the four wallet migrations at startup
and Hibernate validates the mapped schema. Redis is optional and best effort. Kafka is required
when outbox delivery is enabled; publication is inactive until explicitly enabled. The complete
integration verification path uses all three dependencies.

## Environment configuration

These are the complete environment mappings declared by `application.yml`. Do not place API keys
in files or command logs. Supply only `<environment-provided secret>` values through the runtime
secret manager.

### Internal credentials

| Variable | Default | Contract |
| --- | --- | --- |
| `WALLET_PLATFORM_API_KEY` | none | Required Platform secret. |
| `WALLET_GATEWAY_API_KEY` | none | Required Gateway secret. |
| `WALLET_BETTING_SERVICE_API_KEY` | none | Required Betting secret. |
| `WALLET_SETTLEMENT_SERVICE_API_KEY` | none | Required Settlement secret. |
| `WALLET_ADMIN_API_KEY` | none | Required Admin secret. |

All five values must be distinct, nonblank, and at least 32 characters. Startup fails if any value
is absent or violates that contract.

### Database and scheduler

| Variable | Default | Purpose |
| --- | --- | --- |
| `WALLET_SCHEDULER_POOL_SIZE` | `4` | Shared scheduled-task pool size. |
| `WALLET_DB_URL` | `jdbc:postgresql://localhost:5432/wallet` | JDBC URL. |
| `WALLET_DB_USER` | `wallet` | Database user. |
| `WALLET_DB_PASSWORD` | `wallet` | Database password. Replace outside a local environment. |
| `WALLET_DB_CONNECTION_TIMEOUT_MS` | `2000` | Hikari connection acquisition timeout in milliseconds. |
| `WALLET_DB_LOCK_TIMEOUT` | `2s` | PostgreSQL session `lock_timeout`. |
| `WALLET_DB_STATEMENT_TIMEOUT` | `5s` | PostgreSQL session `statement_timeout`. |

The fixed pool bounds are 20 maximum connections and 5 minimum idle connections. JDBC timestamps
use UTC, and Open Session in View is disabled.

### Network dependencies and HTTP

| Variable | Default | Purpose |
| --- | --- | --- |
| `WALLET_KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka bootstrap servers. |
| `WALLET_REDIS_HOST` | `localhost` | Redis host. |
| `WALLET_REDIS_PORT` | `6379` | Redis port. |
| `WALLET_HTTP_PORT` | `8081` | HTTP listener port. |

Redis operations use a fixed 200 ms timeout. HTTP shutdown is graceful.

### Integrity, outbox, and recovery scheduling

| Variable | Default | Purpose |
| --- | --- | --- |
| `WALLET_INTEGRITY_ENABLED` | `true` | Enable periodic integrity scans. |
| `WALLET_INTEGRITY_POLL_INTERVAL` | `PT30S` | Delay between scans. |
| `WALLET_OUTBOX_ENABLED` | `false` | Enable outbox delivery and backlog sampling. |
| `WALLET_RECOVERY_ENABLED` | `true` | Enable blocked-adjustment recovery. |
| `WALLET_RECOVERY_POLL_INTERVAL` | `PT1S` | Delay between recovery polls. |
| `WALLET_RECOVERY_RETRY_BASE` | `PT1S` | Initial insufficient-funds delay. |
| `WALLET_RECOVERY_RETRY_CAP` | `PT60S` | Maximum insufficient-funds delay. |

Outbox scheduling is intentionally off by default. A production deployment publishes wallet events
only after an operator deliberately sets `WALLET_OUTBOX_ENABLED=true` and confirms the Kafka
destination, consumer contracts, and alerting.

## Advanced Spring properties

These settings are Spring properties, not additional environment mappings declared by
`application.yml`.

| Property | Default | Purpose |
| --- | --- | --- |
| `wallet.outbox.owner` | `${HOSTNAME:wallet-service}-${random.uuid}` | Nonblank process identity, at most 128 characters, used for lease fencing. |
| `wallet.outbox.poll-interval` | `PT1S` | Delay between claims. |
| `wallet.outbox.batch-size` | `20` | Maximum rows per claim. |
| `wallet.outbox.max-in-flight` | `100` | Process-wide asynchronous send bound. |
| `wallet.outbox.lease-duration` | `PT30S` | Claim lease duration. |
| `wallet.outbox.retry-base` | `PT1S` | Initial delivery retry delay. |
| `wallet.outbox.retry-cap` | `PT60S` | Maximum delivery retry delay. |
| `wallet.outbox.metrics-interval` | `PT5S` | Backlog gauge sampling delay. |

The publisher requires the lease duration to exceed Kafka completion bounds plus its safety margin.
Kafka producer settings are `acks=all`, idempotence enabled, at most 5 in-flight requests per
connection, a 5 second delivery timeout, a 5 second maximum blocking time, and a 4 second request
timeout. Delivery retries have no attempt limit and use the configured capped exponential delay.

Recovery uses a 5 second transaction timeout. An insufficient-funds attempt changes only proof
retry metadata and keeps the account, ledger, and operation state untouched.

## Health endpoints

The management base path is `/actuator`.

- Anonymous GET: `/actuator/health`, `/actuator/health/**`, `/actuator/prometheus`.
- Platform-authenticated access: `/actuator`, `/actuator/info`, `/actuator/metrics`, and every other
  management route.

Health details are shown only to an authorized caller. The `walletIntegrityHealth` component
behaves as follows:

| State | Meaning |
| --- | --- |
| `UNKNOWN` | No scan has completed; detail `reason=integrity_not_checked`. |
| `DOWN` | The scan failed; detail `reason=integrity_scan_failed`. |
| `DOWN` | A completed scan found one or more drift facts. |
| `UP` | The latest completed scan found zero drift facts. |

A completed scan includes `lastCheckedAt` and `driftCount` in the component details.

## Metrics

Micrometer registers these outbox meters:

- `wallet.outbox.claimed`
- `wallet.outbox.published`
- `wallet.outbox.retried`
- `wallet.outbox.fenced.completion`
- `wallet.outbox.lease.takeovers`
- `wallet.outbox.pending`
- `wallet.outbox.leased`
- `wallet.outbox.oldest.pending.seconds`

Integrity meters are:

- `wallet.integrity.account.snapshot.drift`
- `wallet.integrity.account.orphan.ledgers`
- `wallet.integrity.operation.group.drift`
- `wallet.integrity.recovery.queue.drift`
- `wallet.integrity.adjustment.outcome.drift`
- `wallet.integrity.adjustment.failure.drift`
- `wallet.integrity.adjustment.fingerprint.drift`
- `wallet.integrity.adjustment.ledger.drift`
- `wallet.integrity.total.drift`
- `wallet.integrity.scan.failed`
- `wallet.integrity.last.checked.epoch.seconds`

Prometheus renders the dotted Micrometer IDs with underscores; counter names also receive the
`_total` suffix, while gauge names do not. The integrity gauges describe the latest completed
repeatable-read scan; a scrape does not query PostgreSQL.

## Verification

Both verification commands require Docker:

```bash
./mvnw clean verify
./mvnw -Psemantic-gates clean verify
```

The default command runs the complete suite. The semantic profile selects the tagged wallet,
recovery, outbox, security, and live HTTP checks. The container-backed checks use PostgreSQL 16,
Redis 7, and Kafka 3.8 and exercise schema migrations, database concurrency, broker delivery,
recovery, and idempotent HTTP behavior.
