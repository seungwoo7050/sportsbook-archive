# Build and Use

## Prerequisites

- JDK 17
- a working Docker daemon for Redis integration tests
- `com.sportsbook:shared-protocol:1.0.0` available from the configured Maven repositories

The checked-in wrapper downloads Maven 3.9.11.

## Verify

Run the complete build from the gateway project root:

```sh
./mvnw clean verify
```

The verify lifecycle compiles with Java release 17, runs unit and integration tests, checks Google
Java Format compliance, and applies Checkstyle validation. Tests cover JWT and header boundaries,
exact routing, Redis limiting, STOMP authorization and expiry, raw Avro contracts, Kafka retry and
DLT behavior, fan-out isolation, operational endpoints, structured logging, and concurrent identity
isolation.

The Redis integration suite starts a Redis container. Embedded Kafka tests create their own brokers
and topics.

## Package

The same verify command produces an executable Spring Boot artifact:

```text
target/gateway-1.0.0.jar
```

The artifact identity is `com.sportsbook:gateway:1.0.0`. Its runtime dependency set contains
`com.sportsbook:shared-protocol:1.0.0`.

## Start locally

Make Redis, Kafka, betting, wallet, and odds-feed endpoints available, then inject the required
runtime values through the process environment:

- `GATEWAY_JWT_PUBLIC_KEY`
- `GATEWAY_BETTING_API_KEY`
- `GATEWAY_WALLET_API_KEY`

Start from source:

```sh
./mvnw spring-boot:run
```

Or start the packaged artifact:

```sh
java -jar target/gateway-1.0.0.jar
```

Do not put these required values in a tracked environment file. The full configuration inventory is
in [Operations](operations.md).

## Basic checks

After startup, application availability and build identity are available without a token:

```sh
curl --fail http://localhost:8080/actuator/health/liveness
curl --fail http://localhost:8080/actuator/health/readiness
curl --fail http://localhost:8080/actuator/info
```

Prometheus can scrape:

```text
GET /actuator/prometheus
```

These probes report process availability, not Redis, Kafka, or downstream reachability.

## Runtime use

Use HTTP bearer authentication for private REST routes. Supply the bearer token in the STOMP
`CONNECT` frame for the private bet queue. Anonymous clients may use public event and odds HTTP
reads and may subscribe to canonical event destinations on the odds stream.

See [HTTP contract](http-contract.md) for exact routes and [Realtime contract](realtime-contract.md)
for STOMP destinations, Kafka inputs, DLT behavior, and client revision handling.

## Deployment checklist

- Run exactly one replica.
- Inject the RSA public key and distinct betting and wallet credentials through the deployment
  secret system; servlet startup rejects credential reuse.
- Set production dependency URIs and broker locations explicitly.
- Replace the default WebSocket origin patterns.
- Configure trusted proxy CIDRs only for controlled direct peers.
- Provision each DLT with the same partition count as its source and at least seven days of
  retention.
- Scrape Prometheus and collect standard-output JSON logs.
