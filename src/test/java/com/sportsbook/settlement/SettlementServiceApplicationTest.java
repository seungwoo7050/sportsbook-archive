package com.sportsbook.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.config.RawKafkaProducerConfiguration;
import com.sportsbook.settlement.config.SettlementWorkerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class SettlementServiceApplicationTest {

  @Autowired
  @Qualifier(RawKafkaProducerConfiguration.OPERATIONS)
  private KafkaOperations<byte[], byte[]> rawOperations;

  @Autowired private KafkaTemplate<Object, Object> defaultTemplate;
  @Autowired private ApplicationContext context;

  @Test
  void loadsApplicationContextWithIsolatedKafkaTemplates() {
    assertThat(rawOperations).isNotSameAs(defaultTemplate);
    assertThat(context.containsBean(SettlementWorkerConfiguration.RECOVERY)).isFalse();
  }
}
