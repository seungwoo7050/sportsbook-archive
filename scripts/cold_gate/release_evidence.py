from __future__ import annotations

import hashlib
import subprocess
from collections.abc import Callable

from scripts.cold_gate.artifacts import SERVICES, verify_release_artifacts
from scripts.cold_gate.build import ReleaseArtifacts
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.owned_path import require_regular_file
from scripts.cold_gate.release_identity import SHA, jar_row, lock_entries


Runner = Callable[..., subprocess.CompletedProcess[str]]
class ReleaseEvidence:
    def __init__(
        self,
        context: ColdGateContext,
        artifacts: ReleaseArtifacts,
        store: EvidenceStore,
        runner: Runner = subprocess.run,
    ) -> None:
        self.context = context
        self.artifacts = artifacts
        self.store = store
        self.runner = runner

    def capture(self, orchestration_sha: str) -> None:
        self.context.require_owned()
        if not SHA.fullmatch(orchestration_sha) or orchestration_sha[:12] not in self.context.project:
            raise RuntimeError("orchestration identity does not own this cold run")
        head = self._git("rev-parse", "HEAD")
        dirty = self._git("status", "--porcelain", "--untracked-files=no")
        if head != orchestration_sha or dirty:
            raise RuntimeError("orchestration checkout is not the clean requested SHA")

        lock_path = self.context.root / "services.lock"
        require_regular_file(lock_path)
        lock_content = lock_path.read_text()
        entries = lock_entries(lock_content)
        for logical, _branch, commit, _artifact in entries:
            source = self.artifacts.sources / logical
            observed = self._git("-C", str(source), "rev-parse", "HEAD")
            if observed != commit:
                raise RuntimeError(f"{logical} materialized SHA drifted")

        service_hashes = verify_release_artifacts(self.artifacts.service_jars)
        rows = ["logical\tsource_sha\tsource_artifact\tstaged_artifact\tsha256"]
        shared = (
            self.artifacts.maven_repository
            / "com/sportsbook/shared-protocol/1.0.0/shared-protocol-1.0.0.jar"
        )
        require_regular_file(shared)
        shared_entry = entries[0]
        rows.append(jar_row("shared", shared_entry[2], shared_entry[3], shared.name, shared))
        entry_by_name = {entry[0]: entry for entry in entries}
        for logical in SERVICES:
            entry = entry_by_name[logical]
            jar = self.artifacts.service_jars / f"{logical}.jar"
            row = jar_row(logical, entry[2], entry[3], jar.name, jar)
            if row.rsplit("\t", 1)[1] != service_hashes[jar.name]:
                raise RuntimeError(f"{logical} staged checksum drifted")
            rows.append(row)
        require_regular_file(self.artifacts.fixture_jar)
        rows.append(
            jar_row(
                "fixture",
                orchestration_sha,
                "fixtures/avro-publisher/pom.xml",
                self.artifacts.fixture_jar.name,
                self.artifacts.fixture_jar,
            )
        )
        lock_hash = hashlib.sha256(lock_content.encode()).hexdigest()
        self.store.write(
            "run.tsv",
            "field\tvalue\n"
            f"project\t{self.context.project}\n"
            f"orchestration_sha\t{orchestration_sha}\n"
            f"services_lock_sha256\t{lock_hash}\n",
        )
        self.store.write("services.lock", lock_content)
        self.store.write("jars.sha256", "\n".join(rows) + "\n")

    def _git(self, *arguments: str) -> str:
        result = self.runner(
            ["git", "-C", str(self.context.root), *arguments],
            text=True,
            capture_output=True,
            check=True,
        )
        return result.stdout.strip()
