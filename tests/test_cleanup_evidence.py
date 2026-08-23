import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.cleanup_evidence import CleanupEvidence
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.redaction import EvidenceRedactor


SHA = "0" * 40


class CleanupEvidenceTest(unittest.TestCase):
    def fixture(self, root, leak=None):
        context = ColdGateContext.create(root, SHA, "00000001")
        store = EvidenceStore(context, EvidenceRedactor(("redaction-secret-value",)))
        sources = context.runtime / "sources"
        jars = root / "docker/.jars/generation.test"

        def runner(command, **_options):
            category = "containers"
            if command[1:3] == ["network", "ls"]:
                category = "networks"
            elif command[1:3] == ["volume", "ls"]:
                category = "volumes"
            output = "owned-resource\n" if category == leak else ""
            return subprocess.CompletedProcess(command, 0, stdout=output)

        return context, store, sources, jars, runner

    def test_records_six_zero_resource_receipts(self):
        with tempfile.TemporaryDirectory() as temporary:
            context, store, sources, jars, runner = self.fixture(
                pathlib.Path(temporary).resolve()
            )

            CleanupEvidence(context, store, runner).capture(sources, jars)

            self.assertEqual(
                (context.evidence / "cleanup.tsv").read_text().splitlines(),
                [
                    "resource\tremaining",
                    "containers\t0",
                    "networks\t0",
                    "volumes\t0",
                    "sources\t0",
                    "jar-link\t0",
                    "jar-generation\t0",
                ],
            )

    def test_rejects_leaks_and_foreign_targets_before_writing(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context, store, sources, jars, runner = self.fixture(root, "networks")
            with self.assertRaisesRegex(RuntimeError, "networks"):
                CleanupEvidence(context, store, runner).capture(sources, jars)
            with self.assertRaisesRegex(RuntimeError, "not owned"):
                CleanupEvidence(context, store, runner).capture(root / "foreign", jars)
            self.assertEqual(list(context.evidence.iterdir()), [])

    def test_rejects_remaining_owned_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context, store, sources, jars, runner = self.fixture(root)
            sources.mkdir()
            with self.assertRaisesRegex(RuntimeError, "sources"):
                CleanupEvidence(context, store, runner).capture(sources, jars)
            self.assertEqual(list(context.evidence.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
