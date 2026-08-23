from __future__ import annotations

import base64
import json
import subprocess
import uuid
from collections.abc import Callable
from pathlib import Path

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.owned_path import require_regular_file


Runner = Callable[..., subprocess.CompletedProcess[bytes]]


class JwtSigner:
    def __init__(
        self,
        context: ColdGateContext,
        private_key: Path,
        runner: Runner = subprocess.run,
    ) -> None:
        expected = context.runtime / "secrets/jwt-private.pem"
        if private_key != expected:
            raise RuntimeError("JWT key is not owned by this cold gate")
        context.require_owned()
        require_regular_file(private_key)
        if private_key.stat().st_mode & 0o077:
            raise RuntimeError("JWT private key permissions are too broad")
        self.private_key = private_key
        self.runner = runner

    def user(self, subject: str, now: int) -> str:
        parsed = uuid.UUID(subject)
        if str(parsed) != subject or parsed.version != 7:
            raise ValueError("user subject must be a canonical UUIDv7")
        return self.sign({"sub": subject, "roles": ["USER"], "iat": now, "exp": now + 1200})

    def admin(self, now: int) -> str:
        return self.sign(
            {
                "sub": "e2e-admin",
                "role": "ADMIN",
                "iss": "sportsbook-admin-e2e",
                "iat": now,
                "nbf": now - 5,
                "exp": now + 1200,
            }
        )

    def sign(self, claims: dict[str, object]) -> str:
        header = _encode({"alg": "RS256", "typ": "JWT"})
        payload = _encode(claims)
        unsigned = f"{header}.{payload}"
        result = self.runner(
            ["openssl", "dgst", "-sha256", "-sign", str(self.private_key)],
            input=unsigned.encode(),
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            check=True,
        )
        return f"{unsigned}.{_base64url(result.stdout)}"


def _encode(value: dict[str, object]) -> str:
    return _base64url(json.dumps(value, sort_keys=True, separators=(",", ":")).encode())


def _base64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")
