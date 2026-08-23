from __future__ import annotations

from e2e.assertions import require_fields, wait_fields
from e2e.revision_ordering_stimulus import stage_revision_ordering, wait_gap
from e2e.runtime import E2eRuntime
from scripts.cold_gate.polling import poll_until


NAME = "revision-ordering-projection"


def run(runtime: E2eRuntime) -> None:
    state = stage_revision_ordering(runtime)
    runtime.fixtures.publish(
        "MatchResult", state.fixture.match_result("WON", state.source_time)
    )
    wait_fields(
        "late base Settlement resolution",
        lambda: runtime.base.settlement(state.bet_id),
        {
            "status": "SETTLED",
            "result": "WON",
            "payout": "20000",
            "revision_number": "0",
        },
        terminal={"status": frozenset({"VOIDED"})},
    )
    wait_fields(
        "late base Wallet settlement",
        lambda: runtime.base.wallet(state.fixture.user),
        {"available": "110000", "locked": "0", "debt": "0", "frozen": "0"},
    )
    wait_fields(
        "late base Settlement outbox",
        lambda: runtime.placements.settlement_outbox(state.fixture.event, "BetSettled"),
        {"event_count": "1", "topic": "bet.settled.v1", "published": "1"},
    )
    poll_until(
        "late base Betting consumption",
        lambda: runtime.kafka.topic_lag("betting-resolution", "bet.settled.v1"),
        lambda lag: lag == 0,
        timeout=60,
        interval=0.5,
    )
    require_fields(
        runtime.base.betting(state.bet_id) or {},
        {
            "status": "SETTLED",
            "result": "WON",
            "payout": "20000",
            "revision_number": "2",
            "revision_id": state.revision_id,
            "payload_sha256": state.payload_sha256,
        },
        "late base ordering projection",
    )
    wait_gap(runtime, state.gap_expected)
