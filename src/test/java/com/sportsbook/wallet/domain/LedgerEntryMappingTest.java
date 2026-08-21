package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Money;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LedgerEntryMappingTest {

  @Test
  void mapsImmutableJournalValueColumns() throws Exception {
    assertThat(LedgerEntry.class).hasAnnotation(Entity.class);
    assertThat(LedgerEntry.class.getAnnotation(Table.class).name()).isEqualTo("ledger_entry");
    assertThat(column("entryId"))
        .satisfies(this::isImmutable)
        .extracting(Column::name)
        .isEqualTo("entry_id");
    assertThat(column("accountId"))
        .satisfies(this::isImmutable)
        .extracting(Column::name)
        .isEqualTo("account_id");
    assertThat(column("bucket"))
        .satisfies(this::isImmutable)
        .extracting(Column::name)
        .isEqualTo("bucket");
    assertThat(column("side"))
        .satisfies(this::isImmutable)
        .extracting(Column::name)
        .isEqualTo("side");
    Field money = LedgerEntry.class.getDeclaredField("money");
    assertThat(money.getAnnotation(Embedded.class)).isNotNull();
    assertThat(money.getAnnotation(AttributeOverrides.class).value())
        .allSatisfy(override -> assertThat(override.column().updatable()).isFalse());
  }

  @Test
  void exposesHydratedJournalValues() {
    LedgerEntry entry = new LedgerEntry();
    UUID entryId = UUID.fromString("019b76da-a000-7000-8000-000000000003");
    UUID accountId = UUID.fromString("019b76da-a000-7000-8000-000000000004");
    ReflectionTestUtils.setField(entry, "entryId", entryId);
    ReflectionTestUtils.setField(entry, "accountId", accountId);
    ReflectionTestUtils.setField(entry, "bucket", BalanceBucket.LOCKED);
    ReflectionTestUtils.setField(entry, "side", LedgerSide.CREDIT);
    ReflectionTestUtils.setField(entry, "money", EmbeddedMoney.of(Money.krw(8L)));

    assertThat(entry.entryId()).isEqualTo(entryId);
    assertThat(entry.accountId()).isEqualTo(accountId);
    assertThat(entry.bucket()).isEqualTo(BalanceBucket.LOCKED);
    assertThat(entry.side()).isEqualTo(LedgerSide.CREDIT);
    assertThat(entry.money()).isEqualTo(Money.krw(8L));
  }

  @Test
  void mapsImmutableRequestAndOperationIdentity() throws Exception {
    for (String field :
        new String[] {"reason", "idempotencyKey", "operationGroupId", "createdAt"}) {
      assertThat(column(field).updatable()).isFalse();
    }
    Table table = LedgerEntry.class.getAnnotation(Table.class);
    assertThat(table.uniqueConstraints())
        .extracting(UniqueConstraint::name)
        .containsExactly("uk_ledger_entry_idempotency_side", "uk_ledger_entry_group_side");
    assertThat(table.uniqueConstraints())
        .extracting(constraint -> constraint.columnNames())
        .containsExactly(
            new String[] {"idempotency_key", "side"}, new String[] {"operation_group_id", "side"});
    assertThat(table.indexes())
        .extracting(Index::name, Index::columnList)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "ix_ledger_entry_account_created", "account_id, created_at"),
            org.assertj.core.groups.Tuple.tuple(
                "ix_ledger_entry_idempotency_key", "idempotency_key"));
  }

  @Test
  void exposesHydratedRequestAndOperationIdentity() {
    LedgerEntry entry = new LedgerEntry();
    UUID groupId = UUID.fromString("019b76da-a000-7000-8000-000000000005");
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    ReflectionTestUtils.setField(entry, "reason", LedgerReason.DEPOSIT);
    ReflectionTestUtils.setField(entry, "idempotencyKey", "deposit:mapping");
    ReflectionTestUtils.setField(entry, "operationGroupId", groupId);
    ReflectionTestUtils.setField(entry, "createdAt", createdAt);

    assertThat(entry.reason()).isEqualTo(LedgerReason.DEPOSIT);
    assertThat(entry.idempotencyKey()).isEqualTo("deposit:mapping");
    assertThat(entry.operationGroupId()).isEqualTo(groupId);
    assertThat(entry.createdAt()).isEqualTo(createdAt);
  }

  private Column column(String field) throws Exception {
    return LedgerEntry.class.getDeclaredField(field).getAnnotation(Column.class);
  }

  private void isImmutable(Column column) {
    assertThat(column.updatable()).isFalse();
  }
}
