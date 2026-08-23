from __future__ import annotations

import dataclasses
import os
import subprocess
from collections.abc import Callable
from pathlib import Path

from scripts.cold_gate.artifacts import verify_release_artifacts
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.owned_path import ensure_directory


Runner = Callable[..., subprocess.CompletedProcess[str]]


@dataclasses.dataclass(frozen=True)
class ReleaseArtifacts:
    sources: Path
    maven_repository: Path
    service_jars: Path
    fixture_jar: Path


class ReleaseBuilder:
    def __init__(
        self,
        context: ColdGateContext,
        environment: dict[str, str],
        runner: Runner = subprocess.run,
    ) -> None:
        self.context = context
        self.environment = environment
        self.runner = runner

    def build(self) -> ReleaseArtifacts:
        self.context.require_owned()
        root = self.context.root
        sources = self.context.runtime / "sources"
        repository = self.context.runtime / "m2/repository"
        fixture_output = self.context.runtime / "fixtures"
        jars_link = root / "docker/jars"
        generations = root / "docker/.jars"
        if any(path.exists() or path.is_symlink() for path in (jars_link, generations)):
            raise RuntimeError("release JAR staging is not empty")
        ensure_directory(repository.parent)
        ensure_directory(repository)
        ensure_directory(fixture_output)

        commands = (
            [str(root / "scripts/materialize-sources.sh"), str(sources), "materialize"],
            [str(root / "scripts/install-shared.sh"), str(sources), str(repository)],
            [str(root / "scripts/stage-release-jars.sh"), str(sources), str(repository)],
            [
                str(root / "scripts/stage-fixture-publisher.sh"),
                str(sources),
                str(repository),
                str(fixture_output),
            ],
        )
        for command in commands:
            environment = self.environment.copy()
            if command[0].endswith("stage-release-jars.sh"):
                environment["DOCKER_OUTPUT_ROOT"] = str(root / "docker")
            self.runner(
                command,
                cwd=root,
                env=environment,
                text=True,
                capture_output=True,
                check=True,
            )

        fixture = fixture_output / "avro-fixture-publisher.jar"
        if not jars_link.is_symlink() or not fixture.is_file():
            raise RuntimeError("release artifacts are incomplete")
        service_jars = (root / "docker" / os.readlink(jars_link)).resolve(strict=True)
        if len(list(service_jars.glob("*.jar"))) != 7:
            raise RuntimeError("service JAR generation is incomplete")
        verify_release_artifacts(service_jars)
        return ReleaseArtifacts(sources, repository, service_jars, fixture)
