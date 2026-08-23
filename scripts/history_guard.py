#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.cold_gate.history_policy import verify_history
from scripts.cold_gate.history_repository import load_history


def main() -> None:
    commits = load_history(ROOT)
    verify_history(commits)
    print(f"history_guard_commits={len(commits)}")


if __name__ == "__main__":
    main()
