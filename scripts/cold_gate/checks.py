from __future__ import annotations

from e2e.runtime import E2eRuntime
from e2e.scenarios import run_all
from scripts.cold_gate.build import ReleaseArtifacts
from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.compose_evidence import capture_compose_config
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.database import PostgresClient
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.log_evidence import LogEvidence
from scripts.cold_gate.migration_evidence import MigrationEvidence
from scripts.cold_gate.readiness_evidence import ReadinessEvidence
from scripts.cold_gate.release_evidence import ReleaseEvidence
from scripts.cold_gate.runtime_evidence import RuntimeEvidence
from scripts.cold_gate.scenario_evidence import ScenarioEvidence
from scripts.cold_gate.secrets import RuntimeSecrets
from scripts.cold_gate.stack import ColdStack
from scripts.cold_gate.topic_evidence import TopicEvidence


class ReleaseChecks:
    def __init__(
        self,
        context: ColdGateContext,
        compose: ComposeProject,
        artifacts: ReleaseArtifacts,
        secrets: RuntimeSecrets,
        store: EvidenceStore,
    ) -> None:
        if compose.context is not context or store.context is not context:
            raise RuntimeError("release checks ownership mismatch")
        self.context = context
        self.compose = compose
        self.artifacts = artifacts
        self.secrets = secrets
        self.store = store
        self.stack = ColdStack(context, compose)
        self.logs_captured = False

    def run(self, commit: str) -> tuple[str, ...]:
        ReleaseEvidence(self.context, self.artifacts, self.store).capture(commit)
        capture_compose_config(
            self.compose,
            self.store,
            self.secrets.environment,
            self.secrets.secret_values,
        )
        self.stack.start(self.secrets.environment)
        TopicEvidence(self.compose, self.store).capture()
        MigrationEvidence(
            self.artifacts, PostgresClient(self.compose), self.store
        ).capture()
        passed = run_all(
            E2eRuntime(self.context, self.compose, self.artifacts, self.secrets)
        )
        RuntimeEvidence(self.compose, self.artifacts, self.store).capture()
        ReadinessEvidence(self.compose, self.store).capture()
        ScenarioEvidence(self.store).capture(passed)
        self.capture_logs()
        return passed

    def capture_logs(self) -> None:
        if self.logs_captured:
            return
        LogEvidence(self.stack, self.store).capture()
        self.logs_captured = True
