from __future__ import annotations

from e2e.model import ScenarioIds
from e2e.runtime import E2eRuntime
from scripts.cold_gate.kafka_record import KafkaRecord
from scripts.cold_gate.polling import poll_until


def admin_topic_offsets(runtime: E2eRuntime) -> tuple[int, int, int]:
    return tuple(runtime.kafka.end_offset("admin.action", partition) for partition in range(3))


def wait_admin_record(
    runtime: E2eRuntime,
    before: tuple[int, int, int],
) -> KafkaRecord:
    def appended(after: tuple[int, int, int]) -> bool:
        deltas = tuple(current - prior for current, prior in zip(after, before, strict=True))
        if any(delta < 0 for delta in deltas) or sum(deltas) > 1:
            raise RuntimeError("Admin action topic changed by more than one record")
        return sorted(deltas) == [0, 0, 1]

    after = poll_until(
        "Admin action publication",
        lambda: admin_topic_offsets(runtime),
        appended,
        timeout=60,
        interval=0.5,
    )
    partition = next(
        index for index, (current, prior) in enumerate(zip(after, before, strict=True))
        if current == prior + 1
    )
    schema = (
        runtime.artifacts.sources
        / "admin/src/main/avro/com/sportsbook/admin/event/AdminActionRecorded.avsc"
    )
    return runtime.probe.read("admin.action", partition, before[partition], schema)


def require_odds_correlation(
    runtime: E2eRuntime,
    fixture: ScenarioIds,
    action_id: str,
) -> None:
    mapping_key = poll_until(
        "Odds action mapping",
        lambda: runtime.odds.scalar("GET", "oddsfeed:operator:action:" + action_id),
        lambda value: value.startswith("oddsfeed:operator:idempotency:"),
        timeout=30,
        interval=0.25,
    )
    metadata = runtime.odds.scalar("GET", mapping_key).split("|")
    if len(metadata) != 5 or metadata[1] != action_id:
        raise RuntimeError("Odds action metadata drifted")
    sequence = metadata[2]
    if not sequence.isdigit() or int(sequence) < 1:
        raise RuntimeError("Odds action sequence is invalid")
    committed_key = f"oddsfeed:operator:committed:{fixture.event}:{fixture.market}"
    poll_until(
        "Odds action commit",
        lambda: runtime.odds.scalar("GET", committed_key),
        lambda value: value == sequence,
        timeout=30,
        interval=0.25,
    )
    if runtime.odds.scalar("GET", f"market:{fixture.event}:{fixture.market}") != "SUSPENDED":
        raise RuntimeError("Odds market state did not commit the operator action")
