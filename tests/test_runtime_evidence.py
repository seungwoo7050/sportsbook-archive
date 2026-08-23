import hashlib
import json
import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.build import ReleaseArtifacts
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.inventory import APPLICATION_SERVICES, COMPLETED_SERVICES, SERVICES
from scripts.cold_gate.redaction import EvidenceRedactor
from scripts.cold_gate.runtime_evidence import RuntimeEvidence
from tests.test_release_artifact_identity import write_release


SHA = "0" * 40
IMAGE = "sha256:" + "b" * 64


class FakeCompose:
    def __init__(self, context, hashes, drift=None):
        self.context = context
        self.hashes = hashes
        self.drift = drift
        self.ids = {service: f"{index + 1:064x}" for index, service in enumerate(SERVICES)}

    def run(self, *arguments, **_options):
        if arguments[0] == "ps":
            return subprocess.CompletedProcess(arguments, 0, stdout=self.ids[arguments[-1]] + "\n")
        service = arguments[2]
        digest = "f" * 64 if service == self.drift else self.hashes[f"{service}.jar"]
        return subprocess.CompletedProcess(arguments, 0, stdout=f"{digest}  /app/app.jar\n")


class RuntimeEvidenceTest(unittest.TestCase):
    def fixture(self, root, drift=None):
        for name in ("compose.yaml", "compose.toxiproxy.yaml", "compose.observability.yaml"):
            (root / name).write_text("services: {}\n")
        context = ColdGateContext.create(root, SHA, "00000001")
        jars = root / "jars"
        jars.mkdir()
        write_release(jars)
        hashes = {
            path.name: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in jars.glob("*.jar")
        }
        fixture = context.runtime / "fixture.jar"
        fixture.write_bytes(b"fixture")
        artifacts = ReleaseArtifacts(context.runtime, context.runtime, jars, fixture)
        store = EvidenceStore(context, EvidenceRedactor(["redaction-secret-value"]))
        compose = FakeCompose(context, hashes, drift)

        def runner(command, **_options):
            service = next(name for name, value in compose.ids.items() if value == command[-1])
            completed = service in COMPLETED_SERVICES
            fields = (
                compose.ids[service], f"/{context.project}-{service}-1", IMAGE,
                "exited" if completed else "running", "-" if completed else "healthy",
                "0", context.project, service,
            )
            return subprocess.CompletedProcess(command, 0, stdout="\t".join(fields) + "\n")

        return context, RuntimeEvidence(compose, artifacts, store, runner)

    def test_records_exact_runtime_and_embedded_release_identities(self):
        with tempfile.TemporaryDirectory() as temporary:
            context, evidence = self.fixture(pathlib.Path(temporary).resolve())

            evidence.capture()

            images = (context.evidence / "images.tsv").read_text().splitlines()
            states = json.loads((context.evidence / "compose-ps.json").read_text())
            self.assertEqual([row.split("\t")[0] for row in images[1:]], list(SERVICES))
            self.assertEqual([row["service"] for row in states], list(SERVICES))
            self.assertEqual(
                sum(row.split("\t")[2] != "-" for row in images[1:]),
                len(APPLICATION_SERVICES),
            )

    def test_rejects_embedded_jar_drift_before_writing(self):
        with tempfile.TemporaryDirectory() as temporary:
            context, evidence = self.fixture(pathlib.Path(temporary).resolve(), "wallet")

            with self.assertRaisesRegex(RuntimeError, "wallet embedded"):
                evidence.capture()
            self.assertEqual(list(context.evidence.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
