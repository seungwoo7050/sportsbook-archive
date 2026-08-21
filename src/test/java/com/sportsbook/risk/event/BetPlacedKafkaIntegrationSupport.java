package com.sportsbook.risk.event;

import static org.mockito.Mockito.reset;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(
    properties = {
      "risk.auth.betting-service-api-key=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      "risk.auth.admin-api-key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "risk.auth.platform-api-key=pppppppppppppppppppppppppppppppp",
      "management.health.redis.enabled=false",
      "management.endpoint.health.validate-group-membership=false"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = {"bet.placed.v1", "bet.placed.v1.DLT"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class BetPlacedKafkaIntegrationSupport {
  static final String DLT_TOPIC = "bet.placed.v1.DLT";

  @Autowired private KafkaTemplate<String, byte[]> kafka;
  @Autowired private EmbeddedKafkaBroker broker;
  @MockBean protected AcceptedBetReconciler reconciler;

  @BeforeEach
  void resetReconciler() {
    reset(reconciler);
  }

  void publish(String key, byte[] payload) throws Exception {
    kafka.send("bet.placed.v1", key, payload).get(10, TimeUnit.SECONDS);
  }

  ConsumerRecord<String, byte[]> consumeDeadLetter() {
    Map<String, Object> properties =
        KafkaTestUtils.consumerProps("risk-dlt-test-" + UUID.randomUUID(), "false", broker);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    try (Consumer<String, byte[]> consumer =
        new DefaultKafkaConsumerFactory<>(
                properties, new StringDeserializer(), new ByteArrayDeserializer())
            .createConsumer()) {
      broker.consumeFromAnEmbeddedTopic(consumer, DLT_TOPIC);
      return KafkaTestUtils.getSingleRecord(consumer, DLT_TOPIC, Duration.ofSeconds(10));
    }
  }
}
