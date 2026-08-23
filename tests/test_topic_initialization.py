from tests.kafka_fixture import KafkaFixture
from tests.test_topic_manifest import DLTS, SOURCES


class TopicInitializationTest(KafkaFixture):
    def test_repeated_initialization_preserves_exact_broker_state(self) -> None:
        first = self.initialize_topics()
        self.assertEqual(first.returncode, 0, first.stderr)
        first_state = self.topic_state()

        second = self.initialize_topics()
        self.assertEqual(second.returncode, 0, second.stderr)
        second_state = self.topic_state()

        self.assertEqual(second_state, first_state)
        self.assertEqual(set(second_state), SOURCES | DLTS)
        for topic, (partitions, replication, retention) in second_state.items():
            with self.subTest(topic=topic):
                self.assertEqual(partitions, 3)
                self.assertEqual(replication, 1)
                if topic in DLTS:
                    self.assertGreaterEqual(retention, 604_800_000)
                else:
                    self.assertIsNone(retention)


if __name__ == "__main__":
    import unittest

    unittest.main()
