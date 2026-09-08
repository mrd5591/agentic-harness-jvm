package io.harness.sample.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Covers the entity, including the no-arg constructor the persistence provider requires. */
class OrderRecordTest {

  @Test
  @DisplayName("the persistence constructor produces an empty record")
  void persistenceConstructorIsUsable() {
    OrderRecord record = new OrderRecord();

    assertThat(record.getId()).isNull();
    assertThat(record.getSku()).isNull();
    assertThat(record.getTotalCents()).isZero();
  }

  @Test
  @DisplayName("the value constructor sets every field")
  void valueConstructorSetsEveryField() {
    OrderRecord record = new OrderRecord("id-9", "SKU-9", 4200L);

    assertThat(record.getId()).isEqualTo("id-9");
    assertThat(record.getSku()).isEqualTo("SKU-9");
    assertThat(record.getTotalCents()).isEqualTo(4200L);
  }
}
