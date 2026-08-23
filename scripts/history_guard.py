#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

from scripts.cold_gate.history_policy import verify_history
from scripts.cold_gate.history_repository import load_history


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    commits = load_history(root)
    verify_history(commits)
    print(f"history_guard_commits={len(commits)}")


if __name__ == "__main__":
    main()
