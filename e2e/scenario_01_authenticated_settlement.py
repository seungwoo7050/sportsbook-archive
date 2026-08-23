from __future__ import annotations

import time

from e2e.assertions import require_fields, wait_fields
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime


NAME = "authenticated-placement-and-settlement"


def run(runtime: E2eRuntime) -> None:
    fixture = ScenarioIds.create(1)
    runtime.seed(fixture)
    token = runtime.user_token(fixture)
    placement = runtime.bets.place(fixture, token)
    require_fields(
        placement.__dict__,
        {"http_status": 201, "status": "ACCEPTED"},
        "authenticated placement",
    )
    wait_fields(
        "Settlement placement projection",
        lambda: runtime.base.settlement(placement.bet_id),
        {"status": "PENDING", "revision_number": "0"},
        terminal={"status": frozenset({"SETTLED", "VOIDED"})},
    )

    runtime.fixtures.publish(
        "MatchResult",
        fixture.match_result("WON", int(time.time() * 1000) - 5_000),
    )
    wait_fields(
        "Settlement base resolution",
        lambda: runtime.base.settlement(placement.bet_id),
        {
            "status": "SETTLED",
            "result": "WON",
            "payout": "20000",
            "currency": "KRW",
            "revision_number": "0",
        },
        terminal={"status": frozenset({"VOIDED"})},
    )
    wait_fields(
        "Betting base resolution",
        lambda: runtime.base.betting(placement.bet_id),
        {
            "status": "SETTLED",
            "placement_phase": "RISK_COMMITTED",
            "risk_committed": "1",
            "wallet_confirmed": "1",
            "result": "WON",
            "payout": "20000",
            "currency": "KRW",
            "revision_number": "0",
        },
        terminal={"status": frozenset({"REJECTED", "VOIDED"})},
    )
    wait_fields(
        "Wallet base resolution",
        lambda: runtime.base.wallet(fixture.user),
        {"available": "110000", "locked": "0", "debt": "0", "frozen": "0"},
    )
    outbox = wait_fields(
        "Settlement event publication",
        lambda: runtime.placements.settlement_outbox(fixture.event, "BetSettled"),
        {"event_count": "1", "topic": "bet.settled.v1", "published": "1"},
    )
    require_fields(outbox, {"event_count": "1"}, "Settlement outbox")

    queried = runtime.bets.get(placement.bet_id, token)
    resolution = queried.get("resolution")
    if not isinstance(resolution, dict):
        raise RuntimeError("public settled bet has no resolution")
    require_fields(
        resolution,
        {
            "settlementResult": "WON",
            "settledPayout": {"amount": 20_000, "currency": "KRW"},
            "resolutionEventId": fixture.event,
            "resolutionRevisionNumber": 0,
        },
        "public settled resolution",
    )
