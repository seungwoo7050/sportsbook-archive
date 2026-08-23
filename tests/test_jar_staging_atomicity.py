import hashlib

from tests.staging_fixture import StagingFixture


class JarStagingAtomicityTest(StagingFixture):
    def test_failed_rebuild_preserves_the_active_generation(self) -> None:
        first = self.stage()
        self.assertEqual(first.returncode, 0, first.stderr)
        active = self.active_generation()
        link_target = (self.docker / "jars").readlink()
        before = {
            path.name: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in active.iterdir()
        }

        failed = self.stage(FAIL_LOGICAL="risk")

        self.assertNotEqual(failed.returncode, 0)
        self.assertEqual((self.docker / "jars").readlink(), link_target)
        self.assertEqual(
            {
                path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                for path in active.iterdir()
            },
            before,
        )
        self.assertEqual(list((self.docker / ".jars").iterdir()), [active])


if __name__ == "__main__":
    import unittest

    unittest.main()
