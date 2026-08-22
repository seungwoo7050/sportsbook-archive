package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AdminAction;
import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.MarketClient;
import com.sportsbook.admin.client.MarketStatusPayload;
import com.sportsbook.admin.context.AdminContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/events/{eventId}/markets/{marketId}")
public class MarketAdminController {

  private final MarketClient markets;

  public MarketAdminController(MarketClient markets) {
    this.markets = markets;
  }

  @PostMapping("/suspend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  @Audited(
      action = AdminAction.MARKET_SUSPEND,
      target = "#eventId + '/' + #marketId",
      reason = "#body.reason()")
  public void suspend(
      @PathVariable UUID eventId,
      @PathVariable UUID marketId,
      @RequestBody MarketStatusPayload body,
      AdminContext context,
      HttpServletRequest servletRequest) {
    changeStatus(
        eventId, marketId, MarketClient.Action.SUSPEND, body, context, servletRequest);
  }

  private void changeStatus(
      UUID eventId,
      UUID marketId,
      MarketClient.Action action,
      MarketStatusPayload body,
      AdminContext context,
      HttpServletRequest request) {
    markets.changeStatus(
        eventId,
        marketId,
        action,
        body,
        AdminRequestHeaders.requireIdempotencyKey(request),
        context.actionId());
  }
}
