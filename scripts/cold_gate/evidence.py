from __future__ import annotations

import os
import re
import tempfile
from pathlib import Path, PurePosixPath

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.inventory import SERVICES
from scripts.cold_gate.owned_path import (
    ensure_directory,
    require_directory,
    require_regular_file,
)
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
LOG_SERVICES = frozenset(SERVICES)
LOG_PATTERN = re.compile(r"logs/([a-z0-9][a-z0-9-]*)\.log")


class EvidenceStore:
    def __init__(self, context: ColdGateContext, redactor: EvidenceRedactor) -> None:
        self.context = context
        self.redactor = redactor

    def write(self, relative_name: str, content: str) -> Path:
        self.context.require_owned()
        self._validate_name(relative_name)
        limit = 1024 * 1024 if relative_name.startswith("logs/") else 256 * 1024
        if "\0" in content or len(content.encode()) > limit:
            raise RuntimeError("evidence content is unsafe or too large")
        redacted = self.redactor.redact(content)
        self.redactor.require_clean(redacted)
        target = self.context.evidence / relative_name
        if target.parent == self.context.evidence:
            require_directory(target.parent)
        elif target.parent == self.context.evidence / "logs":
            ensure_directory(target.parent)
        else:
            raise RuntimeError("evidence parent is not owned")
        if target.exists() or target.is_symlink():
            raise RuntimeError("evidence files are write-once")

        pending: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="w", dir=target.parent, prefix=".pending.", delete=False
            ) as output:
                output.write(redacted)
                pending = Path(output.name)
            os.replace(pending, target)
            require_regular_file(target)
        finally:
            if pending is not None and pending.exists():
                pending.unlink()
        return target

    def verify(self, complete: bool = False) -> None:
        self.context.require_owned()
        found = set()
        for path in self.context.evidence.rglob("*"):
            if path.is_dir():
                if path != self.context.evidence / "logs":
                    raise RuntimeError("evidence contains an unknown directory")
                require_directory(path)
                continue
            require_regular_file(path)
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
        log_match = LOG_PATTERN.fullmatch(relative_name)
        if relative_name not in REQUIRED_FILES and (
            log_match is None or log_match.group(1) not in LOG_SERVICES
        ):
            raise RuntimeError("evidence file is not in the whitelist")
