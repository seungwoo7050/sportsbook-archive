from __future__ import annotations

import dataclasses
import subprocess
from collections.abc import Callable
from pathlib import Path


Runner = Callable[..., subprocess.CompletedProcess[str]]
FORMAT = "%x1e%H%x1f%P%x1f%B%x1f"


@dataclasses.dataclass(frozen=True)
class Change:
    path: str
    additions: int
    deletions: int


@dataclasses.dataclass(frozen=True)
class Commit:
    sha: str
    parents: tuple[str, ...]
    message: str
    changes: tuple[Change, ...]

    @property
    def subject(self) -> str:
        return self.message.splitlines()[0] if self.message else ""

    @property
    def churn(self) -> int:
        return sum(change.additions + change.deletions for change in self.changes)


def load_history(
    root: Path,
    revision: str = "HEAD",
    runner: Runner = subprocess.run,
) -> tuple[Commit, ...]:
    result = runner(
        [
            "git", "-C", str(root), "log", "--reverse", "--no-renames",
            f"--format={FORMAT}", "--numstat", revision,
        ],
        text=True,
        capture_output=True,
        check=True,
    )
    commits = []
    for raw in result.stdout.split("\x1e")[1:]:
        fields = raw.split("\x1f", 3)
        if len(fields) != 4:
            raise RuntimeError("Git history record is malformed")
        sha, parents, message, stats = fields
        changes = []
        for line in stats.splitlines():
            if not line:
                continue
            columns = line.split("\t", 2)
            if len(columns) != 3 or not columns[0].isdigit() or not columns[1].isdigit():
                raise RuntimeError(f"{sha[:12]} contains an unmeasured change")
            changes.append(Change(columns[2], int(columns[0]), int(columns[1])))
        commits.append(
            Commit(sha, tuple(parents.split()) if parents else (), message.rstrip("\n"),
                   tuple(changes))
        )
    if not commits:
        raise RuntimeError("Git history is empty")
    return tuple(commits)
