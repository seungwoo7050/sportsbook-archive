package com.sportsbook.wallet.web;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.command.ForfeitCommand;
import com.sportsbook.wallet.web.dto.TransactionRequest;
import com.sportsbook.wallet.web.dto.WalletOperationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes settlement-owned forfeiture of losing locked stakes. */
@RestController
@RequestMapping("/internal/v1/wallet/transactions")
public class ForfeitController {
  private final WalletService wallet;

  public ForfeitController(WalletService wallet) {
    this.wallet = Objects.requireNonNull(wallet, "wallet");
  }

  @PostMapping("/forfeit")
  WalletOperationResponse forfeit(
      @Valid @RequestBody TransactionRequest body, HttpServletRequest request) {
    IdempotencyKey key = WalletRequestHeaders.requireIdempotencyKey(request);
    return WalletOperationResponse.from(
        wallet.forfeit(new ForfeitCommand(body.userId(), body.amount(), key)));
  }
}
