package com.sportsbook.settlement.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sportsbook.settlement.correction.ResultCandidateIntake;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.ResultFanout;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class MatchResultWireBoundaryTest {

  private final ResultCandidateIntake intake = mock(ResultCandidateIntake.class);
  private final AcceptedResultRepository accepted = mock(AcceptedResultRepository.class);
  private final ResultFanout fanout = mock(ResultFanout.class);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
  private final MatchResultListener listener =
      new MatchResultListener(intake, accepted, fanout, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

  @Test
  void rejectsAMismatchedRawEventKeyBeforeIntake() {
    ConsumerRecord<byte[], byte[]> valid = MatchResultListenerTest.record(UUID.randomUUID());
    ConsumerRecord<byte[], byte[]> mismatch =
        new ConsumerRecord<>(
            valid.topic(),
            valid.partition(),
            valid.offset(),
            UUID.randomUUID().toString().getBytes(UTF_8),
            valid.value());

    assertThatThrownBy(() -> listener.receive(mismatch, acknowledgment))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(intake, accepted, fanout, acknowledgment);
  }

  @Test
  void rejectsTrailingAvroBytesBeforeIntake() {
    ConsumerRecord<byte[], byte[]> valid = MatchResultListenerTest.record(UUID.randomUUID());
    byte[] trailing = Arrays.copyOf(valid.value(), valid.value().length + 1);
    ConsumerRecord<byte[], byte[]> malformed =
        new ConsumerRecord<>(
            valid.topic(), valid.partition(), valid.offset(), valid.key(), trailing);

    assertThatThrownBy(() -> listener.receive(malformed, acknowledgment))
        .isInstanceOf(StrictAvroDecoder.DecodeException.class);
    verifyNoInteractions(intake, accepted, fanout, acknowledgment);
  }
}
