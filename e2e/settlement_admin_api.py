from __future__ import annotations

import dataclasses
import uuid

from e2e.assertions import require_uuidv7
from scripts.cold_gate.container_http import ContainerHttpClient


@dataclasses.dataclass(frozen=True)
class AdminMutation:
    action_id: str
    payload: dict[str, object]


class SettlementAdminApi:
    def __init__(self, client: ContainerHttpClient) -> None:
        if client.service != "admin":
            raise ValueError("Settlement admin calls must originate in Admin")
        self.client = client

    def approve(self, candidate_id: str, token: str, key: str) -> AdminMutation:
        return self._candidate(candidate_id, "approve", token, key, None)

    def reject(
        self, candidate_id: str, token: str, key: str, reason: str
    ) -> AdminMutation:
        if not reason or len(reason) > 256:
            raise ValueError("candidate rejection reason is invalid")
        return self._candidate(candidate_id, "reject", token, key, {"reason": reason})

    def retry(self, revision_id: str, token: str, key: str) -> AdminMutation:
        _canonical_uuid(revision_id, "revision")
        response = self.client.request(
            "POST",
            f"/admin/v1/settlements/revisions/{revision_id}/retry",
            headers=self._headers(token, key),
        ).require_status(202)
        payload = _object(response.json())
        if payload.get("idempotencyKey") != key or payload.get("outcome") not in {
            "QUEUED",
            "REPLAY",
        }:
            raise RuntimeError("revision retry receipt drifted")
        return AdminMutation(
            require_uuidv7(response.header("X-Admin-Action-Id"), "admin action ID"),
            payload,
        )

    def _candidate(
        self,
        candidate_id: str,
        action: str,
        token: str,
        key: str,
        body: object | None,
    ) -> AdminMutation:
        _canonical_uuid(candidate_id, "candidate")
        response = self.client.request(
            "POST",
            f"/admin/v1/settlements/result-candidates/{candidate_id}/{action}",
            headers=self._headers(token, key),
            body=body,
        ).require_status(200)
        payload = _object(response.json())
        expected = "CANDIDATE_APPROVED" if action == "approve" else "CANDIDATE_REJECTED"
        if (
            payload.get("idempotencyKey") != key
            or payload.get("outcome") != expected
            or payload.get("replay") is not False
        ):
            raise RuntimeError("candidate mutation receipt drifted")
        return AdminMutation(
            require_uuidv7(response.header("X-Admin-Action-Id"), "admin action ID"),
            payload,
        )

    @staticmethod
    def _headers(token: str, key: str) -> dict[str, str]:
        _canonical_uuid(key, "idempotency")
        return {"Authorization": "Bearer " + token, "Idempotency-Key": key}


def _canonical_uuid(value: str, name: str) -> None:
    parsed = uuid.UUID(value)
    if str(parsed) != value:
        raise ValueError(f"{name} ID must be canonical")


def _object(value: object) -> dict[str, object]:
    if not isinstance(value, dict):
        raise RuntimeError("Admin response is not an object")
    return value
