from __future__ import annotations

import subprocess

from scripts.cold_gate.compose import ComposeProject


REDIS_SERVICES = frozenset({"redis-risk", "redis-odds", "redis-wallet", "redis-gateway"})


class RedisClient:
    def __init__(self, compose: ComposeProject, service: str) -> None:
        if service not in REDIS_SERVICES:
            raise ValueError("Redis service is outside the release inventory")
        self.compose = compose
        self.service = service

    def command(self, name: str, *arguments: str) -> tuple[str, ...]:
        values = (name.upper(), *arguments)
        if (
            name.upper() not in {"GET", "SET", "HGET", "EXISTS", "TTL"}
            or any(not value or "\0" in value or "\r" in value or "\n" in value for value in values)
            or sum(len(value.encode()) for value in values) > 4096
        ):
            raise ValueError("Redis command is outside the gate contract")
        try:
            result = self.compose.run(
                "exec",
                "-T",
                self.service,
                "redis-cli",
                "--raw",
                *values,
                capture_output=True,
            )
        except subprocess.CalledProcessError as error:
            raise RuntimeError(f"Redis command failed for {self.service}") from error
        return tuple(result.stdout.rstrip("\n").splitlines()) if result.stdout else ()

    def scalar(self, name: str, *arguments: str) -> str:
        lines = self.command(name, *arguments)
        if len(lines) != 1:
            raise RuntimeError("expected one Redis response line")
        return lines[0]
