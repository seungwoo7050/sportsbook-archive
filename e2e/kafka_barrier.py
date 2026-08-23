from __future__ import annotations

from e2e.runtime import E2eRuntime
from scripts.cold_gate.fixture_receipt import FixtureReceipt
from scripts.cold_gate.polling import poll_until


def wait_consumed(
    runtime: E2eRuntime,
    group: str,
    receipt: FixtureReceipt,
    *,
    timeout: float = 60,
) -> None:
    poll_until(
        f"{group} consumption of {receipt.topic}",
        lambda: runtime.kafka.committed_offset(group, receipt.topic, receipt.partition),
        lambda offset: offset > receipt.offset,
        timeout=timeout,
        interval=0.5,
    )
