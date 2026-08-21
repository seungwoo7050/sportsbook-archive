# Operations

## Required configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `INTERNAL_BETTING_SERVICE_API_KEY` | none | Required `betting-service` credential, at least 32 characters |
| `INTERNAL_ADMIN_API_KEY` | none | Required `admin-api` credential, at least 32 characters |
| `INTERNAL_PLATFORM_API_KEY` | none | Required `platform` credential, at least 32 characters |
| `REDIS_HOST` | `localhost` | Standalone Redis host |
| `REDIS_PORT` | `6379` | Standalone Redis port |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka bootstrap servers |
| `SERVER_PORT` | `8083` | HTTP port |
| `OTEL_TRACES_SAMPLER_ARG` | `0.1` | OpenTelemetry sampling probability |

The three secrets must be distinct. The service refuses to start when credential validation
fails. Do not pass secrets as command-line arguments or place them in tracked configuration.

Policy, retention, consumer group, and topic defaults are in
[`application.yml`](../src/main/resources/application.yml). Reservation retention must exceed both
the lease and the 30-day monthly counter window.

## Build

Use JDK 17 and the checked-in Maven 3.9.11 wrapper. The Maven repository must already contain
`com.sportsbook:shared-protocol:1.0.0`.

```bash
java -version
./mvnw -version
./mvnw -B clean verify
```

`verify` runs unit and integration tests, Spotless, and Checkstyle, then builds the executable
Spring Boot JAR.

## Start and stop

Supply secrets through the runtime's secret mechanism. A local shell can start the packaged
service as follows:

```bash
export INTERNAL_BETTING_SERVICE_API_KEY='replace-with-a-distinct-secret-at-least-32-characters'
export INTERNAL_ADMIN_API_KEY='replace-with-another-distinct-secret-at-least-32-characters'
export INTERNAL_PLATFORM_API_KEY='replace-with-a-third-distinct-secret-at-least-32-characters'
export REDIS_HOST=localhost REDIS_PORT=6379
export KAFKA_BOOTSTRAP=localhost:9092 SERVER_PORT=8083
java -jar target/risk-service-1.0.0.jar
```

Use the process supervisor's normal termination signal and readiness draining. The service has no
local state volume to flush, but Redis and Kafka must be operated according to their own shutdown
procedures.

## Health and readiness

```bash
curl -fsS http://localhost:8083/actuator/health
curl -fsS http://localhost:8083/actuator/health/liveness
curl -fsS http://localhost:8083/actuator/health/readiness
```

The readiness response is `UP` only when application readiness, Redis, and Kafka metadata checks
are up. The Kafka check has a two-second budget. Health details are intentionally hidden.

Health, probe paths, and Prometheus are anonymous so infrastructure can scrape them. Internal API
credentials must not be sent to those endpoints.

## Metrics

Prometheus metrics are exposed at:

```bash
curl -fsS http://localhost:8083/actuator/prometheus
```

Application meter IDs and bounded tags are:

| Meter ID | Tags | Meaning |
| --- | --- | --- |
| `risk.check.latency` | none | Diagnostic check duration |
| `risk.limit.violations` | `reason` | Diagnostic limit rejection count |
| `risk.pattern.flags` | `rule`, `action` | Diagnostic pattern match count |
| `risk.signal.delivery` | `outcome` | Best-effort signal delivery callback result |
| `risk.reservation.lua.latency` | `operation` | Redis script duration for reserve, commit, accepted projection, or release |
| `risk.reservation.expirations` | none | Expired reservations cleaned during admission |
| `risk.reservation.requests` | `result` | Created, rejected, conflict, or replay admission result |
| `risk.reservation.transitions` | `operation`, `result` | Commit and release result count |
| `risk.bet.placed.reconciliation` | `result` | Accepted-event reconciliation result count |
| `risk_bet_placed_dlt_total` | `reason` | Permanent accepted-event failures sent to DLT |

Micrometer converts dotted meter IDs to Prometheus snake-case names and adds the normal counter or
timer suffixes. No meter uses user, bet, selection, or reservation token as a tag.

Operationally significant conditions include readiness failure, a rising failed signal count, DLT
records, fingerprint or terminal reconciliation results, persistent consumer lag, and reservation
script latency. Alert thresholds belong to the deployment's traffic and latency objectives.

## Correctness gate

The release gate requires Docker, `curl`, `jq`, JDK 17, a Maven repository populated with the
shared protocol artifact, and three distinct test secrets. It starts isolated Redis 7 and Kafka,
builds the service, waits for readiness, checks concurrent admission and lifecycle replay, and
asserts that core reservation metrics are present.

```bash
export RISK_MAVEN_REPO=/absolute/path/to/isolated-maven-repository
export INTERNAL_BETTING_SERVICE_API_KEY='gate-betting-secret-at-least-32-characters'
export INTERNAL_ADMIN_API_KEY='gate-admin-secret-at-least-32-characters'
export INTERNAL_PLATFORM_API_KEY='gate-platform-secret-at-least-32-characters'
bash load-test/run-gate.sh
```

The runner uses a temporary output directory and removes its containers and volume on exit. Gate
responses, metrics, and service logs are diagnostic output and are not project artifacts.

## Incident boundaries

- Redis failure makes admission, reservation lifecycle, limit administration, and accepted-event
  reconciliation unavailable. Do not bypass the service with a default approval.
- Kafka input reconciliation retries unhandled failures without a limit. Inspect the blocking
  source record and dependency health before resetting offsets.
- Advisory risk signal failure does not roll back an admission result. Use the delivery metrics
  and logs to assess missing notifications.
- Deleting or evicting lifecycle and active aggregate keys can break replay and consistency checks.
  Restore Redis as a coherent dataset; do not repair individual aggregate keys while traffic is
  active.

The detailed state and event semantics are documented in
[Redis keyspace and reservation lifecycle](../architecture/redis-keyspace-and-reservation-lifecycle.md)
and [Runtime and consistency boundaries](../architecture/runtime-and-consistency-boundaries.md).
