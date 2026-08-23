#!/usr/bin/env python3
from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHA = re.compile(r"^[0-9a-f]{40}$")
EXECUTABLE_TREES = ("scripts", "e2e")


def clean_head(root: Path) -> str:
    head = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--verify", "HEAD"],
        text=True,
        capture_output=True,
        check=True,
    ).stdout.strip()
    dirty = subprocess.run(
        ["git", "-C", str(root), "status", "--porcelain", "--untracked-files=all"],
        text=True,
        capture_output=True,
        check=True,
    ).stdout
    if SHA.fullmatch(head) is None or dirty:
        raise RuntimeError("cold release gate requires one clean exact HEAD")
    return head


def reject_executable_caches(root: Path) -> None:
    for name in EXECUTABLE_TREES:
        tree = root / name
        if not tree.is_dir() or tree.is_symlink():
            raise RuntimeError(f"release executable tree is invalid: {name}")
        for _parent, directories, files in os.walk(tree, followlinks=False):
            if "__pycache__" in directories or any(
                filename.endswith((".pyc", ".pyo")) for filename in files
            ):
                raise RuntimeError(f"release executable cache exists under {name}")


def _load_gate():
    if str(ROOT) not in sys.path:
        sys.path.insert(0, str(ROOT))
    from scripts.cold_gate.gate import run_release_gate

    return run_release_gate


def main() -> None:
    head = clean_head(ROOT)
    reject_executable_caches(ROOT)
    evidence = _load_gate()(ROOT, head)
    print(f"cold_gate_evidence={evidence}")


if __name__ == "__main__":
    main()
