import hashlib
import zipfile

from tests.fixture_staging_fixture import BUILT, FixtureStagingFixture


class FixtureStagingTest(FixtureStagingFixture):
    def test_stages_only_the_shaded_java17_publisher(self) -> None:
        result = self.stage()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(BUILT.parent.exists())
        staged = self.output / "avro-fixture-publisher.jar"
        self.assertEqual([path.name for path in self.output.iterdir()], [staged.name])
        self.assertEqual(staged.stat().st_mode & 0o777, 0o444)
        self.assertIn(hashlib.sha256(staged.read_bytes()).hexdigest(), result.stdout)
        with zipfile.ZipFile(staged) as archive:
            names = set(archive.namelist())
            self.assertIn(
                "com/sportsbook/orchestration/fixture/FixturePublisher.class", names
            )
            self.assertIn("com/sportsbook/orchestration/fixture/KafkaProbe.class", names)
            self.assertIn(
                "com/sportsbook/protocol/event/EventLifecycle.class", names
            )
            self.assertIn(
                "org/apache/kafka/clients/producer/KafkaProducer.class", names
            )
            self.assertEqual(
                archive.read(
                    "META-INF/maven/com.sportsbook/shared-protocol/pom.properties"
                ),
                b"version=1.0.0\n",
            )


if __name__ == "__main__":
    import unittest

    unittest.main()
