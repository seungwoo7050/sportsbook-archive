package com.sportsbook.wallet.web;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.wallet.domain.error.WalletOperationNotFoundException;
import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.command.DebitCommand;
import com.sportsbook.wallet.web.dto.TransactionRequest;
import com.sportsbook.wallet.web.dto.WalletOperationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes betting-owned stake reservation and durable debit lookup. */
@RestController
@RequestMapping("/internal/v1/wallet/transactions/debit")
public class BettingTransactionController {
  private final WalletService wallet;

  public BettingTransactionController(WalletService wallet) {
    this.wallet = Objects.requireNonNull(wallet, "wallet");
  }

  @PostMapping
  WalletOperationResponse debit(
      @Valid @RequestBody TransactionRequest body, HttpServletRequest request) {
    IdempotencyKey key = WalletRequestHeaders.requireCanonicalDebitKey(request);
    return WalletOperationResponse.from(
        wallet.debit(new DebitCommand(body.userId(), body.amount(), key)));
  }

  @GetMapping("/{betId}")
  WalletOperationResponse debitOutcome(@PathVariable String betId) {
    UUID canonicalBetId = WalletRequestHeaders.requireCanonicalDebitId(betId);
    return wallet
        .findDebit(canonicalBetId)
        .map(WalletOperationResponse::from)
        .orElseThrow(() -> new WalletOperationNotFoundException(canonicalBetId));
  }
}
