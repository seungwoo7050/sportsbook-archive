# Sportsbook Betting Service

Betting Service owns wager placement, durable placement recovery, user-scoped bet queries, and
the local projection of settlement outcomes. It coordinates Risk Service and Wallet Service
without treating a network timeout as a business verdict, and publishes accepted bets through a
transactional outbox.

## Guarantees

- PostgreSQL is authoritative for bet state, idempotency, compensation, settlement projections,
  wallet-event receipts, and the transactional outbox.
- A risk reservation token is persisted before the first wallet side effect.
- Wallet debit and risk commit use stable identities across retries.
- Ambiguous dependency outcomes remain `PENDING` and are reconciled from durable evidence.
- A terminal rejection that follows a side effect is committed only after compensation completes.
- Wallet Kafka records are wake-up hints; wallet HTTP state remains authoritative.
- Placement recovery uses database-time, owner-fenced leases so replicas claim bounded, disjoint
  batches and an expired worker can be resumed safely.
- Base settlement and full-snapshot revisions are consumed idempotently under row locking.
- SYSTEM bets publish their unit stake while Risk and Wallet receive the full committed exposure.
- Internal callers use caller-specific secrets. Secrets are required, redacted from configuration
  output, and never share a client instance.

## Toolchain

- Java 17
- Maven Wrapper 3.9.11
- Spring Boot 3.2.11
- PostgreSQL with Flyway migrations V1 through V10
- Redis for odds snapshots and an idempotency fast path
- Kafka with raw Avro values from `com.sportsbook:shared-protocol:1.0.0`

The exact shared protocol source is commit
`f9de6bc1e533761ab4bb1454d8d4ab8175cdf001`.

## Build

Install the fixed shared protocol artifact in the local Maven repository, then run:

```bash
./mvnw clean verify
```

The build enforces Google Java Format, semantic import checks, unit and contract tests, and the
Spring Boot executable JAR build.

## Required secrets

Normal startup requires three distinct deployment-managed secrets of at least 32 nonblank
characters:

- `BETTING_GATEWAY_API_KEY`: authenticates Gateway requests entering Betting Service.
- `BETTING_RISK_API_KEY`: authenticates Betting Service to Risk Service.
- `BETTING_WALLET_API_KEY`: authenticates Betting Service to Wallet Service.

No production default exists for any secret.

## Run locally

Start PostgreSQL, Redis, Kafka, Risk Service, and Wallet Service, provide the required secrets,
then run:

```bash
./mvnw spring-boot:run
```

The default HTTP port is `8082`. Gateway calls the protected `/internal/v1/bets` routes; clients
must not call them directly.

## Reference

- [Architecture](docs/architecture.md)
- [Internal API and dependency contracts](docs/internal-api.md)
- [Operations](docs/operations.md)
