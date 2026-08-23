from __future__ import annotations

import time
from collections.abc import Callable
from typing import TypeVar


T = TypeVar("T")


def poll_until(
    description: str,
    probe: Callable[[], T],
    accepted: Callable[[T], bool],
    *,
    timeout: float = 60.0,
    interval: float = 0.25,
    clock: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> T:
    if not description or timeout <= 0 or interval <= 0:
        raise ValueError("polling contract is invalid")
    deadline = clock() + timeout
    last_value: T | None = None
    last_error: Exception | None = None
    while True:
        try:
            last_value = probe()
            last_error = None
        except Exception as error:  # Transient dependencies may not be ready yet.
            last_error = error
        else:
            if accepted(last_value):
                return last_value
        remaining = deadline - clock()
        if remaining <= 0:
            detail = f"; last error: {last_error}" if last_error else f"; last value: {last_value!r}"
            raise TimeoutError(f"timed out waiting for {description}{detail}")
        sleep(min(interval, remaining))
