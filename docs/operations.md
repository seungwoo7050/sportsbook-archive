# Gateway Operations

## Deployment requirements

Run exactly one gateway replica with Java 17. Before startup:

1. make Redis and Kafka reachable;
2. provision the four Kafka inputs and their four DLTs;
3. configure the betting, wallet, and odds-feed base URIs;
4. inject the RSA public key and distinct betting and wallet API keys from the deployment secret
   system; and
5. configure the allowed WebSocket origins.

The service does not perform a startup reachability check for Redis, Kafka, or downstream services.
Configuration shape and required runtime values are validated during application creation. A
servlet deployment fails startup if the betting and wallet keys are equal; failure text does not
render either value.

## Required runtime values

| Environment variable | Requirement |
|---|---|
| `GATEWAY_JWT_PUBLIC_KEY` | X.509 `PUBLIC KEY` PEM for an RSA key of at least 2048 bits |
| `GATEWAY_BETTING_API_KEY` | Nonblank betting secret of at least 32 characters, distinct from the wallet key |
| `GATEWAY_WALLET_API_KEY` | Nonblank wallet secret of at least 32 characters, distinct from the betting key |

The public key may contain literal newlines or escaped `\n` sequences. Do not place these values in
tracked configuration, command examples, logs, or diagnostic bundles. Changing any required value
requires a process restart because decoders and downstream authenticators are constructed at startup.

## HTTP and downstream configuration

| Environment variable | Default | Validation or use |
|---|---|---|
| `GATEWAY_HTTP_PORT` | `8080` | Public HTTP and WebSocket port |
| `GATEWAY_BETTING_URI` | `http://localhost:8082` | Betting HTTP base URI |
| `GATEWAY_WALLET_URI` | `http://localhost:8081` | Wallet HTTP base URI |
| `GATEWAY_ODDS_FEED_URI` | `http://localhost:8085` | Odds-feed HTTP base URI |
| `GATEWAY_DOWNSTREAM_CONNECT_TIMEOUT` | `500ms` | Downstream connection bound |
| `GATEWAY_DOWNSTREAM_READ_TIMEOUT` | `3s` | Downstream response-read bound |
| `GATEWAY_WS_ALLOWED_ORIGINS` | local HTTP origin patterns | Comma-separated Spring origin patterns |

Each downstream base URI must be an absolute `http` or `https` URI with a host and root path. User
information, a non-root path, query, and fragment are rejected.

The process uses graceful shutdown with a 20-second Spring lifecycle phase timeout.

## Redis and rate-limit configuration

| Environment variable | Default |
|---|---|
| `GATEWAY_REDIS_HOST` | `localhost` |
| `GATEWAY_REDIS_PORT` | `6379` |
| `GATEWAY_REDIS_USERNAME` | empty |
| `GATEWAY_REDIS_PASSWORD` | empty |
| `GATEWAY_REDIS_DATABASE` | `0` |
| `GATEWAY_REDIS_SSL` | `false` |
| `GATEWAY_RATELIMIT_ENABLED` | `true` |
| `GATEWAY_RATELIMIT_USER_CAPACITY` | `120` |
| `GATEWAY_RATELIMIT_USER_PERIOD` | `1m` |
| `GATEWAY_RATELIMIT_IP_CAPACITY` | `60` |
| `GATEWAY_RATELIMIT_IP_PERIOD` | `1m` |
| `GATEWAY_TRUSTED_PROXY_CIDRS` | empty |

Trusted proxy CIDRs are a comma-separated list. Leave the list empty unless the gateway's direct
network peers are controlled proxies that overwrite forwarding headers.

Redis connection establishment is bounded at 300 ms and commands at 500 ms. The client reconnects
automatically but rejects commands while disconnected. Requests fail open during those errors and
increment `gateway.ratelimit.fail.open`.

## Kafka configuration

| Environment variable | Default |
|---|---|
| `GATEWAY_KAFKA_BOOTSTRAP` | `localhost:9092` |
| `GATEWAY_TOPIC_ODDS_CHANGED` | `odds.changed` |
| `GATEWAY_TOPIC_BET_SETTLED` | `bet.settled.v1` |
| `GATEWAY_TOPIC_BET_VOIDED` | `bet.voided.v1` |
| `GATEWAY_TOPIC_BET_RESOLUTION_REVISED` | `bet.resolution.revised.v1` |
| `GATEWAY_KAFKA_RETRY_INTERVAL` | `1s` |
| `GATEWAY_KAFKA_RETRY_ATTEMPTS` | `2` |
| `GATEWAY_KAFKA_MAX_BLOCK_MS` | `5000` |
| `GATEWAY_KAFKA_REQUEST_TIMEOUT_MS` | `5000` |
| `GATEWAY_KAFKA_DELIVERY_TIMEOUT_MS` | `10000` |
| `GATEWAY_KAFKA_DLT_WAIT_TIMEOUT` | `11s` |
| `GATEWAY_KAFKA_DLT_TIMEOUT_BUFFER` | `1s` |

Input topic names must be nonblank, distinct, and must not end in `.DLT`. Retry attempts may be zero
or greater; durations must be positive.

For every configured input topic, provision `<input>.DLT` with the same partition count and at least
seven days of retention. The recoverer does not perform a separate destination-partition preflight;
the producer sends directly to the exact source partition number. If the DLT has fewer partitions,
publication fails and the source offset remains uncommitted. Topic auto-creation is not an
operational substitute for explicit provisioning.

The consumer groups are fixed as `gateway-odds` and `gateway-bets`. Auto-commit is disabled and the
listener acknowledgment mode is `RECORD`. Keep the gateway deployment at one replica.

## Health and metrics

These anonymous Actuator endpoints are exposed:

| Endpoint | Purpose |
|---|---|
| `/actuator/health` | Aggregate application availability |
| `/actuator/health/liveness` | `livenessState` only |
| `/actuator/health/readiness` | `readinessState` only |
| `/actuator/info` | Build group, artifact, name, and version |
| `/actuator/prometheus` | Prometheus scrape |

Health responses hide components and details. The liveness and readiness groups do not include
Redis, Kafka, betting, wallet, or odds-feed checks. Monitor those dependencies separately.

All Micrometer metrics receive `service="gateway"`. The rate-limit fail-open metric is exported to
Prometheus as `gateway_ratelimit_fail_open_total`. Alert on sustained increases because admitted
traffic is no longer receiving distributed enforcement during that interval.

Build info excludes build time and does not expose source-control metadata.

## Structured logs

Logs go to standard output as newline-delimited JSON. The configured fields are:

- `@timestamp`
- `level`
- `logger_name`
- `message`
- `service`, fixed to `gateway`
- `traceId` and `spanId` when present in MDC
- `stack_trace` when an exception is attached

Root and application loggers use `INFO`; the Apache Kafka logger uses `WARN`. Only `traceId` and
`spanId` are admitted from MDC.

The event provider replaces recognizable bearer tokens and values labelled as authorization,
internal API key, API key, password, or token with `[REDACTED]` in both formatted messages and
emitted stack traces. This is a final guard, not permission to log credentials. Avoid placing
secrets in message arguments or exception text.

## Failure response guide

| Symptom | Gateway behavior | Operator action |
|---|---|---|
| Redis unavailable | Requests admitted; fail-open counter rises | Restore Redis and confirm the counter stops increasing |
| Downstream connection refused | HTTP 502 problem response | Check the configured URI, network, and target process |
| Downstream read bound exceeded | HTTP 504 problem response | Check target saturation and request processing |
| Invalid Avro or key contract | Immediate same-partition DLT publication | Inspect raw evidence, correct the producer or data, then use controlled replay |
| Transient event delivery failure | Two retries, then DLT | Correct the delivery path and inspect the DLT |
| DLT unavailable | Source offset retained and record redelivered | Restore the DLT with matching partitions before resuming normal flow |
| Authenticated WebSocket reaches `exp` | Socket closed with code 1008 | Client obtains a new token and reconnects |

## DLT handling

Treat a DLT record as sensitive operational evidence because its raw application headers and value
are retained. Restrict read and produce permissions accordingly.

After correcting the root cause:

1. read the record from the exact DLT;
2. verify that the target source topic and same-numbered partition exist;
3. remove only framework recovery, exception, delivery-attempt, and deserializer-exception headers;
4. retain the raw key, value, and application headers;
5. publish to the tail of the paired source partition and wait for acknowledgment; and
6. confirm normal processing without changing the gateway consumer-group offsets.

`DltReplayRecordFactory` implements the record transformation but does not expose an HTTP endpoint,
consume DLT records, or publish them automatically.
