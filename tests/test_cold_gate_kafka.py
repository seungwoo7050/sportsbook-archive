import subprocess
import unittest

from scripts.cold_gate.kafka import KafkaAdmin


class FakeCompose:
    def __init__(self, output: str, failure: bool = False) -> None:
        self.output = output
        self.failure = failure
        self.calls = []

    def run(self, *arguments, **options):
        self.calls.append((arguments, options))
        if self.failure:
            raise subprocess.CalledProcessError(1, arguments, stderr="credential")
        return subprocess.CompletedProcess(arguments, 0, stdout=self.output)


class ColdGateKafkaTest(unittest.TestCase):
    def test_parses_exact_topic_partition_assignments(self) -> None:
        output = """
GROUP TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG CONSUMER-ID HOST CLIENT-ID
settlement-service match.result 0 4 4 0 consumer /172.1 client
settlement-service match.result 1 2 2 0 consumer /172.1 client
settlement-service event.lifecycle 2 0 0 0 consumer /172.1 client
"""
        compose = FakeCompose(output)

        assignments = KafkaAdmin(compose).assignments("settlement-service")

        self.assertEqual(
            assignments,
            {("match.result", 0), ("match.result", 1), ("event.lifecycle", 2)},
        )
        arguments, options = compose.calls[0]
        self.assertEqual(arguments[:3], ("exec", "-T", "kafka"))
        self.assertEqual(arguments[-2:], ("--group", "settlement-service"))
        self.assertEqual(options, {"capture_output": True})

    def test_rejects_unknown_groups_duplicate_rows_and_invalid_partitions(self) -> None:
        with self.assertRaisesRegex(ValueError, "outside"):
            KafkaAdmin(FakeCompose("")).assignments("unknown")
        duplicate = "\n".join(
            ["settlement-service match.result 0 0 0 0 c h id"] * 2
        )
        with self.assertRaisesRegex(RuntimeError, "duplicated"):
            KafkaAdmin(FakeCompose(duplicate)).assignments("settlement-service")
        with self.assertRaisesRegex(RuntimeError, "out of range"):
            KafkaAdmin(
                FakeCompose("settlement-service match.result 3 0 0 0 c h id\n")
            ).assignments("settlement-service")

    def test_hides_kafka_transport_output(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "settlement-service") as captured:
            KafkaAdmin(FakeCompose("", failure=True)).assignments("settlement-service")
        self.assertNotIn("credential", str(captured.exception))

    def test_reads_one_numeric_committed_offset(self) -> None:
        output = "betting-service bet.resolution.revised.v1 2 41 41 0 c h id\n"
        admin = KafkaAdmin(FakeCompose(output))

        self.assertEqual(
            admin.committed_offset("betting-service", "bet.resolution.revised.v1", 2),
            41,
        )
        with self.assertRaisesRegex(RuntimeError, "not unique"):
            KafkaAdmin(FakeCompose("")).committed_offset(
                "betting-service", "bet.resolution.revised.v1", 2
            )
        with self.assertRaisesRegex(RuntimeError, "unavailable"):
            KafkaAdmin(
                FakeCompose("betting-service bet.resolution.revised.v1 2 - 0 0 c h id\n")
            ).committed_offset("betting-service", "bet.resolution.revised.v1", 2)


if __name__ == "__main__":
    unittest.main()
