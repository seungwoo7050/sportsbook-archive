from __future__ import annotations

from collections.abc import Callable

from e2e import (
    scenario_01_authenticated_settlement,
    scenario_02_risk_recovery,
    scenario_03_wallet_lost_response,
    scenario_04_lifecycle_refund,
    scenario_05_result_first,
    scenario_06_payout_increase,
    scenario_07_blocked_recovery,
    scenario_08_admin_candidates,
    scenario_09_admin_revision_retry,
    scenario_10_revision_ordering,
    scenario_11_replay_invariance,
    scenario_12_partition_dlt,
    scenario_13_admin_correlation,
)
from e2e.runtime import E2eRuntime


SCENARIOS = (
    scenario_01_authenticated_settlement,
    scenario_02_risk_recovery,
    scenario_03_wallet_lost_response,
    scenario_04_lifecycle_refund,
    scenario_05_result_first,
    scenario_06_payout_increase,
    scenario_07_blocked_recovery,
    scenario_08_admin_candidates,
    scenario_09_admin_revision_retry,
    scenario_10_revision_ordering,
    scenario_11_replay_invariance,
    scenario_12_partition_dlt,
    scenario_13_admin_correlation,
)


def run_all(
    runtime: E2eRuntime,
    completed: Callable[[str], None] | None = None,
) -> tuple[str, ...]:
    names = tuple(module.NAME for module in SCENARIOS)
    if len(names) != 13 or len(set(names)) != 13:
        raise RuntimeError("E2E scenario inventory is invalid")
    runtime.wait_for_settlement_assignments()
    passed = []
    for module in SCENARIOS:
        module.run(runtime)
        passed.append(module.NAME)
        if completed is not None:
            completed(module.NAME)
    return tuple(passed)
