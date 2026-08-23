from __future__ import annotations

import uuid

from e2e.model import OUTCOMES, ScenarioIds, money


def revision_payload(
    fixture: ScenarioIds,
    bet_id: str,
    revision_id: str,
    revision_number: int,
    previous_result: str,
    new_result: str,
    previous_payout: int,
    new_payout: int,
    source_result_settled_at: int,
    revised_at: int,
) -> dict[str, object]:
    for value in (bet_id, revision_id):
        parsed = uuid.UUID(value)
        if str(parsed) != value:
            raise ValueError("revision fixture UUID is not canonical")
    if (
        revision_number < 1
        or previous_result not in OUTCOMES
        or new_result not in OUTCOMES
        or previous_payout < 0
        or new_payout < 0
        or source_result_settled_at <= 0
        or revised_at < source_result_settled_at
    ):
        raise ValueError("revision fixture is invalid")
    return {
        "revisionId": revision_id,
        "revisionNumber": revision_number,
        "betId": bet_id,
        "userId": fixture.user,
        "eventId": fixture.event,
        "previousResult": previous_result,
        "newResult": new_result,
        "previousPayout": money(previous_payout),
        "newPayout": money(new_payout),
        "sourceResultSettledAt": source_result_settled_at,
        "revisedAt": revised_at,
    }
