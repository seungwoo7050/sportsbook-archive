import re

from tests.compose_fixture import ComposeFixture


class KafkaFixture(ComposeFixture):
    def setUp(self) -> None:
        super().setUp()
        started = self.compose("up", "--detach", "--wait", "kafka")
        self.assertEqual(started.returncode, 0, started.stderr)

    def kafka(self, command: str, *arguments: str):
        return self.compose(
            "exec", "--no-TTY", "kafka", f"/opt/kafka/bin/{command}", *arguments
        )

    def initialize_topics(self):
        return self.compose("run", "--rm", "--no-deps", "topic-init")

    def create_topic(self, topic: str, partitions: int, retention: int | None = None):
        arguments = [
            "--bootstrap-server",
            "localhost:9092",
            "--create",
            "--topic",
            topic,
            "--partitions",
            str(partitions),
            "--replication-factor",
            "1",
        ]
        if retention is not None:
            arguments.extend(["--config", f"retention.ms={retention}"])
        return self.kafka("kafka-topics.sh", *arguments)

    def topic_state(self) -> dict[str, tuple[int, int, int | None]]:
        listed = self.kafka(
            "kafka-topics.sh", "--bootstrap-server", "localhost:9092", "--list"
        )
        self.assertEqual(listed.returncode, 0, listed.stderr)
        topics = sorted(topic for topic in listed.stdout.splitlines() if not topic.startswith("__"))
        state = {}
        for topic in topics:
            described = self.kafka(
                "kafka-topics.sh",
                "--bootstrap-server",
                "localhost:9092",
                "--describe",
                "--topic",
                topic,
            )
            self.assertEqual(described.returncode, 0, described.stderr)
            partitions = int(re.search(r"PartitionCount: (\d+)", described.stdout).group(1))
            replication = int(re.search(r"ReplicationFactor: (\d+)", described.stdout).group(1))
            retention = self.topic_retention(topic) if topic.endswith(".DLT") else None
            state[topic] = (partitions, replication, retention)
        return state

    def topic_retention(self, topic: str) -> int:
        config = self.kafka(
            "kafka-configs.sh",
            "--bootstrap-server",
            "localhost:9092",
            "--entity-type",
            "topics",
            "--entity-name",
            topic,
            "--describe",
        )
        self.assertEqual(config.returncode, 0, config.stderr)
        return int(re.search(r"retention.ms=(\d+)", config.stdout).group(1))
