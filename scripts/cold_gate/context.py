from __future__ import annotations

import dataclasses
import re
import secrets
from pathlib import Path


PROJECT_PATTERN = re.compile(r"^sb-gate-[0-9a-f]{12}-[0-9a-f]{8}$")


@dataclasses.dataclass(frozen=True)
class ColdGateContext:
    root: Path
    project: str
    runtime: Path
    evidence: Path
    lock: Path

    @classmethod
    def create(
        cls, root: Path, commit: str, nonce: str | None = None
    ) -> "ColdGateContext":
        root = root.resolve(strict=True)
        if not re.fullmatch(r"[0-9a-f]{40}", commit):
            raise ValueError("commit must be a full lowercase SHA")
        nonce = nonce or secrets.token_hex(4)
        if not re.fullmatch(r"[0-9a-f]{8}", nonce):
            raise ValueError("nonce must be eight lowercase hex characters")
        project = f"sb-gate-{commit[:12]}-{nonce}"
        if not PROJECT_PATTERN.fullmatch(project):
            raise ValueError("generated project name is invalid")

        runtime_root = root / ".runtime"
        runtime_root.mkdir(exist_ok=True)
        lock = runtime_root / "cold-gate.lock"
        try:
            lock.mkdir()
        except FileExistsError as exception:
            raise RuntimeError("another cold gate owns this worktree") from exception

        runtime = runtime_root / "cold-gate" / project
        evidence = root / "evidence" / "cold-gate" / project
        try:
            if runtime.exists() or evidence.exists():
                raise RuntimeError("cold gate run path already exists")
            runtime.mkdir(parents=True)
            evidence.mkdir(parents=True)
            marker = f"project={project}\nroot={root}\n"
            (runtime / ".owner").write_text(marker)
            (lock / "owner").write_text(marker)
        except BaseException:
            lock.rmdir()
            raise
        return cls(root, project, runtime, evidence, lock)

    def require_owned(self) -> None:
        if not PROJECT_PATTERN.fullmatch(self.project):
            raise RuntimeError("invalid cold gate project")
        expected = f"project={self.project}\nroot={self.root}\n"
        if (self.runtime / ".owner").read_text() != expected:
            raise RuntimeError("runtime ownership marker mismatch")
        if (self.lock / "owner").read_text() != expected:
            raise RuntimeError("lock ownership marker mismatch")
        for path, parent in (
            (self.runtime, self.root / ".runtime" / "cold-gate"),
            (self.evidence, self.root / "evidence" / "cold-gate"),
        ):
            if path.is_symlink() or path.parent.resolve() != parent.resolve():
                raise RuntimeError("cold gate path escaped its owned parent")
