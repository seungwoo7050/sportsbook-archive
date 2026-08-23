package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AdminAction;
import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.RiskClient;
import com.sportsbook.admin.client.RiskLimitPayload;
import com.sportsbook.admin.client.RiskLimitType;
import com.sportsbook.admin.client.RiskLimitsResponse;
import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.protocol.value.Currency;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/risk/users/{userId}/limits")
public class RiskAdminController {

  private final RiskClient risk;

  public RiskAdminController(RiskClient risk) {
    this.risk = risk;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','TRADER','CS','READONLY')")
  public RiskLimitsResponse getLimits(@PathVariable UUID userId) {
    return risk.getLimits(userId);
  }

  @PatchMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  @Audited(action = AdminAction.RISK_LIMIT_UPDATE, target = "#userId + ':' + #body.type()")
  public void setLimit(
      @PathVariable UUID userId, @Valid @RequestBody RiskLimitPayload body, AdminContext context) {
    risk.setLimit(userId, body);
  }

  @DeleteMapping("/{type}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  @Audited(action = AdminAction.RISK_LIMIT_CLEAR, target = "#userId + ':' + #type")
  public void clearLimit(
      @PathVariable UUID userId,
      @PathVariable RiskLimitType type,
      @RequestParam(required = false) Currency currency,
      AdminContext context) {
    if (type.requiresCurrency() != (currency != null)) {
      throw new AdminRequestException("Risk limit currency scope does not match its type");
    }
    risk.clearLimit(userId, type, currency);
  }
}
