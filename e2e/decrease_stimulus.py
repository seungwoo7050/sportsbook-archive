from __future__ import annotations

import dataclasses
import time

from e2e.assertions import wait_fields
from e2e.blocked_correction import wait_blocked_decrease
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime
from e2e.terminal import wait_base_settlement


@dataclasses.dataclass(frozen=True)
class BlockedCorrection:
    fixture: ScenarioIds
    bet_id: str
    revision_id: str


def create_blocked_decrease(runtime: E2eRuntime, number: int) -> BlockedCorrection:
    fixture = ScenarioIds.create(number)
    runtime.seed(fixture)
    placement = runtime.bets.place(fixture, runtime.user_token(fixture))
    if placement.http_status != 201:
        raise RuntimeError("blocked correction placement was not accepted")
    wait_fields(
        "blocked correction placement projection",
        lambda: runtime.base.settlement(placement.bet_id),
        {"status": "PENDING"},
        terminal={"status": frozenset({"SETTLED", "VOIDED"})},
    )
    base_time = int(time.time() * 1000) - 10_000
    runtime.fixtures.publish("MatchResult", fixture.match_result("WON", base_time))
    wait_base_settlement(runtime, fixture, placement.bet_id, "WON", 20_000, 110_000)
    runtime.wallet_api.transfer(
        fixture,
        "withdraw",
        110_000,
        f"e2e-drain-{number:02d}",
    )
    runtime.fixtures.publish("MatchResult", fixture.match_result("LOST", base_time + 1_000))
    blocked = wait_blocked_decrease(runtime, placement.bet_id)
    return BlockedCorrection(fixture, placement.bet_id, str(blocked["revision_id"]))
