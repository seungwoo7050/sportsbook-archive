# Orchestration — Wave 4 Handoff

## Purpose

This report fixes the complete Sportsbook 1.0 runtime contract. It records the exact service
releases, infrastructure inventory, trust boundaries, startup order, release identity, and final
cold-stack result that future work must preserve.

## Final state

- Released local branch: `orchestration`
- Final tip: `e643aa4e025e79a2fda31c71726d02a22fe6eef5`
- Release commit: `bf792c98e784d6306fb0c2dcccd1f7ab20152c61`
- Version: 1.0.0
- History: 372 commits, one README-only root, 371 single-parent commits, no merges
- Final evidence directory:
  `evidence/cold-gate/sb-gate-e643aa4e025e-3e713f9c` on the local ignored filesystem

The local `orchestration` and `rebuild-orchestration-final` refs point to the final tip. No tag,
remote push, or tracked verification output was created.

## Locked service releases

| Logical service | Branch | Final tip |
| --- | --- | --- |
| Shared Protocol | `shared-protocol` | `f9de6bc1e533761ab4bb1454d8d4ab8175cdf001` |
| Wallet | `wallet-service` | `c9a05f4d652f24ac97d3e1cd753f69cef2725ff3` |
| Risk | `risk-service` | `c64f67dbc437a18640dc4984dea4d8194fb5b164` |
| Odds Feed | `odds-feed-service` | `574e83d2862f086ae07ff56fd95a8336f78a72da` |
| Betting | `betting-service` | `40f040e2eff9638d7d6ff1983d86584b02cfebbc` |
| Gateway | `gateway` | `8248a3233f0fce7ca36a503ee71b7a8a0802d733` |
| Settlement | `settlement-service` | `e935873660aad4ceb28788521f7657289f97bc15` |
| Admin API | `admin-api` | `2fb55910475b31084e6489bf01c34cc970c96874` |

`services.lock` is authoritative. Materialization accepts only full local commit objects whose
branch refs equal the lock, uses detached worktrees under one owned runtime directory, installs the
exact Shared Protocol artifact into an isolated Maven repository, and atomically stages the seven
executable service JARs plus the fixture publisher.

## Runtime topology

Startup is dependency ordered:

```text
PostgreSQL + Kafka + Redis Risk/Odds/Wallet/Gateway
-> secret-preflight + topic-init
-> Wallet + Risk + Odds
-> Betting
-> Gateway
-> consumer-assignment
-> Settlement
-> Admin
```

PostgreSQL bootstraps the `wallet`, `betting`, `settlement`, and `admin` databases. Kafka runs in
persistent KRaft mode with auto-create disabled. Four isolated Redis instances use AOF and
`noeviction`. Gateway is the only application with a loopback publication and is fixed to one
replica; all services share one internal backend network.

The topic manifest owns 14 source topics and nine uppercase `.DLT` topics. Every topic has three
partitions and replication factor one. DLT retention is at least seven days. Topic initialization
is idempotent and rejects existing broker metadata that differs from the manifest.

Migration inventory is fixed to Wallet V1–V4, Betting V1–V10, Settlement V1 and V3–V10, and Admin
V1–V2. No released migration was changed.

## Authentication wiring

The stack generates eleven distinct service/platform secrets of at least 32 characters and fails
before application startup when the inventory, length, or uniqueness contract is violated. Exact
caller/callee pairs are preserved for Gateway to Betting/Wallet, Betting to Risk/Wallet,
Settlement to Wallet, Admin to Wallet/Risk/Odds/Settlement, and the Wallet/Risk platform callers.

JWT verification receives an inline RSA public key. Wallet outbox publication is explicitly
enabled. Settlement uses only its canonical datasource, Kafka, and Wallet base URL variables.
Evidence and logs are scanned against every generated secret before being retained.

## History quality

The final history preserves the Wave 0–3 policy:

- production and adjacent tests are separate commits;
- handwritten production commits stay within two files and the 100-line review limit;
- commits are linear and contain empty conventional-message bodies;
- no fixup, squash, diary, changelog, provenance, reconstruction, or tracked evidence material is
  present;
- the penultimate commit is the single release commit; and
- the final commit is the single project documentation commit.

The repository history guard traversed all 372 commits at the final docs tip.

## Verification completed

One cold release gate at the exact final tip completed successfully. It rebuilt release JARs from
the locked sources with tests skipped, built the runtime images, started an empty stack, verified
topic and migration inventories, ran all 13 functional E2E scenarios, captured runtime/JAR
identity and readiness, scanned retained evidence/logs for secrets, and removed every owned Docker
and materialization resource.

The combined Compose configuration SHA-256 is
`9cb05fb5ebd70e566dd137598fd884e9d1ca5cefdf8233b4bc0cf7a4069442be`.
The exact scenario and artifact evidence is recorded in `handoffs/wave4/integration.md`.
