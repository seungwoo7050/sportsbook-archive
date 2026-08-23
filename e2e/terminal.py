from __future__ import annotations

from e2e.assertions import wait_fields
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime


def wait_base_settlement(
    runtime: E2eRuntime,
    fixture: ScenarioIds,
    bet_id: str,
    outcome: str,
    payout: int,
    available: int,
) -> None:
    wait_fields(
        f"Settlement {outcome} base resolution",
        lambda: runtime.base.settlement(bet_id),
        {
            "status": "SETTLED",
            "result": outcome,
            "payout": str(payout),
            "currency": "KRW",
            "revision_number": "0",
        },
        terminal={"status": frozenset({"VOIDED"})},
    )
    wait_fields(
        f"Betting {outcome} base resolution",
        lambda: runtime.base.betting(bet_id),
        {
            "status": "SETTLED",
            "placement_phase": "RISK_COMMITTED",
            "result": outcome,
            "payout": str(payout),
            "currency": "KRW",
            "revision_number": "0",
        },
        terminal={"status": frozenset({"REJECTED", "VOIDED"})},
    )
    wait_fields(
        f"Wallet {outcome} base resolution",
        lambda: runtime.base.wallet(fixture.user),
        {
            "available": str(available),
            "locked": "0",
            "debt": "0",
            "frozen": "0",
        },
    )
