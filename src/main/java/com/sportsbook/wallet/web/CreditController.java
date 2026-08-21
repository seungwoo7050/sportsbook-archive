package com.sportsbook.wallet.web;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.web.dto.CreditRequest;
import com.sportsbook.wallet.web.dto.WalletOperationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes caller-sensitive wallet credits. */
@RestController
@RequestMapping("/internal/v1/wallet/transactions/credit")
public class CreditController {
  private final WalletService wallet;

  public CreditController(WalletService wallet) {
    this.wallet = Objects.requireNonNull(wallet, "wallet");
  }

  @PostMapping
  WalletOperationResponse credit(
      @AuthenticationPrincipal WalletCaller caller,
      @Valid @RequestBody CreditRequest body,
      HttpServletRequest request) {
    IdempotencyKey key = WalletRequestHeaders.requireIdempotencyKey(request);
    CreditCommand command =
        new CreditCommand(body.userId(), body.amount(), body.source(), body.reason(), key);
    return WalletOperationResponse.from(wallet.credit(caller, command));
  }
}
