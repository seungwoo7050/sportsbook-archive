from __future__ import annotations

import shutil
import subprocess
from collections.abc import Callable
from pathlib import Path

from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.context import ColdGateContext


Runner = Callable[..., subprocess.CompletedProcess[str]]


class ScopedCleanup:
    def __init__(
        self,
        context: ColdGateContext,
        compose: ComposeProject,
        runner: Runner = subprocess.run,
    ) -> None:
        self.context = context
        self.compose = compose
        if compose.context is not context:
            raise RuntimeError("cleanup Compose project has different ownership")
        self.runner = runner

    def run(self, sources: Path | None = None) -> None:
        self.context.require_owned()
        if sources is not None:
            expected = self.context.runtime / "sources"
            if sources != expected or sources.is_symlink() or not sources.is_dir():
                raise RuntimeError("source cleanup target is not owned by this run")

        self.compose.run(
            "down",
            "--volumes",
            "--remove-orphans",
            "--rmi",
            "local",
            "--timeout",
            "30",
        )
        self.compose.require_absent()

        if sources is not None:
            self.runner(
                [
                    str(self.context.root / "scripts" / "materialize-sources.sh"),
                    str(sources),
                    "cleanup",
                ],
                cwd=self.context.root,
                text=True,
                capture_output=True,
                check=True,
            )
            if sources.exists() or sources.is_symlink():
                raise RuntimeError("materialized sources remain after cleanup")

        self.context.require_owned()
        shutil.rmtree(self.context.runtime)
        (self.context.lock / "owner").unlink()
        self.context.lock.rmdir()
