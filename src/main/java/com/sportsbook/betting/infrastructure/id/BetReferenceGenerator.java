package com.sportsbook.betting.infrastructure.id;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class BetReferenceGenerator {

  private static final char[] BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  public String next(Instant at) {
    StringBuilder value = new StringBuilder("B-").append(DATE.format(at)).append('-');
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (int index = 0; index < 8; index++) {
      value.append(BASE36[random.nextInt(BASE36.length)]);
    }
    return value.toString();
  }
}
