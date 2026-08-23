import types
import unittest
from unittest import mock

import scripts.cold_gate.checks as subject
from scripts.cold_gate.scenario_evidence import EXPECTED_SCENARIOS


COMMIT = "0" * 40


def capturer(events, name):
    instance = mock.Mock()
    instance.capture.side_effect = lambda *_args: events.append(name)
    return mock.Mock(return_value=instance)


class ReleaseChecksTest(unittest.TestCase):
    def test_runs_one_ordered_semantic_gate_and_captures_logs_once(self):
        events = []
        context = object()
        compose = types.SimpleNamespace(context=context)
        store = types.SimpleNamespace(context=context)
        artifacts = object()
        secrets = types.SimpleNamespace(environment={"KEY": "value"}, secret_values=("secret",))
        stack = mock.Mock()
        stack.start.side_effect = lambda _environment: events.append("start")
        replacements = {
            "ColdStack": mock.Mock(return_value=stack),
            "ReleaseEvidence": capturer(events, "release"),
            "RuntimeEvidence": capturer(events, "runtime"),
            "TopicEvidence": capturer(events, "topics"),
            "MigrationEvidence": capturer(events, "migrations"),
            "ReadinessEvidence": capturer(events, "readiness"),
            "ScenarioEvidence": capturer(events, "scenarios"),
            "LogEvidence": capturer(events, "logs"),
            "PostgresClient": mock.Mock(return_value="database"),
            "E2eRuntime": mock.Mock(return_value="e2e-runtime"),
            "capture_compose_config": mock.Mock(side_effect=lambda *_args: events.append("compose")),
            "run_all": mock.Mock(
                side_effect=lambda runtime: events.append("e2e") or EXPECTED_SCENARIOS
            ),
        }
        with mock.patch.multiple(subject, **replacements):
            checks = subject.ReleaseChecks(context, compose, artifacts, secrets, store)

            passed = checks.run(COMMIT)
            checks.capture_logs()

        self.assertEqual(passed, EXPECTED_SCENARIOS)
        self.assertEqual(
            events,
            [
                "release",
                "compose",
                "start",
                "topics",
                "migrations",
                "e2e",
                "runtime",
                "readiness",
                "scenarios",
                "logs",
            ],
        )
        replacements["ReleaseEvidence"].return_value.capture.assert_called_once_with(COMMIT)
        replacements["run_all"].assert_called_once_with("e2e-runtime")
        replacements["ScenarioEvidence"].return_value.capture.assert_called_once_with(
            EXPECTED_SCENARIOS
        )

    def test_rejects_cross_owned_compose_or_store(self):
        context = object()
        owned = types.SimpleNamespace(context=context)
        foreign = types.SimpleNamespace(context=object())
        stack = mock.Mock()
        with mock.patch.object(subject, "ColdStack", return_value=stack):
            with self.assertRaisesRegex(RuntimeError, "ownership"):
                subject.ReleaseChecks(context, foreign, object(), object(), owned)
            with self.assertRaisesRegex(RuntimeError, "ownership"):
                subject.ReleaseChecks(context, owned, object(), object(), foreign)
        stack.assert_not_called()


if __name__ == "__main__":
    unittest.main()
