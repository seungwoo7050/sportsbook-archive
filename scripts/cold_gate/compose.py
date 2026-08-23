from __future__ import annotations

import os
import subprocess
from collections.abc import Callable, Iterable
from pathlib import Path

from scripts.cold_gate.context import ColdGateContext


Runner = Callable[..., subprocess.CompletedProcess[str]]


class ComposeProject:
    def __init__(
        self,
        context: ColdGateContext,
        overlays: Iterable[Path] = (),
        runner: Runner = subprocess.run,
    ) -> None:
        self.context = context
        self.files = (context.root / "compose.yaml", *overlays)
        if not all(path.is_file() for path in self.files):
            raise RuntimeError("a tracked Compose input is missing")
        self.runner = runner

    def command(self, *arguments: str) -> list[str]:
        command = [
            "docker",
            "compose",
            "--project-name",
            self.context.project,
            "--project-directory",
            str(self.context.root),
        ]
        for path in self.files:
            command.extend(("--file", str(path)))
        return [*command, *arguments]

    def run(
        self,
        *arguments: str,
        environment: dict[str, str] | None = None,
        check: bool = True,
        capture_output: bool = False,
    ) -> subprocess.CompletedProcess[str]:
        self.context.require_owned()
        return self.runner(
            self.command(*arguments),
            cwd=self.context.root,
            env=environment or os.environ.copy(),
            text=True,
            capture_output=capture_output,
            check=check,
        )

    def require_absent(self) -> None:
        self.context.require_owned()
        label = f"label=com.docker.compose.project={self.context.project}"
        commands = (
            ["docker", "ps", "--all", "--quiet", "--filter", label],
            ["docker", "network", "ls", "--quiet", "--filter", label],
            ["docker", "volume", "ls", "--quiet", "--filter", label],
        )
        for command in commands:
            result = self.runner(
                command,
                cwd=self.context.root,
                text=True,
                capture_output=True,
                check=True,
            )
            if result.stdout.strip():
                raise RuntimeError("cold project already owns Docker resources")
