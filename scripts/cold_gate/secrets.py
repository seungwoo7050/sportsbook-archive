from __future__ import annotations

import dataclasses
import os
import secrets
import socket
import subprocess
from pathlib import Path

from scripts.cold_gate.context import ColdGateContext


def _available_loopback_ports() -> tuple[str, str]:
    candidates = (
        socket.socket(socket.AF_INET, socket.SOCK_STREAM),
        socket.socket(socket.AF_INET, socket.SOCK_STREAM),
    )
    try:
        for candidate in candidates:
            candidate.bind(("127.0.0.1", 0))
        return tuple(str(candidate.getsockname()[1]) for candidate in candidates)
    finally:
        for candidate in candidates:
            candidate.close()


@dataclasses.dataclass(frozen=True)
class RuntimeSecrets:
    environment: dict[str, str]
    private_key: Path
    secret_values: tuple[str, ...]

    @property
    def gateway_port(self) -> int:
        port = int(self.environment["GATEWAY_HOST_PORT"])
        if not 0 < port <= 65535:
            raise RuntimeError("generated gateway port is invalid")
        return port

    @property
    def toxiproxy_port(self) -> int:
        port = int(self.environment["TOXIPROXY_HOST_PORT"])
        if not 0 < port <= 65535:
            raise RuntimeError("generated Toxiproxy port is invalid")
        return port

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
        gateway_port, toxiproxy_port = _available_loopback_ports()
        environment = os.environ.copy()
        environment.update(dict(zip(names, values, strict=True)))
        environment.update(
            {
                "POSTGRES_PASSWORD": postgres_password,
                "GRAFANA_ADMIN_PASSWORD": grafana_password,
                "GATEWAY_JWT_PUBLIC_KEY": public_pem,
                "ADMIN_JWT_PUBLIC_KEY": public_pem,
                "ADMIN_JWT_ISSUER": "sportsbook-admin-e2e",
                "GATEWAY_HOST_PORT": gateway_port,
                "TOXIPROXY_HOST_PORT": toxiproxy_port,
                "COMPOSE_PROJECT_NAME": context.project,
            }
        )
        sensitive = (*values, postgres_password, grafana_password, public_pem)
        return cls(environment, private_key, sensitive)
