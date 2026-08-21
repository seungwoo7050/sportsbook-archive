package com.sportsbook.oddsfeed.kafka;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public final class BrokerAvailability {

  private final AtomicBoolean available = new AtomicBoolean();

  public boolean isAvailable() {
    return available.get();
  }

  public void markAvailable() {
    available.set(true);
  }

  public void markUnavailable() {
    available.set(false);
  }
}
