from __future__ import annotations

import uuid
from collections.abc import Callable, Mapping

from scripts.cold_gate.polling import poll_until


def require_fields(actual: Mapping[str, object], expected: Mapping[str, object], name: str) -> None:
    drift = {
        key: {"expected": value, "actual": actual.get(key)}
        for key, value in expected.items()
        if actual.get(key) != value
    }
    if drift:
        raise RuntimeError(f"{name} drifted: {drift}")


def wait_fields(
    description: str,
    probe: Callable[[], Mapping[str, object] | None],
    expected: Mapping[str, object],
    *,
    timeout: float = 60,
    terminal: Mapping[str, frozenset[object]] | None = None,
) -> Mapping[str, object]:
    def accepted(actual: Mapping[str, object] | None) -> bool:
        if actual is None:
            return False
        for field, forbidden in (terminal or {}).items():
            if actual.get(field) in forbidden and actual.get(field) != expected.get(field):
                raise RuntimeError(
                    f"{description} entered terminal {field}={actual.get(field)!r}"
                )
        return all(actual.get(key) == value for key, value in expected.items())

    return poll_until(
        description,
        probe,
        accepted,
        timeout=timeout,
        interval=0.25,
    )


def require_uuidv7(value: str, name: str) -> str:
    try:
        parsed = uuid.UUID(value)
    except ValueError as error:
        raise RuntimeError(f"{name} is not a UUID") from error
    if str(parsed) != value or parsed.version != 7:
        raise RuntimeError(f"{name} is not a canonical UUIDv7")
    return value
