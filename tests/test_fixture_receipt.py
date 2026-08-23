import unittest

from scripts.cold_gate.fixture_receipt import FixtureReceipt


KEY = "11000000-0000-7000-8000-0000000000ab"
HASH = "a" * 64


class FixtureReceiptTest(unittest.TestCase):
    def test_parses_an_exact_acknowledged_publication(self) -> None:
        receipt = FixtureReceipt.parse(
            f"topic=match.result\tkey={KEY}\tpartition=2\toffset=19"
            f"\tsha256={HASH}\tfingerprint=3f39fbc4bbfea727\n"
        )

        self.assertEqual(receipt.topic, "match.result")
        self.assertEqual(receipt.key, KEY)
        self.assertEqual(receipt.partition, 2)
        self.assertEqual(receipt.offset, 19)
        self.assertEqual(receipt.sha256, HASH)
        self.assertEqual(receipt.fingerprint, "3f39fbc4bbfea727")

    def test_accepts_only_the_fixed_poison_receipt_shape(self) -> None:
        receipt = FixtureReceipt.parse(
            f"topic=match.result\tkey={KEY}\tpartition=2\toffset=0"
            f"\tsha256={HASH}\tfingerprint=malformed\n"
        )
        self.assertEqual(receipt.fingerprint, "malformed")

    def test_rejects_unowned_topics_and_malformed_fields(self) -> None:
        valid = (
            f"topic=match.result\tkey={KEY}\tpartition=2\toffset=0"
            f"\tsha256={HASH}\tfingerprint=malformed\n"
        )
        invalid = (
            valid.replace("match.result", "unknown.topic"),
            valid.replace("partition=2", "partition=3"),
            valid.replace("offset=0", "offset=-1"),
            valid.replace(KEY, KEY.upper()),
            valid + "extra\n",
        )
        for output in invalid:
            with self.subTest(output=output):
                with self.assertRaises(RuntimeError):
                    FixtureReceipt.parse(output)


if __name__ == "__main__":
    unittest.main()
