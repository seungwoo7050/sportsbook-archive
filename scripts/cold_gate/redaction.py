from __future__ import annotations

import re
from collections.abc import Iterable


PEM_PATTERN = re.compile(
    r"-----BEGIN [^-\r\n]*KEY-----.*?-----END [^-\r\n]*KEY-----",
    re.DOTALL,
)
JWT_PATTERN = re.compile(r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b")


class EvidenceRedactor:
    def __init__(self, secret_values: Iterable[str]) -> None:
        values = {value for value in secret_values if value}
        if any(len(value) < 8 for value in values):
            raise ValueError("redaction secrets must contain at least eight characters")
        self.secret_values = tuple(sorted(values, key=len, reverse=True))

    def redact(self, value: str) -> str:
        redacted = PEM_PATTERN.sub("[REDACTED PEM]", value)
        redacted = JWT_PATTERN.sub("[REDACTED JWT]", redacted)
        for secret in self.secret_values:
            redacted = redacted.replace(secret, "[REDACTED SECRET]")
        return redacted

    def require_clean(self, value: str) -> None:
        if PEM_PATTERN.search(value) or JWT_PATTERN.search(value):
            raise RuntimeError("evidence contains key material or a JWT")
        if any(secret in value for secret in self.secret_values):
            raise RuntimeError("evidence contains an exact secret value")
