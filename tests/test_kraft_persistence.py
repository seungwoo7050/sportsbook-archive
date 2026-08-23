from tests.compose_fixture import ComposeFixture


class KraftPersistenceTest(ComposeFixture):
    def test_preserves_cluster_metadata_across_container_recreation(self) -> None:
        started = self.compose("up", "--detach", "--wait", "kafka")
        self.assertEqual(started.returncode, 0, started.stderr)

        quorum = self.kafka(
            "kafka-metadata-quorum.sh",
            "--bootstrap-server",
            "localhost:9092",
            "describe",
            "--status",
        )
        self.assertEqual(quorum.returncode, 0, quorum.stderr)
        self.assertIn("ClusterId:              MkU3OEVBNTcwNTJENDM2Qk", quorum.stdout)
        self.assertRegex(quorum.stdout, r"LeaderId:\s+1")

        created = self.kafka(
            "kafka-topics.sh",
            "--bootstrap-server",
            "localhost:9092",
            "--create",
            "--topic",
            "kraft.persistence.probe",
            "--partitions",
            "3",
            "--replication-factor",
            "1",
        )
        self.assertEqual(created.returncode, 0, created.stderr)
        metadata_before = self.compose(
            "exec", "--no-TTY", "kafka", "cat", "/var/lib/kafka/data/meta.properties"
        )
        self.assertEqual(metadata_before.returncode, 0, metadata_before.stderr)

        stopped = self.compose("down")
        self.assertEqual(stopped.returncode, 0, stopped.stderr)
        restarted = self.compose("up", "--detach", "--wait", "kafka")
        self.assertEqual(restarted.returncode, 0, restarted.stderr)

        metadata_after = self.compose(
            "exec", "--no-TTY", "kafka", "cat", "/var/lib/kafka/data/meta.properties"
        )
        described = self.kafka(
            "kafka-topics.sh",
            "--bootstrap-server",
            "localhost:9092",
            "--describe",
            "--topic",
            "kraft.persistence.probe",
        )
        self.assertEqual(metadata_after.returncode, 0, metadata_after.stderr)
        self.assertEqual(metadata_after.stdout, metadata_before.stdout)
        self.assertEqual(described.returncode, 0, described.stderr)
        self.assertIn("PartitionCount: 3", described.stdout)
        self.assertIn("ReplicationFactor: 1", described.stdout)

    def kafka(self, command: str, *arguments: str):
        return self.compose(
            "exec", "--no-TTY", "kafka", f"/opt/kafka/bin/{command}", *arguments
        )


if __name__ == "__main__":
    import unittest

    unittest.main()
