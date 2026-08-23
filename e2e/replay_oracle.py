from __future__ import annotations

import json

from e2e.base_oracles import BaseOracles
from e2e.model import ScenarioIds
from scripts.cold_gate.database import PostgresClient, uuid_literal


class ReplayOracle:
    def __init__(self, database: PostgresClient, base: BaseOracles) -> None:
        self.database = database
        self.base = base

    def snapshot(self, fixture: ScenarioIds, bet_id: str) -> str:
        bet = uuid_literal(bet_id)
        event = uuid_literal(fixture.event)
        user = uuid_literal(fixture.user)
        settlement_counts = self.database.one(
            "settlement",
            f"""
            SELECT
              (SELECT count(*) FROM result_candidate WHERE event_id = {event})::text AS candidates,
              (SELECT count(*) FROM settlement_revision WHERE bet_id = {bet})::text AS revisions,
              (SELECT count(*) FROM outbox_event
               WHERE partition_key IN ({event}::text, {bet}::text))::text AS outbox_events,
              (SELECT count(*) FROM settlement_attempt WHERE bet_id = {bet})::text AS attempts
            """,
        )
        wallet_counts = self.database.one(
            "wallet",
            f"""
            SELECT
              (SELECT count(*) FROM ledger_entry WHERE account_id = {user})::text AS ledger_entries,
              (SELECT count(*) FROM wallet_operation WHERE user_id = {user})::text AS operations,
              (SELECT count(*) FROM wallet_adjustment WHERE user_id = {user})::text AS adjustments,
              (SELECT count(*) FROM outbox_event o JOIN wallet_operation w
               ON w.idempotency_key = o.operation_key WHERE w.user_id = {user})::text AS outbox_events
            """,
        )
        betting_counts = self.database.one(
            "betting",
            f"""
            SELECT
              (SELECT count(*) FROM outbox_event)::text AS outbox_events,
              (SELECT count(*) FROM wallet_event_receipt WHERE bet_id = {bet})::text AS wallet_receipts
            """,
        )
        snapshot = {
            "betting": self.base.betting(bet_id),
            "settlement": self.base.settlement(bet_id),
            "wallet": self.base.wallet(fixture.user),
            "settlementCounts": settlement_counts,
            "walletCounts": wallet_counts,
            "bettingCounts": betting_counts,
        }
        return json.dumps(snapshot, sort_keys=True, separators=(",", ":"))
