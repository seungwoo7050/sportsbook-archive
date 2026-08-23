import os
import pathlib
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.redaction import EvidenceRedactor


SHA = "0123456789abcdef0123456789abcdef01234567"


class EvidencePathSafetyTest(unittest.TestCase):
    def store(self, root: pathlib.Path) -> EvidenceStore:
        context = ColdGateContext.create(root, SHA, "00000001")
        return EvidenceStore(context, EvidenceRedactor(("service-secret-value",)))

    def test_rejects_symlinked_log_parent_without_touching_victim(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            store = self.store(root)
            victim = root / "victim"
            victim.mkdir()
            (store.context.evidence / "logs").symlink_to(
                victim, target_is_directory=True
            )

            with self.assertRaisesRegex(RuntimeError, "physical directory"):
                store.write("logs/admin.log", "safe\n")
            self.assertEqual(list(victim.iterdir()), [])

    def test_rejects_unknown_logs_overwrites_and_unsafe_content(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = self.store(pathlib.Path(temporary))

            with self.assertRaisesRegex(RuntimeError, "whitelist"):
                store.write("logs/foreign.log", "safe\n")
            store.write("run.tsv", "run\tPASS\n")
            with self.assertRaisesRegex(RuntimeError, "write-once"):
                store.write("run.tsv", "replacement\n")
            with self.assertRaisesRegex(RuntimeError, "unsafe or too large"):
                store.write("readiness.tsv", "contains\0nul")
            with self.assertRaisesRegex(RuntimeError, "unsafe or too large"):
                store.write("topics.tsv", "x" * (256 * 1024 + 1))

    def test_rejects_special_files_and_unknown_directories(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = self.store(pathlib.Path(temporary))
            fifo = store.context.evidence / "run.tsv"
            os.mkfifo(fifo)

            with self.assertRaisesRegex(RuntimeError, "regular file"):
                store.verify()
            fifo.unlink()
            (store.context.evidence / "foreign").mkdir()
            with self.assertRaisesRegex(RuntimeError, "unknown directory"):
                store.verify()


if __name__ == "__main__":
    unittest.main()
