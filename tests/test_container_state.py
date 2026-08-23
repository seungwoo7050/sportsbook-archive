import unittest

from scripts.cold_gate.container_state import ContainerState


PROJECT = "sb-gate-0123456789ab-00000001"
CONTAINER = "a" * 64
IMAGE = "sha256:" + "b" * 64


def receipt(service: str, state: str, health: str, exit_code: int) -> str:
    return "\t".join(
        (
            CONTAINER,
            f"/{PROJECT}-{service}-1",
            IMAGE,
            state,
            health,
            str(exit_code),
            PROJECT,
            service,
        )
    ) + "\n"


class ContainerStateTest(unittest.TestCase):
    def test_accepts_running_and_completed_service_contracts(self) -> None:
        wallet = ContainerState.parse(
            receipt("wallet", "running", "healthy", 0), PROJECT, "wallet"
        )
        topic_init = ContainerState.parse(
            receipt("topic-init", "exited", "-", 0), PROJECT, "topic-init"
        )

        self.assertEqual(wallet.state, "running")
        self.assertEqual(wallet.image_id, IMAGE)
        self.assertEqual(topic_init.exit_code, 0)

    def test_rejects_wrong_health_exit_labels_and_ids(self) -> None:
        invalid = (
            (receipt("wallet", "running", "unhealthy", 0), "wallet"),
            (receipt("topic-init", "exited", "-", 1), "topic-init"),
            (receipt("wallet", "running", "healthy", 0).replace(PROJECT, "other", 1), "wallet"),
            (receipt("wallet", "running", "healthy", 0).replace(CONTAINER, "short"), "wallet"),
        )
        for output, service in invalid:
            with self.subTest(output=output):
                with self.assertRaises(RuntimeError):
                    ContainerState.parse(output, PROJECT, service)

    def test_rejects_unknown_services_and_ambiguous_receipts(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "invalid"):
            ContainerState.parse(receipt("wallet", "running", "healthy", 0), PROJECT, "unknown")
        with self.assertRaisesRegex(RuntimeError, "invalid"):
            ContainerState.parse("too\tfew\tfields\n", PROJECT, "wallet")


if __name__ == "__main__":
    unittest.main()
