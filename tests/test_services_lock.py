import pathlib
import re
import subprocess
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
EXPECTED = {
    "shared": ("shared-protocol", "shared-protocol-1.0.0.jar"),
    "wallet": ("wallet-service", "wallet-service-1.0.0.jar"),
    "risk": ("risk-service", "risk-service-1.0.0.jar"),
    "odds": ("odds-feed-service", "odds-feed-service-1.0.0.jar"),
    "betting": ("betting-service", "betting-service-1.0.0.jar"),
    "gateway": ("gateway", "gateway-1.0.0.jar"),
    "settlement": ("settlement-service", "settlement-service-1.0.0.jar"),
    "admin": ("admin-api", "admin-api-1.0.0.jar"),
}


def entries() -> list[tuple[str, str, str, str]]:
    lines = (ROOT / "services.lock").read_text().splitlines()
    return [tuple(line.split("|")) for line in lines if line and not line.startswith("#")]


def branch_tip(branch: str) -> str:
    for namespace in ("refs/heads", "refs/remotes/origin"):
        result = subprocess.run(
            ["git", "rev-parse", "--verify", f"{namespace}/{branch}^{{commit}}"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        if result.returncode == 0:
            return result.stdout.strip()
    raise AssertionError(f"{branch} ref is unavailable")


class ServicesLockTest(unittest.TestCase):
    def test_pins_every_release_branch_to_a_full_commit(self) -> None:
        locked = entries()
        self.assertEqual([entry[0] for entry in locked], list(EXPECTED))
        self.assertEqual(len({entry[2] for entry in locked}), len(locked))

        for logical, branch, commit, artifact in locked:
            with self.subTest(service=logical):
                self.assertEqual((branch, artifact), EXPECTED[logical])
                self.assertRegex(commit, re.compile(r"^[0-9a-f]{40}$"))
                object_type = subprocess.check_output(
                    ["git", "cat-file", "-t", commit], cwd=ROOT, text=True
                ).strip()
                self.assertEqual(object_type, "commit")
                self.assertEqual(branch_tip(branch), commit)

    def test_does_not_create_an_orchestration_lock_cycle(self) -> None:
        self.assertNotIn("orchestration", {entry[0] for entry in entries()})

    def test_settlement_release_declares_executable_packaging(self) -> None:
        commit = next(entry[2] for entry in entries() if entry[0] == "settlement")
        pom = subprocess.check_output(
            ["git", "show", f"{commit}:pom.xml"], cwd=ROOT, text=True
        )

        self.assertIn("<artifactId>spring-boot-maven-plugin</artifactId>", pom)

    def test_betting_release_locks_the_root_before_loading_legs(self) -> None:
        commit = next(entry[2] for entry in entries() if entry[0] == "betting")
        repository = subprocess.check_output(
            [
                "git",
                "show",
                f"{commit}:src/main/java/com/sportsbook/betting/persistence/BetRepository.java",
            ],
            cwd=ROOT,
            text=True,
        )

        self.assertIn("default Optional<Bet> findLockedByBetId", repository)
        self.assertIn("Optional<Bet> findLockedRootByBetId", repository)


if __name__ == "__main__":
    unittest.main()
