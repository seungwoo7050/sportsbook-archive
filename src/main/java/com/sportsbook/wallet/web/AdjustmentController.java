package com.sportsbook.wallet.web;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.error.WalletAdjustmentNotFoundException;
import com.sportsbook.wallet.service.WalletAdjustmentService;
import com.sportsbook.wallet.web.dto.AdjustmentProofResponse;
import com.sportsbook.wallet.web.dto.AdjustmentRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes settlement payout correction requests and durable proofs. */
@RestController
@RequestMapping("/internal/v1/wallet/transactions/adjustment")
public class AdjustmentController {
  private static final String PROOF_PATH = "/internal/v1/wallet/transactions/adjustment/";

  private final WalletAdjustmentService adjustments;

  public AdjustmentController(WalletAdjustmentService adjustments) {
    this.adjustments = Objects.requireNonNull(adjustments, "adjustments");
  }

  @PostMapping
  ResponseEntity<AdjustmentProofResponse> adjust(
      @Valid @RequestBody AdjustmentRequest body, HttpServletRequest request) {
    IdempotencyKey key = WalletRequestHeaders.requireIdempotencyKey(request);
    WalletAdjustment proof = adjustments.adjust(body.toCommand(key));
    AdjustmentProofResponse response = AdjustmentProofResponse.from(proof);
    return switch (proof.status()) {
      case APPLIED -> ResponseEntity.ok(response);
      case BLOCKED ->
          ResponseEntity.accepted()
              .location(URI.create(PROOF_PATH + proof.revisionId()))
              .body(response);
      case REJECTED -> throw new IllegalStateException("Adjustment rejection returned as a proof");
    };
  }

  @GetMapping("/{revisionId}")
  AdjustmentProofResponse proof(@PathVariable UUID revisionId) {
    return adjustments
        .findProof(revisionId)
        .map(AdjustmentProofResponse::from)
        .orElseThrow(() -> new WalletAdjustmentNotFoundException(revisionId));
  }
}
