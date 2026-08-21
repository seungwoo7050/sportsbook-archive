package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.domain.WalletOperationStatus;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletAdjustmentServiceTest {
  private static final UUID REVISION_ID = UUID.fromString("019b76da-a000-7000-8000-000000000141");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-000000000142");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000143");
  private static final IdempotencyKey KEY = IdempotencyKey.of("settlement:revision:" + REVISION_ID);

  @Mock WalletOperationExecutor operations;
  @Mock AdjustmentFirstWriter firstWriter;
  @Mock WalletAdjustmentRepository adjustments;
  @Mock WalletAdjustment proof;
  @Mock WalletOperation outcome;
  @InjectMocks WalletAdjustmentService service;

  @Test
  void executesTheCanonicalRequestAndReturnsItsProofDirectly() {
    AdjustmentCommand command = command();
    OperationFingerprint fingerprint =
        OperationFingerprint.adjustment(
            WalletCaller.SETTLEMENT,
            USER_ID,
            command.previousPayout(),
            command.newPayout(),
            REVISION_ID,
            BET_ID,
            1L);
    when(adjustments.findById(REVISION_ID)).thenReturn(Optional.of(proof));
    when(proof.idempotencyKey()).thenReturn(KEY.value());
    when(operations.execute(any(), any(), any(), any(), any(), any(), any())).thenReturn(outcome);
    when(outcome.status()).thenReturn(WalletOperationStatus.BLOCKED_FUNDS);

    assertThat(service.adjust(command)).isSameAs(proof);

    verify(operations)
        .execute(
            eq(KEY),
            eq(WalletCaller.SETTLEMENT),
            eq(WalletOperationKind.BET_ADJUSTMENT),
            eq(USER_ID),
            eq(Money.krw(300L)),
            eq(fingerprint),
            any());
  }

  private AdjustmentCommand command() {
    return new AdjustmentCommand(
        REVISION_ID, BET_ID, 1L, USER_ID, Money.krw(1_000L), Money.krw(700L), KEY);
  }
}
