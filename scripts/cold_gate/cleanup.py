from __future__ import annotations

import os
import shutil
import subprocess
from collections.abc import Callable
from pathlib import Path

from scripts.cold_gate.cleanup_evidence import CleanupEvidence
from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.owned_path import require_directory


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

    def run(
        self,
        sources: Path | None = None,
        service_jars: Path | None = None,
        evidence: CleanupEvidence | None = None,
    ) -> None:
        self.context.require_owned()
        if evidence is not None and (
            evidence.context is not self.context or sources is None or service_jars is None
        ):
            raise RuntimeError("cleanup evidence targets have different ownership")
        if sources is not None:
            expected = self.context.runtime / "sources"
            if sources != expected or sources.is_symlink() or not sources.is_dir():
                raise RuntimeError("source cleanup target is not owned by this run")
        jars_link = self.context.root / "docker/jars"
        if service_jars is not None:
            expected_parent = self.context.root / "docker/.jars"
            expected_link = f".jars/{service_jars.name}"
            if (
                service_jars.parent != expected_parent
                or not jars_link.is_symlink()
                or os.readlink(jars_link) != expected_link
            ):
                raise RuntimeError("service JAR cleanup target is not owned")
            require_directory(service_jars)

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
        if service_jars is not None:
            jars_link.unlink()
            shutil.rmtree(service_jars)
            service_jars.parent.rmdir()
        if evidence is not None:
            evidence.capture(sources, service_jars)

        self.context.require_owned()
        shutil.rmtree(self.context.runtime)
        (self.context.lock / "owner").unlink()
        self.context.lock.rmdir()
