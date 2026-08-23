from __future__ import annotations

from scripts.cold_gate.database import PostgresClient, uuid_literal


class PlacementOracles:
    def __init__(self, database: PostgresClient) -> None:
        self.database = database

    def wallet_debit(self, bet_id: str) -> dict[str, str]:
        key = uuid_literal(bet_id) + "::text"
        return self.database.one(
            "wallet",
            f"""
            SELECT
              (SELECT count(*) FROM wallet_operation
               WHERE idempotency_key = {key})::text AS operation_count,
              COALESCE((SELECT min(caller_id) FROM wallet_operation
                        WHERE idempotency_key = {key}), '') AS caller,
              COALESCE((SELECT min(operation_kind) FROM wallet_operation
                        WHERE idempotency_key = {key}), '') AS kind,
              COALESCE((SELECT min(status) FROM wallet_operation
                        WHERE idempotency_key = {key}), '') AS status,
              COALESCE((SELECT min((operation_group_id IS NOT NULL)::int) FROM wallet_operation
                        WHERE idempotency_key = {key}), 0)::text AS operation_group,
              (SELECT count(*) FROM ledger_entry
               WHERE idempotency_key = {key})::text AS ledger_count,
              (SELECT count(*) FROM outbox_event
               WHERE operation_key = {key})::text AS outbox_count,
              COALESCE((SELECT min(topic) FROM outbox_event
                        WHERE operation_key = {key}), '') AS outbox_topic,
              COALESCE((SELECT min(schema_name) FROM outbox_event
                        WHERE operation_key = {key}), '') AS outbox_schema
            """,
        )

    def betting_outbox(self, user_id: str, bet_id: str) -> dict[str, str]:
        return self.database.one(
            "betting",
            f"""
            SELECT count(*)::text AS event_count,
                   COALESCE(min(topic), '') AS topic,
                   COALESCE(min(schema_name), '') AS schema,
                   min((published_at IS NOT NULL)::int)::text AS published
            FROM outbox_event
            WHERE partition_key = {uuid_literal(user_id)}::text
              AND schema_name = 'BetPlacedRequested'
              AND payload IS NOT NULL
              AND EXISTS (SELECT 1 FROM bet WHERE bet_id = {uuid_literal(bet_id)})
            """,
        )

    def settlement_outbox(self, partition_id: str, schema_name: str) -> dict[str, str]:
        if schema_name not in {"BetSettled", "BetVoided", "BetResolutionRevised"}:
            raise ValueError("Settlement outbox schema is outside the E2E contract")
        return self.database.one(
            "settlement",
            f"""
            SELECT count(*)::text AS event_count,
                   COALESCE(min(topic), '') AS topic,
                   min((published_at IS NOT NULL)::int)::text AS published
            FROM outbox_event
            WHERE partition_key = {uuid_literal(partition_id)}::text
              AND schema_name = '{schema_name}'
            """,
        )
