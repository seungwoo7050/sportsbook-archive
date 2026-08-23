import hashlib
import pathlib
import tempfile
import unittest

from scripts.cold_gate.release_identity import jar_row, lock_entries


ROOT = pathlib.Path(__file__).resolve().parents[1]


class ReleaseIdentityTest(unittest.TestCase):
    def test_accepts_the_exact_eight_locked_release_rows(self) -> None:
        rows = lock_entries((ROOT / "services.lock").read_text())

        self.assertEqual(len(rows), 8)
        self.assertEqual(rows[0][0], "shared")
        self.assertEqual(rows[-1][0], "admin")
        self.assertTrue(all(len(row[2]) == 40 for row in rows))

    def test_rejects_missing_reordered_or_malformed_locks(self) -> None:
        lines = (ROOT / "services.lock").read_text().splitlines()
        variants = (
            "\n".join(lines[:-1]) + "\n",
            "\n".join([lines[0], lines[2], lines[1], *lines[3:]]) + "\n",
            (ROOT / "services.lock").read_text().replace(rows_sha(lines[1]), "invalid", 1),
        )
        for content in variants:
            with self.subTest(content=content):
                with self.assertRaises(RuntimeError):
                    lock_entries(content)

    def test_records_the_actual_staged_artifact_hash(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            artifact = pathlib.Path(temporary) / "wallet.jar"
            artifact.write_bytes(b"release")
            digest = hashlib.sha256(b"release").hexdigest()

            row = jar_row("wallet", "a" * 40, "source.jar", "wallet.jar", artifact)

            self.assertEqual(
                row,
                f"wallet\t{'a' * 40}\tsource.jar\twallet.jar\t{digest}",
            )


def rows_sha(line: str) -> str:
    return line.split("|")[2]


if __name__ == "__main__":
    unittest.main()
