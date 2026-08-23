import json
import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.compose_evidence import capture_compose_config
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.inventory import SERVICES
from scripts.cold_gate.redaction import EvidenceRedactor


SHA = "0123456789abcdef0123456789abcdef01234567"
SECRET = "runtime-secret-value-000000000000"


class FakeCompose:
    def __init__(self, context: ColdGateContext, output: str) -> None:
        self.context = context
        self.output = output
        self.calls = []

    def run(self, *arguments, **options):
        self.calls.append((arguments, options))
        return subprocess.CompletedProcess(arguments, 0, stdout=self.output)


class ComposeEvidenceTest(unittest.TestCase):
    def fixture(self, root: pathlib.Path):
        context = ColdGateContext.create(root, SHA, "00000001")
        store = EvidenceStore(context, EvidenceRedactor([SECRET]))
        environment = {"COMPOSE_PROJECT_NAME": context.project}
        config = {
            "services": {service: {"image": service} for service in reversed(SERVICES)},
            "name": context.project,
            "networks": {"backend": {"internal": True}},
        }
        return context, store, environment, config

    def test_records_only_the_canonical_combined_config_hash(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, store, environment, config = self.fixture(
                pathlib.Path(temporary).resolve()
            )
            compose = FakeCompose(context, json.dumps(config))

            digest = capture_compose_config(compose, store, environment, [SECRET])

            self.assertRegex(digest, r"^[0-9a-f]{64}$")
            self.assertEqual(
                (context.evidence / "compose.sha256").read_text(),
                f"artifact\tsha256\ncombined-config\t{digest}\n",
            )
            self.assertEqual(
                compose.calls[0][0],
                ("config", "--no-interpolate", "--format", "json"),
            )

    def test_rejects_secret_or_service_inventory_drift_before_writing(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, store, environment, config = self.fixture(
                pathlib.Path(temporary).resolve()
            )
            config["credential"] = SECRET
            with self.assertRaisesRegex(RuntimeError, "runtime secret"):
                capture_compose_config(
                    FakeCompose(context, json.dumps(config)), store, environment, [SECRET]
                )
            self.assertEqual(list(context.evidence.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
