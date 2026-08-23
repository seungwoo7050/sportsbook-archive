# Full-stack Integration — Wave 4 Handoff

## Gate identity

- Orchestration tip: `dcfc01345377aeccefd2eeb15cb4b9736a669b10`
- Cold project: `sb-gate-dcfc01345377-b5db17f5`
- GitHub Actions run: `32657305364` (`success`, 16m25s)
- Evidence artifact:
  `orchestration-evidence-dcfc01345377aeccefd2eeb15cb4b9736a669b10`
- Evidence artifact SHA-256:
  `214fc834c0e366c9049dc7621a351a2efd7f6763b76c985bcf548421a2ea2fcd`
- Combined Compose SHA-256:
  `03f3ed88a4041548d449bfcf950a965ef4330f5b754bb0e7a7a7efa39d5708a7`
- `services.lock` evidence SHA-256:
  `a87aa711e92577b75e5966256bc3c8e76f593912fe0bf43b416fbe67ba5b43ac`
- Scenario evidence SHA-256:
  `b2ccb6976d722885e736188b4d2f2c92ce3d4282df032e1fd484b18b0d433349`
- Cleanup evidence SHA-256:
  `54acd864d0d3743d440ec674350013eefe88a82638459ec656d69a47874b3333`

Evidence is not tracked by Git and is retained by GitHub Actions for 14 days. It contains only
identities, inventories, results, and redacted service logs; no generated secret or tracked
measurement output is retained.

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
| Shared Protocol | `f9de6bc1e533761ab4bb1454d8d4ab8175cdf001` | `f139c1f1150386570162a00f13573d74806ded4f0aef6c209a5a9b058bb02c6a` |
| Wallet | `c9a05f4d652f24ac97d3e1cd753f69cef2725ff3` | `162eb8b1d1d4703f456830eca238520d3ab1b94ee535ef498fe493652422abda` |
| Risk | `c64f67dbc437a18640dc4984dea4d8194fb5b164` | `451782689fd32f65e3489644533c086aa1e2156fe722daf3fb3c66f92a437f2a` |
| Odds Feed | `574e83d2862f086ae07ff56fd95a8336f78a72da` | `2d73c62d0bbcb6a90b40a026d33043a5cbbb46b76101c11bb31d69d5db548021` |
| Betting | `40f040e2eff9638d7d6ff1983d86584b02cfebbc` | `07b494395c04d657ff014cf3c7529922d4fb45269399629435bad549568abcc8` |
| Gateway | `8248a3233f0fce7ca36a503ee71b7a8a0802d733` | `2ec8d320c22992b8030a23ce4e7ba1568c952c0f4c9acc043f70b9e8a6da39c1` |
| Settlement | `e935873660aad4ceb28788521f7657289f97bc15` | `de939a2861885f3cf156856615014be587efad96c600de5cbcf347b2d88062b3` |
| Admin API | `2fb55910475b31084e6489bf01c34cc970c96874` | `912e24680a1effd1d1e900ae5f3840db830f29ff4d6da272220334fd5d7fceeb` |

Each application image's `/app/app.jar` hash matched its staged release JAR. The fixture publisher
SHA-256 was `3f54f7785a692420592ed7d9dd784c798eb2110405663f1cfd86e574562b22e9`.

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

## Remote verification

GitHub Actions run `32657305364` verified the exact orchestration tip and completed successfully in
16m25s. It passed the 375-commit history guard, all 239 static contract tests, the cold release
gate, all 13 E2E scenarios, evidence redaction, and scoped cleanup. `origin/orchestration` points to
the verified tip. The uploaded artifact digest is
`sha256:214fc834c0e366c9049dc7621a351a2efd7f6763b76c985bcf548421a2ea2fcd`.
