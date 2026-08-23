import pathlib
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore, REQUIRED_FILES
from scripts.cold_gate.redaction import EvidenceRedactor


SHA = "0123456789abcdef0123456789abcdef01234567"
SECRET = "wallet-secret-value-0000000000000001"


class EvidenceCaptureTest(unittest.TestCase):
    def store(self, root: pathlib.Path) -> EvidenceStore:
        context = ColdGateContext.create(root, SHA, "00000001")
        return EvidenceStore(context, EvidenceRedactor((SECRET,)))

    def test_writes_only_whitelisted_redacted_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = self.store(pathlib.Path(temporary))

            target = store.write(
                "logs/admin.log",
                f"key={SECRET} token=eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.signature\n",
            )

            content = target.read_text()
            self.assertNotIn(SECRET, content)
            self.assertNotIn("eyJhbGci", content)
            store.verify()
            with self.assertRaisesRegex(RuntimeError, "whitelist"):
                store.write("resolved-compose.yaml", "services: {}\n")
            with self.assertRaisesRegex(RuntimeError, "escaped"):
                store.write("../outside", "unsafe\n")

    def test_requires_the_complete_fixed_inventory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = self.store(pathlib.Path(temporary))

            with self.assertRaisesRegex(RuntimeError, "incomplete"):
                store.verify(complete=True)
            for name in REQUIRED_FILES:
                store.write(name, f"artifact\t{name}\n")

            store.verify(complete=True)

    def test_rejects_symlinks_and_directly_added_files(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            store = self.store(root)
            foreign = root / "foreign"
            foreign.write_text("foreign\n")
            target = store.context.evidence / "run.tsv"
            target.symlink_to(foreign)

            with self.assertRaisesRegex(RuntimeError, "symlink"):
                store.write("run.tsv", "run\n")
            target.unlink()
            (store.context.evidence / "unexpected.txt").write_text("unexpected\n")
            with self.assertRaisesRegex(RuntimeError, "whitelist"):
                store.verify()


if __name__ == "__main__":
    unittest.main()
