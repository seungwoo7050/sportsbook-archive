import os
import pathlib
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
MATERIALIZER = ROOT / "scripts/materialize-sources.sh"
STAGER = ROOT / "scripts/stage-release-jars.sh"
FAKE_MAVEN = ROOT / "tests/fake_maven.py"


class StagingFixture(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.temporary_path = pathlib.Path(self.temporary.name)
        self.sources = self.temporary_path / "sources"
        self.repository = self.temporary_path / "m2"
        self.docker = self.temporary_path / "docker"
        self.jdk = self.temporary_path / "jdk"
        self.repository.mkdir()
        materialized = subprocess.run(
            [str(MATERIALIZER), str(self.sources)],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(materialized.returncode, 0, materialized.stderr)
        self.environment = os.environ.copy()
        (self.jdk / "bin").mkdir(parents=True)
        for name, output in (
            ("java", 'openjdk version "17.0.0"'),
            ("javac", "javac 17.0.0"),
            ("jar", "BOOT-INF/classes/Probe.class"),
        ):
            executable = self.jdk / "bin" / name
            redirect = "" if name == "jar" else " >&2"
            executable.write_text(f"#!/bin/sh\nprintf '{output}\\n'{redirect}\n")
            executable.chmod(0o755)
        self.environment["JAVA_HOME"] = str(self.jdk)
        self.environment["PATH"] = f"{self.jdk / 'bin'}:{self.environment['PATH']}"
        self.environment["MAVEN_RUNNER"] = str(FAKE_MAVEN)
        self.environment["DOCKER_OUTPUT_ROOT"] = self.docker.name

    def tearDown(self) -> None:
        if self.sources.exists():
            subprocess.run(
                [str(MATERIALIZER), str(self.sources), "cleanup"],
                cwd=ROOT,
                check=False,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        self.temporary.cleanup()

    def stage(self, **environment: str) -> subprocess.CompletedProcess[str]:
        run_environment = self.environment | environment
        return subprocess.run(
            [str(STAGER), self.sources.name, self.repository.name],
            cwd=self.temporary_path,
            env=run_environment,
            text=True,
            capture_output=True,
            check=False,
        )

    def active_generation(self) -> pathlib.Path:
        link = self.docker / "jars"
        return self.docker / os.readlink(link)
