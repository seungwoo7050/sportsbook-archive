from __future__ import annotations

from scripts.cold_gate.database import PostgresClient, uuid_literal


class CorrectionOracles:
    def __init__(self, database: PostgresClient) -> None:
        self.database = database

    def candidates(self, event_id: str) -> list[dict[str, str]]:
        return self.database.query(
            "settlement",
            f"""
            SELECT candidate_id::text AS candidate_id, candidate_sequence::text AS sequence,
                   state, COALESCE(decision_reason, '') AS decision_reason,
                   (decided_at IS NOT NULL)::int AS decided,
                   COALESCE(replaces_candidate_id::text, '') AS replaces_candidate_id
            FROM result_candidate WHERE event_id = {uuid_literal(event_id)}
            ORDER BY candidate_sequence
            """,
        )

    def accepted_candidate(self, event_id: str) -> dict[str, str] | None:
        rows = self.database.query(
            "settlement",
            f"""
            SELECT candidate_id::text AS candidate_id, state, decision_reason,
                   (match_result.accepted_candidate_id = candidate_id)::int AS accepted
            FROM result_candidate
            JOIN match_result USING (event_id)
            WHERE event_id = {uuid_literal(event_id)} AND state = 'ACCEPTED'
            """,
        )
        return _optional_one(rows, "accepted result candidate")

    def revision(self, bet_id: str, number: int = 1) -> dict[str, str] | None:
        if number < 1:
            raise ValueError("revision number must be positive")
        rows = self.database.query(
            "settlement",
            f"""
            SELECT revision_id::text AS revision_id, state, attempt_count::text AS attempt_count,
                   previous_result, new_result,
                   previous_payout_amount::text AS previous_payout,
                   new_payout_amount::text AS new_payout,
                   COALESCE(wallet_status, '') AS wallet_status,
                   COALESCE(wallet_queue_sequence::text, '') AS queue_sequence,
                   (wallet_operation_group_id IS NOT NULL)::int AS operation_group,
                   (applied_at IS NOT NULL)::int AS applied,
                   COALESCE(last_error_code, '') AS last_error
            FROM settlement_revision
            WHERE bet_id = {uuid_literal(bet_id)} AND revision_number = {number}
            """,
        )
        return _optional_one(rows, "settlement revision")

    def wallet_adjustment(self, revision_id: str) -> dict[str, str] | None:
        rows = self.database.query(
            "wallet",
            f"""
            SELECT status, delta_amount::text AS delta,
                   COALESCE(queue_sequence::text, '') AS queue_sequence,
                   (operation_group_id IS NOT NULL)::int AS operation_group,
                   (applied_at IS NOT NULL)::int AS applied,
                   (next_attempt_at IS NOT NULL)::int AS retry_scheduled
            FROM wallet_adjustment WHERE revision_id = {uuid_literal(revision_id)}
            """,
        )
        return _optional_one(rows, "Wallet adjustment")


def _optional_one(rows: list[dict[str, str]], name: str) -> dict[str, str] | None:
    if not rows:
        return None
    if len(rows) != 1:
        raise RuntimeError(f"conflicting {name} rows")
    return rows[0]
