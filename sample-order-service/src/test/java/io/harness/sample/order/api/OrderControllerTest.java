package io.harness.sample.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.sample.order.contract.OrderContracts.CreateOrderRequest;
import io.harness.sample.order.contract.OrderContracts.OrderResponse;
import io.harness.sample.order.domain.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The controller delegates and returns the contract type. */
class OrderControllerTest {

  @Test
  @DisplayName("placing through the controller returns the projection")
  void placeReturnsProjection() {
    OrderController controller = new OrderController(new OrderService(event -> {}));

    OrderResponse response = controller.place(new CreateOrderRequest("SKU-3", 2, 199L));

    assertThat(response.totalCents()).isEqualTo(398L);
    assertThat(response.sku()).isEqualTo("SKU-3");
  }
}
