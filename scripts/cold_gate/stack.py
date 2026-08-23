from __future__ import annotations

import re
import subprocess

from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import LOG_SERVICES


class ColdStack:
    def __init__(self, context: ColdGateContext, compose: ComposeProject) -> None:
        if compose.context is not context:
            raise RuntimeError("cold stack Compose project has different ownership")
        self.context = context
        self.compose = compose

    def start(self, environment: dict[str, str]) -> None:
        self.context.require_owned()
        if environment.get("COMPOSE_PROJECT_NAME") != self.context.project:
            raise RuntimeError("Compose environment does not own this cold project")
        self.compose.require_absent()
        try:
            self.compose.run(
                "config",
                "--quiet",
                environment=environment,
                capture_output=True,
            )
            self.compose.run(
                "up",
                "--detach",
                "--build",
                "--wait",
                "--wait-timeout",
                "900",
                environment=environment,
                capture_output=True,
            )
        except subprocess.CalledProcessError as error:
            raise RuntimeError("cold stack startup failed") from error

    def loopback_port(self, service: str, container_port: int) -> int:
        if service not in {"gateway", "toxiproxy", "grafana"} or container_port not in {
            8080,
            8474,
            3000,
        }:
            raise ValueError("published port is outside the cold stack contract")
        result = self.compose.run(
            "port", service, str(container_port), capture_output=True
        )
        endpoint = result.stdout.strip()
        match = re.fullmatch(r"127\.0\.0\.1:([1-9][0-9]{0,4})", endpoint)
        if match is None or int(match.group(1)) > 65535:
            raise RuntimeError(f"{service} did not publish one loopback port")
        return int(match.group(1))

    def logs(self, service: str) -> str:
        if service not in LOG_SERVICES:
            raise ValueError("log service is outside the cold stack inventory")
        result = self.compose.run(
            "logs", "--no-color", "--tail", "2000", service, capture_output=True
        )
        return result.stdout
