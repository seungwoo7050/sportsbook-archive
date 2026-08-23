import pathlib
import subprocess
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class RepositoryPolicyTest(unittest.TestCase):
    def ignored(self, path: str) -> bool:
        result = subprocess.run(
            ["git", "check-ignore", "--no-index", "--quiet", path],
            cwd=ROOT,
            check=False,
        )
        return result.returncode == 0

    def test_ignores_generated_runtime_and_evidence(self) -> None:
        for path in (
            ".runtime/secrets.env",
            "build/generation/service.jar",
            "docker/.jars/generation/service.jar",
            "docker/jars/service.jar",
            "evidence/release.json",
        ):
            with self.subTest(path=path):
                self.assertTrue(self.ignored(path))

    def test_does_not_ignore_source_material(self) -> None:
        self.assertFalse(self.ignored("scripts/build-release.sh"))


if __name__ == "__main__":
    unittest.main()
