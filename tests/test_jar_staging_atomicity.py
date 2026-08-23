import hashlib

from scripts.cold_gate.cleanup_targets import discover_cleanup_targets
from scripts.cold_gate.context import ColdGateContext
from tests.staging_fixture import StagingFixture


SHA = "0123456789abcdef0123456789abcdef01234567"


class JarStagingAtomicityTest(StagingFixture):
    def test_first_build_failure_leaves_no_generation_root(self) -> None:
        failed = self.stage(FAIL_LOGICAL="wallet")

        self.assertNotEqual(failed.returncode, 0)
        self.assertFalse((self.docker / ".jars").exists())
        context = ColdGateContext.create(self.temporary_path, SHA, "00000001")
        self.assertEqual(discover_cleanup_targets(context), (None, None))

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
