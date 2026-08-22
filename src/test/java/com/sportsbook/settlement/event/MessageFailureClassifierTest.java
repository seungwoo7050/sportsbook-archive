package com.sportsbook.settlement.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.event.MessageFailureClassifier.Disposition;
import com.sportsbook.settlement.readmodel.PlacementContractException;
import java.sql.SQLTransientException;
import org.junit.jupiter.api.Test;

class MessageFailureClassifierTest {

  private final MessageFailureClassifier classifier = new MessageFailureClassifier();

  @Test
  void sendsBoundaryContractFailuresToPermanentRecovery() {
    assertThat(classifier.classify(new IllegalArgumentException("invalid key")))
        .isEqualTo(Disposition.PERMANENT);
    assertThat(classifier.classify(new PlacementContractException("invalid placement")))
        .isEqualTo(Disposition.PERMANENT);
    assertThat(
            classifier.classify(
                new RuntimeException(
                    "listener", new StrictAvroDecoder.DecodeException("invalid avro"))))
        .isEqualTo(Disposition.PERMANENT);
  }

  @Test
  void keepsInfrastructureAndUnknownFailuresRetryable() {
    assertThat(classifier.classify(new SQLTransientException("database unavailable")))
        .isEqualTo(Disposition.TRANSIENT);
    assertThat(classifier.classify(new IllegalStateException("unexpected")))
        .isEqualTo(Disposition.TRANSIENT);
  }
}
