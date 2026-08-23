import os
import pathlib
import subprocess
import tempfile
import textwrap
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/install-shared.sh"
SHARED_SHA = "f9de6bc1e533761ab4bb1454d8d4ab8175cdf001"


class SharedInstallTest(unittest.TestCase):
    def test_installs_only_the_locked_shared_artifact(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            temporary_path = pathlib.Path(temporary)
            source_root = temporary_path / "sources"
            source = source_root / "shared"
            repository = temporary_path / "m2"
            runner = temporary_path / "fake-maven"
            java = temporary_path / "jdk/bin/java"
            source_root.mkdir()
            subprocess.run(
                ["git", "worktree", "add", "--quiet", "--detach", str(source), SHARED_SHA],
                cwd=ROOT,
                check=True,
            )
            self.addCleanup(
                subprocess.run,
                ["git", "worktree", "remove", "--force", str(source)],
                cwd=ROOT,
                check=False,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            runner.write_text(
                textwrap.dedent(
                    """\
                    #!/usr/bin/env python3
                    import pathlib
                    import shutil
                    import sys
                    import zipfile

                    repository = pathlib.Path(next(
                        value.split("=", 1)[1]
                        for value in sys.argv
                        if value.startswith("-Dmaven.repo.local=")
                    ))
                    source = pathlib.Path.cwd()
                    artifact = source / "target/shared-protocol-1.0.0.jar"
                    artifact.parent.mkdir(parents=True)
                    with zipfile.ZipFile(artifact, "w") as archive:
                        archive.writestr("com/sportsbook/protocol/value/Money.class", b"class")
                    destination = repository / "com/sportsbook/shared-protocol/1.0.0"
                    destination.mkdir(parents=True)
                    shutil.copy2(artifact, destination / artifact.name)
                    (destination / "shared-protocol-1.0.0.pom").write_text(
                        "<project><version>1.0.0</version></project>\\n"
                    )
                    """
                )
            )
            runner.chmod(0o755)
            java.parent.mkdir(parents=True)
            java.write_text('#!/bin/sh\nprintf \'openjdk version "17.0.0"\\n\' >&2\n')
            java.chmod(0o755)
            javac = java.parent / "javac"
            javac.write_text("#!/bin/sh\nprintf 'javac 17.0.0\\n' >&2\n")
            javac.chmod(0o755)
            jar = java.parent / "jar"
            jar.write_text(
                "#!/bin/sh\nprintf 'com/sportsbook/protocol/value/Money.class\\n'\n"
            )
            jar.chmod(0o755)
            environment = os.environ.copy()
            environment["JAVA_HOME"] = str(java.parents[1])
            environment["MAVEN_RUNNER"] = runner.name
            environment["PATH"] = f"{java.parent}:{environment['PATH']}"

            result = subprocess.run(
                [str(SCRIPT), source_root.name, repository.name],
                cwd=temporary_path,
                env=environment,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            installed = repository / "com/sportsbook/shared-protocol/1.0.0"
            self.assertEqual(
                {path.name for path in installed.iterdir()},
                {"shared-protocol-1.0.0.jar", "shared-protocol-1.0.0.pom"},
            )
            self.assertEqual(
                (source / "target/shared-protocol-1.0.0.jar").read_bytes(),
                (installed / "shared-protocol-1.0.0.jar").read_bytes(),
            )
            subprocess.run(
                ["git", "worktree", "remove", "--force", str(source)],
                cwd=ROOT,
                check=True,
            )


if __name__ == "__main__":
    unittest.main()
