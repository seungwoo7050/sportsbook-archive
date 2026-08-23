import os
import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.build import ReleaseBuilder
from scripts.cold_gate.context import ColdGateContext


SHA = "0123456789abcdef0123456789abcdef01234567"
SERVICES = ("wallet", "risk", "odds", "betting", "gateway", "settlement", "admin")


class ReleaseBuilderTest(unittest.TestCase):
    def context(self, root: pathlib.Path) -> ColdGateContext:
        (root / "docker").mkdir()
        (root / "scripts").mkdir()
        return ColdGateContext.create(root, SHA, "00000001")

    def test_builds_in_locked_order_and_returns_exact_artifacts(self) -> None:
        calls = []

        def runner(command, **options):
            calls.append((command, options))
            name = pathlib.Path(command[0]).name
            if name == "materialize-sources.sh":
                pathlib.Path(command[1]).mkdir()
            elif name == "stage-release-jars.sh":
                docker = pathlib.Path(options["env"]["DOCKER_OUTPUT_ROOT"])
                generation = docker / ".jars/generation.test"
                generation.mkdir(parents=True)
                for service in SERVICES:
                    (generation / f"{service}.jar").write_bytes(service.encode())
                (docker / "jars").symlink_to(".jars/generation.test")
            elif name == "stage-fixture-publisher.sh":
                pathlib.Path(command[3], "avro-fixture-publisher.jar").write_bytes(
                    b"fixture"
                )
            return subprocess.CompletedProcess(command, 0, stdout="")

        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = self.context(root)

            artifacts = ReleaseBuilder(context, {"JAVA_HOME": "/jdk17"}, runner).build()

            self.assertEqual(
                [pathlib.Path(call[0][0]).name for call in calls],
                [
                    "materialize-sources.sh",
                    "install-shared.sh",
                    "stage-release-jars.sh",
                    "stage-fixture-publisher.sh",
                ],
            )
            self.assertEqual(
                {path.name for path in artifacts.service_jars.glob("*.jar")},
                {f"{service}.jar" for service in SERVICES},
            )
            self.assertEqual(artifacts.sources, context.runtime / "sources")
            self.assertEqual(artifacts.fixture_jar.read_bytes(), b"fixture")

    def test_rejects_preexisting_staging_without_running_commands(self) -> None:
        calls = []
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context = self.context(root)
            (root / "docker/.jars").mkdir()

            with self.assertRaisesRegex(RuntimeError, "not empty"):
                ReleaseBuilder(context, os.environ.copy(), calls.append).build()

            self.assertEqual(calls, [])


if __name__ == "__main__":
    unittest.main()
