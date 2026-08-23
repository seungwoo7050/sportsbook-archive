import hashlib
import zipfile

from tests.staging_fixture import StagingFixture


SERVICES = {"wallet", "risk", "odds", "betting", "gateway", "settlement", "admin"}


class JarStagingCompletenessTest(StagingFixture):
    def test_publishes_exactly_one_complete_generation(self) -> None:
        result = self.stage()

        self.assertEqual(result.returncode, 0, result.stderr)
        active = self.active_generation()
        self.assertTrue((self.docker / "jars").is_symlink())
        self.assertEqual(
            {path.name for path in active.iterdir()},
            {*(f"{service}.jar" for service in SERVICES), "SHA256SUMS"},
        )

        expected_sums = []
        for service in sorted(SERVICES):
            jar = active / f"{service}.jar"
            with zipfile.ZipFile(jar) as archive:
                self.assertEqual(
                    archive.read("BOOT-INF/classes/Probe.class"), service.encode()
                )
            expected_sums.append(f"{hashlib.sha256(jar.read_bytes()).hexdigest()}  {jar.name}")

        self.assertCountEqual(
            (active / "SHA256SUMS").read_text().splitlines(), expected_sums
        )
        self.assertEqual(len(list((self.docker / ".jars").iterdir())), 1)

    def test_rejects_non_release_jdk_before_publication(self) -> None:
        java = self.jdk / "bin/java"
        java.write_text('#!/bin/sh\nprintf \'openjdk version "21.0.0"\\n\' >&2\n')
        java.chmod(0o755)

        result = self.stage()

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Java 17 JDK is required", result.stderr)
        self.assertFalse((self.docker / "jars").exists())


if __name__ == "__main__":
    import unittest

    unittest.main()
