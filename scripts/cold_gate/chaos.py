from __future__ import annotations

import re
import urllib.request
from collections.abc import Callable

from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.http import HostHttpClient, HttpResponse


PROXIES = frozenset({"betting_to_risk", "betting_to_wallet", "settlement_to_wallet"})
LOST_RESPONSE_TOXIC = "e2e_wallet_response_timeout"


class ChaosClient:
    def __init__(
        self,
        compose: ComposeProject,
        opener: Callable[..., object] = urllib.request.urlopen,
    ) -> None:
        result = compose.run("port", "toxiproxy", "8474", capture_output=True)
        endpoint = result.stdout.strip()
        match = re.fullmatch(r"127\.0\.0\.1:([1-9][0-9]{0,4})", endpoint)
        if match is None or int(match.group(1)) > 65535:
            raise RuntimeError("Toxiproxy did not publish one loopback port")
        self.http = HostHttpClient(f"http://{endpoint}", opener)

    def proxy(self, name: str) -> dict[str, object]:
        return self._json("GET", self._proxy_path(name), expected=(200,))

    def set_enabled(self, name: str, enabled: bool) -> dict[str, object]:
        return self._json(
            "POST", self._proxy_path(name), body={"enabled": enabled}, expected=(200,)
        )

    def add_wallet_response_timeout(self) -> dict[str, object]:
        return self._json(
            "POST",
            self._proxy_path("betting_to_wallet") + "/toxics",
            body={
                "name": LOST_RESPONSE_TOXIC,
                "type": "timeout",
                "stream": "downstream",
                "attributes": {"timeout": 0},
            },
            expected=(200,),
        )

    def remove_wallet_response_timeout(self) -> None:
        response = self.http.request(
            "DELETE",
            self._proxy_path("betting_to_wallet") + "/toxics/" + LOST_RESPONSE_TOXIC,
        )
        response.require_status(204)

    def _json(
        self, method: str, path: str, *, body=None, expected: tuple[int, ...]
    ) -> dict[str, object]:
        response: HttpResponse = self.http.request(method, path, body=body)
        payload = response.require_status(*expected).json()
        if not isinstance(payload, dict):
            raise RuntimeError("Toxiproxy response is not an object")
        return payload

    @staticmethod
    def _proxy_path(name: str) -> str:
        if name not in PROXIES:
            raise ValueError("proxy is outside the cold gate inventory")
        return "/proxies/" + name
