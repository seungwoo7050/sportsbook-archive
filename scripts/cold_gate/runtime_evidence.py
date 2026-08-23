from __future__ import annotations

import json
import re
import subprocess
from collections.abc import Callable

from scripts.cold_gate.artifacts import verify_release_artifacts
from scripts.cold_gate.build import ReleaseArtifacts
from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.container_state import ContainerState, HEX64
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.inventory import APPLICATION_SERVICES, SERVICES


Runner = Callable[..., subprocess.CompletedProcess[str]]
INSPECT_FORMAT = (
    '{{.Id}}\t{{.Name}}\t{{.Image}}\t{{.State.Status}}\t'
    '{{if .State.Health}}{{.State.Health.Status}}{{else}}-{{end}}\t'
    '{{.State.ExitCode}}\t{{index .Config.Labels "com.docker.compose.project"}}\t'
    '{{index .Config.Labels "com.docker.compose.service"}}'
)
JAR_SUM = re.compile(r"^([0-9a-f]{64})  /app/app\.jar\n?$")


class RuntimeEvidence:
    def __init__(
        self,
        compose: ComposeProject,
        artifacts: ReleaseArtifacts,
        store: EvidenceStore,
        runner: Runner = subprocess.run,
    ) -> None:
        self.compose = compose
        self.artifacts = artifacts
        self.store = store
        self.runner = runner

    def capture(self) -> None:
        self.compose.context.require_owned()
        release_hashes = verify_release_artifacts(self.artifacts.service_jars)
        states = []
        image_rows = ["service\timage_id\tembedded_jar_sha256"]
        for service in SERVICES:
            container_id = self.compose.run(
                "ps", "--all", "--quiet", service, capture_output=True
            ).stdout.strip()
            if HEX64.fullmatch(container_id) is None:
                raise RuntimeError(f"{service} does not have one scoped container")
            try:
                inspected = self.runner(
                    ["docker", "inspect", "--format", INSPECT_FORMAT, container_id],
                    cwd=self.compose.context.root,
                    text=True,
                    capture_output=True,
                    check=True,
                ).stdout
            except subprocess.CalledProcessError as error:
                raise RuntimeError(f"Docker inspection failed for {service}") from error
            state = ContainerState.parse(
                inspected, self.compose.context.project, service
            )
            embedded = "-"
            if service in APPLICATION_SERVICES:
                output = self.compose.run(
                    "exec", "-T", service, "sha256sum", "/app/app.jar", capture_output=True
                ).stdout
                match = JAR_SUM.fullmatch(output)
                if match is None or match.group(1) != release_hashes[f"{service}.jar"]:
                    raise RuntimeError(f"{service} embedded release JAR drifted")
                embedded = match.group(1)
            image_rows.append(f"{service}\t{state.image_id}\t{embedded}")
            states.append(
                {
                    "service": service,
                    "name": state.name,
                    "image_id": state.image_id,
                    "state": state.state,
                    "health": state.health,
                    "exit_code": state.exit_code,
                }
            )
        self.store.write("images.tsv", "\n".join(image_rows) + "\n")
        self.store.write(
            "compose-ps.json",
            json.dumps(states, sort_keys=True, separators=(",", ":")) + "\n",
        )
