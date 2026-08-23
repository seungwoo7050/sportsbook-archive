from __future__ import annotations

import hashlib
import re
from pathlib import Path

from scripts.cold_gate.artifacts import SERVICES


SHA = re.compile(r"^[0-9a-f]{40}$")


def lock_entries(content: str) -> list[tuple[str, str, str, str]]:
    rows = [
        tuple(line.split("|"))
        for line in content.splitlines()
        if line and not line.startswith("#")
    ]
    if len(rows) != 8 or any(
        len(row) != 4 or SHA.fullmatch(row[2]) is None for row in rows
    ):
        raise RuntimeError("services lock evidence is invalid")
    if [row[0] for row in rows] != ["shared", *SERVICES]:
        raise RuntimeError("services lock evidence order drifted")
    return rows


def jar_row(
    logical: str, source_sha: str, source: str, staged: str, path: Path
) -> str:
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return f"{logical}\t{source_sha}\t{source}\t{staged}\t{digest}"
