# Full-stack Integration — Wave 4 Handoff

## Gate identity

- Orchestration tip: `e643aa4e025e79a2fda31c71726d02a22fe6eef5`
- Cold project: `sb-gate-e643aa4e025e-3e713f9c`
- Evidence directory:
  `evidence/cold-gate/sb-gate-e643aa4e025e-3e713f9c` in the orchestration worktree
- Combined Compose SHA-256:
  `9cb05fb5ebd70e566dd137598fd884e9d1ca5cefdf8233b4bc0cf7a4069442be`
- `services.lock` evidence SHA-256:
  `a87aa711e92577b75e5966256bc3c8e76f593912fe0bf43b416fbe67ba5b43ac`
- Scenario evidence SHA-256:
  `b2ccb6976d722885e736188b4d2f2c92ce3d4282df032e1fd484b18b0d433349`
- Cleanup evidence SHA-256:
  `54acd864d0d3743d440ec674350013eefe88a82638459ec656d69a47874b3333`

Evidence is ignored and remains local. It contains only identities, inventories, results, and
redacted service logs; no generated secret or tracked measurement output is retained.

## Functional result

All 13 scenarios passed in order:

1. authenticated placement and settlement;
2. Risk outage producing PENDING followed by recovery;
3. Wallet lost-response with exactly-once debit;
4. lifecycle-before-placement refund;
5. result-before-placement settlement;
6. payout-increase correction;
7. payout-decrease BLOCKED recovery;
8. Admin candidate approve and reject;
9. Admin revision retry;
10. duplicate, lower, gap, revision-before-base, and late-base ordering;
11. replay invariance;
12. partition 2 poison record to partition 2 uppercase DLT; and
13. Admin response, audit row, downstream request, and Kafka correlation.

The scenarios crossed the public Gateway authentication boundary and the internal Betting, Risk,
Wallet, Settlement, Admin, PostgreSQL, Redis, and Kafka boundaries. Cold placement accepts either
contractual `201 ACCEPTED` or `202 PENDING`; a PENDING normal placement must recover to the exact
ACCEPTED checkpoints before settlement proceeds.

## Release artifact identity

| Artifact | Locked source | SHA-256 |
| --- | --- | --- |
| Shared Protocol | `f9de6bc1e533761ab4bb1454d8d4ab8175cdf001` | `a98fb2fb99fe67fdb2dddb45a2d937fcc5331f63dd850c44f2538dea863ce6a8` |
| Wallet | `c9a05f4d652f24ac97d3e1cd753f69cef2725ff3` | `728c52dd2b34f7d24133d3dc265508b27d690aa09107486338a04b2d24f94000` |
| Risk | `c64f67dbc437a18640dc4984dea4d8194fb5b164` | `ddb679904b4c6fabbb5050e979d2de0be5cd97da18a88cd4aae4701d1ff6ff3b` |
| Odds Feed | `574e83d2862f086ae07ff56fd95a8336f78a72da` | `f1f1affb701a526c9b603a3de2f814fb16ba5fe84478cef40e7462d4def96d12` |
| Betting | `40f040e2eff9638d7d6ff1983d86584b02cfebbc` | `cdd82bfad8de31983dab689e023b21dc38b61679a2071f550ff923aaa2865551` |
| Gateway | `8248a3233f0fce7ca36a503ee71b7a8a0802d733` | `ee0ca42614eb4b38a048f9ca0cda3470a61929aa00df1c5af40c19fac4a8cfed` |
| Settlement | `e935873660aad4ceb28788521f7657289f97bc15` | `3962323215dbe02eaef66368e8ab81eaf5f0a9b3618585d03adc0d97cb5fa061` |
| Admin API | `2fb55910475b31084e6489bf01c34cc970c96874` | `fae5995fdd7939c32cde3944d05a1b3d891ded3acf724c98ad41f11b63ce7b6a` |

Each application image's `/app/app.jar` hash matched its staged release JAR. The fixture publisher
SHA-256 was `080f2d629e3098301f61337612a28fbf3564e6e6b35ea76f9b6060370c0ba458`.

## Infrastructure evidence

- All 14 source topics and nine uppercase DLT topics matched three partitions and RF 1.
- Every DLT reported retention `604800000` ms.
- Wallet V1–V4, Betting V1–V10, Settlement V1/V3–V10, and Admin V1–V2 were present with successful
  Flyway rows and the locked checksums.
- Wallet, Risk, Odds, Betting, Gateway, Settlement, and Admin health/readiness endpoints were UP.
- Wallet integrity reported total drift 0, scan failures 0, and a completed first scan.
- Cleanup reported zero containers, networks, volumes, materialized sources, JAR links, and JAR
  generations owned by the cold project.

## Minimal upstream corrections

Two reproducible release defects were corrected without reopening completed Wave 0–2 scope:

- Settlement tip `e935873660aad4ceb28788521f7657289f97bc15` adds only executable Spring Boot JAR packaging and
  its adjacent packaging test. The prior locked artifact was a plain non-executable JAR.
- Betting tip `40f040e2eff9638d7d6ff1983d86584b02cfebbc` locks the root bet row before loading its legs and adds
  the adjacent repository contract test. This removes Hibernate follow-on-locking optimistic races
  between HTTP recovery and Wallet Kafka events.

Both corrected histories remain linear with their original release and final documentation
positions. No prior handoff was rewritten; this Wave 4 report supersedes only their release SHA for
full-stack materialization.

## Push boundary

Local work is complete. Before any push, `origin/main`, `origin/admin-api`, and
`origin/orchestration` must be queried again. The exact local/remote SHA and whether each update is
fast-forward, new-branch, or forced must be briefed, and no push may occur until approval is given.
