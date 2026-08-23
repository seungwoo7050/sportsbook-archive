from __future__ import annotations

import time

from e2e.assertions import require_fields, wait_fields
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime


NAME = "risk-outage-pending-recovery"


def run(runtime: E2eRuntime) -> None:
    fixture = ScenarioIds.create(2)
    runtime.seed(fixture)
    token = runtime.user_token(fixture)
    placement = None
    try:
        disabled = runtime.chaos.set_enabled("betting_to_risk", False)
        require_fields(disabled, {"enabled": False}, "Risk proxy disable")
        placement = runtime.bets.place(fixture, token)
        require_fields(
            placement.__dict__,
            {"http_status": 202, "status": "PENDING"},
            "Risk-outage placement",
        )
        require_fields(
            runtime.base.betting(placement.bet_id) or {},
            {
                "status": "PENDING",
                "placement_phase": "CREATED",
                "risk_committed": "0",
                "wallet_confirmed": "0",
            },
            "Risk-outage Betting checkpoint",
        )
        require_fields(
            runtime.base.wallet(fixture.user) or {},
            {"available": "100000", "locked": "0"},
            "Risk-outage Wallet state",
        )
        require_fields(
            runtime.placements.wallet_debit(placement.bet_id),
            {"operation_count": "0", "ledger_count": "0", "outbox_count": "0"},
            "Risk-outage Wallet effects",
        )
        if runtime.risk.scalar("EXISTS", f"risk:reservation:{placement.bet_id}") != "0":
            raise RuntimeError("Risk outage created a reservation")
    finally:
        enabled = runtime.chaos.set_enabled("betting_to_risk", True)
        require_fields(enabled, {"enabled": True}, "Risk proxy restoration")
    if placement is None:
        raise RuntimeError("Risk-outage placement did not return a bet")

    wait_fields(
        "Risk-outage placement recovery",
        lambda: runtime.base.betting(placement.bet_id),
        {
            "status": "ACCEPTED",
            "placement_phase": "RISK_COMMITTED",
            "risk_committed": "1",
            "wallet_confirmed": "1",
        },
        terminal={"status": frozenset({"REJECTED", "VOIDED", "SETTLED"})},
    )
    wait_fields(
        "Risk-outage Wallet debit",
        lambda: runtime.base.wallet(fixture.user),
        {"available": "90000", "locked": "10000"},
    )
    reservation = f"risk:reservation:{placement.bet_id}"
    expected = {
        "state": "COMMITTED",
        "userId": fixture.user,
        "betId": placement.bet_id,
        "stake": "10000",
        "currency": "KRW",
        "selectionCount": "1",
        "selections": fixture.selection,
    }
    for field, value in expected.items():
        if runtime.risk.scalar("HGET", reservation, field) != value:
            raise RuntimeError(f"Risk reservation {field} drifted")
    if runtime.risk.scalar("EXISTS", f"risk:reservations:user:{{{fixture.user}}}:bets") != "0":
        raise RuntimeError("Risk retained an active reservation footprint")

    runtime.fixtures.publish(
        "MatchResult", fixture.match_result("LOST", int(time.time() * 1000) - 5_000)
    )
    wait_fields(
        "Risk-outage terminal cleanup",
        lambda: runtime.base.wallet(fixture.user),
        {"available": "90000", "locked": "0", "debt": "0", "frozen": "0"},
    )
