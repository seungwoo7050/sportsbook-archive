package com.sportsbook.settlement.correction;

import com.sportsbook.settlement.result.MatchResultRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResultCandidateIntake {

  private final ResultCandidateStore store;
  private final ResultCandidateFingerprinter fingerprints;

  public ResultCandidateIntake(ResultCandidateStore store) {
    this.store = store;
    this.fingerprints = new ResultCandidateFingerprinter();
  }

  @Transactional
  public IntakeResult ingest(MatchResultRecord result) {
    var accepted = store.findAcceptedCandidateId(result.eventId());
    String fingerprint =
        fingerprints.fingerprint(result.eventId(), result.mode(), result.outcomes());
    ResultCandidate candidate =
        ResultCandidate.pending(
            result.eventId(),
            fingerprint,
            result.mode(),
            result.outcomes(),
            result.settledAt(),
            result.receivedAt(),
            accepted.orElse(null));
    ResultCandidateStore.RecordOutcome recorded = store.record(candidate);
    if (recorded.kind() != ResultCandidateStore.RecordKind.CREATED) {
      return recorded.kind() == ResultCandidateStore.RecordKind.EXACT_REPLAY
          ? IntakeResult.EXACT_REPLAY
          : IntakeResult.NO_CHANGE;
    }
    if (accepted.isEmpty()) {
      return store.acceptFirst(candidate.candidateId(), result.receivedAt())
          ? IntakeResult.FIRST_ACCEPTED
          : IntakeResult.CORRECTION_PENDING;
    }
    return store.replaceAccepted(
            candidate.candidateId(), accepted.orElseThrow(), result.receivedAt())
        ? IntakeResult.AUTO_CORRECTION_ACCEPTED
        : IntakeResult.CORRECTION_PENDING;
  }

  public enum IntakeResult {
    EXACT_REPLAY,
    NO_CHANGE,
    FIRST_ACCEPTED,
    AUTO_CORRECTION_ACCEPTED,
    CORRECTION_PENDING
  }
}
