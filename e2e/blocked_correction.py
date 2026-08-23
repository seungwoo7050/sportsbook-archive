from __future__ import annotations

from collections.abc import Mapping

from e2e.assertions import require_fields, wait_fields
from e2e.runtime import E2eRuntime
from scripts.cold_gate.polling import poll_until


def wait_blocked_decrease(runtime: E2eRuntime, bet_id: str) -> Mapping[str, object]:
    def blocked(row: dict[str, str] | None) -> bool:
        if row is None:
            return False
        if row.get("state") in {"REJECTED", "EXHAUSTED"}:
            raise RuntimeError(f"payout decrease entered terminal state {row['state']}")
        attempts = int(row.get("attempt_count", "0"))
        if attempts >= 12:
            raise RuntimeError("payout decrease exhausted before recovery deposit")
        if row.get("state") != "BLOCKED":
            return False
        require_fields(
            row,
            {
                "previous_result": "WON",
                "new_result": "LOST",
                "previous_payout": "20000",
                "new_payout": "0",
                "wallet_status": "BLOCKED",
                "queue_sequence": "1",
                "operation_group": "0",
                "applied": "0",
            },
            "blocked payout decrease",
        )
        if attempts < 1:
            raise RuntimeError("blocked payout decrease has no attempt proof")
        return True

    return poll_until(
        "blocked payout decrease",
        lambda: runtime.corrections.revision(bet_id),
        blocked,
        timeout=15,
        interval=0.1,
    )


def wait_applied_decrease(runtime: E2eRuntime, bet_id: str) -> Mapping[str, object]:
    return wait_fields(
        "applied payout decrease",
        lambda: runtime.corrections.revision(bet_id),
        {
            "state": "APPLIED",
            "previous_result": "WON",
            "new_result": "LOST",
            "previous_payout": "20000",
            "new_payout": "0",
            "wallet_status": "APPLIED",
            "queue_sequence": "1",
            "operation_group": "1",
            "applied": "1",
        },
        terminal={"state": frozenset({"REJECTED", "EXHAUSTED"})},
        timeout=90,
    )
