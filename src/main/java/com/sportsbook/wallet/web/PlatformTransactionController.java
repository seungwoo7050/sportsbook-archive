package com.sportsbook.wallet.web;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.command.DepositCommand;
import com.sportsbook.wallet.service.command.WithdrawCommand;
import com.sportsbook.wallet.web.dto.TransactionRequest;
import com.sportsbook.wallet.web.dto.WalletOperationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes platform-owned external payment transfers. */
@RestController
@RequestMapping("/internal/v1/wallet/transactions")
public class PlatformTransactionController {
  private final WalletService wallet;

  public PlatformTransactionController(WalletService wallet) {
    this.wallet = Objects.requireNonNull(wallet, "wallet");
  }

  @PostMapping("/deposit")
  WalletOperationResponse deposit(
      @Valid @RequestBody TransactionRequest body, HttpServletRequest request) {
    IdempotencyKey key = WalletRequestHeaders.requireIdempotencyKey(request);
    return WalletOperationResponse.from(
        wallet.deposit(new DepositCommand(body.userId(), body.amount(), key)));
  }

  @PostMapping("/withdraw")
  WalletOperationResponse withdraw(
      @Valid @RequestBody TransactionRequest body, HttpServletRequest request) {
    IdempotencyKey key = WalletRequestHeaders.requireIdempotencyKey(request);
    return WalletOperationResponse.from(
        wallet.withdraw(new WithdrawCommand(body.userId(), body.amount(), key)));
  }
}
