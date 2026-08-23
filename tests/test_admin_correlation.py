import pathlib
import types
import unittest

from e2e.admin_correlation import MAX_ADMIN_SCAN_RECORDS, wait_admin_record
from scripts.cold_gate.kafka_record import KafkaRecord


TARGET = "77000000-0000-7000-8000-000000000013"


def record(partition: int, offset: int, action_id: str) -> KafkaRecord:
    return KafkaRecord(
        "admin.action",
        partition,
        offset,
        "e2e-admin",
        b"record",
        "0" * 64,
        {},
        {"actionId": action_id},
    )


class FakeKafka:
    def __init__(self, offsets: tuple[int, int, int]) -> None:
        self.offsets = offsets

    def end_offset(self, _topic: str, partition: int) -> int:
        return self.offsets[partition]


class FakeProbe:
    def __init__(self, records: dict[tuple[int, int], KafkaRecord]) -> None:
        self.records = records
        self.calls: list[tuple[int, int]] = []

    def read(self, _topic: str, partition: int, offset: int, _schema) -> KafkaRecord:
        self.calls.append((partition, offset))
        return self.records[(partition, offset)]


class AdminCorrelationTest(unittest.TestCase):
    def runtime(self, offsets, records):
        return types.SimpleNamespace(
            kafka=FakeKafka(offsets),
            probe=FakeProbe(records),
            artifacts=types.SimpleNamespace(sources=pathlib.Path("/release/sources")),
        )

    def test_selects_action_id_while_allowing_other_admin_records(self) -> None:
        records = {
            (0, 0): record(0, 0, "late-candidate-action"),
            (0, 1): record(0, 1, TARGET),
            (1, 0): record(1, 0, "late-revision-action"),
        }
        runtime = self.runtime((2, 1, 0), records)

        observed = wait_admin_record(runtime, (0, 0, 0), TARGET)

        self.assertIs(observed, records[(0, 1)])
        self.assertEqual(runtime.probe.calls, [(0, 0), (0, 1), (1, 0)])

    def test_rejects_an_unbounded_admin_record_window(self) -> None:
        runtime = self.runtime((MAX_ADMIN_SCAN_RECORDS + 1, 0, 0), {})

        with self.assertRaisesRegex(RuntimeError, "bounded window"):
            wait_admin_record(runtime, (0, 0, 0), TARGET)


if __name__ == "__main__":
    unittest.main()
