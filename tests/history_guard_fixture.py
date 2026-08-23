from __future__ import annotations

import pathlib
import subprocess

from scripts.cold_gate.history_rules import ROOT_SUBJECT


def git(root: pathlib.Path, *arguments: str) -> str:
    return subprocess.run(
        ["git", "-C", str(root), *arguments],
        text=True,
        capture_output=True,
        check=True,
    ).stdout.strip()


def commit_file(root: pathlib.Path, path: str, content: str, subject: str) -> None:
    target = root / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content)
    git(root, "add", "--", path)
    git(root, "commit", "--quiet", "-m", subject)


def create_long_history(
    root: pathlib.Path,
    pairs: int = 120,
    fault_pair: int | None = None,
) -> None:
    git(root, "init", "--quiet", "--initial-branch=main")
    git(root, "config", "user.name", "History Guard")
    git(root, "config", "user.email", "history@example.invalid")
    commit_file(root, "README.md", "# Fixture\n", ROOT_SUBJECT)
    for number in range(1, pairs + 1):
        production_subject = f"build(unit): add bounded unit {number:03d}"
        if number == fault_pair:
            production_subject = "fix(unit): record provenance"
        commit_file(
            root,
            f"src/unit_{number:03d}.txt",
            f"unit={number}\n",
            production_subject,
        )
        commit_file(
            root,
            f"tests/unit_{number:03d}.txt",
            f"verified={number}\n",
            f"test(unit): verify bounded unit {number:03d}",
        )
