from __future__ import annotations

from scripts.cold_gate.database import PostgresClient, uuid_literal


class BaseOracles:
    def __init__(self, database: PostgresClient) -> None:
        self.database = database

    def betting(self, bet_id: str) -> dict[str, str] | None:
        rows = self.database.query(
            "betting",
            f"""
            SELECT status, placement_phase, risk_commit_observed::int AS risk_committed,
                   (wallet_operation_id IS NOT NULL)::int AS wallet_confirmed,
                   COALESCE(settlement_result, '') AS result,
                   COALESCE(settled_payout_amount::text, '') AS payout,
                   COALESCE(settled_payout_currency, '') AS currency,
                   COALESCE(void_reason, '') AS void_reason,
                   COALESCE(resolution_revision_number::text, '') AS revision_number,
                   COALESCE(resolution_revision_id::text, '') AS revision_id,
                   COALESCE(resolution_payload_sha256, '') AS payload_sha256
            FROM bet WHERE bet_id = {uuid_literal(bet_id)}
            """,
        )
        return _optional_one(rows, "Betting")

    def settlement(self, bet_id: str) -> dict[str, str] | None:
        rows = self.database.query(
            "settlement",
            f"""
            SELECT status, COALESCE(result, '') AS result,
                   COALESCE(payout_amount::text, '') AS payout,
                   COALESCE(payout_currency, '') AS currency,
                   revision_number::text AS revision_number
            FROM bet WHERE bet_id = {uuid_literal(bet_id)}
            """,
        )
        return _optional_one(rows, "Settlement")

    def wallet(self, user_id: str) -> dict[str, str] | None:
        rows = self.database.query(
            "wallet",
            f"""
            SELECT available_amount::text AS available, locked_amount::text AS locked,
                   recovery_debt_amount::text AS debt,
                   (recovery_frozen_at IS NOT NULL)::int AS frozen
            FROM account WHERE user_id = {uuid_literal(user_id)}
            """,
        )
        return _optional_one(rows, "Wallet")

    def tombstone(self, event_id: str) -> str | None:
        rows = self.database.query(
            "settlement",
            f"""
            SELECT terminal_status FROM event_lifecycle_tombstone
            WHERE event_id = {uuid_literal(event_id)}
            """,
        )
        if not rows:
            return None
        if len(rows) != 1:
            raise RuntimeError("Settlement has conflicting lifecycle tombstones")
        return rows[0]["terminal_status"]


def _optional_one(rows: list[dict[str, str]], owner: str) -> dict[str, str] | None:
    if not rows:
        return None
    if len(rows) != 1:
        raise RuntimeError(f"{owner} returned conflicting aggregate rows")
    return rows[0]
