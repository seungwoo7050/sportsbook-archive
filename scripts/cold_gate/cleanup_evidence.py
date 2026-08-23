from __future__ import annotations

import subprocess
from collections.abc import Callable
from pathlib import Path

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore


Runner = Callable[..., subprocess.CompletedProcess[str]]


class CleanupEvidence:
    def __init__(
        self,
        context: ColdGateContext,
        store: EvidenceStore,
        runner: Runner = subprocess.run,
    ) -> None:
        if store.context is not context:
            raise RuntimeError("cleanup evidence ownership mismatch")
        self.context = context
        self.store = store
        self.runner = runner

    def capture(self, sources: Path, service_jars: Path) -> None:
        self.context.require_owned()
        expected_sources = self.context.runtime / "sources"
        expected_jars = self.context.root / "docker/.jars" / service_jars.name
        if sources != expected_sources or service_jars != expected_jars:
            raise RuntimeError("cleanup evidence targets are not owned")
        label = f"label=com.docker.compose.project={self.context.project}"
        commands = (
            ("containers", ["docker", "ps", "--all", "--quiet", "--filter", label]),
            ("networks", ["docker", "network", "ls", "--quiet", "--filter", label]),
            ("volumes", ["docker", "volume", "ls", "--quiet", "--filter", label]),
        )
        rows = ["resource\tremaining"]
        for resource, command in commands:
            try:
                output = self.runner(
                    command,
                    cwd=self.context.root,
                    text=True,
                    capture_output=True,
                    check=True,
                ).stdout
            except subprocess.CalledProcessError as error:
                raise RuntimeError(f"cleanup query failed for {resource}") from error
            if output.strip():
                raise RuntimeError(f"cleanup left scoped {resource}")
            rows.append(f"{resource}\t0")
        paths = (
            ("sources", sources),
            ("jar-link", self.context.root / "docker/jars"),
            ("jar-generation", service_jars),
        )
        for resource, path in paths:
            if path.exists() or path.is_symlink():
                raise RuntimeError(f"cleanup left {resource}")
            rows.append(f"{resource}\t0")
        self.store.write("cleanup.tsv", "\n".join(rows) + "\n")
