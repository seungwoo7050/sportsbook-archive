# Gateway Architecture

## Responsibility

The gateway owns the platform's public transport boundary. Its responsibilities are deliberately
narrow:

1. authenticate users at HTTP and STOMP entry points;
2. remove caller-controlled trust headers;
3. apply Redis-backed request limits;
4. proxy only approved HTTP method and path combinations;
5. validate raw Avro events before projecting them to WebSocket clients; and
6. quarantine records that cannot be delivered safely.

Business state remains in the downstream services. The gateway has no database, does not call the
risk service, and does not keep the latest state or revision for a bet.

## Request path

```text
HTTP client
    |
    v
trusted-header removal -> JWT security -> rate limit -> exact route
                                                     |
                           +-------------------------+-------------------------+
                           |                         |                         |
                           v                         v                         v
                    betting service            wallet service             odds feed
                  identity + betting key    identity + wallet key        public reads
```

The trusted-header filter runs at the outside edge and hides all case variants of
`X-User-Id`, `X-User-Roles`, `X-Internal-Service`, and `X-Internal-Api-Key`. Route filters remove
those headers again before constructing downstream identity. They also remove the external bearer
token from every downstream request.

Authenticated betting and wallet calls receive `X-User-Id` derived from the verified JWT. If the
token has roles, the gateway supplies them as a comma-separated `X-User-Roles` value. Both private
downstream routes receive `X-Internal-Service: gateway` plus their distinct configured API key.
Servlet startup validates the two keys together and rejects a shared value. Public odds-feed calls
receive no identity or internal credential headers.

## Realtime path

```text
Kafka input (raw key and value)
    |
    v
strict Avro decode -> key and payload validation -> local STOMP projection
    |                                                   |
    |                                                   +-> public event topic
    |                                                   +-> owner user queue
    v
same-partition DLT after a permanent failure or exhausted transient retries
```

Kafka keys and values remain byte arrays until the event contract validates them. The gateway uses
the generated record types from `shared-protocol` and rejects malformed binary data, trailing
bytes, invalid identities, or key/payload mismatches.

The STOMP broker is process-local. Authenticated sessions are associated with the JWT subject, and
terminal bet updates are sent through Spring's user destination mapping. Odds updates are broadcast
on an event-specific public topic.

## State and delivery boundaries

Redis contains distributed token buckets under gateway-owned key prefixes. Redis is not an identity
or business-state source. If Redis is unavailable, the current request is admitted and the failure
is counted; later requests resume distributed limiting after connectivity returns.

WebSocket sessions and token-expiry schedules exist only in process memory. The gateway does not
offer durable WebSocket subscriptions or replay. Kafka delivery and WebSocket delivery may produce
duplicates around failures, so clients must tolerate repeated projections. Bet clients use revision
numbers as described in the realtime contract.

## Single-replica deployment

Gateway 1.0 runs as exactly one replica. Kafka consumer groups divide partitions among consumers,
while the simple STOMP broker knows only the clients connected to its own process. Multiple replicas
would therefore allow one process to consume an update whose subscriber is connected to another.
Deployment configuration must enforce a replica count of one.

Scaling beyond one replica requires a shared broker or another cross-instance fan-out design and is
outside this contract.

## Dependency boundaries

| Dependency | Gateway use | Failure behavior |
|---|---|---|
| shared protocol | Money, problem, and Avro event types | Required at build time |
| betting service | Bet placement and reads | 502 on connection or I/O failure; 504 on read timeout |
| wallet service | Authenticated balance read | Same proxy failure mapping |
| odds feed | Anonymous event and odds reads | Same proxy failure mapping |
| Redis | Distributed rate-limit buckets | Request is admitted on Redis failure |
| Kafka | Four event inputs and four dead-letter outputs | Failed source offset is retained if DLT publication fails |

There is no direct gateway-to-risk contract.

## Operational surface

Only `health`, `info`, and `prometheus` Actuator endpoints are exposed. Liveness and readiness
report application availability state; they do not include Redis, Kafka, or downstream reachability.
This prevents a dependency outage from creating an automatic restart loop and makes dependency
alerts a metrics and logs concern.

Structured logs are emitted as one JSON object per line. The stable service field is `gateway`, and
only `traceId` and `spanId` are admitted from MDC. Recognizable credential patterns in formatted
messages and emitted stack traces are replaced with `[REDACTED]`.
