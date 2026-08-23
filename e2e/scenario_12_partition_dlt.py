from __future__ import annotations

from e2e.kafka_barrier import wait_consumed
from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime
from scripts.cold_gate.polling import poll_until


NAME = "partition-two-poison-dlt"
DLT = "match.result.DLT"
POISON_HASH = "76be8b528d0075f7aae98d6fa57a6d3c83ae480a8469e668d7b0af968995ac71"


def run(runtime: E2eRuntime) -> None:
    fixture = ScenarioIds.create(12)
    start = runtime.kafka.end_offset(DLT, 2)
    source = runtime.fixtures.poison_match_result(fixture.event)

    def appended(end: int) -> bool:
        if end > start + 1:
            raise RuntimeError("poison scenario appended more than one DLT record")
        return end == start + 1

    poll_until(
        "partition two DLT append",
        lambda: runtime.kafka.end_offset(DLT, 2),
        appended,
        timeout=60,
        interval=0.5,
    )
    record = runtime.probe.read(DLT, 2, start)
    if (
        source.partition != 2
        or source.sha256 != POISON_HASH
        or record.key != fixture.event
        or record.value != b"\x80"
        or record.value_sha256 != POISON_HASH
    ):
        raise RuntimeError("partition two DLT did not preserve record identity")
    expected_headers = {
        "settlement-dlt-original-topic",
        "settlement-dlt-original-partition",
        "settlement-dlt-original-offset",
        "settlement-dlt-original-timestamp",
        "settlement-dlt-consumer-group",
        "settlement-dlt-exception-type",
    }
    if set(record.headers) != expected_headers or any(
        len(values) != 1 for values in record.headers.values()
    ):
        raise RuntimeError("partition two DLT header inventory drifted")
    if record.headers["settlement-dlt-original-topic"][0] != b"match.result":
        raise RuntimeError("DLT original topic drifted")
    if int.from_bytes(
        record.headers["settlement-dlt-original-partition"][0], "big", signed=True
    ) != 2:
        raise RuntimeError("DLT original partition drifted")
    if int.from_bytes(
        record.headers["settlement-dlt-original-offset"][0], "big", signed=True
    ) != source.offset:
        raise RuntimeError("DLT original offset drifted")
    if int.from_bytes(
        record.headers["settlement-dlt-original-timestamp"][0], "big", signed=True
    ) <= 0:
        raise RuntimeError("DLT original timestamp is invalid")
    if record.headers["settlement-dlt-consumer-group"][0] != b"settlement-service":
        raise RuntimeError("DLT consumer group drifted")
    try:
        exception_type = record.headers["settlement-dlt-exception-type"][0].decode("utf-8")
    except UnicodeDecodeError as error:
        raise RuntimeError("DLT exception type is not UTF-8") from error
    if not exception_type:
        raise RuntimeError("DLT exception type is empty")
    wait_consumed(runtime, "settlement-service", source)
