import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.build import ReleaseArtifacts
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.redaction import EvidenceRedactor
from scripts.cold_gate.release_evidence import ReleaseEvidence
from scripts.cold_gate.release_identity import lock_entries
from tests.test_release_artifact_identity import write_release


ROOT = pathlib.Path(__file__).resolve().parents[1]
ORCHESTRATION_SHA = "0123456789abcdef0123456789abcdef01234567"


class ReleaseEvidenceTest(unittest.TestCase):
    def fixture(self, root: pathlib.Path):
        lock_content = (ROOT / "services.lock").read_text()
        (root / "services.lock").write_text(lock_content)
        context = ColdGateContext.create(root, ORCHESTRATION_SHA, "00000001")
        sources = context.runtime / "sources"
        sources.mkdir()
        commits = {}
        for logical, _branch, commit, _artifact in lock_entries(lock_content):
            (sources / logical).mkdir()
            commits[logical] = commit
        repository = context.runtime / "m2/repository"
        shared = repository / "com/sportsbook/shared-protocol/1.0.0/shared-protocol-1.0.0.jar"
        shared.parent.mkdir(parents=True)
        shared.write_bytes(b"shared")
        service_jars = root / "docker/.jars/generation.test"
        service_jars.mkdir(parents=True)
        write_release(service_jars)
        fixture_dir = context.runtime / "fixtures"
        fixture_dir.mkdir()
        fixture_jar = fixture_dir / "avro-fixture-publisher.jar"
        fixture_jar.write_bytes(b"fixture")
        artifacts = ReleaseArtifacts(sources, repository, service_jars, fixture_jar)
        store = EvidenceStore(context, EvidenceRedactor(["redaction-secret-value"]))
        return context, artifacts, store, commits

    def runner(self, root: pathlib.Path, commits: dict[str, str], mismatch: str | None = None):
        def run(command, **_options):
            target = pathlib.Path(command[max(index for index, value in enumerate(command) if value == "-C") + 1])
            if "status" in command:
                output = ""
            elif target == root:
                output = ORCHESTRATION_SHA + "\n"
            else:
                output = ("0" * 40 if target.name == mismatch else commits[target.name]) + "\n"
            return subprocess.CompletedProcess(command, 0, stdout=output)

        return run

    def test_records_release_sources_and_actual_artifact_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context, artifacts, store, commits = self.fixture(root)

            ReleaseEvidence(
                context, artifacts, store, self.runner(root, commits)
            ).capture(ORCHESTRATION_SHA)

            run = (context.evidence / "run.tsv").read_text()
            jars = (context.evidence / "jars.sha256").read_text().splitlines()
            self.assertIn(f"project\t{context.project}", run)
            self.assertIn(f"orchestration_sha\t{ORCHESTRATION_SHA}", run)
            self.assertEqual(len(jars), 10)
            self.assertEqual([line.split("\t")[0] for line in jars[1:]],
                             ["shared", "wallet", "risk", "odds", "betting", "gateway", "settlement", "admin", "fixture"])
            self.assertEqual(
                (context.evidence / "services.lock").read_text(),
                (root / "services.lock").read_text(),
            )

    def test_rejects_a_materialized_source_mismatch_before_writing(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context, artifacts, store, commits = self.fixture(root)

            with self.assertRaisesRegex(RuntimeError, "wallet materialized"):
                ReleaseEvidence(
                    context, artifacts, store, self.runner(root, commits, "wallet")
                ).capture(ORCHESTRATION_SHA)
            self.assertEqual(list(context.evidence.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
