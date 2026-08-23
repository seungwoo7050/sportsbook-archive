from __future__ import annotations

from e2e.admin_correlation import (
    admin_topic_offsets,
    require_odds_correlation,
    wait_admin_record,
)
from e2e.assertions import require_fields
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime
from scripts.cold_gate.polling import poll_until


NAME = "admin-audit-downstream-correlation"
TRACE_ID = "1" * 32
TRACEPARENT = f"00-{TRACE_ID}-{'2' * 16}-01"
REASON = "e2e correlation"


def run(runtime: E2eRuntime) -> None:
    fixture = ScenarioIds.create(13)
    runtime.seed(fixture)
    before = admin_topic_offsets(runtime)
    token = runtime.admin_token()
    action_id = runtime.market_admin.suspend(
        fixture,
        token,
        "e2e-admin-market-13",
        TRACEPARENT,
        REASON,
    )
    audit = poll_until(
        "authoritative Admin audit row",
        lambda: runtime.market_admin.audit(action_id, token),
        lambda payload: payload.get("outcome") == "SUCCESS",
        timeout=30,
        interval=0.25,
    )
    expected = {
        "actionId": action_id,
        "actorId": "e2e-admin",
        "actorRole": "ADMIN",
        "action": "MARKET_SUSPEND",
        "target": f"{fixture.event}/{fixture.market}",
        "outcome": "SUCCESS",
        "httpStatus": 202,
        "reason": REASON,
        "traceId": TRACE_ID,
    }
    require_fields(audit, expected, "authoritative Admin audit")
    if not audit.get("startedAt") or not audit.get("completedAt"):
        raise RuntimeError("Admin audit lifecycle timestamps are incomplete")

    record = wait_admin_record(runtime, before, action_id)
    if record.key != "e2e-admin" or record.avro is None:
        raise RuntimeError("Admin action Kafka identity drifted")
    require_fields(record.avro, expected, "Admin action Kafka record")
    if not record.avro.get("startedAt") or not record.avro.get("completedAt"):
        raise RuntimeError("Admin action Kafka lifecycle timestamps are incomplete")
    require_odds_correlation(runtime, fixture, action_id)
