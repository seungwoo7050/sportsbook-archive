from tests.compose_fixture import ComposeFixture


class KafkaAutoCreateTest(ComposeFixture):
    def test_rejects_production_to_an_undeclared_topic(self) -> None:
        started = self.compose("up", "--detach", "--wait", "kafka")
        self.assertEqual(started.returncode, 0, started.stderr)

        produced = self.compose(
            "exec",
            "--no-TTY",
            "kafka",
            "/opt/kafka/bin/kafka-producer-perf-test.sh",
            "--topic",
            "undeclared.contract.probe",
            "--num-records",
            "1",
            "--record-size",
            "1",
            "--throughput",
            "-1",
            "--producer-props",
            "bootstrap.servers=localhost:9092",
            "max.block.ms=3000",
        )
        self.assertIn("0 records sent", produced.stdout)
        self.assertIn("UNKNOWN_TOPIC_OR_PARTITION", produced.stderr)
        self.assertIn("not present in metadata", produced.stderr)

        listed = self.compose(
            "exec",
            "--no-TTY",
            "kafka",
            "/opt/kafka/bin/kafka-topics.sh",
            "--bootstrap-server",
            "localhost:9092",
            "--list",
        )
        self.assertEqual(listed.returncode, 0, listed.stderr)
        self.assertNotIn("undeclared.contract.probe", listed.stdout.splitlines())


if __name__ == "__main__":
    import unittest

    unittest.main()
