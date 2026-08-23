from __future__ import annotations

import time

from e2e.assertions import require_fields, wait_fields
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime
from scripts.cold_gate.polling import poll_until


NAME = "lifecycle-before-placement-refund"


def run(runtime: E2eRuntime) -> None:
    fixture = ScenarioIds.create(4)
    runtime.seed(fixture)
    occurred_at = int(time.time() * 1000) - 5_000
    runtime.fixtures.publish("EventLifecycle", fixture.cancelled(occurred_at))
    poll_until(
        "cancelled event tombstone",
        lambda: runtime.base.tombstone(fixture.event),
        lambda status: status == "CANCELLED",
        timeout=60,
        interval=0.25,
    )

    token = runtime.user_token(fixture)
    placement = runtime.bets.place(fixture, token)
    require_fields(
        placement.__dict__,
        {"http_status": 201, "status": "ACCEPTED"},
        "lifecycle-first placement",
    )
    wait_fields(
        "lifecycle-first Settlement void",
        lambda: runtime.base.settlement(placement.bet_id),
        {
            "status": "VOIDED",
            "result": "VOID",
            "payout": "10000",
            "currency": "KRW",
            "revision_number": "0",
        },
        terminal={"status": frozenset({"SETTLED"})},
    )
    wait_fields(
        "lifecycle-first Betting void",
        lambda: runtime.base.betting(placement.bet_id),
        {
            "status": "VOIDED",
            "void_reason": "EVENT_CANCELLED",
            "revision_number": "0",
        },
        terminal={"status": frozenset({"REJECTED", "SETTLED"})},
    )
    wait_fields(
        "lifecycle-first Wallet refund",
        lambda: runtime.base.wallet(fixture.user),
        {"available": "100000", "locked": "0", "debt": "0", "frozen": "0"},
    )
    require_fields(
        runtime.placements.wallet_void_refund(placement.bet_id),
        {
            "operation_count": "1",
            "caller": "SETTLEMENT",
            "kind": "BET_REFUND",
            "status": "SUCCEEDED",
        },
        "void refund operation",
    )
    wait_fields(
        "BetVoided publication",
        lambda: runtime.placements.settlement_outbox(fixture.event, "BetVoided"),
        {"event_count": "1", "topic": "bet.voided.v1", "published": "1"},
    )
    queried = runtime.bets.get(placement.bet_id, token)
    resolution = queried.get("resolution")
    if not isinstance(resolution, dict):
        raise RuntimeError("public voided bet has no resolution")
    require_fields(
        resolution,
        {
            "voidReason": "EVENT_CANCELLED",
            "resolutionEventId": fixture.event,
            "resolutionRevisionNumber": 0,
        },
        "public void resolution",
    )
    if "settlementResult" in resolution or "settledPayout" in resolution:
        raise RuntimeError("public void resolution exposed settlement fields")
