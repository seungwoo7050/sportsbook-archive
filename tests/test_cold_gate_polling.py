import unittest

from scripts.cold_gate.polling import poll_until


class FakeTime:
    def __init__(self) -> None:
        self.now = 0.0
        self.sleeps = []

    def clock(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.sleeps.append(seconds)
        self.now += seconds


class ColdGatePollingTest(unittest.TestCase):
    def test_returns_first_accepted_observation(self) -> None:
        timer = FakeTime()
        values = iter(("starting", "ready"))

        result = poll_until(
            "service readiness",
            lambda: next(values),
            lambda value: value == "ready",
            timeout=5,
            interval=1,
            clock=timer.clock,
            sleep=timer.sleep,
        )

        self.assertEqual(result, "ready")
        self.assertEqual(timer.sleeps, [1])

    def test_retries_transient_probe_errors(self) -> None:
        timer = FakeTime()
        attempts = 0

        def probe() -> str:
            nonlocal attempts
            attempts += 1
            if attempts == 1:
                raise ConnectionError("starting")
            return "ready"

        self.assertEqual(
            poll_until(
                "dependency",
                probe,
                lambda value: value == "ready",
                timeout=2,
                interval=0.5,
                clock=timer.clock,
                sleep=timer.sleep,
            ),
            "ready",
        )

    def test_timeout_reports_the_last_safe_observation(self) -> None:
        timer = FakeTime()
        with self.assertRaisesRegex(TimeoutError, "last value: 'pending'"):
            poll_until(
                "projection",
                lambda: "pending",
                lambda value: value == "ready",
                timeout=1,
                interval=0.6,
                clock=timer.clock,
                sleep=timer.sleep,
            )
        self.assertEqual(timer.sleeps, [0.6, 0.4])


if __name__ == "__main__":
    unittest.main()
