package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class WalletOperationMappingTest {

  @Test
  void mapsImmutableRequestIdentity() throws Exception {
    assertThat(WalletOperation.class).hasAnnotation(Entity.class);
    assertThat(WalletOperation.class.getAnnotation(Table.class).name())
        .isEqualTo("wallet_operation");
    assertThat(field("idempotencyKey").getAnnotation(Id.class)).isNotNull();
    for (String name :
        new String[] {
          "idempotencyKey", "caller", "kind", "userId", "requestFingerprint", "requestedAt"
        }) {
      assertThat(column(name).updatable()).as(name).isFalse();
    }
    Field amount = field("requestAmount");
    assertThat(amount.getAnnotation(Embedded.class)).isNotNull();
    assertThat(amount.getAnnotation(AttributeOverrides.class).value())
        .allSatisfy(override -> assertThat(override.column().updatable()).isFalse());
  }

  @Test
  void mapsDurableOutcomeState() throws Exception {
    assertThat(column("status").name()).isEqualTo("status");
    assertThat(column("operationGroupId").name()).isEqualTo("operation_group_id");
    assertThat(field("failure").getAnnotation(Embedded.class)).isNotNull();
    assertThat(column("requestedAt").name()).isEqualTo("requested_at");
    assertThat(column("updatedAt").name()).isEqualTo("updated_at");
    assertThat(column("completedAt").name()).isEqualTo("completed_at");
    assertThat(field("version").getAnnotation(Version.class)).isNotNull();
  }

  private static Field field(String name) throws Exception {
    return WalletOperation.class.getDeclaredField(name);
  }

  private static Column column(String name) throws Exception {
    return field(name).getAnnotation(Column.class);
  }
}
