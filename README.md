# Sportsbook API Gateway

The gateway is the public HTTP and WebSocket boundary for the sportsbook platform. It validates
user JWTs, enforces distributed request limits, proxies an allowlist of HTTP routes, and projects
validated Kafka events to STOMP clients. It does not own betting, wallet, odds, settlement, or risk
state.

## Runtime contract

- Java 17 and `com.sportsbook:shared-protocol:1.0.0`
- RS256 JWT verification with a required expiry and canonical UUID subject
- Redis-backed limits of 120 requests per minute for authenticated users and 60 per minute for
  anonymous client addresses
- 500 ms downstream connect timeout and 3 s downstream read timeout
- Public odds streams and owner-scoped bet updates over STOMP
- Raw Avro consumption from four Kafka inputs with a same-partition dead-letter topic for each
- Exactly one gateway replica

The gateway is intentionally stateless with respect to bets and result revisions. Redis stores only
rate-limit buckets. WebSocket sessions and expiry tasks are local to the running process.

## Build

The shared protocol artifact must be available to Maven before building the gateway.

```sh
./mvnw clean verify
```

The Maven wrapper uses Maven 3.9.11. Integration tests require a working Docker daemon for the
Redis container used by the rate-limit tests.

## Run

Supply the three required runtime values through the runtime environment:

- `GATEWAY_JWT_PUBLIC_KEY`: an RSA public key in `PUBLIC KEY` PEM form, at least 2048 bits
- `GATEWAY_BETTING_API_KEY`: the gateway-specific betting credential, at least 32 characters
- `GATEWAY_WALLET_API_KEY`: the gateway-specific wallet credential, at least 32 characters

The two downstream credentials must be different. A servlet deployment fails during startup when
either credential is missing or weak, or when the same value is reused for both services.

Then start the packaged service:

```sh
java -jar target/gateway-1.0.0.jar
```

The default HTTP port is `8080`. Redis defaults to `localhost:6379`, Kafka to `localhost:9092`,
and downstream service locations to their local development ports. Production deployments should
set every dependency location explicitly and must provision all input and dead-letter topics before
starting the service.

## Documentation

- [Architecture](docs/architecture.md)
- [HTTP contract](docs/http-contract.md)
- [Realtime contract](docs/realtime-contract.md)
- [Operations](docs/operations.md)
- [Build and use](docs/build-and-use.md)
