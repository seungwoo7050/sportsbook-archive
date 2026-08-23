import hashlib
import zipfile

from tests.fixture_staging_fixture import FixtureStagingFixture


class FixtureStagingTest(FixtureStagingFixture):
    def test_stages_only_the_shaded_java17_publisher(self) -> None:
        result = self.stage()

        self.assertEqual(result.returncode, 0, result.stderr)
        staged = self.output / "avro-fixture-publisher.jar"
        self.assertEqual([path.name for path in self.output.iterdir()], [staged.name])
        self.assertIn(hashlib.sha256(staged.read_bytes()).hexdigest(), result.stdout)
        with zipfile.ZipFile(staged) as archive:
            names = set(archive.namelist())
            self.assertIn(
                "com/sportsbook/orchestration/fixture/FixturePublisher.class", names
            )
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
