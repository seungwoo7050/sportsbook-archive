from __future__ import annotations

from pathlib import Path

from scripts.cold_gate.build import ReleaseBuilder
from scripts.cold_gate.checks import ReleaseChecks
from scripts.cold_gate.cleanup import ScopedCleanup, remove_owned_context
from scripts.cold_gate.cleanup_evidence import CleanupEvidence
from scripts.cold_gate.cleanup_targets import discover_cleanup_targets
from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.redaction import EvidenceRedactor
from scripts.cold_gate.secrets import RuntimeSecrets


def run_release_gate(root: Path, commit: str) -> Path:
    context = ColdGateContext.create(root, commit)
    compose = None
    compose_bound = False
    checks = None
    try:
        compose = ComposeProject(context)
        secrets = RuntimeSecrets.generate(context)
        compose.bind_environment(secrets.environment)
        compose_bound = True
        store = EvidenceStore(context, EvidenceRedactor(secrets.secret_values))
        artifacts = ReleaseBuilder(context, secrets.environment).build()
        checks = ReleaseChecks(context, compose, artifacts, secrets, store)
        checks.run(commit)
        cleanup_evidence = CleanupEvidence(context, store)
        ScopedCleanup(context, compose).run(
            artifacts.sources, artifacts.service_jars, cleanup_evidence
        )
    except Exception as error:
        failures = [error]
        if checks is not None:
            try:
                checks.capture_logs()
            except Exception as log_error:
                failures.append(log_error)
        if compose_bound:
            try:
                sources, service_jars = discover_cleanup_targets(context)
                ScopedCleanup(context, compose).run(sources, service_jars)
            except Exception as cleanup_error:
                failures.append(cleanup_error)
        else:
            try:
                remove_owned_context(context)
            except Exception as cleanup_error:
                failures.append(cleanup_error)
        if len(failures) > 1:
            raise ExceptionGroup("cold release gate and cleanup failed", failures)
        raise
    return context.evidence
