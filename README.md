# Sportsbook Wallet Service

The wallet service is the authoritative owner of sportsbook account balances, locked funds,
matched double-entry ledger entries, durable operation outcomes, settlement adjustments, and
wallet integration events. No other service may mutate a wallet account.

PostgreSQL owns correctness. Redis is a best-effort idempotency hint, and Kafka publication is
driven from the PostgreSQL outbox.

## Prerequisites

- Java 17.
- Maven 3.9.11, provided by `./mvnw`.
- `com.sportsbook:shared-protocol:1.0.0` installed in the local Maven repository before building
  the wallet.
- PostgreSQL for an application run; Redis is optional, and Kafka is required when outbox delivery
  is enabled.
- Docker for both verification commands.

## Run

Provide five distinct internal API keys of at least 32 characters. Keep them in the runtime secret
manager; never store them in the repository.

```bash
env \
  WALLET_PLATFORM_API_KEY='<environment-provided secret>' \
  WALLET_GATEWAY_API_KEY='<environment-provided secret>' \
  WALLET_BETTING_SERVICE_API_KEY='<environment-provided secret>' \
  WALLET_SETTLEMENT_SERVICE_API_KEY='<environment-provided secret>' \
  WALLET_ADMIN_API_KEY='<environment-provided secret>' \
  ./mvnw spring-boot:run
```

The remaining environment settings and their exact defaults are listed in
[Operations](docs/operations.md).

## Verify

```bash
./mvnw clean verify
./mvnw -Psemantic-gates clean verify
```

Both commands are container-backed. The semantic profile selects the tagged PostgreSQL 16,
Redis 7, and Kafka 3.8 subset.

## Contracts

- [Architecture and invariants](docs/architecture.md)
- [Internal HTTP API](docs/internal-api.md)
- [Operations and observability](docs/operations.md)
