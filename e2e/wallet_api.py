from __future__ import annotations

from e2e.model import ScenarioIds
from scripts.cold_gate.container_http import ContainerHttpClient


class WalletApi:
    def __init__(self, client: ContainerHttpClient, platform_key: str) -> None:
        if client.service != "wallet" or len(platform_key) < 32:
            raise ValueError("Wallet E2E credentials are invalid")
        self.client = client
        self.headers = {
            "X-Internal-Service": "platform",
            "X-Internal-Api-Key": platform_key,
        }

    def open_and_fund(self, fixture: ScenarioIds) -> None:
        opened = self.client.request(
            "POST",
            "/internal/v1/wallet/accounts",
            headers=self.headers,
            body=fixture.account(),
        ).require_status(200)
        payload = _object(opened.json())
        if (
            payload.get("userId") != fixture.user
            or payload.get("currency") != "KRW"
            or payload.get("available") != {"amount": 0, "currency": "KRW"}
            or payload.get("locked") != {"amount": 0, "currency": "KRW"}
            or payload.get("outboundFrozen") is not False
        ):
            raise RuntimeError("Wallet account fixture response drifted")
        self.transfer(fixture, "deposit", 100_000, f"e2e-deposit-{fixture.number:02d}")

    def transfer(
        self, fixture: ScenarioIds, operation: str, amount: int, idempotency_key: str
    ) -> dict[str, object]:
        if operation not in {"deposit", "withdraw"} or not idempotency_key.startswith("e2e-"):
            raise ValueError("Wallet fixture transfer is outside the E2E contract")
        headers = {**self.headers, "Idempotency-Key": idempotency_key}
        response = self.client.request(
            "POST",
            "/internal/v1/wallet/transactions/" + operation,
            headers=headers,
            body=fixture.transfer(amount),
        ).require_status(200)
        payload = _object(response.json())
        expected_reason = "DEPOSIT" if operation == "deposit" else "WITHDRAW"
        if (
            payload.get("userId") != fixture.user
            or payload.get("amount") != {"amount": amount, "currency": "KRW"}
            or payload.get("reason") != expected_reason
            or not payload.get("operationGroupId")
        ):
            raise RuntimeError("Wallet transfer receipt drifted")
        return payload


def _object(value: object) -> dict[str, object]:
    if not isinstance(value, dict):
        raise RuntimeError("HTTP payload is not an object")
    return value
