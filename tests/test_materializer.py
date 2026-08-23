import os
import pathlib
import subprocess
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/materialize-sources.sh"


def locked_entries() -> list[tuple[str, str, str, str]]:
    lines = (ROOT / "services.lock").read_text().splitlines()
    return [tuple(line.split("|")) for line in lines if line and not line.startswith("#")]


class MaterializerTest(unittest.TestCase):
    def run_script(
        self, target: pathlib.Path, mode: str = "materialize", lock: pathlib.Path | None = None
    ) -> subprocess.CompletedProcess[str]:
        environment = os.environ.copy()
        if lock is not None:
            environment["SERVICES_LOCK"] = str(lock)
        return subprocess.run(
            [str(SCRIPT), str(target), mode],
            cwd=target.parent,
            env=environment,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_materializes_exact_detached_commits_and_cleans_them(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            target = pathlib.Path(temporary) / "sources"
            self.assertEqual(self.run_script(target).returncode, 0)

            for logical, _branch, commit, _artifact in locked_entries():
                with self.subTest(service=logical):
                    source = target / logical
                    self.assertTrue((source / ".git").is_file())
                    head = subprocess.check_output(
                        ["git", "rev-parse", "HEAD"], cwd=source, text=True
                    ).strip()
                    self.assertEqual(head, commit)
                    attached = subprocess.run(
                        ["git", "symbolic-ref", "-q", "HEAD"], cwd=source, check=False
                    )
                    self.assertNotEqual(attached.returncode, 0)

            self.assertEqual(self.run_script(target, "cleanup").returncode, 0)
            self.assertFalse(target.exists())

    def test_removes_partial_worktrees_after_a_lock_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            temporary_path = pathlib.Path(temporary)
            target = temporary_path / "sources"
            valid = (ROOT / "services.lock").read_text().splitlines()[1]
            bad_lock = temporary_path / "bad.lock"
            bad_lock.write_text(
                valid
                + "\n"
                + "risk|risk-service|0000000000000000000000000000000000000000|risk-service-1.0.0.jar\n"
            )

            failed = self.run_script(target, lock=bad_lock)
            self.assertNotEqual(failed.returncode, 0)
            self.assertIn("invalid services lock", failed.stderr)
            self.assertFalse(target.exists())

    def test_preflights_every_worktree_before_cleanup_removes_any(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            target = pathlib.Path(temporary) / "sources"
            self.assertEqual(self.run_script(target).returncode, 0)
            entries = locked_entries()
            last_name, _branch, last_commit, _artifact = entries[-1]
            last_source = target / last_name
            subprocess.run(
                ["git", "checkout", "--quiet", "--detach", "HEAD^"],
                cwd=last_source,
                check=True,
            )

            failed = self.run_script(target, "cleanup")

            self.assertNotEqual(failed.returncode, 0)
            self.assertTrue(all((target / entry[0]).is_dir() for entry in entries))
            subprocess.run(
                ["git", "checkout", "--quiet", "--detach", last_commit],
                cwd=last_source,
                check=True,
            )
            self.assertEqual(self.run_script(target, "cleanup").returncode, 0)


if __name__ == "__main__":
    unittest.main()
