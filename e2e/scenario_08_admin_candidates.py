from __future__ import annotations

import time

from e2e.assertions import require_fields, wait_fields
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime
from scripts.cold_gate.polling import poll_until


NAME = "admin-candidate-approve-reject"
APPROVAL_HORIZON_MILLIS = 60_000
APPROVAL_ELIGIBILITY_TIMEOUT_SECONDS = 75


def run(runtime: E2eRuntime) -> None:
    token = runtime.admin_token()
    approved_fixture = ScenarioIds.create(81)
    approved_at = int(time.time() * 1000) + APPROVAL_HORIZON_MILLIS
    runtime.fixtures.publish(
        "MatchResult", approved_fixture.match_result("WON", approved_at)
    )
    approved_candidate = _pending_candidate(runtime, approved_fixture.event)
    poll_until(
        "candidate approval eligibility",
        lambda: int(time.time() * 1000),
        lambda now: now >= approved_at + 100,
        timeout=APPROVAL_ELIGIBILITY_TIMEOUT_SECONDS,
        interval=0.1,
    )
    approval = runtime.settlement_admin.approve(
        approved_candidate["candidate_id"],
        token,
        "44000000-0000-7000-8000-000000000081",
    )
    require_fields(
        approval.payload,
        {"outcome": "CANDIDATE_APPROVED", "replay": False},
        "candidate approval receipt",
    )
    wait_fields(
        "approved candidate state",
        lambda: _single_candidate(runtime, approved_fixture.event),
        {"state": "ACCEPTED", "decision_reason": "OPERATOR_APPROVED", "decided": "1"},
        terminal={"state": frozenset({"REJECTED"})},
    )

    rejected_fixture = ScenarioIds.create(82)
    runtime.fixtures.publish(
        "MatchResult",
        rejected_fixture.match_result(
            "LOST", int(time.time() * 1000) + APPROVAL_HORIZON_MILLIS
        ),
    )
    rejected_candidate = _pending_candidate(runtime, rejected_fixture.event)
    rejection = runtime.settlement_admin.reject(
        rejected_candidate["candidate_id"],
        token,
        "44000000-0000-7000-8000-000000000082",
        "e2e rejected candidate",
    )
    require_fields(
        rejection.payload,
        {"outcome": "CANDIDATE_REJECTED", "replay": False},
        "candidate rejection receipt",
    )
    wait_fields(
        "rejected candidate state",
        lambda: _single_candidate(runtime, rejected_fixture.event),
        {
            "state": "REJECTED",
            "decision_reason": "e2e rejected candidate",
            "decided": "1",
        },
        terminal={"state": frozenset({"ACCEPTED", "SUPERSEDED"})},
    )


def _pending_candidate(runtime: E2eRuntime, event_id: str) -> dict[str, str]:
    candidate = poll_until(
        "pending result candidate",
        lambda: _single_candidate(runtime, event_id),
        lambda row: row is not None,
        timeout=30,
        interval=0.25,
    )
    require_fields(
        candidate,
        {"state": "PENDING", "decision_reason": "FUTURE_HELD", "decided": "0"},
        "pending result candidate",
    )
    return candidate


def _single_candidate(runtime: E2eRuntime, event_id: str) -> dict[str, str] | None:
    rows = runtime.corrections.candidates(event_id)
    if not rows:
        return None
    if len(rows) != 1:
        raise RuntimeError("operator scenario has conflicting candidates")
    return rows[0]
