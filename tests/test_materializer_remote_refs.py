import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE_SCRIPT = ROOT / "scripts/materialize-sources.sh"


class MaterializerRemoteRefsTest(unittest.TestCase):
    def fixture(self, parent: pathlib.Path) -> tuple[pathlib.Path, pathlib.Path, str, str]:
        repository = parent / "repository"
        repository.mkdir()
        subprocess.run(["git", "init", "--quiet"], cwd=repository, check=True)
        subprocess.run(["git", "config", "user.name", "Fixture"], cwd=repository, check=True)
        subprocess.run(
            ["git", "config", "user.email", "fixture@example.invalid"],
            cwd=repository,
            check=True,
        )
        (repository / "README.md").write_text("locked\n")
        subprocess.run(["git", "add", "README.md"], cwd=repository, check=True)
        subprocess.run(["git", "commit", "--quiet", "-m", "locked"], cwd=repository, check=True)
        locked = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=repository, text=True
        ).strip()
        (repository / "README.md").write_text("later\n")
        subprocess.run(["git", "commit", "--quiet", "-am", "later"], cwd=repository, check=True)
        later = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=repository, text=True
        ).strip()

        scripts = repository / "scripts"
        scripts.mkdir()
        script = scripts / SOURCE_SCRIPT.name
        shutil.copyfile(SOURCE_SCRIPT, script)
        script.chmod(0o755)
        lock = parent / "services.lock"
        lines = []
        for index in range(8):
            branch = f"service-{index}"
            subprocess.run(
                ["git", "update-ref", f"refs/remotes/origin/{branch}", locked],
                cwd=repository,
                check=True,
            )
            lines.append(f"service{index}|{branch}|{locked}|service{index}.jar")
        lock.write_text("\n".join(lines) + "\n")
        return script, lock, locked, later

    def invoke(self, script: pathlib.Path, lock: pathlib.Path, target: pathlib.Path, mode="materialize"):
        environment = os.environ.copy()
        environment["SERVICES_LOCK"] = str(lock)
        return subprocess.run(
            [str(script), str(target), mode],
            cwd=script.parent.parent,
            env=environment,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_materializes_when_only_remote_tracking_refs_exist(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            parent = pathlib.Path(temporary).resolve()
            script, lock, locked, _later = self.fixture(parent)
            target = parent / "sources"

            result = self.invoke(script, lock, target)

            self.assertEqual(result.returncode, 0, result.stderr)
            for source in target.iterdir():
                self.assertEqual(
                    subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip(),
                    locked,
                )
            self.assertEqual(self.invoke(script, lock, target, "cleanup").returncode, 0)

    def test_does_not_hide_a_diverged_local_branch(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            parent = pathlib.Path(temporary).resolve()
            script, lock, _locked, later = self.fixture(parent)
            repository = script.parent.parent
            subprocess.run(
                ["git", "update-ref", "refs/heads/service-0", later], cwd=repository, check=True
            )

            result = self.invoke(script, lock, parent / "sources")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("no longer matches", result.stderr)
            self.assertFalse((parent / "sources").exists())


if __name__ == "__main__":
    unittest.main()
