from __future__ import annotations

import os
import re
import tempfile
from pathlib import Path, PurePosixPath

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.redaction import EvidenceRedactor


REQUIRED_FILES = frozenset(
    {
        "run.tsv",
        "services.lock",
        "jars.sha256",
        "images.tsv",
        "compose.sha256",
        "topics.tsv",
        "migrations.tsv",
        "readiness.tsv",
        "scenarios.tsv",
        "compose-ps.json",
        "cleanup.tsv",
    }
)
LOG_PATTERN = re.compile(r"logs/[a-z0-9][a-z0-9-]*\.log")


class EvidenceStore:
    def __init__(self, context: ColdGateContext, redactor: EvidenceRedactor) -> None:
        self.context = context
        self.redactor = redactor

    def write(self, relative_name: str, content: str) -> Path:
        self.context.require_owned()
        self._validate_name(relative_name)
        redacted = self.redactor.redact(content)
        self.redactor.require_clean(redacted)
        target = self.context.evidence / relative_name
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.is_symlink():
            raise RuntimeError("evidence target must not be a symlink")

        pending: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="w", dir=target.parent, prefix=".pending.", delete=False
            ) as output:
                output.write(redacted)
                pending = Path(output.name)
            os.replace(pending, target)
        finally:
            if pending is not None and pending.exists():
                pending.unlink()
        return target

    def verify(self, complete: bool = False) -> None:
        self.context.require_owned()
        found = set()
        for path in self.context.evidence.rglob("*"):
            if path.is_symlink():
                raise RuntimeError("evidence must not contain symlinks")
            if path.is_dir():
                continue
            relative_name = path.relative_to(self.context.evidence).as_posix()
            self._validate_name(relative_name)
            found.add(relative_name)
            self.redactor.require_clean(path.read_text())
        if complete and not REQUIRED_FILES.issubset(found):
            raise RuntimeError("evidence inventory is incomplete")

    @staticmethod
    def _validate_name(relative_name: str) -> None:
        path = PurePosixPath(relative_name)
        if path.is_absolute() or ".." in path.parts:
            raise RuntimeError("evidence path escaped its root")
        if relative_name not in REQUIRED_FILES and not LOG_PATTERN.fullmatch(relative_name):
            raise RuntimeError("evidence file is not in the whitelist")
