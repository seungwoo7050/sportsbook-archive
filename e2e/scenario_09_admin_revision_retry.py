from __future__ import annotations

from e2e.assertions import require_fields, wait_fields
from e2e.blocked_correction import wait_applied_decrease
from e2e.decrease_stimulus import create_blocked_decrease
from e2e.runtime import E2eRuntime
from scripts.cold_gate.database import uuid_literal


NAME = "admin-revision-retry"


def run(runtime: E2eRuntime) -> None:
    blocked = create_blocked_decrease(runtime, 9)
    settlement_stopped = False
    try:
        runtime.stop_settlement()
        settlement_stopped = True
        updated = runtime.database.one(
            "settlement",
            f"""
            UPDATE settlement_revision
            SET attempt_count = 12,
                next_retry_at = NULL,
                last_error_code = 'WALLET_RETRY_EXHAUSTED',
                updated_at = CURRENT_TIMESTAMP
            WHERE revision_id = {uuid_literal(blocked.revision_id)}
              AND state = 'BLOCKED'
              AND wallet_status = 'BLOCKED'
              AND lease_token IS NULL
            RETURNING revision_id::text AS revision_id
            """,
        )
        if updated["revision_id"] != blocked.revision_id:
            raise RuntimeError("revision exhaustion fixture changed identity")

        runtime.wallet_api.transfer(
            blocked.fixture,
            "deposit",
            20_000,
            "e2e-retry-proof-deposit-09",
        )
        wait_fields(
            "operator retry Wallet proof",
            lambda: runtime.corrections.wallet_adjustment(blocked.revision_id),
            {
                "status": "APPLIED",
                "delta": "-20000",
                "queue_sequence": "1",
                "operation_group": "1",
                "applied": "1",
                "retry_scheduled": "0",
            },
            terminal={"status": frozenset({"REJECTED"})},
            timeout=60,
        )
        runtime.start_settlement()
        settlement_stopped = False

        mutation = runtime.settlement_admin.retry(
            blocked.revision_id,
            runtime.admin_token(),
            "44000000-0000-7000-8000-000000000009",
        )
        require_fields(
            mutation.payload,
            {
                "outcome": "QUEUED",
                "revisionState": "BLOCKED",
                "attemptCount": 0,
            },
            "operator retry receipt",
        )
        if not mutation.payload.get("nextRetryAt"):
            raise RuntimeError("operator retry receipt has no next attempt")
        applied = wait_applied_decrease(runtime, blocked.bet_id)
        if applied["revision_id"] != blocked.revision_id:
            raise RuntimeError("operator retry changed revision identity")
        wait_fields(
            "operator retry Betting projection",
            lambda: runtime.base.betting(blocked.bet_id),
            {
                "status": "SETTLED",
                "result": "LOST",
                "payout": "0",
                "revision_number": "1",
                "revision_id": blocked.revision_id,
            },
        )
        wait_fields(
            "operator retry Wallet account",
            lambda: runtime.base.wallet(blocked.fixture.user),
            {"available": "0", "locked": "0", "debt": "0", "frozen": "0"},
        )
    finally:
        if settlement_stopped:
            runtime.start_settlement()
