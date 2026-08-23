import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.context import ColdGateContext


SHA = "0123456789abcdef0123456789abcdef01234567"


class ColdGateComposeTest(unittest.TestCase):
    def context(self, root: pathlib.Path) -> ColdGateContext:
        (root / "compose.yaml").write_text("services: {}\n")
        (root / "compose.toxiproxy.yaml").write_text("services: {}\n")
        return ColdGateContext.create(root, SHA, "89abcdef")

    def test_scopes_commands_to_exact_project_directory_and_files(self) -> None:
        calls = []

        def runner(command, **options):
            calls.append((command, options))
            return subprocess.CompletedProcess(command, 0, stdout="")

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = self.context(root)
            compose = ComposeProject(
                context, (root / "compose.toxiproxy.yaml",), runner
            )

            compose.run("config", "--quiet")
            compose.require_absent()

        command = calls[0][0]
        self.assertEqual(command[:6], [
            "docker",
            "compose",
            "--project-name",
            "sb-gate-0123456789ab-89abcdef",
            "--project-directory",
            str(root),
        ])
        self.assertEqual(
            command[6:],
            [
                "--file",
                str(root / "compose.yaml"),
                "--file",
                str(root / "compose.toxiproxy.yaml"),
                "config",
                "--quiet",
            ],
        )
        labels = [call[0][-1] for call in calls[1:]]
        self.assertEqual(
            labels,
            [
                "label=com.docker.compose.project=sb-gate-0123456789ab-89abcdef"
            ]
            * 3,
        )

    def test_rejects_existing_project_resources_without_down(self) -> None:
        def runner(command, **_options):
            output = "owned\n" if command[1:3] == ["ps", "--all"] else ""
            return subprocess.CompletedProcess(command, 0, stdout=output)

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            compose = ComposeProject(self.context(root), runner=runner)

            with self.assertRaisesRegex(RuntimeError, "already owns"):
                compose.require_absent()


if __name__ == "__main__":
    unittest.main()
