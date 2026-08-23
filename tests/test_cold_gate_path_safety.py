import pathlib
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext


SHA = "0123456789abcdef0123456789abcdef01234567"


class ColdGatePathSafetyTest(unittest.TestCase):
    def test_rejects_symlinked_runtime_ancestor_without_touching_victim(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            victim = root / "victim"
            victim.mkdir()
            (root / ".runtime").symlink_to(victim, target_is_directory=True)

            with self.assertRaisesRegex(RuntimeError, "physical directory"):
                ColdGateContext.create(root, SHA, "00000001")

            self.assertEqual(list(victim.iterdir()), [])

    def test_rolls_back_owned_parents_when_evidence_is_a_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            victim = root / "victim"
            victim.mkdir()
            (root / "evidence").symlink_to(victim, target_is_directory=True)

            with self.assertRaisesRegex(RuntimeError, "physical directory"):
                ColdGateContext.create(root, SHA, "00000001")

            self.assertFalse((root / ".runtime").exists())
            self.assertEqual(list(victim.iterdir()), [])

    def test_rejects_symlinked_markers_even_with_matching_content(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = ColdGateContext.create(root, SHA, "00000001")
            marker = context.runtime / ".owner"
            content = marker.read_text()
            marker.unlink()
            foreign = root / "foreign-marker"
            foreign.write_text(content)
            marker.symlink_to(foreign)

            with self.assertRaisesRegex(RuntimeError, "regular file"):
                context.require_owned()
            self.assertEqual(foreign.read_text(), content)

    def test_rejects_forged_context_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = ColdGateContext.create(root, SHA, "00000001")
            forged = ColdGateContext(
                context.root,
                context.project,
                root / "foreign",
                context.evidence,
                context.lock,
            )

            with self.assertRaisesRegex(RuntimeError, "do not match"):
                forged.require_owned()


if __name__ == "__main__":
    unittest.main()
