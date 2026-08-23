from __future__ import annotations

import time

from e2e.assertions import require_fields, wait_fields
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime
from e2e.terminal import wait_base_settlement


NAME = "payout-increase-correction"


def run(runtime: E2eRuntime) -> None:
    fixture = ScenarioIds.create(6)
    runtime.seed(fixture)
    placement = runtime.bets.place(fixture, runtime.user_token(fixture))
    require_fields(placement.__dict__, {"http_status": 201}, "increase placement")
    wait_fields(
        "increase placement projection",
        lambda: runtime.base.settlement(placement.bet_id),
        {"status": "PENDING"},
        terminal={"status": frozenset({"SETTLED", "VOIDED"})},
    )
    base_time = int(time.time() * 1000) - 10_000
    runtime.fixtures.publish("MatchResult", fixture.match_result("LOST", base_time))
    wait_base_settlement(runtime, fixture, placement.bet_id, "LOST", 0, 90_000)

    runtime.fixtures.publish("MatchResult", fixture.match_result("WON", base_time + 1_000))
    revision = wait_fields(
        "payout increase revision",
        lambda: runtime.corrections.revision(placement.bet_id),
        {
            "state": "APPLIED",
            "attempt_count": "1",
            "previous_result": "LOST",
            "new_result": "WON",
            "previous_payout": "0",
            "new_payout": "20000",
            "wallet_status": "APPLIED",
            "queue_sequence": "",
            "operation_group": "1",
            "applied": "1",
        },
        terminal={"state": frozenset({"REJECTED", "EXHAUSTED"})},
        timeout=90,
    )
    revision_id = str(revision["revision_id"])
    require_fields(
        runtime.corrections.wallet_adjustment(revision_id) or {},
        {
            "status": "APPLIED",
            "delta": "20000",
            "queue_sequence": "",
            "operation_group": "1",
            "applied": "1",
            "retry_scheduled": "0",
        },
        "payout increase Wallet adjustment",
    )
    wait_fields(
        "payout increase Betting projection",
        lambda: runtime.base.betting(placement.bet_id),
        {
            "status": "SETTLED",
            "result": "WON",
            "payout": "20000",
            "revision_number": "1",
            "revision_id": revision_id,
        },
    )
    wait_fields(
        "payout increase Wallet balance",
        lambda: runtime.base.wallet(fixture.user),
        {"available": "110000", "locked": "0", "debt": "0", "frozen": "0"},
    )
    candidates = runtime.corrections.candidates(fixture.event)
    if len(candidates) != 2:
        raise RuntimeError("payout increase candidate inventory drifted")
    require_fields(candidates[0], {"state": "SUPERSEDED", "decision_reason": "AUTO_CORRECTION"}, "base candidate")
    require_fields(candidates[1], {"state": "ACCEPTED", "decision_reason": "AUTO_CORRECTION"}, "corrected candidate")
    wait_fields(
        "payout increase event publication",
        lambda: runtime.placements.settlement_outbox(placement.bet_id, "BetResolutionRevised"),
        {"event_count": "1", "topic": "bet.resolution.revised.v1", "published": "1"},
    )
