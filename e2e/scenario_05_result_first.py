from __future__ import annotations

import time

from e2e.assertions import require_fields
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime
from e2e.terminal import wait_base_settlement
from scripts.cold_gate.polling import poll_until


NAME = "result-before-placement-settlement"


def run(runtime: E2eRuntime) -> None:
    fixture = ScenarioIds.create(5)
    runtime.seed(fixture)
    runtime.fixtures.publish(
        "MatchResult", fixture.match_result("WON", int(time.time() * 1000) - 5_000)
    )
    candidate = poll_until(
        "result-first accepted candidate",
        lambda: runtime.corrections.accepted_candidate(fixture.event),
        lambda row: row is not None,
        timeout=60,
        interval=0.25,
    )
    require_fields(
        candidate,
        {"state": "ACCEPTED", "decision_reason": "FIRST_RESULT", "accepted": "1"},
        "result-first candidate",
    )

    token = runtime.user_token(fixture)
    placement = runtime.bets.place(fixture, token)
    require_fields(
        placement.__dict__,
        {"http_status": 201, "status": "ACCEPTED"},
        "result-first placement",
    )
    wait_base_settlement(runtime, fixture, placement.bet_id, "WON", 20_000, 110_000)
    candidates = runtime.corrections.candidates(fixture.event)
    if len(candidates) != 1:
        raise RuntimeError("result-first flow created duplicate candidates")
    require_fields(
        candidates[0],
        {"state": "ACCEPTED", "decision_reason": "FIRST_RESULT", "decided": "1"},
        "result-first immutable candidate",
    )
