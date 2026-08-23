from __future__ import annotations

import json
import re
import urllib.parse
from collections.abc import Iterable


PEM_PATTERN = re.compile(
    r"-----BEGIN [^-\r\n]*KEY-----.*?-----END [^-\r\n]*KEY-----",
    re.DOTALL,
)
JWT_PATTERN = re.compile(r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b")
BEARER_PATTERN = re.compile(r"(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+")
JSON_SENSITIVE_PATTERN = re.compile(
    r'(?i)("[^"]*(?:api[_-]?key|password|secret|token)[^"]*"\s*:\s*)'
    r'"(?:\\.|[^"])*"'
)
PLAIN_SENSITIVE_PATTERN = re.compile(
    r"(?im)^(\s*(?:x-api-key|api[_-]?key|authorization|proxy-authorization|"
    r"[^\s:=]*(?:password|secret|token))\s*[:=]\s*).+$"
)


class EvidenceRedactor:
    def __init__(self, secret_values: Iterable[str]) -> None:
        values = {value for value in secret_values if value}
        if any(len(value) < 8 for value in values):
            raise ValueError("redaction secrets must contain at least eight characters")
        variants = set(values)
        for value in values:
            variants.add(json.dumps(value)[1:-1])
            variants.add(urllib.parse.quote(value, safe=""))
        self.secret_values = tuple(sorted(variants, key=len, reverse=True))

    def redact(self, value: str) -> str:
        redacted = PEM_PATTERN.sub("[REDACTED PEM]", value)
        redacted = JWT_PATTERN.sub("[REDACTED JWT]", redacted)
        redacted = BEARER_PATTERN.sub("Bearer [REDACTED]", redacted)
        for secret in self.secret_values:
            redacted = redacted.replace(secret, "[REDACTED SECRET]")
        redacted = JSON_SENSITIVE_PATTERN.sub(r'\1"[REDACTED]"', redacted)
        redacted = PLAIN_SENSITIVE_PATTERN.sub(r"\1[REDACTED]", redacted)
        return redacted

    def require_clean(self, value: str) -> None:
        if PEM_PATTERN.search(value) or JWT_PATTERN.search(value):
            raise RuntimeError("evidence contains key material or a JWT")
        if any(secret in value for secret in self.secret_values):
            raise RuntimeError("evidence contains an exact secret value")
        if self.redact(value) != value:
            raise RuntimeError("evidence contains credential material")
