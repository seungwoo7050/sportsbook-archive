import pathlib
import types
import unittest
from unittest import mock

import scripts.cold_gate.gate as subject


COMMIT = "0" * 40


class ReleaseGateTest(unittest.TestCase):
    def fixture(self):
        context = types.SimpleNamespace(evidence=pathlib.Path("/evidence"))
        compose = mock.Mock()
        secrets = types.SimpleNamespace(environment={"KEY": "value"}, secret_values=("secret",))
        artifacts = types.SimpleNamespace(sources=pathlib.Path("/sources"),
                                          service_jars=pathlib.Path("/jars"))
        store = object()
        checks = mock.Mock()
        cleanup = mock.Mock()
        replacements = {
            "ColdGateContext": mock.Mock(),
            "ComposeProject": mock.Mock(return_value=compose),
            "RuntimeSecrets": mock.Mock(),
            "EvidenceRedactor": mock.Mock(return_value="redactor"),
            "EvidenceStore": mock.Mock(return_value=store),
            "ReleaseBuilder": mock.Mock(),
            "ReleaseChecks": mock.Mock(return_value=checks),
            "CleanupEvidence": mock.Mock(return_value="receipt"),
            "ScopedCleanup": mock.Mock(return_value=cleanup),
            "discover_cleanup_targets": mock.Mock(
                return_value=(artifacts.sources, artifacts.service_jars)
            ),
        }
        replacements["ColdGateContext"].create.return_value = context
        replacements["RuntimeSecrets"].generate.return_value = secrets
        replacements["ReleaseBuilder"].return_value.build.return_value = artifacts
        return context, compose, secrets, artifacts, store, checks, cleanup, replacements

    def test_runs_checks_then_attested_cleanup(self):
        values = self.fixture()
        context, compose, secrets, artifacts, store, checks, cleanup, replacements = values
        root = pathlib.Path("/release")
        with mock.patch.multiple(subject, **replacements):
            evidence = subject.run_release_gate(root, COMMIT)

        self.assertEqual(evidence, context.evidence)
        compose.bind_environment.assert_called_once_with(secrets.environment)
        replacements["ReleaseBuilder"].assert_called_once_with(context, secrets.environment)
        replacements["ReleaseChecks"].assert_called_once_with(
            context, compose, artifacts, secrets, store
        )
        checks.run.assert_called_once_with(COMMIT)
        cleanup.run.assert_called_once_with(
            artifacts.sources, artifacts.service_jars, "receipt"
        )
        replacements["discover_cleanup_targets"].assert_not_called()

    def test_failure_captures_logs_and_cleans_discovered_targets(self):
        values = self.fixture()
        _context, _compose, _secrets, artifacts, _store, checks, cleanup, replacements = values
        checks.run.side_effect = RuntimeError("scenario failed")
        with mock.patch.multiple(subject, **replacements):
            with self.assertRaisesRegex(RuntimeError, "scenario failed"):
                subject.run_release_gate(pathlib.Path("/release"), COMMIT)

        checks.capture_logs.assert_called_once_with()
        cleanup.run.assert_called_once_with(artifacts.sources, artifacts.service_jars)
        replacements["CleanupEvidence"].assert_not_called()

    def test_preserves_primary_log_and_cleanup_failures(self):
        values = self.fixture()
        _context, _compose, _secrets, _artifacts, _store, checks, cleanup, replacements = values
        checks.run.side_effect = RuntimeError("primary")
        checks.capture_logs.side_effect = RuntimeError("logs")
        cleanup.run.side_effect = RuntimeError("cleanup")
        with mock.patch.multiple(subject, **replacements):
            with self.assertRaises(ExceptionGroup) as captured:
                subject.run_release_gate(pathlib.Path("/release"), COMMIT)

        self.assertEqual(
            [str(error) for error in captured.exception.exceptions],
            ["primary", "logs", "cleanup"],
        )


if __name__ == "__main__":
    unittest.main()
