from __future__ import annotations

import dataclasses


OUTCOMES = frozenset({"WON", "LOST", "PUSH", "VOID"})


@dataclasses.dataclass(frozen=True)
class ScenarioIds:
    number: int
    user: str
    event: str
    market: str
    selection: str

    @classmethod
    def create(cls, number: int) -> "ScenarioIds":
        if number < 1 or number > 999_999_999_999:
            raise ValueError("scenario number is out of range")
        suffix = f"{number:012d}"
        return cls(
            number,
            f"01000000-0000-7000-8000-{suffix}",
            f"11000000-0000-7000-8000-{suffix}",
            f"22000000-0000-7000-8000-{suffix}",
            f"33000000-0000-7000-8000-{suffix}",
        )

    def account(self) -> dict[str, object]:
        return {"userId": self.user, "currency": "KRW"}

    def transfer(self, amount: int) -> dict[str, object]:
        if amount <= 0:
            raise ValueError("transfer amount must be positive")
        return {"userId": self.user, "amount": money(amount)}

    def placement(self) -> dict[str, object]:
        return {
            "slipType": {"type": "SINGLE"},
            "selections": [
                {
                    "eventId": self.event,
                    "marketId": self.market,
                    "selectionId": self.selection,
                    "odds": 2.0000,
                }
            ],
            "stake": money(10_000),
        }

    def match_result(self, outcome: str, settled_at_ms: int) -> dict[str, object]:
        if outcome not in OUTCOMES or settled_at_ms <= 0:
            raise ValueError("match result is invalid")
        return {
            "eventId": self.event,
            "score": "1-0",
            "finalStatus": "COMPLETED",
            "resultDetail": {self.selection: outcome},
            "settledAt": settled_at_ms,
        }

    def cancelled(self, occurred_at_ms: int) -> dict[str, object]:
        if occurred_at_ms <= 60_000:
            raise ValueError("lifecycle timestamp is invalid")
        return {
            "eventId": self.event,
            "status": "CANCELLED",
            "occurredAt": occurred_at_ms,
            "scheduledStartAt": occurred_at_ms - 60_000,
        }


def money(amount: int) -> dict[str, object]:
    return {"amount": amount, "currency": "KRW"}
