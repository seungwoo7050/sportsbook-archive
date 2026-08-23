from __future__ import annotations

import dataclasses
import re
import secrets
from pathlib import Path

from scripts.cold_gate.owned_path import (
    ensure_directory,
    require_directory,
    require_regular_file,
)


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

        require_directory(root)
        runtime_root = root / ".runtime"
        runtime_parent = runtime_root / "cold-gate"
        evidence_root = root / "evidence"
        evidence_parent = evidence_root / "cold-gate"
        lock = runtime_root / "cold-gate.lock"
        runtime = runtime_parent / project
        evidence = evidence_parent / project
        owned_directories: list[Path] = []
        try:
            for parent in (runtime_root, runtime_parent, evidence_root, evidence_parent):
                if ensure_directory(parent):
                    owned_directories.append(parent)
            if lock.exists() or lock.is_symlink():
                raise RuntimeError("another cold gate owns this worktree")
            lock.mkdir()
            owned_directories.append(lock)
            require_directory(lock)
            if any(path.exists() or path.is_symlink() for path in (runtime, evidence)):
                raise RuntimeError("cold gate run path already exists")
            for path in (runtime, evidence):
                path.mkdir()
                owned_directories.append(path)
                require_directory(path)
            marker = f"project={project}\nroot={root}\n"
            (runtime / ".owner").write_text(marker)
            (lock / "owner").write_text(marker)
        except BaseException:
            for marker_path in (runtime / ".owner", lock / "owner"):
                marker_path.unlink(missing_ok=True)
            for directory in reversed(owned_directories):
                try:
                    directory.rmdir()
                except OSError:
                    pass
            raise
        return cls(root, project, runtime, evidence, lock)

    def require_owned(self) -> None:
        if not PROJECT_PATTERN.fullmatch(self.project):
            raise RuntimeError("invalid cold gate project")
        expected_runtime = self.root / ".runtime" / "cold-gate" / self.project
        expected_evidence = self.root / "evidence" / "cold-gate" / self.project
        expected_lock = self.root / ".runtime" / "cold-gate.lock"
        if (self.runtime, self.evidence, self.lock) != (
            expected_runtime,
            expected_evidence,
            expected_lock,
        ):
            raise RuntimeError("cold gate paths do not match their project")
        for directory in (
            self.root,
            self.root / ".runtime",
            self.runtime.parent,
            self.root / "evidence",
            self.evidence.parent,
            self.runtime,
            self.evidence,
            self.lock,
        ):
            require_directory(directory)
        expected = f"project={self.project}\nroot={self.root}\n"
        runtime_marker = self.runtime / ".owner"
        lock_marker = self.lock / "owner"
        require_regular_file(runtime_marker)
        require_regular_file(lock_marker)
        if runtime_marker.read_text() != expected:
            raise RuntimeError("runtime ownership marker mismatch")
        if lock_marker.read_text() != expected:
            raise RuntimeError("lock ownership marker mismatch")
