# Sportsbook Orchestration

Version 1.0.0 is the reproducible build, runtime, and full-stack verification boundary for the
sportsbook backend. It materializes every service at an exact commit, builds Java 17 release
artifacts in isolation, starts one private Compose project, verifies the cross-service contracts,
captures redacted evidence, and removes only resources owned by that run.

The supported release path is `scripts/cold_release_gate.py`. Direct long-lived use of the Compose
files is intentionally not the release contract because runtime credentials, exact artifacts,
ownership labels, evidence, and scoped cleanup are created by the gate.

## Locked release inputs

`services.lock` is the sole service-source inventory. Every checkout is a detached, clean worktree
at the full SHA below.

| Logical service | Branch | Locked commit | Exact artifact |
| --- | --- | --- | --- |
| Shared protocol | `shared-protocol` | `f9de6bc1e533761ab4bb1454d8d4ab8175cdf001` | `shared-protocol-1.0.0.jar` |
| Wallet | `wallet-service` | `c9a05f4d652f24ac97d3e1cd753f69cef2725ff3` | `wallet-service-1.0.0.jar` |
| Risk | `risk-service` | `c64f67dbc437a18640dc4984dea4d8194fb5b164` | `risk-service-1.0.0.jar` |
| Odds Feed | `odds-feed-service` | `574e83d2862f086ae07ff56fd95a8336f78a72da` | `odds-feed-service-1.0.0.jar` |
| Betting | `betting-service` | `f712bdf389ee3fb63d8cdc84c49e2b84a346edde` | `betting-service-1.0.0.jar` |
| Gateway | `gateway` | `8248a3233f0fce7ca36a503ee71b7a8a0802d733` | `gateway-1.0.0.jar` |
| Settlement | `settlement-service` | `fc53ee8bfbb99b083f504d414d84ae5a994e4b57` | `settlement-service-1.0.0.jar` |
| Admin | `admin-api` | `2fb55910475b31084e6489bf01c34cc970c96874` | `admin-api-1.0.0.jar` |

The shared artifact is installed first into a run-owned Maven repository. Each application is then
packaged with tests skipped—the service branches already own their final suites—and exactly seven
executable JARs are staged atomically. The Avro fixture publisher is built separately against the
same shared 1.0.0 artifact. All Java artifacts and the runtime image target Java 17; application
containers run as UID/GID 10001 from `eclipse-temurin:17-jre-jammy`.

Host build overrides are forbidden. In particular, the gate rejects `MAVEN_RUNNER`,
`SERVICES_LOCK`, `DOCKER_OUTPUT_ROOT`, `MAVEN_ARGS`, `MAVEN_OPTS`, `JAVA_TOOL_OPTIONS`,
`JDK_JAVA_OPTIONS`, and `_JAVA_OPTIONS` inherited from the shell.

## Stack topology

The combined stack contains 21 services: 18 long-running containers and three bounded gates
(`secret-preflight`, `topic-init`, and `consumer-assignment`).

| Layer | Services | Contract |
| --- | --- | --- |
| Persistence | PostgreSQL 16, Kafka 3.8 KRaft | Persistent named volumes; Kafka auto-create disabled |
| Cache | `redis-risk`, `redis-odds`, `redis-wallet`, `redis-gateway` | Dedicated volumes, AOF `everysec`, `noeviction` |
| Applications | Wallet, Risk, Odds, Betting, Gateway, Settlement, Admin | Exact staged JAR, Java 17, non-root runtime |
| Fault injection | Toxiproxy 2.9 | Betting→Risk/Wallet and Settlement→Wallet only |
| Observability | Prometheus 2.54.1, Loki 3.1.1, Grafana 11.2.0, Promtail 3.1.1 | Health-checked and included in final-state evidence |

The backend network is internal. Only Gateway, Toxiproxy control, and Grafana publish loopback
ports. The cold gate requests an ephemeral Gateway host port, and Gateway is fixed to one replica.

Startup is dependency-ordered:

```text
PostgreSQL + Kafka + four Redis instances
  -> secret-preflight + topic-init
  -> Wallet + Risk + Odds
  -> Betting
  -> Gateway
  -> consumer-assignment
  -> Settlement
  -> Admin
```

`docker compose up --wait` establishes the initial health boundary. After all E2E scenarios, the
gate captures the final state of every container and repeats application readiness checks. Wallet
must also report a completed integrity scan with zero drift and zero scan failures.

## Authentication and secret isolation

Every run generates fresh secrets in memory. The 11 service keys are at least 32 characters and
globally distinct. Each value is mapped to exactly one caller/callee direction:

| Logical key | Caller setting | Callee setting |
| --- | --- | --- |
| Gateway→Betting | `GATEWAY_BETTING_API_KEY` | `BETTING_GATEWAY_API_KEY` |
| Gateway→Wallet | `GATEWAY_WALLET_API_KEY` | `WALLET_GATEWAY_API_KEY` |
| Betting→Risk | `BETTING_RISK_API_KEY` | `INTERNAL_BETTING_SERVICE_API_KEY` |
| Betting→Wallet | `BETTING_WALLET_API_KEY` | `WALLET_BETTING_SERVICE_API_KEY` |
| Settlement→Wallet | `SETTLEMENT_WALLET_API_KEY` | `WALLET_SETTLEMENT_SERVICE_API_KEY` |
| Admin→Wallet | `ADMIN_WALLET_API_KEY` | `WALLET_ADMIN_API_KEY` |
| Admin→Risk | `ADMIN_RISK_API_KEY` | `INTERNAL_ADMIN_API_KEY` |
| Admin→Odds | `ADMIN_ODDS_FEED_API_KEY` | `ADMIN_API_INTERNAL_KEY` |
| Admin→Settlement | `ADMIN_SETTLEMENT_API_KEY` | `SETTLEMENT_ADMIN_API_KEY` |
| E2E→Wallet | `WALLET_PLATFORM_API_KEY` | `WALLET_PLATFORM_API_KEY` |
| E2E→Risk | `INTERNAL_PLATFORM_API_KEY` | `INTERNAL_PLATFORM_API_KEY` |

The gate also generates independent PostgreSQL and Grafana passwords plus one 2048-bit RSA key
pair. Gateway and Admin receive the public key as inline PEM. Admin uses issuer
`sportsbook-admin-e2e`; E2E Admin requests execute from loopback, matching the fixed Admin IP
allowlist without weakening it. Private key material and service credentials are never copied into
release evidence.

Wallet outbox, recovery, and integrity scanning are explicitly enabled. Settlement uses canonical
`SPRING_DATASOURCE_*`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, and
`SETTLEMENT_WALLET_BASE_URL` settings. Every Admin downstream has a dedicated URL and credential.

## Kafka contract

`docker/kafka/topics.manifest` is authoritative. Topic initialization is idempotent but fail-closed:
an existing topic with a different partition count, replication factor, or required retention is
reported as drift rather than changed automatically.

All 14 source topics have three partitions and replication factor one:

```text
wallet.debited.v1                 wallet.credited.v1
wallet.debit-failed.v1            risk.limit.violated
risk.pattern.suspected            odds.changed
market.status.changed             event.lifecycle
match.result                      bet.placed.v1
bet.settled.v1                    bet.voided.v1
bet.resolution.revised.v1         admin.action
```

The nine consumed dead-letter topics use the exact uppercase `.DLT` suffix, three partitions, and
at least seven days (`604800000` ms) retention:

```text
wallet.debited.v1.DLT             wallet.debit-failed.v1.DLT
odds.changed.DLT                  event.lifecycle.DLT
match.result.DLT                  bet.placed.v1.DLT
bet.settled.v1.DLT                bet.voided.v1.DLT
bet.resolution.revised.v1.DLT
```

The assignment gate confirms the fixed consumer groups own their expected partitions before
Settlement starts. The E2E poison-record case verifies that a partition-2 source record reaches
partition 2 of the matching DLT with its original identity headers.

## Databases and migrations

PostgreSQL bootstrap creates four databases owned by `sportsbook`: `wallet`, `betting`,
`settlement`, and `admin`. Flyway must report this exact 25-migration inventory before E2E begins:

| Database | Required versions |
| --- | --- |
| Wallet | V1–V4 |
| Betting | V1–V10 |
| Settlement | V1, V3–V10 |
| Admin | V1–V2 |

Missing, duplicate, failed, pending, or unexpected versions fail the release. Hibernate validates
the migrated schema; it does not create it.

## Final E2E scenarios

The gate runs these 13 scenarios once, in order, against the cold stack:

1. Authenticated placement and settlement.
2. Risk outage with durable PENDING placement and recovery.
3. Wallet lost-response handling with exactly-once debit.
4. Lifecycle-before-placement whole-slip refund.
5. Result-before-placement settlement catch-up.
6. Payout-increase correction.
7. Payout-decrease BLOCKED proof and recovery.
8. Admin candidate approval and rejection using database-time eligibility.
9. Admin revision retry with a scanner-fenced queued receipt.
10. Duplicate, lower, gap, revision-before-base, and late-base projection ordering.
11. Match-result replay invariance after Wallet receipt processing.
12. Partition-2 poison record to partition-2 DLT.
13. Admin audit, Kafka action, downstream Odds, action-ID, and trace correlation.

Scenario fixtures use disjoint canonical UUIDv7 identities. Kafka barriers are record- or
outbox-specific where an empty consumer lag could otherwise pass before an asynchronous append.
Admin correlation scans a bounded window and selects the exact action ID, allowing earlier
best-effort Admin publications to arrive independently.

## Running the release gate

Prerequisites are Git, Docker with Compose v2, OpenSSL, Python 3.12, and a complete JDK 17. The
Docker daemon must have enough capacity for 21 containers and seven application image builds.

From a clean exact commit, with no non-ignored untracked files:

```bash
python3 -B scripts/history_guard.py
python3 -B -m unittest discover -s tests
python3 -I -B scripts/cold_release_gate.py
```

Run the cold command only once for a fixed final state. The CLI refuses tracked changes, untracked
build inputs, executable bytecode caches under `scripts/` or `e2e/`, a second active gate lock,
pre-existing staged JAR generations, unsafe source paths, or reserved build environment overrides.

The archive workflow performs the same ordered checks on the exact GitHub SHA with Java 17 and
Python 3.12. It never checks out external service repositories directly; source materialization is
owned by the locked build step.

## Evidence and cleanup

Successful evidence is retained under:

```text
evidence/cold-gate/<compose-project>/
```

It is ignored by Git, write-once, size-bounded, and checked for every generated secret before the
run can complete. The required inventory is:

- `run.tsv`, the orchestration SHA, project identity, and lock hash;
- `services.lock` and `jars.sha256`, exact source and artifact identities;
- `compose.sha256`, the redacted rendered Compose identity;
- `images.tsv` and `compose-ps.json`, final image, embedded JAR, state, and health evidence;
- `topics.tsv` and `migrations.tsv`, exact Kafka and Flyway inventories;
- `readiness.tsv`, final application readiness and Wallet integrity scan;
- `scenarios.tsv`, the 13 ordered passes;
- `logs/<service>.log`, redacted bounded logs for all 21 services; and
- `cleanup.tsv`, zero remaining scoped containers, networks, volumes, sources, and JAR staging.

On success, cleanup removes the run-owned Compose project and volumes, detached service worktrees,
staged JAR symlink/generation, private keys, runtime directory, and lock. Evidence remains. On a
build, startup, check, or interrupt failure, the same ownership checks drive best-effort cleanup;
the primary error and any log or cleanup errors are preserved together.

Never use broad Docker or filesystem cleanup for this project. If a failed run reports that scoped
resources remain, inspect the project name in its error/evidence and resolve that exact ownership
failure before retrying. If the gate reports an executable cache, remove only generated
`__pycache__`/`.pyc` content under `scripts/` and `e2e/`, then continue with `python3 -B`.
