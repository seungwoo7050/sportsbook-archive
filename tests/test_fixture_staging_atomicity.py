import hashlib

from tests.fixture_staging_fixture import FixtureStagingFixture


class FixtureStagingAtomicityTest(FixtureStagingFixture):
    def test_failed_build_preserves_the_published_tool(self) -> None:
        first = self.stage()
        self.assertEqual(first.returncode, 0, first.stderr)
        staged = self.output / "avro-fixture-publisher.jar"
        before = hashlib.sha256(staged.read_bytes()).hexdigest()

        failed = self.stage(FAIL_FIXTURE="true")

        self.assertNotEqual(failed.returncode, 0)
        self.assertEqual(hashlib.sha256(staged.read_bytes()).hexdigest(), before)
        self.assertEqual([path.name for path in self.output.iterdir()], [staged.name])


if __name__ == "__main__":
    import unittest

    unittest.main()
