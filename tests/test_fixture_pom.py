import pathlib
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[1]
POM = ROOT / "fixtures/avro-publisher/pom.xml"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class FixturePomTest(unittest.TestCase):
    def test_uses_java_17_locked_protocol_and_kafka_dependencies(self) -> None:
        root = ET.parse(POM).getroot()
        properties = root.find("m:properties", NS)
        self.assertEqual(properties.find("m:maven.compiler.release", NS).text, "17")

        dependencies = {
            (
                dependency.find("m:groupId", NS).text,
                dependency.find("m:artifactId", NS).text,
            ): dependency.find("m:version", NS).text
            for dependency in root.findall("m:dependencies/m:dependency", NS)
        }
        self.assertEqual(dependencies["com.sportsbook", "shared-protocol"], "1.0.0")
        self.assertEqual(dependencies["org.apache.kafka", "kafka-clients"], "${kafka.version}")
        self.assertEqual(properties.find("m:kafka.version", NS).text, "3.8.0")

        plugins = {
            plugin.find("m:artifactId", NS).text
            for plugin in root.findall("m:build/m:plugins/m:plugin", NS)
        }
        self.assertEqual(
            plugins,
            {"maven-compiler-plugin", "maven-surefire-plugin", "maven-shade-plugin"},
        )
        source = POM.read_text()
        self.assertNotIn("<repositories>", source)
        self.assertNotIn("SNAPSHOT", source)
        self.assertNotIn("systemPath", source)


if __name__ == "__main__":
    unittest.main()
