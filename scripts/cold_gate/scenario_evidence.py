from __future__ import annotations

from collections.abc import Iterable

from e2e.scenarios import SCENARIOS
from scripts.cold_gate.evidence import EvidenceStore


EXPECTED_SCENARIOS = (
    "authenticated-placement-and-settlement",
    "risk-outage-pending-recovery",
    "wallet-lost-response-exactly-once",
    "lifecycle-before-placement-refund",
    "result-before-placement-settlement",
    "payout-increase-correction",
    "payout-decrease-blocked-recovery",
    "admin-candidate-approve-reject",
    "admin-revision-retry",
    "revision-ordering-projection",
    "replay-invariance",
    "partition-two-poison-dlt",
    "admin-audit-downstream-correlation",
)


class ScenarioEvidence:
    def __init__(self, store: EvidenceStore) -> None:
        self.store = store

    def capture(self, passed: Iterable[str]) -> None:
        actual = tuple(passed)
        if (
            tuple(module.NAME for module in SCENARIOS) != EXPECTED_SCENARIOS
            or len(set(EXPECTED_SCENARIOS)) != 13
            or actual != EXPECTED_SCENARIOS
        ):
            raise RuntimeError("E2E PASS inventory is incomplete or out of order")
        rows = ["scenario\tresult"]
        rows.extend(f"{name}\tPASS" for name in actual)
        self.store.write("scenarios.tsv", "\n".join(rows) + "\n")
