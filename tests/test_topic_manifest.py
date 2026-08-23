import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docker/kafka/topics.manifest"
SOURCES = {
    "wallet.debited.v1",
    "wallet.credited.v1",
    "wallet.debit-failed.v1",
    "risk.limit.violated",
    "risk.pattern.suspected",
    "odds.changed",
    "market.status.changed",
    "event.lifecycle",
    "match.result",
    "bet.placed.v1",
    "bet.settled.v1",
    "bet.voided.v1",
    "bet.resolution.revised.v1",
    "admin.action",
}
DLTS = {
    "wallet.debited.v1.DLT",
    "wallet.debit-failed.v1.DLT",
    "odds.changed.DLT",
    "event.lifecycle.DLT",
    "match.result.DLT",
    "bet.placed.v1.DLT",
    "bet.settled.v1.DLT",
    "bet.voided.v1.DLT",
    "bet.resolution.revised.v1.DLT",
}


class TopicManifestTest(unittest.TestCase):
    def test_declares_the_exact_source_and_uppercase_dlt_inventory(self) -> None:
        rows = [
            line.split("|")
            for line in MANIFEST.read_text().splitlines()
            if line and not line.startswith("#")
        ]
        self.assertTrue(all(len(row) == 4 for row in rows))
        self.assertEqual(len(rows), 23)
        self.assertEqual(len({row[0] for row in rows}), len(rows))

        sources = {row[0] for row in rows if not row[0].endswith(".DLT")}
        dlts = {row[0] for row in rows if row[0].endswith(".DLT")}
        self.assertEqual(sources, SOURCES)
        self.assertEqual(dlts, DLTS)

        for topic, partitions, replication, retention in rows:
            with self.subTest(topic=topic):
                self.assertEqual(partitions, "3")
                self.assertEqual(replication, "1")
                if topic.endswith(".DLT"):
                    self.assertGreaterEqual(int(retention), 604_800_000)
                    self.assertIn(topic.removesuffix(".DLT"), SOURCES)
                else:
                    self.assertEqual(retention, "-")


if __name__ == "__main__":
    unittest.main()
