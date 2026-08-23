from __future__ import annotations

import dataclasses
import os
import secrets
import subprocess
from pathlib import Path

from scripts.cold_gate.context import ColdGateContext


@dataclasses.dataclass(frozen=True)
class RuntimeSecrets:
    environment: dict[str, str]
    private_key: Path
    secret_values: tuple[str, ...]

    @classmethod
    def generate(cls, context: ColdGateContext) -> "RuntimeSecrets":
        context.require_owned()
        names = tuple(
            line.strip()
            for line in (context.root / "config/required-secrets.txt").read_text().splitlines()
            if line.strip()
        )
        if len(names) != 11 or len(set(names)) != 11:
            raise RuntimeError("required secret inventory is invalid")
        values = tuple(secrets.token_urlsafe(32) for _ in names)
        if len(set(values)) != 11 or any(len(value) < 32 for value in values):
            raise RuntimeError("generated service keys are invalid")

        secret_directory = context.runtime / "secrets"
        secret_directory.mkdir(mode=0o700)
        private_key = secret_directory / "jwt-private.pem"
        public_key = secret_directory / "jwt-public.pem"
        subprocess.run(
            [
                "openssl",
                "genpkey",
                "-algorithm",
                "RSA",
                "-pkeyopt",
                "rsa_keygen_bits:2048",
                "-out",
                str(private_key),
            ],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        subprocess.run(
            ["openssl", "pkey", "-in", str(private_key), "-pubout", "-out", str(public_key)],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        private_key.chmod(0o600)
        public_key.chmod(0o600)
        public_pem = public_key.read_text()
        postgres_password = secrets.token_urlsafe(32)
        grafana_password = secrets.token_urlsafe(32)
        environment = os.environ.copy()
        environment.update(dict(zip(names, values, strict=True)))
        environment.update(
            {
                "POSTGRES_PASSWORD": postgres_password,
                "GRAFANA_ADMIN_PASSWORD": grafana_password,
                "GATEWAY_JWT_PUBLIC_KEY": public_pem,
                "ADMIN_JWT_PUBLIC_KEY": public_pem,
                "ADMIN_JWT_ISSUER": "sportsbook-admin-e2e",
                "GATEWAY_HOST_PORT": "0",
            }
        )
        sensitive = (*values, postgres_password, grafana_password, public_pem)
        return cls(environment, private_key, sensitive)
