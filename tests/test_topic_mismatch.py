from tests.kafka_fixture import KafkaFixture


class TopicMismatchTest(KafkaFixture):
    def test_partition_mismatch_fails_before_creating_other_topics(self) -> None:
        created = self.create_topic("wallet.debited.v1", partitions=2)
        self.assertEqual(created.returncode, 0, created.stderr)
        before = self.topic_state()

        initialized = self.initialize_topics()

        self.assertNotEqual(initialized.returncode, 0)
        self.assertIn("wallet.debited.v1 partition mismatch", initialized.stderr)
        self.assertEqual(self.topic_state(), before)
        self.assertEqual(set(before), {"wallet.debited.v1"})

    def test_short_dlt_retention_fails_without_altering_the_topic(self) -> None:
        created = self.create_topic(
            "match.result.DLT", partitions=3, retention=60_000
        )
        self.assertEqual(created.returncode, 0, created.stderr)
        before = self.topic_state()

        initialized = self.initialize_topics()

        self.assertNotEqual(initialized.returncode, 0)
        self.assertIn("match.result.DLT retention is too short", initialized.stderr)
        self.assertEqual(self.topic_state(), before)
        self.assertEqual(before["match.result.DLT"], (3, 1, 60_000))


if __name__ == "__main__":
    import unittest

    unittest.main()
