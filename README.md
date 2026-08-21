# Sportsbook Wallet Service

The wallet service owns user balances and the append-only double-entry ledger for the
sportsbook backend. It is the only service allowed to mutate available or locked funds.

## Responsibilities

- keep available and locked balances in one currency per account;
- record every money movement as a balanced debit-credit pair;
- make caller retries resolve to one durable operation outcome;
- publish wallet integration events through a transactional outbox;
- expose internal account, transfer, and settlement-adjustment APIs.

## Runtime

The service uses Java 17, Spring Boot, PostgreSQL, Redis, Kafka, Avro, and Maven. PostgreSQL
owns correctness. Redis is only an optional replay hint, and Kafka publication is driven from
the database outbox.

Build and runtime instructions are added as the executable project is introduced. The final
project documentation records the API, security, recovery, and operational contracts.
