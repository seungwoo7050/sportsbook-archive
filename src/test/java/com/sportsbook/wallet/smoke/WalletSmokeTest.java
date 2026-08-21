package com.sportsbook.wallet.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.outbox.KafkaOutboxDispatcher;
import com.sportsbook.wallet.outbox.WalletEventFactory;
import com.sportsbook.wallet.persistence.OutboxDeliveryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@Tag("wallet-semantic-gate")
class WalletSmokeTest extends WalletSmokeFixture {
  private static final UUID USER_ID = UUID.fromString("019b783d-1000-7000-8000-000000000001");
  private static final String DEPOSIT_KEY = "smoke:deposit:00000001";
  private static final String ACCOUNT_PATH = "/internal/v1/wallet/accounts";

  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  @Autowired OutboxDeliveryRepository outbox;
  @Autowired KafkaOutboxDispatcher dispatcher;

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

  @Test
  void publishesCanonicalDebitsThroughKafkaBeforeFencedCompletion() throws Exception {
    UUID userId = UUID.fromString("019b783d-1000-7000-8000-000000000002");
    String betId = "019b783d-1000-7000-8000-000000000003";
    String account = "{\"userId\":\"" + userId + "\",\"currency\":\"KRW\"}";
    String funds = transaction(userId, 500L);
    String debit = transaction(userId, 200L);
    assertThat(
            request(HttpMethod.POST, ACCOUNT_PATH, WalletCaller.PLATFORM, null, account)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/internal/v1/wallet/transactions/deposit",
                    WalletCaller.PLATFORM,
                    "smoke:kafka:deposit",
                    funds)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            request(
                    HttpMethod.POST,
                    "/internal/v1/wallet/transactions/debit",
                    WalletCaller.BETTING,
                    betId,
                    debit)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    var message = outbox.claim("smoke-kafka", 1, Duration.ofSeconds(30)).get(0);
    var consumerFactory =
        new DefaultKafkaConsumerFactory<String, byte[]>(
            KafkaTestUtils.consumerProps(KAFKA.getBootstrapServers(), "wallet-smoke", "false"),
            new StringDeserializer(),
            new ByteArrayDeserializer());
    try (var consumer = consumerFactory.createConsumer()) {
      consumer.subscribe(List.of(WalletEventFactory.DEBITED_TOPIC));
      dispatcher.dispatch(message).toCompletableFuture().get(10, TimeUnit.SECONDS);
      var record =
          KafkaTestUtils.getSingleRecord(
              consumer, WalletEventFactory.DEBITED_TOPIC, Duration.ofSeconds(10));
      assertThat(record.key()).isEqualTo(userId.toString());
      assertThat(record.value()).containsExactly(message.payload());
      assertThat(record.headers().headers(KafkaOutboxDispatcher.EVENT_ID_HEADER))
          .extracting(header -> new String(header.value(), StandardCharsets.US_ASCII))
          .containsExactly(message.lease().eventId().toString());
      assertThat(outbox.markPublished(message.lease())).isTrue();
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT published_at IS NOT NULL AND lease_owner IS NULL AND lease_until IS NULL
                  FROM outbox_event WHERE event_id=?
                  """,
                  Boolean.class,
                  message.lease().eventId()))
          .isTrue();
    }
  }

  private int count(String table, String key) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE idempotency_key=?", Integer.class, key);
  }

  private String transaction(UUID userId, long amount) {
    return "{\"userId\":\""
        + userId
        + "\",\"amount\":{\"amount\":"
        + amount
        + ",\"currency\":\"KRW\"}}";
  }
}
