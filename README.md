# Risk Service

Risk Service owns sportsbook admission policy. It evaluates user limits and suspicious activity,
then reserves capacity before a bet can debit funds.

The service is intentionally small at its boundary:

- Redis is the authoritative store for limits, reservations, and recent risk history.
- Kafka carries accepted bet facts and non-authoritative risk signals.
- Internal HTTP APIs expose reservation lifecycle, limit administration, and diagnostics.
- Shared Protocol supplies the value objects and Avro records exchanged with other services.

The implementation targets Java 17 and runs as a Spring Boot service.
