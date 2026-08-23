from __future__ import annotations

import time

from e2e.assertions import wait_fields
from e2e.kafka_barrier import wait_consumed
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime
from e2e.terminal import wait_base_settlement


NAME = "replay-invariance"


def run(runtime: E2eRuntime) -> None:
    fixture = ScenarioIds.create(11)
    runtime.seed(fixture)
    placement = runtime.bets.place(fixture, runtime.user_token(fixture))
    wait_fields(
        "replay placement projection",
        lambda: runtime.base.settlement(placement.bet_id),
        {"status": "PENDING"},
        terminal={"status": frozenset({"SETTLED", "VOIDED"})},
    )
    payload = fixture.match_result("WON", int(time.time() * 1000) - 5_000)
    original = runtime.fixtures.publish("MatchResult", payload)
    wait_consumed(runtime, "settlement-service", original)
    wait_base_settlement(runtime, fixture, placement.bet_id, "WON", 20_000, 110_000)
    before = runtime.replays.snapshot(fixture, placement.bet_id)

    for _attempt in range(3):
        replay = runtime.fixtures.publish("MatchResult", payload)
        if replay.sha256 != original.sha256:
            raise RuntimeError("replayed MatchResult bytes drifted")
        wait_consumed(runtime, "settlement-service", replay)
    after = runtime.replays.snapshot(fixture, placement.bet_id)
    if after != before:
        raise RuntimeError("replayed MatchResult changed durable projections")

    candidates = runtime.corrections.candidates(fixture.event)
    if len(candidates) != 1 or candidates[0]["state"] != "ACCEPTED":
        raise RuntimeError("replayed MatchResult changed candidate identity")
    if runtime.corrections.revision(placement.bet_id) is not None:
        raise RuntimeError("replayed MatchResult created a correction")
