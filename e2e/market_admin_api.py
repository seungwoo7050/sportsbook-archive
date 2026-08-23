from __future__ import annotations

from e2e.assertions import require_uuidv7
from e2e.model import ScenarioIds
from scripts.cold_gate.container_http import ContainerHttpClient


class MarketAdminApi:
    def __init__(self, client: ContainerHttpClient) -> None:
        if client.service != "admin":
            raise ValueError("market operator calls must originate in Admin")
        self.client = client

    def suspend(
        self,
        fixture: ScenarioIds,
        token: str,
        idempotency_key: str,
        traceparent: str,
        reason: str,
    ) -> str:
        if not idempotency_key.startswith("e2e-") or not reason:
            raise ValueError("market operator fixture is invalid")
        response = self.client.request(
            "POST",
            f"/admin/v1/events/{fixture.event}/markets/{fixture.market}/suspend",
            headers={
                "Authorization": "Bearer " + token,
                "Idempotency-Key": idempotency_key,
                "traceparent": traceparent,
            },
            body={"reason": reason},
        ).require_status(202)
        if response.body:
            raise RuntimeError("market operator response must be empty")
        return require_uuidv7(
            response.header("X-Admin-Action-Id"), "market operator action ID"
        )

    def audit(self, action_id: str, token: str) -> dict[str, object]:
        response = self.client.request(
            "GET",
            "/admin/v1/audit-logs/" + action_id,
            headers={"Authorization": "Bearer " + token},
        ).require_status(200)
        payload = response.json()
        if not isinstance(payload, dict) or payload.get("actionId") != action_id:
            raise RuntimeError("Admin audit lookup returned the wrong action")
        return payload
