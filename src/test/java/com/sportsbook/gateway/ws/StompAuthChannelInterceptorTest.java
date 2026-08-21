package com.sportsbook.gateway.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class StompAuthChannelInterceptorTest {

  private static final String USER_ID = "11111111-1111-4111-8111-111111111111";
  private static final String EVENT_ID = "abcdefab-cdef-4abc-8def-abcdefabcdef";

  @Test
  void authenticatesConnectWithTheSharedDecoder() {
    AtomicReference<String> decoded = new AtomicReference<>();
    StompAuthChannelInterceptor interceptor =
        new StompAuthChannelInterceptor(
            token -> {
              decoded.set(token);
              return jwt();
            });
    Message<byte[]> frame = connect(StompCommand.STOMP, "Bearer signed-token");

    interceptor.preSend(frame, null);

    JwtAuthenticationToken authentication =
        (JwtAuthenticationToken) StompHeaderAccessor.wrap(frame).getUser();
    assertThat(decoded).hasValue("signed-token");
    assertThat(authentication.getName()).isEqualTo(USER_ID);
    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_USER", "ROLE_TRADER");
  }

  @Test
  void permitsAnonymousConnectWithoutDecoding() {
    StompAuthChannelInterceptor interceptor =
        new StompAuthChannelInterceptor(
            token -> {
              throw new AssertionError("anonymous CONNECT must not decode a token");
            });
    Message<byte[]> frame = connect(StompCommand.CONNECT);
    MessageHeaderAccessor.getAccessor(frame, StompHeaderAccessor.class)
        .setUser(new JwtAuthenticationToken(jwt()));

    assertThatCode(() -> interceptor.preSend(frame, null)).doesNotThrowAnyException();
    assertThat(StompHeaderAccessor.wrap(frame).getUser()).isNull();
  }

  @Test
  void rejectsMalformedDuplicateAndUnverifiableCredentials() {
    StompAuthChannelInterceptor interceptor =
        new StompAuthChannelInterceptor(
            token -> {
              throw new JwtException("rejected");
            });

    assertRejected(interceptor, "Basic token");
    assertRejected(interceptor, "Bearer ");
    assertRejected(interceptor, "Bearer one", "Bearer two");
    assertRejected(interceptor, "Bearer rejected-token");
  }

  @Test
  void rejectsEveryUnsupportedClientCommand() {
    for (StompCommand command :
        List.of(
            StompCommand.SEND,
            StompCommand.MESSAGE,
            StompCommand.CONNECTED,
            StompCommand.RECEIPT,
            StompCommand.ERROR,
            StompCommand.ACK,
            StompCommand.NACK,
            StompCommand.BEGIN,
            StompCommand.COMMIT,
            StompCommand.ABORT)) {
      assertDenied(frame(command, "/topic/odds/" + EVENT_ID, null));
    }
  }

  @Test
  void allowsOnlyPublicOddsAndAuthenticatedBetSubscriptions() {
    StompAuthChannelInterceptor interceptor = interceptor();
    for (StompCommand command : List.of(StompCommand.UNSUBSCRIBE, StompCommand.DISCONNECT)) {
      assertThatCode(() -> interceptor.preSend(frame(command, null, null), null))
          .doesNotThrowAnyException();
    }
    assertThatCode(
            () ->
                interceptor.preSend(
                    frame(StompCommand.SUBSCRIBE, "/topic/odds/" + EVENT_ID, null), null))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                interceptor.preSend(
                    frame(
                        StompCommand.SUBSCRIBE,
                        "/user/queue/bets",
                        new JwtAuthenticationToken(jwt())),
                    null))
        .doesNotThrowAnyException();

    assertDenied(frame(StompCommand.SUBSCRIBE, "/user/queue/bets", null));
    assertDenied(frame(StompCommand.SUBSCRIBE, "/user/queue/bets", (Principal) () -> USER_ID));
    assertDenied(frame(StompCommand.SUBSCRIBE, "/topic/odds/" + EVENT_ID + "/extra", null));
    assertDenied(frame(StompCommand.SUBSCRIBE, "/topic/odds/" + EVENT_ID.toUpperCase(), null));
    assertDenied(frame(StompCommand.SUBSCRIBE, "/queue/internal", null));
  }

  private static void assertRejected(
      StompAuthChannelInterceptor interceptor, String... credentials) {
    assertThatThrownBy(() -> interceptor.preSend(connect(StompCommand.CONNECT, credentials), null))
        .isInstanceOf(MessageDeliveryException.class);
  }

  private static void assertDenied(Message<byte[]> frame) {
    assertThatThrownBy(() -> interceptor().preSend(frame, null))
        .isInstanceOf(MessageDeliveryException.class);
  }

  private static StompAuthChannelInterceptor interceptor() {
    return new StompAuthChannelInterceptor(token -> jwt());
  }

  private static Message<byte[]> frame(
      StompCommand command, String destination, Principal principal) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setDestination(destination);
    accessor.setUser(principal);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private static Message<byte[]> connect(StompCommand command, String... credentials) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    for (String credential : credentials) {
      accessor.addNativeHeader("Authorization", credential);
    }
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private static Jwt jwt() {
    return Jwt.withTokenValue("signed-token")
        .header("alg", "RS256")
        .subject(USER_ID)
        .claim("roles", List.of("USER", "TRADER"))
        .issuedAt(Instant.now().minusSeconds(1))
        .expiresAt(Instant.now().plusSeconds(60))
        .build();
  }
}
