from __future__ import annotations

import time

from e2e.assertions import require_fields, wait_fields
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime


NAME = "wallet-lost-response-exactly-once"


def run(runtime: E2eRuntime) -> None:
    fixture = ScenarioIds.create(3)
    runtime.seed(fixture)
    token = runtime.user_token(fixture)
    placement = None
    toxic_created = False
    try:
        toxic = runtime.chaos.add_wallet_response_timeout()
        toxic_created = True
        require_fields(
            toxic,
            {"name": "e2e_wallet_response_timeout", "type": "timeout", "stream": "downstream"},
            "Wallet response toxic",
        )
        placement = runtime.bets.place(fixture, token)
        require_fields(
            placement.__dict__,
            {"http_status": 202, "status": "PENDING"},
            "lost-response placement",
        )
        wait_fields(
            "lost-response Wallet commit",
            lambda: runtime.placements.wallet_debit(placement.bet_id),
            {
                "operation_count": "1",
                "caller": "BETTING",
                "kind": "BET_DEBIT",
                "status": "SUCCEEDED",
                "operation_group": "1",
                "ledger_count": "2",
                "outbox_count": "1",
                "outbox_topic": "wallet.debited.v1",
                "outbox_schema": "WalletDebited",
            },
        )
        require_fields(
            runtime.base.betting(placement.bet_id) or {},
            {
                "status": "PENDING",
                "placement_phase": "RISK_RESERVED",
                "risk_committed": "0",
                "wallet_confirmed": "0",
            },
            "lost-response Betting checkpoint",
        )
        require_fields(
            runtime.base.wallet(fixture.user) or {},
            {"available": "90000", "locked": "10000"},
            "lost-response Wallet state",
        )
        if runtime.risk.scalar("HGET", f"risk:reservation:{placement.bet_id}", "state") != "RESERVED":
            raise RuntimeError("Risk reservation advanced before Wallet proof recovery")
        require_fields(
            runtime.placements.betting_outbox(fixture.user, placement.bet_id),
            {"event_count": "0"},
            "pre-recovery Betting outbox",
        )
    finally:
        if toxic_created:
            runtime.chaos.remove_wallet_response_timeout()
    if placement is None:
        raise RuntimeError("lost-response placement did not return a bet")

    wait_fields(
        "lost-response GET-first recovery",
        lambda: runtime.base.betting(placement.bet_id),
        {"status": "ACCEPTED", "placement_phase": "RISK_COMMITTED"},
        terminal={"status": frozenset({"REJECTED", "VOIDED", "SETTLED"})},
    )
    require_fields(
        runtime.placements.wallet_debit(placement.bet_id),
        {"operation_count": "1", "ledger_count": "2", "outbox_count": "1"},
        "recovered Wallet debit",
    )
    wait_fields(
        "recovered Betting outbox",
        lambda: runtime.placements.betting_outbox(fixture.user, placement.bet_id),
        {"event_count": "1", "topic": "bet.placed.v1", "schema": "BetPlacedRequested"},
    )
    runtime.fixtures.publish(
        "MatchResult", fixture.match_result("LOST", int(time.time() * 1000) - 5_000)
    )
    wait_fields(
        "lost-response terminal cleanup",
        lambda: runtime.base.wallet(fixture.user),
        {"available": "90000", "locked": "0", "debt": "0", "frozen": "0"},
    )
