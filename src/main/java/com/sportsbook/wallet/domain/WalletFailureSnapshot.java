package com.sportsbook.wallet.domain;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/** Immutable rejection data persisted with an operation instead of recomputing mutable facts. */
@Embeddable
public class WalletFailureSnapshot {
  @Enumerated(EnumType.STRING)
  @Column(name = "failure_code", length = 32)
  private WalletFailureCode code;

  @Column(name = "failure_http_status")
  private Short httpStatus;

  @Column(name = "failure_title", length = 128)
  private String title;

  @Column(name = "failure_detail", length = 1024)
  private String detail;

  @Column(name = "failure_balance_amount")
  private Long balanceAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_balance_currency", length = 3)
  private Currency balanceCurrency;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_expected_currency", length = 3)
  private Currency expectedCurrency;

  protected WalletFailureSnapshot() {}

  private WalletFailureSnapshot(
      WalletFailureCode code, String detail, Money balance, Currency expectedCurrency) {
    this.code = Objects.requireNonNull(code, "code");
    this.httpStatus = (short) code.httpStatus();
    this.title = code.title();
    this.detail = Objects.requireNonNull(detail, "detail");
    this.balanceAmount = balance == null ? null : balance.amount();
    this.balanceCurrency = balance == null ? null : balance.currency();
    this.expectedCurrency = expectedCurrency;
  }

  public static WalletFailureSnapshot of(WalletFailureCode code, String detail) {
    return new WalletFailureSnapshot(code, detail, null, null);
  }

  public static WalletFailureSnapshot withBalance(
      WalletFailureCode code, String detail, Money balance) {
    return new WalletFailureSnapshot(code, detail, Objects.requireNonNull(balance), null);
  }

  public static WalletFailureSnapshot currencyMismatch(String detail, Currency expected) {
    return new WalletFailureSnapshot(WalletFailureCode.CURRENCY_MISMATCH, detail, null, expected);
  }

  public WalletFailureCode code() {
    return code;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String title() {
    return title;
  }

  public String detail() {
    return detail;
  }

  public Money balance() {
    return balanceAmount == null ? null : new Money(balanceAmount, balanceCurrency);
  }

  public Currency expectedCurrency() {
    return expectedCurrency;
  }
}
