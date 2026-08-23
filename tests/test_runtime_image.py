import pathlib
import shutil
import subprocess
import tempfile
import unittest
import uuid
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
DOCKERFILE = ROOT / "docker/Dockerfile.jvm"


class RuntimeImageTest(unittest.TestCase):
    def test_runs_java_17_and_provides_the_health_tool(self) -> None:
        image = f"sportsbook-runtime-contract:{uuid.uuid4().hex}"
        self.addCleanup(
            subprocess.run,
            ["docker", "image", "rm", "--force", image],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

        with tempfile.TemporaryDirectory() as temporary:
            context = pathlib.Path(temporary)
            shutil.copy2(DOCKERFILE, context / "Dockerfile")
            jars = context / "jars"
            jars.mkdir()
            with zipfile.ZipFile(jars / "probe.jar", "w") as archive:
                archive.writestr("BOOT-INF/classes/Probe.class", b"probe")

            built = subprocess.run(
                [
                    "docker",
                    "build",
                    "--quiet",
                    "--build-arg",
                    "JAR=probe.jar",
                    "--tag",
                    image,
                    ".",
                ],
                cwd=context,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(built.returncode, 0, built.stderr)

        java = self.run_in_image(image, "java", "-XshowSettings:properties", "-version")
        self.assertEqual(java.returncode, 0, java.stderr)
        self.assertIn("java.class.version = 61.0", java.stderr)

        curl = self.run_in_image(image, "curl", "--version")
        self.assertEqual(curl.returncode, 0, curl.stderr)
        self.assertTrue(curl.stdout.startswith("curl "))

        user = self.run_in_image(image, "id", "-u")
        self.assertEqual(user.returncode, 0, user.stderr)
        self.assertEqual(user.stdout.strip(), "10001")

    @staticmethod
    def run_in_image(image: str, *command: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["docker", "run", "--rm", "--entrypoint", command[0], image, *command[1:]],
            text=True,
            capture_output=True,
            check=False,
        )


if __name__ == "__main__":
    unittest.main()
