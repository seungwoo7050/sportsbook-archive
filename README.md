# Sportsbook Betting Service

Betting owns wager placement, placement recovery, bet queries, and the local
projection of settlement results. It coordinates risk and wallet operations
through authenticated internal APIs and publishes betting lifecycle events
through a transactional outbox.

The service is a Java 17 Spring Boot application. Build and runtime details are
kept in the Maven project and the operator documentation under `docs/`.
