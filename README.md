# Risk Service

Risk Service owns atomic sportsbook admission, short-lived capacity reservations, user limit
overrides, and accepted-bet reconciliation. It is a Java 17 Spring Boot service published as
`com.sportsbook:risk-service:1.0.0` and uses `com.sportsbook:shared-protocol:1.0.0` for shared value
objects, errors, and Avro records.

Redis is the authoritative runtime state for admission and reservation decisions. Kafka supplies
accepted-bet facts and carries advisory risk signals. The service does not keep a relational
database or a transactional Kafka outbox.

## What the service owns

- Atomic admission against the configured single-bet, daily, weekly, monthly, and per-minute
  selection limits.
- Suspicious-pattern evaluation for rapid betting, sudden stake increases, and repeated
  selections.
- Idempotent reservation, commit, and release operations keyed by `betId` and an opaque request
  fingerprint.
- Per-user limit overrides managed by `admin-api`.
- Reconciliation of `BetPlacedRequested` records from `bet.placed.v1`, including first-seen
  projection when an accepted event arrives without a retained reservation.

Risk signals from the diagnostic evaluator are best effort. Reservation admission returns its
flags and rejections directly but does not publish those decisions as Kafka signals.

## Runtime dependencies

- Temurin-compatible JDK 17
- Standalone Redis 7
- Kafka broker reachable through `KAFKA_BOOTSTRAP`

The Lua operations intentionally span bet-scoped and user-scoped keys, so Redis Cluster is not a
supported topology. Redis availability and persistence policy are deployment responsibilities;
the service itself does not add a second state store.

## Build and run

The build requires the `shared-protocol:1.0.0` artifact in the configured Maven repository.

```bash
./mvnw clean verify
```

Set three distinct internal secrets of at least 32 characters, then start the executable JAR:

```bash
export INTERNAL_BETTING_SERVICE_API_KEY='replace-with-a-distinct-secret-at-least-32-characters'
export INTERNAL_ADMIN_API_KEY='replace-with-another-distinct-secret-at-least-32-characters'
export INTERNAL_PLATFORM_API_KEY='replace-with-a-third-distinct-secret-at-least-32-characters'
export REDIS_HOST=localhost
export REDIS_PORT=6379
export KAFKA_BOOTSTRAP=localhost:9092
java -jar target/risk-service-1.0.0.jar
```

The default HTTP port is `8083`. `/actuator/health`, its probe paths, and
`/actuator/prometheus` are anonymous; every internal API requires caller-specific credentials.

## Contracts and operations

- [Runtime and consistency boundaries](architecture/runtime-and-consistency-boundaries.md)
- [Redis keyspace and reservation lifecycle](architecture/redis-keyspace-and-reservation-lifecycle.md)
- [Internal API and event contracts](architecture/internal-api-and-event-contracts.md)
- [Operations](docs/operations.md)

Configuration defaults live in
[`src/main/resources/application.yml`](src/main/resources/application.yml). Credentials must be
provided through environment variables and must not be stored in configuration files.
