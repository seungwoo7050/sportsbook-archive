import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.cleanup import ScopedCleanup
from scripts.cold_gate.context import ColdGateContext
from tests.test_scoped_cleanup import FakeCompose, FakeEvidence


SHA = "0123456789abcdef0123456789abcdef01234567"


class ScopedCleanupFailClosedTest(unittest.TestCase):
    def test_rejects_a_compose_project_from_another_context(self) -> None:
        with tempfile.TemporaryDirectory() as left, tempfile.TemporaryDirectory() as right:
            context = ColdGateContext.create(pathlib.Path(left), SHA, "00000001")
            foreign = ColdGateContext.create(pathlib.Path(right), SHA, "00000002")

            with self.assertRaisesRegex(RuntimeError, "different ownership"):
                ScopedCleanup(context, FakeCompose(foreign))

            self.assertTrue(context.runtime.is_dir())
            self.assertTrue(foreign.runtime.is_dir())

    def test_preserves_runtime_when_materializer_leaves_sources(self) -> None:
        def no_op(command, **_options):
            return subprocess.CompletedProcess(command, 0, stdout="")

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = ColdGateContext.create(root, SHA, "00000001")
            sources = context.runtime / "sources"
            sources.mkdir()
            sibling = root / "sibling"
            sibling.write_text("preserve\n")

            with self.assertRaisesRegex(RuntimeError, "sources remain"):
                ScopedCleanup(context, FakeCompose(context), no_op).run(sources)

            context.require_owned()
            self.assertTrue(sources.is_dir())
            self.assertEqual(sibling.read_text(), "preserve\n")
            self.assertTrue(context.evidence.is_dir())

    def test_preserves_ownership_when_docker_absence_check_fails(self) -> None:
        class FailingCompose(FakeCompose):
            def require_absent(self) -> None:
                raise RuntimeError("resources remain")

        with tempfile.TemporaryDirectory() as temporary:
            context = ColdGateContext.create(
                pathlib.Path(temporary), SHA, "00000001"
            )

            with self.assertRaisesRegex(RuntimeError, "resources remain"):
                ScopedCleanup(context, FailingCompose(context)).run()

            context.require_owned()
            self.assertTrue(context.evidence.is_dir())

    def test_rejects_incomplete_or_foreign_evidence_targets_before_cleanup(self) -> None:
        with tempfile.TemporaryDirectory() as left, tempfile.TemporaryDirectory() as right:
            context = ColdGateContext.create(pathlib.Path(left), SHA, "00000001")
            foreign = ColdGateContext.create(pathlib.Path(right), SHA, "00000002")
            compose = FakeCompose(context)
            with self.assertRaisesRegex(RuntimeError, "different ownership"):
                ScopedCleanup(context, compose).run(evidence=FakeEvidence(foreign))
            with self.assertRaisesRegex(RuntimeError, "different ownership"):
                ScopedCleanup(context, compose).run(evidence=FakeEvidence(context))
            self.assertEqual(compose.calls, [])


if __name__ == "__main__":
    unittest.main()
