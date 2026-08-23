from __future__ import annotations

import dataclasses
import json
import urllib.error
import urllib.parse
import urllib.request
from collections.abc import Callable, Mapping


@dataclasses.dataclass(frozen=True)
class HttpResponse:
    status: int
    headers: tuple[tuple[str, str], ...]
    body: bytes

    def json(self) -> object:
        try:
            return json.loads(self.body)
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise RuntimeError("HTTP response is not JSON") from error

    def header(self, name: str) -> str:
        values = [value for key, value in self.headers if key.lower() == name.lower()]
        if len(values) != 1:
            raise RuntimeError(f"expected exactly one {name} header")
        return values[0]

    def require_status(self, *expected: int) -> "HttpResponse":
        if self.status not in expected:
            raise RuntimeError(f"unexpected HTTP status {self.status}: {self.body[:512]!r}")
        return self


class HostHttpClient:
    def __init__(
        self,
        base_url: str,
        opener: Callable[..., object] = urllib.request.urlopen,
    ) -> None:
        parsed = urllib.parse.urlsplit(base_url)
        if (
            parsed.scheme != "http"
            or parsed.hostname != "127.0.0.1"
            or parsed.port is None
            or parsed.path not in ("", "/")
            or parsed.username is not None
            or parsed.query
            or parsed.fragment
        ):
            raise ValueError("cold gate HTTP base must be loopback with an explicit port")
        self.base_url = f"http://127.0.0.1:{parsed.port}"
        self.opener = opener

    def request(
        self,
        method: str,
        path: str,
        *,
        headers: Mapping[str, str] | None = None,
        body: object | None = None,
        timeout: float = 15.0,
    ) -> HttpResponse:
        if not path.startswith("/") or path.startswith("//"):
            raise ValueError("HTTP path must be absolute within the service")
        payload = None if body is None else json.dumps(body, separators=(",", ":")).encode()
        request_headers = dict(headers or {})
        if payload is not None:
            request_headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            self.base_url + path,
            data=payload,
            headers=request_headers,
            method=method,
        )
        try:
            response = self.opener(request, timeout=timeout)
        except urllib.error.HTTPError as error:
            return _response(error.code, error.headers.items(), error.read())
        with response:
            return _response(response.status, response.headers.items(), response.read())


def _response(status: int, headers, body: bytes) -> HttpResponse:
    return HttpResponse(status, tuple((str(key), str(value)) for key, value in headers), body)
