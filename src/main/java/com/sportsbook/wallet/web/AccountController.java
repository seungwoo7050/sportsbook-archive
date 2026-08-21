package com.sportsbook.wallet.web;

import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.command.OpenAccountCommand;
import com.sportsbook.wallet.web.dto.AccountResponse;
import com.sportsbook.wallet.web.dto.BalanceResponse;
import com.sportsbook.wallet.web.dto.OpenAccountRequest;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes account creation and read-only balance snapshots. */
@RestController
@RequestMapping("/internal/v1/wallet/accounts")
public class AccountController {
  private final WalletService wallet;

  public AccountController(WalletService wallet) {
    this.wallet = Objects.requireNonNull(wallet, "wallet");
  }

  @PostMapping
  AccountResponse openAccount(@Valid @RequestBody OpenAccountRequest request) {
    return AccountResponse.from(
        wallet.openAccount(new OpenAccountCommand(request.userId(), request.currency())));
  }

  @GetMapping("/{userId}/balance")
  BalanceResponse balance(@PathVariable UUID userId) {
    return BalanceResponse.from(wallet.requireAccount(userId));
  }
}
