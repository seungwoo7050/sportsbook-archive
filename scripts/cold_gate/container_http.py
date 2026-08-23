from __future__ import annotations

import json
import re
import subprocess
from collections.abc import Mapping

from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.http import HttpResponse, parse_curl_response


SERVICE_PORTS = {
    "wallet": 8081,
    "betting": 8082,
    "risk": 8083,
    "settlement": 8084,
    "odds": 8085,
    "gateway": 8080,
    "admin": 8090,
}
HEADER_NAME = re.compile(r"^[A-Za-z0-9-]+$")


class ContainerHttpClient:
    def __init__(self, compose: ComposeProject, service: str) -> None:
        if service not in SERVICE_PORTS:
            raise ValueError("container HTTP service is not an application")
        self.compose = compose
        self.service = service

    def request(
        self,
        method: str,
        path: str,
        *,
        headers: Mapping[str, str] | None = None,
        body: object | None = None,
        timeout: int = 15,
    ) -> HttpResponse:
        if (
            not re.fullmatch(r"[A-Z]+", method)
            or not path.startswith("/")
            or path.startswith("//")
        ):
            raise ValueError("container HTTP request target is invalid")
        if timeout < 1 or timeout > 60:
            raise ValueError("container HTTP timeout is invalid")
        command = [
            "exec",
            "-T",
            self.service,
            "curl",
            "--silent",
            "--show-error",
            "--include",
            "--request",
            method,
            "--max-time",
            str(timeout),
            "--write-out",
            "\n__E2E_STATUS__:%{http_code}\n",
        ]
        for name, value in (headers or {}).items():
            if not HEADER_NAME.fullmatch(name) or "\r" in value or "\n" in value:
                raise ValueError("container HTTP header is invalid")
            command.extend(("--header", f"{name}: {value}"))
        if body is not None:
            command.extend(
                (
                    "--header",
                    "Content-Type: application/json",
                    "--data-binary",
                    json.dumps(body, separators=(",", ":")),
                )
            )
        command.append(f"http://localhost:{SERVICE_PORTS[self.service]}{path}")
        try:
            result = self.compose.run(*command, capture_output=True)
        except subprocess.CalledProcessError as error:
            raise RuntimeError(f"container HTTP transport failed for {self.service}") from error
        return parse_curl_response(result.stdout.encode())
