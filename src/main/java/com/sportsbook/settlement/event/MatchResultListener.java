package com.sportsbook.settlement.event;

import com.sportsbook.protocol.event.MatchResult;
import com.sportsbook.settlement.correction.CorrectionFanout;
import com.sportsbook.settlement.correction.ResultCandidateIntake;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import com.sportsbook.settlement.result.ResultFanout;
import java.time.Clock;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class MatchResultListener {

  private final ResultCandidateIntake intake;
  private final AcceptedResultRepository acceptedResults;
  private final ResultFanout fanout;
  private final CorrectionFanout corrections;
  private final Clock clock;
  private final StrictAvroDecoder decoder;
  private final KafkaUuidKeyValidator keys;
  private final MatchResultMapper mapper;

  @Autowired
  public MatchResultListener(
      ResultCandidateIntake intake,
      AcceptedResultRepository acceptedResults,
      ResultFanout fanout,
      CorrectionFanout corrections,
      Clock clock) {
    this(
        intake,
        acceptedResults,
        fanout,
        corrections,
        clock,
        new StrictAvroDecoder(),
        new KafkaUuidKeyValidator(),
        new MatchResultMapper());
  }

  MatchResultListener(
      ResultCandidateIntake intake,
      AcceptedResultRepository acceptedResults,
      ResultFanout fanout,
      CorrectionFanout corrections,
      Clock clock,
      StrictAvroDecoder decoder,
      KafkaUuidKeyValidator keys,
      MatchResultMapper mapper) {
    this.intake = intake;
    this.acceptedResults = acceptedResults;
    this.fanout = fanout;
    this.corrections = corrections;
    this.clock = clock;
    this.decoder = decoder;
    this.keys = keys;
    this.mapper = mapper;
  }

  @KafkaListener(
      topics = "${settlement.topics.match-result:match.result}",
      groupId = "settlement-service")
  public void receive(ConsumerRecord<byte[], byte[]> record, Acknowledgment acknowledgment) {
    MatchResult event = decoder.decode(record.value(), MatchResult.class);
    var eventId = keys.requireMatching(record.key(), event.getEventId(), "eventId");
    var result = intake.ingest(mapper.map(event, clock.instant()));
    boolean correction =
        result == ResultCandidateIntake.IntakeResult.AUTO_CORRECTION_ACCEPTED
            || result == ResultCandidateIntake.IntakeResult.ACCEPTED_REPLAY;
    if (result == ResultCandidateIntake.IntakeResult.FIRST_ACCEPTED || correction) {
      var accepted =
          acceptedResults
              .findByEventId(eventId)
              .orElseThrow(
                  () -> new IllegalStateException("Accepted result projection is missing"));
      fanout.fanOut(accepted);
      if (correction) {
        corrections.fanOut(accepted);
      }
    }
    acknowledgment.acknowledge();
  }
}
