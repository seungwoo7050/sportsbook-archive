from __future__ import annotations

from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.inventory import SERVICES
from scripts.cold_gate.stack import ColdStack


class LogEvidence:
    def __init__(self, stack: ColdStack, store: EvidenceStore) -> None:
        if stack.context is not store.context:
            raise RuntimeError("log evidence ownership mismatch")
        self.stack = stack
        self.store = store

    def capture(self) -> None:
        self.stack.context.require_owned()
        for service in SERVICES:
            content = self.stack.logs(service)
            if "\0" in content:
                raise RuntimeError(f"{service} emitted an unsafe log")
            self.store.write(f"logs/{service}.log", content)
