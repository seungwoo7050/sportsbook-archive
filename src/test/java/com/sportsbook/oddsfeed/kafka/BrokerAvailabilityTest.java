package com.sportsbook.oddsfeed.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrokerAvailabilityTest {

  @Test
  void startsUnavailableAndTracksAcknowledgements() {
    BrokerAvailability availability = new BrokerAvailability();

    assertThat(availability.isAvailable()).isFalse();

    availability.markAvailable();
    assertThat(availability.isAvailable()).isTrue();

    availability.markUnavailable();
    assertThat(availability.isAvailable()).isFalse();
  }
}
