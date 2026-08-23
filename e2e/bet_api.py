from __future__ import annotations

import dataclasses
import uuid

from e2e.model import ScenarioIds
from scripts.cold_gate.http import HostHttpClient


@dataclasses.dataclass(frozen=True)
class PlacementReceipt:
    bet_id: str
    http_status: int
    status: str


class BetApi:
    def __init__(self, client: HostHttpClient) -> None:
        self.client = client

    def place(self, fixture: ScenarioIds, token: str) -> PlacementReceipt:
        response = self.client.request(
            "POST",
            "/api/v1/bets",
            headers={
                "Authorization": "Bearer " + token,
                "Idempotency-Key": f"e2e-place-{fixture.number:02d}",
            },
            body=fixture.placement(),
        ).require_status(201, 202)
        payload = _object(response.json())
        bet_id = str(payload.get("betId", ""))
        try:
            parsed = uuid.UUID(bet_id)
        except ValueError as error:
            raise RuntimeError("placement did not return a bet UUID") from error
        if str(parsed) != bet_id:
            raise RuntimeError("placement bet UUID is not canonical")
        expected_status = "ACCEPTED" if response.status == 201 else "PENDING"
        expected_selection = {
            "eventId": fixture.event,
            "marketId": fixture.market,
            "selectionId": fixture.selection,
            "oddsAtSubmission": "2.0000",
        }
        if (
            response.header("Location") != "/api/v1/bets/" + bet_id
            or payload.get("userId") != fixture.user
            or payload.get("status") != expected_status
            or payload.get("slipType") != {"type": "SINGLE"}
            or payload.get("stake") != {"amount": 10_000, "currency": "KRW"}
            or payload.get("maxPayout") != {"amount": 20_000, "currency": "KRW"}
            or payload.get("selections") != [expected_selection]
        ):
            raise RuntimeError("placement response drifted")
        return PlacementReceipt(bet_id, response.status, expected_status)

    def get(self, bet_id: str, token: str) -> dict[str, object]:
        uuid.UUID(bet_id)
        response = self.client.request(
            "GET",
            "/api/v1/bets/" + bet_id,
            headers={"Authorization": "Bearer " + token},
        ).require_status(200)
        payload = _object(response.json())
        if payload.get("betId") != bet_id:
            raise RuntimeError("bet query returned the wrong aggregate")
        return payload


def _object(value: object) -> dict[str, object]:
    if not isinstance(value, dict):
        raise RuntimeError("HTTP payload is not an object")
    return value
