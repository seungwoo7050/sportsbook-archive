from __future__ import annotations

import dataclasses
import re
import uuid


TOPICS = frozenset(
    {"event.lifecycle", "match.result", "bet.settled.v1", "bet.resolution.revised.v1"}
)
RECEIPT = re.compile(
    r"^topic=([^\t]+)\tkey=([^\t]+)\tpartition=([0-2])\toffset=([0-9]+)"
    r"\tsha256=([0-9a-f]{64})\tfingerprint=([0-9a-f]{16}|malformed)\n?$"
)


@dataclasses.dataclass(frozen=True)
class FixtureReceipt:
    topic: str
    key: str
    partition: int
    offset: int
    sha256: str
    fingerprint: str

    @classmethod
    def parse(cls, output: str) -> "FixtureReceipt":
        match = RECEIPT.fullmatch(output)
        if match is None or match.group(1) not in TOPICS:
            raise RuntimeError("fixture publication receipt is invalid")
        key = match.group(2)
        try:
            parsed = uuid.UUID(key)
        except ValueError as error:
            raise RuntimeError("fixture receipt key is not a UUID") from error
        if str(parsed) != key:
            raise RuntimeError("fixture receipt key is not canonical")
        return cls(
            topic=match.group(1),
            key=key,
            partition=int(match.group(3)),
            offset=int(match.group(4)),
            sha256=match.group(5),
            fingerprint=match.group(6),
        )
