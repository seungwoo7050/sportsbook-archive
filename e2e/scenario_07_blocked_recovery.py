from __future__ import annotations

import time

from e2e.assertions import require_fields, wait_fields
from e2e.blocked_correction import wait_applied_decrease, wait_blocked_decrease
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime
from e2e.terminal import wait_base_settlement


NAME = "payout-decrease-blocked-recovery"


def run(runtime: E2eRuntime) -> None:
    fixture = ScenarioIds.create(7)
    runtime.seed(fixture)
    placement = runtime.bets.place(fixture, runtime.user_token(fixture))
    require_fields(placement.__dict__, {"http_status": 201}, "decrease placement")
    wait_fields(
        "decrease placement projection",
        lambda: runtime.base.settlement(placement.bet_id),
        {"status": "PENDING"},
        terminal={"status": frozenset({"SETTLED", "VOIDED"})},
    )
    base_time = int(time.time() * 1000) - 10_000
    runtime.fixtures.publish("MatchResult", fixture.match_result("WON", base_time))
    wait_base_settlement(runtime, fixture, placement.bet_id, "WON", 20_000, 110_000)

    runtime.wallet_api.transfer(fixture, "withdraw", 110_000, "e2e-withdraw-07")
    require_fields(
        runtime.base.wallet(fixture.user) or {},
        {"available": "0", "locked": "0", "debt": "0", "frozen": "0"},
        "drained payout balance",
    )
    runtime.fixtures.publish("MatchResult", fixture.match_result("LOST", base_time + 1_000))
    blocked = wait_blocked_decrease(runtime, placement.bet_id)
    revision_id = str(blocked["revision_id"])
    require_fields(
        runtime.base.wallet(fixture.user) or {},
        {"available": "0", "locked": "0", "debt": "20000", "frozen": "1"},
        "blocked Wallet account",
    )
    require_fields(
        runtime.corrections.wallet_adjustment(revision_id) or {},
        {
            "status": "BLOCKED",
            "delta": "-20000",
            "queue_sequence": "1",
            "operation_group": "0",
            "applied": "0",
            "retry_scheduled": "1",
        },
        "blocked Wallet adjustment",
    )

    runtime.wallet_api.transfer(
        fixture, "deposit", 20_000, "e2e-recovery-deposit-07"
    )
    applied = wait_applied_decrease(runtime, placement.bet_id)
    if applied["revision_id"] != revision_id:
        raise RuntimeError("recovery changed the correction identity")
    require_fields(
        runtime.corrections.wallet_adjustment(revision_id) or {},
        {
            "status": "APPLIED",
            "delta": "-20000",
            "queue_sequence": "1",
            "operation_group": "1",
            "applied": "1",
            "retry_scheduled": "0",
        },
        "recovered Wallet adjustment",
    )
    wait_fields(
        "recovered Settlement projection",
        lambda: runtime.base.settlement(placement.bet_id),
        {"status": "SETTLED", "result": "LOST", "payout": "0", "revision_number": "1"},
    )
    wait_fields(
        "recovered Betting projection",
        lambda: runtime.base.betting(placement.bet_id),
        {
            "status": "SETTLED",
            "result": "LOST",
            "payout": "0",
            "revision_number": "1",
            "revision_id": revision_id,
        },
    )
    wait_fields(
        "recovered Wallet account",
        lambda: runtime.base.wallet(fixture.user),
        {"available": "0", "locked": "0", "debt": "0", "frozen": "0"},
    )
