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
        (root / "compose.observability.yaml").write_text("services: {}\n")
        return ColdGateContext.create(root, SHA, "89abcdef")

    def test_scopes_commands_to_exact_project_directory_and_files(self) -> None:
        calls = []

        def runner(command, **options):
            calls.append((command, options))
            return subprocess.CompletedProcess(command, 0, stdout="")

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = self.context(root)
            compose = ComposeProject(context, runner)

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
                "--file",
                str(root / "compose.observability.yaml"),
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

    def test_preserves_an_explicitly_empty_environment(self) -> None:
        captured = []

        def runner(command, **options):
            captured.append(options["env"])
            return subprocess.CompletedProcess(command, 0, stdout="")

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            ComposeProject(self.context(root), runner).run(
                "config", "--quiet", environment={}
            )

        self.assertEqual(captured, [{}])

    def test_binds_one_owned_environment_for_later_commands(self) -> None:
        captured = []

        def runner(command, **options):
            captured.append(options["env"])
            return subprocess.CompletedProcess(command, 0, stdout="")

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = self.context(root)
            compose = ComposeProject(context, runner)
            environment = {
                "COMPOSE_PROJECT_NAME": context.project,
                "GRAFANA_ADMIN_PASSWORD": "runtime-secret",
            }
            compose.bind_environment(environment)
            environment["GRAFANA_ADMIN_PASSWORD"] = "mutated"
            compose.run("ps", "--all")

            self.assertEqual(captured[0]["GRAFANA_ADMIN_PASSWORD"], "runtime-secret")
            with self.assertRaisesRegex(RuntimeError, "already bound"):
                compose.bind_environment(environment | {"COMPOSE_PROJECT_NAME": context.project})
            foreign = ComposeProject(context, runner)
            with self.assertRaisesRegex(RuntimeError, "another cold project"):
                foreign.bind_environment({"COMPOSE_PROJECT_NAME": "foreign"})

    def test_rejects_symlinked_compose_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = self.context(root)
            observability = root / "compose.observability.yaml"
            target = root / "foreign.yaml"
            target.write_text("services: {}\n")
            observability.unlink()
            observability.symlink_to(target)

            with self.assertRaisesRegex(RuntimeError, "regular file"):
                ComposeProject(context)


if __name__ == "__main__":
    unittest.main()
