import pathlib
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext, PROJECT_PATTERN


SHA = "0123456789abcdef0123456789abcdef01234567"


class ColdGateContextTest(unittest.TestCase):
    def test_creates_owned_unique_project_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()

            context = ColdGateContext.create(root, SHA, "89abcdef")

            self.assertEqual(context.project, "sb-gate-0123456789ab-89abcdef")
            self.assertRegex(context.project, PROJECT_PATTERN)
            self.assertEqual(context.runtime.parent, root / ".runtime" / "cold-gate")
            self.assertEqual(context.evidence.parent, root / "evidence" / "cold-gate")
            self.assertTrue(context.lock.is_dir())
            context.require_owned()

    def test_rejects_concurrent_or_reused_run_contexts(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            ColdGateContext.create(root, SHA, "00000001")

            with self.assertRaisesRegex(RuntimeError, "another cold gate"):
                ColdGateContext.create(root, SHA, "00000002")

    def test_rejects_untrusted_identifiers_before_writing(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)

            with self.assertRaises(ValueError):
                ColdGateContext.create(root, "HEAD", "00000001")
            with self.assertRaises(ValueError):
                ColdGateContext.create(root, SHA, "../escape")

            self.assertFalse((root / ".runtime").exists())

    def test_detects_marker_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context = ColdGateContext.create(pathlib.Path(temporary), SHA, "00000001")
            (context.runtime / ".owner").write_text("project=foreign\n")

            with self.assertRaisesRegex(RuntimeError, "marker mismatch"):
                context.require_owned()


if __name__ == "__main__":
    unittest.main()
