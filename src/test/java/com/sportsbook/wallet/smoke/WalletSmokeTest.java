package com.sportsbook.wallet.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.wallet.domain.WalletCaller;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("wallet-semantic-gate")
class WalletSmokeTest extends WalletSmokeFixture {
  private static final UUID USER_ID = UUID.fromString("019b783d-1000-7000-8000-000000000001");
  private static final String DEPOSIT_KEY = "smoke:deposit:00000001";
  private static final String ACCOUNT_PATH = "/internal/v1/wallet/accounts";

  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;

  @Test
  void servesAuthenticatedDurableReplayAcrossPostgresAndRedis() throws Exception {
    var health = request(HttpMethod.GET, "/actuator/health", null, null, null);
    var unauthenticated =
        request(HttpMethod.GET, ACCOUNT_PATH + "/" + USER_ID + "/balance", null, null, null);

    assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    String account = "{\"userId\":\"" + USER_ID + "\",\"currency\":\"KRW\"}";
    var opened = request(HttpMethod.POST, ACCOUNT_PATH, WalletCaller.PLATFORM, null, account);
    assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.OK);

    String deposit =
        "{\"userId\":\"" + USER_ID + "\",\"amount\":{\"amount\":700,\"currency\":\"KRW\"}}";
    var first =
        request(
            HttpMethod.POST,
            "/internal/v1/wallet/transactions/deposit",
            WalletCaller.PLATFORM,
            DEPOSIT_KEY,
            deposit);
    var replay =
        request(
            HttpMethod.POST,
            "/internal/v1/wallet/transactions/deposit",
            WalletCaller.PLATFORM,
            DEPOSIT_KEY,
            deposit);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(replay.getBody()).isEqualTo(first.getBody());
    assertThat(json.readTree(first.getBody()).path("reason").textValue()).isEqualTo("DEPOSIT");

    var balance =
        request(
            HttpMethod.GET,
            ACCOUNT_PATH + "/" + USER_ID + "/balance",
            WalletCaller.GATEWAY,
            null,
            null);
    assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(json.readTree(balance.getBody()).at("/available/amount").longValue())
        .isEqualTo(700L);
    assertThat(count("wallet_operation", DEPOSIT_KEY)).isEqualTo(1);
    assertThat(count("ledger_entry", DEPOSIT_KEY)).isEqualTo(2);
    assertThat(redis.opsForValue().get("idempotency:wallet:" + DEPOSIT_KEY)).isEqualTo("1");
  }

  private int count(String table, String key) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE idempotency_key=?", Integer.class, key);
  }
}
