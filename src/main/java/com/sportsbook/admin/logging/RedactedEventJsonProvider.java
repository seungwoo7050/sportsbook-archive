package com.sportsbook.admin.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.regex.Pattern;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

public final class RedactedEventJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

  private static final String REDACTED = "[REDACTED]";
  private static final Pattern LABELLED_SECRET =
      Pattern.compile(
          "(?i)((?:[\"']?)(?:authorization|idempotency[-_ ]?key|"
              + "x[-_ ]?internal[-_ ]?api[-_ ]?key|x[-_ ]?api[-_ ]?key|"
              + "api[-_ ]?key|password|token)(?:[\"']?)\\s*[:=]\\s*)"
              + "(?:bearer\\s+)?(?:\"[^\"]*\"|'[^']*'|[^\\r\\n,;]+)");
  private static final Pattern BEARER_SECRET = Pattern.compile("(?i)\\bbearer\\s+[^\\s,;]+");

  public RedactedEventJsonProvider() {
    setFieldName("message");
  }

  @Override
  public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
    generator.writeStringField(getFieldName(), redact(event.getFormattedMessage()));
    if (event.getThrowableProxy() != null) {
      generator.writeStringField(
          "stack_trace", redact(ThrowableProxyUtil.asString(event.getThrowableProxy())));
    }
  }

  static String redact(String value) {
    if (value == null) {
      return "";
    }
    String labelled = LABELLED_SECRET.matcher(value).replaceAll("$1" + REDACTED);
    return BEARER_SECRET.matcher(labelled).replaceAll("Bearer " + REDACTED);
  }
}
