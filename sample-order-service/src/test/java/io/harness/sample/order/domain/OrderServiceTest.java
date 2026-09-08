package io.harness.sample.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.sample.order.contract.OrderContracts.CreateOrderRequest;
import io.harness.sample.order.contract.OrderContracts.OrderPlacedEvent;
import io.harness.sample.order.contract.OrderContracts.OrderResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Behaviour of the order domain, including the invariants the build will not let regress. */
class OrderServiceTest {

  private final List<OrderPlacedEvent> published = new ArrayList<>();
  private final OrderService service = new OrderService(published::add);

  @Test
  @DisplayName("placing an order computes the total in minor units and publishes the event")
  void placeComputesTotalAndPublishes() {
    OrderResponse response = service.place(new CreateOrderRequest("SKU-1", 3, 250L));

    assertThat(response.sku()).isEqualTo("SKU-1");
    assertThat(response.totalCents()).isEqualTo(750L);
    assertThat(response.id()).isNotBlank();
    assertThat(published).hasSize(1);
    assertThat(published.get(0).orderId()).isEqualTo(response.id());
    assertThat(published.get(0).totalCents()).isEqualTo(750L);
  }

  @Test
  @DisplayName("a non-positive quantity is rejected and nothing is published")
  void nonPositiveQuantityIsRejected() {
    assertThatThrownBy(() -> service.place(new CreateOrderRequest("SKU-1", 0, 250L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("quantity must be positive");
    assertThat(published).isEmpty();
  }

  @Test
  @DisplayName("a non-positive price is rejected and nothing is published")
  void nonPositivePriceIsRejected() {
    assertThatThrownBy(() -> service.place(new CreateOrderRequest("SKU-1", 1, 0L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("unitPriceCents must be positive");
    assertThat(published).isEmpty();
  }

  @Test
  @DisplayName("an overflowing total fails loudly rather than wrapping")
  void overflowingTotalThrows() {
    assertThatThrownBy(
            () -> service.place(new CreateOrderRequest("SKU-1", Integer.MAX_VALUE, Long.MAX_VALUE)))
        .isInstanceOf(ArithmeticException.class)
        .hasMessage("long overflow");
  }

  @Test
  @DisplayName("projection copies every field the contract declares")
  void projectionCopiesEveryField() {
    OrderResponse projected = OrderService.project(new OrderRecord("id-1", "SKU-2", 900L));

    assertThat(projected).isEqualTo(new OrderResponse("id-1", "SKU-2", 900L));
  }
}
