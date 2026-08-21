package com.sportsbook.risk.event;

import com.sportsbook.risk.support.RedisTestSupport;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    properties = {
      "risk.auth.betting-service-api-key=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      "risk.auth.admin-api-key=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "risk.auth.platform-api-key=pppppppppppppppppppppppppppppppp"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = {"bet.placed.v1", "bet.placed.v1.DLT"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class BetPlacedKafkaRedisIntegrationSupport extends RedisTestSupport {
  @Autowired private KafkaTemplate<String, byte[]> kafka;
  @Autowired private EmbeddedKafkaBroker broker;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.data.redis.host", REDIS::getHost);
    properties.add("spring.data.redis.port", REDIS::getFirstMappedPort);
  }

  void publishAcceptedBet() throws Exception {
    kafka
        .send("bet.placed.v1", BetPlacedEventFixture.USER_ID, BetPlacedEventFixture.payload())
        .get(10, TimeUnit.SECONDS);
  }

  long committedSourceOffset() throws Exception {
    try (Admin admin =
        Admin.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()))) {
      OffsetAndMetadata offset =
          admin
              .listConsumerGroupOffsets("risk.bet-placed-consumer")
              .partitionsToOffsetAndMetadata()
              .get(10, TimeUnit.SECONDS)
              .get(new TopicPartition("bet.placed.v1", 0));
      return offset == null ? 0L : offset.offset();
    }
  }
}
