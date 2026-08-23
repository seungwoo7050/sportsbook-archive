from __future__ import annotations

import dataclasses
import decimal
import time

from e2e.assertions import require_fields, wait_fields
from e2e.metrics import metric_value
from e2e.model import ScenarioIds
from e2e.revision_fixture import revision_payload
from e2e.runtime import E2eRuntime
from scripts.cold_gate.fixture_receipt import FixtureReceipt
from scripts.cold_gate.polling import poll_until


GAP_METRIC = "betting_resolution_revision_gaps_total"


@dataclasses.dataclass(frozen=True)
class OrderingState:
    fixture: ScenarioIds
    bet_id: str
    revision_id: str
    payload_sha256: str
    source_time: int
    gap_expected: decimal.Decimal


def stage_revision_ordering(runtime: E2eRuntime) -> OrderingState:
    fixture = ScenarioIds.create(10)
    runtime.seed(fixture)
    placement = runtime.bets.place(fixture, runtime.user_token(fixture))
    wait_fields(
        "ordering placement projection",
        lambda: runtime.base.settlement(placement.bet_id),
        {"status": "PENDING"},
        terminal={"status": frozenset({"SETTLED", "VOIDED"})},
    )
    gap_expected = metric_value(runtime.betting_http, GAP_METRIC) + 1
    source_time = int(time.time() * 1000) - 10_000
    revision_two = revision_payload(
        fixture, placement.bet_id, "55000000-0000-7000-8000-000000000010",
        2, "LOST", "WON", 0, 20_000, source_time, source_time + 1_000,
    )
    first = runtime.fixtures.publish("BetResolutionRevised", revision_two)
    wait_consumed(runtime, first)
    wait_fields(
        "revision-before-base projection",
        lambda: runtime.base.betting(placement.bet_id),
        {
            "status": "SETTLED", "result": "WON", "payout": "20000",
            "revision_number": "2", "revision_id": revision_two["revisionId"],
            "payload_sha256": first.sha256,
        },
    )
    wait_gap(runtime, gap_expected)

    duplicate = runtime.fixtures.publish("BetResolutionRevised", revision_two)
    if duplicate.sha256 != first.sha256:
        raise RuntimeError("duplicate revision bytes drifted")
    wait_consumed(runtime, duplicate)
    revision_one = revision_payload(
        fixture, placement.bet_id, "55000000-0000-7000-8000-000000000011",
        1, "LOST", "LOST", 0, 0, source_time, source_time + 2_000,
    )
    lower = runtime.fixtures.publish("BetResolutionRevised", revision_one)
    wait_consumed(runtime, lower)
    require_fields(
        runtime.base.betting(placement.bet_id) or {},
        {"revision_number": "2", "revision_id": revision_two["revisionId"],
         "payload_sha256": first.sha256},
        "duplicate and lower revision projection",
    )
    wait_gap(runtime, gap_expected)
    return OrderingState(
        fixture, placement.bet_id, str(revision_two["revisionId"]), first.sha256,
        source_time, gap_expected,
    )


def wait_consumed(runtime: E2eRuntime, receipt: FixtureReceipt) -> None:
    poll_until(
        "Betting revision consumption",
        lambda: runtime.kafka.committed_offset("betting-resolution", receipt.topic, receipt.partition),
        lambda offset: offset > receipt.offset,
        timeout=60,
        interval=0.5,
    )


def wait_gap(runtime: E2eRuntime, expected: decimal.Decimal) -> None:
    poll_until(
        "revision gap counter",
        lambda: metric_value(runtime.betting_http, GAP_METRIC),
        lambda value: value == expected,
        timeout=30,
        interval=0.25,
    )
