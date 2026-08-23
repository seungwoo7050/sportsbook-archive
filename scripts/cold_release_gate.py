#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.cold_gate.gate import run_release_gate
from scripts.cold_gate.release_identity import SHA


def clean_head(root: Path) -> str:
    head = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--verify", "HEAD"],
        text=True,
        capture_output=True,
        check=True,
    ).stdout.strip()
    dirty = subprocess.run(
        ["git", "-C", str(root), "status", "--porcelain", "--untracked-files=no"],
        text=True,
        capture_output=True,
        check=True,
    ).stdout
    if SHA.fullmatch(head) is None or dirty:
        raise RuntimeError("cold release gate requires one clean exact HEAD")
    return head


def main() -> None:
    evidence = run_release_gate(ROOT, clean_head(ROOT))
    print(f"cold_gate_evidence={evidence}")


if __name__ == "__main__":
    main()
