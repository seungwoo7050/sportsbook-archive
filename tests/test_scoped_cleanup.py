import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.cleanup import ScopedCleanup
from scripts.cold_gate.context import ColdGateContext


SHA = "0123456789abcdef0123456789abcdef01234567"


class FakeCompose:
    def __init__(self, context) -> None:
        self.context = context
        self.calls = []
        self.absence_checks = 0

    def run(self, *arguments: str) -> None:
        self.calls.append(arguments)

    def require_absent(self) -> None:
        self.absence_checks += 1


class ScopedCleanupTest(unittest.TestCase):
    def test_removes_only_owned_runtime_and_preserves_evidence(self) -> None:
        materializer_calls = []

        def runner(command, **_options):
            materializer_calls.append(command)
            pathlib.Path(command[1]).rmdir()
            return subprocess.CompletedProcess(command, 0, stdout="")

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = ColdGateContext.create(root, SHA, "00000001")
            sources = context.runtime / "sources"
            sources.mkdir()
            compose = FakeCompose(context)

            ScopedCleanup(context, compose, runner).run(sources)

            self.assertEqual(
                compose.calls,
                [
                    (
                        "down",
                        "--volumes",
                        "--remove-orphans",
                        "--rmi",
                        "local",
                        "--timeout",
                        "30",
                    )
                ],
            )
            self.assertEqual(compose.absence_checks, 1)
            self.assertEqual(materializer_calls[0][1:], [str(sources), "cleanup"])
            self.assertFalse(context.runtime.exists())
            self.assertFalse(context.lock.exists())
            self.assertTrue(context.evidence.is_dir())

    def test_rejects_foreign_or_symlinked_source_targets(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = ColdGateContext.create(root, SHA, "00000001")
            foreign = root / "foreign"
            foreign.mkdir()
            compose = FakeCompose(context)

            with self.assertRaisesRegex(RuntimeError, "not owned"):
                ScopedCleanup(context, compose).run(foreign)

            (context.runtime / "sources").symlink_to(foreign, target_is_directory=True)
            with self.assertRaisesRegex(RuntimeError, "not owned"):
                ScopedCleanup(context, compose).run(context.runtime / "sources")
            self.assertEqual(compose.calls, [])

    def test_rejects_tampered_ownership_before_docker_commands(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context = ColdGateContext.create(
                pathlib.Path(temporary), SHA, "00000001"
            )
            (context.runtime / ".owner").write_text("project=foreign\n")
            compose = FakeCompose(context)

            with self.assertRaisesRegex(RuntimeError, "marker mismatch"):
                ScopedCleanup(context, compose).run()
            self.assertEqual(compose.calls, [])


if __name__ == "__main__":
    unittest.main()
