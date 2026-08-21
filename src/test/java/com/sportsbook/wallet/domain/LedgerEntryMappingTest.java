package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Money;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
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

  private Column column(String field) throws Exception {
    return LedgerEntry.class.getDeclaredField(field).getAnnotation(Column.class);
  }

  private void isImmutable(Column column) {
    assertThat(column.updatable()).isFalse();
  }
}
