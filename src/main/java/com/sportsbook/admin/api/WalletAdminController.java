package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AdminAction;
import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.WalletClient;
import com.sportsbook.admin.context.AdminContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/wallet")
public class WalletAdminController {

  private final WalletClient wallet;

  public WalletAdminController(WalletClient wallet) {
    this.wallet = wallet;
  }

  @PostMapping("/{userId}/refund")
  @PreAuthorize("hasAnyRole('ADMIN','CS')")
  @Audited(action = AdminAction.WALLET_REFUND, target = "#userId", reason = "#body.reason()")
  public RefundResponse refund(
      @PathVariable UUID userId,
      @Valid @RequestBody RefundRequest body,
      AdminContext context,
      HttpServletRequest servletRequest) {
    UUID operationGroup =
        wallet.refund(
            userId, body.money(), AdminRequestHeaders.requireIdempotencyKey(servletRequest));
    return new RefundResponse(operationGroup, context.actionId());
  }
}
