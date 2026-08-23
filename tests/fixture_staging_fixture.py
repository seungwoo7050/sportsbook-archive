import os
import pathlib
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/stage-fixture-publisher.sh"
SHARED_SHA = "f9de6bc1e533761ab4bb1454d8d4ab8175cdf001"
BUILT = ROOT / "fixtures/avro-publisher/target/avro-fixture-publisher.jar"


class FixtureStagingFixture(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.base = pathlib.Path(self.temporary.name).resolve()
        self.sources = self.base / "sources"
        self.shared = self.sources / "shared"
        self.repository = self.base / "m2"
        self.output = self.base / "output"
        self.runner = self.base / "fake-maven"
        self.backup = self.base / "publisher.backup"
        self.sources.mkdir()
        self.repository.mkdir()
        self.output.mkdir()
        subprocess.run(
            ["git", "worktree", "add", "--quiet", "--detach", str(self.shared), SHARED_SHA],
            cwd=ROOT,
            check=True,
        )
        if BUILT.exists():
            shutil.copy2(BUILT, self.backup)
        self._write_runner()
        self.environment = os.environ.copy()
        self.environment["JAVA_HOME"] = str(self.base / "jdk")
        self.environment["PATH"] = f"{self.base / 'jdk/bin'}:{self.environment['PATH']}"
        self.environment["MAVEN_RUNNER"] = str(self.runner)

    def tearDown(self) -> None:
        if self.backup.exists():
            BUILT.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(self.backup, BUILT)
        elif BUILT.exists():
            BUILT.unlink()
        subprocess.run(
            ["git", "worktree", "remove", "--force", str(self.shared)],
            cwd=ROOT,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        self.temporary.cleanup()

    def _write_runner(self) -> None:
        self.runner.write_text(
            textwrap.dedent(
                """\
                #!/usr/bin/env python3
                import os, pathlib, sys, zipfile
                if os.environ.get("FAIL_FIXTURE"):
                    raise SystemExit(19)
                pom = pathlib.Path(sys.argv[sys.argv.index("-f") + 1])
                jar = pom.parent / "target/avro-fixture-publisher.jar"
                jar.parent.mkdir(parents=True, exist_ok=True)
                with zipfile.ZipFile(jar, "w") as archive:
                    archive.writestr("META-INF/MANIFEST.MF", "Main-Class: com.sportsbook.orchestration.fixture.FixturePublisher\\n")
                    archive.writestr("META-INF/maven/com.sportsbook/shared-protocol/pom.properties", "version=1.0.0\\n")
                    archive.writestr("com/sportsbook/orchestration/fixture/FixturePublisher.class", b"class")
                    archive.writestr("com/sportsbook/protocol/event/EventLifecycle.class", b"shared")
                    archive.writestr("org/apache/kafka/clients/producer/KafkaProducer.class", b"kafka")
                """
            )
        )
        self.runner.chmod(0o755)
        binaries = {
            "java": 'openjdk version "17.0.0"',
            "javac": "javac 17.0.0",
            "jar": "com/sportsbook/orchestration/fixture/FixturePublisher.class\\ncom/sportsbook/protocol/event/EventLifecycle.class\\norg/apache/kafka/clients/producer/KafkaProducer.class",
            "javap": "  major version: 61",
        }
        (self.base / "jdk/bin").mkdir(parents=True)
        for name, output in binaries.items():
            binary = self.base / "jdk/bin" / name
            binary.write_text(f"#!/bin/sh\nprintf '{output}\\n'\n")
            binary.chmod(0o755)

    def stage(self, **environment: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [str(SCRIPT), str(self.sources), str(self.repository), str(self.output)],
            cwd=self.base,
            env=self.environment | environment,
            text=True,
            capture_output=True,
            check=False,
        )
