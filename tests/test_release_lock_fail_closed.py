import pathlib

from tests.staging_fixture import StagingFixture


class ReleaseLockFailClosedTest(StagingFixture):
    def test_rejects_partial_manifest_before_building_services(self) -> None:
        lock = self.temporary_path / "partial.lock"
        lock.write_text(
            "shared|shared-protocol|"
            "f9de6bc1e533761ab4bb1454d8d4ab8175cdf001|shared-protocol-1.0.0.jar\n"
        )

        result = self.stage(SERVICES_LOCK=str(lock))

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("invalid services lock", result.stderr)
        self.assertFalse((self.docker / "jars").exists())
        for source in self.sources.iterdir():
            if source.name != "shared":
                self.assertFalse((source / "target").exists())


if __name__ == "__main__":
    import unittest

    unittest.main()
