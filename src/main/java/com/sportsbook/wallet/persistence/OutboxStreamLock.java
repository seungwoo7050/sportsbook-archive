package com.sportsbook.wallet.persistence;

import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Allocates positions while holding the stream row until the surrounding transaction commits. */
@Component
public class OutboxStreamLock {
  private static final String CREATE_STREAM =
      """
      INSERT INTO outbox_stream(topic, partition_key, last_sequence)
      VALUES (:topic, :partitionKey, 0)
      ON CONFLICT (topic, partition_key) DO NOTHING
      """;
  private static final String NEXT_SEQUENCE =
      """
      UPDATE outbox_stream
      SET last_sequence = last_sequence + 1
      WHERE topic = :topic AND partition_key = :partitionKey
      RETURNING last_sequence
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public OutboxStreamLock(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long nextSequence(String topic, String partitionKey) {
    Map<String, ?> parameters =
        Map.of(
            "topic",
            required(topic, "topic"),
            "partitionKey",
            required(partitionKey, "partitionKey"));
    jdbc.update(CREATE_STREAM, parameters);
    Long sequence = jdbc.queryForObject(NEXT_SEQUENCE, parameters, Long.class);
    if (sequence == null || sequence < 1L) {
      throw new IllegalStateException("Outbox stream did not allocate a positive sequence");
    }
    return sequence;
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
