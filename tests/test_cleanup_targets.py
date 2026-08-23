import pathlib
import tempfile
import unittest

from scripts.cold_gate.cleanup_targets import discover_cleanup_targets
from scripts.cold_gate.context import ColdGateContext


SHA = "0" * 40


class CleanupTargetsTest(unittest.TestCase):
    def context(self, root):
        return ColdGateContext.create(root, SHA, "00000001")

    def test_discovers_absent_or_exact_owned_targets(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = self.context(root)
            self.assertEqual(discover_cleanup_targets(context), (None, None))

            sources = context.runtime / "sources"
            sources.mkdir()
            jars = root / "docker/.jars/generation.release"
            jars.mkdir(parents=True)
            (root / "docker/jars").symlink_to(".jars/generation.release")

            self.assertEqual(discover_cleanup_targets(context), (sources, jars))

    def test_rejects_escaped_link_and_ambiguous_generations(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = self.context(root)
            docker = root / "docker"
            docker.mkdir()
            foreign = root / "foreign"
            foreign.mkdir()
            (docker / "jars").symlink_to(foreign, target_is_directory=True)
            with self.assertRaisesRegex(RuntimeError, "escaped"):
                discover_cleanup_targets(context)

            (docker / "jars").unlink()
            first = docker / ".jars/first"
            second = docker / ".jars/second"
            first.mkdir(parents=True)
            second.mkdir()
            (docker / "jars").symlink_to(".jars/first")
            with self.assertRaisesRegex(RuntimeError, "ambiguous"):
                discover_cleanup_targets(context)

    def test_rejects_symlinked_sources_and_orphan_generations(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = self.context(root)
            foreign = root / "foreign"
            foreign.mkdir()
            (context.runtime / "sources").symlink_to(foreign, target_is_directory=True)
            with self.assertRaisesRegex(RuntimeError, "source target"):
                discover_cleanup_targets(context)

            (context.runtime / "sources").unlink()
            (root / "docker/.jars/orphan").mkdir(parents=True)
            with self.assertRaisesRegex(RuntimeError, "without its owned link"):
                discover_cleanup_targets(context)


if __name__ == "__main__":
    unittest.main()
