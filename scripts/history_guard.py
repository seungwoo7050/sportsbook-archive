#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.cold_gate.history_policy import verify_history
from scripts.cold_gate.history_repository import Commit, load_history
from scripts.cold_gate.history_rules import DOCS_SUBJECT


def verify_version(root: Path, commits: tuple[Commit, ...]) -> None:
    version = root / "VERSION"
    released = commits[-1].subject == DOCS_SUBJECT
    if released and (version.is_symlink() or not version.is_file() or version.read_text() != "1.0.0\n"):
        raise RuntimeError("released VERSION content is not exactly 1.0.0")
    if not released and (version.exists() or version.is_symlink()):
        raise RuntimeError("VERSION exists before the terminal release pair")


def main() -> None:
    commits = load_history(ROOT)
    verify_history(commits)
    verify_version(ROOT, commits)
    print(f"history_guard_commits={len(commits)}")


if __name__ == "__main__":
    main()
